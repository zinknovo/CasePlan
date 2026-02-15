package com.caseplan.adapter.in.queue;

import com.caseplan.domain.model.CasePlan;
import com.caseplan.application.port.out.QueuePort;
import com.caseplan.adapter.out.persistence.CasePlanRepo;
import com.caseplan.application.service.CasePlanGenerationService;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "caseplan.consumer.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class CasePlanConsumer {

    private final CasePlanRepo casePlanRepo;
    private final StringRedisTemplate redisTemplate;
    private final QueuePort queuePort;
    private final CasePlanGenerationService generationService;

    private static final String QUEUE_KEY = "caseplan:pending";
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
            queuePort.enqueue(plan.getId().toString());
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
            queuePort.enqueue(idStr);
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
                generationService.processWithRetry(Long.parseLong(idStr));
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

}
