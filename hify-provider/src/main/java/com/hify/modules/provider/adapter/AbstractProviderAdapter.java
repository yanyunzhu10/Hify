package com.hify.modules.provider.adapter;

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
 * Adapter 公共逻辑：apiKey 提取、baseUrl 规整、按字段名解析模型数量。
 * 需要鉴权的供应商（OpenAI / Anthropic / Gemini）继承此类复用 apiKey 提取。
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

    /**
     * 解析响应体中指定数组字段的长度。解析失败返回 0。
     *
     * @param responseBody 响应体
     * @param arrayField   模型列表所在的字段名（OpenAI/Anthropic 为 "data"，Ollama 为 "models"）
     */
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

    /**
     * 从 JSON 数组字段逐条提取模型信息。
     * <p>
     * 各供应商的模型列表结构：
     * <ul>
     *   <li>OpenAI / Anthropic：arrayField="data", idField="id", nameField 传 null → 展示名用 id</li>
     *   <li>Ollama：arrayField="models", idField="name", nameField 传 null → 展示名用 name</li>
     *   <li>Gemini：arrayField="models", idField="name", nameField="displayName" → 展示名取 displayName</li>
     * </ul>
     *
     * @param responseBody 响应体
     * @param arrayField   模型数组所在的 JSON 字段
     * @param idField      每条模型的唯一标识字段（如 "id" / "name"）
     * @param nameField    展示名字段，可为 null（此时取 idField 的值）
     * @return 模型列表，解析失败返回空列表
     */
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
}
