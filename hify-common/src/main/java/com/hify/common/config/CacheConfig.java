package com.hify.common.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Spring Cache 配置（开发环境）。
 * <p>
 * 开发环境使用内存缓存，避免本地开发时依赖 Redis。
 * 生产环境由 {@link RedisConfig} 提供 RedisCacheManager。
 */
@Configuration
@EnableCaching
@Profile("dev")
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager();
    }
}
