package com.payflow.transaction.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_steps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SagaStep {

    @Id
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_name", nullable = false, length = 30)
    private SagaStepName stepName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SagaStepStatus status;

    @Column(name = "executed_at")
    private Instant executedAt;

    public SagaStep(UUID transactionId, SagaStepName stepName) {
        this.id = UUID.randomUUID();
        this.transactionId = transactionId;
        this.stepName = stepName;
        this.status = SagaStepStatus.PENDING;
    }

    public void markSuccess() {
        this.status = SagaStepStatus.SUCCESS;
        this.executedAt = Instant.now();
    }
    public void markFailed() {
        this.status = SagaStepStatus.FAILED;
        this.executedAt = Instant.now();
    }
    public void markCompensated() {
        this.status = SagaStepStatus.COMPENSATED;
        this.executedAt = Instant.now();
    }
}
