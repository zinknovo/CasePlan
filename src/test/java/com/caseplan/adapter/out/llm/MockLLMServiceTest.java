package com.caseplan.adapter.out.llm;

import com.caseplan.application.port.out.ChatMessage;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class MockLLMServiceTest {

    private final MockLLMService service = new MockLLMService();

    @Test
    public void chat_singleMessage_returnsCannedPlanWithMarker() {
        String result = service.chat("任意输入");

        assertNotNull(result);
        assertTrue(result.contains("【MOCK"));
    }

    @Test
    public void chat_withSystemMessage_returnsCannedPlan() {
        List<ChatMessage> messages = Arrays.asList(
                ChatMessage.system("You are helpful"),
                ChatMessage.user("Hi")
        );

        String result = service.chat(messages);

        assertTrue(result.contains("MOCK"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void chat_emptyMessages_throws() {
        service.chat(Collections.emptyList());
    }
}
