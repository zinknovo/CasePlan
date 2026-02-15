package com.caseplan.adapter.in.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.caseplan.application.service.CasePlanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.HashMap;
import java.util.Map;

/**
 * GET /orders/{id} -> returns caseplan status and generated content/error.
 */
public class GetOrderStatusHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CasePlanService casePlanService;

    public GetOrderStatusHandler() {
        ConfigurableApplicationContext ctx = LambdaSpringContext.getContext();
        this.casePlanService = ctx.getBean(CasePlanService.class);
    }

    GetOrderStatusHandler(CasePlanService casePlanService) {
        this.casePlanService = casePlanService;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        long start = System.currentTimeMillis();
        int statusCode = 500;
        try {
            Long id = extractId(event);
            if (id == null) {
                statusCode = 400;
                return LambdaJsonResponse.json(MAPPER, statusCode, LambdaJsonResponse.mapOf("message", "id is required (path /orders/{id} or query ?id=)"));
            }

            Map<String, Object> status = casePlanService.getStatus(id);
            if (status == null) {
                statusCode = 404;
                return LambdaJsonResponse.json(MAPPER, statusCode, LambdaJsonResponse.mapOf("message", "order not found", "id", id));
            }

            Map<String, Object> body = new HashMap<>();
            body.put("id", id);
            body.putAll(status);
            statusCode = 200;
            return LambdaJsonResponse.json(MAPPER, statusCode, body);
        } catch (Exception e) {
            statusCode = 500;
            return LambdaJsonResponse.json(MAPPER, statusCode, LambdaJsonResponse.mapOf("message", "internal error", "error", e.getMessage()));
        } finally {
            long duration = System.currentTimeMillis() - start;
            String statusBucket = statusCode < 400 ? "2xx" : statusCode < 500 ? "4xx" : "5xx";
            CloudWatchEmf.record()
                    .dimension("handler", "GetOrderStatus")
                    .dimension("status", statusBucket)
                    .count("OrderStatusQueried", 1)
                    .millis("HandlerDuration", duration)
                    .emit();
        }
    }

    private Long extractId(APIGatewayProxyRequestEvent event) {
        if (event == null) {
            return null;
        }

        Map<String, String> pathParams = event.getPathParameters();
        if (pathParams != null && pathParams.get("id") != null) {
            return parseLong(pathParams.get("id"));
        }

        Map<String, String> queryParams = event.getQueryStringParameters();
        if (queryParams != null && queryParams.get("id") != null) {
            return parseLong(queryParams.get("id"));
        }

        return null;
    }

    private Long parseLong(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

}
