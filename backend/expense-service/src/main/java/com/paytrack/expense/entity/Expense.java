package com.paytrack.expense.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

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

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "expense_id", nullable = false)
    @Builder.Default
    private String expenseId = UUID.randomUUID().toString();

    @Column(name = "wallet_id", nullable = true)
    private String walletId;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String category = "OTHER"; // FOOD, TRANSPORT, HOUSING, ENTERTAINMENT, UTILITIES, HEALTHCARE, SHOPPING, EDUCATION, OTHER

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "INR";

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED

    @Column(name = "source", nullable = false, length = 20)
    @Builder.Default
    private String source = "MANUAL"; // PAYMENT or MANUAL

    @Column(name = "category_source", nullable = false, length = 20)
    @Builder.Default
    private String categorySource = "MANUAL"; // USER, RULE, DEFAULT, MANUAL

    @Column(name = "txn_ref", nullable = true, unique = true, length = 36)
    private String txnRef;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        if (currency == null) {
            currency = "INR";
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
        if (expenseId == null) {
            expenseId = UUID.randomUUID().toString();
        }
    }
}
