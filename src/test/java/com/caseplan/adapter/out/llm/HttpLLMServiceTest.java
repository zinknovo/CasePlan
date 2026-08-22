package com.caseplan.adapter.out.llm;

import com.caseplan.application.port.out.ChatMessage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class HttpLLMServiceTest {

    private static final String MODELS_URL = "https://api.example.com/models";

    @Mock private RestTemplate restTemplate;

    /** Concrete subclass to exercise the shared HTTP/model-resolution machinery. */
    private static class TestHttpLLMService extends HttpLLMService {

        TestHttpLLMService(RestTemplate restTemplate, String configuredModel, long modelRefreshSeconds) {
            this(restTemplate, "test-key", configuredModel, modelRefreshSeconds);
        }

        TestHttpLLMService(RestTemplate restTemplate, String apiKey, String configuredModel, long modelRefreshSeconds) {
            super(restTemplate, apiKey, "llm.test.api-key", configuredModel, modelRefreshSeconds, 4096);
        }

        @Override
        protected String providerLabel() {
            return "Test";
        }

        @Override
        protected String modelProperty() {
            return "llm.test.model";
        }

        @Override
        protected String modelsUrl() {
            return MODELS_URL;
        }

        @Override
        protected String chatUrl() {
            return "https://api.example.com/chat";
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
            return "test response";
        }

        // Expose the protected helpers for testing
        String testResolveModel() {
            return resolveModel(jsonHeaders());
        }

        Map<String, Object> testExchangeForMapBody() {
            return exchangeForMapBody(
                    "https://api.example.com/test", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), "null body error");
        }

        Map<String, Object> testPostChat(List<ChatMessage> messages) {
            HttpHeaders headers = jsonHeaders();
            return postChat(headers, newChatBody("a-model", toApiMessages(messages)));
        }
    }

    private TestHttpLLMService service;

    @SuppressWarnings("unchecked")
    private void mockModelsResponse(Map<String, Object> responseBody) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
    }

    private static Map<String, Object> modelsBody(Object data) {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("data", data);
        return responseBody;
    }

    private static Map<String, Object> model(String id, String timeField, Object time) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        if (timeField != null) {
            row.put(timeField, time);
        }
        return row;
    }

    // ==================== headers / api key ====================

    @Test
    public void jsonHeaders_appliesJsonContentTypeAndAuth() {
        service = new TestHttpLLMService(restTemplate, "model", 86400);

        HttpHeaders headers = service.jsonHeaders();

        assertEquals(MediaType.APPLICATION_JSON, headers.getContentType());
        assertEquals("Bearer test-key", headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    /** Unset {@code ${ENV:}} placeholders bind to "", so blank counts as missing just like null. */
    @Test
    public void requireApiKey_missingKey_errorNamesTheProperty() {
        for (String missing : Arrays.asList(null, "", "   ")) {
            service = new TestHttpLLMService(restTemplate, missing, "model", 86400);

            IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.jsonHeaders());
            assertTrue(error.getMessage().contains("llm.test.api-key"));
        }
    }

    @Test
    public void requireApiKey_trimsSurroundingWhitespace() {
        service = new TestHttpLLMService(restTemplate, "  padded-key  ", "model", 86400);

        assertEquals("Bearer padded-key", service.jsonHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }

    // ==================== message mapping / body ====================

    @Test
    public void toApiMessages_keepsOrderRoleAndContent() {
        List<Map<String, String>> apiMessages = HttpLLMService.toApiMessages(Arrays.asList(
                ChatMessage.system("be brief"),
                ChatMessage.user("hi")
        ));

        assertEquals(2, apiMessages.size());
        assertEquals("system", apiMessages.getFirst().get("role"));
        assertEquals("be brief", apiMessages.getFirst().get("content"));
        assertEquals("user", apiMessages.get(1).get("role"));
        assertEquals("hi", apiMessages.get(1).get("content"));
    }

    @Test
    public void trimToNull_blankAndNullBecomeNull() {
        assertNull(HttpLLMService.trimToNull(null));
        assertNull(HttpLLMService.trimToNull("   "));
        assertEquals("x", HttpLLMService.trimToNull("  x  "));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void postChat_sendsModelMaxTokensAndMessages() {
        service = new TestHttpLLMService(restTemplate, "model", 86400);
        when(restTemplate.exchange(eq("https://api.example.com/chat"), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(new HashMap<>(), HttpStatus.OK));

        service.testPostChat(Collections.singletonList(ChatMessage.user("hi")));

        org.mockito.ArgumentCaptor<HttpEntity<?>> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), any(ParameterizedTypeReference.class));

        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        assertNotNull(body);
        assertEquals("a-model", body.get("model"));
        assertEquals(4096, body.get("max_tokens"));
        assertEquals(1, ((List<?>) body.get("messages")).size());
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalStateException.class)
    public void postChat_nullBody_throws() {
        service = new TestHttpLLMService(restTemplate, "model", 86400);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        service.testPostChat(Collections.singletonList(ChatMessage.user("hi")));
    }

    // ==================== resolveModel ====================

    @Test
    public void resolveModel_configuredModel_returnsItDirectly() {
        service = new TestHttpLLMService(restTemplate, "my-model", 86400);
        assertEquals("my-model", service.testResolveModel());
        verifyNoInteractions(restTemplate);
    }

    @Test
    public void resolveModel_configuredModelWithSpaces_trimmed() {
        service = new TestHttpLLMService(restTemplate, "  my-model  ", 86400);
        assertEquals("my-model", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_noConfiguredModel_fetchesFromApi() {
        service = new TestHttpLLMService(restTemplate, null, 86400);
        mockModelsResponse(modelsBody(Arrays.asList(
                model("model-old", "created", 1000000000),   // epoch seconds
                model("model-new", "created", 2000000000)
        )));

        assertEquals("model-new", service.testResolveModel());
        verify(restTemplate).exchange(eq(MODELS_URL), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_emptyConfiguredModel_fetchesFromApi() {
        service = new TestHttpLLMService(restTemplate, "  ", 86400);
        mockModelsResponse(modelsBody(Collections.singletonList(model("the-model", "created", 1000000000))));

        assertEquals("the-model", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_cached_doesNotCallApiAgain() {
        service = new TestHttpLLMService(restTemplate, null, 86400);
        mockModelsResponse(modelsBody(Collections.singletonList(model("cached-model", "created", 1000000000))));

        assertEquals("cached-model", service.testResolveModel());
        assertEquals("cached-model", service.testResolveModel());

        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_noModels_errorNamesTheModelProperty() {
        service = new TestHttpLLMService(restTemplate, null, 86400);
        mockModelsResponse(modelsBody(Collections.emptyList()));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.testResolveModel());
        assertTrue(error.getMessage().contains("llm.test.model"));
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalStateException.class)
    public void resolveModel_nullData_throws() {
        service = new TestHttpLLMService(restTemplate, null, 86400);
        mockModelsResponse(modelsBody(null));

        service.testResolveModel();
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalStateException.class)
    public void resolveModel_nullResponseBody_throws() {
        service = new TestHttpLLMService(restTemplate, null, 86400);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        service.testResolveModel();
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalStateException.class)
    public void resolveModel_allIdsBlank_throwsNoValidModel() {
        service = new TestHttpLLMService(restTemplate, null, 86400);
        mockModelsResponse(modelsBody(Arrays.asList(model(null, null, null), model("  ", null, null))));

        service.testResolveModel();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_isoTimestamp_parsedCorrectly() {
        service = new TestHttpLLMService(restTemplate, null, 86400);
        mockModelsResponse(modelsBody(Arrays.asList(
                model("model-a", "created_at", "2024-01-01T00:00:00Z"),
                model("model-b", "created_at", "2025-06-15T12:00:00Z")
        )));

        assertEquals("model-b", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_dateOnly_parsedCorrectly() {
        service = new TestHttpLLMService(restTemplate, null, 86400);
        mockModelsResponse(modelsBody(Arrays.asList(
                model("old", "release_date", "2023-01-01"),
                model("new", "release_date", "2025-12-01")
        )));

        assertEquals("new", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_millisecondTimestamp_parsedCorrectly() {
        service = new TestHttpLLMService(restTemplate, null, 86400);
        mockModelsResponse(modelsBody(Arrays.asList(
                model("old", "created", 1700000000000L),
                model("new", "created", 1800000000000L)
        )));

        assertEquals("new", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_nullId_skipped() {
        service = new TestHttpLLMService(restTemplate, null, 86400);
        mockModelsResponse(modelsBody(Arrays.asList(
                model(null, "created", 9999999999L),
                model("valid-model", "created", 1000000000)
        )));

        assertEquals("valid-model", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_emptyId_skipped() {
        service = new TestHttpLLMService(restTemplate, null, 86400);
        mockModelsResponse(modelsBody(Arrays.asList(
                model("  ", "created", 9999999999L),
                model("valid-model", "created", 1000000000)
        )));

        assertEquals("valid-model", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_noTimeField_usesMinValue() {
        service = new TestHttpLLMService(restTemplate, null, 86400);
        mockModelsResponse(modelsBody(Arrays.asList(
                model("model-no-time", null, null),
                model("model-with-time", "created", 1000000000)
        )));

        assertEquals("model-with-time", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_unparsableTimeString_usesMinValue() {
        service = new TestHttpLLMService(restTemplate, null, 86400);
        mockModelsResponse(modelsBody(Arrays.asList(
                model("model-bad-time", "created", "not-a-date"),
                model("model-good", "created", 2000000000)
        )));

        assertEquals("model-good", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_emptyTimeString_usesMinValue() {
        service = new TestHttpLLMService(restTemplate, null, 86400);
        mockModelsResponse(modelsBody(Arrays.asList(
                model("model-empty-time", "created", "  "),
                model("model-good", "created", 1000000000)
        )));

        assertEquals("model-good", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_nullTimeValue_usesMinValue() {
        service = new TestHttpLLMService(restTemplate, null, 86400);
        mockModelsResponse(modelsBody(Arrays.asList(
                model("model1", "created", null),
                model("model2", "created", 1000000000)
        )));

        assertEquals("model2", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_zeroRefreshSeconds_clampedToOne() {
        service = new TestHttpLLMService(restTemplate, null, 0);
        mockModelsResponse(modelsBody(Collections.singletonList(model("test-model", "created", 1000000000))));

        assertEquals("test-model", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_olderModelListedSecond_notChosen() {
        service = new TestHttpLLMService(restTemplate, null, 86400);
        // newest first: the older row must not displace it
        mockModelsResponse(modelsBody(Arrays.asList(
                model("model-new", "created", 2000000000),
                model("model-old", "created", 1000000000)
        )));

        assertEquals("model-new", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_sameTimestamp_choosesLexicographicallyLater() {
        service = new TestHttpLLMService(restTemplate, null, 86400);
        mockModelsResponse(modelsBody(Arrays.asList(
                model("model-aaa", "created", 1500000000),
                model("model-zzz", "created", 1500000000)
        )));

        assertEquals("model-zzz", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_sameTimestamp_lexicographicallyEarlierNotChosen() {
        service = new TestHttpLLMService(restTemplate, null, 86400);
        // model-zzz is seen first; model-aaa ties on time but is lexicographically earlier
        mockModelsResponse(modelsBody(Arrays.asList(
                model("model-zzz", "created", 1500000000),
                model("model-aaa", "created", 1500000000)
        )));

        assertEquals("model-zzz", service.testResolveModel());
    }

    // ==================== exchangeForMapBody ====================

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalStateException.class)
    public void exchangeForMapBody_nullBody_throws() {
        service = new TestHttpLLMService(restTemplate, "model", 86400);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        service.testExchangeForMapBody();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void exchangeForMapBody_validBody_returnsMap() {
        service = new TestHttpLLMService(restTemplate, "model", 86400);
        Map<String, Object> body = new HashMap<>();
        body.put("key", "value");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        assertEquals("value", service.testExchangeForMapBody().get("key"));
    }
}
