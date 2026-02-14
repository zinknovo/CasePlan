package com.caseplan.common.exception;

import org.springframework.http.HttpStatus;

public class BlockException extends BaseAppException {
    public BlockException(String code, String message, Object detail) {
        super("block_error", code, message, detail, HttpStatus.CONFLICT);
    }

    public BlockException(String message, Object detail) {
        this("BLOCK_ERROR", message, detail);
    }
}
