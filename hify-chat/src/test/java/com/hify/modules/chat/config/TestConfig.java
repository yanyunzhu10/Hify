package com.hify.modules.chat.config;

import com.hify.modules.provider.adapter.ProviderAdapter;
import com.hify.modules.provider.dto.ChatRequest;
import com.hify.modules.provider.dto.ChatResponse;
import com.hify.modules.provider.dto.ChatStreamChunk;
import com.hify.modules.provider.dto.ModelInfo;
import com.hify.modules.provider.entity.Provider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 测试配置类
 *
 * 在 mock profile 下，使用 MockProviderAdapter 来模拟 LLM 响应
 */
@Profile("mock")
@Component
public class TestConfig {

    /**
     * Mock Provider 实现
     */
    @Component
    public static class MockProviderAdapter implements ProviderAdapter {

        private boolean shouldReturnToolCalls = true;
        private final List<ChatRequest> receivedRequests = new CopyOnWriteArrayList<>();

        public void setShouldReturnToolCalls(boolean shouldReturnToolCalls) {
            this.shouldReturnToolCalls = shouldReturnToolCalls;
        }

        public List<ChatRequest> getReceivedRequests() {
            return new ArrayList<>(receivedRequests);
        }

        public void clearReceivedRequests() {
            receivedRequests.clear();
        }

        @Override
        public boolean supports(String type) {
            return "mock".equals(type);
        }

        @Override
        public String buildUrl(String baseUrl) {
            return baseUrl + "/models";
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
            receivedRequests.add(request);
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
                chunk.setContent("这是模拟的流式响应内容，退款条件，工具调用失败");
                return chunk;
            }
            return null;
        }

        // 其他方法的简单实现
        @Override
        public List<ModelInfo> parseModels(String response) {
            return List.of();
        }

        @Override
        public int parseModelCount(String response) {
            return 0;
        }

    }
}