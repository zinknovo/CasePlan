package com.caseplan.common.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends BaseAppException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String code, String message, Object detail) {
        super("validation_error", code, message, detail, HttpStatus.BAD_REQUEST);
    }

    public ValidationException(String message, Object detail) {
        this("VALIDATION_ERROR", message, detail);
    }
}
