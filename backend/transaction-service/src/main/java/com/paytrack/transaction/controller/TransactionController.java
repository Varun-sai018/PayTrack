package com.paytrack.transaction.controller;

import com.paytrack.transaction.UserContextHolder;
import com.paytrack.transaction.dto.*;
import com.paytrack.transaction.exception.BusinessException;
import com.paytrack.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<TransferResponse>> transfer(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String xIdempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        String key = resolveIdempotencyKey(idempotencyKey, xIdempotencyKey);
        String userId = getAuthenticatedUserId();

        TransferResponse response = transactionService.transfer(userId, key, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Transfer processed successfully"));
    }

    @PostMapping("/qr/generate")
    public ResponseEntity<ApiResponse<QrGenerateResponse>> qrGenerate(
            @RequestBody(required = false) QrGenerateRequest request) {

        String userId = getAuthenticatedUserId();
        QrGenerateResponse response = transactionService.qrGenerate(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "QR generated successfully"));
    }

    @PostMapping("/qr/pay")
    public ResponseEntity<ApiResponse<TransferResponse>> qrPay(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String xIdempotencyKey,
            @Valid @RequestBody QrPayRequest request) {

        String key = resolveIdempotencyKey(idempotencyKey, xIdempotencyKey);
        String userId = getAuthenticatedUserId();

        TransferResponse response = transactionService.qrPay(userId, key, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "QR payment processed successfully"));
    }

    @PostMapping("/recharge")
    public ResponseEntity<ApiResponse<RechargeResponse>> recharge(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String xIdempotencyKey,
            @Valid @RequestBody RechargeRequest request) {

        String key = resolveIdempotencyKey(idempotencyKey, xIdempotencyKey);
        String userId = getAuthenticatedUserId();

        RechargeResponse response = transactionService.recharge(userId, key, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Recharge processed successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TransactionDto>>> getAllTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<TransactionDto> transactions = transactionService.getAllTransactionsByUser(page, size);
        return ResponseEntity.ok(ApiResponse.success(transactions, "Transactions retrieved"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransactionDto>> getTransactionById(
            @PathVariable(required = false) String id,
            @RequestParam(required = false) String txnRef) {

        Long numericId = null;
        String ref = txnRef;
        if (id != null && !id.isEmpty()) {
            if (id.startsWith("TXN")) {
                ref = id;
            } else {
                try {
                    numericId = Long.parseLong(id);
                } catch (NumberFormatException e) {
                    ref = id;
                }
            }
        }

        TransactionDto transaction = transactionService.getTransactionById(numericId, ref);
        return ResponseEntity.ok(ApiResponse.success(transaction, "Transaction retrieved"));
    }

    @GetMapping("/admin/all")
    public ResponseEntity<ApiResponse<List<TransactionDto>>> getAdminAllTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        List<TransactionDto> transactions = transactionService.getAdminAllTransactions(page, size, status, type, from, to);
        return ResponseEntity.ok(ApiResponse.success(transactions, "Admin transactions retrieved"));
    }

    @GetMapping("/admin/summary")
    public ResponseEntity<ApiResponse<TransactionSummaryResponse>> getAdminSummary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        TransactionSummaryResponse summary = transactionService.getAdminSummary(from, to);
        return ResponseEntity.ok(ApiResponse.success(summary, "Admin summary retrieved"));
    }

    private String resolveIdempotencyKey(String key1, String key2) {
        String key = (key1 != null && !key1.trim().isEmpty()) ? key1.trim() : (key2 != null ? key2.trim() : null);
        if (key == null || key.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "Idempotency-Key header is required");
        }
        return key;
    }

    private String getAuthenticatedUserId() {
        String userId = UserContextHolder.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new BusinessException("UNAUTHORIZED", "Missing authenticated user context");
        }
        return userId;
    }
}
