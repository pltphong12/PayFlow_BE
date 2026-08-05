package com.payflow.transaction.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletLookupResponse(
    UUID id,
    UUID userId,
    BigDecimal balance,
    String currency,
    String status
) {
}
