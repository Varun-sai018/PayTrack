package com.paytrack.auth.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.paytrack.auth.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration:86400000}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;

    @Value("${jwt.issuer:ecommerce-app}")
    private String issuer;

    public String generateAccessToken(User user) {
        return generateToken(user, accessTokenExpiration);
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, refreshTokenExpiration);
    }

    private String generateToken(User user, long expiration) {
        return JWT.create()
                .withSubject(user.getEmail())
                .withClaim("userId", user.getUserId())
                .withClaim("role", user.getRole())
                .withClaim("email", user.getEmail())
                .withClaim("kycStatus", user.getKycStatus().name())
                .withExpiresAt(new Date(System.currentTimeMillis() + expiration))
                .withIssuer(issuer)
                .sign(Algorithm.HMAC256(secret.getBytes(StandardCharsets.UTF_8)));
    }

    public boolean validateToken(String token) {
        try {
            JWT.require(Algorithm.HMAC256(secret.getBytes(StandardCharsets.UTF_8))).build().verify(token);
            return true;
        } catch (Exception ex) {
            log.error("JWT validation failed: {}", ex.getMessage());
            return false;
        }
    }

    public LocalDateTime getExpirationTime(String token) {
        DecodedJWT decoded = JWT.decode(token);
        return decoded.getExpiresAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    public String extractEmail(String token) {
        return JWT.decode(token).getSubject();
    }

    public String extractUserId(String token) {
        return JWT.decode(token).getClaim("userId").asString();
    }

    public String extractRole(String token) {
        return JWT.decode(token).getClaim("role").asString();
    }
}
