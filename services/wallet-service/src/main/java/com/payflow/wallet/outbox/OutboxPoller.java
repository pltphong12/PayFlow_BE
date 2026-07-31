package com.payflow.wallet.outbox;

import com.payflow.wallet.entity.OutboxEventStatus;
import com.payflow.wallet.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(
        name = "payflow.outbox.poller-enabled",
        havingValue = "true",
        matchIfMissing = true
)
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private final OutboxEventPublishingService publishingService;
    private final OutboxEventRepository outboxEventRepository;

    @Scheduled(fixedDelayString = "${payflow.outbox.poll-interval-ms:2000}")
    public void publishingPendingEvents() {
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
                    "Failed to publish outbox event, eventId={}",
                    eventId,
                    exception
                );
            }
        }
    }
}
