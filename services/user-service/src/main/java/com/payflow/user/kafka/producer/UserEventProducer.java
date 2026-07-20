package com.payflow.user.kafka.producer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.payflow.common.event.UserRegistered;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventProducer {
    private final KafkaTemplate<String, UserRegistered> kafkaTemplate;
    
    @Value("${payflow.kafka.topics.user-events}")
    private String userEventsTopic;

    public void publishUserRegistered(UserRegistered event) {
        kafkaTemplate.send(userEventsTopic, event.userId().toString(), event);
        log.info("Published UserRegistered for userId={}", event.userId());
    }
}
