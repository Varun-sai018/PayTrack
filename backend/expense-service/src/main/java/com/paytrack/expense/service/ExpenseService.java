package com.paytrack.expense.service;

import com.paytrack.expense.UserContextHolder;
import com.paytrack.expense.dto.ExpenseDto;
import com.paytrack.expense.entity.Expense;
import com.paytrack.expense.exception.ResourceNotFoundException;
import com.paytrack.expense.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ExpenseService {

    private final ExpenseRepository expenseRepository;

    public ExpenseDto createExpense(ExpenseDto expenseDto) {
        String userId = UserContextHolder.getUserId();
        expenseDto.setUserId(userId);

        Expense expense = new Expense();
        expense.setUserId(userId);

        // Issue 2: auto-generate expenseId when caller omits it (column is NOT NULL)
        String expenseId = expenseDto.getExpenseId();
        if (expenseId == null || expenseId.isBlank()) {
            expenseId = java.util.UUID.randomUUID().toString();
        }
        expense.setExpenseId(expenseId);

        // Polish: walletId defaults to userId when caller omits it (column is NOT NULL)
        String walletId = expenseDto.getWalletId();
        if (walletId == null || walletId.isBlank()) {
            walletId = userId;
        }
        expense.setWalletId(walletId);
        expense.setCategory(expenseDto.getCategory());
        expense.setDescription(expenseDto.getDescription());
        expense.setAmount(expenseDto.getAmount());
        expense.setStatus(expenseDto.getStatus() != null ? expenseDto.getStatus() : "PENDING");

        Expense savedExpense = expenseRepository.save(expense);
        return convertToDto(savedExpense);
    }

    public List<ExpenseDto> getAllExpenses() {
        String userId = UserContextHolder.getUserId();
        List<Expense> expenses = expenseRepository.findByUserId(userId);
        return expenses.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public ExpenseDto getExpenseById(Long id) {
        String userId = UserContextHolder.getUserId();
        Expense expense = expenseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with ID: " + id));
        return convertToDto(expense);
    }

    public ExpenseDto updateExpenseStatus(Long id, String status) {
        String userId = UserContextHolder.getUserId();
        Expense expense = expenseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with ID: " + id));
        expense.setStatus(status);
        Expense updatedExpense = expenseRepository.save(expense);
        return convertToDto(updatedExpense);
    }

    public List<ExpenseDto> getExpensesByWalletId(String walletId) {
        String userId = UserContextHolder.getUserId();
        List<Expense> expenses = expenseRepository.findByWalletIdAndUserId(walletId, userId);
        return expenses.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private ExpenseDto convertToDto(Expense expense) {
        return ExpenseDto.builder()
                .id(expense.getId())
                .userId(expense.getUserId())
                .expenseId(expense.getExpenseId())
                .walletId(expense.getWalletId())
                .category(expense.getCategory())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .status(expense.getStatus())
                .timestamp(expense.getTimestamp())
                .build();
    }
}
