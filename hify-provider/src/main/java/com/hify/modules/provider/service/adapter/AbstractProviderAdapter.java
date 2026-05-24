package com.hify.modules.provider.service.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.modules.provider.dto.response.ConnectionTestResponse;
import com.hify.modules.provider.client.LlmHttpClient;
import com.hify.modules.provider.entity.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 供应商适配器抽象基类
 * <p>
 * 统一实现 {@link #testConnection(Provider)} 的计时与结果包装逻辑，
 * 子类只需实现 {@link #listModels(Provider)} 即可。
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractProviderAdapter implements ProviderAdapter {

    protected final LlmHttpClient httpClient;
    protected final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ConnectionTestResponse testConnection(Provider provider) {
        long start = System.currentTimeMillis();
        try {
            List<ConnectionTestResponse.ModelInfo> models = listModels(provider);
            long latency = System.currentTimeMillis() - start;
            return ConnectionTestResponse.success(latency, models.size(), models);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("Provider connection test failed: code={}, type={}, error={}",
                    provider.getCode(), provider.getProviderType(), e.getMessage());
            return ConnectionTestResponse.fail(latency, e.getMessage());
        }
    }
}
