package com.hify.modules.provider.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hify.modules.provider.dto.ChatRequest;
import com.hify.modules.provider.dto.ChatResponse;
import com.hify.modules.provider.dto.ChatStreamChunk;
import com.hify.modules.provider.dto.ModelInfo;
import com.hify.modules.provider.entity.Provider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic（Claude）适配器。
 * <ul>
 *   <li>Chat 端点：{baseUrl}/v1/messages</li>
 *   <li>请求体：system 从 messages 提取为顶层字段</li>
 *   <li>流式：SSE 事件类型 content_block_delta / message_delta</li>
 * </ul>
 */
@Component
public class AnthropicAdapter extends AbstractProviderAdapter {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Override
    public boolean supports(String type) {
        return "anthropic".equals(type);
    }

    // ==================== 连通性 ====================

    @Override
    public String buildUrl(String baseUrl) {
        return trimTrailingSlash(baseUrl) + "/v1/models";
    }

    @Override
    public Map<String, String> buildAuthHeaders(Provider provider) {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", extractApiKey(provider));
        headers.put("anthropic-version", ANTHROPIC_VERSION);
        return headers;
    }

    @Override
    public int parseModelCount(String responseBody) {
        return countArrayField(responseBody, "data");
    }

    @Override
    public List<ModelInfo> parseModels(String responseBody) {
        return extractModelList(responseBody, "data", "id", null);
    }

    // ==================== Chat ====================

    @Override
    public String buildChatUrl(String baseUrl, String model) {
        return trimTrailingSlash(baseUrl) + "/v1/messages";
    }

    @Override
    public String buildChatRequestBody(ChatRequest request) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("model", request.getModel());
        root.put("stream", request.isStream());
        if (request.getMaxTokens() != null) {
            root.put("max_tokens", request.getMaxTokens());
        }
        if (request.getTemperature() != null) {
            root.put("temperature", request.getTemperature());
        }

        // system 从 messages 提取为顶层字段
        ArrayNode messages = OBJECT_MAPPER.createArrayNode();
        for (ChatRequest.Message msg : request.getMessages()) {
            if ("system".equals(msg.getRole())) {
                if (msg.getContent() != null) {
                    root.put("system", msg.getContent());
                }
            } else {
                ObjectNode m = OBJECT_MAPPER.createObjectNode();
                m.put("role", msg.getRole());
                m.put("content", msg.getContent());
                messages.add(m);
            }
        }
        root.set("messages", messages);
        return toJson(root);
    }

    @Override
    public ChatResponse parseChatResponse(String responseBody) {
        JsonNode root = safeParse(responseBody);
        if (root == null) return null;
        ChatResponse r = new ChatResponse();
        JsonNode content = root.path("content");
        if (content.isArray() && !content.isEmpty()) {
            r.setContent(content.path(0).path("text").asText());
        }
        r.setFinishReason(root.path("stop_reason").asText(null));
        JsonNode usage = root.path("usage");
        r.setCompletionTokens(usage.has("output_tokens") ? usage.get("output_tokens").asInt() : null);
        r.setPromptTokens(usage.has("input_tokens") ? usage.get("input_tokens").asInt() : null);
        return r;
    }

    @Override
    public ChatStreamChunk parseStreamLine(String rawLine) {
        JsonNode root = extractSseData(rawLine);
        if (root == null) return null;
        String type = root.path("type").asText(null);
        if (type == null) return null;

        return switch (type) {
            case "content_block_delta" -> {
                String text = root.path("delta").path("text").asText(null);
                yield text != null ? ChatStreamChunk.content(text) : null;
            }
            case "message_delta" -> {
                String stopReason = root.path("delta").path("stop_reason").asText(null);
                JsonNode usage = root.path("usage");
                Integer tokens = usage.has("output_tokens")
                        ? usage.get("output_tokens").asInt() : null;
                yield ChatStreamChunk.finish(stopReason, tokens);
            }
            default -> null;
        };
    }
}
