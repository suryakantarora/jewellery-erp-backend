package com.finotech.jewellery.shared.exception;

public class ValidationException extends BusinessException {

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
    }
}
