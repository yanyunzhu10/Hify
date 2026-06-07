package com.hify.modules.provider.adapter;

import com.hify.modules.provider.dto.ModelInfo;
import com.hify.modules.provider.entity.Provider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ollama（本地）适配器。
 * <ul>
 *   <li>探测端点：{baseUrl}/api/tags</li>
 *   <li>认证：无（本地服务）</li>
 *   <li>响应：{"models": [...]}</li>
 * </ul>
 */
@Component
public class OllamaAdapter extends AbstractProviderAdapter {

    @Override
    public boolean supports(String type) {
        return "ollama".equals(type);
    }

    @Override
    public String buildUrl(String baseUrl) {
        return trimTrailingSlash(baseUrl) + "/api/tags";
    }

    @Override
    public Map<String, String> buildAuthHeaders(Provider provider) {
        return new HashMap<>(); // 本地 Ollama 无需鉴权
    }

    @Override
    public int parseModelCount(String responseBody) {
        return countArrayField(responseBody, "models");
    }

    @Override
    public List<ModelInfo> parseModels(String responseBody) {
        // Ollama: {"models": [{"name": "llama3:latest", ...}, ...]}，name 即模型标识
        return extractModelList(responseBody, "models", "name", null);
    }
}
