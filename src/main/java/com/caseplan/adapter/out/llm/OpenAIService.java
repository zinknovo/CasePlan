package com.caseplan.adapter.out.llm;

import com.caseplan.application.port.out.ChatMessage;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * LLM service for OpenAI-compatible APIs: OpenAI, DeepSeek, or any endpoint that follows
 * the OpenAI chat completions format (POST with messages[], returns choices[].message.content).
 */
public class OpenAIService extends BaseLLMService {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String apiKey;
    private final String configuredModel;
    private final long modelRefreshSeconds;
    private final int maxTokens;
    private final ModelCache modelCache = new ModelCache();

    public OpenAIService(
            RestTemplate restTemplate,
            String baseUrl,
            String apiKey,
            String configuredModel,
            long modelRefreshSeconds,
            int maxTokens) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.apiKey = apiKey;
        this.configuredModel = configuredModel;
        this.modelRefreshSeconds = modelRefreshSeconds;
        this.maxTokens = maxTokens;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected String doChat(List<ChatMessage> messages) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(Objects.requireNonNull(apiKey, "llm.openai.api-key is required"));

        List<Map<String, String>> apiMessages = new ArrayList<>();
        for (ChatMessage message : messages) {
            Map<String, String> map = new HashMap<>();
            map.put("role", message.getRole());
            map.put("content", message.getContent());
            apiMessages.add(map);
        }

        String model = resolveModel(
                headers,
                restTemplate,
                configuredModel,
                modelRefreshSeconds,
                modelCache,
                baseUrl + "models",
                "LLM models API returned null body",
                "LLM models API returned no models; set llm.openai.model explicitly",
                "No valid model id from models API",
                Arrays.asList("created", "created_at", "release_date")
        );
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("messages", apiMessages);

        String url = baseUrl + "chat/completions";
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        Map<String, Object> responseBody = exchangeForMapBody(
                restTemplate,
                url,
                HttpMethod.POST,
                request,
                "LLM API returned null body"
        );

        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("LLM API returned no choices");
        }

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) {
            return "";
        }
        Object content = message.get("content");
        return content != null ? content.toString() : "";
    }
}
