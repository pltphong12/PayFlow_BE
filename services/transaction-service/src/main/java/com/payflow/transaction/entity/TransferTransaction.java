package com.payflow.transaction.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransferTransaction {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(name = "sender_user_id", nullable = false)
    private UUID senderUserId;

    @Column(name = "receiver_user_id", nullable = false)
    private UUID receiverUserId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public TransferTransaction(
        UUID senderUserId,
        UUID receiverUserId,
        BigDecimal amount,
        String idempotencyKey
    ) {
        this.id = UUID.randomUUID();
        this.type = TransactionType.TRANSFER;
        this.senderUserId = senderUserId;
        this.receiverUserId = receiverUserId;
        this.amount = amount;
        this.status = TransactionStatus.PENDING;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
    }

    public void markCompleted() {
        this.status = TransactionStatus.COMPLETED;
    }
    public void markFailed() {
        this.status = TransactionStatus.FAILED;
    }
    public void markCompensating() {
        this.status = TransactionStatus.COMPENSATING;
    }
}
