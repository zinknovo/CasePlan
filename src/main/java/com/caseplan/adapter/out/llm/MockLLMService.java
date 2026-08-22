package com.caseplan.adapter.out.llm;

import com.caseplan.application.port.out.ChatMessage;

import java.util.List;

/**
 * Mock LLM provider: returns a canned service plan without calling any external API.
 * Used for load tests and demos so queue-drain scenarios do not consume LLM quota.
 */
public class MockLLMService extends BaseLLMService {

    /** Marker embedded in generated content so mock output is easy to spot. */
    public static final String MARKER = "【MOCK 生成】";

    @Override
    protected String doChat(List<ChatMessage> messages) {
        return MARKER + " 本服务方案由 mock provider 生成，未调用真实 LLM，仅用于压测与演示。\n"
                + "1. 初步评估：根据客户描述与案由，初步判断存在可诉事由。\n"
                + "2. 服务方案：建议采取调解优先、诉讼备选的两步策略。\n"
                + "3. 预期结果与风险：调解可显著缩短周期，诉讼存在证据风险，需补充材料。";
    }
}
