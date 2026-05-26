package com.hify.common.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cache-Aside 防穿透/击穿守卫。
 *
 * <p>核心行为：
 * <ol>
 *   <li>先查 Spring Cache（命中正常值直接返回；命中 {@link NullValue} 返回 null）</li>
 *   <li>未命中 → Redis SETNX 获取互斥锁（key + ":lock"）</li>
 *   <li>获取锁成功 → 双重检查缓存 → 执行 loader →
 *       结果 null 则写入 {@link NullValue}（短 TTL），非 null 则写入 Spring Cache</li>
 *   <li>获取锁失败 → 短暂自旋重试查缓存（3 次，间隔 100ms）→ 仍无则直接执行 loader（兜底）</li>
 * </ol>
 *
 * <p>与 Spring Cache 的兼容性：
 * <ul>
 *   <li>正常值通过 {@link CacheManager} 读写，复用现有 RedisCache 序列化配置</li>
 *   <li>NullValue 通过 {@link StringRedisTemplate} 直接写入，绕过 {@code disableCachingNullValues()} 限制</li>
 *   <li>key 格式与 Spring Cache 保持一致：{@code cacheName::key}</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CachePenetrationGuard {

    private static final String LOCK_SUFFIX = ":lock";
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final int RETRY_TIMES = 3;
    private static final long RETRY_INTERVAL_MS = 100;

    private final CacheManager cacheManager;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 从缓存获取值，未命中时通过 loader 加载并写入缓存。
     *
     * @param cacheName Spring Cache 名称（如 {@code "agent-cache"}）
     * @param key       缓存 key（如 {@code "1"}）
     * @param loader    加载器，返回 null 表示 DB 中不存在
     * @param nullTtl   空值标记的存活时间（建议 30s~2min）
     * @return 缓存值或 loader 返回值；若 DB 不存在则返回 null
     */
    public <T> T getOrLoad(String cacheName, String key, Supplier<T> loader, Duration nullTtl) {
        String fullKey = cacheName + "::" + key;

        // 1. 查 Spring Cache
        T cached = getFromCache(cacheName, key);
        if (cached != null || isNullValueCached(fullKey)) {
            log.debug("Cache hit, cacheName={}, key={}", cacheName, key);
            return cached;
        }

        // 2. 获取分布式锁
        String lockKey = fullKey + LOCK_SUFFIX;
        boolean locked = tryLock(lockKey);

        if (locked) {
            try {
                // 3. 双重检查
                cached = getFromCache(cacheName, key);
                if (cached != null || isNullValueCached(fullKey)) {
                    return cached;
                }

                // 4. 执行 loader
                T result = loader.get();

                if (result == null) {
                    // 防穿透：缓存空值标记（短 TTL）
                    writeNullValue(fullKey, nullTtl);
                    log.debug("Cache null value, cacheName={}, key={}, nullTtl={}s", cacheName, key, nullTtl.getSeconds());
                } else {
                    // 写入 Spring Cache（复用现有 TTL 配置）
                    Cache cache = cacheManager.getCache(cacheName);
                    if (cache != null) {
                        cache.put(key, result);
                    }
                }
                return result;
            } finally {
                unlock(lockKey);
            }
        }

        // 5. 未获取锁 → 自旋重试查缓存
        for (int i = 0; i < RETRY_TIMES; i++) {
            sleepQuietly(RETRY_INTERVAL_MS);
            cached = getFromCache(cacheName, key);
            if (cached != null || isNullValueCached(fullKey)) {
                return cached;
            }
        }

        // 6. 兜底：直接执行 loader（不缓存，避免异常场景污染缓存）
        log.warn("Cache lock missed after retry, executing loader directly, cacheName={}, key={}", cacheName, key);
        return loader.get();
    }

    /**
     * 重载方法，空值 TTL 默认为 60 秒。
     */
    public <T> T getOrLoad(String cacheName, String key, Supplier<T> loader) {
        return getOrLoad(cacheName, key, loader, Duration.ofMinutes(1));
    }

    // ── 内部工具 ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private <T> T getFromCache(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return null;
        }
        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper == null) {
            return null;
        }
        Object value = wrapper.get();
        if (value == NullValue.INSTANCE) {
            return null;
        }
        return (T) value;
    }

    private boolean isNullValueCached(String fullKey) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(fullKey + ":null"));
        } catch (Exception e) {
            log.warn("Failed to check null value key, fullKey={}", fullKey, e);
            return false;
        }
    }

    private void writeNullValue(String fullKey, Duration nullTtl) {
        try {
            String nullKey = fullKey + ":null";
            stringRedisTemplate.opsForValue().set(nullKey, "1", nullTtl);
        } catch (Exception e) {
            log.warn("Failed to write null value, fullKey={}", fullKey, e);
        }
    }

    private boolean tryLock(String lockKey) {
        try {
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", LOCK_TTL);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("Failed to acquire cache lock, lockKey={}", lockKey, e);
            return false;
        }
    }

    private void unlock(String lockKey) {
        try {
            stringRedisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.warn("Failed to release cache lock, lockKey={}", lockKey, e);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
