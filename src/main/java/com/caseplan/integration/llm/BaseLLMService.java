package com.caseplan.integration.llm;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Abstract base for LLM implementations. Subclasses implement the provider-specific HTTP call;
 * business code uses {@link LLMService} and does not depend on this class.
 */
public abstract class BaseLLMService implements LLMService {

    @Override
    public String chat(String userMessage) {
        ChatMessage message = ChatMessage.user(userMessage);
        List<ChatMessage> messages = Collections.singletonList(message);
        return chat(messages);
    }

    @Override
    public String chat(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be null or empty");
        }
        return doChat(messages);
    }

    /**
     * Provider-specific implementation: send messages to the LLM API and return the reply text.
     */
    protected abstract String doChat(List<ChatMessage> messages);

    protected Map<String, Object> exchangeForMapBody(
            RestTemplate restTemplate,
            String url,
            HttpMethod method,
            HttpEntity<?> request,
            String nullBodyErrorMessage) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                method,
                request,
                new ParameterizedTypeReference<>() {
                }
        );
        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) {
            throw new IllegalStateException(nullBodyErrorMessage);
        }
        return responseBody;
    }

    protected String resolveModel(
            HttpHeaders headers,
            RestTemplate restTemplate,
            String configuredModel,
            long modelRefreshSeconds,
            ModelCache modelCache,
            String modelsUrl,
            String nullBodyErrorMessage,
            String noModelsErrorMessage,
            String noValidModelErrorMessage,
            List<String> timeFieldPriority) {
        if (configuredModel != null && !configuredModel.trim().isEmpty()) {
            return configuredModel.trim();
        }

        long now = System.currentTimeMillis();
        String cached = modelCache.cachedResolvedModel;
        if (cached != null && now < modelCache.modelCacheExpiresAtMs) {
            return cached;
        }

        synchronized (modelCache.lock) {
            now = System.currentTimeMillis();
            if (modelCache.cachedResolvedModel != null && now < modelCache.modelCacheExpiresAtMs) {
                return modelCache.cachedResolvedModel;
            }

            HttpEntity<Void> request = new HttpEntity<>(headers);
            Map<String, Object> responseBody = exchangeForMapBody(
                    restTemplate,
                    modelsUrl,
                    HttpMethod.GET,
                    request,
                    nullBodyErrorMessage
            );

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
            if (data == null || data.isEmpty()) {
                throw new IllegalStateException(noModelsErrorMessage);
            }

            String latest = data.stream()
                    .map(row -> toCandidate(row, timeFieldPriority))
                    .filter(Objects::nonNull)
                    .max(Comparator.comparingLong(ModelCandidate::getSortKey).thenComparing(ModelCandidate::getModelId))
                    .map(ModelCandidate::getModelId)
                    .orElseThrow(() -> new IllegalStateException(noValidModelErrorMessage));

            long refreshSeconds = Math.max(modelRefreshSeconds, 1L);
            modelCache.cachedResolvedModel = latest;
            modelCache.modelCacheExpiresAtMs = now + (refreshSeconds * 1000L);
            return latest;
        }
    }

    private ModelCandidate toCandidate(Map<String, Object> row, List<String> timeFieldPriority) {
        Object idObj = row.get("id");
        if (idObj == null) {
            return null;
        }
        String id = idObj.toString().trim();
        if (id.isEmpty()) {
            return null;
        }

        long sortKey = Long.MIN_VALUE;
        for (String key : timeFieldPriority) {
            long value = parseTimeValue(row.get(key));
            if (value != Long.MIN_VALUE) {
                sortKey = value;
                break;
            }
        }
        return new ModelCandidate(id, sortKey);
    }

    private long parseTimeValue(Object value) {
        if (value == null) {
            return Long.MIN_VALUE;
        }
        if (value instanceof Number) {
            long ts = ((Number) value).longValue();
            return ts < 1_000_000_000_000L ? ts * 1000L : ts;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return Long.MIN_VALUE;
        }
        try {
            return Instant.parse(text).toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(text).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return Long.MIN_VALUE;
        }
    }

    protected static final class ModelCache {
        private final Object lock = new Object();
        private volatile String cachedResolvedModel;
        private volatile long modelCacheExpiresAtMs;
    }

    private static final class ModelCandidate {
        private final String modelId;
        private final long sortKey;

        private ModelCandidate(String modelId, long sortKey) {
            this.modelId = modelId;
            this.sortKey = sortKey;
        }

        private String getModelId() {
            return modelId;
        }

        private long getSortKey() {
            return sortKey;
        }
    }
}
