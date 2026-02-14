package com.caseplan.integration.llm;

import java.util.List;

/**
 * Abstraction for LLM chat completion. Business code depends only on this interface;
 * the concrete provider (DeepSeek, OpenAI, Claude, local) is chosen by configuration.
 */
public interface LLMService {

    /** Sends a single user message and returns the assistant's reply text. */
    String chat(String userMessage);

    /** Sends a list of messages (e.g. system + user, or multi-turn) and returns the assistant's reply text. */
    String chat(List<ChatMessage> messages);
}
