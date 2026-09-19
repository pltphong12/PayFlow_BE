package com.payflow.wallet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.event.WalletCredited;
import com.payflow.common.exception.BusinessException;
import com.payflow.wallet.entity.*;
import com.payflow.wallet.repository.LedgerEntryRepository;
import com.payflow.wallet.repository.OutboxEventRepository;
import com.payflow.wallet.repository.TopupRequestRepository;
import com.payflow.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TopupCompletionService {
    private static final int MAX_RETRIES = 3;

    private final TopupRequestRepository topupRequestRepository;
    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public void completeTopup(UUID topupRequestId, TopupStatus gatewayResult) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(
                    ignored -> completeTopupInTransaction(topupRequestId, gatewayResult)
                );
                return;
            } catch (OptimisticLockingFailureException exception) {
                if (attempt == MAX_RETRIES) {
                    throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "Wallet balance changed concurrently. Please retry."
                    );
                }
                log.info(
                    "Retrying topup completion after optimistic-lock conflict, topupRequestId={}, attempt={}",
                    topupRequestId,
                    attempt + 1
                );
            }
        }
    }

    private void completeTopupInTransaction(
        UUID topupRequestId,
        TopupStatus gatewayResult
    ) {
        TopupRequest topupRequest = topupRequestRepository
            .findById(topupRequestId)
            .orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND,
                "Topup request not found"
            ));
        if (topupRequest.getStatus() != TopupStatus.PENDING) {
            log.info(
                "Ignoring duplicate gateway result for topupRequestId={}",
                topupRequestId
            );
            return;
        }
        if (gatewayResult == TopupStatus.FAILED) {
            topupRequest.markFailed();
            log.info("Topup failed, topupRequestId={}", topupRequestId);
            return;
        }

        Wallet wallet = walletRepository
            .findByUserId(topupRequest.getUserId())
            .orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND,
                "Wallet not found"
            ));
        wallet.credit(topupRequest.getAmount());
        walletRepository.flush();

        ledgerEntryRepository.save(new LedgerEntry(
            wallet,
            topupRequest.getId(),
            LedgerEntryType.CREDIT,
            topupRequest.getAmount(),
            wallet.getBalance()
        ));

        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        WalletCredited event = new WalletCredited(
            eventId,
            wallet.getId(),
            wallet.getUserId(),
            topupRequest.getId(),
            topupRequest.getAmount(),
            occurredAt
        );
        outboxEventRepository.save(new OutboxEvent(
            eventId,
            topupRequest.getId(),
            WalletCredited.class.getSimpleName(),
            serialize(event)
        ));
        topupRequest.markSuccess();
        log.info(
            "Topup completed successfully, topupRequestId={}, walletId={}, amount={}",
            topupRequest.getId(),
            wallet.getId(),
            topupRequest.getAmount()
        );
    }

    private String serialize(WalletCredited event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                "Cannot serialize WalletCredited event",
                exception
            );
        }
    }
}
