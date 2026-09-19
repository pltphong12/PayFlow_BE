package com.payflow.wallet.kafka.consumer;

import com.payflow.common.event.UserRegistered;
import com.payflow.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserRegisteredConsumerTest {

    @Mock
    WalletService walletService;

    @Test
    void onUserRegistered_callsWalletServiceHandler() {
        UserRegisteredConsumer consumer = new UserRegisteredConsumer(walletService);

        UUID userId = UUID.randomUUID();
        UserRegistered event = new UserRegistered(
                UUID.randomUUID(),
                userId,
                "user@example.com",
                "User Name",
                Instant.now()
        );

        consumer.onUserRegistered(event);

        verify(walletService, times(1)).handleUserRegistered(event);
    }
}

