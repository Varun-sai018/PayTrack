package com.paytrack.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycRequestDto {

    @NotBlank(message = "Document type is required")
    private String docType;

    @NotBlank(message = "Document number is required")
    @Pattern(regexp = "^[A-Za-z0-9]{6,12}$", message = "Document number must be 6-12 alphanumeric characters")
    private String docNumber;
}