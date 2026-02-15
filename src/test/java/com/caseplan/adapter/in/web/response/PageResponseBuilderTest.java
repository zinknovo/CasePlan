package com.caseplan.adapter.in.web.response;

import org.junit.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PageResponseBuilderTest {

    @Test
    @SuppressWarnings("unchecked")
    public void from_buildsStandardPagedMap() {
        Page<String> page = new PageImpl<>(Arrays.asList("a", "b"), PageRequest.of(0, 20), 35);

        Map<String, Object> result = PageResponseBuilder.from(page, 0, 0);

        assertEquals(35L, result.get("count"));
        assertEquals(1, result.get("page"));
        assertEquals(1, result.get("page_size"));
        assertTrue(result.get("results") instanceof java.util.List);
        assertEquals(2, ((java.util.List<String>) result.get("results")).size());
    }
}
