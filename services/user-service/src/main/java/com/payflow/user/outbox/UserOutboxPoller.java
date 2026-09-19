package com.payflow.user.outbox;

import com.payflow.user.entity.OutboxEventStatus;
import com.payflow.user.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserOutboxPoller {

    private final OutboxEventRepository outboxEventRepository;
    private final UserOutboxEventPublishingService publishingService;

    @Value("${payflow.outbox.poller-enabled:true}")
    private boolean pollerEnabled;

    @Scheduled(fixedDelayString = "${payflow.outbox.poll-interval-ms:2000}")
    public void publishPendingEvents() {
        if (!pollerEnabled) {
            return;
        }
        outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING)
            .forEach(outboxEvent -> {
                try {
                    publishingService.publish(outboxEvent.getId());
                } catch (Exception exception) {
                    log.error(
                        "Failed to publish user outbox event, eventId={}",
                        outboxEvent.getId(),
                        exception
                    );
                }
            });
    }
}
