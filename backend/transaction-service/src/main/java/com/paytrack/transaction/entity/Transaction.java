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

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "txn_ref", nullable = false, unique = true, length = 36)
    private String txnRef;

    @Column(name = "idempotency_key", nullable = true, length = 64)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = true, length = 64)
    private String requestHash;

    @Column(name = "receiver_user_id", nullable = true, length = 36)
    private String receiverUserId;

    @Column(name = "receiver_upi_id", nullable = true, length = 255)
    private String receiverUpiId;

    @Column(name = "failure_reason", nullable = true, columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "requested_category", nullable = true, length = 50)
    private String requestedCategory;

    @Column(name = "event_published", nullable = false)
    @Builder.Default
    private boolean eventPublished = false;

    @Column(nullable = false, length = 20)
    private String type; // P2P, QR, RECHARGE, DEBIT, CREDIT, TRANSFER

    @Column(nullable = false, length = 20)
    private String status; // PENDING, COMPLETED, FAILED

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "INR";

    @Column(nullable = true, length = 255)
    private String description;

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
    }
}
