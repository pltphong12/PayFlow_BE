package com.payflow.transaction.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.event.TransferCompleted;
import com.payflow.common.event.TransferFailed;
import com.payflow.common.exception.BusinessException;
import com.payflow.transaction.entity.*;
import com.payflow.transaction.repository.OutboxEventRepository;
import com.payflow.transaction.repository.SagaStepRepository;
import com.payflow.transaction.repository.TransferTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferSagaStateService {

    private final TransferTransactionRepository transactionRepository;
    private final SagaStepRepository sagaStepRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

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
        TransferTransaction transaction = getById(transactionId);
        if (isTerminal(transaction)) {
            return;
        }
        getStep(transactionId, SagaStepName.DEBIT_SENDER).markSuccess();
    }

    @Transactional
    public void markDebitFailed(UUID transactionId) {
        TransferTransaction transaction = getById(transactionId);
        if (isTerminal(transaction)) {
            return;
        }
        getStep(transactionId, SagaStepName.DEBIT_SENDER).markFailed();
        transaction.markFailed();
        createTransferFailedOutboxIfAbsent(
            transaction,
            "Sender wallet debit failed"
        );
    }

    @Transactional
    public void markCreditSuccessAndComplete(UUID transactionId) {
        TransferTransaction transaction = getById(transactionId);
        if (isTerminal(transaction)) {
            return;
        }
        getStep(transactionId, SagaStepName.CREDIT_RECEIVER).markSuccess();
        transaction.markCompleted();
        createTransferCompletedOutboxIfAbsent(transaction);
    }

    @Transactional
    public void markCompensating(UUID transactionId) {
        TransferTransaction transaction = getById(transactionId);
        if (isTerminal(transaction)) {
            return;
        }
        getStep(transactionId, SagaStepName.CREDIT_RECEIVER).markFailed();
        transaction.markCompensating();
    }

    @Transactional
    public void markCompensatedAndFailed(UUID transactionId) {
        TransferTransaction transaction = getById(transactionId);
        if (isTerminal(transaction)) {
            return;
        }
        getStep(transactionId, SagaStepName.DEBIT_SENDER).markCompensated();
        transaction.markFailed();
        createTransferFailedOutboxIfAbsent(
            transaction,
            "Receiver wallet credit failed; sender was refunded"
        );
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

    private void createTransferCompletedOutboxIfAbsent(
        TransferTransaction transaction
    ) {
        String eventType = TransferCompleted.class.getSimpleName();
        if (outboxEventRepository.existsByAggregateIdAndEventType(
            transaction.getId(),
            eventType
        )) {
            return;
        }
        TransferCompleted event = new TransferCompleted(
            UUID.randomUUID(),
            transaction.getId(),
            transaction.getSenderUserId(),
            transaction.getReceiverUserId(),
            transaction.getAmount(),
            Instant.now()
        );
        saveOutboxEvent(transaction.getId(), eventType, serialize(event));
    }

    private void createTransferFailedOutboxIfAbsent(
        TransferTransaction transaction,
        String failureReason
    ) {
        String eventType = TransferFailed.class.getSimpleName();
        if (outboxEventRepository.existsByAggregateIdAndEventType(
            transaction.getId(),
            eventType
        )) {
            return;
        }
        TransferFailed event = new TransferFailed(
            UUID.randomUUID(),
            transaction.getId(),
            transaction.getSenderUserId(),
            transaction.getReceiverUserId(),
            transaction.getAmount(),
            failureReason,
            Instant.now()
        );
        saveOutboxEvent(transaction.getId(), eventType, serialize(event));
    }

    private void saveOutboxEvent(
        UUID aggregateId,
        String eventType,
        String payload
    ) {
        try {
            outboxEventRepository.save(new OutboxEvent(
                aggregateId,
                eventType,
                payload
            ));
        } catch (DataIntegrityViolationException exception) {
            log.info(
                "Ignoring duplicate terminal outbox event, aggregateId={}, eventType={}",
                aggregateId,
                eventType
            );
        }
    }

    private boolean isTerminal(TransferTransaction transaction) {
        return transaction.getStatus() == TransactionStatus.COMPLETED
            || transaction.getStatus() == TransactionStatus.FAILED;
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                "Cannot serialize transfer outbox event",
                exception
            );
        }
    }
}
