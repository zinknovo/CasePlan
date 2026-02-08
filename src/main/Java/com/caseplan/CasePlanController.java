package com.caseplan;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/caseplans")
@RequiredArgsConstructor
public class CasePlanController {

    private final CasePlanRepo repo;
    private final RestTemplate rest = new RestTemplate();

    @Value("${deepseek.api-key}")
    private String apiKey;

    // GET /api/caseplans — list all
    @GetMapping
    public List<CasePlan> list() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    // GET /api/caseplans/{id} — get one
    @GetMapping("/{id}")
    public ResponseEntity<CasePlan> get(@PathVariable @NonNull Long id) {
        return repo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/caseplans — create + generate (sync)
    @PostMapping
    public ResponseEntity<CasePlan> create(@RequestBody CasePlan input) {
        // 1. Save as pending
        input.setStatus("pending");
        CasePlan saved = repo.save(input);

        // 2. Set to processing
        saved.setStatus("processing");
        repo.save(saved);

        // 3. Call LLM (sync — intentionally blocking)
        try {
            String plan = callDeepSeek(saved);
            saved.setGeneratedPlan(plan);
            saved.setStatus("completed");
        } catch (Exception e) {
            saved.setStatus("failed");
            saved.setErrorMessage(e.getMessage());
        }

        // 4. Save final state
        repo.save(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    private String callDeepSeek(CasePlan cp) {
        String today = LocalDate.now().toString();
        String prompt = String.format(
                "You are a legal assistant. Generate a Legal Service Plan based on the following case information.\n\n"
                + "IMPORTANT: Use today's date as the \"Date of Plan\" at the top of the document. Today's date is: %s\n\n"
                + "The plan MUST contain these four sections:\n"
                + "1. **Problem List** — Summary of legal issues and key disputes\n"
                + "2. **Goals** — Expected legal outcomes\n"
                + "3. **Attorney Interventions** — Specific legal actions recommended\n"
                + "4. **Monitoring Plan** — Key dates, follow-ups, and materials needed\n\n"
                + "--- Case Information ---\n"
                + "Client Name: %s %s\n"
                + "Referring Attorney: %s (Bar #: %s)\n"
                + "Case Number: %s\n"
                + "Primary Cause of Action: %s\n"
                + "Legal Remedy Sought: %s\n"
                + "Additional Causes: %s\n"
                + "Prior Legal Actions: %s\n"
                + "Case Documents/Notes:\n%s\n\n"
                + "Generate the Legal Service Plan now. Begin with \"Date of Plan: %s\" (use this exact date).\n",
                today,
                cp.getClientFirstName(), cp.getClientLastName(),
                cp.getReferringAttorney(), cp.getBarNumber(),
                cp.getCaseNumber(),
                cp.getPrimaryCauseOfAction(),
                cp.getLegalRemedySought(),
                Optional.ofNullable(cp.getAdditionalCauses()).orElse("None"),
                Optional.ofNullable(cp.getPriorLegalActions()).orElse("None"),
                Optional.ofNullable(cp.getCaseDocuments()).orElse("None provided"),
                today
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(Objects.requireNonNull(apiKey, "deepseek.api-key is required"));

        Map<String, Object> body = Map.of(
                "model", "deepseek-chat",
                "max_tokens", 2000,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "https://api.deepseek.com/v1/chat/completions",
                Objects.requireNonNull(HttpMethod.POST),
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) {
            throw new IllegalStateException("DeepSeek API returned null body");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("DeepSeek API returned no choices");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        Object content = message != null ? message.get("content") : null;
        return content != null ? content.toString() : "";
    }
}
