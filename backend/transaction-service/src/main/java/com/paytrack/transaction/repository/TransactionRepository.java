package com.paytrack.transaction.repository;

import com.paytrack.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByWalletId(String walletId);

    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId ORDER BY t.timestamp DESC")
    List<Transaction> findByUserIdOrderByTimestampDesc(@Param("userId") String userId);

    @Query("SELECT t FROM Transaction t WHERE t.receiverUserId = :receiverUserId ORDER BY t.timestamp DESC")
    List<Transaction> findByReceiverUserIdOrderByTimestampDesc(@Param("receiverUserId") String receiverUserId);

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

    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.status = :status")
    List<Transaction> findByUserIdAndStatus(
            @Param("userId") String userId,
            @Param("status") String status);
}