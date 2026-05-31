package com.hify.modules.provider.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * 启用 Spring Cache（provider 模块使用 Redis 做缓存后端）。
 * <p>
 * CacheManager 由 spring-boot-starter-data-redis 自动装配，
 * 此处仅负责开启声明式缓存注解扫描。
 * </p>
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
