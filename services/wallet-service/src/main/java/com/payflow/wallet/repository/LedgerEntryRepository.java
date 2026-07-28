package com.payflow.wallet.repository;

import com.payflow.wallet.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    Page<LedgerEntry> findByWallet_IdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);
}
