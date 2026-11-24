package com.paytrack.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SandboxPayResponse {
    private String status;
    private String paymentId;
    private String signature;
    private String failureReason;
}
