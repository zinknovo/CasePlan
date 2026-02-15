package com.caseplan.adapter.in.lambda;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.caseplan.application.service.CasePlanGenerationService;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GenerateCasePlanWorkerHandlerTest {

    @Test
    public void handleRequest_emptyRecords_returnsNoRecords() {
        CasePlanGenerationService generationService = mock(CasePlanGenerationService.class);
        GenerateCasePlanWorkerHandler handler = new GenerateCasePlanWorkerHandler(generationService);

        String result = handler.handleRequest(new SQSEvent(), null);
        assertEquals("no records", result);
        verify(generationService, never()).processWithRetry(anyLong());
    }

    @Test
    public void handleRequest_nullEvent_returnsNoRecords() {
        CasePlanGenerationService generationService = mock(CasePlanGenerationService.class);
        GenerateCasePlanWorkerHandler handler = new GenerateCasePlanWorkerHandler(generationService);
        String result = handler.handleRequest(null, null);
        assertEquals("no records", result);
    }

    @Test
    public void handleRequest_successAndSkip_countsCorrectly() {
        CasePlanGenerationService generationService = mock(CasePlanGenerationService.class);
        when(generationService.processWithRetry(1L)).thenReturn(true);
        when(generationService.processWithRetry(2L)).thenReturn(false);

        SQSEvent.SQSMessage m1 = new SQSEvent.SQSMessage();
        m1.setBody("{\"planId\":1}");
        SQSEvent.SQSMessage m2 = new SQSEvent.SQSMessage();
        m2.setBody("{\"planId\":\"2\"}");
        SQSEvent.SQSMessage m3 = new SQSEvent.SQSMessage();
        m3.setBody("{\"planId\":null}");
        SQSEvent event = new SQSEvent();
        event.setRecords(java.util.Arrays.asList(m1, m2, m3));

        GenerateCasePlanWorkerHandler handler = new GenerateCasePlanWorkerHandler(generationService);
        String result = handler.handleRequest(event, null);

        assertTrue(result.contains("success=1"));
        assertTrue(result.contains("skipped=2"));
        assertTrue(result.contains("failed=0"));
        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(generationService, org.mockito.Mockito.times(2)).processWithRetry(captor.capture());
        assertEquals(java.util.Arrays.asList(1L, 2L), captor.getAllValues());
    }

    @Test
    public void handleRequest_invalidJsonBody_isSkipped() {
        CasePlanGenerationService generationService = mock(CasePlanGenerationService.class);
        SQSEvent.SQSMessage m = new SQSEvent.SQSMessage();
        m.setBody("{bad");
        SQSEvent event = new SQSEvent();
        event.setRecords(java.util.Collections.singletonList(m));

        GenerateCasePlanWorkerHandler handler = new GenerateCasePlanWorkerHandler(generationService);
        String result = handler.handleRequest(event, null);

        assertTrue(result.contains("skipped=1"));
        verify(generationService, never()).processWithRetry(anyLong());
    }

    @Test
    public void handleRequest_emptyAndMissingPlanId_areSkipped() {
        CasePlanGenerationService generationService = mock(CasePlanGenerationService.class);
        SQSEvent.SQSMessage m1 = new SQSEvent.SQSMessage();
        m1.setBody("");
        SQSEvent.SQSMessage m2 = new SQSEvent.SQSMessage();
        m2.setBody("{\"foo\":1}");
        SQSEvent event = new SQSEvent();
        event.setRecords(java.util.Arrays.asList(m1, m2));

        GenerateCasePlanWorkerHandler handler = new GenerateCasePlanWorkerHandler(generationService);
        String result = handler.handleRequest(event, null);

        assertTrue(result.contains("skipped=2"));
        verify(generationService, never()).processWithRetry(anyLong());
    }

    @Test
    public void handleRequest_nullBody_isSkipped() {
        CasePlanGenerationService generationService = mock(CasePlanGenerationService.class);
        SQSEvent.SQSMessage m = new SQSEvent.SQSMessage();
        m.setBody(null);
        SQSEvent event = new SQSEvent();
        event.setRecords(java.util.Collections.singletonList(m));

        GenerateCasePlanWorkerHandler handler = new GenerateCasePlanWorkerHandler(generationService);
        String result = handler.handleRequest(event, null);
        assertTrue(result.contains("skipped=1"));
    }

    @Test(expected = RuntimeException.class)
    public void handleRequest_recordFailure_throws() {
        CasePlanGenerationService generationService = mock(CasePlanGenerationService.class);
        when(generationService.processWithRetry(1L)).thenThrow(new RuntimeException("boom"));

        SQSEvent.SQSMessage m1 = new SQSEvent.SQSMessage();
        m1.setBody("{\"planId\":1}");
        SQSEvent event = new SQSEvent();
        event.setRecords(java.util.Collections.singletonList(m1));

        GenerateCasePlanWorkerHandler handler = new GenerateCasePlanWorkerHandler(generationService);
        handler.handleRequest(event, null);
    }
}
