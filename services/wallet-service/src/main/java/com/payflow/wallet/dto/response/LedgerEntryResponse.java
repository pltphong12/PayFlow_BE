package com.payflow.wallet.dto.response;

import com.payflow.wallet.entity.LedgerEntryType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class LedgerEntryResponse {
    private UUID id;
    private UUID transactionId;
    private LedgerEntryType entryType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private Instant createdAt;
}
