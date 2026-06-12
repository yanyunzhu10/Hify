package com.hify.common.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ThreadPoolConfig {

    @Bean
    @Qualifier("llmExecutor")
    public ThreadPoolExecutor llmExecutor() {
        return new MdcPropagatingThreadPoolExecutor(
                10, 50, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new NamedThreadFactory("llm-"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 流式 SSE 调用专用线程池（长连接）。
     * 超限直接拒绝（AbortPolicy），由上层返回 503，不用 CallerRunsPolicy
     * （流式任务跑在调用方线程会阻塞 Tomcat IO 线程）。
     */
    @Bean
    @Qualifier("llmStreamExecutor")
    public ThreadPoolExecutor llmStreamExecutor() {
        return new MdcPropagatingThreadPoolExecutor(
                30, 80, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50),
                new NamedThreadFactory("llm-stream-"),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Bean
    @Qualifier("asyncExecutor")
    public ThreadPoolExecutor asyncExecutor() {
        return new MdcPropagatingThreadPoolExecutor(
                5, 20, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                new NamedThreadFactory("async-"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    private static class MdcPropagatingThreadPoolExecutor extends ThreadPoolExecutor {

        MdcPropagatingThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                         TimeUnit unit, LinkedBlockingQueue<Runnable> workQueue,
                                         java.util.concurrent.ThreadFactory threadFactory,
                                         RejectedExecutionHandler handler) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        }

        @Override
        public void execute(Runnable command) {
            super.execute(wrap(command));
        }

        private Runnable wrap(Runnable command) {
            Map<String, String> captured = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                try {
                    if (captured == null || captured.isEmpty()) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(captured);
                    }
                    command.run();
                } finally {
                    if (previous == null || previous.isEmpty()) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(previous);
                    }
                }
            };
        }
    }

    private static class NamedThreadFactory implements java.util.concurrent.ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
