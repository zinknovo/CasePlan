package com.caseplan.adapter.out.llm;

import com.caseplan.application.port.out.ChatMessage;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Base for LLM providers reached over HTTP. Owns everything such providers share: the
 * RestTemplate, the API key, model resolution (including the "newest model" cache), and the
 * request/response plumbing. Subclasses only describe what is provider-specific -- endpoints,
 * auth headers, and how to read the reply out of the response body.
 */
public abstract class HttpLLMService extends BaseLLMService {

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String apiKeyProperty;
    private final String configuredModel;
    private final long modelRefreshSeconds;
    private final int maxTokens;

    private final Object modelCacheLock = new Object();
    private volatile String cachedResolvedModel;
    private volatile long modelCacheExpiresAtMs;

    protected HttpLLMService(
            RestTemplate restTemplate,
            String apiKey,
            String apiKeyProperty,
            String configuredModel,
            long modelRefreshSeconds,
            int maxTokens) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.apiKeyProperty = apiKeyProperty;
        this.configuredModel = configuredModel;
        this.modelRefreshSeconds = modelRefreshSeconds;
        this.maxTokens = maxTokens;
    }

    // ==================== provider description ====================

    /** Provider name used in error messages, e.g. "Claude" for Anthropic. */
    protected abstract String providerLabel();

    /** Config property that supplies the model id, named in the "set it explicitly" error. */
    protected abstract String modelProperty();

    /** Endpoint that lists the models this provider offers. */
    protected abstract String modelsUrl();

    /** Endpoint that completes a chat. */
    protected abstract String chatUrl();

    /**
     * Response fields to try, in order, when dating a model so the newest one can be picked.
     * Providers disagree on the field name, so each states its own preference.
     */
    protected abstract List<String> modelTimeFields();

    /** Adds this provider's authentication headers. */
    protected abstract void applyAuthHeaders(HttpHeaders headers);

    // ==================== shared request building ====================

    /** JSON headers with the provider's auth applied. Fails fast if the API key is missing. */
    protected final HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        applyAuthHeaders(headers);
        return headers;
    }

    protected final String requireApiKey() {
        return Objects.requireNonNull(apiKey, apiKeyProperty + " is required");
    }

    /** The {@code model} / {@code max_tokens} / {@code messages} body both providers start from. */
    protected final Map<String, Object> newChatBody(String model, List<Map<String, String>> apiMessages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("messages", apiMessages);
        return body;
    }

    /** One message in the {@code {role, content}} shape both providers' wire formats use. */
    protected static Map<String, String> toApiMessage(ChatMessage message) {
        Map<String, String> map = new HashMap<>();
        map.put("role", message.getRole());
        map.put("content", message.getContent());
        return map;
    }

    /** All messages, in order, in wire format. */
    protected static List<Map<String, String>> toApiMessages(List<ChatMessage> messages) {
        List<Map<String, String>> apiMessages = new ArrayList<>();
        for (ChatMessage message : messages) {
            apiMessages.add(toApiMessage(message));
        }
        return apiMessages;
    }

    protected static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ==================== shared HTTP plumbing ====================

    protected final Map<String, Object> postChat(HttpHeaders headers, Map<String, Object> body) {
        return exchangeForMapBody(
                chatUrl(),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                providerLabel() + " API returned null body"
        );
    }

    protected final Map<String, Object> exchangeForMapBody(
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

    // ==================== model resolution ====================

    /**
     * Returns the configured model, or -- when none is configured -- the newest model the
     * provider advertises. Resolved ids are cached for {@code modelRefreshSeconds}.
     */
    protected final String resolveModel(HttpHeaders headers) {
        String explicit = trimToNull(configuredModel);
        if (explicit != null) {
            return explicit;
        }

        long now = System.currentTimeMillis();
        String cached = cachedResolvedModel;
        if (cached != null && now < modelCacheExpiresAtMs) {
            return cached;
        }

        synchronized (modelCacheLock) {
            now = System.currentTimeMillis();
            if (cachedResolvedModel != null && now < modelCacheExpiresAtMs) {
                return cachedResolvedModel;
            }

            Map<String, Object> responseBody = exchangeForMapBody(
                    modelsUrl(),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    providerLabel() + " models API returned null body"
            );

            String latest = newestModelId(responseBody);
            long refreshSeconds = Math.max(modelRefreshSeconds, 1L);
            cachedResolvedModel = latest;
            modelCacheExpiresAtMs = now + (refreshSeconds * 1000L);
            return latest;
        }
    }

    @SuppressWarnings("unchecked")
    private String newestModelId(Map<String, Object> responseBody) {
        List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
        if (data == null || data.isEmpty()) {
            throw new IllegalStateException(
                    providerLabel() + " models API returned no models; set " + modelProperty() + " explicitly");
        }

        ModelCandidate best = null;
        for (Map<String, Object> row : data) {
            ModelCandidate candidate = toCandidate(row);
            if (candidate == null) {
                continue;
            }
            if (best == null || candidate.isNewerThan(best)) {
                best = candidate;
            }
        }

        if (best == null) {
            throw new IllegalStateException("No valid " + providerLabel() + " model id from models API");
        }
        return best.modelId();
    }

    private ModelCandidate toCandidate(Map<String, Object> row) {
        Object idObj = row.get("id");
        if (idObj == null) {
            return null;
        }
        String id = idObj.toString().trim();
        if (id.isEmpty()) {
            return null;
        }

        long sortKey = Long.MIN_VALUE;
        for (String key : modelTimeFields()) {
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

    private record ModelCandidate(String modelId, long sortKey) {

        /** Newer wins; ties break on the lexicographically later id so the pick is deterministic. */
        boolean isNewerThan(ModelCandidate other) {
            if (sortKey != other.sortKey) {
                return sortKey > other.sortKey;
            }
            return modelId.compareTo(other.modelId) > 0;
        }
    }
}
