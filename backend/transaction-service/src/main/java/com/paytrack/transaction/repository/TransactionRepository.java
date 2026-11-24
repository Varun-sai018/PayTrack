package com.paytrack.transaction.repository;

import com.paytrack.transaction.entity.Transaction;
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
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId OR t.receiverUserId = :userId ORDER BY t.timestamp DESC")
    List<Transaction> findBySenderOrReceiverOrderByTimestampDesc(@Param("userId") String userId);

    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId OR t.receiverUserId = :userId ORDER BY t.timestamp DESC")
    Page<Transaction> findBySenderOrReceiver(@Param("userId") String userId, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.txnRef = :txnRef")
    Optional<Transaction> findByTxnRef(@Param("txnRef") String txnRef);

    @Query("SELECT t FROM Transaction t WHERE t.idempotencyKey = :idempotencyKey")
    Optional<Transaction> findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.idempotencyKey = :idempotencyKey")
    Optional<Transaction> findByUserIdAndIdempotencyKey(
            @Param("userId") String userId,
            @Param("idempotencyKey") String idempotencyKey);

    @Query("SELECT t FROM Transaction t WHERE t.status = :status AND t.eventPublished = false")
    List<Transaction> findCompletedNotPublished(@Param("status") String status);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.userId = :userId AND t.status = 'COMPLETED' AND t.timestamp >= :since")
    BigDecimal sumCompletedAmountSince(
            @Param("userId") String userId,
            @Param("since") LocalDateTime since);

    @Query("SELECT t FROM Transaction t WHERE t.status = :status ORDER BY t.timestamp DESC")
    List<Transaction> findByStatus(@Param("status") String status);
}
