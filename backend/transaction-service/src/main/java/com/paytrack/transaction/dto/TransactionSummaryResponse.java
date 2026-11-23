package com.paytrack.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSummaryResponse {

    private LocalDate date;
    private Long totalTransactions;
    private BigDecimal totalVolume;
    private Map<String, Long> countByType;
    private Map<String, BigDecimal> volumeByType;
    private List<TransactionDto> recentTransactions;
}