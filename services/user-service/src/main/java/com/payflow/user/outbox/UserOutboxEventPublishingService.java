package com.payflow.user.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.event.UserRegistered;
import com.payflow.common.exception.BusinessException;
import com.payflow.user.entity.OutboxEvent;
import com.payflow.user.entity.OutboxEventStatus;
import com.payflow.user.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserOutboxEventPublishingService {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, UserRegistered> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${payflow.kafka.topics.user-events}")
    private String userEventsTopic;

    @Transactional
    public void publish(UUID eventId) {
        OutboxEvent outboxEvent = outboxEventRepository.findById(eventId)
            .orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND,
                "Outbox event not found"
            ));
        if (outboxEvent.getStatus() != OutboxEventStatus.PENDING) {
            return;
        }

        UserRegistered event = deserialize(outboxEvent);
        try {
            kafkaTemplate
                .send(userEventsTopic, event.userId().toString(), event)
                .get();
            outboxEvent.markSent();
            log.info(
                "Published UserRegistered from outbox, eventId={}, userId={}",
                event.eventId(),
                event.userId()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Interrupted while publishing outbox event: " + eventId,
                exception
            );
        } catch (ExecutionException exception) {
            throw new IllegalStateException(
                "Kafka publish failed for outbox event: " + eventId,
                exception
            );
        }
    }

    private UserRegistered deserialize(OutboxEvent outboxEvent) {
        try {
            return objectMapper.readValue(
                outboxEvent.getPayload(),
                UserRegistered.class
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                "Cannot deserialize outbox event: " + outboxEvent.getId(),
                exception
            );
        }
    }
}
