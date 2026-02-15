package com.caseplan.adapter.in.web.controller;

import com.caseplan.adapter.in.web.dto.CreateCasePlanRequest;
import com.caseplan.application.port.in.CreateCasePlanCommand;
import com.caseplan.application.service.CasePlanService;
import com.caseplan.application.service.CreateCasePlanResult;
import com.caseplan.common.exception.BlockException;
import com.caseplan.common.exception.WarningException;
import com.caseplan.domain.model.CasePlan;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class CasePlanControllerTest {

    @Mock private CasePlanService casePlanService;

    @InjectMocks
    private CasePlanController controller;

    @Test
    @SuppressWarnings("unchecked")
    public void listAll_withPaginationAndFilters_returnsPagedResponse() {
        CasePlan p1 = new CasePlan();
        p1.setId(1L);
        CasePlan p2 = new CasePlan();
        p2.setId(2L);
        Page<CasePlan> page = new PageImpl<>(Arrays.asList(p1, p2), PageRequest.of(0, 20), 35);
        when(casePlanService.listPage(1, 20, "completed", "zhang")).thenReturn(page);

        Map<String, Object> response = controller.listAll(1, 20, "completed", "zhang");

        assertEquals(35L, response.get("count"));
        assertEquals(1, response.get("page"));
        assertEquals(20, response.get("page_size"));
        assertNotNull(response.get("results"));
        assertEquals(2, ((java.util.List<CasePlan>) response.get("results")).size());
    }

    @Test
    public void getById_found_returns200() {
        CasePlan plan = new CasePlan();
        plan.setId(1L);
        when(casePlanService.getById(1L)).thenReturn(Optional.of(plan));

        ResponseEntity<CasePlan> response = controller.getById(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    public void getById_notFound_returns404() {
        when(casePlanService.getById(999L)).thenReturn(Optional.empty());

        ResponseEntity<CasePlan> response = controller.getById(999L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    public void getStatus_found_returns200() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "completed");
        when(casePlanService.getStatus(1L)).thenReturn(status);

        ResponseEntity<Map<String, Object>> response = controller.getStatus(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("completed", response.getBody().get("status"));
    }

    @Test
    public void getStatus_notFound_returns404() {
        when(casePlanService.getStatus(999L)).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = controller.getStatus(999L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    public void create_withoutWarnings_returnsCasePlanBody() {
        CreateCasePlanRequest req = new CreateCasePlanRequest();
        CasePlan plan = new CasePlan();
        plan.setId(10L);
        when(casePlanService.create(any(CreateCasePlanCommand.class)))
                .thenReturn(new CreateCasePlanResult(plan, Collections.emptyList()));

        ResponseEntity<?> response = controller.create(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody() instanceof CasePlan);
        assertEquals(Long.valueOf(10L), ((CasePlan) response.getBody()).getId());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void create_withWarnings_returnsWrappedBody() {
        CreateCasePlanRequest req = new CreateCasePlanRequest();
        CasePlan plan = new CasePlan();
        plan.setId(11L);
        WarningException warning = new WarningException("W_CODE", "warn msg", null);
        when(casePlanService.create(any(CreateCasePlanCommand.class)))
                .thenReturn(new CreateCasePlanResult(plan, Collections.singletonList(warning)));

        ResponseEntity<?> response = controller.create(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body.get("data"));
        assertNotNull(body.get("warnings"));
    }

    @Test(expected = BlockException.class)
    public void create_whenServiceThrowsBlockException_propagates() {
        CreateCasePlanRequest req = new CreateCasePlanRequest();
        when(casePlanService.create(any(CreateCasePlanCommand.class)))
                .thenThrow(new BlockException("B_CODE", "blocked", null));

        controller.create(req);
    }

    @Test
    public void download_ready_returnsAttachment() {
        CasePlan plan = new CasePlan();
        plan.setId(5L);
        plan.setStatus("completed");
        plan.setGeneratedPlan("hello");
        when(casePlanService.getForDownload(5L)).thenReturn(Optional.of(plan));

        ResponseEntity<byte[]> response = controller.download(5L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("attachment; filename=\"caseplan-5.txt\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("hello", new String(response.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    public void download_notFound_returns404() {
        when(casePlanService.getForDownload(404L)).thenReturn(Optional.empty());

        ResponseEntity<byte[]> response = controller.download(404L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void retry_failed_returns202() {
        CasePlan failed = new CasePlan();
        failed.setId(9L);
        failed.setStatus("pending");
        when(casePlanService.retryFailed(9L)).thenReturn(Optional.of(failed));

        ResponseEntity<Map<String, Object>> response = controller.retry(9L);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        assertEquals(9L, response.getBody().get("id"));
    }

    @Test
    public void retry_notFound_returns404() {
        when(casePlanService.retryFailed(999L)).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = controller.retry(999L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
