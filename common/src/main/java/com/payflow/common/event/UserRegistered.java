package com.payflow.common.event;

import java.time.Instant;
import java.util.UUID;

public record UserRegistered(
        UUID eventId,
        UUID userId,
        String email,
        String fullName,
        Instant timestamp) {
}
