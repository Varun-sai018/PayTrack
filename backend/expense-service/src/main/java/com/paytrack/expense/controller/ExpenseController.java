package com.paytrack.expense.controller;

import com.paytrack.expense.dto.*;
import com.paytrack.expense.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@Slf4j
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    public ResponseEntity<ApiResponse<ExpenseDto>> createExpense(@Valid @RequestBody ExpenseDto expenseDto) {
        ExpenseDto expense = expenseService.createExpense(expenseDto);
        return ResponseEntity.ok(ApiResponse.success(expense, "Expense created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExpenseDto>>> getExpenses(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<ExpenseDto> expenses = expenseService.getExpenses(month, category, page, size);
        return ResponseEntity.ok(ApiResponse.success(expenses, "Expenses retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpenseDto>> getExpenseById(@PathVariable Long id) {
        ExpenseDto expense = expenseService.getExpenseById(id);
        return ResponseEntity.ok(ApiResponse.success(expense, "Expense retrieved successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteExpense(@PathVariable Long id) {
        expenseService.deleteExpense(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Expense deleted successfully"));
    }

    @PutMapping("/txn/{txnRef}/category")
    public ResponseEntity<ApiResponse<ExpenseDto>> updateCategoryByTxnRef(
            @PathVariable String txnRef,
            @Valid @RequestBody CategoryUpdateRequest request) {

        ExpenseDto updated = expenseService.updateCategoryByTxnRef(txnRef, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Expense category updated successfully"));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ExpenseSummaryResponse>> getOverallSummary() {
        ExpenseSummaryResponse summary = expenseService.getOverallSummary();
        return ResponseEntity.ok(ApiResponse.success(summary, "Expense summary retrieved successfully"));
    }

    @GetMapping("/summary/monthly")
    public ResponseEntity<ApiResponse<MonthlySummaryResponse>> getMonthlySummary(
            @RequestParam(required = false) String month) {

        MonthlySummaryResponse summary = expenseService.getMonthlySummary(month);
        return ResponseEntity.ok(ApiResponse.success(summary, "Monthly summary retrieved successfully"));
    }

    @GetMapping("/summary/trend")
    public ResponseEntity<ApiResponse<TrendResponse>> getTrend(
            @RequestParam(defaultValue = "6") int months) {

        TrendResponse trend = expenseService.getTrend(months);
        return ResponseEntity.ok(ApiResponse.success(trend, "Expense trends retrieved successfully"));
    }
}
