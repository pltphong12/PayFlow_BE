package com.payflow.wallet.repository;

import com.payflow.wallet.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    @Modifying
    @Query(
        value = """
            INSERT INTO wallets (
                id, user_id, balance, currency, version, status, created_at
            ) VALUES (
                :walletId, :userId, 0, 'VND', 0, 'ACTIVE', NOW()
            ) ON CONFLICT (user_id) DO NOTHING
            """,
        nativeQuery = true
    )
    int insertWalletIfAbsent(
        @Param("walletId") UUID walletId,
        @Param("userId") UUID userId
    );
}
