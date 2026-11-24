package com.paytrack.transaction.service;

import com.paytrack.transaction.UserContextHolder;
import com.paytrack.transaction.client.AuthClient;
import com.paytrack.transaction.client.WalletClient;
import com.paytrack.transaction.dto.*;
import com.paytrack.transaction.entity.Recharge;
import com.paytrack.transaction.entity.Transaction;
import com.paytrack.transaction.exception.BusinessException;
import com.paytrack.transaction.repository.RechargeRepository;
import com.paytrack.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private RechargeRepository rechargeRepository;

    @Mock
    private WalletClient walletClient;

    @Mock
    private AuthClient authClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Spy
    private RechargeOperator rechargeOperator = new DummyRechargeOperator();

    @InjectMocks
    private TransactionService transactionService;

    private UserLookupDto receiverDto;
    private TransferRequest transferRequest;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(transactionService, "minAmount", new BigDecimal("1.00"));
        ReflectionTestUtils.setField(transactionService, "maxTransactionLimit", new BigDecimal("10000.00"));
        ReflectionTestUtils.setField(transactionService, "dailyTotalLimit", new BigDecimal("25000.00"));
        ReflectionTestUtils.setField(transactionService, "minRechargeAmount", new BigDecimal("10.00"));
        ReflectionTestUtils.setField(transactionService, "maxRechargeAmount", new BigDecimal("2000.00"));
        ReflectionTestUtils.setField(transactionService, "configuredTimezone", "Asia/Kolkata");
        ReflectionTestUtils.setField(transactionService, "internalApiKey", "test-key");

        UserContextHolder.setUserId("sender-123");
        UserContextHolder.setRole("USER");
        UserContextHolder.setKycStatus("APPROVED");

        receiverDto = UserLookupDto.builder()
                .userId("receiver-123")
                .maskedName("R*** U***")
                .upiId("receiver.user@paytrack")
                .accountStatus("ACTIVE")
                .kycStatus("APPROVED")
                .build();

        transferRequest = TransferRequest.builder()
                .receiverUpiId("receiver.user@paytrack")
                .amount(new BigDecimal("100.00"))
                .note("Test transfer")
                .category("FOOD")
                .build();

        idempotencyKey = "test-idempotency-key-123";
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void transfer_shouldCreateTransactionAndCallWallet() {
        // Given
        when(authClient.lookupUserByUpi(eq("receiver.user@paytrack"), anyString()))
                .thenReturn(ApiResponse.success(receiverDto));
        when(transactionRepository.findByUserIdAndIdempotencyKey(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InternalTransferResponse walletResp = InternalTransferResponse.builder()
                .txnRef("TXN20261123000001")
                .status("COMPLETED")
                .senderBalanceAfter(new BigDecimal("900.00"))
                .build();
        when(walletClient.internalTransfer(anyString(), any(InternalTransferRequest.class)))
                .thenReturn(ApiResponse.success(walletResp));

        // When
        TransferResponse result = transactionService.transfer("sender-123", idempotencyKey, transferRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAmount()).isEqualByComparingTo("100.00");
        assertThat(result.getType()).isEqualTo("P2P");
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getBalanceAfter()).isEqualByComparingTo("900.00");

        verify(transactionRepository, atLeastOnce()).save(any(Transaction.class));
        verify(walletClient).internalTransfer(anyString(), any(InternalTransferRequest.class));
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void transfer_shouldThrowWhenReceiverNotFound() {
        // Given
        when(authClient.lookupUserByUpi(eq("receiver.user@paytrack"), anyString()))
                .thenReturn(ApiResponse.error("Not found", "NOT_FOUND"));

        // When/Then
        assertThatThrownBy(() -> transactionService.transfer("sender-123", idempotencyKey, transferRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void transfer_shouldThrowWhenReceiverKycNotApproved() {
        // Given
        UserLookupDto notKyc = UserLookupDto.builder()
                .userId("receiver-123")
                .maskedName("R*** U***")
                .upiId("receiver.user@paytrack")
                .accountStatus("ACTIVE")
                .kycStatus("NOT_SUBMITTED")
                .build();
        when(authClient.lookupUserByUpi(eq("receiver.user@paytrack"), anyString()))
                .thenReturn(ApiResponse.success(notKyc));

        // When/Then
        assertThatThrownBy(() -> transactionService.transfer("sender-123", idempotencyKey, transferRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("KYC");
    }

    @Test
    void transfer_shouldThrowWhenReceiverBlocked() {
        // Given
        UserLookupDto blocked = UserLookupDto.builder()
                .userId("receiver-123")
                .maskedName("R*** U***")
                .upiId("receiver.user@paytrack")
                .accountStatus("BLOCKED")
                .kycStatus("APPROVED")
                .build();
        when(authClient.lookupUserByUpi(eq("receiver.user@paytrack"), anyString()))
                .thenReturn(ApiResponse.success(blocked));

        // When/Then
        assertThatThrownBy(() -> transactionService.transfer("sender-123", idempotencyKey, transferRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Receiver account is not active");
    }

    @Test
    void transfer_shouldThrowWhenSelfPayment() {
        // Given
        UserLookupDto self = UserLookupDto.builder()
                .userId("sender-123")
                .maskedName("S*** U***")
                .upiId("sender.user@paytrack")
                .accountStatus("ACTIVE")
                .kycStatus("APPROVED")
                .build();
        when(authClient.lookupUserByUpi(eq("sender.user@paytrack"), anyString()))
                .thenReturn(ApiResponse.success(self));

        TransferRequest selfTransfer = TransferRequest.builder()
                .receiverUpiId("sender.user@paytrack")
                .amount(new BigDecimal("100.00"))
                .build();

        // When/Then
        assertThatThrownBy(() -> transactionService.transfer("sender-123", idempotencyKey, selfTransfer))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot pay yourself");
    }

    @Test
    void transfer_shouldThrowWhenAmountExceedsLimit() {
        // Given
        TransferRequest large = TransferRequest.builder()
                .receiverUpiId("receiver.user@paytrack")
                .amount(new BigDecimal("20000.00"))
                .build();

        // When/Then
        assertThatThrownBy(() -> transactionService.transfer("sender-123", idempotencyKey, large))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exceeds limit");
    }

    @Test
    void qrGenerate_shouldCreateValidQrPayload() {
        // Given
        UserLookupDto senderDto = UserLookupDto.builder()
                .userId("sender-123")
                .fullName("Sender User")
                .upiId("sender.user@paytrack")
                .build();
        when(authClient.getCurrentUser("sender-123")).thenReturn(ApiResponse.success(senderDto));

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
                .build();

        when(authClient.lookupUserByUpi(eq("receiver.user@paytrack"), anyString()))
                .thenReturn(ApiResponse.success(receiverDto));
        when(transactionRepository.findByUserIdAndIdempotencyKey(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InternalTransferResponse walletResp = InternalTransferResponse.builder()
                .txnRef("TXN20261123000002")
                .status("COMPLETED")
                .senderBalanceAfter(new BigDecimal("925.00"))
                .build();
        when(walletClient.internalTransfer(anyString(), any(InternalTransferRequest.class)))
                .thenReturn(ApiResponse.success(walletResp));

        // When
        TransferResponse result = transactionService.qrPay("sender-123", idempotencyKey, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAmount()).isEqualByComparingTo("75.00");
        assertThat(result.getType()).isEqualTo("QR");
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        verify(walletClient).internalTransfer(anyString(), any(InternalTransferRequest.class));
    }

    @Test
    void qrPay_shouldThrowWhenQrCurrencyNotInr() {
        // Given
        String qrPayload = "upi://pay?pa=receiver.user@paytrack&pn=Receiver User&am=75.00&cu=USD";
        QrPayRequest request = QrPayRequest.builder()
                .qrPayload(qrPayload)
                .amount(new BigDecimal("75.00"))
                .build();

        // When/Then
        assertThatThrownBy(() -> transactionService.qrPay("sender-123", idempotencyKey, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("QR currency must be INR");
    }

    @Test
    void recharge_shouldDebitWalletAndCallOperator() {
        // Given
        RechargeRequest request = RechargeRequest.builder()
                .mobileNumber("9876543210")
                .operator("AIRTEL")
                .amount(new BigDecimal("100.00"))
                .category("UTILITIES")
                .build();

        when(transactionRepository.findByUserIdAndIdempotencyKey(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(10L);
            return t;
        });

        InternalTransferResponse debitResp = InternalTransferResponse.builder()
                .txnRef("TXN20261123000003-DEBIT")
                .status("COMPLETED")
                .senderBalanceAfter(new BigDecimal("800.00"))
                .build();
        when(walletClient.internalTransfer(anyString(), any(InternalTransferRequest.class)))
                .thenReturn(ApiResponse.success(debitResp));

        // When
        RechargeResponse result = transactionService.recharge("sender-123", idempotencyKey, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getOperatorRef()).isNotNull();
        assertThat(result.getOperatorRef()).startsWith("OP");
        verify(walletClient, times(1)).internalTransfer(anyString(), any(InternalTransferRequest.class));
        verify(rechargeRepository).save(any(Recharge.class));
    }

    @Test
    void recharge_shouldReverseWhenOperatorDeclined() {
        // Given
        RechargeRequest request = RechargeRequest.builder()
                .mobileNumber("9876540000") // ends in 0000 -> declines
                .operator("AIRTEL")
                .amount(new BigDecimal("100.00"))
                .build();

        when(transactionRepository.findByUserIdAndIdempotencyKey(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(10L);
            return t;
        });

        InternalTransferResponse debitResp = InternalTransferResponse.builder()
                .txnRef("TXN20261123000003-DEBIT")
                .status("COMPLETED")
                .build();
        when(walletClient.internalTransfer(anyString(), any(InternalTransferRequest.class)))
                .thenReturn(ApiResponse.success(debitResp));

        // When
        RechargeResponse result = transactionService.recharge("sender-123", idempotencyKey, request);

        // Then
        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getOperatorRef()).isNull();
        // Called once for debit, once for reversal
        verify(walletClient, times(2)).internalTransfer(anyString(), any(InternalTransferRequest.class));
    }

    @Test
    void recharge_shouldValidateAmountRange() {
        RechargeRequest tooLow = RechargeRequest.builder()
                .mobileNumber("9876543210")
                .operator("AIRTEL")
                .amount(new BigDecimal("5.00"))
                .build();

        assertThatThrownBy(() -> transactionService.recharge("sender-123", idempotencyKey, tooLow))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Amount must be at least");

        RechargeRequest tooHigh = RechargeRequest.builder()
                .mobileNumber("9876543210")
                .operator("AIRTEL")
                .amount(new BigDecimal("5000.00"))
                .build();

        assertThatThrownBy(() -> transactionService.recharge("sender-123", idempotencyKey, tooHigh))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Amount exceeds limit");
    }

    @Test
    void recharge_shouldValidateOperator() {
        RechargeRequest invalidOperator = RechargeRequest.builder()
                .mobileNumber("9876543210")
                .operator("INVALID")
                .amount(new BigDecimal("100.00"))
                .build();

        assertThatThrownBy(() -> transactionService.recharge("sender-123", idempotencyKey, invalidOperator))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid operator");
    }
}
