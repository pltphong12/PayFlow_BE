package com.payflow.wallet.service;

import com.payflow.common.exception.BusinessException;
import com.payflow.wallet.dto.response.WalletMutationResponse;
import com.payflow.wallet.entity.LedgerEntry;
import com.payflow.wallet.entity.LedgerEntryType;
import com.payflow.wallet.entity.Wallet;
import com.payflow.wallet.entity.WalletStatus;
import com.payflow.wallet.repository.LedgerEntryRepository;
import com.payflow.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletMutationService {

    private static final int MAX_RETRIES = 3;

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransactionTemplate transactionTemplate;

    public WalletMutationResponse debit(
        UUID walletId,
        UUID transactionId,
        BigDecimal amount
    ) {
        return mutate(walletId, transactionId, amount, LedgerEntryType.DEBIT);
    }

    public WalletMutationResponse credit(
        UUID walletId,
        UUID transactionId,
        BigDecimal amount
    ) {
        return mutate(walletId, transactionId, amount, LedgerEntryType.CREDIT);
    }

    private WalletMutationResponse mutate(
        UUID walletId,
        UUID transactionId,
        BigDecimal amount,
        LedgerEntryType entryType
    ) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return Objects.requireNonNull(transactionTemplate.execute(
                    status -> applyMutation(walletId, transactionId, amount, entryType)
                ));
            } catch (OptimisticLockingFailureException exception) {
                if (attempt == MAX_RETRIES) {
                    throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "Wallet balance changed concurrently. Please retry."
                    );
                }
            }
        }
        throw new IllegalStateException("Unreachable code");
    }

    private WalletMutationResponse applyMutation(
        UUID walletId,
        UUID transactionId,
        BigDecimal amount,
        LedgerEntryType entryType
    ) {
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND,
                "Wallet not found"
            ));
        boolean alreadyProcessed = ledgerEntryRepository
            .existsByWallet_IdAndTransactionIdAndEntryType(
                walletId,
                transactionId,
                entryType
            );
        if (alreadyProcessed) {
            return toResponse(wallet);
        }
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new BusinessException(
                HttpStatus.CONFLICT,
                "Wallet is frozen"
            );
        }
        if (entryType == LedgerEntryType.DEBIT
            && wallet.getBalance().compareTo(amount) < 0) {
            throw new BusinessException(
                HttpStatus.CONFLICT,
                "Insufficient wallet balance"
            );
        }
        if (entryType == LedgerEntryType.DEBIT) {
            wallet.debit(amount);
        } else {
            wallet.credit(amount);
        }
        ledgerEntryRepository.save(new LedgerEntry(
            wallet,
            transactionId,
            entryType,
            amount,
            wallet.getBalance()
        ));
        // Ép JPA chạy UPDATE ngay để bắt optimistic-lock conflict trong lần retry này.
        walletRepository.flush();
        return toResponse(wallet);
    }

    private WalletMutationResponse toResponse(Wallet wallet) {
        return new WalletMutationResponse(
            wallet.getId(),
            wallet.getBalance(),
            wallet.getVersion()
        );
    }
}
