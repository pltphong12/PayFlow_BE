package com.payflow.transaction.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletMutationRequest(
    UUID transactionId,
    BigDecimal amount
) {
}
