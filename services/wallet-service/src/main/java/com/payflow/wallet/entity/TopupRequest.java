package com.payflow.wallet.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "topup_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TopupRequest {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TopupStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public TopupRequest(UUID userId, BigDecimal amount, String idempotencyKey) {
        this.userId = userId;
        this.amount = amount;
        this.status = TopupStatus.PENDING;
        this.idempotencyKey = idempotencyKey;
    }

    public void markSuccess() {
        this.status = TopupStatus.SUCCESS;
    }
    public void markFailed() {
        this.status = TopupStatus.FAILED;
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
