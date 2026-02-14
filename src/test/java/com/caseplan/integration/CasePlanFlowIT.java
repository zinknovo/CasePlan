package com.caseplan.integration;

import com.caseplan.core.entity.Attorney;
import com.caseplan.core.entity.CaseInfo;
import com.caseplan.core.entity.CasePlan;
import com.caseplan.core.entity.Client;
import com.caseplan.core.repo.AttorneyRepo;
import com.caseplan.core.repo.CaseInfoRepo;
import com.caseplan.core.repo.CasePlanRepo;
import com.caseplan.core.repo.ClientRepo;
import com.caseplan.integration.consumer.CasePlanConsumer;
import com.caseplan.integration.llm.LLMService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
public class CasePlanFlowIT {

    private static final String QUEUE_KEY = "caseplan:pending";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CasePlanRepo casePlanRepo;

    @Autowired
    private CaseInfoRepo caseInfoRepo;

    @Autowired
    private ClientRepo clientRepo;

    @Autowired
    private AttorneyRepo attorneyRepo;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private LLMService llmService;

    @MockBean
    private CasePlanConsumer disabledBackgroundConsumer;

    @Before
    public void cleanState() {
        redisTemplate.delete(QUEUE_KEY);
        casePlanRepo.deleteAll();
        caseInfoRepo.deleteAll();
        clientRepo.deleteAll();
        attorneyRepo.deleteAll();
    }

    // ==================== Baseline flow IT ====================

    /** Create API should persist pending record and enqueue task id into Redis. */
    @Test
    public void createCasePlan_persistsPendingAndPushesQueue() throws Exception {
        long planId = createCasePlan();

        CasePlan saved = casePlanRepo.findById(planId).orElseThrow();
        assertEquals("pending", saved.getStatus());
        assertNull(saved.getCaseInfo().getCaseNumber());

        List<String> queuedIds = redisTemplate.opsForList().range(QUEUE_KEY, 0, -1);
        assertTrue(queuedIds != null && queuedIds.contains(String.valueOf(planId)));
    }

    /** Worker should move status pending -> completed when LLM succeeds. */
    @Test
    public void consumerProcessWithRetry_transitionsPendingToCompleted() throws Exception {
        long planId = createCasePlan();
        Mockito.when(llmService.chat(Mockito.anyString())).thenReturn("Generated plan from integration test");

        CasePlanConsumer consumer = new CasePlanConsumer(casePlanRepo, redisTemplate, llmService);
        Method processWithRetry = CasePlanConsumer.class.getDeclaredMethod("processWithRetry", String.class);
        processWithRetry.setAccessible(true);
        processWithRetry.invoke(consumer, String.valueOf(planId));

        CasePlan updated = casePlanRepo.findById(planId).orElseThrow();
        assertEquals("completed", updated.getStatus());
        assertEquals("Generated plan from integration test", updated.getGeneratedPlan());
        assertNull(updated.getErrorMessage());
    }

    /** Startup reconciliation should re-queue pending records missing from Redis list. */
    @Test
    public void recoverLostPendingQueueItems_requeuesPendingRecordsMissingFromRedis() throws Exception {
        long planId = createPendingCasePlanDirectly();
        redisTemplate.delete(QUEUE_KEY);

        CasePlanConsumer consumer = new CasePlanConsumer(casePlanRepo, redisTemplate, llmService);
        Method recoverLost = CasePlanConsumer.class.getDeclaredMethod("recoverLostPendingQueueItems");
        recoverLost.setAccessible(true);
        recoverLost.invoke(consumer);

        List<String> queuedIds = redisTemplate.opsForList().range(QUEUE_KEY, 0, -1);
        assertTrue(queuedIds != null && queuedIds.contains(String.valueOf(planId)));
    }

    // ==================== Reliability IT ====================

    /** Worker should mark failed after all retry attempts are exhausted. */
    @Test
    public void consumerProcessWithRetry_allAttemptsFail_marksFailed() throws Exception {
        long planId = createCasePlan();
        Mockito.when(llmService.chat(Mockito.anyString())).thenThrow(new RuntimeException("LLM timeout"));

        CasePlanConsumer consumer = new CasePlanConsumer(casePlanRepo, redisTemplate, llmService);
        Method processWithRetry = CasePlanConsumer.class.getDeclaredMethod("processWithRetry", String.class);
        processWithRetry.setAccessible(true);
        processWithRetry.invoke(consumer, String.valueOf(planId));

        CasePlan updated = casePlanRepo.findById(planId).orElseThrow();
        assertEquals("failed", updated.getStatus());
        assertEquals("LLM timeout", updated.getErrorMessage());
    }

