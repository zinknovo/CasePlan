package com.caseplan.adapter.out.llm;

import com.caseplan.application.port.out.ChatMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * LLM service for Anthropic Claude (Messages API). Uses x-api-key and anthropic-version headers,
 * and hoists the system prompt out of the message list into a top-level "system" field.
 */
public class AnthropicService extends HttpLLMService {

    private static final String ANTHROPIC_BASE_URL = "https://api.anthropic.com/v1";
    // Anthropic custom headers: not in the IDE's known-header list, so the check is suppressed per constant.
    //noinspection UastIncorrectHttpHeaderInspection
    private static final String HEADER_X_API_KEY = "x-api-key";
    //noinspection UastIncorrectHttpHeaderInspection
    private static final String HEADER_ANTHROPIC_VERSION = "anthropic-version";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    public AnthropicService(
            RestTemplate restTemplate,
            String apiKey,
            String configuredModel,
            long modelRefreshSeconds,
            int maxTokens) {
        super(restTemplate, apiKey, "llm.anthropic.api-key", configuredModel, modelRefreshSeconds, maxTokens);
    }

    @Override
    protected String providerLabel() {
        return "Claude";
    }

    @Override
    protected String modelProperty() {
        return "llm.anthropic.model";
    }

    @Override
    protected String modelsUrl() {
        return ANTHROPIC_BASE_URL + "/models";
    }

    @Override
    protected String chatUrl() {
        return ANTHROPIC_BASE_URL + "/messages";
    }

    @Override
    protected List<String> modelTimeFields() {
        return Arrays.asList("created_at", "created", "release_date");
    }

    @Override
    protected void applyAuthHeaders(HttpHeaders headers) {
        headers.set(HEADER_X_API_KEY, requireApiKey());
        headers.set(HEADER_ANTHROPIC_VERSION, ANTHROPIC_VERSION);
    }

    @Override
    protected String doChat(List<ChatMessage> messages) {
        HttpHeaders headers = jsonHeaders();

        // Claude takes the system prompt as a top-level field, not as a message.
        String systemPrompt = null;
        List<Map<String, String>> apiMessages = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (ChatMessage.ROLE_SYSTEM.equals(message.getRole())) {
                if (systemPrompt == null) {
                    systemPrompt = message.getContent();
                }
                continue;
            }
            apiMessages.add(toApiMessage(message));
        }

        Map<String, Object> body = newChatBody(resolveModel(headers), apiMessages);
        if (systemPrompt != null) {
            body.put("system", systemPrompt);
        }

        return firstTextBlock(postChat(headers, body));
    }

    /** Claude replies with content[] blocks; the text lives in the first {"type":"text"} block. */
    @SuppressWarnings("unchecked")
    private String firstTextBlock(Map<String, Object> responseBody) {
        List<Map<String, Object>> content = (List<Map<String, Object>>) responseBody.get("content");
        if (content == null || content.isEmpty()) {
            return "";
        }
        for (Map<String, Object> block : content) {
            if ("text".equals(block.get("type"))) {
                Object text = block.get("text");
                return text != null ? text.toString() : "";
            }
        }
        return "";
    }
}
