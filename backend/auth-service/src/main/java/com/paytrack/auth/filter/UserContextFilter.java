package com.paytrack.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class UserContextFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-Id";
    private final String expectedInternalKey;

    public UserContextFilter() {
        this.expectedInternalKey = "paytrack-internal-api-key-2026";
    }

    public UserContextFilter(String expectedInternalKey) {
        this.expectedInternalKey = expectedInternalKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/internal/")) {
            String internalKey = request.getHeader("X-Internal-Key");
            if (expectedInternalKey != null && !expectedInternalKey.isEmpty()) {
                if (internalKey == null || !expectedInternalKey.equals(internalKey)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"message\":\"Missing or invalid internal API key\"}");
                    return;
                }
            }
            filterChain.doFilter(request, response);
            return;
        }

        String userId = request.getHeader(USER_ID_HEADER);
        if (userId != null && !userId.isEmpty()) {
            request.setAttribute("X-User-Id", userId);
        }
        filterChain.doFilter(request, response);
    }
}