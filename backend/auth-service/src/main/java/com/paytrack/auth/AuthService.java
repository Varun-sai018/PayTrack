package com.paytrack.auth;

import com.paytrack.auth.dto.JwtResponse;
import com.paytrack.auth.dto.KycRequestDto;
import com.paytrack.auth.dto.KycStatusResponse;
import com.paytrack.auth.dto.LoginRequest;
import com.paytrack.auth.dto.RegisterRequest;
import com.paytrack.auth.dto.UserDto;
import com.paytrack.auth.dto.UserLookupResponse;
import com.paytrack.auth.entity.KycRequest;
import com.paytrack.auth.entity.RefreshToken;
import com.paytrack.auth.entity.User;
import com.paytrack.auth.event.UserRegisteredEvent;
import com.paytrack.auth.exception.BusinessException;
import com.paytrack.auth.exception.ResourceNotFoundException;
import com.paytrack.auth.repository.KycRequestRepository;
import com.paytrack.auth.repository.RefreshTokenRepository;
import com.paytrack.auth.repository.UserRepository;
import com.paytrack.auth.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final KycRequestRepository kycRequestRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public UserDto register(RegisterRequest registerRequest) {
        log.info("Registering new user: {}", registerRequest.getEmail());

        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new BusinessException("Email already exists");
        }

        if (userRepository.existsByPhone(registerRequest.getPhone())) {
            throw new BusinessException("Phone number already exists");
        }

        // Generate UPI ID
        String upiId = generateUpiId(registerRequest.getFullName());

        User user = User.builder()
                .userId(UUID.randomUUID().toString())
                .email(registerRequest.getEmail())
                .fullName(registerRequest.getFullName())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .phone(registerRequest.getPhone())
                .upiId(upiId)
                .role(registerRequest.getRole() != null ? registerRequest.getRole() : "ROLE_CUSTOMER")
                .active(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getEmail());

        // Publish user registered event
        try {
            if (rabbitTemplate != null) {
                UserRegisteredEvent event = UserRegisteredEvent.fromUser(
                        savedUser.getUserId(),
                        savedUser.getEmail(),
                        savedUser.getFullName(),
                        savedUser.getRole()
                );
                rabbitTemplate.convertAndSend(
                        "notification.exchange",
                        "user.registered",
                        event
                );
                log.info("Published user.registered event for user: {}", savedUser.getEmail());
            }
        } catch (Exception e) {
            log.warn("Could not publish user.registered event to RabbitMQ: {}", e.getMessage());
        }

        return convertToDto(savedUser);
    }

    public JwtResponse login(LoginRequest loginRequest) {
        log.info("User login attempt: {}", loginRequest.getEmail());

        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new BusinessException("User not found"));

        if (user.getAccountStatus() == User.AccountStatus.BLOCKED) {
            throw new BusinessException("Account is blocked");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        // Store refresh token
        storeRefreshToken(user.getUserId(), refreshToken);

        log.info("User logged in successfully: {}", user.getEmail());

        return JwtResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresAt(jwtUtil.getExpirationTime(accessToken))
                .user(convertToDto(user))
                .build();
    }

    @Transactional
    public String refreshAccessToken(String refreshToken) {
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(hashToken(refreshToken))
                .orElseThrow(() -> new BusinessException("Invalid refresh token"));

        if (storedToken.isExpired() || storedToken.isRevoked()) {
            throw new BusinessException("Refresh token expired or revoked");
        }

        User user = userRepository.findByUserId(storedToken.getUserId())
                .orElseThrow(() -> new BusinessException("User not found"));

        if (user.getAccountStatus() == User.AccountStatus.BLOCKED) {
            throw new BusinessException("Account is blocked");
        }

        return jwtUtil.generateAccessToken(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(hashToken(refreshToken))
                .orElseThrow(() -> new BusinessException("Invalid refresh token"));
        storedToken.revoke();
        refreshTokenRepository.save(storedToken);
    }

    @Transactional
    public KycRequestDto submitKyc(String userId, KycRequestDto request) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getKycStatus() == User.KycStatus.PENDING) {
            throw new BusinessException("KYC already pending review");
        }

        if (user.getKycStatus() == User.KycStatus.APPROVED) {
            throw new BusinessException("KYC already approved");
        }

        // Create KYC request
        KycRequest kycRequest = KycRequest.builder()
                .userId(userId)
                .docType(KycRequest.DocType.valueOf(request.getDocType()))
                .docLast4(request.getDocNumber().substring(request.getDocNumber().length() - 4))
                .status(KycRequest.KycRequestStatus.PENDING)
                .build();

        kycRequestRepository.save(kycRequest);

        // Update user KYC status
        user.setKycStatus(User.KycStatus.PENDING);
        userRepository.save(user);

        return request;
    }

    public KycStatusResponse getKycStatus(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        KycRequest latestRequest = kycRequestRepository.findLatestByUserId(userId).orElse(null);

        return KycStatusResponse.builder()
                .kycStatus(user.getKycStatus().name())
                .remarks(latestRequest != null ? latestRequest.getRemarks() : null)
                .build();
    }

    public UserDto getCurrentUser(String userId) {
        return userRepository.findByUserId(userId)
                .map(this::convertToDto)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with userId: " + userId));
    }

    public UserLookupResponse lookupUserByUpi(String upiId) {
        User user = userRepository.findByUpiId(upiId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String maskedName = maskName(user.getFullName());

        return UserLookupResponse.builder()
                .userId(user.getUserId())
                .maskedName(maskedName)
                .upiId(user.getUpiId())
                .accountStatus(user.getAccountStatus() != null ? user.getAccountStatus().name() : null)
                .kycStatus(user.getKycStatus() != null ? user.getKycStatus().name() : null)
                .build();
    }

    public List<UserDto> getAllUsers(int page, int size) {
        return userRepository.findAll().stream()
                .skip((long) page * size)
                .limit(size)
                .map(this::convertToDto)
                .toList();
    }

    public UserDto updateUserStatus(String userId, String status) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setAccountStatus(User.AccountStatus.valueOf(status));
        userRepository.save(user);

        return convertToDto(user);
    }

    public List<KycRequest> getKycRequests(String status) {
        if (status != null && !status.isEmpty()) {
            return kycRequestRepository.findByStatus(KycRequest.KycRequestStatus.valueOf(status));
        }
        return kycRequestRepository.findAll();
    }

    @Transactional
    public KycRequest reviewKyc(String kycId, String decision, String remarks, String reviewerId) {
        KycRequest kycRequest = kycRequestRepository.findById(kycId)
                .orElseThrow(() -> new ResourceNotFoundException("KYC request not found"));

        if (kycRequest.getStatus() != KycRequest.KycRequestStatus.PENDING) {
            throw new BusinessException("KYC request already reviewed");
        }

        User user = userRepository.findByUserId(kycRequest.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        kycRequest.setStatus(KycRequest.KycRequestStatus.valueOf(decision));
        kycRequest.setRemarks(remarks);
        kycRequest.setReviewedBy(reviewerId);
        kycRequest.setReviewedAt(LocalDateTime.now());

        if (decision.equals("APPROVED")) {
            user.setKycStatus(User.KycStatus.APPROVED);
        } else {
            user.setKycStatus(User.KycStatus.REJECTED);
        }

        userRepository.save(user);
        kycRequestRepository.save(kycRequest);

        return kycRequest;
    }

    private String generateUpiId(String fullName) {
        String[] parts = fullName.trim().toLowerCase().split("\\s+");
        String first = parts.length > 0 ? parts[0] : "";
        String last = parts.length > 1 ? parts[parts.length - 1] : first;

        String baseUpiId = first + "." + last + "@paytrack";

        if (!userRepository.existsByUpiId(baseUpiId)) {
            return baseUpiId;
        }

        // Add random digits if collision
        String randomDigits = String.valueOf((int) (Math.random() * 1000));
        return first + "." + last + randomDigits + "@paytrack";
    }

    private String maskName(String fullName) {
        String[] parts = fullName.split("\\s+");
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) masked.append(" ");
            String part = parts[i];
            if (!part.isEmpty()) {
                masked.append(part.charAt(0)).append("***");
            }
        }
        return masked.toString();
    }

    private void storeRefreshToken(String userId, String token) {
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(hashToken(token))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(refreshToken);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private UserDto convertToDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .phone(user.getPhone())
                .upiId(user.getUpiId())
                .kycStatus(user.getKycStatus().name())
                .accountStatus(user.getAccountStatus().name())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}