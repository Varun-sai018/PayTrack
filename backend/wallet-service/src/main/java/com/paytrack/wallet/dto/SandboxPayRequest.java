package com.paytrack.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SandboxPayRequest {
    @NotBlank(message = "Order ID is required")
    private String orderId;

    @NotBlank(message = "Payment method is required (UPI or CARD)")
    private String method;

    @NotBlank(message = "Instrument is required")
    private String instrument;
}
