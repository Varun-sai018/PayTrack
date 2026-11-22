package com.paytrack.gateway.config;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AuthorizationConfig {

    public Map<String, List<String>> routeRoles() {
        return Map.of(
                "GET:/api/wallets",
                List.of("ROLE_ADMIN", "ROLE_CUSTOMER"),
                "POST:/api/wallets",
                List.of("ROLE_ADMIN", "ROLE_CUSTOMER"),
                "GET:/api/transactions",
                List.of("ROLE_ADMIN", "ROLE_CUSTOMER"),
                "POST:/api/transactions",
                List.of("ROLE_ADMIN", "ROLE_CUSTOMER"),
                "GET:/api/expenses",
                List.of("ROLE_ADMIN", "ROLE_CUSTOMER"),
                "POST:/api/expenses",
                List.of("ROLE_ADMIN", "ROLE_CUSTOMER")
        );
    }
}