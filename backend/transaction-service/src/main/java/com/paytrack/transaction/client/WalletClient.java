package com.paytrack.transaction.client;

import com.paytrack.transaction.UserContextHolder;
import com.paytrack.transaction.dto.ApiResponse;
import com.paytrack.transaction.dto.InternalTransferRequest;
import com.paytrack.transaction.dto.InternalTransferResponse;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "wallet-service", configuration = WalletClient.Config.class)
public interface WalletClient {

    @PostMapping("/internal/wallets/transfer")
    ApiResponse<InternalTransferResponse> internalTransfer(
            @RequestHeader("X-Internal-Key") String internalKey,
            @RequestBody InternalTransferRequest request
    );

    @Configuration
    class Config {

        @Value("${internal.api.key:paytrack-internal-api-key-2026}")
        private String internalApiKey;

        @Bean
        public RequestInterceptor userIdRequestInterceptor() {
            return request -> {
                String userId = UserContextHolder.getUserId();
                if (userId != null && !userId.isEmpty()) {
                    request.header("X-User-Id", userId);
                }
                if (internalApiKey != null && !internalApiKey.isEmpty()) {
                    request.header("X-Internal-Key", internalApiKey);
                }
            };
        }
    }
}
