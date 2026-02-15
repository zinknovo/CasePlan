package com.caseplan.adapter.in.web.response;

import org.springframework.data.domain.Page;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PageResponseBuilder {

    private PageResponseBuilder() {
    }

    public static Map<String, Object> from(Page<?> page, int requestedPage, int requestedPageSize) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("count", page.getTotalElements());
        response.put("page", Math.max(requestedPage, 1));
        response.put("page_size", Math.max(requestedPageSize, 1));
        response.put("results", page.getContent());
        return response;
    }
}
