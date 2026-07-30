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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TopupCompletionService {

    private final TopupRequestRepository topupRequestRepository;
    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void completeTopup(UUID topupRequestId, TopupStatus gatewayResult) {
        TopupRequest topupRequest = topupRequestRepository
            .findById(topupRequestId)
            .orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND,
                "Topup request not found"
            ));
        // Check if topup in db that have id equal current id, it will return (Idempotency)
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
        // Looking for wallet and add amount into wallet
        Wallet wallet = walletRepository
            .findByUserId(topupRequest.getUserId())
            .orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND,
                "Wallet not found"
            ));
        wallet.credit(topupRequest.getAmount());
        // Create ledger in order to view when we need, it will be useful for reporting and auditing
        LedgerEntry ledgerEntry = new LedgerEntry(
            wallet,
            topupRequest.getId(),
            LedgerEntryType.CREDIT,
            topupRequest.getAmount(),
            wallet.getBalance()
        );
        ledgerEntryRepository.save(ledgerEntry);

        // Create kafka-event and save outbox
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
        OutboxEvent outboxEvent = new OutboxEvent(
            eventId,
            topupRequest.getId(),
            WalletCredited.class.getSimpleName(),
            serialize(event)
        );
        outboxEventRepository.save(outboxEvent);
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
