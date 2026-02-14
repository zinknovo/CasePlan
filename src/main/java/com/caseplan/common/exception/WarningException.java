package com.caseplan.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class WarningException extends BaseAppException {
    public WarningException(String code, String message, Object detail) {
        super("warning", code, message, detail, HttpStatus.OK);
    }

    public WarningException(String message, Object detail) {
        this("WARNING", message, detail);
    }
}
