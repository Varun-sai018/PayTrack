package com.paytrack.wallet.service;

import com.paytrack.wallet.UserContextHolder;
import com.paytrack.wallet.dto.*;
import com.paytrack.wallet.entity.LedgerEntry;
import com.paytrack.wallet.entity.PaymentOrder;
import com.paytrack.wallet.entity.Wallet;
import com.paytrack.wallet.exception.InsufficientBalanceException;
import com.paytrack.wallet.exception.ResourceNotFoundException;
import com.paytrack.wallet.exception.WalletNotFoundException;
import com.paytrack.wallet.repository.LedgerEntryRepository;
import com.paytrack.wallet.repository.PaymentOrderRepository;
import com.paytrack.wallet.repository.WalletRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    public static final String OPERATOR_WALLET = "OPERATOR";
    public static final String BANK_SETTLEMENT_WALLET = "BANK_SETTLEMENT";

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PaymentOrderRepository paymentOrderRepository;

    @Value("${paytrack.gateway.key-id:sandbox_key_paytrack_2026}")
    private String gatewayKeyId;

    @Value("${paytrack.gateway.key-secret:paytrack-gateway-hmac-secret-2026}")
    private String gatewayKeySecret;

    @PostConstruct
    public void initSystemWallets() {
        try {
            getOrCreateSystemWallet(OPERATOR_WALLET);
            getOrCreateSystemWallet(BANK_SETTLEMENT_WALLET);
        } catch (Exception e) {
            log.warn("System wallets initialization deferred: {}", e.getMessage());
        }
    }

    public Wallet getOrCreateSystemWallet(String systemName) {
        return walletRepository.findBySystemName(systemName)
                .orElseGet(() -> {
                    Wallet systemWallet = Wallet.builder()
                            .systemName(systemName)
                            .ownerType(Wallet.OwnerType.SYSTEM)
                            .balance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                            .currency("INR")
                            .version(0L)
                            .build();
                    return walletRepository.save(systemWallet);
                });
    }

    public WalletDto getWallet(String userId) {
        Wallet wallet = getOrCreateWallet(userId);
        return convertToDto(wallet);
    }

    @Transactional
    public Wallet getOrCreateWallet(String userId) {
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Wallet newWallet = Wallet.builder()
                            .userId(userId)
                            .ownerType(Wallet.OwnerType.USER)
                            .balance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                            .currency("INR")
                            .version(0L)
                            .build();
                    Wallet saved = walletRepository.save(newWallet);
                    log.info("Initialized new INR wallet for user: {}", userId);
                    return saved;
                });
    }

    @Transactional
    public WalletDto createWallet(CreateWalletRequest request) {
        if (request.getCurrency() != null && !"INR".equalsIgnoreCase(request.getCurrency().trim())) {
            throw new IllegalArgumentException("Only INR currency is supported");
        }
        if (walletRepository.existsByUserId(request.getUserId())) {
            throw new IllegalArgumentException("Wallet already exists for user: " + request.getUserId());
        }
        Wallet wallet = Wallet.builder()
                .userId(request.getUserId())
                .ownerType(Wallet.OwnerType.USER)
                .balance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .currency("INR")
                .version(0L)
                .build();
        return convertToDto(walletRepository.save(wallet));
    }

    public Page<StatementEntryDto> getStatement(String userId, int page, int size, String type) {
        Wallet wallet = getOrCreateWallet(userId);
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        Page<LedgerEntry> entries;
        if (type != null && !type.isBlank()) {
            LedgerEntry.EntryType entryType = LedgerEntry.EntryType.valueOf(type.toUpperCase());
            entries = ledgerEntryRepository.findByWalletIdAndEntryTypeOrderByCreatedAtDesc(wallet.getId(), entryType, pageable);
        } else {
            entries = ledgerEntryRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable);
        }
        return entries.map(this::convertToStatementDto);
    }

    @Transactional
    public InternalTransferResponse transfer(InternalTransferRequest request) {
        String txnRef = request.getTxnRef();
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero");
        }

        // Idempotency check: if ledger entries for this txnRef already exist, return existing result
        List<LedgerEntry> existing = ledgerEntryRepository.findByTxnRef(txnRef);
        if (!existing.isEmpty()) {
            log.info("Transfer {} already processed (idempotent replay)", txnRef);
            BigDecimal senderBalance = existing.stream()
                    .filter(e -> e.getEntryType() == LedgerEntry.EntryType.DEBIT)
                    .map(LedgerEntry::getBalanceAfter)
                    .findFirst()
                    .orElse(BigDecimal.ZERO);
            return InternalTransferResponse.builder()
                    .txnRef(txnRef)
                    .status("SUCCESS")
                    .senderBalanceAfter(senderBalance)
                    .build();
        }

        // Resolve sender wallet
        Wallet senderWallet;
        if (request.getFromSystemWallet() != null && !request.getFromSystemWallet().isBlank()) {
            senderWallet = getOrCreateSystemWallet(request.getFromSystemWallet());
        } else if (request.getFromUserId() != null && !request.getFromUserId().isBlank()) {
            senderWallet = getOrCreateWallet(request.getFromUserId());
        } else {
            throw new IllegalArgumentException("Sender must be specified (fromUserId or fromSystemWallet)");
        }

        // Resolve receiver wallet
        Wallet receiverWallet;
        if (request.getToSystemWallet() != null && !request.getToSystemWallet().isBlank()) {
            receiverWallet = getOrCreateSystemWallet(request.getToSystemWallet());
        } else if (request.getToUserId() != null && !request.getToUserId().isBlank()) {
            receiverWallet = getOrCreateWallet(request.getToUserId());
        } else {
            throw new IllegalArgumentException("Receiver must be specified (toUserId or toSystemWallet)");
        }

        if (senderWallet.getId().equals(receiverWallet.getId())) {
            throw new IllegalArgumentException("Sender and receiver wallets cannot be the same");
        }

        // Deadlock prevention: Lock wallets in ascending ID order
        Wallet firstLockWallet;
        Wallet secondLockWallet;
        boolean senderIsFirst = senderWallet.getId() < receiverWallet.getId();
        if (senderIsFirst) {
            firstLockWallet = walletRepository.findByIdForUpdate(senderWallet.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Sender wallet not found"));
            secondLockWallet = walletRepository.findByIdForUpdate(receiverWallet.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Receiver wallet not found"));
        } else {
            firstLockWallet = walletRepository.findByIdForUpdate(receiverWallet.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Receiver wallet not found"));
            secondLockWallet = walletRepository.findByIdForUpdate(senderWallet.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Sender wallet not found"));
        }

        Wallet lockedSender = senderIsFirst ? firstLockWallet : secondLockWallet;
        Wallet lockedReceiver = senderIsFirst ? secondLockWallet : firstLockWallet;

        // Check sender balance (User wallets cannot go below zero)
        if (lockedSender.getOwnerType() == Wallet.OwnerType.USER && lockedSender.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance. Available: " + lockedSender.getBalance());
        }

        // Update balances
        lockedSender.setBalance(lockedSender.getBalance().subtract(amount).setScale(2, RoundingMode.HALF_UP));
        lockedReceiver.setBalance(lockedReceiver.getBalance().add(amount).setScale(2, RoundingMode.HALF_UP));

        walletRepository.save(lockedSender);
        walletRepository.save(lockedReceiver);

        // Double-entry ledger
        LedgerEntry debitEntry = LedgerEntry.builder()
                .id(UUID.randomUUID().toString())
                .walletId(lockedSender.getId())
                .txnRef(txnRef)
                .entryType(LedgerEntry.EntryType.DEBIT)
                .amount(amount)
                .balanceAfter(lockedSender.getBalance())
                .description(request.getDescription() != null ? request.getDescription() : "Debit transfer")
                .createdAt(LocalDateTime.now())
                .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .id(UUID.randomUUID().toString())
                .walletId(lockedReceiver.getId())
                .txnRef(txnRef)
                .entryType(LedgerEntry.EntryType.CREDIT)
                .amount(amount)
                .balanceAfter(lockedReceiver.getBalance())
                .description(request.getDescription() != null ? request.getDescription() : "Credit transfer")
                .createdAt(LocalDateTime.now())
                .build();

        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        log.info("Atomic transfer completed: {} - Amount: {} INR, SenderBalanceAfter: {}",
                txnRef, amount, lockedSender.getBalance());

        return InternalTransferResponse.builder()
                .txnRef(txnRef)
                .status("SUCCESS")
                .senderBalanceAfter(lockedSender.getBalance())
                .build();
    }

    @Transactional
    public TopupOrderResponse createTopupOrder(String userId, TopupOrderRequest request) {
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Top-up amount must be at least 1.00 INR");
        }

        String orderId = "order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        PaymentOrder order = PaymentOrder.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .currency("INR")
                .status(PaymentOrder.OrderStatus.CREATED)
                .createdAt(LocalDateTime.now())
                .build();

        paymentOrderRepository.save(order);
        log.info("Created top-up order {} for user {} for {} INR", orderId, userId, amount);

        return TopupOrderResponse.builder()
                .orderId(orderId)
                .amount(amount)
                .currency("INR")
                .keyId(gatewayKeyId)
                .build();
    }

    @Transactional
    public TopupVerifyResponse verifyTopup(String userId, TopupVerifyRequest request) {
        PaymentOrder order = paymentOrderRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment order not found: " + request.getOrderId()));

        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Order does not belong to the authenticated user");
        }

        Wallet userWallet = getOrCreateWallet(userId);

        if (order.getStatus() == PaymentOrder.OrderStatus.PAID) {
            log.info("Order {} already verified and paid", order.getOrderId());
            return TopupVerifyResponse.builder()
                    .orderId(order.getOrderId())
                    .status("PAID")
                    .balance(userWallet.getBalance())
                    .build();
        }

        // Verify HMAC-SHA256 signature: orderId + "|" + paymentId
        String expectedPayload = request.getOrderId() + "|" + request.getPaymentId();
        String expectedSignature = calculateHmacSha256(expectedPayload, gatewayKeySecret);

        if (!expectedSignature.equalsIgnoreCase(request.getSignature())) {
            order.setStatus(PaymentOrder.OrderStatus.FAILED);
            order.setFailureReason("Invalid signature");
            paymentOrderRepository.save(order);
            throw new IllegalArgumentException("Invalid payment signature");
        }

        order.setStatus(PaymentOrder.OrderStatus.PAID);
        order.setPaymentId(request.getPaymentId());
        order.setPaidAt(LocalDateTime.now());
        paymentOrderRepository.save(order);

        // Counter-entry from BANK_SETTLEMENT to user wallet
        InternalTransferRequest transferRequest = InternalTransferRequest.builder()
                .txnRef("TOPUP-" + order.getOrderId())
                .fromSystemWallet(BANK_SETTLEMENT_WALLET)
                .toUserId(userId)
                .amount(order.getAmount())
                .description("Wallet top-up via " + request.getPaymentId())
                .build();

        InternalTransferResponse transferResponse = transfer(transferRequest);

        log.info("Top-up verified and credited for user {}: order {}, new balance {}",
                userId, order.getOrderId(), transferResponse.getSenderBalanceAfter());

        Wallet updatedWallet = getOrCreateWallet(userId);

        return TopupVerifyResponse.builder()
                .orderId(order.getOrderId())
                .status("PAID")
                .balance(updatedWallet.getBalance())
                .build();
    }

    public SandboxPayResponse sandboxPay(SandboxPayRequest request) {
        PaymentOrder order = paymentOrderRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.getOrderId()));

        if (order.getStatus() == PaymentOrder.OrderStatus.PAID) {
            return SandboxPayResponse.builder()
                    .status("PAID")
                    .paymentId(order.getPaymentId())
                    .signature(calculateHmacSha256(order.getOrderId() + "|" + order.getPaymentId(), gatewayKeySecret))
                    .build();
        }

        String instrument = request.getInstrument().trim().toLowerCase();

        boolean success = "success@paytrack".equals(instrument) || "4111111111111111".equals(instrument);
        String failureReason = null;

        if (!success) {
            if ("failure@paytrack".equals(instrument)) {
                failureReason = "Payment declined by user";
            } else if ("4000000000000002".equals(instrument)) {
                failureReason = "CARD_DECLINED";
            } else if ("4000000000009995".equals(instrument)) {
                failureReason = "INSUFFICIENT_FUNDS";
            } else {
                failureReason = "INVALID_INSTRUMENT";
            }
        }

        if (success) {
            String paymentId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String signature = calculateHmacSha256(order.getOrderId() + "|" + paymentId, gatewayKeySecret);
            return SandboxPayResponse.builder()
                    .status("PAID")
                    .paymentId(paymentId)
                    .signature(signature)
                    .build();
        } else {
            return SandboxPayResponse.builder()
                    .status("FAILED")
                    .failureReason(failureReason)
                    .build();
        }
    }

    private String calculateHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    private WalletDto convertToDto(Wallet wallet) {
        return WalletDto.builder()
                .id(wallet.getId())
                .userId(wallet.getUserId())
                .balance(wallet.getBalance())
                .currency(wallet.getCurrency())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }

    private StatementEntryDto convertToStatementDto(LedgerEntry entry) {
        return StatementEntryDto.builder()
                .id(entry.getId())
                .txnRef(entry.getTxnRef())
                .entryType(entry.getEntryType().name())
                .amount(entry.getAmount())
                .balanceAfter(entry.getBalanceAfter())
                .description(entry.getDescription())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
