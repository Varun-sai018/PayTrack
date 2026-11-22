package com.paytrack.transaction.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String transactionId;

    @Column(nullable = false)
    private String walletId;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String type; // Withdrawal, Deposit, Transfer, etc.

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private String status; // PENDING, COMPLETED, FAILED

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        if (status == null) {
            status = "PENDING";
        }
    }
}
