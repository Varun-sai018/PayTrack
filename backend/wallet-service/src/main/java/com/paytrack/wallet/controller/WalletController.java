package com.paytrack.wallet.controller;

import com.paytrack.wallet.dto.ApiResponse;
import com.paytrack.wallet.dto.CreateWalletRequest;
import com.paytrack.wallet.dto.UpdateBalanceRequest;
import com.paytrack.wallet.dto.WalletDto;
import com.paytrack.wallet.service.WalletService;
import com.paytrack.wallet.UserContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<WalletDto>> getMyWallet() {
        String userId = UserContextHolder.getUserId();
        WalletDto wallet = walletService.getWallet(userId);
        return ResponseEntity.ok(ApiResponse.success(wallet, "Wallet retrieved successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WalletDto>> createWallet(@RequestBody CreateWalletRequest request) {
        WalletDto wallet = walletService.createWallet(request);
        return ResponseEntity.ok(ApiResponse.success(wallet, "Wallet created successfully"));
    }

    @PostMapping("/credit")
    public ResponseEntity<ApiResponse<WalletDto>> credit(@RequestParam BigDecimal amount) {
        String userId = UserContextHolder.getUserId();
        WalletDto wallet = walletService.credit(userId, amount);
        return ResponseEntity.ok(ApiResponse.success(wallet, "Wallet credited successfully"));
    }

    @PostMapping("/debit")
    public ResponseEntity<ApiResponse<WalletDto>> debit(@RequestParam BigDecimal amount) {
        String userId = UserContextHolder.getUserId();
        WalletDto wallet = walletService.debit(userId, amount);
        return ResponseEntity.ok(ApiResponse.success(wallet, "Wallet debited successfully"));
    }

    @PutMapping("/balance")
    public ResponseEntity<ApiResponse<WalletDto>> updateBalance(@RequestBody UpdateBalanceRequest request) {
        String userId = UserContextHolder.getUserId();
        WalletDto wallet = walletService.updateBalance(request, userId);
        return ResponseEntity.ok(ApiResponse.success(wallet, "Wallet balance updated successfully"));
    }
}
