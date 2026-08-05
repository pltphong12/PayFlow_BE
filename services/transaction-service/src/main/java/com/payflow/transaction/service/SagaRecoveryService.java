package com.payflow.transaction.service;

import com.payflow.transaction.client.WalletServiceClient;
import com.payflow.transaction.client.dto.WalletLookupResponse;
import com.payflow.transaction.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaRecoveryService {

    private final TransferSagaStateService sagaStateService;
    private final WalletServiceClient walletServiceClient;

    public void resume(TransferTransaction transaction) {
        if (transaction.getStatus() == TransactionStatus.PENDING) {
            resumePending(transaction);
            return;
        }
        if (transaction.getStatus() == TransactionStatus.COMPENSATING) {
            resumeCompensation(transaction);
        }
    }

    private void resumePending(TransferTransaction transaction) {
        SagaStep debitStep = sagaStateService.getSagaStep(
            transaction.getId(),
            SagaStepName.DEBIT_SENDER
        );
        if (debitStep.getStatus() == SagaStepStatus.PENDING) {
            retryDebit(transaction);
            return;
        }
        if (debitStep.getStatus() == SagaStepStatus.SUCCESS) {
            retryCreditReceiver(transaction);
        }
    }

    private void retryDebit(TransferTransaction transaction) {
        UUID transactionId = transaction.getId();
        try {
            WalletLookupResponse senderWallet =
                walletServiceClient.getWalletByUserId(
                    transaction.getSenderUserId()
                );
            walletServiceClient.debit(
                senderWallet.id(),
                transactionId,
                transaction.getAmount()
            );
            sagaStateService.markDebitSuccess(transactionId);
            log.info(
                "[SAGA] step=DEBIT_SENDER status=RECOVERED transactionId={}",
                transactionId
            );
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                sagaStateService.markDebitFailed(transactionId);
                log.info(
                    "[SAGA] step=DEBIT_SENDER status=FAILED transactionId={} httpStatus={}",
                    transactionId,
                    exception.getStatusCode()
                );
                return;
            }
            log.error(
                "[SAGA] step=DEBIT_SENDER status=RETRY_PENDING transactionId={}",
                transactionId,
                exception
            );
        } catch (ResourceAccessException exception) {
            log.error(
                "[SAGA] step=DEBIT_SENDER status=RETRY_PENDING transactionId={}",
                transactionId,
                exception
            );
        }
    }

    private void retryCreditReceiver(TransferTransaction transaction) {
        UUID transactionId = transaction.getId();
        try {
            WalletLookupResponse receiverWallet =
                walletServiceClient.getWalletByUserId(
                    transaction.getReceiverUserId()
                );
            walletServiceClient.credit(
                receiverWallet.id(),
                transactionId,
                transaction.getAmount()
            );
            sagaStateService.markCreditSuccessAndComplete(transactionId);
            log.info(
                "[SAGA] step=CREDIT_RECEIVER status=RECOVERED transactionId={}",
                transactionId
            );
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                sagaStateService.markCompensating(transactionId);
                resumeCompensation(transaction);
                return;
            }
            log.error(
                "[SAGA] step=CREDIT_RECEIVER status=RETRY_PENDING transactionId={}",
                transactionId,
                exception
            );
        } catch (ResourceAccessException exception) {
            log.error(
                "[SAGA] step=CREDIT_RECEIVER status=RETRY_PENDING transactionId={}",
                transactionId,
                exception
            );
        }
    }

    private void resumeCompensation(TransferTransaction transaction) {
        UUID transactionId = transaction.getId();
        try {
            WalletLookupResponse senderWallet =
                walletServiceClient.getWalletByUserId(
                    transaction.getSenderUserId()
                );
            walletServiceClient.credit(
                senderWallet.id(),
                transactionId,
                transaction.getAmount()
            );
            sagaStateService.markCompensatedAndFailed(transactionId);
            log.info(
                "[SAGA] step=COMPENSATE_SENDER status=RECOVERED transactionId={}",
                transactionId
            );
        } catch (RestClientResponseException | ResourceAccessException exception) {
            log.error(
                "[SAGA] step=COMPENSATE_SENDER status=RETRY_PENDING transactionId={}",
                transactionId,
                exception
            );
        }
    }
}
