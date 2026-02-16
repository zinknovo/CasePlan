package com.caseplan.common.exception.handler;

import com.caseplan.common.exception.BlockException;
import com.caseplan.common.exception.ValidationException;
import com.caseplan.common.exception.WarningException;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Before
    public void setup() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    public void handleBlockException_returns409() {
        Map<String, Object> detail = new HashMap<>();
        detail.put("key", "value");
        BlockException ex = new BlockException("TEST_CODE", "Test block message", detail);

        ResponseEntity<Map<String, Object>> response = handler.handleBlockException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("block_error", response.getBody().get("type"));
        assertEquals("TEST_CODE", response.getBody().get("code"));
        assertEquals("Test block message", response.getBody().get("message"));
        assertNotNull(response.getBody().get("detail"));
    }

    @Test
    public void handleBlockException_noDetail_noDetailInResponse() {
        BlockException ex = new BlockException("TEST_CODE", "msg", null);

        ResponseEntity<Map<String, Object>> response = handler.handleBlockException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertFalse(response.getBody().containsKey("detail"));
    }

    @Test
    public void handleValidationException_returns400() {
        ValidationException ex = new ValidationException("VAL_CODE", "Validation failed", null);

        ResponseEntity<Map<String, Object>> response = handler.handleValidationException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("validation_error", response.getBody().get("type"));
        assertEquals("VAL_CODE", response.getBody().get("code"));
    }

    @Test
    public void handleWarningException_returns200() {
        Map<String, Object> detail = new HashMap<>();
        detail.put("info", "some info");
        WarningException ex = new WarningException("WARN_CODE", "Warning message", detail);

        ResponseEntity<Map<String, Object>> response = handler.handleWarningException(ex);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("warning", response.getBody().get("type"));
        assertEquals("WARN_CODE", response.getBody().get("code"));
        assertEquals("Warning message", response.getBody().get("message"));
        assertNotNull(response.getBody().get("detail"));
    }

    @Test
    public void handleWarningException_noDetail_noDetailInResponse() {
        WarningException ex = new WarningException("WARN_CODE", "msg", null);

        ResponseEntity<Map<String, Object>> response = handler.handleWarningException(ex);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(response.getBody().containsKey("detail"));
    }

    @Test
    public void handleBlockException_defaultCode() {
        BlockException ex = new BlockException("msg", null);

        ResponseEntity<Map<String, Object>> response = handler.handleBlockException(ex);

        assertEquals("BLOCK_ERROR", response.getBody().get("code"));
    }

    @Test
    public void handleWarningException_defaultCode() {
        WarningException ex = new WarningException("msg", null);

        ResponseEntity<Map<String, Object>> response = handler.handleWarningException(ex);

        assertEquals("WARNING", response.getBody().get("code"));
    }

    @Test
    public void handleValidationException_defaultCode() {
        ValidationException ex = new ValidationException("msg", null);

        ResponseEntity<Map<String, Object>> response = handler.handleValidationException(ex);

        assertEquals("VALIDATION_ERROR", response.getBody().get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void handleRequestValidationFailure_returns400WithFieldErrors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "clientFirstName", "Client first name is required"));
        bindingResult.addError(new FieldError("request", "docketNumber", "Docket number must be 6 digits"));

        MethodParameter param = new MethodParameter(
                this.getClass().getDeclaredMethod("handleRequestValidationFailure_returns400WithFieldErrors"), -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleRequestValidationFailure(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("validation_error", response.getBody().get("type"));
        assertEquals("VALIDATION_ERROR", response.getBody().get("code"));
        assertEquals("Validation failed", response.getBody().get("message"));

        List<Map<String, String>> fieldErrors = (List<Map<String, String>>) response.getBody().get("detail");
        assertEquals(2, fieldErrors.size());
        assertEquals("clientFirstName", fieldErrors.get(0).get("field"));
        assertEquals("docketNumber", fieldErrors.get(1).get("field"));
    }
}
