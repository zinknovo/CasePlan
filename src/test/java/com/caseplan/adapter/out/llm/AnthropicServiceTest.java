package com.caseplan.adapter.out.llm;

import com.caseplan.application.port.out.ChatMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
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
public class AnthropicServiceTest {

    //noinspection UastIncorrectHttpHeaderInspection
    private static final String HEADER_X_API_KEY = "x-api-key";
    //noinspection UastIncorrectHttpHeaderInspection
    private static final String HEADER_ANTHROPIC_VERSION = "anthropic-version";

    @Mock private RestTemplate restTemplate;

    private AnthropicService service;

    @Before
    public void setup() {
        service = new AnthropicService(restTemplate, "test-api-key", "claude-sonnet-5", 86400, 4096);
    }

    @SuppressWarnings("unchecked")
    private void mockChatResponse(String text) {
        Map<String, Object> textBlock = new HashMap<>();
        textBlock.put("type", "text");
        textBlock.put("text", text);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("content", Collections.singletonList(textBlock));

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(
                eq("https://api.anthropic.com/v1/messages"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);
    }

    @Test
    public void chat_singleMessage_returnsContent() {
        mockChatResponse("Hello from Claude");

        String result = service.chat("Hi");

        assertEquals("Hello from Claude", result);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void chat_withSystemMessage_systemSentSeparately() {
        mockChatResponse("response");

        List<ChatMessage> messages = Arrays.asList(
                ChatMessage.system("You are helpful"),
                ChatMessage.user("Hi")
        );
        service.chat(messages);

        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), any(ParameterizedTypeReference.class));

        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        // System message should be in body.system, not in messages array
        assertNotNull(body);
        assertEquals("You are helpful", body.get("system"));

        List<Map<String, String>> apiMessages = (List<Map<String, String>>) body.get("messages");
        // messages array should only contain user message, not system
        assertEquals(1, apiMessages.size());
        assertEquals("user", apiMessages.getFirst().get("role"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void chat_sendsCorrectHeaders() {
        mockChatResponse("response");

        service.chat("Hi");

        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), any(ParameterizedTypeReference.class));

        HttpHeaders headers = captor.getValue().getHeaders();
        assertEquals(MediaType.APPLICATION_JSON, headers.getContentType());
        assertEquals("test-api-key", headers.getFirst(HEADER_X_API_KEY));
        assertEquals("2023-06-01", headers.getFirst(HEADER_ANTHROPIC_VERSION));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void chat_emptyContent_returnsEmptyString() {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("content", Collections.emptyList());

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(responseEntity);

        String result = service.chat("Hi");
        assertEquals("", result);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void chat_noTextBlock_returnsEmptyString() {
        Map<String, Object> imageBlock = new HashMap<>();
        imageBlock.put("type", "image");
        imageBlock.put("source", "data");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("content", Collections.singletonList(imageBlock));

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(responseEntity);

        String result = service.chat("Hi");
        assertEquals("", result);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void chat_nullContentInTextBlock_returnsEmptyString() {
        Map<String, Object> textBlock = new HashMap<>();
        textBlock.put("type", "text");
        textBlock.put("text", null);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("content", Collections.singletonList(textBlock));

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(responseEntity);

        String result = service.chat("Hi");
        assertEquals("", result);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void chat_multipleSystemMessages_onlyFirstBecomesSystemField() {
        mockChatResponse("response");

        service.chat(Arrays.asList(
                ChatMessage.system("first"),
                ChatMessage.system("second"),
                ChatMessage.user("Hi")
        ));

        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), any(ParameterizedTypeReference.class));

        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        assertNotNull(body);
        assertEquals("first", body.get("system"));
        // both system messages are kept out of messages[]
        assertEquals(1, ((List<?>) body.get("messages")).size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void chat_responseWithoutContentKey_returnsEmptyString() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(new HashMap<>(), HttpStatus.OK));

        assertEquals("", service.chat("Hi"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void chat_noConfiguredModel_resolvesNewestFromModelsApi() {
        AnthropicService autoService = new AnthropicService(restTemplate, "test-api-key", "", 86400, 4096);
        mockModelsResponse(Arrays.asList(
                modelRow("claude-old", "2024-01-01T00:00:00Z"),
                modelRow("claude-new", "2025-06-15T12:00:00Z")
        ));
        mockChatResponse("response");

        autoService.chat("Hi");

        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("https://api.anthropic.com/v1/messages"), eq(HttpMethod.POST), captor.capture(), any(ParameterizedTypeReference.class));

        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        assertNotNull(body);
        assertEquals("claude-new", body.get("model"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void chat_noConfiguredModel_noModels_errorNamesTheProperty() {
        AnthropicService autoService = new AnthropicService(restTemplate, "test-api-key", "", 86400, 4096);
        mockModelsResponse(Collections.emptyList());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> autoService.chat("Hi"));
        assertTrue(error.getMessage().contains("llm.anthropic.model"));
    }

    @SuppressWarnings("unchecked")
    private void mockModelsResponse(List<Map<String, Object>> models) {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("data", models);
        when(restTemplate.exchange(
                eq("https://api.anthropic.com/v1/models"),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
    }

    private static Map<String, Object> modelRow(String id, String createdAt) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("created_at", createdAt);
        return row;
    }
}
