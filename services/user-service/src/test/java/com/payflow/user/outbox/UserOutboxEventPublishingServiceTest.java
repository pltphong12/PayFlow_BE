package com.payflow.user.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.event.UserRegistered;
import com.payflow.user.entity.OutboxEvent;
import com.payflow.user.entity.OutboxEventStatus;
import com.payflow.user.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

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

class UserOutboxEventPublishingServiceTest {

    @Test
    void publish_whenEventIsPending_sendsKafkaMessageAndMarksSent() throws Exception {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, UserRegistered> kafkaTemplate = mock(KafkaTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        UserRegistered event = new UserRegistered(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "user@payflow.vn",
            "User",
            Instant.now()
        );
        OutboxEvent outboxEvent = new OutboxEvent(
            event.userId(),
            UserRegistered.class.getSimpleName(),
            objectMapper.writeValueAsString(event)
        );
        ReflectionTestUtils.setField(outboxEvent, "id", UUID.randomUUID());

        when(repository.findById(outboxEvent.getId())).thenReturn(Optional.of(outboxEvent));
        when(kafkaTemplate.send(
            eq("user-events"),
            eq(event.userId().toString()),
            any(UserRegistered.class)
        )).thenReturn(CompletableFuture.completedFuture(null));

        UserOutboxEventPublishingService service = new UserOutboxEventPublishingService(
            repository,
            kafkaTemplate,
            objectMapper
        );
        ReflectionTestUtils.setField(service, "userEventsTopic", "user-events");

        service.publish(outboxEvent.getId());

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(outboxEvent.getSentAt()).isNotNull();
        verify(kafkaTemplate).send(
            eq("user-events"),
            eq(event.userId().toString()),
            any(UserRegistered.class)
        );
    }

    @Test
    void publish_whenKafkaFails_keepsEventPending() throws Exception {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, UserRegistered> kafkaTemplate = mock(KafkaTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        UserRegistered event = new UserRegistered(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "user@payflow.vn",
            "User",
            Instant.now()
        );
        OutboxEvent outboxEvent = new OutboxEvent(
            event.userId(),
            UserRegistered.class.getSimpleName(),
            objectMapper.writeValueAsString(event)
        );
        ReflectionTestUtils.setField(outboxEvent, "id", UUID.randomUUID());

        CompletableFuture<SendResult<String, UserRegistered>> failedFuture =
            new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("kafka down"));

        when(repository.findById(outboxEvent.getId())).thenReturn(Optional.of(outboxEvent));
        when(kafkaTemplate.send(
            eq("user-events"),
            eq(event.userId().toString()),
            any(UserRegistered.class)
        )).thenReturn(failedFuture);

        UserOutboxEventPublishingService service = new UserOutboxEventPublishingService(
            repository,
            kafkaTemplate,
            objectMapper
        );
        ReflectionTestUtils.setField(service, "userEventsTopic", "user-events");

        assertThatThrownBy(() -> service.publish(outboxEvent.getId()))
            .isInstanceOf(IllegalStateException.class);

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEvent.getSentAt()).isNull();
    }
}
