package com.hify.modules.chat.config;

import com.hify.modules.provider.adapter.ProviderAdapter;
import com.hify.modules.provider.dto.ChatRequest;
import com.hify.modules.provider.dto.ChatResponse;
import com.hify.modules.provider.dto.ChatStreamChunk;
import com.hify.modules.provider.entity.Provider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * 测试配置类
 *
 * 在 mock profile 下，使用 MockProviderAdapter 来模拟 LLM 响应
 */
@Profile("mock")
public class TestConfig {

    /**
     * Mock Provider Adapter，用于测试
     */
    @Bean
    @Primary
    public ProviderAdapter mockProviderAdapter() {
        return new MockProviderAdapter();
    }

    @Bean
    public MockProviderAdapter mockProviderAccessor() {
        return (MockProviderAdapter) mockProviderAdapter();
    }

    /**
     * Mock Provider 实现
     */
    public static class MockProviderAdapter implements ProviderAdapter {

        private boolean shouldReturnToolCalls = true; // 控制是否返回 tool_calls

        public void setShouldReturnToolCalls(boolean shouldReturnToolCalls) {
            this.shouldReturnToolCalls = shouldReturnToolCalls;
        }

        @Override
        public List<String> parseModels(String response) {
            return List.of("gpt-3.5-turbo", "gpt-4");
        }

        @Override
        public String buildChatUrl(String baseUrl, String modelId) {
            return baseUrl + "/chat/completions";
        }

        @Override
        public Map<String, String> buildAuthHeaders(Provider provider) {
            return Map.of("Authorization", "Bearer mock-key");
        }

        @Override
        public String buildChatRequestBody(ChatRequest request) {
            // 返回模拟的请求体
            return "{\"model\":\"gpt-3.5-turbo\",\"messages\":[],\"stream\":false}";
        }

        @Override
        public ChatResponse parseChatResponse(String response) {
            if (shouldReturnToolCalls && response.contains("refund_eligibility")) {
                // 返回 tool_calls 响应
                ChatResponse chatResponse = new ChatResponse();
                chatResponse.setFinishReason("tool_calls");

                // 创建 tool_calls
                Map<String, Object> toolCall = Map.of(
                    "id", "call_123",
                    "type", "function",
                    "function", Map.of(
                        "name", "check_refund_eligibility",
                        "arguments", "{\"orderId\":\"123\"}"
                    )
                );

                chatResponse.setToolCalls(List.of(toolCall));
                return chatResponse;
            } else {
                // 返回普通文本响应
                ChatResponse chatResponse = new ChatResponse();
                chatResponse.setFinishReason("stop");
                chatResponse.setContent("这是模拟的 LLM 响应");
                return chatResponse;
            }
        }

        @Override
        public ChatStreamChunk parseStreamLine(String rawLine) {
            // 解析 SSE 流中的增量内容
            if (rawLine.contains("content")) {
                ChatStreamChunk chunk = new ChatStreamChunk();
                chunk.setContent("这是模拟的流式响应内容");
                return chunk;
            }
            return null;
        }

        @Override
        public String getType() {
            return "MOCK";
        }

        @Override
        public String getProviderType() {
            return "MOCK";
        }
    }
}