package com.caseplan.integration.consumer;

import com.caseplan.core.entity.*;
import com.caseplan.core.repo.CasePlanRepo;
import com.caseplan.integration.llm.LLMService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class CasePlanConsumerTest {

    @Mock private CasePlanRepo casePlanRepo;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private LLMService llmService;
    @Mock private ListOperations<String, String> listOps;

    private CasePlanConsumer consumer;

    @Before
    public void setup() throws Exception {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        // Use reflection to create consumer without triggering @PostConstruct
        consumer = createConsumerWithoutPostConstruct();
    }

    private CasePlanConsumer createConsumerWithoutPostConstruct() throws Exception {
        // Use the constructor directly via reflection to avoid @PostConstruct
        java.lang.reflect.Constructor<CasePlanConsumer> ctor =
                CasePlanConsumer.class.getDeclaredConstructor(
                        CasePlanRepo.class, StringRedisTemplate.class, LLMService.class);
        return ctor.newInstance(casePlanRepo, redisTemplate, llmService);
    }

    private CasePlan buildCasePlan(Long id, String status) {
        CasePlan plan = new CasePlan();
        plan.setId(id);
        plan.setStatus(status);

        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setId(1L);
        caseInfo.setCaseNumber("123456");
        caseInfo.setPrimaryCauseOfAction("Contract Breach");
        caseInfo.setLegalRemedySought("Damages");

        Client client = new Client();
        client.setId(1L);
        client.setFirstName("John");
        client.setLastName("Doe");
        caseInfo.setClient(client);

        Attorney attorney = new Attorney();
        attorney.setId(1L);
        attorney.setName("Jane Smith");
        attorney.setBarNumber("BAR123");
        caseInfo.setAttorney(attorney);

        plan.setCaseInfo(caseInfo);
        return plan;
    }

    // ==================== processWithRetry ====================

    @Test
    public void processWithRetry_success_statusCompleted() throws Exception {
        CasePlan plan = buildCasePlan(1L, "pending");
        when(casePlanRepo.findById(1L)).thenReturn(Optional.of(plan));
        when(casePlanRepo.save(any(CasePlan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(llmService.chat(anyString())).thenReturn("Generated plan content");

        Method method = CasePlanConsumer.class.getDeclaredMethod("processWithRetry", String.class);
        method.setAccessible(true);
        method.invoke(consumer, "1");

        ArgumentCaptor<CasePlan> captor = ArgumentCaptor.forClass(CasePlan.class);
        verify(casePlanRepo, atLeast(2)).save(captor.capture());

        // Last save should be completed
        CasePlan lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("completed", lastSaved.getStatus());
        assertEquals("Generated plan content", lastSaved.getGeneratedPlan());
        assertNull(lastSaved.getErrorMessage());
    }

    @Test
    public void processWithRetry_notFound_doesNothing() throws Exception {
        when(casePlanRepo.findById(999L)).thenReturn(Optional.empty());

        Method method = CasePlanConsumer.class.getDeclaredMethod("processWithRetry", String.class);
        method.setAccessible(true);
        method.invoke(consumer, "999");

        verify(casePlanRepo, never()).save(any());
        verify(llmService, never()).chat(anyString());
    }

    @Test
    public void processWithRetry_allRetrysFail_statusFailed() throws Exception {
        CasePlan plan = buildCasePlan(1L, "pending");
        when(casePlanRepo.findById(1L)).thenReturn(Optional.of(plan));
        when(casePlanRepo.save(any(CasePlan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(llmService.chat(anyString())).thenThrow(new RuntimeException("LLM down"));

        Method method = CasePlanConsumer.class.getDeclaredMethod("processWithRetry", String.class);
        method.setAccessible(true);
        method.invoke(consumer, "1");

        ArgumentCaptor<CasePlan> captor = ArgumentCaptor.forClass(CasePlan.class);
        verify(casePlanRepo, atLeast(2)).save(captor.capture());

        CasePlan lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("failed", lastSaved.getStatus());
        assertEquals("LLM down", lastSaved.getErrorMessage());
    }

    @Test
    public void processWithRetry_failsThenSucceeds_statusCompleted() throws Exception {
        CasePlan plan = buildCasePlan(1L, "pending");
        when(casePlanRepo.findById(1L)).thenReturn(Optional.of(plan));
        when(casePlanRepo.save(any(CasePlan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(llmService.chat(anyString()))
                .thenThrow(new RuntimeException("timeout"))
                .thenReturn("Plan generated on retry");

        Method method = CasePlanConsumer.class.getDeclaredMethod("processWithRetry", String.class);
        method.setAccessible(true);
        method.invoke(consumer, "1");

        ArgumentCaptor<CasePlan> captor = ArgumentCaptor.forClass(CasePlan.class);
        verify(casePlanRepo, atLeast(2)).save(captor.capture());

        CasePlan lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("completed", lastSaved.getStatus());
        assertEquals("Plan generated on retry", lastSaved.getGeneratedPlan());
    }

    @Test
    public void processWithRetry_notPending_skipsProcessing() throws Exception {
        CasePlan plan = buildCasePlan(1L, "completed");
        when(casePlanRepo.findById(1L)).thenReturn(Optional.of(plan));

        Method method = CasePlanConsumer.class.getDeclaredMethod("processWithRetry", String.class);
        method.setAccessible(true);
        method.invoke(consumer, "1");

        verify(casePlanRepo, never()).save(any(CasePlan.class));
        verify(llmService, never()).chat(anyString());
    }

    // ==================== recoverStaleProcessing ====================

    @Test
    public void recoverStaleProcessing_requeuesStaleRecords() throws Exception {
        CasePlan stale1 = buildCasePlan(10L, "processing");
        CasePlan stale2 = buildCasePlan(20L, "processing");
        when(casePlanRepo.findByStatusAndUpdatedAtBefore(eq("processing"), any(Instant.class)))
                .thenReturn(Arrays.asList(stale1, stale2));
        when(casePlanRepo.save(any(CasePlan.class))).thenAnswer(inv -> inv.getArgument(0));

        Method method = CasePlanConsumer.class.getDeclaredMethod("recoverStaleProcessing");
        method.setAccessible(true);
        method.invoke(consumer);

        verify(casePlanRepo, times(2)).save(any(CasePlan.class));
        verify(listOps).rightPush("caseplan:pending", "10");
        verify(listOps).rightPush("caseplan:pending", "20");
        assertEquals("pending", stale1.getStatus());
        assertEquals("pending", stale2.getStatus());
    }

    @Test
    public void recoverStaleProcessing_noStaleRecords_doesNothing() throws Exception {
        when(casePlanRepo.findByStatusAndUpdatedAtBefore(eq("processing"), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        Method method = CasePlanConsumer.class.getDeclaredMethod("recoverStaleProcessing");
        method.setAccessible(true);
        method.invoke(consumer);

        verify(casePlanRepo, never()).save(any());
        verify(listOps, never()).rightPush(anyString(), anyString());
    }

    @Test
    public void recoverLostPendingQueueItems_requeuesMissingOnly() throws Exception {
        CasePlan pending1 = buildCasePlan(100L, "pending");
        CasePlan pending2 = buildCasePlan(200L, "pending");
        when(casePlanRepo.findByStatus("pending")).thenReturn(Arrays.asList(pending1, pending2));
        when(listOps.range("caseplan:pending", 0, -1)).thenReturn(Collections.singletonList("100"));

        Method method = CasePlanConsumer.class.getDeclaredMethod("recoverLostPendingQueueItems");
        method.setAccessible(true);
        method.invoke(consumer);

        verify(listOps, never()).rightPush("caseplan:pending", "100");
        verify(listOps).rightPush("caseplan:pending", "200");
    }

    @Test
    public void recoverLostPendingQueueItems_emptyPending_doesNothing() throws Exception {
        when(casePlanRepo.findByStatus("pending")).thenReturn(Collections.emptyList());

        Method method = CasePlanConsumer.class.getDeclaredMethod("recoverLostPendingQueueItems");
        method.setAccessible(true);
        method.invoke(consumer);

        verify(listOps, never()).range(anyString(), anyLong(), anyLong());
        verify(listOps, never()).rightPush(anyString(), anyString());
    }
}
