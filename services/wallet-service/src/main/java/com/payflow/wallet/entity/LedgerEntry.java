package com.payflow.wallet.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY,  optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    // Phase 2/3 có thể tạo ledger entries trước khi có transactionId thật,
    // nên `transaction_id` được phép NULL theo schema Flyway.
    @Column(name = "transaction_id")
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private LedgerEntryType entryType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public LedgerEntry (
        Wallet wallet,
        UUID transactionId,
        LedgerEntryType entryType,
        BigDecimal amount,
        BigDecimal balanceAfter
    ) {
        this.wallet = wallet;
        this.transactionId = transactionId;
        this.entryType = entryType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
