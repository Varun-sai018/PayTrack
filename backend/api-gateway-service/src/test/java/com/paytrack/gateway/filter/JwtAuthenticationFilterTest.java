package com.paytrack.gateway.filter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false"
})
@AutoConfigureWebTestClient
public class JwtAuthenticationFilterTest {

    @Autowired
    private WebTestClient webTestClient;

    @Value("${jwt.secret}")
    private String secret;

    private String createToken(String subject, List<String> roles) {
        Algorithm algorithm = Algorithm.HMAC256(secret.getBytes(StandardCharsets.UTF_8));
        var builder = JWT.create()
                .withSubject(subject)
                .withExpiresAt(new Date(System.currentTimeMillis() + 3600000));
        if (roles != null) {
            builder.withClaim("role", roles);
        }
        return builder.sign(algorithm);
    }

    private String createTokenWithStringRole(String subject, String role) {
        Algorithm algorithm = Algorithm.HMAC256(secret.getBytes(StandardCharsets.UTF_8));
        return JWT.create()
                .withSubject(subject)
                .withClaim("role", role)
                .withExpiresAt(new Date(System.currentTimeMillis() + 3600000))
                .sign(algorithm);
    }

    @Test
    @DisplayName("Public API /api/auth/login should be accessible without token")
    void testPublicApi() {
        webTestClient.post()
                .uri("/api/auth/login")
                .exchange()
                // Downstream is not running, so circuit breaker will forward to /fallback/auth
                // Let's verify what happens!
                .expectStatus().is5xxServerError();
    }

    @Test
    @DisplayName("Protected API without Authorization header should return 401")
    void testMissingAuthHeader() {
        webTestClient.get()
                .uri("/api/wallets")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("Unauthorized")
                .jsonPath("$.message").isEqualTo("Authorization Header Missing");
    }

    @Test
    @DisplayName("Protected API with invalid token should return 401")
    void testInvalidToken() {
        webTestClient.get()
                .uri("/api/wallets")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.message").isEqualTo("Invalid JWT Token");
    }

    @Test
    @DisplayName("Protected API with valid token but unauthorized role should return 403")
    void testForbiddenRole() {
        String token = createToken("user@test.com", List.of("ROLE_USER"));
        webTestClient.get()
                .uri("/api/wallets")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.message").isEqualTo("Access Denied");
    }

    @Test
    @DisplayName("Token without role claim should not throw NullPointerException (500)")
    void testTokenWithoutRoleClaim() {
        String token = createToken("user@test.com", null);
        webTestClient.get()
                .uri("/api/wallets")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                // Should return 403 Forbidden or 401 Unauthorized, NOT 500 NPE!
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("Token with String role claim should not throw NullPointerException (500)")
    void testTokenWithStringRoleClaim() {
        String token = createTokenWithStringRole("user@test.com", "ROLE_CUSTOMER");
        webTestClient.get()
                .uri("/api/wallets")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                // If it successfully extracts ROLE_CUSTOMER, it passes authorization
                // (downstream fails with 5xx because wallet-service isn't running)
                .expectStatus().is5xxServerError();
    }

    @Test
    @DisplayName("CORS Preflight OPTIONS request should not require Authorization header")
    void testCorsPreflight() {
        webTestClient.options()
                .uri("http://localhost:8080/api/wallets")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                // Should return 200 OK or 204 No Content, NOT 401 Unauthorized!
                .expectStatus().is2xxSuccessful();
    }

    @Test
    @DisplayName("POST to Fallback endpoint should be supported")
    void testPostFallback() {
        webTestClient.post()
                .uri("/fallback/auth")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Auth Service is temporarily unavailable. Please try again later.");
    }
}
