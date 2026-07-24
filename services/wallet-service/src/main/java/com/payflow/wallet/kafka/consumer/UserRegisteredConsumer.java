package com.payflow.wallet.kafka.consumer;

import com.payflow.common.event.UserRegistered;
import com.payflow.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredConsumer {
    private final WalletService walletService;

    @KafkaListener(topics = "${payflow.kafka.topics.user-events}")
    public void onUserRegistered(UserRegistered event) {
        log.info("Received UserRegisteredEvent for userId {}", event.userId());
        try {
            walletService.createWalletIfAbsent(event.userId());
        } catch (IllegalArgumentException e) {
            // không DLQ — log lỗi, không nuốt exception nếu muốn Kafka retry offset
            log.error("Failed to create wallet for userId {}", event.userId(), e);
            throw e;
        }
    }
}
