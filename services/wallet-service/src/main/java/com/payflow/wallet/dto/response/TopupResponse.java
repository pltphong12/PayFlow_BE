package com.payflow.wallet.dto.response;

import com.payflow.wallet.entity.TopupStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record TopupResponse (
    UUID id,
    UUID userId,
    BigDecimal amount,
    TopupStatus status,
    Instant createdAt
){
}