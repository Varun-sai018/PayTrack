package com.paytrack.wallet.repository;

import com.paytrack.wallet.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, String> {
    Page<LedgerEntry> findByWalletIdOrderByCreatedAtDesc(Long walletId, Pageable pageable);
    Page<LedgerEntry> findByWalletIdAndEntryTypeOrderByCreatedAtDesc(Long walletId, LedgerEntry.EntryType entryType, Pageable pageable);
    boolean existsByWalletIdAndTxnRefAndEntryType(Long walletId, String txnRef, LedgerEntry.EntryType entryType);
    List<LedgerEntry> findByTxnRef(String txnRef);
}
