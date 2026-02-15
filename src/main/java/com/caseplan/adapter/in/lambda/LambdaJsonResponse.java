package com.caseplan.adapter.in.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

final class LambdaJsonResponse {

    private LambdaJsonResponse() {
    }

    static APIGatewayProxyResponseEvent json(ObjectMapper mapper, int statusCode, Map<String, Object> body) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(statusCode);
        response.setHeaders(stringMapOf("Content-Type", "application/json"));
        try {
            response.setBody(mapper.writeValueAsString(body));
        } catch (JsonProcessingException e) {
            response.setStatusCode(500);
            response.setBody("{\"message\":\"serialization error\"}");
        }
        return response;
    }

    static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    private static Map<String, String> stringMapOf(String... kv) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }
}
