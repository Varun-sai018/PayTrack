package com.paytrack.transaction.client;

import com.paytrack.transaction.UserContextHolder;
import feign.RequestInterceptor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Map;

@FeignClient(name = "wallet-service", configuration = WalletClient.Config.class)
public interface WalletClient {

    @PostMapping("/api/wallets/credit")
    void credit(@RequestParam BigDecimal amount);

    @PostMapping("/api/wallets/debit")
    void debit(@RequestParam BigDecimal amount);

    @PostMapping("/internal/wallets/transfer")
    TransferResponse internalTransfer(
            @RequestParam String txnRef,
            @RequestParam(required = false) String fromUserId,
            @RequestParam(required = false) String toUserId,
            @RequestParam BigDecimal amount,
            @RequestParam String description
    );

    @Configuration
    class Config {

        @Bean
        public RequestInterceptor userIdRequestInterceptor() {
            return request -> {
                String userId = UserContextHolder.getUserId();
                if (userId != null && !userId.isEmpty()) {
                    request.header("X-User-Id", userId);
                }
                // Add internal key for internal endpoints
                String internalKey = System.getProperty("INTERNAL_API_KEY");
                if (internalKey != null && !internalKey.isEmpty()) {
                    request.header("X-Internal-Key", internalKey);
                }
            };
        }
    }
}
