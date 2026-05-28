package com.hify.common.resilience;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@Data
@ConfigurationProperties("hify.resilience.retry")
public class RetryProperties {

    private Timeout timeout = new Timeout();

    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class Timeout {
        /** 网络超时的最大重试次数（不含首次调用） */
        private int maxAttempts = 2;
        /** 每次重试前的固定等待 */
        private Duration interval = Duration.ofSeconds(1);
    }

    @Data
    public static class RateLimit {
        /** 限流退避序列；列表长度即最大重试次数，元素是每次重试前的等待 */
        private List<Duration> backoffs = List.of(Duration.ofSeconds(2), Duration.ofSeconds(4));
    }
}
