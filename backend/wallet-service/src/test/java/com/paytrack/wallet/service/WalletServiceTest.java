package com.paytrack.wallet.service;

import com.paytrack.wallet.dto.*;
import com.paytrack.wallet.entity.LedgerEntry;
import com.paytrack.wallet.entity.PaymentOrder;
import com.paytrack.wallet.entity.Wallet;
import com.paytrack.wallet.exception.InsufficientBalanceException;
import com.paytrack.wallet.repository.LedgerEntryRepository;
import com.paytrack.wallet.repository.PaymentOrderRepository;
import com.paytrack.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @InjectMocks
    private WalletService walletService;

    private final String keySecret = "test-secret-key-12345678901234567890";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(walletService, "gatewayKeyId", "test_key_id");
        ReflectionTestUtils.setField(walletService, "gatewayKeySecret", keySecret);
    }

    @Test
    @DisplayName("Create wallet should default currency to INR")
    void createWallet_defaultCurrencyInr() {
        CreateWalletRequest request = CreateWalletRequest.builder()
                .userId("user-123")
                .build();

        when(walletRepository.existsByUserId("user-123")).thenReturn(false);
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> {
            Wallet w = invocation.getArgument(0);
            w.setId(1L);
            return w;
        });

        WalletDto result = walletService.createWallet(request);

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getCurrency()).isEqualTo("INR");
        assertThat(result.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Create wallet with non-INR currency should throw IllegalArgumentException")
    void createWallet_nonInr_shouldThrow() {
        CreateWalletRequest request = CreateWalletRequest.builder()
                .userId("user-123")
                .currency("USD")
                .build();

        assertThatThrownBy(() -> walletService.createWallet(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only INR currency is supported");
    }

    @Test
    @DisplayName("Atomic transfer should debit sender and credit receiver in double-entry ledger")
    void transfer_success_doubleEntry() {
        Wallet sender = Wallet.builder()
                .id(1L)
                .userId("sender-1")
                .ownerType(Wallet.OwnerType.USER)
                .balance(new BigDecimal("500.00"))
                .currency("INR")
                .build();

        Wallet receiver = Wallet.builder()
                .id(2L)
                .userId("receiver-2")
                .ownerType(Wallet.OwnerType.USER)
                .balance(new BigDecimal("100.00"))
                .currency("INR")
                .build();

        when(ledgerEntryRepository.findByTxnRef("TXN-001")).thenReturn(List.of());
        when(walletRepository.findByUserId("sender-1")).thenReturn(Optional.of(sender));
        when(walletRepository.findByUserId("receiver-2")).thenReturn(Optional.of(receiver));
        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(sender));
        when(walletRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiver));

        InternalTransferRequest request = InternalTransferRequest.builder()
                .txnRef("TXN-001")
                .fromUserId("sender-1")
                .toUserId("receiver-2")
                .amount(new BigDecimal("200.00"))
                .description("P2P transfer")
                .build();

        InternalTransferResponse response = walletService.transfer(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getSenderBalanceAfter()).isEqualByComparingTo("300.00");
        assertThat(receiver.getBalance()).isEqualByComparingTo("300.00");

        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("Transfer with insufficient balance should throw InsufficientBalanceException")
    void transfer_insufficientBalance_shouldThrow() {
        Wallet sender = Wallet.builder()
                .id(1L)
                .userId("sender-1")
                .ownerType(Wallet.OwnerType.USER)
                .balance(new BigDecimal("50.00"))
                .currency("INR")
                .build();

        Wallet receiver = Wallet.builder()
                .id(2L)
                .userId("receiver-2")
                .ownerType(Wallet.OwnerType.USER)
                .balance(new BigDecimal("100.00"))
                .currency("INR")
                .build();

        when(ledgerEntryRepository.findByTxnRef("TXN-002")).thenReturn(List.of());
        when(walletRepository.findByUserId("sender-1")).thenReturn(Optional.of(sender));
        when(walletRepository.findByUserId("receiver-2")).thenReturn(Optional.of(receiver));
        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(sender));
        when(walletRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiver));

        InternalTransferRequest request = InternalTransferRequest.builder()
                .txnRef("TXN-002")
                .fromUserId("sender-1")
                .toUserId("receiver-2")
                .amount(new BigDecimal("100.00"))
                .build();

        assertThatThrownBy(() -> walletService.transfer(request))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient balance");
    }

    @Test
    @DisplayName("Transfer should be idempotent on txnRef")
    void transfer_idempotent() {
        LedgerEntry existingDebit = LedgerEntry.builder()
                .entryType(LedgerEntry.EntryType.DEBIT)
                .balanceAfter(new BigDecimal("400.00"))
                .build();

        when(ledgerEntryRepository.findByTxnRef("TXN-EXISTING")).thenReturn(List.of(existingDebit));

        InternalTransferRequest request = InternalTransferRequest.builder()
                .txnRef("TXN-EXISTING")
                .fromUserId("sender-1")
                .toUserId("receiver-2")
                .amount(new BigDecimal("100.00"))
                .build();

        InternalTransferResponse response = walletService.transfer(request);

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getSenderBalanceAfter()).isEqualByComparingTo("400.00");
        verify(walletRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    @DisplayName("Verify top-up with valid signature should credit user wallet")
    void verifyTopup_validSignature_success() throws Exception {
        String orderId = "order_123456789";
        String paymentId = "pay_987654321";

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String validSignature = HexFormat.of().formatHex(mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));

        PaymentOrder order = PaymentOrder.builder()
                .orderId(orderId)
                .userId("user-100")
                .amount(new BigDecimal("500.00"))
                .status(PaymentOrder.OrderStatus.CREATED)
                .build();

        Wallet bankWallet = Wallet.builder()
                .id(99L)
                .systemName("BANK_SETTLEMENT")
                .ownerType(Wallet.OwnerType.SYSTEM)
                .balance(BigDecimal.ZERO)
                .currency("INR")
                .build();

        Wallet userWallet = Wallet.builder()
                .id(1L)
                .userId("user-100")
                .ownerType(Wallet.OwnerType.USER)
                .balance(new BigDecimal("100.00"))
                .currency("INR")
                .build();

        when(paymentOrderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));
        when(walletRepository.findByUserId("user-100")).thenReturn(Optional.of(userWallet));
        when(walletRepository.findBySystemName("BANK_SETTLEMENT")).thenReturn(Optional.of(bankWallet));
        when(ledgerEntryRepository.findByTxnRef("TOPUP-" + orderId)).thenReturn(List.of());
        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(userWallet));
        when(walletRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(bankWallet));

        TopupVerifyRequest verifyRequest = TopupVerifyRequest.builder()
                .orderId(orderId)
                .paymentId(paymentId)
                .signature(validSignature)
                .build();

        TopupVerifyResponse response = walletService.verifyTopup("user-100", verifyRequest);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("PAID");
        assertThat(order.getStatus()).isEqualTo(PaymentOrder.OrderStatus.PAID);
        assertThat(userWallet.getBalance()).isEqualByComparingTo("600.00");
    }

    @Test
    @DisplayName("Verify top-up with invalid signature should throw and mark order FAILED")
    void verifyTopup_invalidSignature_shouldThrow() {
        String orderId = "order_invalid";
        PaymentOrder order = PaymentOrder.builder()
                .orderId(orderId)
                .userId("user-100")
                .amount(new BigDecimal("500.00"))
                .status(PaymentOrder.OrderStatus.CREATED)
                .build();

        Wallet userWallet = Wallet.builder()
                .id(1L)
                .userId("user-100")
                .balance(new BigDecimal("100.00"))
                .build();

        when(paymentOrderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));
        when(walletRepository.findByUserId("user-100")).thenReturn(Optional.of(userWallet));

        TopupVerifyRequest verifyRequest = TopupVerifyRequest.builder()
                .orderId(orderId)
                .paymentId("pay_123")
                .signature("invalid_hex_signature")
                .build();

        assertThatThrownBy(() -> walletService.verifyTopup("user-100", verifyRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid payment signature");

        assertThat(order.getStatus()).isEqualTo(PaymentOrder.OrderStatus.FAILED);
    }
}
