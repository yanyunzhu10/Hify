package com.hify.modules.provider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.exception.LlmApiException;
import com.hify.common.http.LlmHttpClient;
import com.hify.modules.provider.dto.ConnectionTestResult;
import com.hify.modules.provider.entity.Provider;
import com.hify.modules.provider.entity.ProviderHealth;
import com.hify.modules.provider.mapper.ProviderHealthMapper;
import com.hify.modules.provider.service.ProviderConnectivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderConnectivityServiceImpl implements ProviderConnectivityService {

    private final LlmHttpClient llmHttpClient;
    private final ProviderHealthMapper providerHealthMapper;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Override
    @Transactional
    public ConnectionTestResult test(Provider provider) {
        String type = normalizeType(provider.getType());
        String url = buildUrl(provider.getBaseUrl(), type);
        Map<String, String> headers = buildAuthHeaders(provider, type);

        long start = System.currentTimeMillis();
        ConnectionTestResult result;

        try {
            String body = llmHttpClient.get(url, headers);
            long latency = System.currentTimeMillis() - start;
            int count = parseModelCount(body, type);
            log.info("连通性测试成功 provider={} type={} latencyMs={} modelCount={}",
                    provider.getName(), type, latency, count);
            result = ConnectionTestResult.ok(latency, count);

            // 保存健康状态：成功
            saveHealthStatus(provider.getId(), "UP", latency, null, true);
        } catch (LlmApiException e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("连通性测试失败 provider={} type={} latencyMs={} error={}",
                    provider.getName(), type, latency, e.getMessage());
            result = ConnectionTestResult.fail(latency, e.getMessage());

            // 保存健康状态：失败
            saveHealthStatus(provider.getId(), "DOWN", latency, e.getMessage(), false);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("连通性测试异常 provider={} type={} latencyMs={} error={}",
                    provider.getName(), type, latency, e.getMessage());
            result = ConnectionTestResult.fail(latency, e.getMessage());

            // 保存健康状态：异常
            saveHealthStatus(provider.getId(), "DOWN", latency, e.getMessage(), false);
        }

        return result;
    }

    // ============================================================
    // 健康状态保存
    // ============================================================

    /**
     * 保存或更新健康状态。
     *
     * @param providerId   供应商 ID
     * @param status       健康状态：UP / DOWN / DEGRADED / UNKNOWN
     * @param latencyMs    延迟（毫秒）
     * @param errorMessage 错误信息（成功时为 null）
     * @param success      是否成功
     */
    private void saveHealthStatus(Long providerId, String status, long latencyMs,
                                   String errorMessage, boolean success) {
        LocalDateTime now = LocalDateTime.now();

        // 查询是否已存在健康状态记录
        ProviderHealth health = providerHealthMapper.selectOne(
                new LambdaQueryWrapper<ProviderHealth>()
                        .eq(ProviderHealth::getProviderId, providerId)
        );

        if (health == null) {
            // 新建健康状态记录
            health = new ProviderHealth();
            health.setProviderId(providerId);
            health.setStatus(status);
            health.setLastCheckAt(now);
            health.setLastSuccessAt(success ? now : null);
            health.setFailCount(success ? 0 : 1);
            health.setLatencyMs((int) latencyMs);
            health.setErrorMessage(errorMessage);
            health.setUpdatedAt(now);  // 手动设置 updated_at
            providerHealthMapper.insert(health);
        } else {
            // 更新健康状态记录
            health.setStatus(status);
            health.setLastCheckAt(now);
            if (success) {
                health.setLastSuccessAt(now);
                health.setFailCount(0);
                health.setErrorMessage(null);
            } else {
                health.setFailCount(health.getFailCount() != null ? health.getFailCount() + 1 : 1);
                health.setErrorMessage(errorMessage);
            }
            health.setLatencyMs((int) latencyMs);
            health.setUpdatedAt(now);  // 手动设置 updated_at
            providerHealthMapper.updateById(health);
        }

        log.debug("健康状态已保存 providerId={} status={} latencyMs={} failCount={}",
                providerId, status, latencyMs, health.getFailCount());
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
