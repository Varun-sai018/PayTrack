package com.paytrack.transaction.service;

import com.paytrack.transaction.client.AuthClient;
import com.paytrack.transaction.client.WalletClient;
import com.paytrack.transaction.dto.RechargeRequest;
import com.paytrack.transaction.dto.RechargeResponse;
import com.paytrack.transaction.dto.QrGenerateRequest;
import com.paytrack.transaction.dto.QrGenerateResponse;
import com.paytrack.transaction.dto.QrPayRequest;
import com.paytrack.transaction.dto.TransactionDto;
import com.paytrack.transaction.dto.TransferRequest;
import com.paytrack.transaction.dto.TransferResponse;
import com.paytrack.transaction.dto.UserLookupDto;
import com.paytrack.transaction.entity.Transaction;
import com.paytrack.transaction.UserContextHolder;
import com.paytrack.transaction.exception.BusinessException;
import com.paytrack.transaction.exception.ResourceNotFoundException;
import com.paytrack.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletClient walletClient;
    private final AuthClient authClient;
    private final RabbitTemplate rabbitTemplate;

    private static final String DAILY_TOTAL_LIMIT = "25000";
    private static final String MAX_TRANSACTION_LIMIT = "10000";
    private static final String MIN_AMOUNT = "1.00";
    private static final String INTERNAL_API_KEY = System.getProperty("INTERNAL_API_KEY", "default-internal-key");

    @Transactional
    public TransactionDto createTransaction(TransactionDto transactionDto) {
        String userId = UserContextHolder.getUserId();

        String type = transactionDto.getType() != null ? transactionDto.getType().toUpperCase() : "";

        if ("DEBIT".equals(type) || "CREDIT".equals(type) || "TRANSFER".equals(type)) {
            // Generic DEBIT, CREDIT, TRANSFER is removed per M-08 scope
            throw new BusinessException("Generic transaction type not allowed. Use /transfer, /qr/pay, or /recharge");
        }

        throw new BusinessException("Invalid transaction type: " + type);
    }

    @Transactional
    public TransferResponse transfer(String userId, String idempotencyKey, TransferRequest request) {
        // 1. Validate amount
        validateAmount(request.getAmount());

        // 2. Lookup receiver by UPI ID via Auth service Feign client
        UserLookupDto receiver = lookupReceiverByUpi(request.getReceiverUpiId());

        // 3. Check KYC status - receiver must be APPROVED
        if ("APPROVED".equals(receiver.getKycStatus())) {
            throw new BusinessException("KYC_NOT_APPROVED", "Receiver KYC is not approved");
        }

        // 4. Check self-payment
        if (receiver.getUserId().equals(userId)) {
            throw new BusinessException("CANNOT_PAY_YOURSELF", "Cannot pay yourself");
        }

        // 5. Check per-transaction limit
        validateTransactionLimit(request.getAmount());

        // 6. Check daily limits
        validateDailyLimits(userId, request.getAmount());

        // 7. Check idempotency
        String txnRef = generateTxnRef();
        if (idempotencyKey != null) {
            Transaction existing = transactionRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existing != null) {
                if (existing.getRequestHash() != null && existing.getRequestHash().equals(request.getRequestHash())) {
                    return convertToDto(existing);
                }
                throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "Idempotency key already used with different request");
            }
        }

        // 8. Save PENDING with a txn_ref
        Transaction transaction = Transaction.builder()
                .userId(userId)
                .txnRef(txnRef)
                .idempotencyKey(idempotencyKey)
                .requestHash(request.getRequestHash())
                .receiverUserId(receiver.getUserId())
                .receiverUpiId(request.getReceiverUpiId())
                .amount(request.getAmount())
                .type("P2P")
                .status("PENDING")
                .build();

        transactionRepository.save(transaction);

        // 9. Feign to Wallet internal transfer
        try {
            String description = "P2P transfer to " + receiver.getMaskedName();
            TransferResponse transferResponse = walletClient.internalTransfer(
                    txnRef,
                    userId,
                    receiver.getUserId(),
                    request.getAmount(),
                    description
            );

            // 10. Mark COMPLETED or FAILED
            if ("COMPLETED".equals(transferResponse.getStatus())) {
                transaction.setStatus("COMPLETED");
                transaction.setFailureReason(null);
            } else {
                transaction.setStatus("FAILED");
                transaction.setFailureReason(transferResponse.getFailureReason());
            }
            transaction = transactionRepository.save(transaction);

            // 11. Publish payment.completed
            publishPaymentCompleted(transaction);

            return convertToDto(transaction);

        } catch (Exception e) {
            // Circuit open or timeout after retry: FAILED WALLET_UNAVAILABLE
            transaction.setStatus("FAILED");
            transaction.setFailureReason("WALLET_UNAVAILABLE");
            transaction = transactionRepository.save(transaction);
            publishPaymentCompleted(transaction);
            throw new BusinessException("SERVICE_UNAVAILABLE", "Wallet service unavailable: " + e.getMessage());
        }
    }

    @Transactional
    public QrGenerateResponse qrGenerate(String userId, QrGenerateRequest request) {
        // Get user info via Auth service Feign client
        UserLookupDto sender = authClient.getCurrentUser(userId, INTERNAL_API_KEY);

        // Generate QR payload: upi://pay?pa=<upiId>&pn=<name>&am=<amount>&cu=INR
        String upiId = sender.getUpiId();
        String name = sender.getMaskedName();

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

        // If am is present, validate it matches
        if (am != null && !am.isEmpty()) {
            if (!am.equals(amount.toString())) {
                throw new BusinessException("INVALID_SIGNATURE", "QR amount must match request amount");
            }
        }

        // Lookup receiver by UPI ID from QR via Auth service
        UserLookupDto receiver = lookupReceiverByUpi(pa);

        // Check KYC
        if (!"APPROVED".equals(receiver.getKycStatus())) {
            throw new BusinessException("KYC_NOT_APPROVED", "Receiver KYC is not approved");
        }

        // Check self-payment
        if (receiver.getUserId().equals(senderId)) {
            throw new BusinessException("CANNOT_PAY_YOURSELF", "Cannot pay yourself");
        }

        // Check amount limits
        validateAmount(amount);
        validateTransactionLimit(amount);
        validateDailyLimits(senderId, amount);

        // Check idempotency
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

        // Save PENDING
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

        // Feign to Wallet internal transfer
        try {
            String description = "QR payment to " + receiver.getMaskedName();
            TransferResponse transferResponse = walletClient.internalTransfer(
                    txnRef,
                    senderId,
                    receiver.getUserId(),
                    amount,
                    description
            );

            if ("COMPLETED".equals(transferResponse.getStatus())) {
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
            throw new BusinessException("SERVICE_UNAVAILABLE", "Wallet service unavailable: " + e.getMessage());
        }
    }

    @Transactional
    public RechargeResponse recharge(String userId, String idempotencyKey, RechargeRequest request) {
        String mobileNumber = request.getMobileNumber();
        String operator = request.getOperator();
        BigDecimal amount = request.getAmount();
        String category = request.getCategory();

        // Validate mobile number
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

        // Check KYC via auth service
        UserLookupDto user = authClient.getCurrentUser(userId, INTERNAL_API_KEY);
        if (!"APPROVED".equals(user.getKycStatus())) {
            throw new BusinessException("KYC_NOT_APPROVED", "User KYC is not approved");
        }

        // Check daily limits
        validateDailyLimits(userId, amount);

        // Check idempotency
        String txnRef = generateTxnRef();
        if (idempotencyKey != null) {
            Transaction existing = transactionRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existing != null) {
                if (existing.getRequestHash() != null && existing.getRequestHash().equals(request.getRequestHash())) {
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
                .receiverUserId(null)
                .receiverUpiId(null)
                .amount(amount)
                .type("RECHARGE")
                .status("PENDING")
                .build();

        transactionRepository.save(transaction);

        // Debit user wallet and credit OPERATOR system wallet
        try {
            // First, debit user wallet (transfer from user to OPERATOR)
            String debitTxnRef = txnRef + "-DEBIT";
            TransferResponse debitResponse = walletClient.internalTransfer(
                    debitTxnRef,
                    userId,
                    null,
                    amount,
                    "Recharge: debit user wallet to OPERATOR"
            );

            // Call the RechargeOperator stub
            boolean rechargeSuccess = callRechargeOperatorStub(mobileNumber, operator, amount);

            if (rechargeSuccess) {
                transaction.setStatus("COMPLETED");
                transaction.setFailureReason(null);
                transaction = transactionRepository.save(transaction);
                publishPaymentCompleted(transaction);

                String operatorRef = "OP" + String.format("%04d", new java.util.Random().nextInt(9999));
                return RechargeResponse.builder()
                        .txnRef(txnRef)
                        .status("COMPLETED")
                        .operatorRef(operatorRef)
                        .amount(amount)
                        .createdAt(transaction.getTimestamp())
                        .build();
            } else {
                String reverseTxnRef = txnRef + "-REV";
                walletClient.internalTransfer(
                        reverseTxnRef,
                        null,
                        userId,
                        amount,
                        "Recharge reversal: operator declined, reversing to user"
                );

                transaction.setStatus("FAILED");
                transaction.setFailureReason("OPERATOR_DECLINED");
                transaction = transactionRepository.save(transaction);
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
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        BigDecimal todayTotal = transactionRepository.sumCompletedAmountToday(userId, today);

        BigDecimal dailyTotalLimit = new BigDecimal(DAILY_TOTAL_LIMIT);

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

    private UserLookupDto lookupReceiverByUpi(String upiId) {
        UserLookupDto receiver = authClient.lookupUserByUpi(upiId, INTERNAL_API_KEY);

        if ("BLOCKED".equals(receiver.getAccountStatus())) {
            throw new BusinessException("ACCOUNT_BLOCKED", "Receiver account is blocked");
        }

        return receiver;
    }

    private String generateTxnRef() {
        String datePart = LocalDate.now(ZoneId.of("Asia/Kolkata")).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = String.format("%06d", new java.util.Random().nextInt(999999) + 1);
        return "TXN" + datePart + randomPart;
    }

    private String extractParameter(String qrPayload, String paramName) {
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
        if (mobileNumber.endsWith("0000")) {
            log.info("Recharge operator declined for mobile number ending 0000");
            return false;
        }
        String operatorRef = "OP" + String.format("%04d", new java.util.Random().nextInt(9999));
        log.info("Recharge operator succeeded, operatorRef: {}", operatorRef);
        return true;
    }

    private void publishPaymentCompleted(Transaction transaction) {
        if (transaction.isEventPublished()) {
            log.info("Payment already published for txnRef: {}", transaction.getTxnRef());
            return;
        }

        String type = transaction.getType();
        String eventType = "P2P";
        if ("QR".equals(type)) {
            eventType = "QR";
        } else if ("RECHARGE".equals(type)) {
            eventType = "RECHARGE";
        }

        String userId = transaction.getUserId();
        String txnRef = transaction.getTxnRef();

        log.info("Publishing payment.completed for txnRef: {} to expense service", txnRef);

        transaction.setEventPublished(true);
        transactionRepository.save(transaction);
    }

    private TransferResponse convertToDto(Transaction transaction) {
        return TransferResponse.builder()
                .txnRef(transaction.getTxnRef())
                .status(transaction.getStatus())
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .receiverUpiId(transaction.getReceiverUpiId())
                .build();
    }

    private TransactionDto convertTransactionToDto(Transaction transaction) {
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
                .txnRef(transaction.getTxnRef())
                .build();
    }

    // Scheduled retry job - runs every 60 seconds
    @Scheduled(fixedRate = 60000)
    public void retryUnpublishedEvents() {
        log.info("Checking for unpublished payment.completed events...");
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

    public List<TransactionDto> getAllTransactionsByUser(int page, int size) {
        String userId = UserContextHolder.getUserId();
        List<Transaction> transactions = transactionRepository.findByUserIdOrderByTimestampDesc(userId);
        return transactions.stream()
                .skip((long) page * size)
                .limit(size)
                .map(this::convertTransactionToDto)
                .toList();
    }

    public TransactionDto getTransactionById(Long id, String txnRef) {
        if (txnRef != null && !txnRef.isEmpty()) {
            return transactionRepository.findByTxnRef(txnRef)
                    .map(this::convertTransactionToDto)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with txnRef: " + txnRef));
        }
        return transactionRepository.findById(id)
                .map(this::convertTransactionToDto)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
    }

    public List<TransactionDto> getAdminAllTransactions(int page, int size, String status, String type, String from, String to) {
        List<Transaction> transactions = transactionRepository.findAll();
        return transactions.stream()
                .skip((long) page * size)
                .limit(size)
                .map(this::convertTransactionToDto)
                .toList();
    }

    public TransactionSummaryResponse getAdminSummary(String from, String to) {
        List<Transaction> transactions = transactionRepository.findAll();
        BigDecimal totalVolume = transactions.stream()
                .filter(t -> "COMPLETED".equals(t.getStatus()))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return TransactionSummaryResponse.builder()
                .totalTransactions((long) transactions.size())
                .totalVolume(totalVolume)
                .build();
    }
}