package com.payflow.wallet.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletMutationResponse(
    UUID walletId,
    BigDecimal balance,
    Integer version
) {
}
