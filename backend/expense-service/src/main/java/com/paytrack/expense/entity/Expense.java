package com.paytrack.expense.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "expenses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String expenseId;

    @Column(nullable = false)
    private String walletId;

    @Column(nullable = false)
    private String category; // FOOD, TRANSPORT, HOUSING, ENTERTAINMENT, UTILITIES, HEALTHCARE, SHOPPING, EDUCATION, OTHER

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String status; // PENDING, APPROVED, REJECTED

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "source", nullable = false)
    private String source; // PAYMENT or MANUAL

    @Column(name = "category_source", nullable = false)
    private String categorySource; // USER, RULE, DEFAULT, MANUAL

    @Column(name = "txn_ref", nullable = true, unique = true)
    private String txnRef;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        if (status == null) {
            status = "PENDING";
        }
        if (source == null) {
            source = "MANUAL";
        }
        if (categorySource == null) {
            categorySource = "MANUAL";
        }
        if (category == null) {
            category = "OTHER";
        }
    }
}
