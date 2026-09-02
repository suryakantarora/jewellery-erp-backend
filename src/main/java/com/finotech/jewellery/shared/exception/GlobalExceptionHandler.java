package com.finotech.jewellery.shared.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.finotech.jewellery.shared.config.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates every exception into the consistent {@link ApiError} contract.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.debug("Business exception on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(ex.getErrorCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleInvalidBody(MethodArgumentNotValidException ex,
                                                      HttpServletRequest request) {
        List<ApiError.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_FAILED, "Request validation failed", request, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        List<ApiError.FieldError> fields = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldError(String.valueOf(v.getPropertyPath()), v.getMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_FAILED, "Request validation failed", request, fields);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        String message = "Invalid value for parameter '" + ex.getName() + "'";
        return build(ErrorCode.VALIDATION_FAILED, message, request, null);
    }

    /**
     * A missing required query parameter is a caller mistake, not a server
     * fault. Without this it fell through to the catch-all and returned 500,
     * which told a client to retry something that could never succeed.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex,
                                                           HttpServletRequest request) {
        List<ApiError.FieldError> fields =
                List.of(new ApiError.FieldError(ex.getParameterName(), "Required parameter is missing"));
        return build(ErrorCode.VALIDATION_FAILED,
                "Required parameter '" + ex.getParameterName() + "' is missing", request, fields);
    }

    /**
     * An unreadable request body — malformed JSON, or a value that is not one
     * of an enum's constants — is a caller mistake, so it must not surface as
     * a 500.
     *
     * <p>Where Jackson identifies the offending field, it is reported as a
     * field error along with the values that would have been accepted. A client
     * developer sending {@code "type": "NOT_A_TYPE"} otherwise has nothing to
     * go on but "an unexpected error occurred".
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                         HttpServletRequest request) {
        if (ex.getCause() instanceof InvalidFormatException invalid) {
            String field = invalid.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("."));
            String message = describeRejectedValue(invalid);
            return build(ErrorCode.VALIDATION_FAILED, "Request validation failed", request,
                    List.of(new ApiError.FieldError(field.isEmpty() ? "body" : field, message)));
        }
        log.debug("Unreadable request body on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.VALIDATION_FAILED, "Request body could not be read", request, null);
    }

    /**
     * A URL that matches no controller.
     *
     * <p>{@code NoHandlerFoundException} is handled below but never thrown
     * unless Spring is explicitly configured to; since Spring 6.1 an unmatched
     * path falls through to the static resource handler and raises this
     * instead. Without it, a client typo was indistinguishable from an outage.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex,
                                                     HttpServletRequest request) {
        return build(ErrorCode.NOT_FOUND, "Endpoint not found", request, null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                         HttpServletRequest request) {
        return build(ErrorCode.CONCURRENT_MODIFICATION,
                "The record was modified by another user. Reload and retry.", request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex,
                                                    HttpServletRequest request) {
        log.warn("Data integrity violation on {}", request.getRequestURI(), ex);
        return build(ErrorCode.CONFLICT, "Operation violates a data constraint", request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex,
                                                       HttpServletRequest request) {
        return build(ErrorCode.FORBIDDEN, "You do not have permission to perform this action",
                request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex,
                                                         HttpServletRequest request) {
        return build(ErrorCode.UNAUTHORIZED, "Authentication required", request, null);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> handleNoHandler(NoHandlerFoundException ex,
                                                    HttpServletRequest request) {
        return build(ErrorCode.NOT_FOUND, "Endpoint not found", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request, null);
    }

    /** Names the accepted values for an enum, and stays generic otherwise. */
    private static String describeRejectedValue(InvalidFormatException ex) {
        Class<?> target = ex.getTargetType();
        if (target != null && target.isEnum()) {
            return "Must be one of: " + Arrays.stream(target.getEnumConstants())
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
        }
        return "Invalid value for this field";
    }

    private ResponseEntity<ApiError> build(ErrorCode code, String message, HttpServletRequest request,
                                           List<ApiError.FieldError> fields) {
        ApiError body = ApiError.of(code, message, request.getRequestURI(),
                CorrelationIdFilter.current(), fields);
        return ResponseEntity.status(code.status()).body(body);
    }
}
