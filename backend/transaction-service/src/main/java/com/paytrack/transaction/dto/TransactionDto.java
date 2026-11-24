package com.paytrack.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionDto {

    private Long id;
    private String txnRef;
    private String userId;
    private String receiverUserId;
    private String receiverUpiId;
    private String description;
    private BigDecimal amount;
    private String currency;
    private String type;
    private String status;
    private String failureReason;
    private String requestedCategory;
    private LocalDateTime timestamp;
}
