package com.payflow.wallet.dto.response;

import com.payflow.wallet.entity.WalletStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class WalletResponse {
    private UUID id;
    private UUID userId;
    private BigDecimal balance;
    private String currency;
    private WalletStatus status;
    private Instant createdAt;
}
