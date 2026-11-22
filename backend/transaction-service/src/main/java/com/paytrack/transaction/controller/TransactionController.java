package com.paytrack.transaction.controller;

import com.paytrack.transaction.UserContextHolder;
import com.paytrack.transaction.dto.ApiResponse;
import com.paytrack.transaction.dto.TransactionDto;
import com.paytrack.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Validated
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<ApiResponse<TransactionDto>> createTransaction(@RequestBody TransactionDto transactionDto) {
        String userId = UserContextHolder.getUserId();
        transactionDto.setUserId(userId);
        TransactionDto transaction = transactionService.createTransaction(transactionDto);
        return ResponseEntity.ok(ApiResponse.success(transaction, "Transaction created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TransactionDto>>> getAllTransactions() {
        List<TransactionDto> transactions = transactionService.getAllTransactions();
        return ResponseEntity.ok(ApiResponse.success(transactions, "Transactions retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransactionDto>> getTransactionById(@PathVariable Long id) {
        TransactionDto transaction = transactionService.getTransactionById(id);
        return ResponseEntity.ok(ApiResponse.success(transaction, "Transaction retrieved successfully"));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TransactionDto>> updateTransactionStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        TransactionDto transaction = transactionService.updateTransactionStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success(transaction, "Transaction status updated successfully"));
    }

    @GetMapping("/wallet/{walletId}")
    public ResponseEntity<ApiResponse<List<TransactionDto>>> getTransactionsByWalletId(@PathVariable String walletId) {
        List<TransactionDto> transactions = transactionService.getTransactionsByWalletId(walletId);
        return ResponseEntity.ok(ApiResponse.success(transactions, "Transactions retrieved successfully"));
    }
}
