package com.payflow.merchant.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "merchant_balances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantBalance {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false, unique = true)
    private UUID merchantId;

    @Column(
        name = "pending_balance",
        nullable = false,
        precision = 19,
        scale = 2
    )
    private BigDecimal pendingBalance;

    @Column(
        name = "settled_balance",
        nullable = false,
        precision = 19,
        scale = 2
    )
    private BigDecimal settledBalance;

    @Version
    @Column(nullable = false)
    private Integer version;

    public MerchantBalance(UUID merchantId) {
        this.merchantId = merchantId;
        this.pendingBalance = BigDecimal.ZERO;
        this.settledBalance = BigDecimal.ZERO;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}