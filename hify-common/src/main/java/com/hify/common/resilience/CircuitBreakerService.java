package com.hify.common.resilience;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.exception.LlmApiException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * 熔断 + 自定义重试封装：
 *   - 熔断器按 providerName 动态创建（首次访问时基于 default 配置生成实例）
 *   - 重试策略按 {@link LlmApiException.Type} 分类：
 *       TIMEOUT      → 固定间隔重试 N 次
 *       RATE_LIMITED → 退避序列重试（2s、4s）
 *       AUTH_FAILED  → 不重试
 *       OTHER        → 不重试（让熔断器累积失败率）
 *   - 每次重试是独立的熔断器调用，CB 会按真实失败数统计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CircuitBreakerService {

    private final CircuitBreakerRegistry registry;
    private final RetryProperties retryProperties;

    /** 获取或创建 providerName 对应的熔断器（不存在时按 default 配置创建） */
    public CircuitBreaker get(String providerName) {
        return registry.circuitBreaker(providerName);
    }

    /** 执行带熔断 + 自定义重试的同步调用 */
    public <T> T executeWithRetry(String providerName, Supplier<T> supplier) {
        int timeoutRetries = 0;
        int rateLimitRetries = 0;

        while (true) {
            try {
                return get(providerName).executeSupplier(supplier);
            } catch (CallNotPermittedException e) {
                // 熔断器 OPEN 时快速失败，不重试
                log.warn("CircuitBreaker OPEN，拒绝调用 provider={}", providerName);
                throw e;
            } catch (LlmApiException e) {
                switch (e.getType()) {
                    case AUTH_FAILED -> {
                        log.warn("LLM AUTH_FAILED 不重试 provider={} status={}",
                                providerName, e.getHttpStatus());
                        throw e;
                    }
                    case TIMEOUT -> {
                        RetryProperties.Timeout policy = retryProperties.getTimeout();
                        if (timeoutRetries >= policy.getMaxAttempts()) {
                            log.warn("LLM TIMEOUT 重试已耗尽 provider={} attempts={}",
                                    providerName, timeoutRetries);
                            throw e;
                        }
                        timeoutRetries++;
                        Duration wait = policy.getInterval();
                        log.warn("LLM TIMEOUT 第{}次重试 provider={} wait={}ms",
                                timeoutRetries, providerName, wait.toMillis());
                        sleep(wait);
                    }
                    case RATE_LIMITED -> {
                        List<Duration> backoffs = retryProperties.getRateLimit().getBackoffs();
                        if (rateLimitRetries >= backoffs.size()) {
                            log.warn("LLM RATE_LIMITED 退避已耗尽 provider={} attempts={}",
                                    providerName, rateLimitRetries);
                            throw e;
                        }
                        Duration wait = backoffs.get(rateLimitRetries);
                        rateLimitRetries++;
                        log.warn("LLM RATE_LIMITED 第{}次退避 provider={} wait={}ms",
                                rateLimitRetries, providerName, wait.toMillis());
                        sleep(wait);
                    }
                    default -> {
                        log.warn("LLM 调用失败（type=OTHER）不重试 provider={} status={}",
                                providerName, e.getHttpStatus());
                        throw e;
                    }
                }
            }
        }
    }

    private void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "重试等待被中断", e);
        }
    }
}
