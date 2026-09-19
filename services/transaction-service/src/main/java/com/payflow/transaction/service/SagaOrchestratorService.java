package com.payflow.transaction.service;

import com.payflow.common.exception.BusinessException;
import com.payflow.transaction.client.WalletServiceClient;
import com.payflow.transaction.client.dto.WalletLookupResponse;
import com.payflow.transaction.dto.response.TransferResponse;
import com.payflow.transaction.entity.TransferTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaOrchestratorService {

    private final WalletServiceClient walletServiceClient;
    private final TransferSagaStateService sagaStateService;

    public TransferResponse transfer(
        UUID senderUserId,
        UUID receiverUserId,
        BigDecimal amount,
        String idempotencyKey
    ) {
        // Check idempotency
        TransferTransaction existingTransaction = sagaStateService
            .findByIdempotencyKey(idempotencyKey)
            .orElse(null);
        if (existingTransaction != null) {
            assertIdempotentPayload(
                existingTransaction,
                senderUserId,
                receiverUserId,
                amount
            );
            log.info(
                "[SAGA] step=IDEMPOTENCY status=EXISTING transactionId={}",
                existingTransaction.getId()
            );
            return toResponse(existingTransaction);
        }

        // Check sender and receiver
        if (senderUserId.equals(receiverUserId)) {
            throw new BusinessException(
                HttpStatus.BAD_REQUEST,
                "Sender and receiver must be different"
            );
        }

        // Get wallet but don't mutate balance
        WalletLookupResponse senderWallet = walletServiceClient.getWalletByUserId(senderUserId);
        WalletLookupResponse receiverWallet = walletServiceClient.getWalletByUserId(receiverUserId);

        // Create transaction
        TransferTransaction transaction;
        try {
            transaction = sagaStateService.createPendingTransfer(
                senderUserId,
                receiverUserId,
                amount,
                idempotencyKey
            );
        } catch (DataIntegrityViolationException exception) {
            transaction = sagaStateService
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> exception);
            assertIdempotentPayload(
                transaction,
                senderUserId,
                receiverUserId,
                amount
            );
            log.info(
                "[SAGA] step=IDEMPOTENCY status=RACE_RECOVERED transactionId={}",
                transaction.getId()
            );
            return toResponse(transaction);
        }
        UUID transactionId = transaction.getId();
        log.info(
            "[SAGA] step=INIT status=SUCCESS transactionId={}",
            transactionId
        );

        // Step 1: Debit sender
        try {
            walletServiceClient.debit(
                senderWallet.id(),
                transactionId,
                amount
            );
            sagaStateService.markDebitSuccess(transactionId);
            log.info(
                "[SAGA] step=DEBIT_SENDER status=SUCCESS transactionId={}",
                transactionId
            );
        } catch (RestClientResponseException exception) {
            if (!exception.getStatusCode().is4xxClientError()) {
                throw indeterminateFailure(
                    transactionId,
                    "DEBIT_SENDER",
                    exception
                );
            }
            sagaStateService.markDebitFailed(transactionId);
            log.info(
                "[SAGA] step=DEBIT_SENDER status=FAILED transactionId={} httpStatus={}",
                transactionId,
                exception.getStatusCode()
            );
            throw new BusinessException(
                HttpStatus.CONFLICT,
                "Transfer failed: sender wallet cannot be debited"
            );
        } catch (ResourceAccessException exception) {
            throw indeterminateFailure(
                transactionId,
                "DEBIT_SENDER",
                exception
            );
        } catch (IllegalStateException exception) {
            throw indeterminateFailure(
                transactionId,
                "DEBIT_SENDER",
                exception
            );
        }

        // Step 2: Credit receiver
        try{
            walletServiceClient.credit(
                receiverWallet.id(),
                transactionId,
                amount
            );
            sagaStateService.markCreditSuccessAndComplete(transactionId);
            log.info(
                "[SAGA] step=CREDIT_RECEIVER status=SUCCESS transactionId={}",
                transactionId
            );
            return toResponse(sagaStateService.getById(transactionId));
        } catch (RestClientResponseException exception) {
            if (!exception.getStatusCode().is4xxClientError()) {
                throw indeterminateFailure(
                    transactionId,
                    "CREDIT_RECEIVER",
                    exception
                );
            }
            log.info(
                "[SAGA] step=CREDIT_RECEIVER status=FAILED transactionId={} httpStatus={}",
                transactionId,
                exception.getStatusCode()
            );
            return compensateSender(
                transactionId,
                senderWallet.id(),
                amount
            );
        } catch (ResourceAccessException exception) {
            throw indeterminateFailure(
                transactionId,
                "CREDIT_RECEIVER",
                exception
            );
        } catch (IllegalStateException exception) {
            throw indeterminateFailure(
                transactionId,
                "CREDIT_RECEIVER",
                exception
            );
        }
    }

    public TransferResponse getTransfer(
        UUID requesterUserId,
        UUID transactionId
    ) {
        TransferTransaction transaction =
            sagaStateService.getById(transactionId);
        // Check ownership
        boolean isParticipant = requesterUserId.equals(
            transaction.getSenderUserId()
        ) || requesterUserId.equals(
            transaction.getReceiverUserId()
        );

        if (!isParticipant) {
            throw new BusinessException(
                HttpStatus.FORBIDDEN,
                "You do not have access to this transfer"
            );
        }
        return toResponse(transaction);
    }

    private TransferResponse compensateSender(
        UUID transactionId,
        UUID senderWalletId,
        BigDecimal amount
    ) {
        // change status to compensating
        sagaStateService.markCompensating(transactionId);
        log.info(
            "[SAGA] step=COMPENSATE_SENDER status=STARTED transactionId={}",
            transactionId
        );

        try {
            walletServiceClient.credit(
                senderWalletId,
                transactionId,
                amount
            );
            sagaStateService.markCompensatedAndFailed(transactionId);
            log.info(
                "[SAGA] step=COMPENSATE_SENDER status=SUCCESS transactionId={}",
                transactionId
            );
            throw new BusinessException(
                HttpStatus.CONFLICT,
                "Transfer failed: sender balance was refunded"
            );
        } catch (RestClientResponseException exception) {
            log.error(
                "[SAGA] step=COMPENSATE_SENDER status=FAILED transactionId={}",
                transactionId,
                exception
            );
            // Transaction keep status = COMPENSATING.
            // Recovery job will retry refund with the same transactionId.
            throw new BusinessException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Transfer compensation is pending"
            );
        } catch (ResourceAccessException | IllegalStateException exception) {
            log.error(
                "[SAGA] step=COMPENSATE_SENDER status=FAILED transactionId={}",
                transactionId,
                exception
            );
            throw new BusinessException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Transfer compensation is pending"
            );
        }
    }

    private BusinessException indeterminateFailure(
        UUID transactionId,
        String step,
        Exception exception
    ) {
        log.error(
            "[SAGA] step={} status=UNKNOWN transactionId={}",
            step,
            transactionId,
            exception
        );
        return new BusinessException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Transfer is pending because Wallet Service is temporarily unavailable"
        );
    }

    private TransferResponse toResponse(TransferTransaction transaction) {
        return new TransferResponse(
            transaction.getId(),
            transaction.getType(),
            transaction.getSenderUserId(),
            transaction.getReceiverUserId(),
            transaction.getAmount(),
            transaction.getStatus(),
            transaction.getCreatedAt()
        );
    }

    private void assertIdempotentPayload(
        TransferTransaction existingTransaction,
        UUID senderUserId,
        UUID receiverUserId,
        BigDecimal amount
    ) {
        boolean sameSender = existingTransaction.getSenderUserId().equals(senderUserId);
        boolean sameReceiver = existingTransaction.getReceiverUserId().equals(receiverUserId);
        boolean sameAmount = existingTransaction.getAmount().compareTo(amount) == 0;

        if (!sameSender || !sameReceiver || !sameAmount) {
            throw new BusinessException(
                HttpStatus.CONFLICT,
                "Idempotency-Key was already used with different transfer data"
            );
        }
    }
}
