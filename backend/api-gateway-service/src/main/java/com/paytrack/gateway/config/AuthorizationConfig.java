package com.paytrack.gateway.config;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AuthorizationConfig {

    public Map<String, List<String>> routeRoles() {
        return Map.ofEntries(
                Map.entry("/api/auth/admin", List.of("ROLE_ADMIN")),
                Map.entry("/api/transactions/admin", List.of("ROLE_ADMIN")),
                Map.entry("GET:/api/wallets", List.of("ROLE_ADMIN", "ROLE_CUSTOMER")),
                Map.entry("POST:/api/wallets", List.of("ROLE_ADMIN", "ROLE_CUSTOMER")),
                Map.entry("PUT:/api/wallets", List.of("ROLE_ADMIN", "ROLE_CUSTOMER")),
                Map.entry("DELETE:/api/wallets", List.of("ROLE_ADMIN", "ROLE_CUSTOMER")),
                Map.entry("GET:/api/transactions", List.of("ROLE_ADMIN", "ROLE_CUSTOMER")),
                Map.entry("POST:/api/transactions", List.of("ROLE_ADMIN", "ROLE_CUSTOMER")),
                Map.entry("PUT:/api/transactions", List.of("ROLE_ADMIN", "ROLE_CUSTOMER")),
                Map.entry("DELETE:/api/transactions", List.of("ROLE_ADMIN", "ROLE_CUSTOMER")),
                Map.entry("GET:/api/expenses", List.of("ROLE_ADMIN", "ROLE_CUSTOMER")),
                Map.entry("POST:/api/expenses", List.of("ROLE_ADMIN", "ROLE_CUSTOMER")),
                Map.entry("PUT:/api/expenses", List.of("ROLE_ADMIN", "ROLE_CUSTOMER")),
                Map.entry("DELETE:/api/expenses", List.of("ROLE_ADMIN", "ROLE_CUSTOMER"))
        );
    }
}
