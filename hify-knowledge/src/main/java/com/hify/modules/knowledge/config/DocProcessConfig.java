package com.hify.modules.knowledge.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文档异步处理线程池（切块 + 向量化是 CPU/IO 混合任务，独立隔离）。
 */
@Configuration
public class DocProcessConfig {

    @Bean
    @Qualifier("docProcessExecutor")
    public ThreadPoolExecutor docProcessExecutor() {
        return new ThreadPoolExecutor(
                2, 4, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new DocProcessThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    private static class DocProcessThreadFactory implements java.util.concurrent.ThreadFactory {
        private final String prefix = "doc-process-";
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
