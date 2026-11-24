package com.paytrack.wallet.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"wallet_id", "txn_ref", "entry_type"})
}, indexes = {
        @Index(name = "idx_ledger_wallet_created", columnList = "wallet_id, created_at"),
        @Index(name = "idx_ledger_txn_ref", columnList = "txn_ref")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "txn_ref", nullable = false)
    private String txnRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public enum EntryType {
        DEBIT, CREDIT
    }
}
