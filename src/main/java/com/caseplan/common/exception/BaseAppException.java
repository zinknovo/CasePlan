package com.caseplan.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class BaseAppException extends RuntimeException {
    private final String type;
    private final String code;
    private final String message;
    private final Object detail;
    private final HttpStatus httpStatus;

    protected BaseAppException(String type, String code, String message, Object detail, HttpStatus httpStatus) {
        super(message);
        this.type = type;
        this.code = code;
        this.message = message;
        this.detail = detail;
        this.httpStatus = httpStatus;
    }
}
