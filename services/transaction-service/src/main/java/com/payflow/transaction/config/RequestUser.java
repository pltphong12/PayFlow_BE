package com.payflow.transaction.config;

import com.payflow.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public final class RequestUser {
    private static final String HEADER_USER_ID = "X-User-Id";

    private RequestUser() {}

    public static UUID requireUserId(HttpServletRequest request) {
        String rawUserId = request.getHeader(HEADER_USER_ID);
        if (rawUserId == null || rawUserId.isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Missing X-User-Id");
        }
        try {
            return UUID.fromString(rawUserId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid X-User-Id");
        }
    }
}
