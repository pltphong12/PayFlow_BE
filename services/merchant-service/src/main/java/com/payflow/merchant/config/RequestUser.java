package com.payflow.merchant.config;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.payflow.common.exception.BusinessException;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestUser {
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    private RequestUser() {
    }

    public static UUID requireUserId(HttpServletRequest request) {
        String rawUserId = request.getHeader(HEADER_USER_ID);
        if (rawUserId == null || rawUserId.isBlank()) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "Missing X-User-Id");
        }
        try {
            return UUID.fromString(rawUserId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid X-User-Id");
        }
    }

    public static void requireRole(
            HttpServletRequest request,
            String expectedRole) {
        String actualRole = request.getHeader(HEADER_USER_ROLE);
        if (actualRole == null || actualRole.isBlank()) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "Missing X-User-Role");
        }
        if (!expectedRole.equals(actualRole)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }
}
