package com.caseplan.integration.llm;

import org.junit.Test;

import static org.junit.Assert.*;

public class ChatMessageTest {

    @Test
    public void systemFactory_setsRoleAndContent() {
        ChatMessage msg = ChatMessage.system("sys prompt");
        assertEquals("system", msg.getRole());
        assertEquals("sys prompt", msg.getContent());
    }

    @Test
    public void userFactory_setsRoleAndContent() {
        ChatMessage msg = ChatMessage.user("user msg");
        assertEquals("user", msg.getRole());
        assertEquals("user msg", msg.getContent());
    }

    @Test
    public void assistantFactory_setsRoleAndContent() {
        ChatMessage msg = ChatMessage.assistant("reply");
        assertEquals("assistant", msg.getRole());
        assertEquals("reply", msg.getContent());
    }

    @Test
    public void defaultConstructor_nullFields() {
        ChatMessage msg = new ChatMessage();
        assertNull(msg.getRole());
        assertNull(msg.getContent());
    }

    @Test
    public void setters_work() {
        ChatMessage msg = new ChatMessage();
        msg.setRole("user");
        msg.setContent("hello");
        assertEquals("user", msg.getRole());
        assertEquals("hello", msg.getContent());
    }

    @Test
    public void roleConstants() {
        assertEquals("system", ChatMessage.ROLE_SYSTEM);
        assertEquals("user", ChatMessage.ROLE_USER);
        assertEquals("assistant", ChatMessage.ROLE_ASSISTANT);
    }
}
