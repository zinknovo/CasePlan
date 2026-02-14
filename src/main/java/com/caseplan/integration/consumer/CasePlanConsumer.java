package com.caseplan.integration.consumer;

import com.caseplan.core.entity.*;
import com.caseplan.integration.llm.LLMService;
import com.caseplan.core.repo.CasePlanRepo;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class CasePlanConsumer {

    private final CasePlanRepo casePlanRepo;
    private final StringRedisTemplate redisTemplate;
    private final LLMService llmService;

    private static final String QUEUE_KEY = "caseplan:pending";
    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_BASE_MS = 1000L;
    /** Block up to 60s when queue is empty (BLPOP); then loop. No busy polling. */
    private static final long BLPOP_TIMEOUT_SECONDS = 60L;
    /** Sleep (ms) before retrying when Redis/IO throws — avoid hammering on disconnect. */
    private static final long REDIS_ERROR_BACKOFF_MS = 5000L;
    /** Stale recovery: processing records older than this are re-queued on startup. */
    private static final long STALE_PROCESSING_MINUTES = 10L;
    /** Reconcile DB pending records back to queue to recover from queue loss/crash. */
    private static final long PENDING_RECONCILE_INTERVAL_SECONDS = 60L;

    @PostConstruct
    public void startWorker() {
        recoverStaleProcessing();
        recoverLostPendingQueueItems();
        Thread reconcileWorker = new Thread(new Runnable() {
            @Override
            public void run() {
                runReconcileLoop();
            }
        }, "caseplan-reconcile");
        reconcileWorker.setDaemon(false);
        reconcileWorker.start();

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runWorker();
            }
        }, "caseplan-consumer");
        worker.setDaemon(false);
        worker.start();
    }

    /** Orphan recovery: re-queue CasePlans stuck in processing (e.g. process died). */
    @SuppressWarnings("null")
    private void recoverStaleProcessing() {
        Instant cutoff = Instant.now().minusSeconds(STALE_PROCESSING_MINUTES * 60);
        List<CasePlan> stale = casePlanRepo.findByStatusAndUpdatedAtBefore("processing", cutoff);
      for (CasePlan plan : stale) {
        plan.setStatus("pending");
        casePlanRepo.save(plan);
        redisTemplate.opsForList().rightPush(QUEUE_KEY, plan.getId().toString());
      }
    }

    /** DB->Queue reconciliation: ensure pending records exist in Redis queue. */
    private void recoverLostPendingQueueItems() {
        List<CasePlan> pending = casePlanRepo.findByStatus("pending");
        if (pending.isEmpty()) {
            return;
        }

        List<String> queued = redisTemplate.opsForList().range(QUEUE_KEY, 0, -1);
        Set<String> queuedIds = queued != null ? new HashSet<>(queued) : new HashSet<>();
        for (CasePlan plan : pending) {
            if (plan.getId() == null) {
                continue;
            }
            String idStr = plan.getId().toString();
            if (queuedIds.contains(idStr)) {
                continue;
            }
            redisTemplate.opsForList().rightPush(QUEUE_KEY, idStr);
            queuedIds.add(idStr);
        }
    }

    /** Blocking loop: BLPOP until a task id is available, then process with retry. */
    private void runWorker() {
        while (true) {
            try {
                String idStr = redisTemplate.opsForList().leftPop(QUEUE_KEY, BLPOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (idStr == null) {
                    continue; // timeout, queue was empty
                }
                processWithRetry(idStr);
            } catch (Exception e) {
                try {
                    Thread.sleep(REDIS_ERROR_BACKOFF_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /** Periodic safety net for Redis data loss: requeue pending records every minute. */
    private void runReconcileLoop() {
        while (true) {
            try {
                recoverLostPendingQueueItems();
                Thread.sleep(TimeUnit.SECONDS.toMillis(PENDING_RECONCILE_INTERVAL_SECONDS));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                try {
                    Thread.sleep(REDIS_ERROR_BACKOFF_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /** Process one task: up to 3 attempts with exponential backoff (1s, 2s before 2nd and 3rd). */
    private void processWithRetry(String idStr) {
        Long id = Long.parseLong(idStr);
        Optional<CasePlan> optional = casePlanRepo.findById(id);
        if (optional.isEmpty()) {
            return;
        }

        CasePlan casePlan = optional.get();
        if (!"pending".equals(casePlan.getStatus())) {
            return;
        }
        casePlan.setStatus("processing");
        casePlanRepo.save(casePlan);

        try {
            Exception lastException = null;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    String plan = generatePlanWithLLM(casePlan.getCaseInfo());
                    casePlan.setGeneratedPlan(plan);
                    casePlan.setStatus("completed");
                    casePlan.setErrorMessage(null);
                    casePlanRepo.save(casePlan);
                    return;
                } catch (Exception e) {
                    lastException = e;
                    if (attempt < MAX_ATTEMPTS) {
                        long delayMs = BACKOFF_BASE_MS * (1L << (attempt - 1));
                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            casePlan.setStatus("failed");
                            casePlan.setErrorMessage(ie.getMessage());
                            casePlanRepo.save(casePlan);
                            return;
                        }
                    }
                }
            }

            casePlan.setStatus("failed");
            casePlan.setErrorMessage(lastException.getMessage());
            casePlanRepo.save(casePlan);
        } finally {
            if ("processing".equals(casePlan.getStatus())) {
                casePlan.setStatus("failed");
                casePlan.setErrorMessage("Worker crashed or unexpected error");
                casePlanRepo.save(casePlan);
            }
        }
    }

    private String generatePlanWithLLM(CaseInfo caseInfo) {
        String today = LocalDate.now().toString();
        Client client = caseInfo.getClient();
        Attorney attorney = caseInfo.getAttorney();

        String additionalCauses = Optional.ofNullable(caseInfo.getAdditionalCauses()).orElse("None");
        String priorLegalActions = Optional.ofNullable(caseInfo.getPriorLegalActions()).orElse("None");
        String caseDocuments = Optional.ofNullable(caseInfo.getCaseDocuments()).orElse("None provided");
        String caseNumber = Optional.ofNullable(caseInfo.getCaseNumber())
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse("Not assigned yet");

        String promptStr =
                "You are a legal assistant. Generate a Legal Service Plan based on the following case information.\n\n"
                        + "IMPORTANT: Use today's date as the \"Date of Plan\" at the top of the document. Today's date is: " + today + "\n\n"
                        + "The plan MUST contain these four sections:\n"
                        + "1. **Problem List** — Summary of legal issues and key disputes\n"
                        + "2. **Goals** — Expected legal outcomes\n"
                        + "3. **Attorney Interventions** — Specific legal actions recommended\n"
                        + "4. **Monitoring Plan** — Key dates, follow-ups, and materials needed\n\n"
                        + "--- Case Information ---\n"
                        + "Client Name: " + client.getFirstName() + " " + client.getLastName() + "\n"
                        + "Referring Attorney: " + attorney.getName() + " (Bar #: " + attorney.getBarNumber() + ")\n"
                        + "Case Number: " + caseNumber + "\n"
                        + "Primary Cause of Action: " + caseInfo.getPrimaryCauseOfAction() + "\n"
                        + "Legal Remedy Sought: " + caseInfo.getLegalRemedySought() + "\n"
                        + "Additional Causes: " + additionalCauses + "\n"
                        + "Prior Legal Actions: " + priorLegalActions + "\n"
                        + "Case Documents/Notes:\n" + caseDocuments + "\n\n"
                        + "Generate the Legal Service Plan now. Begin with \"Date of Plan: " + today + "\" (use this exact date).\n";

        return llmService.chat(promptStr);
    }
}
