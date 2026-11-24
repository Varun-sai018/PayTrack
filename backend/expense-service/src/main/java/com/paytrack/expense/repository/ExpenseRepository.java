package com.paytrack.expense.repository;

import com.paytrack.expense.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Optional<Expense> findByTxnRef(String txnRef);

    Optional<Expense> findByIdAndUserId(Long id, String userId);

    List<Expense> findByUserId(String userId);

    Page<Expense> findByUserId(String userId, Pageable pageable);

    Page<Expense> findByUserIdAndCategory(String userId, String category, Pageable pageable);

    @Query("SELECT e FROM Expense e WHERE e.userId = :userId AND e.timestamp >= :from AND e.timestamp <= :to ORDER BY e.timestamp DESC")
    List<Expense> findByUserIdAndTimestampBetween(
            @Param("userId") String userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT e FROM Expense e WHERE e.userId = :userId AND e.timestamp >= :from AND e.timestamp <= :to ORDER BY e.timestamp DESC")
    Page<Expense> findByUserIdAndTimestampBetween(
            @Param("userId") String userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query("SELECT e FROM Expense e WHERE e.userId = :userId AND e.category = :category AND e.timestamp >= :from AND e.timestamp <= :to ORDER BY e.timestamp DESC")
    Page<Expense> findByUserIdAndCategoryAndTimestampBetween(
            @Param("userId") String userId,
            @Param("category") String category,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.userId = :userId AND e.timestamp >= :from AND e.timestamp <= :to")
    BigDecimal sumAmountByUserIdAndTimestampBetween(
            @Param("userId") String userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT e.category, COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.userId = :userId AND e.timestamp >= :from AND e.timestamp <= :to GROUP BY e.category")
    List<Object[]> sumAmountGroupByCategory(
            @Param("userId") String userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT e.category, COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.userId = :userId GROUP BY e.category")
    List<Object[]> sumAllAmountGroupByCategory(@Param("userId") String userId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.userId = :userId")
    BigDecimal sumTotalAmountByUserId(@Param("userId") String userId);
}
