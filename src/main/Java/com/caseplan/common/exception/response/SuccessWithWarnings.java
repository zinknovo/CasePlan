package com.caseplan.common.exception.response;

import com.caseplan.common.exception.WarningException;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class SuccessWithWarnings<T> {
    private T data;
    private List<Map<String, Object>> warnings;

    public SuccessWithWarnings(T data, List<WarningException> warningExceptions) {
        this.data = data;
        this.warnings = new ArrayList<>();
        for (WarningException warning : warningExceptions) {
            Map<String, Object> warningMap = new HashMap<>();
            warningMap.put("type", warning.getType());
            warningMap.put("code", warning.getCode());
            warningMap.put("message", warning.getMessage());
            if (warning.getDetail() != null) {
                warningMap.put("detail", warning.getDetail());
            }
            warnings.add(warningMap);
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("data", data);
        map.put("warnings", warnings);
        return map;
    }
}
