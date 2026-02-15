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
@SuppressWarnings({"unchecked", "rawtypes"})
public class ClaudeServiceTest {

    @Mock private RestTemplate restTemplate;

    private ClaudeService service;

    @Before
    public void setup() {
        service = new ClaudeService(restTemplate, "test-api-key", "claude-3-opus", 86400, 4096);
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
    public void chat_withSystemMessage_systemSentSeparately() {
        mockChatResponse("response");

        List<ChatMessage> messages = Arrays.asList(
                ChatMessage.system("You are helpful"),
                ChatMessage.user("Hi")
        );
        service.chat(messages);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), any(ParameterizedTypeReference.class));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        // System message should be in body.system, not in messages array
        assertEquals("You are helpful", body.get("system"));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> apiMessages = (List<Map<String, String>>) body.get("messages");
        // messages array should only contain user message, not system
        assertEquals(1, apiMessages.size());
        assertEquals("user", apiMessages.get(0).get("role"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void chat_sendsCorrectHeaders() {
        mockChatResponse("response");

        service.chat("Hi");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), any(ParameterizedTypeReference.class));

        HttpHeaders headers = captor.getValue().getHeaders();
        assertEquals(MediaType.APPLICATION_JSON, headers.getContentType());
        assertEquals("test-api-key", headers.getFirst("x-api-key"));
        assertEquals("2023-06-01", headers.getFirst("anthropic-version"));
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
}
