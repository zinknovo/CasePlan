package com.caseplan.adapter.out.llm;

import com.caseplan.application.port.out.ChatMessage;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class BaseLLMServiceTest {

    /** BaseLLMService only owns the LLMService contract and its input validation. */
    private static class TestLLMService extends BaseLLMService {
        private List<ChatMessage> received;

        @Override
        protected String doChat(List<ChatMessage> messages) {
            this.received = messages;
            return "test response";
        }
    }

    private final TestLLMService service = new TestLLMService();

    @Test
    public void chat_singleString_wrapsAsUserMessage() {
        assertEquals("test response", service.chat("hello"));
        assertEquals(1, service.received.size());
        assertEquals(ChatMessage.ROLE_USER, service.received.getFirst().getRole());
        assertEquals("hello", service.received.getFirst().getContent());
    }

    @Test
    public void chat_messageList_delegatesToDoChat() {
        List<ChatMessage> messages = Collections.singletonList(ChatMessage.user("hello"));
        assertEquals("test response", service.chat(messages));
        assertEquals(messages, service.received);
    }

    @Test(expected = IllegalArgumentException.class)
    public void chat_nullList_throws() {
        service.chat((List<ChatMessage>) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void chat_emptyList_throws() {
        service.chat(Collections.emptyList());
    }
}
