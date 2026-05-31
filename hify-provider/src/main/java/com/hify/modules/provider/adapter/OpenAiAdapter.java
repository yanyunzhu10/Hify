package com.hify.modules.provider.adapter;

import com.hify.modules.provider.entity.Provider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * OpenAI 及 OpenAI 兼容协议（openai_compatible）适配器。
 * <ul>
 *   <li>探测端点：{baseUrl}/v1/models</li>
 *   <li>认证：Authorization: Bearer {apiKey}</li>
 *   <li>响应：{"data": [...]}</li>
 * </ul>
 */
@Component
public class OpenAiAdapter extends AbstractProviderAdapter {

    @Override
    public boolean supports(String type) {
        return "openai".equals(type) || "openai_compatible".equals(type);
    }

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
}
