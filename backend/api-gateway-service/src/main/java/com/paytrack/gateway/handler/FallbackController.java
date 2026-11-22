package com.paytrack.gateway.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private Map<String, Object> createFallbackResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("data", null);
        return response;
    }

    @RequestMapping("/auth")
    public ResponseEntity<Map<String, Object>> authFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(createFallbackResponse("Auth Service is temporarily unavailable. Please try again later."));
    }

    @RequestMapping("/wallet")
    public ResponseEntity<Map<String, Object>> walletFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(createFallbackResponse("Wallet Service is temporarily unavailable. Please try again later."));
    }

    @RequestMapping("/transaction")
    public ResponseEntity<Map<String, Object>> transactionFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(createFallbackResponse("Transaction Service is temporarily unavailable. Please try again later."));
    }

    @RequestMapping("/expense")
    public ResponseEntity<Map<String, Object>> expenseFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(createFallbackResponse("Expense Service is temporarily unavailable. Please try again later."));
    }
}