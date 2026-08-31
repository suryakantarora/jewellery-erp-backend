package com.finotech.jewellery.shared.exception;

public class ConflictException extends BusinessException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
