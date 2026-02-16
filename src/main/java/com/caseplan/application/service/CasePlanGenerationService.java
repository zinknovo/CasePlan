package com.caseplan.application.service;

import com.caseplan.domain.model.Attorney;
import com.caseplan.domain.model.CaseInfo;
import com.caseplan.domain.model.CasePlan;
import com.caseplan.domain.model.Client;
import com.caseplan.adapter.out.persistence.CasePlanRepo;
import com.caseplan.application.port.out.LLMService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CasePlanGenerationService {

    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_BASE_MS = 1000L;

    private final CasePlanRepo casePlanRepo;
    private final LLMService llmService;

    public boolean processWithRetry(Long id) {
        Optional<CasePlan> optional = casePlanRepo.findById(id);
        if (optional.isEmpty()) {
            return false;
        }

        CasePlan casePlan = optional.get();
        String currentStatus = casePlan.getStatus();
        if ("completed".equals(currentStatus) || "failed".equals(currentStatus)) {
            return false;
        }
        if (!"pending".equals(currentStatus) && !"processing".equals(currentStatus)) {
            return false;
        }
        if ("pending".equals(currentStatus)) {
            casePlan.setStatus("processing");
            casePlanRepo.save(casePlan);
        }

        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String generated = generatePlanWithLLM(casePlan.getCaseInfo());
                casePlan.setGeneratedPlan(generated);
                casePlan.setStatus("completed");
                casePlan.setErrorMessage(null);
                casePlanRepo.save(casePlan);
                return true;
            } catch (Exception e) {
                last = e;
                if (attempt < MAX_ATTEMPTS) {
                    sleep(BACKOFF_BASE_MS * (1L << (attempt - 1)));
                }
            }
        }

        casePlan.setStatus("failed");
        casePlan.setErrorMessage(last != null ? last.getMessage() : "unknown error");
        casePlanRepo.save(casePlan);
        return false;
    }

    private String generatePlanWithLLM(CaseInfo caseInfo) {
        String today = LocalDate.now().toString();
        Client client = caseInfo.getClient();
        Attorney attorney = caseInfo.getAttorney();

        String additionalCauses = optional(caseInfo.getAdditionalCauses(), "None");
        String priorLegalActions = optional(caseInfo.getPriorLegalActions(), "None");
        String caseDocuments = optional(caseInfo.getCaseDocuments(), "None provided");
        String serviceNumber = optional(caseInfo.getServiceNumber(), "Not assigned yet");
        String docketNumber = optional(caseInfo.getCaseNumber(), "Not provided");

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
                        + "Service Number: " + serviceNumber + "\n"
                        + "Docket Number: " + docketNumber + "\n"
                        + "Primary Cause of Action: " + caseInfo.getPrimaryCauseOfAction() + "\n"
                        + "Remedy Sought: " + caseInfo.getRemedySought() + "\n"
                        + "Additional Causes: " + additionalCauses + "\n"
                        + "Prior Legal Actions: " + priorLegalActions + "\n"
                        + "Case Documents/Notes:\n" + caseDocuments + "\n\n"
                        + "Generate the Legal Service Plan now. Begin with \"Date of Plan: " + today + "\" (use this exact date).\n";

        return llmService.chat(promptStr);
    }

    private String optional(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted", e);
        }
    }
}
