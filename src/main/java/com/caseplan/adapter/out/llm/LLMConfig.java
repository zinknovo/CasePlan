package com.caseplan.adapter.out.llm;

import com.caseplan.application.port.out.LLMService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration: registers LLM-related beans based on llm.provider.
 * openai = OpenAI-compatible, claude = Anthropic Claude.
 */
@Configuration
public class LLMConfig {

    @Bean
    public RestTemplate llmRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000);
        factory.setReadTimeout(25000);
        return new RestTemplate(factory);
    }

    @Bean
    public LLMService llmService(
            RestTemplate llmRestTemplate,
            @Value("${llm.provider:openai}") String provider,
            @Value("${llm.openai.base-url:https://api.deepseek.com/v1}") String openaiBaseUrl,
            @Value("${llm.openai.api-key:}") String openaiApiKey,
            @Value("${llm.openai.model:}") String openaiModel,
            @Value("${llm.openai.model-refresh-seconds:2592000}") long openaiModelRefreshSeconds,
            @Value("${llm.openai.max-tokens:4000}") int openaiMaxTokens,
            @Value("${llm.claude.api-key:}") String claudeApiKey,
            @Value("${llm.claude.model:}") String claudeModel,
            @Value("${llm.claude.model-refresh-seconds:2592000}") long claudeModelRefreshSeconds,
            @Value("${llm.claude.max-tokens:4000}") int claudeMaxTokens) {

        switch (provider == null ? "" : provider.trim().toLowerCase()) {
            case "claude":
                return new ClaudeService(
                        llmRestTemplate,
                        claudeApiKey,
                        claudeModel,
                        claudeModelRefreshSeconds,
                        claudeMaxTokens
                );
            case "openai":
            default:
                return new OpenAIService(
                        llmRestTemplate,
                        openaiBaseUrl,
                        openaiApiKey,
                        openaiModel,
                        openaiModelRefreshSeconds,
                        openaiMaxTokens
                );
        }
    }
}
