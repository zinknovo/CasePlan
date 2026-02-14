package com.caseplan.common.exception.response;

import com.caseplan.common.exception.BlockException;
import com.caseplan.common.exception.ValidationException;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ErrorResponseTest {

    @Test
    public void toMap_withDetail_includesDetail() {
        Map<String, Object> detail = new HashMap<>();
        detail.put("field", "value");
        BlockException ex = new BlockException("CODE", "message", detail);

        ErrorResponse response = new ErrorResponse(ex);
        Map<String, Object> map = response.toMap();

        assertEquals("block_error", map.get("type"));
        assertEquals("CODE", map.get("code"));
        assertEquals("message", map.get("message"));
        assertEquals(detail, map.get("detail"));
    }

    @Test
    public void toMap_nullDetail_excludesDetail() {
        ValidationException ex = new ValidationException("CODE", "message", null);

        ErrorResponse response = new ErrorResponse(ex);
        Map<String, Object> map = response.toMap();

        assertFalse(map.containsKey("detail"));
    }

    @Test
    public void getters_returnCorrectValues() {
        BlockException ex = new BlockException("CODE", "msg", "detail");
        ErrorResponse response = new ErrorResponse(ex);

        assertEquals("block_error", response.getType());
        assertEquals("CODE", response.getCode());
        assertEquals("msg", response.getMessage());
        assertEquals("detail", response.getDetail());
    }

    @Test
    public void setters_work() {
        BlockException ex = new BlockException("CODE", "msg", null);
        ErrorResponse response = new ErrorResponse(ex);

        response.setType("new_type");
        response.setCode("NEW_CODE");
        response.setMessage("new msg");
        response.setDetail("new detail");

        assertEquals("new_type", response.getType());
        assertEquals("NEW_CODE", response.getCode());
        assertEquals("new msg", response.getMessage());
        assertEquals("new detail", response.getDetail());
    }
}
