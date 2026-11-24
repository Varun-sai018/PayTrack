package com.paytrack.expense.service;

import com.paytrack.expense.UserContextHolder;
import com.paytrack.expense.dto.*;
import com.paytrack.expense.entity.Expense;
import com.paytrack.expense.event.PaymentCompletedEvent;
import com.paytrack.expense.exception.BusinessException;
import com.paytrack.expense.exception.ResourceNotFoundException;
import com.paytrack.expense.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final CategoryEngine categoryEngine;

    @Transactional
    public void recordPaymentExpense(PaymentCompletedEvent event) {
        log.info("Recording payment expense for txnRef: {}, userId: {}", event.getTxnRef(), event.getUserId());

        // Idempotency: Ignore duplicate payment events
        if (event.getTxnRef() != null) {
            Optional<Expense> existing = expenseRepository.findByTxnRef(event.getTxnRef());
            if (existing.isPresent()) {
                log.info("Expense for txnRef {} already exists, ignoring duplicate event", event.getTxnRef());
                return;
            }
        }

        CategoryEngine.ResolvedCategory resolved = categoryEngine.resolveCategory(
                event.getRequestedCategory(),
                event.getNote(),
                event.getType()
        );

        String description = event.getNote();
        if (description == null || description.trim().isEmpty()) {
            description = (event.getType() != null ? event.getType() : "Payment") + " expense";
        }

        BigDecimal amount = event.getAmount() != null ? event.getAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        String currency = event.getCurrency() != null ? event.getCurrency() : "INR";
        LocalDateTime timestamp = event.getCompletedAt() != null ? event.getCompletedAt() : LocalDateTime.now();

        Expense expense = Expense.builder()
                .userId(event.getUserId())
                .expenseId(UUID.randomUUID().toString())
                .walletId(event.getUserId())
                .category(resolved.category())
                .categorySource(resolved.categorySource())
                .source("PAYMENT")
                .txnRef(event.getTxnRef())
                .description(description)
                .amount(amount)
                .currency(currency)
                .status("APPROVED")
                .timestamp(timestamp)
                .build();

        expenseRepository.save(expense);
        log.info("Saved PAYMENT expense for txnRef {}: category={}, source={}", event.getTxnRef(), resolved.category(), resolved.categorySource());
    }

    @Transactional
    public ExpenseDto createExpense(ExpenseDto expenseDto) {
        String userId = UserContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("UNAUTHORIZED", "User identity missing");
        }

        if (expenseDto.getAmount() == null || expenseDto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("VALIDATION_ERROR", "Amount must be positive");
        }

        String category = expenseDto.getCategory() != null ? expenseDto.getCategory().toUpperCase() : "OTHER";
        if (!CategoryEngine.VALID_CATEGORIES.contains(category)) {
            throw new BusinessException("VALIDATION_ERROR", "Invalid category: " + category);
        }

        String description = expenseDto.getDescription() != null ? expenseDto.getDescription() : "Manual expense";
        BigDecimal amount = expenseDto.getAmount().setScale(2, RoundingMode.HALF_UP);
        String currency = expenseDto.getCurrency() != null ? expenseDto.getCurrency() : "INR";

        Expense expense = Expense.builder()
                .userId(userId)
                .expenseId(UUID.randomUUID().toString())
                .walletId(userId)
                .category(category)
                .categorySource("MANUAL")
                .source("MANUAL")
                .description(description)
                .amount(amount)
                .currency(currency)
                .status("APPROVED")
                .timestamp(LocalDateTime.now())
                .build();

        Expense saved = expenseRepository.save(expense);
        return convertToDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ExpenseDto> getExpenses(String month, String category, int page, int size) {
        String userId = UserContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("UNAUTHORIZED", "User identity missing");
        }

        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        Page<Expense> paged;

        if (month != null && !month.trim().isEmpty()) {
            YearMonth ym;
            try {
                ym = YearMonth.parse(month.trim());
            } catch (Exception e) {
                throw new BusinessException("VALIDATION_ERROR", "Invalid month format. Expected yyyy-MM");
            }
            LocalDateTime start = ym.atDay(1).atStartOfDay();
            LocalDateTime end = ym.atEndOfMonth().atTime(23, 59, 59, 999999999);

            if (category != null && !category.trim().isEmpty()) {
                paged = expenseRepository.findByUserIdAndCategoryAndTimestampBetween(userId, category.toUpperCase(), start, end, pageable);
            } else {
                paged = expenseRepository.findByUserIdAndTimestampBetween(userId, start, end, pageable);
            }
        } else {
            if (category != null && !category.trim().isEmpty()) {
                paged = expenseRepository.findByUserIdAndCategory(userId, category.toUpperCase(), pageable);
            } else {
                paged = expenseRepository.findByUserId(userId, pageable);
            }
        }

        return paged.getContent().stream().map(this::convertToDto).toList();
    }

    @Transactional(readOnly = true)
    public ExpenseDto getExpenseById(Long id) {
        String userId = UserContextHolder.getUserId();
        Expense expense = expenseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with ID: " + id));
        return convertToDto(expense);
    }

    @Transactional
    public void deleteExpense(Long id) {
        String userId = UserContextHolder.getUserId();
        Expense expense = expenseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with ID: " + id));

        if ("PAYMENT".equalsIgnoreCase(expense.getSource())) {
            throw new BusinessException("VALIDATION_ERROR", "Payment-linked expenses cannot be deleted");
        }

        expenseRepository.delete(expense);
        log.info("Deleted manual expense with id: {} for user: {}", id, userId);
    }

    @Transactional
    public ExpenseDto updateCategoryByTxnRef(String txnRef, CategoryUpdateRequest request) {
        String userId = UserContextHolder.getUserId();
        if (txnRef == null || txnRef.trim().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "Transaction reference is required");
        }

        Expense expense = expenseRepository.findByTxnRef(txnRef)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with txnRef: " + txnRef));

        if (!userId.equals(expense.getUserId())) {
            throw new BusinessException("FORBIDDEN", "Not authorized to update this expense");
        }

        String newCategory = request.getCategory() != null ? request.getCategory().trim().toUpperCase() : null;
        if (newCategory == null || !CategoryEngine.VALID_CATEGORIES.contains(newCategory)) {
            throw new BusinessException("VALIDATION_ERROR", "Invalid category: " + request.getCategory());
        }

        expense.setCategory(newCategory);
        expense.setCategorySource("USER");
        Expense updated = expenseRepository.save(expense);

        log.info("Updated category for txnRef {} to {} by USER", txnRef, newCategory);
        return convertToDto(updated);
    }

    @Transactional(readOnly = true)
    public MonthlySummaryResponse getMonthlySummary(String monthStr) {
        String userId = UserContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("UNAUTHORIZED", "User identity missing");
        }

        YearMonth ym;
        if (monthStr != null && !monthStr.trim().isEmpty()) {
            try {
                ym = YearMonth.parse(monthStr.trim());
            } catch (Exception e) {
                throw new BusinessException("VALIDATION_ERROR", "Invalid month format. Expected yyyy-MM");
            }
        } else {
            ym = YearMonth.now();
        }

        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.atEndOfMonth().atTime(23, 59, 59, 999999999);

        List<Object[]> rows = expenseRepository.sumAmountGroupByCategory(userId, start, end);
        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> categoryAmounts = new HashMap<>();

        for (Object[] row : rows) {
            String cat = (String) row[0];
            BigDecimal amt = (BigDecimal) row[1];
            if (amt != null) {
                categoryAmounts.put(cat, amt);
                total = total.add(amt);
            }
        }

        Map<String, MonthlySummaryResponse.CategoryBreakdown> byCategory = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : categoryAmounts.entrySet()) {
            BigDecimal amt = entry.getValue();
            double pct = 0.0;
            if (total.compareTo(BigDecimal.ZERO) > 0) {
                pct = amt.multiply(BigDecimal.valueOf(100))
                        .divide(total, 1, RoundingMode.HALF_UP)
                        .doubleValue();
            }
            byCategory.put(entry.getKey(), MonthlySummaryResponse.CategoryBreakdown.builder()
                    .amount(amt)
                    .percent(pct)
                    .build());
        }

        return MonthlySummaryResponse.builder()
                .month(ym.toString())
                .total(total)
                .byCategory(byCategory)
                .build();
    }

    @Transactional(readOnly = true)
    public TrendResponse getTrend(int months) {
        String userId = UserContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("UNAUTHORIZED", "User identity missing");
        }

        int count = Math.min(Math.max(months, 1), 12);
        YearMonth current = YearMonth.now();
        List<TrendResponse.MonthlyTrendItem> trendItems = new ArrayList<>();

        for (int i = count - 1; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            LocalDateTime start = ym.atDay(1).atStartOfDay();
            LocalDateTime end = ym.atEndOfMonth().atTime(23, 59, 59, 999999999);

            BigDecimal monthTotal = expenseRepository.sumAmountByUserIdAndTimestampBetween(userId, start, end);
            trendItems.add(TrendResponse.MonthlyTrendItem.builder()
                    .month(ym.toString())
                    .total(monthTotal != null ? monthTotal : BigDecimal.ZERO)
                    .build());
        }

        return TrendResponse.builder()
                .trends(trendItems)
                .build();
    }

    @Transactional(readOnly = true)
    public ExpenseSummaryResponse getOverallSummary() {
        String userId = UserContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("UNAUTHORIZED", "User identity missing");
        }

        BigDecimal total = expenseRepository.sumTotalAmountByUserId(userId);
        List<Expense> all = expenseRepository.findByUserId(userId);

        Map<String, BigDecimal> categoryTotals = new HashMap<>();
        for (Expense e : all) {
            categoryTotals.put(e.getCategory(), categoryTotals.getOrDefault(e.getCategory(), BigDecimal.ZERO).add(e.getAmount()));
        }

        return ExpenseSummaryResponse.builder()
                .totalExpenses(total != null ? total : BigDecimal.ZERO)
                .totalCount(all.size())
                .categoryTotals(categoryTotals)
                .build();
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
                .currency(expense.getCurrency())
                .status(expense.getStatus())
                .source(expense.getSource())
                .categorySource(expense.getCategorySource())
                .txnRef(expense.getTxnRef())
                .timestamp(expense.getTimestamp())
                .build();
    }
}
