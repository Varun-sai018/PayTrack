package com.paytrack.transaction.client;

import com.paytrack.transaction.dto.UserLookupDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "auth-service", url = "${auth.service.url:http://localhost:8081}")
public interface AuthClient {

    @GetMapping("/internal/auth/users/by-upi/{upiId}")
    UserLookupDto lookupUserByUpi(
            @PathVariable String upiId,
            @RequestHeader("X-Internal-Key") String internalKey
    );

    @GetMapping("/auth/me")
    UserLookupDto getCurrentUser(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Internal-Key") String internalKey
    );
}