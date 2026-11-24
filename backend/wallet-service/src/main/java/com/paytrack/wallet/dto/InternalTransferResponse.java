package com.paytrack.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalTransferResponse {
    private String txnRef;
    private String status;
    private BigDecimal senderBalanceAfter;
}
