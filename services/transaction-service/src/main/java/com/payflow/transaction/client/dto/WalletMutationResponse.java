package com.payflow.transaction.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletMutationResponse(
    UUID walletId,
    BigDecimal balance,
    Integer version
) {
}
