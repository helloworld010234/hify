package com.hify.common.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CachePenetrationGuardTest {

    private CacheManager cacheManager;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOps;
    private CachePenetrationGuard guard;

    @BeforeEach
    void setUp() {
        cacheManager = new ConcurrentMapCacheManager("test-cache");
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        // 默认锁获取成功（测试防击穿时再单独模拟竞争）
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        guard = new CachePenetrationGuard(cacheManager, stringRedisTemplate);
    }

    @Test
    void shouldReturnCachedValueDirectly() {
        Cache cache = cacheManager.getCache("test-cache");
        assertNotNull(cache);
        cache.put("1", "cached-value");

        String result = guard.getOrLoad("test-cache", "1", () -> "db-value");

        assertEquals("cached-value", result);
    }

    @Test
    void shouldLoadAndCacheValueWhenCacheMiss() {
        AtomicInteger loadCount = new AtomicInteger(0);

        String result = guard.getOrLoad("test-cache", "1", () -> {
            loadCount.incrementAndGet();
            return "db-value";
        });

        assertEquals("db-value", result);
        assertEquals(1, loadCount.get());

        // 第二次应走缓存
        String result2 = guard.getOrLoad("test-cache", "1", () -> {
            loadCount.incrementAndGet();
            return "db-value";
        });
        assertEquals("db-value", result2);
        assertEquals(1, loadCount.get(), "loader 应只执行 1 次");
    }

    @Test
    void shouldCacheNullValueAndPreventPenetration() {
        AtomicInteger loadCount = new AtomicInteger(0);

        // 第一次：loader 返回 null
        String result1 = guard.getOrLoad("test-cache", "missing", () -> {
            loadCount.incrementAndGet();
            return null;
        }, Duration.ofSeconds(30));

        assertNull(result1);
        assertEquals(1, loadCount.get());

        // 模拟 NullValue 已写入 Redis
        when(stringRedisTemplate.hasKey("test-cache::missing:null")).thenReturn(true);

        // 第二次：应直接返回 null，不再执行 loader
        String result2 = guard.getOrLoad("test-cache", "missing", () -> {
            loadCount.incrementAndGet();
            return null;
        }, Duration.ofSeconds(30));

        assertNull(result2);
        assertEquals(1, loadCount.get(), "loader 应只执行 1 次（防穿透）");
    }

    @Test
    void shouldPreventCacheBreakdownWithLock() throws InterruptedException {
        int threadCount = 10;
        AtomicInteger loadCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // 模拟锁竞争：只有一个线程能获取锁
        AtomicInteger lockCounter = new AtomicInteger(0);
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenAnswer(inv -> lockCounter.incrementAndGet() == 1);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    guard.getOrLoad("test-cache", "hot-key", () -> {
                        loadCount.incrementAndGet();
                        sleepQuietly(100); // 模拟 DB 查询耗时
                        return "value";
                    });
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        // 防击穿：虽然 10 个线程并发，但 loader 应只执行 1 次（或少量兜底次数）
        assertTrue(loadCount.get() <= 3,
                "loader 执行次数应远小于线程数（防击穿），实际=" + loadCount.get());
    }

    @Test
    void shouldUseDefaultNullTtlWhenNotSpecified() {
        guard.getOrLoad("test-cache", "1", () -> null);

        // 验证写入 NullValue 时使用了默认 TTL（60s）
        verify(valueOps).set(eq("test-cache::1:null"), eq("1"), argThat(d -> d.getSeconds() == 60));
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
