package com.paytrack.transaction.client;

import com.paytrack.transaction.UserContextHolder;
import feign.RequestInterceptor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient(name = "wallet-service", configuration = WalletClient.Config.class)
public interface WalletClient {

    @PostMapping("/api/wallets/credit")
    void credit(@RequestParam BigDecimal amount);

    @PostMapping("/api/wallets/debit")
    void debit(@RequestParam BigDecimal amount);

    @Configuration
    class Config {

        @Bean
        public RequestInterceptor userIdRequestInterceptor() {
            return request -> {
                String userId = UserContextHolder.getUserId();
                if (userId != null && !userId.isEmpty()) {
                    request.header("X-User-Id", userId);
                }
            };
        }
    }
}
