package com.caseplan.common.exception.response;

import com.caseplan.common.exception.BaseAppException;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class ErrorResponse {
    private String type;
    private String code;
    private String message;
    private Object detail;

    public ErrorResponse(BaseAppException ex) {
        this.type = ex.getType();
        this.code = ex.getCode();
        this.message = ex.getMessage();
        this.detail = ex.getDetail();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("code", code);
        map.put("message", message);
        if (detail != null) {
            map.put("detail", detail);
        }
        return map;
    }
}
