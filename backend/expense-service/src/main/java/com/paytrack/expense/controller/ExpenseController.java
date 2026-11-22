package com.paytrack.expense.controller;

import com.paytrack.expense.dto.ApiResponse;
import com.paytrack.expense.dto.ExpenseDto;
import com.paytrack.expense.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@Validated
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    public ResponseEntity<ApiResponse<ExpenseDto>> createExpense(@RequestBody ExpenseDto expenseDto) {
        ExpenseDto expense = expenseService.createExpense(expenseDto);
        return ResponseEntity.ok(ApiResponse.success(expense, "Expense created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExpenseDto>>> getAllExpenses() {
        List<ExpenseDto> expenses = expenseService.getAllExpenses();
        return ResponseEntity.ok(ApiResponse.success(expenses, "Expenses retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpenseDto>> getExpenseById(@PathVariable Long id) {
        ExpenseDto expense = expenseService.getExpenseById(id);
        return ResponseEntity.ok(ApiResponse.success(expense, "Expense retrieved successfully"));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ExpenseDto>> updateExpenseStatus(@PathVariable Long id, @RequestParam String status) {
        ExpenseDto expense = expenseService.updateExpenseStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success(expense, "Expense status updated successfully"));
    }

    @GetMapping("/wallet/{walletId}")
    public ResponseEntity<ApiResponse<List<ExpenseDto>>> getExpensesByWalletId(@PathVariable String walletId) {
        List<ExpenseDto> expenses = expenseService.getExpensesByWalletId(walletId);
        return ResponseEntity.ok(ApiResponse.success(expenses, "Expenses retrieved successfully"));
    }
}
