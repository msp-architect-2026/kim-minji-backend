package com.minjikim.codecobainbackend.exception;

import lombok.Getter;

@Getter
public class WaferException extends RuntimeException {

    private final ErrorCode errorCode;

    public WaferException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), null);
    }

    public WaferException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public WaferException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, errorCode.getMessage(), cause);
    }

    public WaferException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
