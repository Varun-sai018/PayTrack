package com.paytrack.wallet.filter;

import com.paytrack.wallet.UserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
@Slf4j
public class UserContextFilter extends OncePerRequestFilter {

    private final String expectedGatewaySecret;
    private final String expectedInternalKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 1. Actuator, Swagger, OpenAPI endpoints bypass filters
        if (path.startsWith("/actuator") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Internal endpoints require X-Internal-Key
        if (path.startsWith("/internal/")) {
            String internalKey = request.getHeader("X-Internal-Key");
            if (expectedInternalKey != null && !expectedInternalKey.isBlank()) {
                if (internalKey == null || !expectedInternalKey.equals(internalKey)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"message\":\"Missing or invalid internal API key\"}");
                    return;
                }
            }
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Service endpoints require X-Gateway-Secret if configured
        if (expectedGatewaySecret != null && !expectedGatewaySecret.isBlank()) {
            String gatewaySecret = request.getHeader("X-Gateway-Secret");
            if (gatewaySecret == null || !expectedGatewaySecret.equals(gatewaySecret)) {
                log.warn("Blocked direct access attempt to {}: missing or invalid X-Gateway-Secret", path);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"message\":\"Direct access forbidden. Requests must come via API Gateway.\"}");
                return;
            }
        }

        // 4. Extract user identity headers injected by API Gateway
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            UserContextHolder.setUserId(userId);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
        }
    }
}
