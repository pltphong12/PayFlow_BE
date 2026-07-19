package com.payflow.common.dto;

import org.springframework.http.HttpStatus;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        int code,
        String message,
        T data,
        Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                null,
                data,
                Instant.now());
    }

    public static <T> ApiResponse<T> ok(int code, String message, T data) {
        return new ApiResponse<>(true, code, message, data, Instant.now());
    }

    public static <T> ApiResponse<T> created(T data) {
        return ok(HttpStatus.CREATED.value(), null, data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(false, code, message, null, Instant.now());
    }
}
