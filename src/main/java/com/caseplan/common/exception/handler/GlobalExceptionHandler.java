package com.caseplan.common.exception.handler;

import com.caseplan.common.exception.BlockException;
import com.caseplan.common.exception.ValidationException;
import com.caseplan.common.exception.WarningException;
import com.caseplan.common.exception.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BlockException.class)
    public ResponseEntity<Map<String, Object>> handleBlockException(BlockException ex) {
        ErrorResponse errorResponse = new ErrorResponse(ex);
        return ResponseEntity.status(Objects.requireNonNull(ex.getHttpStatus())).body(errorResponse.toMap());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(ValidationException ex) {
        ErrorResponse errorResponse = new ErrorResponse(ex);
        return ResponseEntity.status(Objects.requireNonNull(ex.getHttpStatus())).body(errorResponse.toMap());
    }

    @ExceptionHandler(WarningException.class)
    public ResponseEntity<Map<String, Object>> handleWarningException(WarningException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("type", ex.getType());
        response.put("code", ex.getCode());
        response.put("message", ex.getMessage());
        if (ex.getDetail() != null) {
            response.put("detail", ex.getDetail());
        }
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /** Handles failed request body validation (@Valid on DTO). Spring throws this before controller runs. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleRequestValidationFailure(MethodArgumentNotValidException ex) {
        List<Map<String, String>> fieldErrors = new ArrayList<>();

        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            Map<String, String> fieldError = new HashMap<>();
            fieldError.put("field", error.getField());
            fieldError.put("message", error.getDefaultMessage());
            fieldErrors.add(fieldError);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("type", "validation_error");
        response.put("code", "VALIDATION_ERROR");
        response.put("message", "Validation failed");
        response.put("detail", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
