package com.paytrack.transaction.controller;

import com.paytrack.transaction.UserContextHolder;
import com.paytrack.transaction.dto.*;
import com.paytrack.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    // REMOVED: POST /api/transactions (generic DEBIT, CREDIT, TRANSFER)
    // Users must use specific endpoints below

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<TransferResponse>> transfer(
            @RequestHeader(value = "X-Idempotency-Key", required = true) String idempotencyKey,
            @RequestBody TransferRequest request) {
        String userId = UserContextHolder.getUserId();
        TransferResponse response = transactionService.transfer(userId, idempotencyKey, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Transfer initiated"));
    }

    @PostMapping("/qr/generate")
    public ResponseEntity<ApiResponse<QrGenerateResponse>> qrGenerate(
            @RequestBody QrGenerateRequest request) {
        String userId = UserContextHolder.getUserId();
        QrGenerateResponse response = transactionService.qrGenerate(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "QR generated"));
    }

    @PostMapping("/qr/pay")
    public ResponseEntity<ApiResponse<TransferResponse>> qrPay(
            @RequestHeader(value = "X-Idempotency-Key", required = true) String idempotencyKey,
            @RequestBody QrPayRequest request) {
        String userId = UserContextHolder.getUserId();
        TransferResponse response = transactionService.qrPay(userId, idempotencyKey, request);
        return ResponseEntity.ok(ApiResponse.success(response, "QR payment initiated"));
    }

    @PostMapping("/recharge")
    public ResponseEntity<ApiResponse<RechargeResponse>> recharge(
            @RequestHeader(value = "X-Idempotency-Key", required = true) String idempotencyKey,
            @RequestBody RechargeRequest request) {
        String userId = UserContextHolder.getUserId();
        RechargeResponse response = transactionService.recharge(userId, idempotencyKey, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Recharge initiated"));
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
            @PathVariable Long id,
            @RequestParam(required = false) String txnRef) {
        TransactionDto transaction = transactionService.getTransactionById(id, txnRef);
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
        return ResponseEntity.ok(ApiResponse.success(transactions, "Transactions retrieved"));
    }

    @GetMapping("/admin/summary")
    public ResponseEntity<ApiResponse<TransactionSummaryResponse>> getAdminSummary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        TransactionSummaryResponse summary = transactionService.getAdminSummary(from, to);
        return ResponseEntity.ok(ApiResponse.success(summary, "Summary retrieved"));
    }
}