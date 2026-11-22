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
    private String userId;
    private String transactionId;
    private String walletId;
    private String description;
    private BigDecimal amount;
    private String type;
    private LocalDateTime timestamp;
    private String status;
}
