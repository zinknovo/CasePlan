package com.caseplan.adapter.out.llm;

import com.caseplan.application.port.out.ChatMessage;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * LLM service for Anthropic Claude (Messages API). Uses x-api-key and anthropic-version headers.
 */
public class ClaudeService extends BaseLLMService {

    private static final String CLAUDE_BASE_URL = "https://api.anthropic.com/v1";
    private static final String HEADER_X_API_KEY = "x-api-key";
    private static final String HEADER_ANTHROPIC_VERSION = "anthropic-version";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String configuredModel;
    private final long modelRefreshSeconds;
    private final int maxTokens;
    private final ModelCache modelCache = new ModelCache();

    public ClaudeService(
            RestTemplate restTemplate,
            String apiKey,
            String configuredModel,
            long modelRefreshSeconds,
            int maxTokens) {
        this.restTemplate = restTemplate;
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
        headers.set(HEADER_X_API_KEY, Objects.requireNonNull(apiKey, "llm.claude.api-key is required"));
        headers.set(HEADER_ANTHROPIC_VERSION, ANTHROPIC_VERSION);

        List<Map<String, String>> apiMessages = new ArrayList<>();
        String systemPrompt = null;
        for (ChatMessage message : messages) {
            if (ChatMessage.ROLE_SYSTEM.equals(message.getRole())) {
                if (systemPrompt == null) {
                    systemPrompt = message.getContent();
                }
                continue;
            }
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
                CLAUDE_BASE_URL + "/models",
                "Claude models API returned null body",
                "Claude models API returned no models; set llm.claude.model explicitly",
                "No valid Claude model id from models API",
                Arrays.asList("created_at", "created", "release_date")
        );
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("messages", apiMessages);
        if (systemPrompt != null) {
            body.put("system", systemPrompt);
        }

        String url = CLAUDE_BASE_URL + "/messages";
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        Map<String, Object> responseBody = exchangeForMapBody(
                restTemplate,
                url,
                HttpMethod.POST,
                request,
                "Claude API returned null body"
        );

        List<Map<String, Object>> content = (List<Map<String, Object>>) responseBody.get("content");
        if (content == null || content.isEmpty()) {
            return "";
        }

        // Claude returns content[] with type "text" and "text" field
        for (Map<String, Object> block : content) {
            if ("text".equals(block.get("type"))) {
                Object text = block.get("text");
                return text != null ? text.toString() : "";
            }
        }
        return "";
    }
}
