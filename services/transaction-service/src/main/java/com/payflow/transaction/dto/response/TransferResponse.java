package com.payflow.transaction.dto.response;

import com.payflow.transaction.entity.TransactionStatus;
import com.payflow.transaction.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
    UUID id,
    TransactionType type,
    UUID senderUserId,
    UUID receiverUserId,
    BigDecimal amount,
    TransactionStatus status,
    Instant createdAt
) {
}
