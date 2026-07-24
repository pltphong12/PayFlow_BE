package com.payflow.wallet.service;

import com.payflow.wallet.entity.Wallet;
import com.payflow.wallet.repository.WalletRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {
    private final WalletRepository walletRepository;

    @Transactional
    public void createWalletIfAbsent(UUID userId) {
        if (walletRepository.existsByUserId(userId)) {
            log.info("Wallet already exists for userId {}", userId);
            return;
        }
        Wallet wallet = walletRepository.save(new Wallet(userId));
        log.info("Created wallet id {} for userId {}", wallet.getId(), userId);
    }
}
