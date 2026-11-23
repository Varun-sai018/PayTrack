package com.paytrack.wallet.integration;

import com.paytrack.wallet.entity.LedgerEntry;
import com.paytrack.wallet.entity.Wallet;
import com.paytrack.wallet.repository.LedgerEntryRepository;
import com.paytrack.wallet.repository.WalletRepository;
import com.paytrack.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class WalletIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("paytrack_wallet")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private WalletService walletService;

    @Test
    void internalTransfer_shouldCreateLedgerEntriesAndUpdateBalances() {
        // Given: Create two user wallets
        String fromUserId = UUID.randomUUID().toString();
        String toUserId = UUID.randomUUID().toString();

        Wallet fromWallet = Wallet.builder()
                .userId(fromUserId)
                .balance(new BigDecimal("1000.00"))
                .currency("INR")
                .ownerType(Wallet.OwnerType.USER)
                .build();

        Wallet toWallet = Wallet.builder()
                .userId(toUserId)
                .balance(new BigDecimal("500.00"))
                .currency("INR")
                .ownerType(Wallet.OwnerType.USER)
                .build();

        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);

        // When: Transfer 200 from fromWallet to toWallet
        String txnRef = "TXN" + System.currentTimeMillis();
        BigDecimal amount = new BigDecimal("200.00");

        walletService.internalTransfer(txnRef, fromUserId, toUserId, amount, "Test transfer");

        // Then: Verify balances updated
        Wallet updatedFromWallet = walletRepository.findByUserId(fromUserId).orElseThrow();
        Wallet updatedToWallet = walletRepository.findByUserId(toUserId).orElseThrow();

        assertThat(updatedFromWallet.getBalance()).isEqualByComparingTo("800.00");
        assertThat(updatedToWallet.getBalance()).isEqualByComparingTo("700.00");

        // And: Verify ledger entries created
        var fromLedgerEntries = ledgerEntryRepository.findByWalletIdAndTxnRefAndEntryType(
                fromWallet.getWalletId(), txnRef, LedgerEntry.EntryType.DEBIT);
        var toLedgerEntries = ledgerEntryRepository.findByWalletIdAndTxnRefAndEntryType(
                toWallet.getWalletId(), txnRef, LedgerEntry.EntryType.CREDIT);

        assertThat(fromLedgerEntries).hasSize(1);
        assertThat(toLedgerEntries).hasSize(1);

        assertThat(fromLedgerEntries.get(0).getAmount()).isEqualByComparingTo("200.00");
        assertThat(fromLedgerEntries.get(0).getBalanceAfter()).isEqualByComparingTo("800.00");

        assertThat(toLedgerEntries.get(0).getAmount()).isEqualByComparingTo("200.00");
        assertThat(toLedgerEntries.get(0).getBalanceAfter()).isEqualByComparingTo("700.00");
    }

    @Test
    void internalTransfer_shouldBeIdempotentOnTxnRef() {
        // Given
        String fromUserId = UUID.randomUUID().toString();
        String toUserId = UUID.randomUUID().toString();

        Wallet fromWallet = Wallet.builder()
                .userId(fromUserId)
                .balance(new BigDecimal("1000.00"))
                .currency("INR")
                .ownerType(Wallet.OwnerType.USER)
                .build();

        Wallet toWallet = Wallet.builder()
                .userId(toUserId)
                .balance(new BigDecimal("500.00"))
                .currency("INR")
                .ownerType(Wallet.OwnerType.USER)
                .build();

        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);

        String txnRef = "TXN" + System.currentTimeMillis();
        BigDecimal amount = new BigDecimal("200.00");

        // When: Execute transfer twice with same txnRef
        walletService.internalTransfer(txnRef, fromUserId, toUserId, amount, "Test transfer");
        walletService.internalTransfer(txnRef, fromUserId, toUserId, amount, "Test transfer");

        // Then: Balances should only be updated once
        Wallet updatedFromWallet = walletRepository.findByUserId(fromUserId).orElseThrow();
        Wallet updatedToWallet = walletRepository.findByUserId(toUserId).orElseThrow();

        assertThat(updatedFromWallet.getBalance()).isEqualByComparingTo("800.00");
        assertThat(updatedToWallet.getBalance()).isEqualByComparingTo("700.00");

        // And: Only one ledger entry pair should exist
        var fromLedgerEntries = ledgerEntryRepository.findByWalletIdAndTxnRefAndEntryType(
                fromWallet.getWalletId(), txnRef, LedgerEntry.EntryType.DEBIT);
        var toLedgerEntries = ledgerEntryRepository.findByWalletIdAndTxnRefAndEntryType(
                toWallet.getWalletId(), txnRef, LedgerEntry.EntryType.CREDIT);

        assertThat(fromLedgerEntries).hasSize(1);
        assertThat(toLedgerEntries).hasSize(1);
    }

    @Test
    void topUp_shouldCreditUserWalletAndDebitBankSettlement() {
        // Given: Create user wallet and bank settlement wallet
        String userId = UUID.randomUUID().toString();
        Wallet userWallet = Wallet.builder()
                .userId(userId)
                .balance(new BigDecimal("0.00"))
                .currency("INR")
                .ownerType(Wallet.OwnerType.USER)
                .build();
        walletRepository.save(userWallet);

        Wallet bankWallet = Wallet.builder()
                .systemName("BANK_SETTLEMENT")
                .balance(new BigDecimal("1000000.00"))
                .currency("INR")
                .ownerType(Wallet.OwnerType.SYSTEM)
                .build();
        walletRepository.save(bankWallet);

        // When: Top up 500
        String orderId = "ORDER" + System.currentTimeMillis();
        String txnRef = "TOPUP-" + orderId;
        BigDecimal amount = new BigDecimal("500.00");

        walletService.topUp(userId, txnRef, orderId, amount, "Test topup");

        // Then: User wallet credited, bank settlement debited
        Wallet updatedUserWallet = walletRepository.findByUserId(userId).orElseThrow();
        Wallet updatedBankWallet = walletRepository.findBySystemName("BANK_SETTLEMENT").orElseThrow();

        assertThat(updatedUserWallet.getBalance()).isEqualByComparingTo("500.00");
        assertThat(updatedBankWallet.getBalance()).isEqualByComparingTo("999500.00");

        // And: Ledger entries created
        var userCredit = ledgerEntryRepository.findByWalletIdAndTxnRefAndEntryType(
                userWallet.getWalletId(), txnRef, LedgerEntry.EntryType.CREDIT);
        var bankDebit = ledgerEntryRepository.findByWalletIdAndTxnRefAndEntryType(
                bankWallet.getWalletId(), txnRef, LedgerEntry.EntryType.DEBIT);

        assertThat(userCredit).hasSize(1);
        assertThat(bankDebit).hasSize(1);
    }
}