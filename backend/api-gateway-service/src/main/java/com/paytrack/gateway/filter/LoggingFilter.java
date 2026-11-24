package com.paytrack.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization",
            "proxy-authorization",
            "cookie",
            "set-cookie",
            "x-gateway-secret"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("""
                ==========================================
                Incoming Request
                ==========================================
                Method       : {}
                URI          : {}
                Headers      : {}
                Host         : {}
                Query Params : {}
                Timestamp    : {}
                ==========================================
                """,
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI(),
                sanitizeHeaders(exchange.getRequest().getHeaders()),
                exchange.getRequest().getHeaders().getHost(),
                exchange.getRequest().getQueryParams(),
                LocalDateTime.now());
        return chain.filter(exchange);
    }

    private Map<String, List<String>> sanitizeHeaders(HttpHeaders headers) {
        Map<String, List<String>> sanitized = new HashMap<>();
        headers.forEach((name, values) -> {
            if (SENSITIVE_HEADERS.contains(name.toLowerCase())) {
                sanitized.put(name, List.of("[MASKED]"));
            } else {
                sanitized.put(name, values);
            }
        });
        return sanitized;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
