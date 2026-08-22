package com.caseplan.common.exception;

import org.springframework.http.HttpStatus;

public class WarningException extends BaseAppException {

    private static final long serialVersionUID = 1L;

    public WarningException(String code, String message, Object detail) {
        super("warning", code, message, detail, HttpStatus.OK);
    }

    public WarningException(String message, Object detail) {
        this("WARNING", message, detail);
    }
}
