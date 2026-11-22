package com.paytrack.expense.repository;

import com.paytrack.expense.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    Optional<Expense> findByWalletId(String walletId);
    List<Expense> findByUserId(String userId);
    Optional<Expense> findByIdAndUserId(Long id, String userId);
    List<Expense> findByWalletIdAndUserId(String walletId, String userId);
}
