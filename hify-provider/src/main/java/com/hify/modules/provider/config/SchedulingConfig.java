package com.hify.modules.provider.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 调度与异步线程池配置。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    /**
     * 健康检查专用线程池，避免阻塞调度主线程。
     * 供应商数量有限（通常 &lt; 20），小池即可满足每分钟一轮的并发探测。
     */
    @Bean("asyncExecutor")
    public ThreadPoolExecutor asyncExecutor() {
        return new ThreadPoolExecutor(
                2, 4,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50),
                new ThreadFactoryBuilder().setNameFormat("health-check-%d").setDaemon(true).build(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
