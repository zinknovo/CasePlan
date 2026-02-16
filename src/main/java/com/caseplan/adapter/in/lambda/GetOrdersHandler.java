package com.caseplan.adapter.in.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.caseplan.application.service.CasePlanService;
import com.caseplan.domain.model.Attorney;
import com.caseplan.domain.model.CaseInfo;
import com.caseplan.domain.model.CasePlan;
import com.caseplan.domain.model.Client;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /orders -> returns latest case plans list for frontend polling.
 */
public class GetOrdersHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CasePlanService casePlanService;

    public GetOrdersHandler() {
        ConfigurableApplicationContext ctx = LambdaSpringContext.getContext();
        this.casePlanService = ctx.getBean(CasePlanService.class);
    }

    GetOrdersHandler(CasePlanService casePlanService) {
        this.casePlanService = casePlanService;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        long start = System.currentTimeMillis();
        int statusCode = 500;
        try {
            List<CasePlan> casePlans = casePlanService.listAll();
            List<Map<String, Object>> items = new ArrayList<>();
            for (CasePlan plan : casePlans) {
                items.add(toItem(plan));
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("count", items.size());
            body.put("items", items);

            statusCode = 200;
            return LambdaJsonResponse.json(MAPPER, statusCode, body);
        } catch (Exception e) {
            statusCode = 500;
            return LambdaJsonResponse.json(MAPPER, statusCode,
                    LambdaJsonResponse.mapOf("message", "internal error", "error", e.getMessage()));
        } finally {
            long duration = System.currentTimeMillis() - start;
            String statusBucket = statusCode < 400 ? "2xx" : statusCode < 500 ? "4xx" : "5xx";
            CloudWatchEmf.record()
                    .dimension("handler", "GetOrders")
                    .dimension("status", statusBucket)
                    .count("OrdersListed", 1)
                    .millis("HandlerDuration", duration)
                    .emit();
        }
    }

    private Map<String, Object> toItem(CasePlan plan) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", plan.getId());
        item.put("status", plan.getStatus());
        item.put("createdAt", plan.getCreatedAt() == null ? null : plan.getCreatedAt().toString());

        CaseInfo info = plan.getCaseInfo();
        if (info != null) {
            Map<String, Object> caseInfo = new LinkedHashMap<>();
            caseInfo.put("caseNumber", info.getCaseNumber());
            caseInfo.put("primaryCauseOfAction", info.getPrimaryCauseOfAction());
            caseInfo.put("remedySought", info.getRemedySought());

            Client client = info.getClient();
            if (client != null) {
                Map<String, Object> clientMap = new LinkedHashMap<>();
                clientMap.put("firstName", client.getFirstName());
                clientMap.put("lastName", client.getLastName());
                caseInfo.put("client", clientMap);
                item.put("clientName", safe(client.getFirstName()) + " " + safe(client.getLastName()));
            }

            Attorney attorney = info.getAttorney();
            if (attorney != null) {
                Map<String, Object> attorneyMap = new LinkedHashMap<>();
                attorneyMap.put("name", attorney.getName());
                attorneyMap.put("barNumber", attorney.getBarNumber());
                caseInfo.put("attorney", attorneyMap);
            }

            item.put("caseInfo", caseInfo);
            item.put("remedySought", info.getRemedySought());
        }
        return item;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

}
