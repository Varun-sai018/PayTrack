package com.paytrack.transaction.service;

import com.paytrack.auth.util.JwtUtil;
import com.paytrack.auth.entity.User;
import com.paytrack.auth.entity.KycRequest;
import com.paytrack.auth.entity.KycRequest.KycRequestStatus;
import com.paytrack.auth.entity.User.KycStatus;
import com.paytrack.auth.entity.User.AccountStatus;
import com.paytrack.auth.repository.UserRepository;
import com.paytrack.transaction.client.WalletClient;
import com.paytrack.transaction.event.PaymentCompletedEvent;
import com.paytrack.transaction.dto.TransactionDto;
import com.paytrack.transaction.dto.TransferRequest;
import com.paytrack.transaction.dto.TransferResponse;
import com.paytrack.transaction.entity.Transaction;
import com.paytrack.transaction.entity.UserContextHolder;
import com.paytrack.transaction.exception.BusinessException;
import com.paytrack.transaction.exception.ResourceNotFoundException;
import com.paytrack.transaction.repository.TransactionRepository;
import com.paytrack.transaction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletClient walletClient;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final RabbitTemplate rabbitTemplate;

    private static final String DAILY_TOTAL_LIMIT = "25000";
    private static final String MAX_TRANSACTION_LIMIT = "10000";
    private static final String MIN_AMOUNT = "1.00";

    @Transactional
    public TransactionDto createTransaction(TransactionDto transactionDto) {
        String userId = UserContextHolder.getUserId();

        String type = transactionDto.getType() != null ? transactionDto.getType().toUpperCase() : "";

        if ("DEBIT".equals(type) || "CREDIT".equals(type) || "TRANSFER".equals(type)) {
            // Generic DEBIT, CREDIT, TRANSFER is removed per M-08 scope
            // Users must use the specific endpoints below
            throw new BusinessException("Generic transaction type not allowed. Use /transfer, /qr/pay, or /recharge");
        }

        if ("P2P".equals(type) || "QR".equals(type) || "RECHARGE".equals(type)) {
            return handleSpecialTransaction(type, transactionDto);
        }

        throw new BusinessException("Invalid transaction type: " + type);
    }

    @Transactional
    public TransactionDto handleSpecialTransaction(String type, TransactionDto transactionDto) {
        String userId = UserContextHolder.getUserId();
        BigDecimal amount = transactionDto.getAmount();

        switch (type) {
            case "P2P":
                return handleP2pTransfer(transactionDto);
            case "QR":
                return handleQrPay(transactionDto);
            case "RECHARGE":
                return handleRecharge(transactionDto);
            default:
                throw new BusinessException("Unsupported transaction type: " + type);
        }
    }

    @Transactional
    public TransactionDto handleP2pTransfer(TransactionDto transactionDto) {
        String userId = UserContextHolder.getUserId();
        String receiverUpiId = transactionDto.getReceiverUpiId();
        BigDecimal amount = transactionDto.getAmount();
        String note = transactionDto.getNote();
        String category = transactionDto.getCategory();

        // 1. Validate amount
        validateAmount(amount);

        // 2. Validate sender is not receiver
        User sender = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found"));

        // 3. Lookup receiver by UPI ID (internal call)
        User receiver = lookupReceiverByUpi(receiverUpiId);

        // 4. Check KYC status - receiver must be APPROVED
        if (receiver.getKycStatus() != AccountStatus.APPROVED) {
            throw new BusinessException("KYC_NOT_APPROVED", "Receiver KYC is not approved");
        }

        // 5. Check per-transaction limit
        validateTransactionLimit(amount);

        // 6. Check daily limits
        validateDailyLimits(userId, amount);

        // 7. Check idempotency - if same (user_id, idempotency_key) exists with same request hash, return existing
        if (transactionDto.getIdempotencyKey() != null) {
            Transaction existing = transactionRepository.findByUserIdAndIdempotencyKey(userId, transactionDto.getIdempotencyKey());
            if (existing != null) {
                // Same key with same request hash - return the original result
                if (existing.getRequestHash() != null && existing.getRequestHash().equals(transactionDto.getRequestHash())) {
                    return convertToDto(existing);
                }
                // Same key with different body - 409 IDEMPOTENCY_KEY_REUSED
                throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "Idempotency key already used with different request");
            }
        }

        // 8. Save PENDING with a txn_ref
        String txnRef = generateTxnRef();
        Transaction transaction = Transaction.builder()
                .userId(userId)
                .txnRef(txnRef)
                .idempotencyKey(transactionDto.getIdempotencyKey())
                .requestHash(transactionDto.getRequestHash())
                .receiverUserId(receiver.getUserId())
                .receiverUpiId(receiverUpiId)
                .amount(amount)
                .type("P2P")
                .status("PENDING")
                .build();

        transactionRepository.save(transaction);

        // 9. Feign to Wallet internal transfer (timeout 3s, one retry on connect or timeout only, circuit breaker)
        try {
            String description = "P2P transfer to " + receiver.getFullName();
            TransferResponse transferResponse = walletClient.internalTransfer(
                    txnRef,
                    userId,
                    receiver.getUserId(),
                    amount,
                    description
            );

            // 10. Mark COMPLETED or FAILED
            if (transferResponse.getStatus().equals("COMPLETED")) {
                transaction.setStatus("COMPLETED");
                transaction.setFailureReason(null);
            } else {
                transaction.setStatus("FAILED");
                transaction.setFailureReason(transferResponse.getFailureReason());
            }
            transaction = transactionRepository.save(transaction);

            // 11. After commit, publish payment.completed to RabbitMQ and set event_published=true
            publishPaymentCompleted(transaction);

            return convertToDto(transaction);

        } catch (Exception e) {
            // Circuit open or timeout after retry: FAILED WALLET_UNAVAILABLE
            transaction.setStatus("FAILED");
            transaction.setFailureReason("WALLET_UNAVAILABLE");
            transaction = transactionRepository.save(transaction);

            // After commit, publish payment.completed (with event_published=false)
            publishPaymentCompleted(transaction);

            throw new BusinessException("SERVICE_UNAVAILABLE", "Wallet service unavailable: " + e.getMessage());
        }
    }

    @Transactional
    public TransactionDto handleQrPay(TransactionDto transactionDto) {
        String userId = UserContextHolder.getUserId();
        BigDecimal amount = transactionDto.getAmount();
        String note = transactionDto.getNote();
        String category = transactionDto.getCategory();

        // QR payload validation: upi://pay?pa=<upiId>&pn=<name>&am=<amount>&cu=INR
        String qrPayload = transactionDto.getNote(); // note field contains QR payload

        if (qrPayload == null || !qrPayload.startsWith("upi://pay?")) {
            throw new BusinessException("INVALID_SIGNATURE", "Invalid QR payload format");
        }

        // Parse QR payload to extract UPI ID and amount
        String pa = extractParameter(qrPayload, "pa");
        String am = extractParameter(qrPayload, "am");

        if (pa == null) {
            throw new BusinessException("INVALID_SIGNATURE", "UPI ID (pa) missing from QR payload");
        }

        // Validate CU = INR
        String cu = extractParameter(qrPayload, "cu");
        if (cu == null || !cu.equals("INR")) {
            throw new BusinessException("INVALID_SIGNATURE", "Currency must be INR");
        }

        // If am is present, validate it matches or is absent
        if (am != null && !am.isEmpty()) {
            // If amount in QR is present, it must match request amount or be absent
            // Per spec: if am is present the request amount must be absent or equal
            if (!am.equals(amount.toString())) {
                throw new BusinessException("INVALID_SIGNATURE", "QR amount must match request amount");
            }
        }

        // Lookup receiver by UPI ID
        User receiver = lookupReceiverByUpi(pa);

        // Check KYC
        if (receiver.getKycStatus() != AccountStatus.APPROVED) {
            throw new BusinessException("KYC_NOT_APPROVED", "Receiver KYC is not approved");
        }

        // Check self-payment
        if (receiver.getUserId().equals(userId)) {
            throw new BusinessException("CANNOT_PAY_YOURSELF", "Cannot pay yourself");
        }

        // Check amount limits
        validateAmount(amount);
        validateTransactionLimit(amount);
        validateDailyLimits(userId, amount);

        // Check idempotency
        String txnRef = generateTxnRef();
        if (transactionDto.getIdempotencyKey() != null) {
            Transaction existing = transactionRepository.findByUserIdAndIdempotencyKey(userId, transactionDto.getIdempotencyKey());
            if (existing != null) {
                if (existing.getRequestHash() != null && existing.getRequestHash().equals(transactionDto.getRequestHash())) {
                    return convertToDto(existing);
                }
                throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "Idempotency key already used");
            }
        }

        // Save PENDING
        Transaction transaction = Transaction.builder()
                .userId(userId)
                .txnRef(txnRef)
                .idempotencyKey(transactionDto.getIdempotencyKey())
                .requestHash(transactionDto.getRequestHash())
                .receiverUserId(receiver.getUserId())
                .receiverUpiId(pa)
                .amount(amount)
                .type("QR")
                .status("PENDING")
                .build();

        transactionRepository.save(transaction);

        // Feign to Wallet internal transfer
        try {
            String description = "QR payment to " + receiver.getFullName();
            TransferResponse transferResponse = walletClient.internalTransfer(
                    txnRef,
                    userId,
                    receiver.getUserId(),
                    amount,
                    description
            );

            // Mark status
            if (transferResponse.getStatus().equals("COMPLETED")) {
                transaction.setStatus("COMPLETED");
                transaction.setFailureReason(null);
            } else {
                transaction.setStatus("FAILED");
                transaction.setFailureReason(transferResponse.getFailureReason());
            }
            transaction = transactionRepository.save(transaction);

            // Publish payment.completed
            publishPaymentCompleted(transaction);

            return convertToDto(transaction);

        } catch (Exception e) {
            transaction.setStatus("FAILED");
            transaction.setFailureReason("WALLET_UNAVAILABLE");
            transaction = transactionRepository.save(transaction);
            publishPaymentCompleted(transaction);
            throw new BusinessException("SERVICE_UNAVAILABLE", "Wallet service unavailable");
        }
    }

    @Transactional
    public QrGenerateResponse qrGenerate(String userId, QrGenerateRequest request) {
        User sender = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Generate QR payload: upi://pay?pa=<upiId>&pn=<name>&am=<amount>&cu=INR
        String upiId = sender.getUpiId();
        String name = sender.getFullName();

        StringBuilder qrPayload = new StringBuilder("upi://pay?pa=");
        qrPayload.append(upiId);
        qrPayload.append("&pn=");
        qrPayload.append(name);

        if (request.getAmount() != null) {
            qrPayload.append("&am=");
            qrPayload.append(request.getAmount());
        }

        qrPayload.append("&cu=INR");

        return QrGenerateResponse.builder()
                .qrPayload(qrPayload.toString())
                .upiId(upiId)
                .receiverName(name)
                .amount(request.getAmount())
                .build();
    }

    @Transactional
    public TransferResponse qrPay(String userId, String idempotencyKey, QrPayRequest request) {
        String senderId = userId;
        BigDecimal amount = request.getAmount();
        String qrPayload = request.getQrPayload();
        String note = request.getNote();
        String category = request.getCategory();

        // 1. Validate QR payload format
        if (qrPayload == null || !qrPayload.startsWith("upi://pay?")) {
            throw new BusinessException("INVALID_SIGNATURE", "Invalid QR payload format");
        }

        // Parse QR payload
        String pa = extractParameter(qrPayload, "pa");
        String am = extractParameter(qrPayload, "am");
        String cu = extractParameter(qrPayload, "cu");

        if (pa == null) {
            throw new BusinessException("INVALID_SIGNATURE", "UPI ID (pa) missing from QR payload");
        }

        // Validate CU = INR
        if (cu == null || !cu.equals("INR")) {
            throw new BusinessException("INVALID_SIGNATURE", "Currency must be INR");
        }

        // If am is present in QR, validate it matches or is absent
        if (am != null && !am.isEmpty()) {
            if (!am.equals(amount.toString())) {
                throw new BusinessException("INVALID_SIGNATURE", "QR amount must match request amount");
            }
        }

        // Lookup receiver by UPI ID from QR
        User receiver = lookupReceiverByUpi(pa);

        // 2. Check KYC status
        if (receiver.getAccountStatus() == User.AccountStatus.BLOCKED) {
            throw new BusinessException("ACCOUNT_BLOCKED", "Receiver account is blocked");
        }
        if (receiver.getKycStatus() != User.KycStatus.APPROVED) {
            throw new BusinessException("KYC_NOT_APPROVED", "Receiver KYC is not approved");
        }

        // 3. Check self-payment
        if (receiver.getUserId().equals(senderId)) {
            throw new BusinessException("CANNOT_PAY_YOURSELF", "Cannot pay yourself");
        }

        // 4. Check amount limits
        validateAmount(amount);
        validateTransactionLimit(amount);
        validateDailyLimits(senderId, amount);

        // 5. Check idempotency
        String txnRef = generateTxnRef();
        if (idempotencyKey != null) {
            Transaction existing = transactionRepository.findByUserIdAndIdempotencyKey(senderId, idempotencyKey);
            if (existing != null) {
                if (existing.getRequestHash() != null && existing.getRequestHash().equals(request.getRequestHash())) {
                    return convertToDto(existing);
                }
                throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "Idempotency key already used");
            }
        }

        // 6. Save PENDING with txn_ref
        Transaction transaction = Transaction.builder()
                .userId(senderId)
                .txnRef(txnRef)
                .idempotencyKey(idempotencyKey)
                .requestHash(request.getRequestHash())
                .receiverUserId(receiver.getUserId())
                .receiverUpiId(pa)
                .amount(amount)
                .type("QR")
                .status("PENDING")
                .build();

        transactionRepository.save(transaction);

        // 7. Feign to Wallet internal transfer
        try {
            String description = "QR payment to " + receiver.getFullName();
            TransferResponse transferResponse = walletClient.internalTransfer(
                    txnRef,
                    senderId,
                    receiver.getUserId(),
                    amount,
                    description
            );

            // Mark status
            if (transferResponse.getStatus().equals("COMPLETED")) {
                transaction.setStatus("COMPLETED");
                transaction.setFailureReason(null);
            } else {
                transaction.setStatus("FAILED");
                transaction.setFailureReason(transferResponse.getFailureReason());
            }
            transaction = transactionRepository.save(transaction);

            // 8. Publish payment.completed
            publishPaymentCompleted(transaction);

            return convertToDto(transaction);

        } catch (Exception e) {
            // Circuit open or timeout after retry: FAILED WALLET_UNAVAILABLE
            transaction.setStatus("FAILED");
            transaction.setFailureReason("WALLET_UNAVAILABLE");
            transaction = transactionRepository.save(transaction);

            // Publish payment.completed (with event_published=false for retry)
            publishPaymentCompleted(transaction);

            throw new BusinessException("SERVICE_UNAVAILABLE", "Wallet service unavailable: " + e.getMessage());
        }
    }

    @Transactional
    public RechargeResponse recharge(String userId, String idempotencyKey, RechargeRequest request) {
        String mobileNumber = request.getMobileNumber();
        String operator = request.getOperator();
        BigDecimal amount = request.getAmount();
        String category = request.getCategory();

        // Validate mobile number (10 digits)
        if (mobileNumber == null || !mobileNumber.matches("\\d{10}")) {
            throw new BusinessException("VALIDATION_ERROR", "Invalid mobile number format");
        }

        // Validate operator
        if (operator == null) {
            throw new BusinessException("VALIDATION_ERROR", "Operator is required");
        }
        operator = operator.toUpperCase();
        if (!operator.matches("(AIRTEL|JIO|VI|BSNL)")) {
            throw new BusinessException("VALIDATION_ERROR", "Invalid operator: " + operator);
        }

        // Validate amount range
        validateRechargeAmount(amount);

        // Check KYC
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getKycStatus() != AccountStatus.APPROVED) {
            throw new BusinessException("KYC_NOT_APPROVED", "User KYC is not approved");
        }

        // Check self-payment (cannot pay yourself)
        // For recharge, we debit the user's wallet and credit the OPERATOR system wallet

        // Check daily limits
        validateDailyLimits(userId, amount);

        // Check idempotency
        String txnRef = generateTxnRef();
        if (idempotencyKey != null) {
            Transaction existing = transactionRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existing != null) {
                if (existing.getRequestHash() != null && existing.getRequestHash().equals(request.getRequestHash())) {
                    // Return the original result if same request hash
                    return RechargeResponse.builder()
                            .txnRef(existing.getTxnRef())
                            .status(existing.getStatus())
                            .operatorRef(null)
                            .amount(existing.getAmount())
                            .createdAt(existing.getTimestamp())
                            .build();
                }
                throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "Idempotency key already used");
            }
        }

        // Save PENDING
        Transaction transaction = Transaction.builder()
                .userId(userId)
                .txnRef(txnRef)
                .idempotencyKey(idempotencyKey)
                .requestHash(request.getRequestHash())
                .receiverUserId(null) // No receiver for recharge
                .receiverUpiId(null)
                .amount(amount)
                .type("RECHARGE")
                .status("PENDING")
                .build();

        transactionRepository.save(transaction);

        // Debit user wallet and credit OPERATOR system wallet
        // Per spec: save PENDING, debit the user wallet to the OPERATOR system wallet
        try {
            // First, debit user wallet (transfer from user to OPERATOR)
            String debitTxnRef = txnRef + "-DEBIT";
            TransferResponse debitResponse = walletClient.internalTransfer(
                    debitTxnRef,
                    userId,
                    null, // to system wallet OPERATOR
                    amount,
                    "Recharge: debit user wallet to OPERATOR"
            );

            // Call the RechargeOperator stub
            // If stub fails, reverse with internal transfer from OPERATOR back to user using txn_ref <ref>-REV
            boolean rechargeSuccess = callRechargeOperatorStub(mobileNumber, operator, amount);

            if (rechargeSuccess) {
                // Mark COMPLETED
                transaction.setStatus("COMPLETED");
                transaction.setFailureReason(null);
                transaction = transactionRepository.save(transaction);

                // Publish payment.completed
                publishPaymentCompleted(transaction);

                // Generate operator ref
                String operatorRef = "OP" + String.format("%04d", new java.util.Random().nextInt(9999));

                return RechargeResponse.builder()
                        .txnRef(txnRef)
                        .status("COMPLETED")
                        .operatorRef(operatorRef)
                        .amount(amount)
                        .createdAt(transaction.getTimestamp())
                        .build();
            } else {
                // Reverse: internal transfer from OPERATOR back to user using txn_ref <ref>-REV
                String reverseTxnRef = txnRef + "-REV";
                walletClient.internalTransfer(
                        reverseTxnRef,
                        null, // from system wallet OPERATOR
                        userId,
                        amount,
                        "Recharge reversal: operator declined, reversing to user"
                );

                transaction.setStatus("FAILED");
                transaction.setFailureReason("OPERATOR_DECLINED");
                transaction = transactionRepository.save(transaction);

                // Publish payment.completed with FAILED status
                publishPaymentCompleted(transaction);

                return RechargeResponse.builder()
                        .txnRef(txnRef)
                        .status("FAILED")
                        .operatorRef(null)
                        .amount(amount)
                        .createdAt(transaction.getTimestamp())
                        .build();
            }

        } catch (Exception e) {
            // If wallet operations fail, attempt reversal
            if (transaction.getStatus() == null || transaction.getStatus().equals("PENDING")) {
                String reverseTxnRef = txnRef + "-REV";
                try {
                    walletClient.internalTransfer(
                            reverseTxnRef,
                            null,
                            userId,
                            amount,
                            "Recharge reversal: wallet error, reversing to user"
                    );
                } catch (Exception rollbackEx) {
                    log.error("Recharge reversal failed: {}", rollbackEx.getMessage());
                }

                transaction.setStatus("FAILED");
                transaction.setFailureReason("SERVICE_UNAVAILABLE");
                transaction = transactionRepository.save(transaction);

                publishPaymentCompleted(transaction);
            }
            throw new BusinessException("SERVICE_UNAVAILABLE", "Recharge failed: " + e.getMessage());
        }
    }

    // Helper methods

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("VALIDATION_ERROR", "Amount must be positive");
        }
        // Check min-amount config
        BigDecimal minAmount = new BigDecimal(MIN_AMOUNT);
        if (amount.compareTo(minAmount) < 0) {
            throw new BusinessException("VALIDATION_ERROR", "Amount must be at least " + MIN_AMOUNT);
        }
    }

    private void validateTransactionLimit(BigDecimal amount) {
        BigDecimal maxTxn = new BigDecimal(MAX_TRANSACTION_LIMIT);
        if (amount.compareTo(maxTxn) > 0) {
            throw new BusinessException("LIMIT_EXCEEDED", "Amount exceeds maximum transaction limit of " + MAX_TRANSACTION_LIMIT);
        }
    }

    private void validateDailyLimits(String userId, BigDecimal amount) {
        // Sum of COMPLETED transactions since start of day in Asia/Kolkata timezone
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        Long completedCount = transactionRepository.countCompletedToday(userId, today);

        // Get daily total limit from config (default 25000)
        BigDecimal dailyTotalLimit = new BigDecimal(DAILY_TOTAL_LIMIT);

        // Calculate total completed amount for today
        BigDecimal todayTotal = transactionRepository.sumCompletedAmountToday(userId, today);

        if (todayTotal != null && todayTotal.add(amount).compareTo(dailyTotalLimit) > 0) {
            throw new BusinessException("LIMIT_EXCEEDED", "Daily total would exceed limit of " + DAILY_TOTAL_LIMIT);
        }
    }

    private void validateRechargeAmount(BigDecimal amount) {
        BigDecimal minRecharge = new BigDecimal("10");
        BigDecimal maxRecharge = new BigDecimal("2000");

        if (amount.compareTo(minRecharge) < 0) {
            throw new BusinessException("VALIDATION_ERROR", "Recharge amount must be at least " + minRecharge);
        }
        if (amount.compareTo(maxRecharge) > 0) {
            throw new BusinessException("VALIDATION_ERROR", "Recharge amount must not exceed " + maxRecharge);
        }
    }

    private User lookupReceiverByUpi(String upiId) {
        User user = userRepository.findByUpiId(upiId)
                .orElseThrow(() -> new ResourceNotFoundException("User with UPI ID not found: " + upiId));

        // Check account status
        if (user.getAccountStatus() == AccountStatus.BLOCKED) {
            throw new BusinessException("ACCOUNT_BLOCKED", "Receiver account is blocked");
        }

        return user;
    }

    private String generateTxnRef() {
        String datePart = LocalDate.now(ZoneId.of("Asia/Kolkata")).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = String.format("%06d", new java.util.Random().nextInt(999999) + 1);
        return "TXN" + datePart + randomPart;
    }

    private String extractParameter(String qrPayload, String paramName) {
        // Simple extraction: upi://pay?pa=<upiId>&pn=<name>&am=<amount>&cu=INR
        String searchFor = paramName + "=";
        int idx = qrPayload.indexOf(searchFor);
        if (idx < 0) {
            return null;
        }
        int start = idx + searchFor.length();
        int end = qrPayload.indexOf("&", start);
        if (end < 0) {
            end = qrPayload.length();
        }
        return qrPayload.substring(start, end);
    }

    private boolean callRechargeOperatorStub(String mobileNumber, String operator, BigDecimal amount) {
        // DummyRechargeOperator stub logic:
        // SUCCESS with operatorRef like OP + random digits
        // A mobile number ending 0000 returns FAILED OPERATOR_DECLINED
        if (mobileNumber.endsWith("0000")) {
            log.info("Recharge operator declined for mobile number ending 0000");
            return false;
        }
        // Simulate success
        String operatorRef = "OP" + String.format("%04d", new java.util.Random().nextInt(9999));
        log.info("Recharge operator succeeded, operatorRef: {}", operatorRef);
        return true;
    }

    private void publishPaymentCompleted(Transaction transaction) {
        // Check if already published
        if (transaction.isEventPublished()) {
            log.info("Payment already published for txnRef: {}", transaction.getTxnRef());
            return;
        }

        // Create PaymentCompletedEvent
        String type = transaction.getType();
        String eventType = "P2P"; // default
        if (type.equals("QR")) {
            eventType = "QR";
        } else if (type.equals("RECHARGE")) {
            eventType = "RECHARGE";
        }

        // Build event
        String userId = transaction.getUserId();
        String txnRef = transaction.getTxnRef();
        BigDecimal amount = transaction.getAmount();
        String note = transaction.getNote();
        String requestedCategory = transaction.getRequestedCategory();

        // Create the event payload
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .eventId(java.util.UUID.randomUUID())
                .txnRef(txnRef)
                .userId(userId)
                .type(eventType)
                .amount(amount)
                .currency("INR")
                .note(note)
                .requestedCategory(requestedCategory)
                .completedAt(transaction.getTimestamp())
                .build();

        // Publish to RabbitMQ after commit (using applicationEventPublisher or direct)
        // Since we're in a transaction, we should publish after commit
        // For simplicity, we'll publish synchronously but set the flag only after successful publish
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.PAYMENT_EXCHANGE,
                    RabbitMQConfig.PAYMENT_ROUTING_KEY,
                    event
            );
            log.info("Published payment.completed event for txnRef: {}", txnRef);

            // Set event_published flag after successful publish
            transaction.setEventPublished(true);
            transactionRepository.save(transaction);

        } catch (Exception e) {
            log.error("Failed to publish payment.completed event for txnRef: {}: {}", txnRef, e.getMessage());
            // Keep event_published = false for retry job
        }
    }

    // Scheduled retry job - runs every 60 seconds
    @Scheduled(fixedRate = 60000) // 60 seconds
    public void retryUnpublishedEvents() {
        log.info("Checking for unpublished payment.completed events...");

        // Find all COMPLETED transactions with event_published=false
        List<Transaction> unpublished = transactionRepository.findCompletedNotPublished("COMPLETED");

        if (unpublished.isEmpty()) {
            log.info("No unpublished events found");
            return;
        }

        log.info("Found {} unpublished events, republishing...", unpublished.size());

        for (Transaction transaction : unpublished) {
            log.info("Republishing payment.completed for txnRef: {}", transaction.getTxnRef());
            publishPaymentCompleted(transaction);
        }
    }

    private TransactionDto convertToDto(Transaction transaction) {
        return TransactionDto.builder()
                .id(transaction.getId())
                .userId(transaction.getUserId())
                .transactionId(transaction.getTransactionId())
                .walletId(transaction.getWalletId())
                .description(transaction.getDescription())
                .amount(transaction.getAmount())
                .type(transaction.getType())
                .timestamp(transaction.getTimestamp())
                .status(transaction.getStatus())
                .receiverUpiId(transaction.getReceiverUpiId())
                .receiverUserId(transaction.getReceiverUserId())
                .note(transaction.getNote())
                .category(transaction.getRequestedCategory())
                .idempotencyKey(transaction.getIdempotencyKey())
                .txnRef(transaction.getTxnRef())
                .build();
    }
}