package com.hify.modules.provider.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.modules.provider.dto.ConnectionTestResult;
import com.hify.modules.provider.entity.Provider;
import com.hify.modules.provider.entity.ProviderHealth;
import com.hify.modules.provider.mapper.ProviderHealthMapper;
import com.hify.modules.provider.mapper.ProviderMapper;
import com.hify.modules.provider.service.ProviderConnectivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 供应商健康检查定时任务。
 * <p>
 * 每分钟遍历所有启用的 provider，通过连通性测试判断健康状态。
 * 成功：status=UP、fail_count 归零；连续失败 3 次：status=DOWN。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderHealthScheduler {

    private final ProviderMapper providerMapper;
    private final ProviderHealthMapper healthMapper;
    private final ProviderConnectivityService connectivityService;

    @Qualifier("asyncExecutor")
    private final ThreadPoolExecutor asyncExecutor;

    /** 连续失败达到此次数时标记为 DOWN */
    private static final int DOWN_THRESHOLD = 3;

    @Scheduled(fixedRate = 60_000, initialDelay = 15_000)
    public void checkAll() {
        List<Provider> providers = providerMapper.selectList(
                new LambdaQueryWrapper<Provider>().eq(Provider::getEnabled, 1)
        );

        if (providers.isEmpty()) {
            log.debug("健康检查跳过：无启用的供应商");
            return;
        }

        log.info("健康检查开始 providerCount={}", providers.size());
        for (Provider provider : providers) {
            asyncExecutor.execute(() -> checkOne(provider));
        }
    }

    // ============================================================
    // 单次检查与状态更新
    // ============================================================

    private void checkOne(Provider provider) {
        try {
            ConnectionTestResult result = connectivityService.test(provider);
            updateHealth(provider.getId(), result);
        } catch (Exception e) {
            log.error("健康检查异常 provider={} id={}", provider.getName(), provider.getId(), e);
        }
    }

    private void updateHealth(Long providerId, ConnectionTestResult result) {
        ProviderHealth health = findOrCreate(providerId);

        health.setLastCheckAt(LocalDateTime.now());

        if (result.isSuccess()) {
            health.setStatus("UP");
            health.setLatencyMs((int) result.getLatencyMs());
            health.setLastSuccessAt(LocalDateTime.now());
            health.setFailCount(0);
            health.setErrorMessage(null);
        } else {
            int failCount = health.getFailCount() != null ? health.getFailCount() + 1 : 1;
            health.setFailCount(failCount);
            health.setLatencyMs((int) result.getLatencyMs());
            health.setErrorMessage(result.getErrorMessage());
            health.setStatus(failCount >= DOWN_THRESHOLD ? "DOWN" : "DEGRADED");
        }

        upsert(health);
    }

    // ============================================================
    // 持久化辅助
    // ============================================================

    /** 根据 providerId 查找已有记录，不存在则创建空记录。 */
    private ProviderHealth findOrCreate(Long providerId) {
        ProviderHealth exist = healthMapper.selectOne(
                new LambdaQueryWrapper<ProviderHealth>().eq(ProviderHealth::getProviderId, providerId)
        );
        if (exist != null) {
            return exist;
        }
        ProviderHealth fresh = new ProviderHealth();
        fresh.setProviderId(providerId);
        fresh.setStatus("UNKNOWN");
        fresh.setFailCount(0);
        return fresh;
    }

    /** id 为空则 insert，否则 update。 */
    private void upsert(ProviderHealth health) {
        if (health.getId() == null) {
            healthMapper.insert(health);
        } else {
            healthMapper.updateById(health);
        }
    }
}
