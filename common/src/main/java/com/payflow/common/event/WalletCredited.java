package com.payflow.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletCredited(
    UUID eventId,
    UUID walletId,
    UUID userId,
    UUID topupRequestId,
    BigDecimal amount,
    Instant occurredAt
) {
}
