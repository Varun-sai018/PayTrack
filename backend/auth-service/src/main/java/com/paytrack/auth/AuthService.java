package com.paytrack.auth;

import com.paytrack.auth.dto.JwtResponse;
import com.paytrack.auth.dto.LoginRequest;
import com.paytrack.auth.dto.RegisterRequest;
import com.paytrack.auth.dto.UserDto;
import com.paytrack.auth.entity.User;
import com.paytrack.auth.event.UserRegisteredEvent;
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

import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public UserDto register(RegisterRequest registerRequest) {
        log.info("Registering new user: {}", registerRequest.getEmail());

        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = User.builder()
                .userId(UUID.randomUUID().toString())
                .email(registerRequest.getEmail())
                .fullName(registerRequest.getFullName())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .role(registerRequest.getRole() != null ? registerRequest.getRole() : "ROLE_CUSTOMER")
                .active(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getEmail());

        // Publish user registered event
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

        return convertToDto(savedUser);
    }

    public JwtResponse login(LoginRequest loginRequest) {
        log.info("User login attempt: {}", loginRequest.getEmail());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        log.info("User logged in successfully: {}", user.getEmail());

        return JwtResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresAt(jwtUtil.getExpirationTime(accessToken))
                .user(convertToDto(user))
                .build();
    }

    public UserDto getCurrentUser(String userId) {
        return userRepository.findByUserId(userId)
                .map(this::convertToDto)
                .orElseThrow(() -> new RuntimeException("User not found with userId: " + userId));
    }

    private UserDto convertToDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
