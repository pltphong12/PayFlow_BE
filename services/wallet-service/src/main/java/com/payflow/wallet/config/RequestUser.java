package com.payflow.wallet.config;

import com.payflow.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public final class RequestUser {
    public static final String HEADER_USER_ID = "X-User-Id";

    private RequestUser() {}

    public static UUID requireUserId(HttpServletRequest request) {
        String raw = request.getHeader(HEADER_USER_ID);
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED,"Missing X-User-Id");
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Missing X-User-Id");
        }
    }
}
