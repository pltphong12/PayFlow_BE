package com.payflow.wallet.event;

import java.util.UUID;

public record TopupCreated(
    UUID topupRequestId
) {
}
