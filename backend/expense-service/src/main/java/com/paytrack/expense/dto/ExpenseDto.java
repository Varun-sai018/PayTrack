package com.paytrack.expense.dto;

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
public class ExpenseDto {

    private Long id;
    private String userId;
    private String expenseId;
    private String walletId;
    private String category;
    private String description;
    private BigDecimal amount;
    private String status;
    private LocalDateTime timestamp;
}
