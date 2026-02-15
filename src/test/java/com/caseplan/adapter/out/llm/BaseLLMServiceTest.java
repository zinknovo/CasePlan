package com.caseplan.adapter.out.llm;

import com.caseplan.application.port.out.ChatMessage;
import org.junit.Before;
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
public class BaseLLMServiceTest {

    @Mock private RestTemplate restTemplate;

    // Concrete subclass to test abstract BaseLLMService
    private static class TestLLMService extends BaseLLMService {
        private final RestTemplate restTemplate;
        private final String configuredModel;
        private final long modelRefreshSeconds;
        private final ModelCache modelCache = new ModelCache();

        TestLLMService(RestTemplate restTemplate, String configuredModel, long modelRefreshSeconds) {
            this.restTemplate = restTemplate;
            this.configuredModel = configuredModel;
            this.modelRefreshSeconds = modelRefreshSeconds;
        }

        @Override
        protected String doChat(List<ChatMessage> messages) {
            return "test response";
        }

        // Expose resolveModel for testing
        String testResolveModel() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer test");
            return resolveModel(
                    headers, restTemplate, configuredModel, modelRefreshSeconds,
                    modelCache, "https://api.example.com/models",
                    "null body", "no models", "no valid model",
                    Arrays.asList("created", "created_at", "release_date")
            );
        }

