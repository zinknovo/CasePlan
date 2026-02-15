package com.caseplan.application.port.out;

import lombok.Getter;
import lombok.Setter;

/**
 * Single message in a chat (role + content). Used for multi-turn or system-prompt flows.
 */
@Getter
@Setter
public class ChatMessage {

    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    private String role;
    private String content;

    public ChatMessage() {
    }

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    /** Factory method: takes content only, fills role as "system". */
    public static ChatMessage system(String content) {
        return new ChatMessage(ROLE_SYSTEM, content);
    }

    /** Factory method: takes content only, fills role as "user". */
    public static ChatMessage user(String content) {
        return new ChatMessage(ROLE_USER, content);
    }

    /** Factory method: takes content only, fills role as "assistant". */
    public static ChatMessage assistant(String content) {
        return new ChatMessage(ROLE_ASSISTANT, content);
    }
}
