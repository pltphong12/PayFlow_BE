package com.payflow.wallet.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateTopupRequest(
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Amount must be greater than or equal to 1")
    BigDecimal amount
) {
}
