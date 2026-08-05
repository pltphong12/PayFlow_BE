package com.payflow.transaction.service;

import com.payflow.common.exception.BusinessException;
import com.payflow.transaction.entity.SagaStep;
import com.payflow.transaction.entity.SagaStepName;
import com.payflow.transaction.entity.TransactionStatus;
import com.payflow.transaction.entity.TransferTransaction;
import com.payflow.transaction.repository.SagaStepRepository;
import com.payflow.transaction.repository.TransferTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferSagaStateService {

    private final TransferTransactionRepository transactionRepository;
    private final SagaStepRepository sagaStepRepository;

    @Transactional(readOnly = true)
    public Optional<TransferTransaction> findByIdempotencyKey(String idempotencyKey) {
        return transactionRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Transactional(readOnly = true)
    public TransferTransaction getById(UUID transactionId) {
        return transactionRepository.findById(transactionId)
            .orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND,
                "Transfer transaction not found"
            ));
    }

    @Transactional
    public TransferTransaction createPendingTransfer(
        UUID senderUserId,
        UUID receiverUserId,
        BigDecimal amount,
        String idempotencyKey
    ) {
        // Create transaction with idempotency key (Status = PENDING)
        TransferTransaction transaction = transactionRepository.save(
            new TransferTransaction(
                senderUserId,
                receiverUserId,
                amount,
                idempotencyKey
            )
        );
        // Save steps for other transaction (Status = PENDING)
        sagaStepRepository.save(
            new SagaStep(transaction.getId(), SagaStepName.DEBIT_SENDER)
        );
        sagaStepRepository.save(
            new SagaStep(transaction.getId(), SagaStepName.CREDIT_RECEIVER)
        );

        return transaction;
    }

    @Transactional
    public void markDebitSuccess(UUID transactionId) {
        getStep(transactionId, SagaStepName.DEBIT_SENDER).markSuccess();
    }

    @Transactional
    public void markDebitFailed(UUID transactionId) {
        getStep(transactionId, SagaStepName.DEBIT_SENDER).markFailed();
        getById(transactionId).markFailed();
    }

    @Transactional
    public void markCreditSuccessAndComplete(UUID transactionId) {
        getStep(transactionId, SagaStepName.CREDIT_RECEIVER).markSuccess();
        getById(transactionId).markCompleted();
    }

    @Transactional
    public void markCompensating(UUID transactionId) {
        getStep(transactionId, SagaStepName.CREDIT_RECEIVER).markFailed();
        getById(transactionId).markCompensating();
    }

    @Transactional
    public void markCompensatedAndFailed(UUID transactionId) {
        getStep(transactionId, SagaStepName.DEBIT_SENDER).markCompensated();
        getById(transactionId).markFailed();
    }

    @Transactional(readOnly = true)
    public List<TransferTransaction> findRecoverableTransactions() {
        return transactionRepository.findTop100ByStatusInOrderByCreatedAtAsc(
            List.of(
                TransactionStatus.PENDING,
                TransactionStatus.COMPENSATING
            )
        );
    }
    @Transactional(readOnly = true)
    public SagaStep getSagaStep(
        UUID transactionId,
        SagaStepName stepName
    ) {
        return getStep(transactionId, stepName);
    }

    private SagaStep getStep(UUID transactionId, SagaStepName stepName) {
        return sagaStepRepository
            .findByTransactionIdAndStepName(transactionId, stepName)
            .orElseThrow(() -> new BusinessException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Saga step not found: " + stepName
            ));
    }
}
