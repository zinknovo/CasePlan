package com.caseplan.adapter.in.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.caseplan.application.service.CasePlanService;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetOrderStatusHandlerTest {

    @Test
    public void handleRequest_missingId_returns400() {
        CasePlanService service = mock(CasePlanService.class);
        GetOrderStatusHandler handler = new GetOrderStatusHandler(service);

        APIGatewayProxyResponseEvent res = handler.handleRequest(new APIGatewayProxyRequestEvent(), null);
        assertEquals(Integer.valueOf(400), res.getStatusCode());
    }

    @Test
    public void handleRequest_notFound_returns404() {
        CasePlanService service = mock(CasePlanService.class);
        when(service.getStatus(9L)).thenReturn(null);
        GetOrderStatusHandler handler = new GetOrderStatusHandler(service);

        APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent();
        req.setPathParameters(Collections.singletonMap("id", "9"));
        APIGatewayProxyResponseEvent res = handler.handleRequest(req, null);

        assertEquals(Integer.valueOf(404), res.getStatusCode());
        assertTrue(res.getBody().contains("order not found"));
    }

    @Test
    public void handleRequest_successFromQueryParam_returns200() {
        CasePlanService service = mock(CasePlanService.class);
        Map<String, Object> body = new HashMap<>();
        body.put("status", "completed");
        body.put("content", "ok");
        when(service.getStatus(5L)).thenReturn(body);
        GetOrderStatusHandler handler = new GetOrderStatusHandler(service);

        APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent();
        req.setQueryStringParameters(Collections.singletonMap("id", "5"));
        APIGatewayProxyResponseEvent res = handler.handleRequest(req, null);

        assertEquals(Integer.valueOf(200), res.getStatusCode());
        assertTrue(res.getBody().contains("\"id\":5"));
        assertTrue(res.getBody().contains("\"status\":\"completed\""));
    }

    @Test
    public void handleRequest_serviceThrows_returns500() {
        CasePlanService service = mock(CasePlanService.class);
        when(service.getStatus(1L)).thenThrow(new RuntimeException("db down"));
        GetOrderStatusHandler handler = new GetOrderStatusHandler(service);

        APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent();
        req.setPathParameters(Collections.singletonMap("id", "1"));
        APIGatewayProxyResponseEvent res = handler.handleRequest(req, null);
        assertEquals(Integer.valueOf(500), res.getStatusCode());
    }

    @Test
    public void handleRequest_nonNumericId_returns400() {
        CasePlanService service = mock(CasePlanService.class);
        GetOrderStatusHandler handler = new GetOrderStatusHandler(service);

        APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent();
        req.setPathParameters(Collections.singletonMap("id", "abc"));
        APIGatewayProxyResponseEvent res = handler.handleRequest(req, null);
        assertEquals(Integer.valueOf(400), res.getStatusCode());
    }

    @Test
    public void handleRequest_nullEvent_returns400() {
        CasePlanService service = mock(CasePlanService.class);
        GetOrderStatusHandler handler = new GetOrderStatusHandler(service);
        APIGatewayProxyResponseEvent res = handler.handleRequest(null, null);
        assertEquals(Integer.valueOf(400), res.getStatusCode());
    }

    @Test
    public void handleRequest_nonNumericQueryId_returns400() {
        CasePlanService service = mock(CasePlanService.class);
        GetOrderStatusHandler handler = new GetOrderStatusHandler(service);
        APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent();
        req.setQueryStringParameters(Collections.singletonMap("id", "x"));
        APIGatewayProxyResponseEvent res = handler.handleRequest(req, null);
        assertEquals(Integer.valueOf(400), res.getStatusCode());
    }

    @Test
    public void handleRequest_pathIdNullFallsBackToQueryId() {
        CasePlanService service = mock(CasePlanService.class);
        when(service.getStatus(11L)).thenReturn(Collections.singletonMap("status", "pending"));
        GetOrderStatusHandler handler = new GetOrderStatusHandler(service);
        APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent();
        req.setPathParameters(Collections.singletonMap("id", null));
        req.setQueryStringParameters(Collections.singletonMap("id", "11"));
        APIGatewayProxyResponseEvent res = handler.handleRequest(req, null);
        assertEquals(Integer.valueOf(200), res.getStatusCode());
    }
}
