package com.payflow.wallet.service;

import com.payflow.common.exception.BusinessException;
import com.payflow.wallet.dto.response.LedgerEntryResponse;
import com.payflow.wallet.dto.response.WalletResponse;
import com.payflow.wallet.entity.LedgerEntry;
import com.payflow.wallet.entity.ProcessedEvent;
import com.payflow.wallet.entity.Wallet;
import com.payflow.wallet.repository.ProcessedEventRepository;
import com.payflow.wallet.repository.LedgerEntryRepository;
import com.payflow.wallet.repository.WalletRepository;
import lombok.NonNull;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import com.payflow.common.event.UserRegistered;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {
    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void handleUserRegistered(@NonNull UserRegistered event) {
        try {
            processedEventRepository.saveAndFlush(new ProcessedEvent(event.eventId()));
        } catch (DataIntegrityViolationException exception) {
            log.info(
                "Ignoring duplicate UserRegistered eventId={}",
                event.eventId()
            );
            return;
        }
        createWalletIfAbsent(event.userId());
    }

    @Transactional
    public void createWalletIfAbsent(UUID userId) {
        int inserted = walletRepository.insertWalletIfAbsent(
            UUID.randomUUID(),
            userId
        );
        if (inserted == 0) {
            log.info("Wallet already exists for userId {}", userId);
            return;
        }
        log.info("Created wallet for userId {}", userId);
    }

    @Transactional(readOnly = true)
    public WalletResponse getWalletByUserId(UUID userId) {
        Wallet wallet = findByUserId(userId);
        return toWalletResponse(wallet);
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> getLedgerByUserId(UUID userId,Pageable pageable) {
        Wallet wallet = findByUserId(userId);
        return ledgerEntryRepository
            .findByWallet_IdOrderByCreatedAtDesc(wallet.getId(), pageable)
            .map(this::toLedgerResponse);
    }

    public Wallet findByUserId(UUID userId) {
        return walletRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Wallet not found for userId"));
    }

    // convert entity to response DTO
    private WalletResponse toWalletResponse(Wallet wallet) {
        return WalletResponse.builder()
            .id(wallet.getId())
            .userId(wallet.getUserId())
            .balance(wallet.getBalance())
            .currency(wallet.getCurrency())
            .status(wallet.getStatus())
            .createdAt(wallet.getCreatedAt())
            .build();
    }
    private LedgerEntryResponse toLedgerResponse(LedgerEntry entry) {
        return LedgerEntryResponse.builder()
            .id(entry.getId())
            .transactionId(entry.getTransactionId())
            .entryType(entry.getEntryType())
            .amount(entry.getAmount())
            .balanceAfter(entry.getBalanceAfter())
            .createdAt(entry.getCreatedAt())
            .build();
    }
}
