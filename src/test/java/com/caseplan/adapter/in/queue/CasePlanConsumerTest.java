package com.caseplan.adapter.in.queue;

import com.caseplan.domain.model.CasePlan;
import com.caseplan.application.port.out.QueuePort;
import com.caseplan.adapter.out.persistence.CasePlanRepo;
import com.caseplan.application.service.CasePlanGenerationService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CasePlanConsumerTest {

    @Mock private CasePlanRepo casePlanRepo;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private QueuePort queuePort;
    @Mock private CasePlanGenerationService generationService;
    @Mock private ListOperations<String, String> listOps;

    private CasePlanConsumer consumer;

    @Before
    public void setup() throws Exception {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        consumer = createConsumerWithoutPostConstruct();
    }

    private CasePlanConsumer createConsumerWithoutPostConstruct() throws Exception {
        java.lang.reflect.Constructor<CasePlanConsumer> ctor =
                CasePlanConsumer.class.getDeclaredConstructor(
                        CasePlanRepo.class, StringRedisTemplate.class, QueuePort.class, CasePlanGenerationService.class);
        return ctor.newInstance(casePlanRepo, redisTemplate, queuePort, generationService);
    }

    private CasePlan plan(Long id, String status) {
        CasePlan plan = new CasePlan();
        plan.setId(id);
        plan.setStatus(status);
        return plan;
    }

    @Test
    public void recoverStaleProcessing_requeuesStaleRecords() throws Exception {
        CasePlan stale1 = plan(10L, "processing");
        CasePlan stale2 = plan(20L, "processing");
        when(casePlanRepo.findByStatusAndUpdatedAtBefore(eq("processing"), any(Instant.class)))
                .thenReturn(Arrays.asList(stale1, stale2));
        when(casePlanRepo.save(any(CasePlan.class))).thenAnswer(new Answer<CasePlan>() {
            @Override
            public CasePlan answer(InvocationOnMock invocation) {
                return invocation.getArgument(0);
            }
        });

        Method method = CasePlanConsumer.class.getDeclaredMethod("recoverStaleProcessing");
        method.setAccessible(true);
        method.invoke(consumer);

        verify(casePlanRepo, times(2)).save(any(CasePlan.class));
        verify(queuePort).enqueue("10");
        verify(queuePort).enqueue("20");
    }

    @Test
    public void recoverStaleProcessing_noStaleRecords_doesNothing() throws Exception {
        when(casePlanRepo.findByStatusAndUpdatedAtBefore(eq("processing"), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        Method method = CasePlanConsumer.class.getDeclaredMethod("recoverStaleProcessing");
        method.setAccessible(true);
        method.invoke(consumer);

        verify(casePlanRepo, never()).save(any());
        verify(queuePort, never()).enqueue(anyString());
    }

    @Test
    public void recoverLostPendingQueueItems_requeuesMissingOnly() throws Exception {
        CasePlan pending1 = plan(100L, "pending");
        CasePlan pending2 = plan(200L, "pending");
        when(casePlanRepo.findByStatus("pending")).thenReturn(Arrays.asList(pending1, pending2));
        when(listOps.range("caseplan:pending", 0, -1)).thenReturn(Collections.singletonList("100"));

        Method method = CasePlanConsumer.class.getDeclaredMethod("recoverLostPendingQueueItems");
        method.setAccessible(true);
        method.invoke(consumer);

        verify(queuePort, never()).enqueue("100");
        verify(queuePort).enqueue("200");
    }

    @Test
    public void recoverLostPendingQueueItems_emptyPending_doesNothing() throws Exception {
        when(casePlanRepo.findByStatus("pending")).thenReturn(Collections.emptyList());

        Method method = CasePlanConsumer.class.getDeclaredMethod("recoverLostPendingQueueItems");
        method.setAccessible(true);
        method.invoke(consumer);

        verify(listOps, never()).range(anyString(), anyLong(), anyLong());
        verify(queuePort, never()).enqueue(anyString());
    }
}
