package com.hify.modules.provider.config;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * Provider 基础设施配置
 * <p>
 * 集中管理 OkHttpClient Bean，避免在业务类中硬编码构造逻辑。
 */
@Configuration
public class ProviderInfraConfig {

    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final int READ_TIMEOUT_SECONDS = 120;
    private static final int WRITE_TIMEOUT_SECONDS = 10;
    private static final int MAX_IDLE_CONNECTIONS = 20;
    private static final int KEEP_ALIVE_DURATION_MINUTES = 5;

    /**
     * 标准 HTTP 客户端（非流式请求）
     */
    @Primary
    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE_DURATION_MINUTES, TimeUnit.MINUTES))
                .build();
    }

    /**
     * SSE 流式 HTTP 客户端
     * <p>
     * readTimeout=0 表示无限等待服务端推送；不设 callTimeout，
     * 由上层 SseEmitter / 业务超时控制总时长。
     */
    @Bean("streamOkHttpClient")
    public OkHttpClient streamOkHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE_DURATION_MINUTES, TimeUnit.MINUTES))
                .build();
    }
}
