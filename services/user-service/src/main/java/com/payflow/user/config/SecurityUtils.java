package com.payflow.user.config;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.payflow.common.exception.BusinessException;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UUID userId)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return userId;
    }
}