    /** Stale processing records should be reset to pending and pushed back to queue. */
    @Test
    public void recoverStaleProcessing_requeuesOldProcessingPlan() throws Exception {
        long planId = createProcessingCasePlanDirectly();
        redisTemplate.delete(QUEUE_KEY);
        jdbcTemplate.update(
                "update dev_caseplans set updated_at = ? where id = ?",
                Timestamp.from(Instant.now().minusSeconds(60L * 60L)),
                planId
        );

        CasePlanConsumer consumer = new CasePlanConsumer(casePlanRepo, redisTemplate, llmService);
        Method recoverStale = CasePlanConsumer.class.getDeclaredMethod("recoverStaleProcessing");
        recoverStale.setAccessible(true);
        recoverStale.invoke(consumer);

        CasePlan updated = casePlanRepo.findById(planId).orElseThrow();
        assertEquals("pending", updated.getStatus());
        List<String> queuedIds = redisTemplate.opsForList().range(QUEUE_KEY, 0, -1);
        assertTrue(queuedIds != null && queuedIds.contains(String.valueOf(planId)));
    }

    // ==================== Concurrency IT ====================

    /** Two different pending tasks processed concurrently should both complete successfully. */
    @Test
    public void consumerProcessWithRetry_parallelDifferentPlans_bothCompleted() throws Exception {
        long planId1 = createPendingCasePlanDirectlyWithSeed("P1");
        long planId2 = createPendingCasePlanDirectlyWithSeed("P2");
        Mockito.when(llmService.chat(Mockito.anyString())).thenReturn("Parallel generated plan");

        CasePlanConsumer consumer = new CasePlanConsumer(casePlanRepo, redisTemplate, llmService);
        Method processWithRetry = CasePlanConsumer.class.getDeclaredMethod("processWithRetry", String.class);
        processWithRetry.setAccessible(true);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Thread t1 = new Thread(() -> invokeProcess(processWithRetry, consumer, String.valueOf(planId1), ready, start, error));
        Thread t2 = new Thread(() -> invokeProcess(processWithRetry, consumer, String.valueOf(planId2), ready, start, error));

        t1.start();
        t2.start();
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        t1.join();
        t2.join();

        if (error.get() != null) {
            throw new AssertionError("Parallel processing failed", error.get());
        }

        CasePlan updated1 = casePlanRepo.findById(planId1).orElseThrow();
        CasePlan updated2 = casePlanRepo.findById(planId2).orElseThrow();
        assertEquals("completed", updated1.getStatus());
        assertEquals("completed", updated2.getStatus());
    }

    private long createCasePlan() throws Exception {
        return createCasePlanWithBarNumber("BAR-IT-001");
    }

    private long createCasePlanWithBarNumber(String barNumber) throws Exception {
        String body = "{"
                + "\"clientFirstName\":\"John\","
                + "\"clientLastName\":\"Doe\","
                + "\"attorneyName\":\"Jane Smith\","
                + "\"barNumber\":\"" + barNumber + "\","
                + "\"referringSource\":\"web\","
                + "\"primaryCauseOfAction\":\"Contract Breach\","
                + "\"opposingParty\":\"Acme Corp\","
                + "\"legalRemedySought\":\"Damages\","
                + "\"confirm\":true"
                + "}";

        MvcResult result = mockMvc.perform(post("/api/caseplans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    private long createPendingCasePlanDirectly() {
        return createPendingCasePlanDirectlyWithSeed("DEFAULT");
    }

    private long createPendingCasePlanDirectlyWithSeed(String seed) {
        Client client = new Client();
        client.setFirstName("Direct" + seed);
        client.setLastName("Client" + seed);
        client = clientRepo.save(client);

        Attorney attorney = new Attorney();
        attorney.setName("Direct Attorney " + seed);
        attorney.setBarNumber("BAR-IT-DIRECT-" + seed);
        attorney = attorneyRepo.save(attorney);

        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setClient(client);
        caseInfo.setAttorney(attorney);
        caseInfo.setPrimaryCauseOfAction("Direct Cause " + seed);
        caseInfo.setOpposingParty("Direct Opposing " + seed);
        caseInfo.setLegalRemedySought("Direct Remedy " + seed);
        caseInfo = caseInfoRepo.save(caseInfo);

        CasePlan plan = new CasePlan();
        plan.setCaseInfo(caseInfo);
        plan.setStatus("pending");
        plan = casePlanRepo.save(plan);
        return plan.getId();
    }

    private long createProcessingCasePlanDirectly() {
        long id = createPendingCasePlanDirectly();
        CasePlan plan = casePlanRepo.findById(id).orElseThrow();
        plan.setStatus("processing");
        return casePlanRepo.save(plan).getId();
    }

    private void invokeProcess(
            Method processMethod,
            CasePlanConsumer consumer,
            String planId,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicReference<Throwable> error) {
        try {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            processMethod.invoke(consumer, planId);
        } catch (Throwable t) {
            error.compareAndSet(null, t);
        }
    }
}
