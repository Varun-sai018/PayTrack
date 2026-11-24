package com.paytrack.wallet.controller;

import com.paytrack.wallet.dto.ApiResponse;
import com.paytrack.wallet.dto.SandboxPayRequest;
import com.paytrack.wallet.dto.SandboxPayResponse;
import com.paytrack.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gateway-sandbox")
@RequiredArgsConstructor
public class SandboxPaymentController {

    private final WalletService walletService;

    @PostMapping("/pay")
    public ResponseEntity<ApiResponse<SandboxPayResponse>> pay(
            @Valid @RequestBody SandboxPayRequest request) {
        SandboxPayResponse response = walletService.sandboxPay(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Sandbox payment processed"));
    }
}
