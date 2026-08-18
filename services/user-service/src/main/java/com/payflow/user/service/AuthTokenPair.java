package com.payflow.user.service;

public record AuthTokenPair(
        String accessToken,
        String refreshToken,
        long accessExpiresIn,
        long refreshExpiresIn
) {
}
