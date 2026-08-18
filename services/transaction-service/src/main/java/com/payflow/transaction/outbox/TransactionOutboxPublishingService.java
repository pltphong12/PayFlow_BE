package com.payflow.transaction.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.event.TransferCompleted;
import com.payflow.common.event.TransferFailed;
import com.payflow.common.exception.BusinessException;
import com.payflow.transaction.entity.OutboxEvent;
import com.payflow.transaction.entity.OutboxEventStatus;
import com.payflow.transaction.repository.OutboxEventRepository;
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
public class TransactionOutboxPublishingService {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${payflow.kafka.topics.transaction-events}")
    private String transactionEventsTopic;

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
        try {
            if ("TransferCompleted".equals(outboxEvent.getEventType())) {
                TransferCompleted event = objectMapper.readValue(
                    outboxEvent.getPayload(),
                    TransferCompleted.class
                );
                kafkaTemplate.send(
                    transactionEventsTopic,
                    event.transactionId().toString(),
                    event
                ).get();
                log.info(
                    "Published TransferCompleted, eventId={}, transactionId={}",
                    event.eventId(),
                    event.transactionId()
                );
            } else if ("TransferFailed".equals(outboxEvent.getEventType())) {
                TransferFailed event = objectMapper.readValue(
                    outboxEvent.getPayload(),
                    TransferFailed.class
                );
                kafkaTemplate.send(
                    transactionEventsTopic,
                    event.transactionId().toString(),
                    event
                ).get();
                log.info(
                    "Published TransferFailed, eventId={}, transactionId={}",
                    event.eventId(),
                    event.transactionId()
                );
            } else {
                throw new IllegalStateException(
                    "Unsupported outbox event type: "
                        + outboxEvent.getEventType()
                );
            }
            outboxEvent.markSent();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Interrupted while publishing outbox event: " + eventId,
                exception
            );
        } catch (ExecutionException | JsonProcessingException exception) {
            throw new IllegalStateException(
                "Failed to publish outbox event: " + eventId,
                exception
            );
        }
    }
}
