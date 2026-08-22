package com.caseplan.adapter.out.llm;

import com.caseplan.application.port.out.ChatMessage;
import com.caseplan.application.port.out.LLMService;

import java.util.Collections;
import java.util.List;

/**
 * Abstract base for LLM implementations: holds the {@link LLMService} contract and its input
 * validation, and leaves the actual call to subclasses. Business code uses {@link LLMService}
 * and does not depend on this class. HTTP-backed providers extend {@link HttpLLMService}.
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
}
