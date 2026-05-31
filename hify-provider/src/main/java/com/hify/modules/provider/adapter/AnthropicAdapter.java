package com.hify.modules.provider.adapter;

import com.hify.modules.provider.entity.Provider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Anthropic（Claude）适配器。
 * <ul>
 *   <li>探测端点：{baseUrl}/v1/models</li>
 *   <li>认证：x-api-key: {apiKey} + anthropic-version: 2023-06-01</li>
 *   <li>响应：{"data": [...]}</li>
 * </ul>
 */
@Component
public class AnthropicAdapter extends AbstractProviderAdapter {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Override
    public boolean supports(String type) {
        return "anthropic".equals(type);
    }

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
}
