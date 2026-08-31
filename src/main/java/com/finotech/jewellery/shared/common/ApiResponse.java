package com.finotech.jewellery.shared.common;

import java.time.Instant;

/**
 * Standard success envelope for every REST endpoint.
 */
public record ApiResponse<T>(boolean success, T data, String message, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, Instant.now());
    }
}
