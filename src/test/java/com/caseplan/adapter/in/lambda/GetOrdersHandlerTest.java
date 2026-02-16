package com.caseplan.adapter.in.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.caseplan.application.service.CasePlanService;
import com.caseplan.domain.model.Attorney;
import com.caseplan.domain.model.CaseInfo;
import com.caseplan.domain.model.CasePlan;
import com.caseplan.domain.model.Client;
import org.junit.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetOrdersHandlerTest {

    @Test
    public void handleRequest_success_returns200AndItems() {
        CasePlanService service = mock(CasePlanService.class);
        when(service.listAll()).thenReturn(List.of(buildPlan(9L, "completed")));
        GetOrdersHandler handler = new GetOrdersHandler(service);

        APIGatewayProxyResponseEvent res = handler.handleRequest(null, null);

        assertEquals(Integer.valueOf(200), res.getStatusCode());
        assertTrue(res.getBody().contains("\"count\":1"));
        assertTrue(res.getBody().contains("\"id\":9"));
        assertTrue(res.getBody().contains("\"status\":\"completed\""));
        assertTrue(res.getBody().contains("\"clientName\":\"Mia Johnson\""));
    }

    @Test
    public void handleRequest_emptyList_returns200() {
        CasePlanService service = mock(CasePlanService.class);
        when(service.listAll()).thenReturn(Collections.emptyList());
        GetOrdersHandler handler = new GetOrdersHandler(service);

        APIGatewayProxyResponseEvent res = handler.handleRequest(null, null);
        assertEquals(Integer.valueOf(200), res.getStatusCode());
        assertTrue(res.getBody().contains("\"count\":0"));
    }

    @Test
    public void handleRequest_serviceThrows_returns500() {
        CasePlanService service = mock(CasePlanService.class);
        when(service.listAll()).thenThrow(new RuntimeException("db down"));
        GetOrdersHandler handler = new GetOrdersHandler(service);

        APIGatewayProxyResponseEvent res = handler.handleRequest(null, null);
        assertEquals(Integer.valueOf(500), res.getStatusCode());
    }

    private CasePlan buildPlan(Long id, String status) {
        Client client = new Client();
        client.setFirstName("Mia");
        client.setLastName("Johnson");

        Attorney attorney = new Attorney();
        attorney.setName("Ethan Cole");
        attorney.setBarNumber("BAR-1");

        CaseInfo info = new CaseInfo();
        info.setCaseNumber("CP-1");
        info.setPrimaryCauseOfAction("Contract");
        info.setRemedySought("Damages");
        info.setClient(client);
        info.setAttorney(attorney);

        CasePlan plan = new CasePlan();
        plan.setId(id);
        plan.setStatus(status);
        plan.setCreatedAt(Instant.parse("2026-02-16T00:00:00Z"));
        plan.setCaseInfo(info);
        return plan;
    }
}
