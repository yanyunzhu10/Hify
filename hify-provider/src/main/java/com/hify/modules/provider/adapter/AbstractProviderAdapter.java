package com.hify.modules.provider.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.provider.dto.ModelInfo;
import com.hify.modules.provider.entity.Provider;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Adapter 公共逻辑：apiKey 提取、baseUrl 规整、模型列表解析、Chat 请求/响应解析。
 */
@Slf4j
abstract class AbstractProviderAdapter implements ProviderAdapter {

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 去掉 baseUrl 末尾的斜杠，便于拼接路径。 */
    protected String trimTrailingSlash(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** 从 authConfig 提取 apiKey，缺失或为空抛 PARAM_ERROR。 */
    protected String extractApiKey(Provider provider) {
        Map<String, Object> auth = provider.getAuthConfig();
        if (auth == null || !auth.containsKey("apiKey")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "authConfig 中缺少 apiKey");
        }
        Object key = auth.get("apiKey");
        if (key == null || key.toString().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "apiKey 不能为空");
        }
        return key.toString();
    }

    /** 对象序列化为 JSON 字符串。 */
    protected String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "JSON 序列化失败: " + e.getMessage());
        }
    }

    /** 安全解析 JSON 字符串为 JsonNode，失败返回 null。 */
    protected JsonNode safeParse(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            log.debug("JSON 解析失败", e);
            return null;
        }
    }

    // ==================== 连通性 & 模型（已有） ====================

    protected int countArrayField(String responseBody, String arrayField) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            JsonNode arr = root.get(arrayField);
            return arr != null && arr.isArray() ? arr.size() : 0;
        } catch (Exception e) {
            log.debug("解析模型数量失败 field={}", arrayField, e);
            return 0;
        }
    }

    protected List<ModelInfo> extractModelList(String responseBody, String arrayField,
                                                String idField, String nameField) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            JsonNode arr = root.get(arrayField);
            if (arr == null || !arr.isArray()) {
                return Collections.emptyList();
            }
            List<ModelInfo> list = new ArrayList<>();
            for (JsonNode item : arr) {
                String modelId = item.has(idField) ? item.get(idField).asText() : null;
                if (modelId == null || modelId.isBlank()) {
                    continue;
                }
                String name = (nameField != null && item.has(nameField))
                        ? item.get(nameField).asText() : modelId;
                list.add(ModelInfo.of(modelId, name));
            }
            return list;
        } catch (Exception e) {
            log.debug("解析模型列表失败 arrayField={} idField={}", arrayField, idField, e);
            return Collections.emptyList();
        }
    }

    // ==================== Chat 流式行解析公共逻辑 ====================

    /**
     * 从 SSE 行提取 JSON 数据。
     * <p>
     * 标准 SSE 行格式为 {@code data: {...}} 或 {@code data: [DONE]}。
     * 返回 null 表示该行应跳过（空行、注释、[DONE]）。
     * </p>
     */
    protected JsonNode extractSseData(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return null;
        }
        if (!rawLine.startsWith("data: ")) {
            return null;
        }
        String payload = rawLine.substring(6);
        if ("[DONE]".equals(payload)) {
            return null;
        }
        return safeParse(payload);
    }
}
