package com.hify.common.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 为 llmExecutor / asyncExecutor 注册 Micrometer Gauge 指标。
 * 实现 {@link MeterBinder} 确保 Spring Boot 在 MeterRegistry 完全配置后（含 PrometheusRegistry）
 * 统一调用 {@link #bindTo(MeterRegistry)}，避免 CompositeMeterRegistry 时机问题。
 *
 * <p>指标命名：{@code hify_thread_pool_{metric}}，tag {@code pool=llm|async}
 */
@Component
public class ThreadPoolMetrics implements MeterBinder {

    private final ThreadPoolExecutor llmExecutor;
    private final ThreadPoolExecutor asyncExecutor;

    public ThreadPoolMetrics(@Qualifier("llmExecutor") ThreadPoolExecutor llmExecutor,
                             @Qualifier("asyncExecutor") ThreadPoolExecutor asyncExecutor) {
        this.llmExecutor = llmExecutor;
        this.asyncExecutor = asyncExecutor;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Map.of("llm", llmExecutor, "async", asyncExecutor)
                .forEach((poolName, executor) -> bindPoolMetrics(registry, poolName, executor));
    }

    private void bindPoolMetrics(MeterRegistry registry, String poolName, ThreadPoolExecutor executor) {
        Tags tags = Tags.of("pool", poolName);

        Gauge.builder("hify_thread_pool_active", executor, ThreadPoolExecutor::getActiveCount)
                .tags(tags)
                .description("Current number of active threads")
                .register(registry);

        Gauge.builder("hify_thread_pool_queue_size", executor, e -> e.getQueue().size())
                .tags(tags)
                .description("Current number of tasks in the queue")
                .register(registry);

        Gauge.builder("hify_thread_pool_completed", executor, ThreadPoolExecutor::getCompletedTaskCount)
                .tags(tags)
                .description("Total number of completed tasks")
                .register(registry);

        Gauge.builder("hify_thread_pool_size", executor, ThreadPoolExecutor::getPoolSize)
                .tags(tags)
                .description("Current pool size")
                .register(registry);

        Gauge.builder("hify_thread_pool_max", executor, ThreadPoolExecutor::getMaximumPoolSize)
                .tags(tags)
                .description("Maximum pool size")
                .register(registry);

        Gauge.builder("hify_thread_pool_core", executor, ThreadPoolExecutor::getCorePoolSize)
                .tags(tags)
                .description("Core pool size")
                .register(registry);
    }
}
