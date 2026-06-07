package com.hify.modules.provider.adapter;

import com.hify.modules.provider.dto.ModelInfo;
import com.hify.modules.provider.entity.Provider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini 适配器。
 * <ul>
 *   <li>探测端点：{baseUrl}/v1beta/models</li>
 *   <li>认证：x-goog-api-key: {apiKey} 请求头</li>
 *   <li>响应：{"models": [...]}</li>
 * </ul>
 */
@Component
public class GeminiAdapter extends AbstractProviderAdapter {

    @Override
    public boolean supports(String type) {
        return "gemini".equals(type);
    }

    @Override
    public String buildUrl(String baseUrl) {
        return trimTrailingSlash(baseUrl) + "/v1beta/models";
    }

    @Override
    public Map<String, String> buildAuthHeaders(Provider provider) {
        // Gemini 鉴权用 x-goog-api-key 请求头，而非 query 参数，避免 key 出现在 URL/日志中。
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
        // Gemini: {"models": [{"name": "models/gemini-1.5-pro", "displayName": "Gemini 1.5 Pro"}, ...]}
        return extractModelList(responseBody, "models", "name", "displayName");
    }
}
