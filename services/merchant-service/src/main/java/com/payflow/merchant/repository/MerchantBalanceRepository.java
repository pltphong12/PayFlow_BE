package com.payflow.merchant.repository;

import com.payflow.merchant.entity.MerchantBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantBalanceRepository
    extends JpaRepository<MerchantBalance, UUID> {

    Optional<MerchantBalance> findByMerchantId(UUID merchantId);

    boolean existsByMerchantId(UUID merchantId);
}