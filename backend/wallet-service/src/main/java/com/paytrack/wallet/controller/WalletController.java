package com.paytrack.wallet.controller;

import com.paytrack.wallet.UserContextHolder;
import com.paytrack.wallet.dto.*;
import com.paytrack.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<WalletDto>> getMyWallet() {
        String userId = UserContextHolder.getUserId();
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User context missing", "UNAUTHORIZED"));
        }
        WalletDto wallet = walletService.getWallet(userId);
        return ResponseEntity.ok(ApiResponse.success(wallet, "Wallet retrieved successfully"));
    }

    @GetMapping("/me/statement")
    public ResponseEntity<ApiResponse<Page<StatementEntryDto>>> getMyStatement(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String type) {
        String userId = UserContextHolder.getUserId();
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User context missing", "UNAUTHORIZED"));
        }
        Page<StatementEntryDto> statement = walletService.getStatement(userId, page, size, type);
        return ResponseEntity.ok(ApiResponse.success(statement, "Statement retrieved successfully"));
    }

    @PostMapping("/topup/orders")
    public ResponseEntity<ApiResponse<TopupOrderResponse>> createTopupOrder(
            @Valid @RequestBody TopupOrderRequest request) {
        String userId = UserContextHolder.getUserId();
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User context missing", "UNAUTHORIZED"));
        }
        TopupOrderResponse response = walletService.createTopupOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Top-up order created successfully"));
    }

    @PostMapping("/topup/verify")
    public ResponseEntity<ApiResponse<TopupVerifyResponse>> verifyTopup(
            @Valid @RequestBody TopupVerifyRequest request) {
        String userId = UserContextHolder.getUserId();
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User context missing", "UNAUTHORIZED"));
        }
        TopupVerifyResponse response = walletService.verifyTopup(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Top-up verified and credited successfully"));
    }
}
