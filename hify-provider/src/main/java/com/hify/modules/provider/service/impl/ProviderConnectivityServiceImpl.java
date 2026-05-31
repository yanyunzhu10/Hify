package com.hify.modules.provider.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.exception.LlmApiException;
import com.hify.common.http.LlmHttpClient;
import com.hify.modules.provider.dto.ConnectionTestResult;
import com.hify.modules.provider.entity.Provider;
import com.hify.modules.provider.service.ProviderConnectivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderConnectivityServiceImpl implements ProviderConnectivityService {

    private final LlmHttpClient llmHttpClient;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Override
    public ConnectionTestResult test(Provider provider) {
        String type = normalizeType(provider.getType());
        String url = buildUrl(provider.getBaseUrl(), type);
        Map<String, String> headers = buildAuthHeaders(provider, type);

        long start = System.currentTimeMillis();
        try {
            String body = llmHttpClient.get(url, headers);
            long latency = System.currentTimeMillis() - start;
            int count = parseModelCount(body, type);
            log.info("连通性测试成功 provider={} type={} latencyMs={} modelCount={}",
                    provider.getName(), type, latency, count);
            return ConnectionTestResult.ok(latency, count);
        } catch (LlmApiException e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("连通性测试失败 provider={} type={} latencyMs={} error={}",
                    provider.getName(), type, latency, e.getMessage());
            return ConnectionTestResult.fail(latency, e.getMessage());
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("连通性测试异常 provider={} type={} latencyMs={} error={}",
                    provider.getName(), type, latency, e.getMessage());
            return ConnectionTestResult.fail(latency, e.getMessage());
        }
    }

    // ============================================================
    // URL 构建
    // ============================================================

    private String buildUrl(String baseUrl, String type) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return switch (type) {
            case "openai", "openai_compatible" -> base + "/v1/models";
            case "anthropic" -> base + "/v1/models";
            case "ollama" -> base + "/api/tags";
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "不支持的供应商类型: " + type);
        };
    }

    // ============================================================
    // 认证头构建
    // ============================================================

    private Map<String, String> buildAuthHeaders(Provider provider, String type) {
        Map<String, String> headers = new HashMap<>();

        if ("ollama".equals(type)) {
            return headers; // 本地 Ollama 无需认证
        }

        String apiKey = extractApiKey(provider);
        if ("anthropic".equals(type)) {
            headers.put("x-api-key", apiKey);
            headers.put("anthropic-version", ANTHROPIC_VERSION);
        } else {
            // openai / openai_compatible / gemini（Gemini 也兼容 Bearer Token）
            headers.put("Authorization", "Bearer " + apiKey);
        }

        return headers;
    }

    private String extractApiKey(Provider provider) {
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

    // ============================================================
    // 响应解析
    // ============================================================

    private int parseModelCount(String body, String type) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            String field = "ollama".equals(type) ? "models" : "data";
            JsonNode arr = root.get(field);
            return arr != null && arr.isArray() ? arr.size() : 0;
        } catch (Exception e) {
            log.debug("解析模型数量失败 type={}", type, e);
            return 0;
        }
    }

    private String normalizeType(String type) {
        return type != null ? type.toLowerCase() : "";
    }
}
