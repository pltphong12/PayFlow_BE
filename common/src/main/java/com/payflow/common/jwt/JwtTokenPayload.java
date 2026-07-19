package com.payflow.common.jwt;

import java.util.UUID;

public record JwtTokenPayload(UUID userId, String role) {
}
