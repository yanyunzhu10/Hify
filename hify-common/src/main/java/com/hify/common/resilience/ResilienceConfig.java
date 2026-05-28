package com.hify.common.resilience;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RetryProperties.class)
public class ResilienceConfig {
}
