package com.paytrack.wallet.controller;

import com.paytrack.wallet.dto.ApiResponse;
import com.paytrack.wallet.dto.InternalTransferRequest;
import com.paytrack.wallet.dto.InternalTransferResponse;
import com.paytrack.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/wallets")
@RequiredArgsConstructor
public class InternalWalletController {

    private final WalletService walletService;

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<InternalTransferResponse>> transfer(
            @Valid @RequestBody InternalTransferRequest request) {
        InternalTransferResponse response = walletService.transfer(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Transfer completed successfully"));
    }
}
