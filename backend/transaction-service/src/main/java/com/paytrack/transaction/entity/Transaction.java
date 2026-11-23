package com.paytrack.transaction.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

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

    @Column(name = "txn_ref", nullable = true, unique = true)
    private String txnRef;

    @Column(name = "idempotency_key", nullable = true, unique = true)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = true)
    private String requestHash;

    @Column(name = "receiver_user_id", nullable = true)
    private String receiverUserId;

    @Column(name = "receiver_upi_id", nullable = true, length = 255)
    private String receiverUpiId;

    @Column(name = "failure_reason", nullable = true, columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "requested_category", nullable = true, length = 50)
    private String requestedCategory;

    @Column(name = "event_published", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean eventPublished = false;

    @Column(nullable = false)
    private String type; // Withdrawal, Deposit, Transfer, P2P, QR, RECHARGE

    @Column(nullable = false)
    private String status; // PENDING, COMPLETED, FAILED

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 255)
    private String description;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        if (status == null) {
            status = "PENDING";
        }
        if (txnRef == null || txnRef.isEmpty()) {
            // Generate txn_ref: TXN + yyyyMMdd + 6 random digits
            String datePart = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            String randomPart = String.format("%06d", new java.util.Random().nextInt(999999) + 1);
            txnRef = "TXN" + datePart + randomPart;
            // Handle collision by regenerating
            while (true) {
                try {
                    // Check if already exists - this is a simplified check; in production would use proper DB constraint handling
                    if (!Transaction.entityManager().createQuery(
                            "SELECT COUNT(t) FROM Transaction t WHERE t.txnRef = :txnRef", Long.class)
                            .setParameter("txnRef", txnRef).getSingleResult() > 0) {
                        break;
                    }
                    String randomPart2 = String.format("%06d", new java.util.Random().nextInt(999999) + 1);
                    txnRef = "TXN" + datePart + randomPart2;
                } catch (Exception e) {
                    break; // If error, use generated value
                }
            }
        }
    }

    @PreUpdate
    protected void onUpdate() {
        // No op
    }
}
