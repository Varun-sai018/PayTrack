package com.paytrack.auth;

import com.paytrack.auth.dto.ApiResponse;
import com.paytrack.auth.dto.JwtResponse;
import com.paytrack.auth.dto.KycRequestDto;
import com.paytrack.auth.dto.KycStatusResponse;
import com.paytrack.auth.dto.LoginRequest;
import com.paytrack.auth.dto.RefreshTokenRequest;
import com.paytrack.auth.dto.RegisterRequest;
import com.paytrack.auth.dto.UserDto;
import com.paytrack.auth.dto.UserLookupResponse;
import com.paytrack.auth.entity.KycRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserDto>> register(@Valid @RequestBody RegisterRequest registerRequest) {
        UserDto user = authService.register(registerRequest);
        return ResponseEntity.ok(ApiResponse.success(user, "User registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<JwtResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        JwtResponse jwtResponse = authService.login(loginRequest);
        return ResponseEntity.ok(ApiResponse.success(jwtResponse, "Login successful"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, String>>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        String accessToken = authService.refreshAccessToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(Map.of("accessToken", accessToken), "Token refreshed"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/kyc")
    public ResponseEntity<ApiResponse<KycRequestDto>> submitKyc(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody KycRequestDto request) {
        KycRequestDto result = authService.submitKyc(userId, request);
        return ResponseEntity.ok(ApiResponse.success(result, "KYC submitted successfully"));
    }

    @GetMapping("/kyc/status")
    public ResponseEntity<ApiResponse<KycStatusResponse>> getKycStatus(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        KycStatusResponse status = authService.getKycStatus(userId);
        return ResponseEntity.ok(ApiResponse.success(status, "KYC status retrieved"));
    }

    @GetMapping("/users/lookup")
    public ResponseEntity<ApiResponse<UserLookupResponse>> lookupUser(@RequestParam String upiId) {
        UserLookupResponse response = authService.lookupUserByUpi(upiId);
        return ResponseEntity.ok(ApiResponse.success(response, "User found"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> getCurrentUser(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UserDto user = authService.getCurrentUser(userId);
        return ResponseEntity.ok(ApiResponse.success(user, "User details retrieved"));
    }

    // Admin endpoints
    @GetMapping("/admin/users")
    public ResponseEntity<ApiResponse<List<UserDto>>> getAdminUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        List<UserDto> users = authService.getAllUsers(page, size);
        return ResponseEntity.ok(ApiResponse.success(users, "Users retrieved"));
    }

    @PutMapping("/admin/users/{id}/status")
    public ResponseEntity<ApiResponse<UserDto>> updateUserStatus(
            @PathVariable String id,
            @RequestParam String status) {
        UserDto user = authService.updateUserStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success(user, "User status updated"));
    }

    @PutMapping("/admin/users/{id}/kyc")
    public ResponseEntity<ApiResponse<UserDto>> updateUserKyc(
            @PathVariable String id,
            @RequestParam String status) {
        UserDto user = authService.updateUserKycStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success(user, "User KYC status updated"));
    }

    @GetMapping("/admin/kyc")
    public ResponseEntity<ApiResponse<List<KycRequest>>> getAdminKyc(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        List<KycRequest> requests = authService.getKycRequests(status);
        return ResponseEntity.ok(ApiResponse.success(requests, "KYC requests retrieved"));
    }

    @PutMapping("/admin/kyc/{id}/review")
    public ResponseEntity<ApiResponse<KycRequest>> reviewKyc(
            @PathVariable String id,
            @RequestParam String decision,
            @RequestParam(required = false) String remarks,
            @RequestHeader(value = "X-User-Id", required = false) String reviewerId) {
        KycRequest request = authService.reviewKyc(id, decision, remarks, reviewerId);
        return ResponseEntity.ok(ApiResponse.success(request, "KYC reviewed"));
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Auth Service is running");
    }
}