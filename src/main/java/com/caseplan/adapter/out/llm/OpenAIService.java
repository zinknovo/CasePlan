package com.caseplan.adapter.out.llm;

import com.caseplan.application.port.out.ChatMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * LLM service for OpenAI-compatible APIs: OpenAI, DeepSeek, or any endpoint that follows
 * the OpenAI chat completions format (POST with messages[], returns choices[].message.content).
 */
public class OpenAIService extends HttpLLMService {

    private final String baseUrl;
    private final String configuredThinkingType;
    private final String configuredReasoningEffort;

    public OpenAIService(
            RestTemplate restTemplate,
            String baseUrl,
            String apiKey,
            String configuredModel,
            String configuredThinkingType,
            String configuredReasoningEffort,
            long modelRefreshSeconds,
            int maxTokens) {
        super(restTemplate, apiKey, "llm.openai.api-key", configuredModel, modelRefreshSeconds, maxTokens);
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.configuredThinkingType = configuredThinkingType;
        this.configuredReasoningEffort = configuredReasoningEffort;
    }

    /** Any OpenAI-compatible endpoint can sit behind this class, so errors stay vendor-neutral. */
    @Override
    protected String providerLabel() {
        return "LLM";
    }

    @Override
    protected String modelProperty() {
        return "llm.openai.model";
    }

    @Override
    protected String modelsUrl() {
        return baseUrl + "models";
    }

    @Override
    protected String chatUrl() {
        return baseUrl + "chat/completions";
    }

    @Override
    protected List<String> modelTimeFields() {
        return Arrays.asList("created", "created_at", "release_date");
    }

    @Override
    protected void applyAuthHeaders(HttpHeaders headers) {
        headers.setBearerAuth(requireApiKey());
    }

    @Override
    protected String doChat(List<ChatMessage> messages) {
        HttpHeaders headers = jsonHeaders();
        String model = resolveModel(headers);

        Map<String, Object> body = newChatBody(model, toApiMessages(messages));
        addDeepSeekThinkingOptions(body, model);

        return firstChoiceContent(postChat(headers, body));
    }

    @SuppressWarnings("unchecked")
    private String firstChoiceContent(Map<String, Object> responseBody) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException(providerLabel() + " API returned no choices");
        }

        Map<String, Object> message = (Map<String, Object>) choices.getFirst().get("message");
        if (message == null) {
            return "";
        }
        Object content = message.get("content");
        return content != null ? content.toString() : "";
    }

    private void addDeepSeekThinkingOptions(Map<String, Object> body, String model) {
        if (!isDeepSeekBaseUrl() || model == null || !model.startsWith("deepseek-v4")) {
            return;
        }

        String thinkingType = trimToNull(configuredThinkingType);
        if (thinkingType == null) {
            thinkingType = "disabled";
        }
        body.put("thinking", Map.of("type", thinkingType));

        String reasoningEffort = trimToNull(configuredReasoningEffort);
        if (reasoningEffort != null) {
            body.put("reasoning_effort", reasoningEffort);
        }
    }

    private boolean isDeepSeekBaseUrl() {
        return baseUrl.contains("api.deepseek.com");
    }
}
