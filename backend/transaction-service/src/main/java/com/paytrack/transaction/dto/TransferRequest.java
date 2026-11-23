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
public class TransferRequest {

    @NotBlank(message = "Receiver UPI ID is required")
    @Pattern(regexp = "^[a-z0-9.]+@paytrack$", message = "Invalid UPI ID format. Expected: first.last@paytrack")
    private String receiverUpiId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Amount must be at least 1.00")
    @DecimalMax(value = "10000.00", message = "Amount must not exceed 10000.00")
    private BigDecimal amount;

    private String note;

    private String category; // Optional: one of the category enum values
}