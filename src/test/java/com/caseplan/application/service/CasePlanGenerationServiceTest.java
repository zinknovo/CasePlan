package com.caseplan.application.service;

import com.caseplan.domain.model.Attorney;
import com.caseplan.domain.model.CaseInfo;
import com.caseplan.domain.model.CasePlan;
import com.caseplan.domain.model.Client;
import com.caseplan.adapter.out.persistence.CasePlanRepo;
import com.caseplan.application.port.out.LLMService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CasePlanGenerationServiceTest {

    @Mock private CasePlanRepo casePlanRepo;
    @Mock private LLMService llmService;

    private CasePlanGenerationService service;

    @Before
    public void setup() {
        service = new CasePlanGenerationService(casePlanRepo, llmService);
    }

    @Test
    public void processWithRetry_notFound_returnsFalse() {
        when(casePlanRepo.findById(999L)).thenReturn(Optional.empty());

        boolean ok = service.processWithRetry(999L);

        assertFalse(ok);
        verify(casePlanRepo, never()).save(any(CasePlan.class));
        verify(llmService, never()).chat(anyString());
    }

    @Test
    public void processWithRetry_pending_success_returnsTrueAndCompleted() {
        CasePlan plan = buildCasePlan(1L, "pending");
        when(casePlanRepo.findById(1L)).thenReturn(Optional.of(plan));
        when(casePlanRepo.save(any(CasePlan.class))).thenAnswer(identityCasePlanAnswer());
        when(llmService.chat(anyString())).thenReturn("generated");

        boolean ok = service.processWithRetry(1L);

        assertTrue(ok);
        ArgumentCaptor<CasePlan> captor = ArgumentCaptor.forClass(CasePlan.class);
        verify(casePlanRepo, atLeast(2)).save(captor.capture());
        CasePlan last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("completed", last.getStatus());
        assertEquals("generated", last.getGeneratedPlan());
        assertNull(last.getErrorMessage());
    }

    @Test
    public void processWithRetry_allAttemptsFail_returnsFalseAndFailed() {
        CasePlan plan = buildCasePlan(2L, "pending");
        when(casePlanRepo.findById(2L)).thenReturn(Optional.of(plan));
        when(casePlanRepo.save(any(CasePlan.class))).thenAnswer(identityCasePlanAnswer());
        when(llmService.chat(anyString())).thenThrow(new RuntimeException("LLM down"));

        boolean ok = service.processWithRetry(2L);

        assertFalse(ok);
        ArgumentCaptor<CasePlan> captor = ArgumentCaptor.forClass(CasePlan.class);
        verify(casePlanRepo, atLeast(2)).save(captor.capture());
        CasePlan last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("failed", last.getStatus());
        assertEquals("LLM down", last.getErrorMessage());
    }

    @Test
    public void processWithRetry_processing_canContinue() {
        CasePlan plan = buildCasePlan(3L, "processing");
        when(casePlanRepo.findById(3L)).thenReturn(Optional.of(plan));
        when(casePlanRepo.save(any(CasePlan.class))).thenAnswer(identityCasePlanAnswer());
        when(llmService.chat(anyString())).thenReturn("ok");

        boolean ok = service.processWithRetry(3L);

        assertTrue(ok);
        ArgumentCaptor<CasePlan> captor = ArgumentCaptor.forClass(CasePlan.class);
        verify(casePlanRepo, atLeast(1)).save(captor.capture());
        CasePlan last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("completed", last.getStatus());
    }

    @Test
    public void processWithRetry_completed_skips() {
        CasePlan plan = buildCasePlan(4L, "completed");
        when(casePlanRepo.findById(4L)).thenReturn(Optional.of(plan));

        boolean ok = service.processWithRetry(4L);

        assertFalse(ok);
        verify(llmService, never()).chat(anyString());
        verify(casePlanRepo, never()).save(any(CasePlan.class));
    }

    @Test
    public void processWithRetry_unknownStatus_skips() {
        CasePlan plan = buildCasePlan(5L, "archived");
        when(casePlanRepo.findById(5L)).thenReturn(Optional.of(plan));

        boolean ok = service.processWithRetry(5L);

        assertFalse(ok);
        verify(llmService, never()).chat(anyString());
        verify(casePlanRepo, never()).save(any(CasePlan.class));
    }

    @Test
    public void processWithRetry_failedStatus_skips() {
        CasePlan plan = buildCasePlan(6L, "failed");
        when(casePlanRepo.findById(6L)).thenReturn(Optional.of(plan));

        boolean ok = service.processWithRetry(6L);

        assertFalse(ok);
        verify(llmService, never()).chat(anyString());
        verify(casePlanRepo, never()).save(any(CasePlan.class));
    }

    @Test
    public void processWithRetry_withNullAndBlankFields_stillGenerates() {
        CasePlan plan = buildCasePlan(7L, "pending");
        plan.getCaseInfo().setCaseNumber("  ");
        plan.getCaseInfo().setAdditionalCauses(null);
        plan.getCaseInfo().setPriorLegalActions("");
        plan.getCaseInfo().setCaseDocuments(null);
        when(casePlanRepo.findById(7L)).thenReturn(Optional.of(plan));
        when(casePlanRepo.save(any(CasePlan.class))).thenAnswer(identityCasePlanAnswer());
        when(llmService.chat(anyString())).thenReturn("ok");

        boolean ok = service.processWithRetry(7L);
        assertTrue(ok);
    }

    private CasePlan buildCasePlan(Long id, String status) {
        CasePlan plan = new CasePlan();
        plan.setId(id);
        plan.setStatus(status);

        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setCaseNumber("123456");
        caseInfo.setPrimaryCauseOfAction("Contract Breach");
        caseInfo.setRemedySought("Damages");
        caseInfo.setAdditionalCauses("None");
        caseInfo.setPriorLegalActions("None");
        caseInfo.setCaseDocuments("N/A");

        Client client = new Client();
        client.setFirstName("John");
        client.setLastName("Doe");
        caseInfo.setClient(client);

        Attorney attorney = new Attorney();
        attorney.setName("Jane Smith");
        attorney.setBarNumber("BAR123");
        caseInfo.setAttorney(attorney);

        plan.setCaseInfo(caseInfo);
        return plan;
    }

    private Answer<CasePlan> identityCasePlanAnswer() {
        return new Answer<CasePlan>() {
            @Override
            public CasePlan answer(InvocationOnMock invocation) {
                return invocation.getArgument(0);
            }
        };
    }
}
