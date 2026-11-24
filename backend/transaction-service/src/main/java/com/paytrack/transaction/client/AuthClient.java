package com.paytrack.transaction.client;

import com.paytrack.transaction.dto.ApiResponse;
import com.paytrack.transaction.dto.UserLookupDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "auth-service", configuration = WalletClient.Config.class)
public interface AuthClient {

    @GetMapping("/internal/auth/users/by-upi/{upiId}")
    ApiResponse<UserLookupDto> lookupUserByUpi(
            @PathVariable("upiId") String upiId,
            @RequestHeader("X-Internal-Key") String internalKey
    );

    @GetMapping("/api/auth/me")
    ApiResponse<UserLookupDto> getCurrentUser(
            @RequestHeader("X-User-Id") String userId
    );
}
