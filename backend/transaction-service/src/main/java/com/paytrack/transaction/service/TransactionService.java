package com.paytrack.transaction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytrack.transaction.UserContextHolder;
import com.paytrack.transaction.client.AuthClient;
import com.paytrack.transaction.client.WalletClient;
import com.paytrack.transaction.config.RabbitMQConfig;
import com.paytrack.transaction.dto.*;
import com.paytrack.transaction.entity.Recharge;
import com.paytrack.transaction.entity.Transaction;
import com.paytrack.transaction.event.PaymentCompletedEvent;
import com.paytrack.transaction.exception.BusinessException;
import com.paytrack.transaction.exception.ResourceNotFoundException;
import com.paytrack.transaction.repository.RechargeRepository;
import com.paytrack.transaction.repository.TransactionRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final RechargeRepository rechargeRepository;
    private final WalletClient walletClient;
    private final AuthClient authClient;
    private final RabbitTemplate rabbitTemplate;
    private final RechargeOperator rechargeOperator;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Value("${paytrack.limits.min-amount:1.00}")
    private BigDecimal minAmount;

    @Value("${paytrack.limits.max-transaction:10000.00}")
    private BigDecimal maxTransactionLimit;

    @Value("${paytrack.limits.daily-total:25000.00}")
    private BigDecimal dailyTotalLimit;

    @Value("${paytrack.recharge.min:10.00}")
    private BigDecimal minRechargeAmount;

    @Value("${paytrack.recharge.max:2000.00}")
    private BigDecimal maxRechargeAmount;

    @Value("${paytrack.timezone:Asia/Kolkata}")
    private String configuredTimezone;

    @Value("${internal.api.key:paytrack-internal-api-key-2026}")
    private String internalApiKey;

    @Transactional
    public TransferResponse transfer(String userId, String idempotencyKey, TransferRequest request) {
        log.info("Processing transfer request for user: {}, receiver: {}, amount: {}", userId, request.getReceiverUpiId(), request.getAmount());

        // 1. Sender KYC status check
        validateSenderKyc();

        // 2. Validate amount
        BigDecimal amount = validateAndScaleAmount(request.getAmount(), minAmount, maxTransactionLimit);

        // 3. Lookup receiver by UPI ID via Auth service
        UserLookupDto receiver = lookupReceiverByUpi(request.getReceiverUpiId());

        // 4. Validate receiver status and KYC
        if (!"ACTIVE".equalsIgnoreCase(receiver.getAccountStatus())) {
            throw new BusinessException("ACCOUNT_BLOCKED", "Receiver account is not active");
        }
        if (!"APPROVED".equalsIgnoreCase(receiver.getKycStatus())) {
            throw new BusinessException("KYC_NOT_APPROVED", "Receiver KYC is not approved");
        }

        // 5. Prevent self payment
        if (receiver.getUserId() != null && receiver.getUserId().equals(userId)) {
            throw new BusinessException("CANNOT_PAY_YOURSELF", "Cannot pay yourself");
        }

        // 6. Check daily limits
        validateDailyLimits(userId, amount);

        // 7. Check idempotency
        String requestHash = computeRequestHash(request);
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            Optional<Transaction> existingOpt = transactionRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existingOpt.isPresent()) {
                Transaction existing = existingOpt.get();
                if (existing.getRequestHash() != null && existing.getRequestHash().equals(requestHash)) {
                    log.info("Idempotent replay for txnRef: {}", existing.getTxnRef());
                    return convertToTransferResponse(existing, null);
                }
                throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "Idempotency key already used with different request");
            }
        }

        // 8. Create and persist PENDING transaction
        String txnRef = generateTxnRef();
        Transaction transaction = Transaction.builder()
                .userId(userId)
                .txnRef(txnRef)
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .receiverUserId(receiver.getUserId())
                .receiverUpiId(request.getReceiverUpiId())
                .amount(amount)
                .currency("INR")
                .type("P2P")
                .status("PENDING")
                .description(request.getNote() != null ? request.getNote() : "P2P transfer to " + receiver.getDisplayName())
                .requestedCategory(request.getCategory())
                .timestamp(LocalDateTime.now())
                .build();

        transaction = transactionRepository.save(transaction);

        // 9. Call Wallet internal transfer
        BigDecimal balanceAfter = null;
        try {
            InternalTransferRequest transferRequest = InternalTransferRequest.builder()
                    .txnRef(txnRef)
                    .fromUserId(userId)
                    .toUserId(receiver.getUserId())
                    .amount(amount)
                    .description("P2P transfer to " + receiver.getDisplayName())
                    .build();

            ApiResponse<InternalTransferResponse> walletResp = walletClient.internalTransfer(internalApiKey, transferRequest);
            if (walletResp != null && walletResp.isSuccess() && walletResp.getData() != null) {
                transaction.setStatus("COMPLETED");
                balanceAfter = walletResp.getData().getSenderBalanceAfter();
            } else {
                transaction.setStatus("FAILED");
                transaction.setFailureReason(walletResp != null ? walletResp.getMessage() : "Wallet transfer failed");
            }
        } catch (FeignException ex) {
            log.error("Feign exception during wallet transfer for {}: HTTP {}", txnRef, ex.status());
            if (ex.status() == 422) {
                transaction.setStatus("FAILED");
                transaction.setFailureReason("INSUFFICIENT_BALANCE");
                transactionRepository.save(transaction);
                throw new BusinessException("INSUFFICIENT_BALANCE", "Insufficient balance in wallet");
            }
            transaction.setStatus("FAILED");
            transaction.setFailureReason("WALLET_UNAVAILABLE");
            transactionRepository.save(transaction);
            throw new BusinessException("SERVICE_UNAVAILABLE", "Wallet service unavailable: " + ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        } catch (Exception ex) {
            log.error("Exception during wallet transfer for {}: {}", txnRef, ex.getMessage());
            transaction.setStatus("FAILED");
            transaction.setFailureReason("WALLET_UNAVAILABLE");
            transactionRepository.save(transaction);
            throw new BusinessException("SERVICE_UNAVAILABLE", "Wallet service unavailable: " + ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }

        transaction = transactionRepository.save(transaction);

        // 10. Publish payment.completed on success
        if ("COMPLETED".equals(transaction.getStatus())) {
            publishPaymentCompleted(transaction);
        }

        return convertToTransferResponse(transaction, balanceAfter);
    }

    @Transactional
    public QrGenerateResponse qrGenerate(String userId, QrGenerateRequest request) {
        log.info("Generating QR payload for user: {}", userId);
        UserLookupDto user;
        try {
            ApiResponse<UserLookupDto> userResp = authClient.getCurrentUser(userId);
            if (userResp != null && userResp.getData() != null) {
                user = userResp.getData();
            } else {
                throw new BusinessException("NOT_FOUND", "User not found in auth service");
            }
        } catch (Exception e) {
            log.error("Error retrieving user from auth service: {}", e.getMessage());
            throw new BusinessException("SERVICE_UNAVAILABLE", "Auth service unavailable: " + e.getMessage());
        }

        String upiId = user.getUpiId();
        if (upiId == null || upiId.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "User does not have a registered UPI ID");
        }
        String name = user.getDisplayName();

        StringBuilder qr = new StringBuilder("upi://pay?pa=").append(upiId)
                .append("&pn=").append(name);

        if (request != null && request.getAmount() != null) {
            BigDecimal amt = request.getAmount().setScale(2, RoundingMode.HALF_UP);
            qr.append("&am=").append(amt);
        }
        qr.append("&cu=INR");

        return QrGenerateResponse.builder()
                .qrPayload(qr.toString())
                .upiId(upiId)
                .receiverName(name)
                .amount(request != null ? request.getAmount() : null)
                .build();
    }

    @Transactional
    public TransferResponse qrPay(String userId, String idempotencyKey, QrPayRequest request) {
        log.info("Processing QR payment for user: {}", userId);

        validateSenderKyc();

        String qrPayload = request.getQrPayload();
        if (qrPayload == null || !qrPayload.startsWith("upi://pay?")) {
            throw new BusinessException("INVALID_SIGNATURE", "Invalid QR payload scheme");
        }

        Map<String, String> params = parseQrPayload(qrPayload);
        String pa = params.get("pa");
        String cu = params.get("cu");
        String amStr = params.get("am");

        if (pa == null || pa.isEmpty()) {
            throw new BusinessException("INVALID_SIGNATURE", "UPI ID (pa) missing from QR payload");
        }
        if (!"INR".equalsIgnoreCase(cu)) {
            throw new BusinessException("INVALID_SIGNATURE", "QR currency must be INR");
        }

        BigDecimal finalAmount = request.getAmount();
        if (amStr != null && !amStr.isEmpty()) {
            BigDecimal qrAmount = new BigDecimal(amStr).setScale(2, RoundingMode.HALF_UP);
            if (finalAmount != null && finalAmount.setScale(2, RoundingMode.HALF_UP).compareTo(qrAmount) != 0) {
                throw new BusinessException("VALIDATION_ERROR", "Request amount does not match amount specified in QR payload");
            }
            finalAmount = qrAmount;
        }

        if (finalAmount == null) {
            throw new BusinessException("VALIDATION_ERROR", "Amount must be specified either in QR or in request");
        }

        finalAmount = validateAndScaleAmount(finalAmount, minAmount, maxTransactionLimit);

        UserLookupDto receiver = lookupReceiverByUpi(pa);
        if (!"ACTIVE".equalsIgnoreCase(receiver.getAccountStatus())) {
            throw new BusinessException("ACCOUNT_BLOCKED", "Receiver account is not active");
        }
        if (!"APPROVED".equalsIgnoreCase(receiver.getKycStatus())) {
            throw new BusinessException("KYC_NOT_APPROVED", "Receiver KYC is not approved");
        }

        if (receiver.getUserId() != null && receiver.getUserId().equals(userId)) {
            throw new BusinessException("CANNOT_PAY_YOURSELF", "Cannot pay yourself");
        }

        validateDailyLimits(userId, finalAmount);

        String requestHash = computeRequestHash(request);
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            Optional<Transaction> existingOpt = transactionRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existingOpt.isPresent()) {
                Transaction existing = existingOpt.get();
                if (existing.getRequestHash() != null && existing.getRequestHash().equals(requestHash)) {
                    log.info("Idempotent replay for QR payment txnRef: {}", existing.getTxnRef());
                    return convertToTransferResponse(existing, null);
                }
                throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "Idempotency key already used with different request");
            }
        }

        String txnRef = generateTxnRef();
        Transaction transaction = Transaction.builder()
                .userId(userId)
                .txnRef(txnRef)
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .receiverUserId(receiver.getUserId())
                .receiverUpiId(pa)
                .amount(finalAmount)
                .currency("INR")
                .type("QR")
                .status("PENDING")
                .description(request.getNote() != null ? request.getNote() : "QR payment to " + receiver.getDisplayName())
                .requestedCategory(request.getCategory())
                .timestamp(LocalDateTime.now())
                .build();

        transaction = transactionRepository.save(transaction);

        BigDecimal balanceAfter = null;
        try {
            InternalTransferRequest transferRequest = InternalTransferRequest.builder()
                    .txnRef(txnRef)
                    .fromUserId(userId)
                    .toUserId(receiver.getUserId())
                    .amount(finalAmount)
                    .description("QR payment to " + receiver.getDisplayName())
                    .build();

            ApiResponse<InternalTransferResponse> walletResp = walletClient.internalTransfer(internalApiKey, transferRequest);
            if (walletResp != null && walletResp.isSuccess() && walletResp.getData() != null) {
                transaction.setStatus("COMPLETED");
                balanceAfter = walletResp.getData().getSenderBalanceAfter();
            } else {
                transaction.setStatus("FAILED");
                transaction.setFailureReason(walletResp != null ? walletResp.getMessage() : "Wallet transfer failed");
            }
        } catch (FeignException ex) {
            log.error("Feign exception during wallet transfer for {}: HTTP {}", txnRef, ex.status());
            if (ex.status() == 422) {
                transaction.setStatus("FAILED");
                transaction.setFailureReason("INSUFFICIENT_BALANCE");
                transactionRepository.save(transaction);
                throw new BusinessException("INSUFFICIENT_BALANCE", "Insufficient balance in wallet");
            }
            transaction.setStatus("FAILED");
            transaction.setFailureReason("WALLET_UNAVAILABLE");
            transactionRepository.save(transaction);
            throw new BusinessException("SERVICE_UNAVAILABLE", "Wallet service unavailable: " + ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        } catch (Exception ex) {
            log.error("Exception during wallet transfer for {}: {}", txnRef, ex.getMessage());
            transaction.setStatus("FAILED");
            transaction.setFailureReason("WALLET_UNAVAILABLE");
            transactionRepository.save(transaction);
            throw new BusinessException("SERVICE_UNAVAILABLE", "Wallet service unavailable: " + ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }

        transaction = transactionRepository.save(transaction);

        if ("COMPLETED".equals(transaction.getStatus())) {
            publishPaymentCompleted(transaction);
        }

        return convertToTransferResponse(transaction, balanceAfter);
    }

    @Transactional
    public RechargeResponse recharge(String userId, String idempotencyKey, RechargeRequest request) {
        log.info("Processing mobile recharge for user: {}, mobile: {}, operator: {}, amount: {}",
                userId, request.getMobileNumber(), request.getOperator(), request.getAmount());

        validateSenderKyc();

        String mobileNumber = request.getMobileNumber();
        if (mobileNumber == null || !mobileNumber.matches("^\\d{10}$")) {
            throw new BusinessException("VALIDATION_ERROR", "Mobile number must be exactly 10 digits");
        }

        String operator = request.getOperator() != null ? request.getOperator().toUpperCase() : "";
        if (!operator.matches("^(AIRTEL|JIO|VI|BSNL)$")) {
            throw new BusinessException("VALIDATION_ERROR", "Invalid operator: " + operator);
        }

        BigDecimal amount = validateAndScaleAmount(request.getAmount(), minRechargeAmount, maxRechargeAmount);
        validateDailyLimits(userId, amount);

        String requestHash = computeRequestHash(request);
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            Optional<Transaction> existingOpt = transactionRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existingOpt.isPresent()) {
                Transaction existing = existingOpt.get();
                if (existing.getRequestHash() != null && existing.getRequestHash().equals(requestHash)) {
                    log.info("Idempotent replay for recharge txnRef: {}", existing.getTxnRef());
                    Optional<Recharge> recOpt = rechargeRepository.findByTxnId(existing.getId());
                    return RechargeResponse.builder()
                            .txnRef(existing.getTxnRef())
                            .status(existing.getStatus())
                            .operatorRef(recOpt.map(Recharge::getOperatorRef).orElse(null))
                            .amount(existing.getAmount())
                            .createdAt(existing.getTimestamp())
                            .build();
                }
                throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "Idempotency key already used with different request");
            }
        }

        String txnRef = generateTxnRef();
        Transaction transaction = Transaction.builder()
                .userId(userId)
                .txnRef(txnRef)
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .amount(amount)
                .currency("INR")
                .type("RECHARGE")
                .status("PENDING")
                .description("Mobile recharge: " + mobileNumber + " (" + operator + ")")
                .requestedCategory(request.getCategory() != null ? request.getCategory() : "UTILITIES")
                .timestamp(LocalDateTime.now())
                .build();

        transaction = transactionRepository.save(transaction);

        // Step 1: Debit user wallet and credit OPERATOR system wallet
        try {
            InternalTransferRequest debitRequest = InternalTransferRequest.builder()
                    .txnRef(txnRef + "-DEBIT")
                    .fromUserId(userId)
                    .toSystemWallet("OPERATOR")
                    .amount(amount)
                    .description("Mobile recharge debit to OPERATOR")
                    .build();

            ApiResponse<InternalTransferResponse> debitResp = walletClient.internalTransfer(internalApiKey, debitRequest);
            if (debitResp == null || !debitResp.isSuccess()) {
                transaction.setStatus("FAILED");
                transaction.setFailureReason("WALLET_DEBIT_FAILED");
                transactionRepository.save(transaction);
                throw new BusinessException("INSUFFICIENT_BALANCE", "Wallet debit failed for recharge");
            }
        } catch (FeignException ex) {
            log.error("Debit failed for recharge: HTTP {}", ex.status());
            transaction.setStatus("FAILED");
            transaction.setFailureReason(ex.status() == 422 ? "INSUFFICIENT_BALANCE" : "WALLET_UNAVAILABLE");
            transactionRepository.save(transaction);
            if (ex.status() == 422) {
                throw new BusinessException("INSUFFICIENT_BALANCE", "Insufficient balance for recharge");
            }
            throw new BusinessException("SERVICE_UNAVAILABLE", "Wallet service unavailable for recharge", HttpStatus.SERVICE_UNAVAILABLE);
        }

        // Step 2: Call RechargeOperator stub
        RechargeOperator.RechargeResult result = rechargeOperator.executeRecharge(mobileNumber, operator, amount);

        if (result.success()) {
            transaction.setStatus("COMPLETED");
            transaction = transactionRepository.save(transaction);

            Recharge rechargeEntity = Recharge.builder()
                    .id(UUID.randomUUID().toString())
                    .txnId(transaction.getId())
                    .mobileNumber(mobileNumber)
                    .operator(operator)
                    .operatorRef(result.operatorRef())
                    .createdAt(LocalDateTime.now())
                    .build();
            rechargeRepository.save(rechargeEntity);

            publishPaymentCompleted(transaction);

            return RechargeResponse.builder()
                    .txnRef(txnRef)
                    .status("COMPLETED")
                    .operatorRef(result.operatorRef())
                    .amount(amount)
                    .createdAt(transaction.getTimestamp())
                    .build();
        } else {
            // Operator declined -> reverse transfer from OPERATOR to user wallet
            log.warn("Recharge operator declined for {}, initiating refund reversal", txnRef);
            try {
                InternalTransferRequest reverseRequest = InternalTransferRequest.builder()
                        .txnRef(txnRef + "-REV")
                        .fromSystemWallet("OPERATOR")
                        .toUserId(userId)
                        .amount(amount)
                        .description("Recharge reversal: operator declined")
                        .build();
                walletClient.internalTransfer(internalApiKey, reverseRequest);
            } catch (Exception ex) {
                log.error("Recharge reversal failed for {}: {}", txnRef, ex.getMessage());
            }

            transaction.setStatus("FAILED");
            transaction.setFailureReason("OPERATOR_DECLINED");
            transaction = transactionRepository.save(transaction);

            return RechargeResponse.builder()
                    .txnRef(txnRef)
                    .status("FAILED")
                    .operatorRef(null)
                    .amount(amount)
                    .createdAt(transaction.getTimestamp())
                    .build();
        }
    }

    public List<TransactionDto> getAllTransactionsByUser(int page, int size) {
        String userId = UserContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("UNAUTHORIZED", "User identity missing");
        }
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        Page<Transaction> paged = transactionRepository.findBySenderOrReceiver(userId, pageable);
        return paged.getContent().stream().map(this::convertTransactionToDto).toList();
    }

    public TransactionDto getTransactionById(Long id, String txnRef) {
        String userId = UserContextHolder.getUserId();
        Transaction transaction;
        if (txnRef != null && !txnRef.trim().isEmpty()) {
            transaction = transactionRepository.findByTxnRef(txnRef)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with txnRef: " + txnRef));
        } else if (id != null) {
            transaction = transactionRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
        } else {
            throw new BusinessException("VALIDATION_ERROR", "Either id or txnRef must be provided");
        }

        // Caller must be sender or receiver unless admin
        String role = UserContextHolder.getRole();
        if (!"ADMIN".equals(role) && !userId.equals(transaction.getUserId()) && !userId.equals(transaction.getReceiverUserId())) {
            throw new BusinessException("FORBIDDEN", "Not authorized to view this transaction");
        }

        return convertTransactionToDto(transaction);
    }

    public List<TransactionDto> getAdminAllTransactions(int page, int size, String status, String type, String from, String to) {
        validateAdmin();
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        List<Transaction> list;
        if (status != null && !status.isEmpty()) {
            list = transactionRepository.findByStatus(status.toUpperCase());
        } else {
            list = transactionRepository.findAll();
        }
        return list.stream()
                .skip((long) page * size)
                .limit(size)
                .map(this::convertTransactionToDto)
                .toList();
    }

    public TransactionSummaryResponse getAdminSummary(String from, String to) {
        validateAdmin();
        List<Transaction> all = transactionRepository.findAll();

        long totalTxns = all.size();
        BigDecimal totalVolume = BigDecimal.ZERO;
        Map<String, Long> countByType = new HashMap<>();
        Map<String, BigDecimal> volumeByType = new HashMap<>();

        for (Transaction t : all) {
            String type = t.getType() != null ? t.getType() : "OTHER";
            countByType.put(type, countByType.getOrDefault(type, 0L) + 1);

            if ("COMPLETED".equals(t.getStatus())) {
                totalVolume = totalVolume.add(t.getAmount());
                volumeByType.put(type, volumeByType.getOrDefault(type, BigDecimal.ZERO).add(t.getAmount()));
            }
        }

        List<TransactionDto> recent = all.stream()
                .sorted(Comparator.comparing(Transaction::getTimestamp).reversed())
                .limit(5)
                .map(this::convertTransactionToDto)
                .toList();

        return TransactionSummaryResponse.builder()
                .date(LocalDate.now(ZoneId.of(configuredTimezone)))
                .totalTransactions(totalTxns)
                .totalVolume(totalVolume)
                .countByType(countByType)
                .volumeByType(volumeByType)
                .recentTransactions(recent)
                .build();
    }

    // Helper methods

    private void validateSenderKyc() {
        String kycStatus = UserContextHolder.getKycStatus();
        if (!"APPROVED".equalsIgnoreCase(kycStatus)) {
            throw new BusinessException("KYC_NOT_APPROVED", "Your KYC is not approved");
        }
    }

    private void validateAdmin() {
        String role = UserContextHolder.getRole();
        if (role == null || (!"ADMIN".equalsIgnoreCase(role) && !"ROLE_ADMIN".equalsIgnoreCase(role) && !role.contains("ADMIN"))) {
            throw new BusinessException("FORBIDDEN", "Admin access required");
        }
    }

    private BigDecimal validateAndScaleAmount(BigDecimal amount, BigDecimal min, BigDecimal max) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("VALIDATION_ERROR", "Amount must be greater than zero");
        }
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        if (scaled.compareTo(min) < 0) {
            throw new BusinessException("LIMIT_EXCEEDED", "Amount must be at least " + min);
        }
        if (scaled.compareTo(max) > 0) {
            throw new BusinessException("LIMIT_EXCEEDED", "Amount exceeds limit of " + max);
        }
        return scaled;
    }

    private void validateDailyLimits(String userId, BigDecimal amount) {
        ZoneId zone = ZoneId.of(configuredTimezone);
        LocalDateTime startOfDay = LocalDate.now(zone).atStartOfDay();
        BigDecimal todayTotal = transactionRepository.sumCompletedAmountSince(userId, startOfDay);

        if (todayTotal != null && todayTotal.add(amount).compareTo(dailyTotalLimit) > 0) {
            throw new BusinessException("LIMIT_EXCEEDED", "Daily transaction volume limit exceeded: " + dailyTotalLimit);
        }
    }

    private UserLookupDto lookupReceiverByUpi(String upiId) {
        try {
            ApiResponse<UserLookupDto> resp = authClient.lookupUserByUpi(upiId, internalApiKey);
            if (resp != null && resp.isSuccess() && resp.getData() != null) {
                return resp.getData();
            }
            throw new BusinessException("NOT_FOUND", "Receiver UPI ID not found: " + upiId);
        } catch (FeignException.NotFound ex) {
            throw new BusinessException("NOT_FOUND", "Receiver UPI ID not found: " + upiId);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception ex) {
            log.error("Error looking up receiver by UPI {}: {}", upiId, ex.getMessage());
            throw new BusinessException("SERVICE_UNAVAILABLE", "Auth service unavailable: " + ex.getMessage());
        }
    }

    private String generateTxnRef() {
        ZoneId zone = ZoneId.of(configuredTimezone);
        String datePart = LocalDate.now(zone).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        for (int i = 0; i < 5; i++) {
            String randomPart = String.format("%06d", new Random().nextInt(999999) + 1);
            String candidate = "TXN" + datePart + randomPart;
            if (transactionRepository.findByTxnRef(candidate).isEmpty()) {
                return candidate;
            }
        }
        return "TXN" + datePart + String.format("%06d", new Random().nextInt(999999) + 1);
    }

    private String computeRequestHash(Object request) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(objectMapper.writeValueAsBytes(request));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private Map<String, String> parseQrPayload(String payload) {
        Map<String, String> params = new HashMap<>();
        int qIdx = payload.indexOf('?');
        if (qIdx < 0) return params;
        String query = payload.substring(qIdx + 1);
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                params.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        return params;
    }

    public void publishPaymentCompleted(Transaction transaction) {
        if (transaction.isEventPublished()) {
            return;
        }
        try {
            PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .txnRef(transaction.getTxnRef())
                    .userId(transaction.getUserId())
                    .type(transaction.getType())
                    .amount(transaction.getAmount())
                    .currency("INR")
                    .note(transaction.getDescription())
                    .requestedCategory(transaction.getRequestedCategory())
                    .completedAt(transaction.getTimestamp() != null ? transaction.getTimestamp() : LocalDateTime.now())
                    .build();

            rabbitTemplate.convertAndSend(RabbitMQConfig.PAYMENT_EXCHANGE, RabbitMQConfig.PAYMENT_COMPLETED_ROUTING_KEY, event);
            transaction.setEventPublished(true);
            transactionRepository.save(transaction);
            log.info("Successfully published payment.completed event for txnRef: {}", transaction.getTxnRef());
        } catch (Exception ex) {
            log.error("Failed to publish payment.completed event for {}: {}", transaction.getTxnRef(), ex.getMessage());
        }
    }

    @Scheduled(fixedRate = 60000)
    public void retryUnpublishedEvents() {
        List<Transaction> unpublished = transactionRepository.findCompletedNotPublished("COMPLETED");
        if (unpublished == null || unpublished.isEmpty()) {
            return;
        }
        log.info("Found {} unpublished completed transactions, republishing...", unpublished.size());
        for (Transaction transaction : unpublished) {
            publishPaymentCompleted(transaction);
        }
    }

    private TransferResponse convertToTransferResponse(Transaction t, BigDecimal balanceAfter) {
        return TransferResponse.builder()
                .txnRef(t.getTxnRef())
                .status(t.getStatus())
                .type(t.getType())
                .amount(t.getAmount())
                .category(t.getRequestedCategory())
                .receiverUpiId(t.getReceiverUpiId())
                .balanceAfter(balanceAfter)
                .failureReason(t.getFailureReason())
                .createdAt(t.getTimestamp())
                .build();
    }

    private TransactionDto convertTransactionToDto(Transaction t) {
        return TransactionDto.builder()
                .id(t.getId())
                .txnRef(t.getTxnRef())
                .userId(t.getUserId())
                .receiverUserId(t.getReceiverUserId())
                .receiverUpiId(t.getReceiverUpiId())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .type(t.getType())
                .status(t.getStatus())
                .failureReason(t.getFailureReason())
                .requestedCategory(t.getRequestedCategory())
                .description(t.getDescription())
                .timestamp(t.getTimestamp())
                .build();
    }
}
