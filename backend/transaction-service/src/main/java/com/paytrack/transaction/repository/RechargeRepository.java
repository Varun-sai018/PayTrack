package com.paytrack.transaction.repository;

import com.paytrack.transaction.entity.Recharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RechargeRepository extends JpaRepository<Recharge, String> {
    Optional<Recharge> findByTxnId(Long txnId);
}
