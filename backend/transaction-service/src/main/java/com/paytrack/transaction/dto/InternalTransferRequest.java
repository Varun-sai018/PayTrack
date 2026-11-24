package com.paytrack.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalTransferRequest {
    private String txnRef;
    private String fromUserId;
    private String fromSystemWallet;
    private String toUserId;
    private String toSystemWallet;
    private BigDecimal amount;
    private String description;
}
