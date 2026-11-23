package com.paytrack.auth.service;

import com.paytrack.auth.dto.KycRequestDto;
import com.paytrack.auth.dto.KycStatusResponse;
import com.paytrack.auth.dto.RegisterRequest;
import com.paytrack.auth.dto.UserDto;
import com.paytrack.auth.entity.KycRequest;
import com.paytrack.auth.entity.RefreshToken;
import com.paytrack.auth.entity.User;
import com.paytrack.auth.repository.KycRequestRepository;
import com.paytrack.auth.repository.RefreshTokenRepository;
import com.paytrack.auth.repository.UserRepository;
import com.paytrack.auth.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KycRequestRepository kycRequestRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest validRegisterRequest;

    @BeforeEach
    void setUp() {
        validRegisterRequest = RegisterRequest.builder()
                .fullName("Test User")
                .email("test@example.com")
                .password("Password123")
                .phone("9876543210")
                .role("ROLE_CUSTOMER")
                .build();
    }

    @Test
    void register_shouldCreateUserAndReturnDto() {
        // Given
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhone(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        UserDto result = authService.register(validRegisterRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        assertThat(result.getFullName()).isEqualTo("Test User");
        assertThat(result.getPhone()).isEqualTo("9876543210");
        assertThat(result.getUpiId()).isNotNull();
        assertThat(result.getUpiId()).endsWith("@paytrack");
        assertThat(result.getKycStatus()).isEqualTo("NOT_SUBMITTED");
        assertThat(result.getAccountStatus()).isEqualTo("ACTIVE");

        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_shouldThrowWhenEmailExists() {
        // Given
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> authService.register(validRegisterRequest))
                .isInstanceOf(com.paytrack.auth.exception.BusinessException.class)
                .hasMessageContaining("Email already exists");
    }

    @Test
    void register_shouldThrowWhenPhoneExists() {
        // Given
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhone(anyString())).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> authService.register(validRegisterRequest))
                .isInstanceOf(com.paytrack.auth.exception.BusinessException.class)
                .hasMessageContaining("Phone number already exists");
    }

    @Test
    void submitKyc_shouldCreateKycRequestAndUpdateUserStatus() {
        // Given
        String userId = "test-user-id";
        KycRequestDto kycRequestDto = KycRequestDto.builder()
                .docType("AADHAAR")
                .docNumber("123456789012")
                .build();

        User user = User.builder()
                .userId(userId)
                .email("test@example.com")
                .fullName("Test User")
                .kycStatus(User.KycStatus.NOT_SUBMITTED)
                .build();

        when(userRepository.findByUserId(userId)).thenReturn(Optional.of(user));
        when(kycRequestRepository.save(any(KycRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        KycRequestDto result = authService.submitKyc(userId, kycRequestDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getDocType()).isEqualTo("AADHAAR");
        assertThat(result.getDocNumber()).isEqualTo("123456789012");

        ArgumentCaptor<KycRequest> kycCaptor = ArgumentCaptor.forClass(KycRequest.class);
        verify(kycRequestRepository).save(kycCaptor.capture());
        assertThat(kycCaptor.getValue().getDocType()).isEqualTo(KycRequest.DocType.AADHAAR);
        assertThat(kycCaptor.getValue().getDocLast4()).isEqualTo("9012");
        assertThat(kycCaptor.getValue().getStatus()).isEqualTo(KycRequest.KycRequestStatus.PENDING);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getKycStatus()).isEqualTo(User.KycStatus.PENDING);
    }

    @Test
    void submitKyc_shouldThrowWhenKycAlreadyPending() {
        // Given
        String userId = "test-user-id";
        KycRequestDto kycRequestDto = KycRequestDto.builder()
                .docType("AADHAAR")
                .docNumber("123456789012")
                .build();

        User user = User.builder()
                .userId(userId)
                .kycStatus(User.KycStatus.PENDING)
                .build();

        when(userRepository.findByUserId(userId)).thenReturn(Optional.of(user));

        // When/Then
        assertThatThrownBy(() -> authService.submitKyc(userId, kycRequestDto))
                .isInstanceOf(com.paytrack.auth.exception.BusinessException.class)
                .hasMessageContaining("KYC already pending review");
    }

    @Test
    void submitKyc_shouldThrowWhenKycAlreadyApproved() {
        // Given
        String userId = "test-user-id";
        KycRequestDto kycRequestDto = KycRequestDto.builder()
                .docType("AADHAAR")
                .docNumber("123456789012")
                .build();

        User user = User.builder()
                .userId(userId)
                .kycStatus(User.KycStatus.APPROVED)
                .build();

        when(userRepository.findByUserId(userId)).thenReturn(Optional.of(user));

        // When/Then
        assertThatThrownBy(() -> authService.submitKyc(userId, kycRequestDto))
                .isInstanceOf(com.paytrack.auth.exception.BusinessException.class)
                .hasMessageContaining("KYC already approved");
    }

    @Test
    void reviewKyc_shouldApproveAndUpdateUser() {
        // Given
        String kycId = "kyc-123";
        String userId = "user-123";
        String reviewerId = "admin-123";

        KycRequest kycRequest = KycRequest.builder()
                .id(kycId)
                .userId(userId)
                .docType(KycRequest.DocType.AADHAAR)
                .docLast4("9012")
                .status(KycRequest.KycRequestStatus.PENDING)
                .build();

        User user = User.builder()
                .userId(userId)
                .kycStatus(User.KycStatus.PENDING)
                .build();

        when(kycRequestRepository.findById(kycId)).thenReturn(Optional.of(kycRequest));
        when(userRepository.findByUserId(userId)).thenReturn(Optional.of(user));
        when(kycRequestRepository.save(any(KycRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        KycRequest result = authService.reviewKyc(kycId, "APPROVED", "Approved", reviewerId);

        // Then
        assertThat(result.getStatus()).isEqualTo(KycRequest.KycRequestStatus.APPROVED);
        assertThat(result.getRemarks()).isEqualTo("Approved");
        assertThat(result.getReviewedBy()).isEqualTo(reviewerId);
        assertThat(result.getReviewedAt()).isNotNull();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getKycStatus()).isEqualTo(User.KycStatus.APPROVED);
    }

    @Test
    void reviewKyc_shouldRejectAndUpdateUser() {
        // Given
        String kycId = "kyc-123";
        String userId = "user-123";
        String reviewerId = "admin-123";

        KycRequest kycRequest = KycRequest.builder()
                .id(kycId)
                .userId(userId)
                .docType(KycRequest.DocType.AADHAAR)
                .docLast4("9012")
                .status(KycRequest.KycRequestStatus.PENDING)
                .build();

        User user = User.builder()
                .userId(userId)
                .kycStatus(User.KycStatus.PENDING)
                .build();

        when(kycRequestRepository.findById(kycId)).thenReturn(Optional.of(kycRequest));
        when(userRepository.findByUserId(userId)).thenReturn(Optional.of(user));
        when(kycRequestRepository.save(any(KycRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        KycRequest result = authService.reviewKyc(kycId, "REJECTED", "Rejected", reviewerId);

        // Then
        assertThat(result.getStatus()).isEqualTo(KycRequest.KycRequestStatus.REJECTED);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getKycStatus()).isEqualTo(User.KycStatus.REJECTED);
    }

    @Test
    void reviewKyc_shouldThrowWhenAlreadyReviewed() {
        // Given
        String kycId = "kyc-123";

        KycRequest kycRequest = KycRequest.builder()
                .id(kycId)
                .status(KycRequest.KycRequestStatus.APPROVED)
                .build();

        when(kycRequestRepository.findById(kycId)).thenReturn(Optional.of(kycRequest));

        // When/Then
        assertThatThrownBy(() -> authService.reviewKyc(kycId, "APPROVED", "Approved", "admin"))
                .isInstanceOf(com.paytrack.auth.exception.BusinessException.class)
                .hasMessageContaining("KYC request already reviewed");
    }
}