package com.payflow.common.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "test-secret-key-at-least-256-bits-long-for-hs256-algorithm",
                15,
                10080
        );
        jwtUtil = new JwtUtil(properties);
    }

    @Test
    void should_parse_access_token_with_same_user_id_and_role() {
        UUID userId = UUID.randomUUID();

        String token = jwtUtil.generateAccessToken(userId, "USER");
        JwtTokenPayload payload = jwtUtil.parseAccessToken(token);

        assertThat(payload.userId()).isEqualTo(userId);
        assertThat(payload.role()).isEqualTo("USER");
    }
}
