package com.payflow.wallet.service;

import com.payflow.common.exception.BusinessException;
import com.payflow.wallet.dto.response.LedgerEntryResponse;
import com.payflow.wallet.dto.response.WalletResponse;
import com.payflow.wallet.entity.LedgerEntry;
import com.payflow.wallet.entity.Wallet;
import com.payflow.wallet.repository.LedgerEntryRepository;
import com.payflow.wallet.repository.WalletRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {
    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional
    public void createWalletIfAbsent(UUID userId) {
        if (walletRepository.existsByUserId(userId)) {
            log.info("Wallet already exists for userId {}", userId);
            return;
        }
        Wallet wallet = walletRepository.save(new Wallet(userId));
        log.info("Created wallet id {} for userId {}", wallet.getId(), userId);
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
