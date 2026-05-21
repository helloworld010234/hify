package com.hify.modules.knowledge.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 文档解析异步线程池配置
 * <p>
 * 文档处理（解析 → 切分 → 向量化 → 入库）耗时较长，使用独立线程池隔离，
 * 避免占满 Tomcat 工作线程或影响其他业务。
 */
@Configuration
public class DocumentParseThreadPoolConfig {

    @Bean("documentParseExecutor")
    public ThreadPoolExecutor documentParseExecutor() {
        return new ThreadPoolExecutor(
                2,                      // corePoolSize
                4,                      // maximumPoolSize
                60L,                    // keepAliveTime
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                r -> {
                    Thread t = new Thread(r, "doc-parse-" + System.nanoTime());
                    t.setDaemon(false);  // 非守护线程，确保任务执行完
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
