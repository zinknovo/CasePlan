package com.caseplan.adapter.in.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.caseplan.common.exception.BaseAppException;
import com.caseplan.domain.model.CasePlan;
import com.caseplan.adapter.in.web.controller.CasePlanController;
import com.caseplan.adapter.in.web.dto.CreateCasePlanRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * POST /orders -> validate + create caseplan + send SQS message.
 */
public class CreateOrderHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CasePlanController casePlanController;
    private final Validator validator;

    public CreateOrderHandler() {
        this.casePlanController = LambdaSpringContext.getContext().getBean(CasePlanController.class);
        this.validator = LambdaSpringContext.getContext().getBean(Validator.class);
    }

    CreateOrderHandler(CasePlanController casePlanController, Validator validator) {
        this.casePlanController = casePlanController;
        this.validator = validator;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        long start = System.currentTimeMillis();
        int statusCode = 500;
        try {
            CreateCasePlanRequest req = parseRequest(event);

            Set<ConstraintViolation<CreateCasePlanRequest>> violations = validator.validate(req);
            if (!violations.isEmpty()) {
                List<String> parts = new ArrayList<>();
                for (ConstraintViolation<CreateCasePlanRequest> violation : violations) {
                    parts.add(violation.getPropertyPath() + ": " + violation.getMessage());
                }
                Collections.sort(parts);
                String message = String.join("; ", parts);
                statusCode = 400;
                return LambdaJsonResponse.json(MAPPER, statusCode, LambdaJsonResponse.mapOf("message", "validation failed", "detail", message));
            }

            Object body = casePlanController.create(req).getBody();
            Long planId = extractPlanId(body);
            if (planId == null) {
                statusCode = 500;
                return LambdaJsonResponse.json(MAPPER, statusCode, LambdaJsonResponse.mapOf("message", "created but cannot extract plan id"));
            }

            statusCode = 201;
            return LambdaJsonResponse.json(MAPPER, statusCode, LambdaJsonResponse.mapOf(
                    "id", planId,
                    "status", "pending",
                    "message", "queued"
            ));
        } catch (BaseAppException e) {
            statusCode = e.getHttpStatus().value();
            return LambdaJsonResponse.json(MAPPER, statusCode, LambdaJsonResponse.mapOf(
                    "type", e.getType(),
                    "code", e.getCode(),
                    "message", e.getMessage(),
                    "detail", e.getDetail()
            ));
        } catch (IllegalArgumentException e) {
            statusCode = 400;
            return LambdaJsonResponse.json(MAPPER, statusCode, LambdaJsonResponse.mapOf("message", e.getMessage()));
        } catch (Exception e) {
            statusCode = 500;
            return LambdaJsonResponse.json(MAPPER, statusCode, LambdaJsonResponse.mapOf("message", "internal error", "error", e.getMessage()));
        } finally {
            long duration = System.currentTimeMillis() - start;
            String statusBucket = statusCode < 400 ? "2xx" : statusCode < 500 ? "4xx" : "5xx";
            CloudWatchEmf.record()
                    .dimension("handler", "CreateOrder")
                    .dimension("status", statusBucket)
                    .count("OrderCreated", 1)
                    .millis("HandlerDuration", duration)
                    .emit();
        }
    }

    private CreateCasePlanRequest parseRequest(APIGatewayProxyRequestEvent event) throws JsonProcessingException {
        if (event == null || event.getBody() == null || event.getBody().trim().isEmpty()) {
            throw new IllegalArgumentException("request body is required");
        }
        return MAPPER.readValue(event.getBody(), CreateCasePlanRequest.class);
    }

    private Long extractPlanId(Object body) {
        if (body == null) {
            return null;
        }
        if (body instanceof CasePlan) {
            return ((CasePlan) body).getId();
        }

        Map<String, Object> map = MAPPER.convertValue(body, new TypeReference<Map<String, Object>>() {});
        if (map.get("id") != null) {
            return toLong(map.get("id"));
        }
        Object data = map.get("data");
        if (data != null) {
            Map<String, Object> dataMap = MAPPER.convertValue(data, new TypeReference<Map<String, Object>>() {});
            return toLong(dataMap.get("id"));
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

}
