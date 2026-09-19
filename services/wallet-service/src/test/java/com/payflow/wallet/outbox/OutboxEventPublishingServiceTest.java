package com.payflow.wallet.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.event.WalletCredited;
import com.payflow.wallet.entity.OutboxEvent;
import com.payflow.wallet.entity.OutboxEventStatus;
import com.payflow.wallet.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventPublishingServiceTest {

    @Test
    void publish_whenEventIsPending_sendsKafkaMessageAndMarksEventSent()
        throws Exception {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, WalletCredited> kafkaTemplate = mock(KafkaTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        WalletCredited event = new WalletCredited(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("50000"),
            Instant.now()
        );
        OutboxEvent outboxEvent = new OutboxEvent(
            event.eventId(),
            event.topupRequestId(),
            WalletCredited.class.getSimpleName(),
            objectMapper.writeValueAsString(event)
        );

        when(repository.findById(outboxEvent.getId())).thenReturn(Optional.of(outboxEvent));
        when(kafkaTemplate.send(
            eq("wallet-events"),
            eq(event.walletId().toString()),
            any(WalletCredited.class)
        )).thenReturn(CompletableFuture.completedFuture(null));

        OutboxEventPublishingService service = new OutboxEventPublishingService(
            repository,
            kafkaTemplate,
            objectMapper
        );
        org.springframework.test.util.ReflectionTestUtils.setField(
            service,
            "walletEventsTopic",
            "wallet-events"
        );

        service.publish(outboxEvent.getId());

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(outboxEvent.getSentAt()).isNotNull();
        verify(kafkaTemplate).send(
            eq("wallet-events"),
            eq(event.walletId().toString()),
            any(WalletCredited.class)
        );
    }

    @Test
    void publish_whenKafkaFails_keepsEventPending() throws Exception {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, WalletCredited> kafkaTemplate = mock(KafkaTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        WalletCredited event = new WalletCredited(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("50000"),
            Instant.now()
        );
        OutboxEvent outboxEvent = new OutboxEvent(
            event.eventId(),
            event.topupRequestId(),
            WalletCredited.class.getSimpleName(),
            objectMapper.writeValueAsString(event)
        );

        CompletableFuture<SendResult<String, WalletCredited>> failedFuture =
            new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("kafka down"));

        when(repository.findById(outboxEvent.getId())).thenReturn(Optional.of(outboxEvent));
        when(kafkaTemplate.send(
            eq("wallet-events"),
            eq(event.walletId().toString()),
            any(WalletCredited.class)
        )).thenReturn(failedFuture);

        OutboxEventPublishingService service = new OutboxEventPublishingService(
            repository,
            kafkaTemplate,
            objectMapper
        );
        org.springframework.test.util.ReflectionTestUtils.setField(
            service,
            "walletEventsTopic",
            "wallet-events"
        );

        assertThatThrownBy(() -> service.publish(outboxEvent.getId()))
            .isInstanceOf(IllegalStateException.class);
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEvent.getSentAt()).isNull();
    }
}
