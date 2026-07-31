package com.payflow.wallet.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.event.WalletCredited;
import com.payflow.common.exception.BusinessException;
import com.payflow.wallet.entity.OutboxEvent;
import com.payflow.wallet.entity.OutboxEventStatus;
import com.payflow.wallet.repository.OutboxEventRepository;
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
public class OutboxEventPublishingService {
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, WalletCredited> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${payflow.kafka.topics.wallet-events}")
    private String walletEventsTopic;

    @Transactional
    public void publish(UUID eventId) {
        OutboxEvent outboxEvent = this.outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Outbox event not found"));
        if (outboxEvent.getStatus() != OutboxEventStatus.PENDING) {
            return;
        }
        WalletCredited event = this.deserialize(outboxEvent);

        try {
            this.kafkaTemplate.send(walletEventsTopic, event.walletId().toString(), event).get();
            outboxEvent.markSent();
            log.info(
                "Published WalletCredited, eventId={}, topupRequestId={}",
                event.eventId(),
                event.topupRequestId()
            );
        }catch (InterruptedException exception){
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Interrupted while publishing outbox event: " + eventId,
                exception
            );
        }catch (ExecutionException exception){
            throw new IllegalStateException(
                "Kafka publish failed for outbox event: " + eventId,
                exception
            );
        }
    }

    private WalletCredited deserialize(OutboxEvent outboxEvent) {
        try {
            return objectMapper.readValue(
                outboxEvent.getPayload(),
                WalletCredited.class
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                "Cannot deserialize outbox event: " + outboxEvent.getId(),
                exception
            );
        }
    }
}
