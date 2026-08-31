package com.finotech.jewellery.shared.exception;

import lombok.Getter;

/**
 * Root of the domain exception hierarchy. Every module throws these rather than
 * leaking persistence or framework exceptions to the API layer.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(String message) {
        this(ErrorCode.BUSINESS_RULE_VIOLATED, message);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
