package com.payflow.common.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.jwt")
public record JwtProperties(
        String secret,
        int accessTokenExpirationMinutes,
        int refreshTokenExpirationMinutes
) {
}
