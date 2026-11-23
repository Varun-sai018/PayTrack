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
public class RechargeRequest {

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^\\d{10}$", message = "Mobile number must be 10 digits")
    private String mobileNumber;

    @NotNull(message = "Operator is required")
    @Pattern(regexp = "(AIRTEL|JIO|VI|BSNL)", message = "Operator must be AIRTEL, JIO, VI, or BSNL")
    private String operator;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "10", message = "Recharge amount must be at least 10.00")
    @DecimalMax(value = "2000", message = "Recharge amount must not exceed 2000.00")
    private BigDecimal amount;

    private String category; // optional
}