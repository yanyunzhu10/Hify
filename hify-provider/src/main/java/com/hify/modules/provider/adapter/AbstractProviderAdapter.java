package com.hify.modules.provider.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.provider.entity.Provider;
import lombok.extern.slf4j.Slf4j;

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
}
