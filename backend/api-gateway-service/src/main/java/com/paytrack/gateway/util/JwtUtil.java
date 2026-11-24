package com.paytrack.gateway.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Slf4j
public class JwtUtil {

    private static final String ROLE_TAG = "role";
    private static final String ROLES_TAG = "roles";
    private static final String EMAIL_TAG = "email";

    @Value("${jwt.secret}")
    private String secret;

    private DecodedJWT getDecodedToken(String token) throws JWTVerificationException {
        Algorithm algorithm = Algorithm.HMAC256(secret.getBytes(StandardCharsets.UTF_8));
        return JWT.require(algorithm).build().verify(token);
    }

    public boolean validateToken(String token) {
        try {
            getDecodedToken(token);
            return true;
        } catch (JWTVerificationException ex) {
            log.error("JWT validation failed: {}", ex.getMessage());
            return false;
        }
    }

    public String retrieveEmailFromToken(String token) {
        var decoded = getDecodedToken(token);
        var emailClaim = decoded.getClaim(EMAIL_TAG);
        if (!emailClaim.isMissing() && !emailClaim.isNull()) {
            return emailClaim.asString();
        }
        return decoded.getSubject();
    }

    public String retrieveUserIdFromToken(String token) {
        var decoded = getDecodedToken(token);
        var userIdClaim = decoded.getClaim("userId");
        if (!userIdClaim.isMissing() && !userIdClaim.isNull()) {
            return userIdClaim.asString();
        }
        return decoded.getSubject();
    }

    public List<String> retrieveRolesFromToken(String token) {
        var decoded = getDecodedToken(token);
        for (String tag : new String[]{ROLE_TAG, ROLES_TAG}) {
            var claim = decoded.getClaim(tag);
            if (claim.isMissing() || claim.isNull()) continue;
            List<String> list = claim.asList(String.class);
            if (list != null && !list.isEmpty()) {
                return list;
            }
            String single = claim.asString();
            if (single != null && !single.isBlank()) {
                return List.of(single);
            }
        }
        return List.of();
    }

    public String retrieveKycStatusFromToken(String token) {
        var decoded = getDecodedToken(token);
        var kycClaim = decoded.getClaim("kycStatus");
        if (!kycClaim.isMissing() && !kycClaim.isNull()) {
            return kycClaim.asString();
        }
        return "NOT_SUBMITTED";
    }
}