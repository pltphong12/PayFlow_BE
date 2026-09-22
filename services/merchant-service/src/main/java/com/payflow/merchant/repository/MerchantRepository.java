package com.payflow.merchant.repository;

import com.payflow.merchant.entity.Merchant;
import com.payflow.merchant.entity.MerchantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    Optional<Merchant> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    Page<Merchant> findByStatus(
        MerchantStatus status,
        Pageable pageable
    );
}