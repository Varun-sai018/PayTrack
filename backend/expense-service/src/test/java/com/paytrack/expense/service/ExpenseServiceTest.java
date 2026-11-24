package com.paytrack.expense.service;

import com.paytrack.expense.UserContextHolder;
import com.paytrack.expense.dto.CategoryUpdateRequest;
import com.paytrack.expense.dto.ExpenseDto;
import com.paytrack.expense.dto.MonthlySummaryResponse;
import com.paytrack.expense.dto.TrendResponse;
import com.paytrack.expense.entity.CategoryRule;
import com.paytrack.expense.entity.Expense;
import com.paytrack.expense.event.PaymentCompletedEvent;
import com.paytrack.expense.exception.BusinessException;
import com.paytrack.expense.repository.CategoryRuleRepository;
import com.paytrack.expense.repository.ExpenseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private CategoryRuleRepository categoryRuleRepository;

    @Spy
    @InjectMocks
    private CategoryEngine categoryEngine;

    private ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        expenseService = new ExpenseService(expenseRepository, categoryEngine);
        UserContextHolder.setUserId("user-100");
        UserContextHolder.setRole("USER");
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void recordPaymentExpense_shouldCategorizeByUserRequest() {
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .txnRef("TXN20261123000001")
                .userId("user-100")
                .type("P2P")
                .amount(new BigDecimal("150.00"))
                .currency("INR")
                .note("Coffee with team")
                .requestedCategory("FOOD")
                .completedAt(LocalDateTime.now())
                .build();

        when(expenseRepository.findByTxnRef("TXN20261123000001")).thenReturn(Optional.empty());

        expenseService.recordPaymentExpense(event);

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());
        Expense saved = captor.getValue();

        assertThat(saved.getCategory()).isEqualTo("FOOD");
        assertThat(saved.getCategorySource()).isEqualTo("USER");
        assertThat(saved.getSource()).isEqualTo("PAYMENT");
        assertThat(saved.getAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    void recordPaymentExpense_shouldCategorizeRechargeToUtilities() {
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .txnRef("TXN20261123000002")
                .userId("user-100")
                .type("RECHARGE")
                .amount(new BigDecimal("299.00"))
                .currency("INR")
                .completedAt(LocalDateTime.now())
                .build();

        when(expenseRepository.findByTxnRef("TXN20261123000002")).thenReturn(Optional.empty());

        expenseService.recordPaymentExpense(event);

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());
        Expense saved = captor.getValue();

        assertThat(saved.getCategory()).isEqualTo("UTILITIES");
        assertThat(saved.getCategorySource()).isEqualTo("RULE");
    }

    @Test
    void recordPaymentExpense_shouldCategorizeByNoteKeyword() {
        CategoryRule rule = CategoryRule.builder()
                .keyword("uber")
                .category("TRANSPORT")
                .matchOn("NOTE")
                .priority(10)
                .build();

        when(categoryRuleRepository.findByMatchOnOrderByPriorityDesc("NOTE")).thenReturn(List.of(rule));
        when(expenseRepository.findByTxnRef("TXN20261123000003")).thenReturn(Optional.empty());

        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .txnRef("TXN20261123000003")
                .userId("user-100")
                .type("P2P")
                .amount(new BigDecimal("350.00"))
                .currency("INR")
                .note("Took an Uber to office")
                .completedAt(LocalDateTime.now())
                .build();

        expenseService.recordPaymentExpense(event);

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());
        Expense saved = captor.getValue();

        assertThat(saved.getCategory()).isEqualTo("TRANSPORT");
        assertThat(saved.getCategorySource()).isEqualTo("RULE");
    }

    @Test
    void recordPaymentExpense_shouldFallbackToOther() {
        when(categoryRuleRepository.findByMatchOnOrderByPriorityDesc("NOTE")).thenReturn(List.of());
        when(expenseRepository.findByTxnRef("TXN20261123000004")).thenReturn(Optional.empty());

        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .txnRef("TXN20261123000004")
                .userId("user-100")
                .type("P2P")
                .amount(new BigDecimal("50.00"))
                .currency("INR")
                .note("Unknown payment")
                .completedAt(LocalDateTime.now())
                .build();

        expenseService.recordPaymentExpense(event);

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());
        Expense saved = captor.getValue();

        assertThat(saved.getCategory()).isEqualTo("OTHER");
        assertThat(saved.getCategorySource()).isEqualTo("DEFAULT");
    }

    @Test
    void recordPaymentExpense_shouldIgnoreDuplicateTxnRef() {
        Expense existing = Expense.builder()
                .id(1L)
                .txnRef("TXN-EXISTING")
                .build();

        when(expenseRepository.findByTxnRef("TXN-EXISTING")).thenReturn(Optional.of(existing));

        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .txnRef("TXN-EXISTING")
                .build();

        expenseService.recordPaymentExpense(event);

        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void createExpense_shouldSaveManualExpense() {
        ExpenseDto dto = ExpenseDto.builder()
                .amount(new BigDecimal("200.00"))
                .category("EDUCATION")
                .description("Textbooks")
                .build();

        when(expenseRepository.save(any(Expense.class))).thenAnswer(i -> {
            Expense e = i.getArgument(0);
            e.setId(10L);
            return e;
        });

        ExpenseDto created = expenseService.createExpense(dto);

        assertThat(created.getId()).isEqualTo(10L);
        assertThat(created.getCategory()).isEqualTo("EDUCATION");
        assertThat(created.getSource()).isEqualTo("MANUAL");
        assertThat(created.getCategorySource()).isEqualTo("MANUAL");
    }

    @Test
    void deleteExpense_shouldDeleteManualExpense() {
        Expense manual = Expense.builder()
                .id(5L)
                .userId("user-100")
                .source("MANUAL")
                .build();

        when(expenseRepository.findByIdAndUserId(5L, "user-100")).thenReturn(Optional.of(manual));

        expenseService.deleteExpense(5L);

        verify(expenseRepository).delete(manual);
    }

    @Test
    void deleteExpense_shouldThrowForPaymentExpense() {
        Expense paymentExp = Expense.builder()
                .id(6L)
                .userId("user-100")
                .source("PAYMENT")
                .txnRef("TXN-PAY")
                .build();

        when(expenseRepository.findByIdAndUserId(6L, "user-100")).thenReturn(Optional.of(paymentExp));

        assertThatThrownBy(() -> expenseService.deleteExpense(6L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Payment-linked expenses cannot be deleted");

        verify(expenseRepository, never()).delete(any(Expense.class));
    }

    @Test
    void updateCategoryByTxnRef_shouldUpdateCategory() {
        Expense expense = Expense.builder()
                .id(7L)
                .userId("user-100")
                .txnRef("TXN-7")
                .category("OTHER")
                .categorySource("DEFAULT")
                .build();

        when(expenseRepository.findByTxnRef("TXN-7")).thenReturn(Optional.of(expense));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(i -> i.getArgument(0));

        CategoryUpdateRequest req = CategoryUpdateRequest.builder().category("SHOPPING").build();
        ExpenseDto updated = expenseService.updateCategoryByTxnRef("TXN-7", req);

        assertThat(updated.getCategory()).isEqualTo("SHOPPING");
        assertThat(updated.getCategorySource()).isEqualTo("USER");
    }

    @Test
    void getMonthlySummary_shouldCalculateCategoryBreakdown() {
        List<Object[]> rows = List.of(
                new Object[]{"FOOD", new BigDecimal("300.00")},
                new Object[]{"TRANSPORT", new BigDecimal("100.00")}
        );

        when(expenseRepository.sumAmountGroupByCategory(eq("user-100"), any(), any())).thenReturn(rows);

        MonthlySummaryResponse summary = expenseService.getMonthlySummary("2026-11");

        assertThat(summary.getTotal()).isEqualByComparingTo("400.00");
        assertThat(summary.getByCategory()).hasSize(2);
        assertThat(summary.getByCategory().get("FOOD").getPercent()).isEqualTo(75.0);
        assertThat(summary.getByCategory().get("TRANSPORT").getPercent()).isEqualTo(25.0);
    }

    @Test
    void getTrend_shouldReturnMonthlyTotals() {
        when(expenseRepository.sumAmountByUserIdAndTimestampBetween(eq("user-100"), any(), any()))
                .thenReturn(new BigDecimal("500.00"));

        TrendResponse trend = expenseService.getTrend(3);

        assertThat(trend.getTrends()).hasSize(3);
        assertThat(trend.getTrends().get(0).getTotal()).isEqualByComparingTo("500.00");
    }
}
