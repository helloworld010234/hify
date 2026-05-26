package com.hify.common.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ThreadPoolConfigTest {

    @Test
    void shouldRegisterAllSixGaugesViaMeterBinder() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        ThreadPoolExecutor llmPool = new ThreadPoolExecutor(
                2, 4, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(10),
                new NamedThreadFactory("test-llm-"),
                new ThreadPoolExecutor.AbortPolicy()
        );

        ThreadPoolExecutor asyncPool = new ThreadPoolExecutor(
                1, 3, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(5),
                new NamedThreadFactory("test-async-"),
                new ThreadPoolExecutor.AbortPolicy()
        );

        ThreadPoolMetrics metrics = new ThreadPoolMetrics(llmPool, asyncPool);
        metrics.bindTo(registry);

        // 6 metrics × 2 pools = 12 meters
        List<Meter> meters = registry.getMeters().stream()
                .filter(m -> m.getId().getName().startsWith("hify_thread_pool_"))
                .toList();

        assertEquals(12, meters.size(), "应注册 12 个线程池指标（6 metric × 2 pool）");

        // 验证指标名称
        List<String> expectedNames = List.of(
                "hify_thread_pool_active",
                "hify_thread_pool_completed",
                "hify_thread_pool_core",
                "hify_thread_pool_max",
                "hify_thread_pool_queue_size",
                "hify_thread_pool_size"
        );

        for (String name : expectedNames) {
            long count = meters.stream().filter(m -> m.getId().getName().equals(name)).count();
            assertEquals(2, count, "指标 " + name + " 应对应 2 个 pool");
        }

        // 验证 tag
        for (String pool : List.of("llm", "async")) {
            for (String name : expectedNames) {
                Gauge gauge = registry.find(name).tag("pool", pool).gauge();
                assertNotNull(gauge, "指标 " + name + " 应存在 pool=" + pool + " 的标签实例");
            }
        }

        llmPool.shutdown();
        asyncPool.shutdown();
    }

    @Test
    void shouldReflectActiveAndCompletedCount() throws InterruptedException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CountDownLatch latch = new CountDownLatch(1);

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 4, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(10),
                new NamedThreadFactory("metrics-"),
                new ThreadPoolExecutor.AbortPolicy()
        );

        ThreadPoolMetrics metrics = new ThreadPoolMetrics(pool, pool); // 复用同一个 pool 便于测试
        metrics.bindTo(registry);

        pool.execute(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread.sleep(100);

        Gauge activeGauge = registry.find("hify_thread_pool_active").tag("pool", "llm").gauge();
        assertNotNull(activeGauge);
        assertEquals(1.0, activeGauge.value(), 0.01, "应有一个活跃线程");

        Gauge completedGauge = registry.find("hify_thread_pool_completed").tag("pool", "llm").gauge();
        assertNotNull(completedGauge);
        assertEquals(0.0, completedGauge.value(), 0.01, "尚未完成任务");

        latch.countDown();
        Thread.sleep(100);

        assertEquals(0.0, activeGauge.value(), 0.01, "活跃线程应归零");
        assertEquals(1.0, completedGauge.value(), 0.01, "应记录 1 个已完成任务");

        pool.shutdown();
    }

    @Test
    void shouldExposeStaticConfigurationGauges() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                3, 7, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(20),
                new NamedThreadFactory("static-"),
                new ThreadPoolExecutor.AbortPolicy()
        );

        ThreadPoolMetrics metrics = new ThreadPoolMetrics(pool, pool);
        metrics.bindTo(registry);

        Gauge coreGauge = registry.find("hify_thread_pool_core").tag("pool", "llm").gauge();
        Gauge maxGauge = registry.find("hify_thread_pool_max").tag("pool", "llm").gauge();
        Gauge sizeGauge = registry.find("hify_thread_pool_size").tag("pool", "llm").gauge();

        assertNotNull(coreGauge);
        assertNotNull(maxGauge);
        assertNotNull(sizeGauge);

        assertEquals(3.0, coreGauge.value(), 0.01);
        assertEquals(7.0, maxGauge.value(), 0.01);
        assertEquals(0.0, sizeGauge.value(), 0.01, "无任务时池大小为 0");

        pool.shutdown();
    }

    @Test
    void shouldReflectQueueSize() throws InterruptedException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CountDownLatch block = new CountDownLatch(1);

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 2, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(10),
                new NamedThreadFactory("queue-"),
                new ThreadPoolExecutor.AbortPolicy()
        );

        ThreadPoolMetrics metrics = new ThreadPoolMetrics(pool, pool);
        metrics.bindTo(registry);

        // 占满唯一工作线程
        pool.execute(() -> {
            try {
                block.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // 再提交 2 个排队任务
        pool.execute(() -> {});
        pool.execute(() -> {});

        Thread.sleep(50);

        Gauge queueGauge = registry.find("hify_thread_pool_queue_size").tag("pool", "llm").gauge();
        assertNotNull(queueGauge);
        assertEquals(2.0, queueGauge.value(), 0.01, "队列应有 2 个积压任务");

        block.countDown();
        pool.shutdown();
    }
}
