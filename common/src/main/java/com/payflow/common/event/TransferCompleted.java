package com.payflow.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferCompleted(
    UUID eventId,
    UUID transactionId,
    UUID senderUserId,
    UUID receiverUserId,
    BigDecimal amount,
    Instant occurredAt
) {
}
