package com.payflow.common.jwt;

import com.payflow.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void should_throw_unauthorized_when_access_token_is_invalid() {
        assertThatThrownBy(() -> jwtUtil.parseAccessToken("invalid-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid access token");
    }

    @Test
    void should_generate_unique_refresh_tokens() {
        String first = jwtUtil.generateRefreshToken();
        String second = jwtUtil.generateRefreshToken();

        assertThat(first).isNotBlank();
        assertThat(second).isNotBlank();
        assertThat(first).isNotEqualTo(second);
    }
}
