package com.paytrack.transaction.service;

import com.paytrack.auth.entity.User;
import com.paytrack.auth.repository.UserRepository;
import com.paytrack.transaction.client.WalletClient;
import com.paytrack.transaction.dto.*;
import com.paytrack.transaction.entity.Transaction;
import com.paytrack.transaction.repository.TransactionRepository;
import com.paytrack.transaction.UserContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletClient walletClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private TransactionService transactionService;

    private User sender;
    private User receiver;
    private TransferRequest transferRequest;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        sender = User.builder()
                .userId("sender-123")
                .fullName("Sender User")
                .email("sender@example.com")
                .upiId("sender.user@paytrack")
                .kycStatus(User.KycStatus.APPROVED)
                .accountStatus(User.AccountStatus.ACTIVE)
                .build();

        receiver = User.builder()
                .userId("receiver-123")
                .fullName("Receiver User")
                .email("receiver@example.com")
                .upiId("receiver.user@paytrack")
                .kycStatus(User.KycStatus.APPROVED)
                .accountStatus(User.AccountStatus.ACTIVE)
                .build();

        transferRequest = TransferRequest.builder()
                .receiverUpiId("receiver.user@paytrack")
                .amount(new BigDecimal("100.00"))
                .note("Test transfer")
                .category("FOOD")
                .build();

        idempotencyKey = "test-idempotency-key-123";
    }

    @Test
    void transfer_shouldCreateTransactionAndCallWallet() {
        // Given
        when(userRepository.findByUpiId("receiver.user@paytrack")).thenReturn(Optional.of(receiver));
        when(userRepository.findByUserId("sender-123")).thenReturn(Optional.of(sender));
        when(transactionRepository.findByUserIdAndIdempotencyKey(anyString(), anyString())).thenReturn(null);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransferResponse walletResponse = TransferResponse.builder()
                .txnRef("TXN20241123000001")
                .status("COMPLETED")
                .type("P2P")
                .amount(new BigDecimal("100.00"))
                .balanceAfter("900.00")
                .build();
        when(walletClient.internalTransfer(anyString(), anyString(), anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(walletResponse);

        // When
        TransactionDto result = transactionService.transfer("sender-123", idempotencyKey, transferRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAmount()).isEqualByComparingTo("100.00");
        assertThat(result.getType()).isEqualTo("P2P");
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getTxnRef()).isNotNull();

        verify(transactionRepository).save(any(Transaction.class));
        verify(walletClient).internalTransfer(anyString(), anyString(), anyString(), any(BigDecimal.class), anyString());
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any());
    }

    @Test
    void transfer_shouldThrowWhenReceiverNotFound() {
        // Given
        when(userRepository.findByUpiId("receiver.user@paytrack")).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> transactionService.transfer("sender-123", idempotencyKey, transferRequest))
                .isInstanceOf(com.paytrack.transaction.exception.ResourceNotFoundException.class)
                .hasMessageContaining("User with UPI ID not found");
    }

    @Test
    void transfer_shouldThrowWhenReceiverKycNotApproved() {
        // Given
        User receiverNotKyc = User.builder()
                .userId("receiver-123")
                .fullName("Receiver User")
                .upiId("receiver.user@paytrack")
                .kycStatus(User.KycStatus.NOT_SUBMITTED)
                .accountStatus(User.AccountStatus.ACTIVE)
                .build();

        when(userRepository.findByUpiId("receiver.user@paytrack")).thenReturn(Optional.of(receiverNotKyc));

        // When/Then
        assertThatThrownBy(() -> transactionService.transfer("sender-123", idempotencyKey, transferRequest))
                .isInstanceOf(com.paytrack.transaction.exception.BusinessException.class)
                .hasMessageContaining("KYC_NOT_APPROVED");
    }

    @Test
    void transfer_shouldThrowWhenReceiverBlocked() {
        // Given
        User receiverBlocked = User.builder()
                .userId("receiver-123")
                .fullName("Receiver User")
                .upiId("receiver.user@paytrack")
                .kycStatus(User.KycStatus.APPROVED)
                .accountStatus(User.AccountStatus.BLOCKED)
                .build();

        when(userRepository.findByUpiId("receiver.user@paytrack")).thenReturn(Optional.of(receiverBlocked));

        // When/Then
        assertThatThrownBy(() -> transactionService.transfer("sender-123", idempotencyKey, transferRequest))
                .isInstanceOf(com.paytrack.transaction.exception.BusinessException.class)
                .hasMessageContaining("ACCOUNT_BLOCKED");
    }

    @Test
    void transfer_shouldThrowWhenSelfPayment() {
        // Given
        when(userRepository.findByUpiId("sender.user@paytrack")).thenReturn(Optional.of(sender));
        when(userRepository.findByUserId("sender-123")).thenReturn(Optional.of(sender));

        TransferRequest selfTransfer = TransferRequest.builder()
                .receiverUpiId("sender.user@paytrack")
                .amount(new BigDecimal("100.00"))
                .build();

        // When/Then
        assertThatThrownBy(() -> transactionService.transfer("sender-123", idempotencyKey, selfTransfer))
                .isInstanceOf(com.paytrack.transaction.exception.BusinessException.class)
                .hasMessageContaining("Cannot pay yourself");
    }

    @Test
    void transfer_shouldThrowWhenAmountExceedsLimit() {
        // Given
        TransferRequest largeTransfer = TransferRequest.builder()
                .receiverUpiId("receiver.user@paytrack")
                .amount(new BigDecimal("20000.00"))
                .build();

        when(userRepository.findByUpiId("receiver.user@paytrack")).thenReturn(Optional.of(receiver));

        // When/Then
        assertThatThrownBy(() -> transactionService.transfer("sender-123", idempotencyKey, largeTransfer))
                .isInstanceOf(com.paytrack.transaction.exception.BusinessException.class)
                .hasMessageContaining("LIMIT_EXCEEDED");
    }

    @Test
    void transfer_shouldReturnExistingTransactionWhenIdempotencyKeyReused() {
        // Given
        Transaction existingTransaction = Transaction.builder()
                .userId("sender-123")
                .txnRef("TXN20241123000001")
                .idempotencyKey(idempotencyKey)
                .requestHash("same-hash")
                .amount(new BigDecimal("100.00"))
                .type("P2P")
                .status("COMPLETED")
                .build();

        when(userRepository.findByUpiId("receiver.user@paytrack")).thenReturn(Optional.of(receiver));
        when(userRepository.findByUserId("sender-123")).thenReturn(Optional.of(sender));
        when(transactionRepository.findByUserIdAndIdempotencyKey("sender-123", idempotencyKey))
                .thenReturn(existingTransaction);

        // When
        TransactionDto result = transactionService.transfer("sender-123", idempotencyKey, transferRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTxnRef()).isEqualTo("TXN20241123000001");
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(walletClient, never()).internalTransfer(anyString(), anyString(), anyString(), any(BigDecimal.class), anyString());
    }

    @Test
    void transfer_shouldThrowWhenIdempotencyKeyReusedWithDifferentBody() {
        // Given
        Transaction existingTransaction = Transaction.builder()
                .userId("sender-123")
                .txnRef("TXN20241123000001")
                .idempotencyKey(idempotencyKey)
                .requestHash("different-hash")
                .amount(new BigDecimal("100.00"))
                .type("P2P")
                .status("COMPLETED")
                .build();

        when(userRepository.findByUpiId("receiver.user@paytrack")).thenReturn(Optional.of(receiver));
        when(userRepository.findByUserId("sender-123")).thenReturn(Optional.of(sender));
        when(transactionRepository.findByUserIdAndIdempotencyKey("sender-123", idempotencyKey))
                .thenReturn(existingTransaction);

        // When/Then
        assertThatThrownBy(() -> transactionService.transfer("sender-123", idempotencyKey, transferRequest))
                .isInstanceOf(com.paytrack.transaction.exception.BusinessException.class)
                .hasMessageContaining("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void qrGenerate_shouldCreateValidQrPayload() {
        // Given
        when(userRepository.findByUserId("sender-123")).thenReturn(Optional.of(sender));

        QrGenerateRequest request = QrGenerateRequest.builder()
                .amount(new BigDecimal("50.00"))
                .build();

        // When
        QrGenerateResponse result = transactionService.qrGenerate("sender-123", request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getQrPayload()).startsWith("upi://pay?");
        assertThat(result.getQrPayload()).contains("pa=sender.user@paytrack");
        assertThat(result.getQrPayload()).contains("pn=Sender User");
        assertThat(result.getQrPayload()).contains("am=50.00");
        assertThat(result.getQrPayload()).contains("cu=INR");
    }

    @Test
    void qrPay_shouldParseQrPayloadAndTransfer() {
        // Given
        String qrPayload = "upi://pay?pa=receiver.user@paytrack&pn=Receiver User&am=75.00&cu=INR";

        QrPayRequest request = QrPayRequest.builder()
                .qrPayload(qrPayload)
                .amount(new BigDecimal("75.00"))
                .category("FOOD")
                .requestHash("qr-hash")
                .build();

        when(userRepository.findByUpiId("receiver.user@paytrack")).thenReturn(Optional.of(receiver));
        when(userRepository.findByUserId("sender-123")).thenReturn(Optional.of(sender));
        when(transactionRepository.findByUserIdAndIdempotencyKey(anyString(), anyString())).thenReturn(null);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransferResponse walletResponse = TransferResponse.builder()
                .txnRef("TXN20241123000002")
                .status("COMPLETED")
                .type("QR")
                .amount(new BigDecimal("75.00"))
                .build();
        when(walletClient.internalTransfer(anyString(), anyString(), anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(walletResponse);

        // When
        TransferResponse result = transactionService.qrPay("sender-123", idempotencyKey, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAmount()).isEqualByComparingTo("75.00");
        assertThat(result.getType()).isEqualTo("QR");
        verify(walletClient).internalTransfer(anyString(), anyString(), anyString(), any(BigDecimal.class), anyString());
    }

    @Test
    void qrPay_shouldThrowWhenQrCurrencyNotInr() {
        // Given
        String qrPayload = "upi://pay?pa=receiver.user@paytrack&pn=Receiver User&am=75.00&cu=USD";

        QrPayRequest request = QrPayRequest.builder()
                .qrPayload(qrPayload)
                .amount(new BigDecimal("75.00"))
                .requestHash("qr-hash")
                .build();

        // When/Then
        assertThatThrownBy(() -> transactionService.qrPay("sender-123", idempotencyKey, request))
                .isInstanceOf(com.paytrack.transaction.exception.BusinessException.class)
                .hasMessageContaining("Currency must be INR");
    }

    @Test
    void recharge_shouldCreateTransactionAndCallWallet() {
        // Given
        RechargeRequest request = RechargeRequest.builder()
                .mobileNumber("9876543210")
                .operator("AIRTEL")
                .amount(new BigDecimal("100.00"))
                .category("UTILITIES")
                .requestHash("recharge-hash")
                .build();

        when(userRepository.findByUserId("sender-123")).thenReturn(Optional.of(sender));
        when(transactionRepository.findByUserIdAndIdempotencyKey(anyString(), anyString())).thenReturn(null);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransferResponse walletResponse = TransferResponse.builder()
                .txnRef("TXN20241123000003-DEBIT")
                .status("COMPLETED")
                .type("RECHARGE")
                .amount(new BigDecimal("100.00"))
                .build();
        when(walletClient.internalTransfer(anyString(), anyString(), anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(walletResponse);

        // When
        RechargeResponse result = transactionService.recharge("sender-123", idempotencyKey, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getOperatorRef()).isNotNull();
        assertThat(result.getOperatorRef()).startsWith("OP");
        verify(walletClient, times(1)).internalTransfer(anyString(), anyString(), anyString(), any(BigDecimal.class), anyString());
    }

    @Test
    void recharge_shouldFailWhenMobileEndsWith0000() {
        // Given
        RechargeRequest request = RechargeRequest.builder()
                .mobileNumber("9876540000")
                .operator("AIRTEL")
                .amount(new BigDecimal("100.00"))
                .requestHash("recharge-hash")
                .build();

        when(userRepository.findByUserId("sender-123")).thenReturn(Optional.of(sender));
        when(transactionRepository.findByUserIdAndIdempotencyKey(anyString(), anyString())).thenReturn(null);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransferResponse walletResponse = TransferResponse.builder()
                .txnRef("TXN20241123000003-DEBIT")
                .status("COMPLETED")
                .type("RECHARGE")
                .amount(new BigDecimal("100.00"))
                .build();
        when(walletClient.internalTransfer(anyString(), anyString(), anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(walletResponse);

        // When
        RechargeResponse result = transactionService.recharge("sender-123", idempotencyKey, request);

        // Then
        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getOperatorRef()).isNull();
        verify(walletClient, times(2)).internalTransfer(anyString(), anyString(), anyString(), any(BigDecimal.class), anyString());
    }

    @Test
    void recharge_shouldValidateAmountRange() {
        // Given
        RechargeRequest tooLow = RechargeRequest.builder()
                .mobileNumber("9876543210")
                .operator("AIRTEL")
                .amount(new BigDecimal("5.00"))
                .requestHash("recharge-hash")
                .build();

        when(userRepository.findByUserId("sender-123")).thenReturn(Optional.of(sender));

        // When/Then
        assertThatThrownBy(() -> transactionService.recharge("sender-123", idempotencyKey, tooLow))
                .isInstanceOf(com.paytrack.transaction.exception.BusinessException.class)
                .hasMessageContaining("Recharge amount must be at least 10");

        RechargeRequest tooHigh = RechargeRequest.builder()
                .mobileNumber("9876543210")
                .operator("AIRTEL")
                .amount(new BigDecimal("5000.00"))
                .requestHash("recharge-hash")
                .build();

        assertThatThrownBy(() -> transactionService.recharge("sender-123", idempotencyKey, tooHigh))
                .isInstanceOf(com.paytrack.transaction.exception.BusinessException.class)
                .hasMessageContaining("Recharge amount must not exceed 2000");
    }

    @Test
    void recharge_shouldValidateOperator() {
        // Given
        RechargeRequest invalidOperator = RechargeRequest.builder()
                .mobileNumber("9876543210")
                .operator("INVALID")
                .amount(new BigDecimal("100.00"))
                .requestHash("recharge-hash")
                .build();

        when(userRepository.findByUserId("sender-123")).thenReturn(Optional.of(sender));

        // When/Then
        assertThatThrownBy(() -> transactionService.recharge("sender-123", idempotencyKey, invalidOperator))
                .isInstanceOf(com.paytrack.transaction.exception.BusinessException.class)
                .hasMessageContaining("Invalid operator");
    }
}