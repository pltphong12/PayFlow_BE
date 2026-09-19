package com.payflow.user.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.event.UserRegistered;
import com.payflow.user.entity.OutboxEvent;
import com.payflow.user.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserRegistrationOutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void enqueueUserRegistered(UserRegistered event) {
        outboxEventRepository.save(new OutboxEvent(
            event.userId(),
            UserRegistered.class.getSimpleName(),
            serialize(event)
        ));
    }

    private String serialize(UserRegistered event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                "Cannot serialize UserRegistered event",
                exception
            );
        }
    }
}
