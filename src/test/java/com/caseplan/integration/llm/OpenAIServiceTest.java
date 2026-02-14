package com.caseplan.integration.llm;

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
public class OpenAIServiceTest {

    @Mock private RestTemplate restTemplate;

    private OpenAIService service;

    @Before
    public void setup() {
        service = new OpenAIService(restTemplate, "https://api.example.com/v1", "test-key", "gpt-4", 86400, 4096);
    }

    @SuppressWarnings("unchecked")
    private void mockChatResponse(String content) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        message.put("content", content);

        Map<String, Object> choice = new HashMap<>();
        choice.put("message", message);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("choices", Collections.singletonList(choice));

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(
                eq("https://api.example.com/v1/chat/completions"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);
    }

    @Test
    public void chat_singleMessage_returnsContent() {
        mockChatResponse("Hello, how can I help?");

        String result = service.chat("Hi");

        assertEquals("Hello, how can I help?", result);
    }

    @Test
    public void chat_multipleMessages_returnsContent() {
        mockChatResponse("Legal plan here");

        List<ChatMessage> messages = Arrays.asList(
                ChatMessage.system("You are a legal assistant"),
                ChatMessage.user("Generate a plan")
        );

        String result = service.chat(messages);

        assertEquals("Legal plan here", result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void chat_nullMessages_throwsException() {
        service.chat((List<ChatMessage>) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void chat_emptyMessages_throwsException() {
        service.chat(Collections.emptyList());
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalStateException.class)
    public void chat_noChoices_throwsException() {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("choices", Collections.emptyList());

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(responseEntity);

        service.chat("Hi");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void chat_nullMessage_returnsEmptyString() {
        Map<String, Object> choice = new HashMap<>();
        choice.put("message", null);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("choices", Collections.singletonList(choice));

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(responseEntity);

        String result = service.chat("Hi");
        assertEquals("", result);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void chat_nullContent_returnsEmptyString() {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        message.put("content", null);

        Map<String, Object> choice = new HashMap<>();
        choice.put("message", message);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("choices", Collections.singletonList(choice));

        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(responseEntity);

        String result = service.chat("Hi");
        assertEquals("", result);
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
        assertEquals("Bearer test-key", headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void chat_sendsCorrectBody() {
        mockChatResponse("response");

        service.chat("Hello world");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), any(ParameterizedTypeReference.class));

        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        assertEquals("gpt-4", body.get("model"));
        assertEquals(4096, body.get("max_tokens"));
    }

    @Test
    public void chat_baseUrlWithTrailingSlash_noDoubleSlash() {
        OpenAIService serviceWithSlash = new OpenAIService(restTemplate, "https://api.example.com/v1/", "key", "gpt-4", 86400, 4096);
        mockChatResponse("response");

        serviceWithSlash.chat("Hi");

        verify(restTemplate).exchange(eq("https://api.example.com/v1/chat/completions"), any(), any(), any(ParameterizedTypeReference.class));
    }
}
