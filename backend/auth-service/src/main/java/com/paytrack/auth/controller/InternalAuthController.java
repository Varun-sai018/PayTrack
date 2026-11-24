package com.paytrack.auth.controller;

import com.paytrack.auth.AuthService;
import com.paytrack.auth.dto.ApiResponse;
import com.paytrack.auth.dto.UserLookupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalAuthController {

    private final AuthService authService;

    @GetMapping("/users/by-upi/{upiId}")
    public ResponseEntity<ApiResponse<UserLookupResponse>> getByUpi(@PathVariable("upiId") String upiId) {
        UserLookupResponse userLookup = authService.lookupUserByUpi(upiId);
        return ResponseEntity.ok(ApiResponse.success(userLookup, "User found"));
    }
}
