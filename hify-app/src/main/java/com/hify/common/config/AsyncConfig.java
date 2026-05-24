package com.hify.common.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 异步线程池配置
 */
@Configuration
public class AsyncConfig {

    /**
     * LLM 流式调用线程池（SSE 长连接）
     * <p>
     * readTimeout=0 的流式请求需要独立线程池隔离，防止阻塞常规业务线程。
     */
    @Bean("llmStreamExecutor")
    public ThreadPoolExecutor llmStreamExecutor() {
        return new ThreadPoolExecutor(
                30,
                80,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50),
                new ThreadFactoryBuilder().setNameFormat("llm-stream-%d").setDaemon(true).build(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    /**
     * LLM 非流式调用线程池
     */
    @Bean("llmExecutor")
    public ThreadPoolExecutor llmExecutor() {
        return new ThreadPoolExecutor(
                4,
                20,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new ThreadFactoryBuilder().setNameFormat("llm-%d").setDaemon(true).build(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 通用异步线程池
     */
    @Bean("asyncExecutor")
    public ThreadPoolExecutor asyncExecutor() {
        return new ThreadPoolExecutor(
                2,
                10,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50),
                new ThreadFactoryBuilder().setNameFormat("async-%d").setDaemon(true).build(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
