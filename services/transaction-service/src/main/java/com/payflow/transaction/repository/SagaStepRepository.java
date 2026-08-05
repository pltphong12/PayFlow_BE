package com.payflow.transaction.repository;

import com.payflow.transaction.entity.SagaStep;
import com.payflow.transaction.entity.SagaStepName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SagaStepRepository extends JpaRepository<SagaStep, UUID> {

    Optional<SagaStep> findByTransactionIdAndStepName(
        UUID transactionId,
        SagaStepName stepName
    );
}
