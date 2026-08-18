package com.payflow.transaction.outbox;

import com.payflow.transaction.entity.OutboxEvent;
import com.payflow.transaction.entity.OutboxEventStatus;
import com.payflow.transaction.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    name = "payflow.outbox.poller-enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class TransactionOutboxPoller {

    private final TransactionOutboxPublishingService publishingService;
    private final OutboxEventRepository outboxEventRepository;

    @Scheduled(fixedRateString = "${payflow.outbox.poll-interval-ms}")
    public void publishPendingEvents() {
        var eventIds = outboxEventRepository
            .findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING)
            .stream()
            .map(event -> event.getId())
            .toList();
        for (UUID eventId : eventIds) {
            try {
                publishingService.publish(eventId);
            } catch (RuntimeException exception) {
                log.error(
                    "Failed to publish transaction outbox event, eventId={}",
                    eventId,
                    exception
                );
            }
        }
    }
}
