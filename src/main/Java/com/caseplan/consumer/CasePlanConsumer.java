package com.caseplan.consumer;

import com.caseplan.entity.*;
import com.caseplan.repo.CasePlanRepo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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
    private final RestTemplate rest = new RestTemplate();

    private static final String QUEUE_KEY = "caseplan:pending";
    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_BASE_MS = 1000L;
    /** Block up to 60s when queue is empty (BLPOP); then loop. No busy polling. */
    private static final long BLPOP_TIMEOUT_SECONDS = 60L;
    /** Sleep (ms) before retrying when Redis/IO throws — avoid hammering on disconnect. */
    private static final long REDIS_ERROR_BACKOFF_MS = 5000L;
    /** Stale recovery: processing records older than this are re-queued on startup. */
    private static final long STALE_PROCESSING_MINUTES = 10L;

    @Value("${deepseek.api-key}")
    private String apiKey;

    @PostConstruct
    public void startWorker() {
        recoverStaleProcessing();
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
        for (int i = 0; i < stale.size(); i++) {
            CasePlan plan = stale.get(i);
            plan.setStatus("pending");
            casePlanRepo.save(plan);
            redisTemplate.opsForList().rightPush(QUEUE_KEY, plan.getId().toString());
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

    /** Process one task: up to 3 attempts with exponential backoff (1s, 2s before 2nd and 3rd). */
    private void processWithRetry(String idStr) {
        Long id = Long.parseLong(idStr);
        Optional<CasePlan> optional = casePlanRepo.findById(id);
        if (!optional.isPresent()) {
            return;
        }

        CasePlan casePlan = optional.get();
        casePlan.setStatus("processing");
        casePlanRepo.save(casePlan);

        try {
            Exception lastException = null;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    String plan = callDeepSeek(casePlan.getCaseInfo());
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
            casePlan.setErrorMessage(lastException != null ? lastException.getMessage() : "Unknown error");
            casePlanRepo.save(casePlan);
        } finally {
            if (casePlan != null && "processing".equals(casePlan.getStatus())) {
                casePlan.setStatus("failed");
                casePlan.setErrorMessage("Worker crashed or unexpected error");
                casePlanRepo.save(casePlan);
            }
        }
    }

    private String callDeepSeek(CaseInfo caseInfo) {
        String today = LocalDate.now().toString();
        Client client = caseInfo.getClient();
        Attorney attorney = caseInfo.getAttorney();

        String additionalCauses = Optional.ofNullable(caseInfo.getAdditionalCauses()).orElse("None");
        String priorLegalActions = Optional.ofNullable(caseInfo.getPriorLegalActions()).orElse("None");
        String caseDocuments = Optional.ofNullable(caseInfo.getCaseDocuments()).orElse("None provided");

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
                        + "Case Number: " + caseInfo.getCaseNumber() + "\n"
                        + "Primary Cause of Action: " + caseInfo.getPrimaryCauseOfAction() + "\n"
                        + "Legal Remedy Sought: " + caseInfo.getLegalRemedySought() + "\n"
                        + "Additional Causes: " + additionalCauses + "\n"
                        + "Prior Legal Actions: " + priorLegalActions + "\n"
                        + "Case Documents/Notes:\n" + caseDocuments + "\n\n"
                        + "Generate the Legal Service Plan now. Begin with \"Date of Plan: " + today + "\" (use this exact date).\n";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(Objects.requireNonNull(apiKey, "deepseek.api-key is required"));

        // build request body
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", promptStr);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "deepseek-chat");
        body.put("max_tokens", 4000);
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message);
        body.put("messages", messages);

        // send POST request to DeepSeek API
        HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(body, headers);
        ResponseEntity<Map<String, Object>> httpResponse = rest.exchange(
                "https://api.deepseek.com/v1/chat/completions",
                Objects.requireNonNull(HttpMethod.POST),
                httpRequest,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        // parse response
        Map<String, Object> responseBody = httpResponse.getBody();
        if (responseBody == null) {
            throw new IllegalStateException("DeepSeek API returned null body");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("DeepSeek API returned no choices");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> reply = (Map<String, Object>) choices.get(0).get("message");
        if (reply == null) {
            return "";
        }

        Object content = reply.get("content");
        return content != null ? content.toString() : "";
    }
}
