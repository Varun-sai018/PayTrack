package com.paytrack.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytrack.gateway.config.AuthorizationConfig;
import com.paytrack.gateway.dto.ErrorResponseDto;
import com.paytrack.gateway.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final AuthorizationConfig authorizationConfig;
    private final ObjectMapper objectMapper;

    @Value("${gateway.secret:paytrack-internal-gateway-secret-2026}")
    private String gatewaySecret = "paytrack-internal-gateway-secret-2026";

    private static final List<String> PUBLIC_APIS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/verify-otp",
            "/api/auth/refresh",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/resend-otp",
            "/swagger-ui",
            "/v3/api-docs",
            "/fallback",
            "/actuator"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (org.springframework.web.cors.reactive.CorsUtils.isPreFlightRequest(exchange.getRequest())) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();
        log.info("Incoming {} {}", method, path);

        if (isPublicApi(path)) {
            log.info("Public API accessed: {}", path);
            return chain.filter(exchange);
        }

        String authorizationHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Authorization Header Missing");
        }

        String token = authorizationHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Invalid JWT Token");
        }

        String email = jwtUtil.retrieveEmailFromToken(token);
        String userId = jwtUtil.retrieveUserIdFromToken(token);
        List<String> roles = jwtUtil.retrieveRolesFromToken(token);
        String kycStatus = jwtUtil.retrieveKycStatusFromToken(token);

        log.info("Authenticated User: {} (id={}, kyc={})", email, userId, kycStatus);
        log.info("Roles: {}", roles);

        String route = determineRoute(path);
        String routeKey = method.toUpperCase() + ":" + route;

        List<String> allowedRoles = authorizationConfig.routeRoles().get(routeKey);
        if (allowedRoles == null) {
            allowedRoles = authorizationConfig.routeRoles().get(route);
        }

        if (allowedRoles != null && !allowedRoles.isEmpty()) {
            boolean authorized = roles.stream().anyMatch(allowedRoles::contains);
            if (!authorized) {
                return writeErrorResponse(exchange, HttpStatus.FORBIDDEN, "Access Denied");
            }
        }

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(r -> r.headers(h -> {
                    h.set("X-User-Id", userId != null ? userId : "");
                    h.set("X-User-Email", email != null ? email : "");
                    h.set("X-User-Roles", String.join(",", roles));
                    h.set("X-User-Role", String.join(",", roles));
                    h.set("X-Kyc-Status", kycStatus != null ? kycStatus : "");
                    h.set("X-Gateway-Secret", gatewaySecret);
                }))
                .build();

        return chain.filter(mutatedExchange);
    }

    private boolean isPublicApi(String path) {
        return PUBLIC_APIS.stream().anyMatch(path::startsWith);
    }

    private String determineRoute(String path) {
        if (path.startsWith("/api/auth/admin")) return "/api/auth/admin";
        if (path.startsWith("/api/transactions/admin")) return "/api/transactions/admin";
        if (path.startsWith("/api/wallets")) return "/api/wallets";
        if (path.startsWith("/api/transactions")) return "/api/transactions";
        if (path.startsWith("/api/expenses")) return "/api/expenses";
        return "";
    }

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponseDto body = ErrorResponseDto.builder()
                .success(false)
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(exchange.getRequest().getPath().value())
                .build();

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("Error writing response: {}", e.getMessage());
            return response.writeWith(Mono.just(response.bufferFactory().wrap(
                ("{\"success\":false,\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8))));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
