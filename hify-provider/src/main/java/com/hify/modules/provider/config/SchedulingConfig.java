package com.hify.modules.provider.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 调度配置。
 * <p>
 * asyncExecutor 由 {@link com.hify.common.config.ThreadPoolConfig} 全局定义，
 * 此处仅负责启用 @Scheduled 注解扫描。
 * </p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
