package com.finotech.jewellery.shared.exception;

import java.time.Instant;
import java.util.List;

/**
 * Consistent error payload for every failed request.
 */
public record ApiError(boolean success,
                       String code,
                       String message,
                       String path,
                       String correlationId,
                       List<FieldError> fieldErrors,
                       Instant timestamp) {

    public record FieldError(String field, String message) {
    }

    public static ApiError of(ErrorCode code, String message, String path, String correlationId) {
        return new ApiError(false, code.name(), message, path, correlationId, null, Instant.now());
    }

    public static ApiError of(ErrorCode code, String message, String path, String correlationId,
                              List<FieldError> fieldErrors) {
        return new ApiError(false, code.name(), message, path, correlationId, fieldErrors, Instant.now());
    }
}