        // Expose exchangeForMapBody for testing
        Map<String, Object> testExchangeForMapBody(String url) {
            HttpHeaders headers = new HttpHeaders();
            return exchangeForMapBody(restTemplate, url, HttpMethod.GET, new HttpEntity<>(headers), "null body error");
        }
    }

    private TestLLMService service;

    // ==================== chat() ====================

    @Test
    public void chat_singleString_delegatesToDoChat() {
        service = new TestLLMService(restTemplate, "model", 86400);
        String result = service.chat("hello");
        assertEquals("test response", result);
    }

    @Test
    public void chat_messageList_delegatesToDoChat() {
        service = new TestLLMService(restTemplate, "model", 86400);
        String result = service.chat(Collections.singletonList(ChatMessage.user("hello")));
        assertEquals("test response", result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void chat_nullList_throws() {
        service = new TestLLMService(restTemplate, "model", 86400);
        service.chat((List<ChatMessage>) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void chat_emptyList_throws() {
        service = new TestLLMService(restTemplate, "model", 86400);
        service.chat(Collections.emptyList());
    }

    // ==================== resolveModel ====================

    @Test
    public void resolveModel_configuredModel_returnsItDirectly() {
        service = new TestLLMService(restTemplate, "my-model", 86400);
        String model = service.testResolveModel();
        assertEquals("my-model", model);
        verifyNoInteractions(restTemplate);
    }

    @Test
    public void resolveModel_configuredModelWithSpaces_trimmed() {
        service = new TestLLMService(restTemplate, "  my-model  ", 86400);
        assertEquals("my-model", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_noConfiguredModel_fetchesFromApi() {
        service = new TestLLMService(restTemplate, null, 86400);

        Map<String, Object> model1 = new HashMap<>();
        model1.put("id", "model-old");
        model1.put("created", 1000000000); // epoch seconds

        Map<String, Object> model2 = new HashMap<>();
        model2.put("id", "model-new");
        model2.put("created", 2000000000);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("data", Arrays.asList(model1, model2));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        String result = service.testResolveModel();
        assertEquals("model-new", result);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_emptyConfiguredModel_fetchesFromApi() {
        service = new TestLLMService(restTemplate, "  ", 86400);

        Map<String, Object> model1 = new HashMap<>();
        model1.put("id", "the-model");
        model1.put("created", 1000000000);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("data", Collections.singletonList(model1));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        assertEquals("the-model", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_cached_doesNotCallApiAgain() {
        service = new TestLLMService(restTemplate, null, 86400);

        Map<String, Object> model1 = new HashMap<>();
        model1.put("id", "cached-model");
        model1.put("created", 1000000000);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("data", Collections.singletonList(model1));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // First call
        assertEquals("cached-model", service.testResolveModel());
        // Second call should use cache
        assertEquals("cached-model", service.testResolveModel());

        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class));
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalStateException.class)
    public void resolveModel_noModels_throws() {
        service = new TestLLMService(restTemplate, null, 86400);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("data", Collections.emptyList());

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        service.testResolveModel();
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalStateException.class)
    public void resolveModel_nullData_throws() {
        service = new TestLLMService(restTemplate, null, 86400);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("data", null);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        service.testResolveModel();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_isoTimestamp_parsedCorrectly() {
        service = new TestLLMService(restTemplate, null, 86400);

        Map<String, Object> model1 = new HashMap<>();
        model1.put("id", "model-a");
        model1.put("created_at", "2024-01-01T00:00:00Z");

        Map<String, Object> model2 = new HashMap<>();
        model2.put("id", "model-b");
        model2.put("created_at", "2025-06-15T12:00:00Z");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("data", Arrays.asList(model1, model2));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        assertEquals("model-b", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_dateOnly_parsedCorrectly() {
        service = new TestLLMService(restTemplate, null, 86400);

        Map<String, Object> model1 = new HashMap<>();
        model1.put("id", "old");
        model1.put("release_date", "2023-01-01");

        Map<String, Object> model2 = new HashMap<>();
        model2.put("id", "new");
        model2.put("release_date", "2025-12-01");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("data", Arrays.asList(model1, model2));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        assertEquals("new", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_millisecondTimestamp_parsedCorrectly() {
        service = new TestLLMService(restTemplate, null, 86400);

        Map<String, Object> model1 = new HashMap<>();
        model1.put("id", "old");
        model1.put("created", 1700000000000L); // millis

        Map<String, Object> model2 = new HashMap<>();
        model2.put("id", "new");
        model2.put("created", 1800000000000L);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("data", Arrays.asList(model1, model2));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        assertEquals("new", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_nullId_skipped() {
        service = new TestLLMService(restTemplate, null, 86400);

        Map<String, Object> model1 = new HashMap<>();
        model1.put("id", null);
        model1.put("created", 9999999999L);

        Map<String, Object> model2 = new HashMap<>();
        model2.put("id", "valid-model");
        model2.put("created", 1000000000);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("data", Arrays.asList(model1, model2));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        assertEquals("valid-model", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_emptyId_skipped() {
        service = new TestLLMService(restTemplate, null, 86400);

        Map<String, Object> model1 = new HashMap<>();
        model1.put("id", "  ");
        model1.put("created", 9999999999L);

        Map<String, Object> model2 = new HashMap<>();
        model2.put("id", "valid-model");
        model2.put("created", 1000000000);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("data", Arrays.asList(model1, model2));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        assertEquals("valid-model", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_noTimeField_usesMinValue() {
        service = new TestLLMService(restTemplate, null, 86400);

        Map<String, Object> model1 = new HashMap<>();
        model1.put("id", "model-no-time");

        Map<String, Object> model2 = new HashMap<>();
        model2.put("id", "model-with-time");
        model2.put("created", 1000000000);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("data", Arrays.asList(model1, model2));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        assertEquals("model-with-time", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_unparsableTimeString_usesMinValue() {
        service = new TestLLMService(restTemplate, null, 86400);

        Map<String, Object> model1 = new HashMap<>();
        model1.put("id", "model-bad-time");
        model1.put("created", "not-a-date");

        Map<String, Object> model2 = new HashMap<>();
        model2.put("id", "model-good");
        model2.put("created", 2000000000);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("data", Arrays.asList(model1, model2));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        assertEquals("model-good", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_emptyTimeString_usesMinValue() {
        service = new TestLLMService(restTemplate, null, 86400);

        Map<String, Object> model1 = new HashMap<>();
        model1.put("id", "model-empty-time");
        model1.put("created", "  ");

        Map<String, Object> model2 = new HashMap<>();
        model2.put("id", "model-good");
        model2.put("created", 1000000000);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("data", Arrays.asList(model1, model2));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        assertEquals("model-good", service.testResolveModel());
    }

    // ==================== exchangeForMapBody ====================

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalStateException.class)
    public void exchangeForMapBody_nullBody_throws() {
        service = new TestLLMService(restTemplate, "model", 86400);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        service.testExchangeForMapBody("https://api.example.com/test");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void exchangeForMapBody_validBody_returnsMap() {
        service = new TestLLMService(restTemplate, "model", 86400);

        Map<String, Object> body = new HashMap<>();
        body.put("key", "value");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        Map<String, Object> result = service.testExchangeForMapBody("https://api.example.com/test");
        assertEquals("value", result.get("key"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_zeroRefreshSeconds_clampedToOne() {
        service = new TestLLMService(restTemplate, null, 0);

        Map<String, Object> model1 = new HashMap<>();
        model1.put("id", "test-model");
        model1.put("created", 1000000000);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("data", Collections.singletonList(model1));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        assertEquals("test-model", service.testResolveModel());
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalStateException.class)
    public void resolveModel_allIdsNull_throwsNoValidModel() {
        service = new TestLLMService(restTemplate, null, 86400);

        Map<String, Object> model1 = new HashMap<>();
        model1.put("id", null);

        Map<String, Object> model2 = new HashMap<>();
        model2.put("id", "  ");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("data", Arrays.asList(model1, model2));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        service.testResolveModel();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resolveModel_nullTimeValue_usesMinValue() {
        service = new TestLLMService(restTemplate, null, 86400);

        Map<String, Object> model1 = new HashMap<>();
        model1.put("id", "model1");
        model1.put("created", null);

        Map<String, Object> model2 = new HashMap<>();
        model2.put("id", "model2");
        model2.put("created", 1000000000);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("data", Arrays.asList(model1, model2));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        assertEquals("model2", service.testResolveModel());
    }
}
