package com.paytrack.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrPayRequest {

    @NotBlank(message = "QR payload is required")
    @Pattern(regexp = "^upi://pay?pa=[a-z0-9.]+@paytrack(&pn=[^&]*)?(&am=[^&]*)?(&cu=INR)?$", message = "Invalid QR payload format")
    private String qrPayload;

    private BigDecimal amount; // if not in QR, amount must be provided

    private String note;

    private String category; // optional

    private String requestHash; // idempotency key request hash
}