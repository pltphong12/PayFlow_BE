package com.payflow.transaction.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.event.TransferCompleted;
import com.payflow.transaction.entity.OutboxEvent;
import com.payflow.transaction.entity.OutboxEventStatus;
import com.payflow.transaction.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionOutboxPublishingServiceTest {

    @Test
    void publish_whenEventIsPending_sendsKafkaMessageAndMarksEventSent()
            throws Exception {
        OutboxEventRepository outboxEventRepository =
                mock(OutboxEventRepository.class);

        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate =
                mock(KafkaTemplate.class);

        UUID transactionId = UUID.randomUUID();
        TransferCompleted event = new TransferCompleted(
                UUID.randomUUID(),
                transactionId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("10000"),
                Instant.now()
        );
        ObjectMapper objectMapper = new ObjectMapper()
                .findAndRegisterModules();

        OutboxEvent outboxEvent = new OutboxEvent(
                transactionId,
                TransferCompleted.class.getSimpleName(),
                objectMapper.writeValueAsString(event)
        );
        ReflectionTestUtils.setField(
                outboxEvent,
                "id",
                UUID.randomUUID()
        );

        when(outboxEventRepository.findById(outboxEvent.getId()))
                .thenReturn(Optional.of(outboxEvent));
        when(kafkaTemplate.send(
                eq("transaction-events"),
                eq(transactionId.toString()),
                any(TransferCompleted.class)
        )).thenReturn(CompletableFuture.completedFuture(null));

        TransactionOutboxPublishingService publishingService =
                new TransactionOutboxPublishingService(
                        outboxEventRepository,
                        kafkaTemplate,
                        objectMapper
                );

        ReflectionTestUtils.setField(
                publishingService,
                "transactionEventsTopic",
                "transaction-events"
        );

        publishingService.publish(outboxEvent.getId());

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(outboxEvent.getSentAt()).isNotNull();
        verify(kafkaTemplate).send(
                eq("transaction-events"),
                eq(transactionId.toString()),
                any(TransferCompleted.class)
        );
    }
}
