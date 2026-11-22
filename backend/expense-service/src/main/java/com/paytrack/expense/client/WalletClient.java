package com.paytrack.expense.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "wallet-service")
public interface WalletClient {

    @GetMapping("/api/wallets/me")
    com.paytrack.expense.dto.ApiResponse<com.paytrack.expense.dto.WalletDto> getMyWallet();
}
