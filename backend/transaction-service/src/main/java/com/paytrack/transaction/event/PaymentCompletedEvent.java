package com.paytrack.transaction.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {

    private UUID eventId;
    private String txnRef;
    private String userId;
    private String type; // P2P, QR, RECHARGE
    private BigDecimal amount;
    private String currency;
    private String note;
    private String requestedCategory;
    private LocalDateTime completedAt;
}