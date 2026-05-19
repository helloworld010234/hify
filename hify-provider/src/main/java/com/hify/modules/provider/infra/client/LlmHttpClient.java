package com.hify.modules.provider.infra.client;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * LLM HTTP 客户端封装
 * <p>
 * 基于 OkHttp，统一处理超时、请求头和响应读取。
 * 所有 LLM 供应商的 HTTP 调用均通过此类发起，便于集中管理连接池和拦截器。
 */
@Slf4j
@Component
public class LlmHttpClient {

    private final OkHttpClient client;

    public LlmHttpClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .connectionPool(new okhttp3.ConnectionPool(20, 5, TimeUnit.MINUTES))
                .build();
    }

    /**
     * 执行 GET 请求
     *
     * @param url     请求地址
     * @param headers 请求头
     * @return 响应体字符串
     * @throws IOException 网络或 HTTP 异常
     */
    public String get(String url, Map<String, String> headers) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).get();
        if (headers != null) {
            headers.forEach(builder::header);
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.warn("HTTP error: url={}, code={}, body={}", url, response.code(), body);
                throw new IOException("HTTP " + response.code() + ": " + body);
            }
            return body;
        }
    }

    /**
     * 执行 POST 请求（JSON 体）
     *
     * @param url     请求地址
     * @param headers 请求头
     * @param jsonBody JSON 请求体
     * @return 响应体字符串
     * @throws IOException 网络或 HTTP 异常
     */
    public String post(String url, Map<String, String> headers, String jsonBody) throws IOException {
        okhttp3.RequestBody body = okhttp3.RequestBody.create(
                jsonBody, okhttp3.MediaType.parse("application/json")
        );
        Request.Builder builder = new Request.Builder().url(url).post(body);
        if (headers != null) {
            headers.forEach(builder::header);
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.warn("HTTP error: url={}, code={}, body={}", url, response.code(), respBody);
                throw new IOException("HTTP " + response.code() + ": " + respBody);
            }
            return respBody;
        }
    }
}
