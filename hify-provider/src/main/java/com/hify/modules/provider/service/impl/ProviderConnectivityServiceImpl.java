package com.hify.modules.provider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.common.exception.LlmApiException;
import com.hify.common.http.LlmHttpClient;
import com.hify.modules.provider.adapter.ProviderAdapter;
import com.hify.modules.provider.adapter.ProviderAdapterFactory;
import com.hify.modules.provider.dto.ConnectionTestResult;
import com.hify.modules.provider.entity.Provider;
import com.hify.modules.provider.entity.ProviderHealth;
import com.hify.modules.provider.dto.ModelInfo;
import com.hify.modules.provider.mapper.ProviderHealthMapper;
import com.hify.modules.provider.service.ModelSyncService;
import com.hify.modules.provider.service.ProviderConnectivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderConnectivityServiceImpl implements ProviderConnectivityService {

    private final LlmHttpClient llmHttpClient;
    private final ProviderHealthMapper providerHealthMapper;
    private final ProviderAdapterFactory adapterFactory;
    private final ModelSyncService modelSyncService;

    @Override
    @Transactional
    public ConnectionTestResult test(Provider provider) {
        // 按 type 路由到对应供应商的 Adapter，由 Adapter 封装 URL / 认证头 / 响应解析差异
        ProviderAdapter adapter = adapterFactory.get(provider.getType());
        String url = adapter.buildUrl(provider.getBaseUrl());
        Map<String, String> headers = adapter.buildAuthHeaders(provider);

        long start = System.currentTimeMillis();
        ConnectionTestResult result;

        try {
            String body = llmHttpClient.get(url, headers);
            long latency = System.currentTimeMillis() - start;
            int count = adapter.parseModelCount(body);
            log.info("连通性测试成功 provider={} type={} latencyMs={} modelCount={}",
                    provider.getName(), provider.getType(), latency, count);
            result = ConnectionTestResult.ok(latency, count);

            // 保存健康状态：成功
            saveHealthStatus(provider.getId(), "UP", latency, null, true);

            // 同步模型列表到 t_model_config
            List<ModelInfo> models = adapter.parseModels(body);
            modelSyncService.sync(provider.getId(), models);
        } catch (LlmApiException e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("连通性测试失败 provider={} type={} latencyMs={} error={}",
                    provider.getName(), provider.getType(), latency, e.getMessage());
            result = ConnectionTestResult.fail(latency, e.getMessage());

            // 保存健康状态：失败
            saveHealthStatus(provider.getId(), "DOWN", latency, e.getMessage(), false);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("连通性测试异常 provider={} type={} latencyMs={} error={}",
                    provider.getName(), provider.getType(), latency, e.getMessage());
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
}
