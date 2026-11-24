package com.paytrack.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {

    private String txnRef;
    private String status;
    private String type;
    private BigDecimal amount;
    private String category;
    private String receiverUpiId;
    private BigDecimal balanceAfter;
    private String failureReason;
    private LocalDateTime createdAt;
}
