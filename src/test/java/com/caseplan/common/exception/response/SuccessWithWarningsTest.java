package com.caseplan.common.exception.response;

import com.caseplan.common.exception.WarningException;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Collections;

import java.util.*;

import static org.junit.Assert.*;

public class SuccessWithWarningsTest {

    @Test
    public void toMap_containsDataAndWarnings() {
        Map<String, Object> detail = new HashMap<>();
        detail.put("key", "val");
        WarningException w1 = new WarningException("CODE1", "msg1", detail);
        WarningException w2 = new WarningException("CODE2", "msg2", null);

        SuccessWithWarnings<String> result = new SuccessWithWarnings<>("testData", Arrays.asList(w1, w2));
        Map<String, Object> map = result.toMap();

        assertEquals("testData", map.get("data"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> warnings = (List<Map<String, Object>>) map.get("warnings");
        assertEquals(2, warnings.size());

        assertEquals("warning", warnings.get(0).get("type"));
        assertEquals("CODE1", warnings.get(0).get("code"));
        assertEquals("msg1", warnings.get(0).get("message"));
        assertNotNull(warnings.get(0).get("detail"));

        assertEquals("CODE2", warnings.get(1).get("code"));
        assertFalse(warnings.get(1).containsKey("detail"));
    }

    @Test
    public void getters_returnCorrectValues() {
        WarningException w = new WarningException("CODE", "msg", null);
        SuccessWithWarnings<String> result = new SuccessWithWarnings<>("data", Collections.singletonList(w));

        assertEquals("data", result.getData());
        assertNotNull(result.getWarnings());
        assertEquals(1, result.getWarnings().size());
    }

    @Test
    public void setters_work() {
        WarningException w = new WarningException("CODE", "msg", null);
        SuccessWithWarnings<String> result = new SuccessWithWarnings<>("data", Collections.singletonList(w));

        result.setData("newData");
        result.setWarnings(new ArrayList<>());

        assertEquals("newData", result.getData());
        assertEquals(0, result.getWarnings().size());
    }
}
