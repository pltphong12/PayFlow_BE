package com.payflow.merchant.repository;

import com.payflow.merchant.entity.Merchant;
import com.payflow.merchant.entity.MerchantStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    Optional<Merchant> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    Page<Merchant> findByStatus(
        MerchantStatus status,
        Pageable pageable
    );

    @Lock (LockModeType.PESSIMISTIC_WRITE)
    @Query ("""
        SELECT merchant
        FROM Merchant merchant
        WHERE merchant.id = :merchantId
        """)
    Optional<Merchant> findByIdForUpdate(
        @Param ("merchantId") UUID merchantId
    );
}