package com.hify.modules.provider.adapter;

import com.fasterxml.jackson.databind.JsonNode;
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
 * Ollama（本地）适配器。
 * <ul>
 *   <li>Chat 端点：{baseUrl}/api/chat</li>
 *   <li>temperature / maxTokens 包在 options 内</li>
 *   <li>流式：每行即一个完整 JSON（无 SSE {@code data:} 前缀），{@code message.content} 取值</li>
 * </ul>
 */
@Component
public class OllamaAdapter extends AbstractProviderAdapter {

    @Override
    public boolean supports(String type) {
        return "ollama".equals(type);
    }

    // ==================== 连通性 ====================

    @Override
    public String buildUrl(String baseUrl) {
        return trimTrailingSlash(baseUrl) + "/api/tags";
    }

    @Override
    public Map<String, String> buildAuthHeaders(Provider provider) {
        return new HashMap<>();
    }

    @Override
    public int parseModelCount(String responseBody) {
        return countArrayField(responseBody, "models");
    }

    @Override
    public List<ModelInfo> parseModels(String responseBody) {
        return extractModelList(responseBody, "models", "name", null);
    }

    // ==================== Chat ====================

    @Override
    public String buildChatUrl(String baseUrl, String model) {
        return trimTrailingSlash(baseUrl) + "/api/chat";
    }

    @Override
    public String buildChatRequestBody(ChatRequest request) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("model", request.getModel());
        root.put("stream", request.isStream());

        // messages 直接序列化
        root.set("messages", OBJECT_MAPPER.valueToTree(request.getMessages()));

        // 参数包在 options 内
        if (request.getTemperature() != null || request.getMaxTokens() != null) {
            ObjectNode options = OBJECT_MAPPER.createObjectNode();
            if (request.getTemperature() != null) {
                options.put("temperature", request.getTemperature());
            }
            if (request.getMaxTokens() != null) {
                options.put("num_predict", request.getMaxTokens());
            }
            root.set("options", options);
        }
        return toJson(root);
    }

    @Override
    public ChatResponse parseChatResponse(String responseBody) {
        JsonNode root = safeParse(responseBody);
        if (root == null) return null;
        ChatResponse r = new ChatResponse();
        r.setContent(root.path("message").path("content").asText());
        r.setFinishReason(root.path("done_reason").asText(null));
        r.setCompletionTokens(root.has("eval_count") ? root.get("eval_count").asInt() : null);
        r.setPromptTokens(root.has("prompt_eval_count") ? root.get("prompt_eval_count").asInt() : null);
        return r;
    }

    @Override
    public ChatStreamChunk parseStreamLine(String rawLine) {
        // Ollama 无 SSE 封装，每行即完整 JSON
        JsonNode root = safeParse(rawLine);
        if (root == null) return null;

        boolean done = root.path("done").asBoolean(false);
        String content = root.path("message").path("content").asText(null);

        if (done) {
            String reason = root.path("done_reason").asText(null);
            Integer tokens = root.has("eval_count") ? root.get("eval_count").asInt() : null;
            return ChatStreamChunk.finish(reason, tokens);
        }
        if (content != null && !content.isEmpty()) {
            return ChatStreamChunk.content(content);
        }
        return null;
    }
}
