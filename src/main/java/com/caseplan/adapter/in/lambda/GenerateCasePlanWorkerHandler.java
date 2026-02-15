package com.caseplan.adapter.in.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.caseplan.application.service.CasePlanGenerationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * SQS worker: consumes planId and generates case plan content via LLM.
 */
public class GenerateCasePlanWorkerHandler implements RequestHandler<SQSEvent, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CasePlanGenerationService generationService;

    public GenerateCasePlanWorkerHandler() {
        ConfigurableApplicationContext ctx = LambdaSpringContext.getContext();
        this.generationService = ctx.getBean(CasePlanGenerationService.class);
    }

    GenerateCasePlanWorkerHandler(CasePlanGenerationService generationService) {
        this.generationService = generationService;
    }

    @Override
    public String handleRequest(SQSEvent event, Context context) {
        long start = System.currentTimeMillis();

        if (event == null || event.getRecords() == null || event.getRecords().isEmpty()) {
            return "no records";
        }

        int success = 0;
        int skipped = 0;
        int failed = 0;

        try {
            for (SQSEvent.SQSMessage msg : event.getRecords()) {
                try {
                    Long planId = extractPlanId(msg.getBody());
                    if (planId == null) {
                        skipped++;
                        continue;
                    }
                    if (generationService.processWithRetry(planId)) {
                        success++;
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    failed++;
                    throw new RuntimeException("Failed processing SQS record: " + e.getMessage(), e);
                }
            }

            return "success=" + success + ", skipped=" + skipped + ", failed=" + failed;
        } finally {
            long duration = System.currentTimeMillis() - start;
            CloudWatchEmf.record()
                    .dimension("handler", "Worker")
                    .count("PlansProcessed", success)
                    .count("PlansFailed", failed)
                    .count("PlansSkipped", skipped)
                    .millis("HandlerDuration", duration)
                    .emit();
        }
    }

    private Long extractPlanId(String body) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        try {
            JsonNode n = MAPPER.readTree(body);
            JsonNode idNode = n.get("planId");
            if (idNode == null || idNode.isNull()) {
                return null;
            }
            return idNode.isNumber() ? idNode.longValue() : Long.parseLong(idNode.asText());
        } catch (Exception e) {
            return null;
        }
    }

}
