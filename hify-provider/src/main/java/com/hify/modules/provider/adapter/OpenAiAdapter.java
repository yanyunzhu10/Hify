package com.hify.modules.provider.adapter;

import com.fasterxml.jackson.databind.JsonNode;
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
 * OpenAI 及 OpenAI 兼容协议适配器。
 * <ul>
 *   <li>Chat 端点：{baseUrl}/v1/chat/completions</li>
 *   <li>请求体：OpenAI 原生格式，直接序列化 {@link ChatRequest}</li>
 *   <li>流式行：SSE 标准，{@code data: \{"choices":[\{"delta":\{"content":"x"\}\}]\}}</li>
 * </ul>
 */
@Component
public class OpenAiAdapter extends AbstractProviderAdapter {

    @Override
    public boolean supports(String type) {
        return "openai".equals(type) || "openai_compatible".equals(type);
    }

    // ==================== 连通性 ====================

    @Override
    public String buildUrl(String baseUrl) {
        return trimTrailingSlash(baseUrl) + "/v1/models";
    }

    @Override
    public Map<String, String> buildAuthHeaders(Provider provider) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + extractApiKey(provider));
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
        return trimTrailingSlash(baseUrl) + "/v1/chat/completions";
    }

    @Override
    public String buildChatRequestBody(ChatRequest request) {
        return toJson(request);
    }

    @Override
    public ChatResponse parseChatResponse(String responseBody) {
        JsonNode root = safeParse(responseBody);
        if (root == null) return null;
        ChatResponse r = new ChatResponse();
        JsonNode choice = root.path("choices").path(0);
        r.setContent(choice.path("message").path("content").asText());
        r.setFinishReason(choice.path("finish_reason").asText(null));
        JsonNode usage = root.path("usage");
        r.setPromptTokens(usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : null);
        r.setCompletionTokens(usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : null);
        return r;
    }

    @Override
    public ChatStreamChunk parseStreamLine(String rawLine) {
        JsonNode root = extractSseData(rawLine);
        if (root == null) return null;
        JsonNode choice = root.path("choices").path(0);
        JsonNode delta = choice.path("delta");
        String content = delta.has("content") ? delta.get("content").asText(null) : null;
        String finishReason = choice.has("finish_reason") ? choice.get("finish_reason").asText(null) : null;
        if (content != null) {
            return ChatStreamChunk.content(content);
        }
        if (finishReason != null && !"null".equals(finishReason)) {
            JsonNode usage = root.path("usage");
            Integer tokens = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : null;
            return ChatStreamChunk.finish(finishReason, tokens);
        }
        return null;
    }
}
