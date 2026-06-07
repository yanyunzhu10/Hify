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
 * Google Gemini 适配器。
 * <ul>
 *   <li>Chat 端点：{baseUrl}/v1beta/models/{model}:streamGenerateContent?alt=sse</li>
 *   <li>请求体：messages 转为 contents（role: assistant→model） + systemInstruction + generationConfig</li>
 *   <li>流式行：SSE 标准，{@code data: \{"candidates":[\{"content":\{"parts":[\{"text":"x"\}]\}\}]\}}</li>
 * </ul>
 */
@Component
public class GeminiAdapter extends AbstractProviderAdapter {

    @Override
    public boolean supports(String type) {
        return "gemini".equals(type);
    }

    // ==================== 连通性 ====================

    @Override
    public String buildUrl(String baseUrl) {
        return trimTrailingSlash(baseUrl) + "/v1beta/models";
    }

    @Override
    public Map<String, String> buildAuthHeaders(Provider provider) {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-goog-api-key", extractApiKey(provider));
        return headers;
    }

    @Override
    public int parseModelCount(String responseBody) {
        return countArrayField(responseBody, "models");
    }

    @Override
    public List<ModelInfo> parseModels(String responseBody) {
        return extractModelList(responseBody, "models", "name", "displayName");
    }

    // ==================== Chat ====================

    @Override
    public String buildChatUrl(String baseUrl, String model) {
        return trimTrailingSlash(baseUrl) + "/v1beta/models/" + model + ":streamGenerateContent?alt=sse";
    }

    @Override
    public String buildChatRequestBody(ChatRequest request) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();

        // messages → contents（role 转换）+ systemInstruction
        ArrayNode contents = OBJECT_MAPPER.createArrayNode();
        for (ChatRequest.Message msg : request.getMessages()) {
            String content = msg.getContent();
            if (content == null || content.isBlank()) continue;

            if ("system".equals(msg.getRole())) {
                ObjectNode si = OBJECT_MAPPER.createObjectNode();
                ArrayNode parts = OBJECT_MAPPER.createArrayNode();
                parts.add(createTextPart(content));
                si.set("parts", parts);
                root.set("systemInstruction", si);
            } else {
                ObjectNode item = OBJECT_MAPPER.createObjectNode();
                // assistant → model
                item.put("role", "assistant".equals(msg.getRole()) ? "model" : msg.getRole());
                ArrayNode parts = OBJECT_MAPPER.createArrayNode();
                parts.add(createTextPart(content));
                item.set("parts", parts);
                contents.add(item);
            }
        }
        root.set("contents", contents);

        // generationConfig
        if (request.getTemperature() != null || request.getMaxTokens() != null) {
            ObjectNode gc = OBJECT_MAPPER.createObjectNode();
            if (request.getTemperature() != null) {
                gc.put("temperature", request.getTemperature());
            }
            if (request.getMaxTokens() != null) {
                gc.put("maxOutputTokens", request.getMaxTokens());
            }
            root.set("generationConfig", gc);
        }
        return toJson(root);
    }

    @Override
    public ChatResponse parseChatResponse(String responseBody) {
        JsonNode root = safeParse(responseBody);
        if (root == null) return null;
        ChatResponse r = new ChatResponse();
        JsonNode candidate = root.path("candidates").path(0);
        r.setContent(candidate.path("content").path("parts").path(0).path("text").asText());
        r.setFinishReason(candidate.path("finishReason").asText(null));
        JsonNode usage = root.path("usageMetadata");
        r.setPromptTokens(usage.has("promptTokenCount") ? usage.get("promptTokenCount").asInt() : null);
        r.setCompletionTokens(usage.has("candidatesTokenCount") ? usage.get("candidatesTokenCount").asInt() : null);
        return r;
    }

    @Override
    public ChatStreamChunk parseStreamLine(String rawLine) {
        JsonNode root = extractSseData(rawLine);
        if (root == null) return null;
        JsonNode candidate = root.path("candidates").path(0);
        if (candidate.isMissingNode()) return null;

        JsonNode parts = candidate.path("content").path("parts");
        String content = null;
        if (parts.isArray() && !parts.isEmpty()) {
            content = parts.path(0).path("text").asText(null);
        }
        String finishReason = candidate.path("finishReason").asText(null);
        boolean hasFinish = finishReason != null && !"null".equals(finishReason)
                && !"FINISH_REASON_UNSPECIFIED".equals(finishReason);

        if (content != null && !content.isEmpty()) {
            return ChatStreamChunk.content(content);
        }
        if (hasFinish) {
            JsonNode usage = root.path("usageMetadata");
            Integer tokens = usage.has("candidatesTokenCount")
                    ? usage.get("candidatesTokenCount").asInt() : null;
            return ChatStreamChunk.finish(finishReason, tokens);
        }
        return null;
    }

    private ObjectNode createTextPart(String text) {
        ObjectNode part = OBJECT_MAPPER.createObjectNode();
        part.put("text", text);
        return part;
    }
}
