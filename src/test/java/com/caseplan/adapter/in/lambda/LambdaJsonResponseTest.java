package com.caseplan.adapter.in.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LambdaJsonResponseTest {

    @Test
    public void json_returnsExpectedResponse() {
        APIGatewayProxyResponseEvent response = LambdaJsonResponse.json(
                new ObjectMapper(),
                201,
                LambdaJsonResponse.mapOf("id", 1, "message", "ok")
        );

        assertEquals(Integer.valueOf(201), response.getStatusCode());
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        assertTrue(response.getBody().contains("\"id\":1"));
    }

    @Test
    public void mapOf_buildsMap() {
        Map<String, Object> map = LambdaJsonResponse.mapOf("a", 1, "b", "x");
        assertEquals(2, map.size());
        assertEquals(1, map.get("a"));
        assertEquals("x", map.get("b"));
    }
}
