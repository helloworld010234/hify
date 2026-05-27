package com.hify.modules.provider.service.impl;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.metrics.HifyMetrics;
import com.hify.modules.provider.api.LlmService;
import com.hify.modules.provider.dto.chat.ChatRequest;
import com.hify.modules.provider.dto.chat.ChatResponse;
import com.hify.common.service.EncryptionService;
import com.hify.modules.provider.service.adapter.ProviderAdapter;
import com.hify.modules.provider.service.adapter.ProviderAdapterFactory;
import com.hify.modules.provider.entity.ModelConfig;
import com.hify.modules.provider.entity.Provider;
import com.hify.modules.provider.mapper.ModelConfigMapper;
import com.hify.modules.provider.mapper.ProviderMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * LLM 调用服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmServiceImpl implements LlmService {

    private final ProviderAdapterFactory adapterFactory;
    private final ModelConfigMapper modelConfigMapper;
    private final ProviderMapper providerMapper;
    private final EncryptionService encryptionService;
    private final HifyMetrics metrics;

    @Override
    @CircuitBreaker(name = "llm-default")
    @Retry(name = "timeout-retry")
    public ChatResponse chat(Long modelConfigId, ChatRequest request) throws IOException {
        Provider provider = resolveProvider(modelConfigId);
        String providerType = provider.getProviderType();
        String model = request.getModel();
        log.info("action=llm_chat_start modelConfigId={} provider={} model={}", modelConfigId, providerType, model);
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            ProviderAdapter adapter = resolveAdapter(modelConfigId, request);
            ChatResponse response = adapter.chat(provider, request);
            success = true;
            return response;
        } finally {
            long duration = System.currentTimeMillis() - start;
            metrics.llmCallIncrement(providerType, model, success);
            metrics.llmCallDuration(providerType, model, duration);
            log.info("action=llm_chat_end modelConfigId={} provider={} model={} durationMs={} success={}",
                    modelConfigId, providerType, model, duration, success);
        }
    }

    @Override
    @CircuitBreaker(name = "llm-default")
    public void streamChat(Long modelConfigId, ChatRequest request,
                           Consumer<String> onDelta, Consumer<String> onFinish) throws IOException {
        Provider provider = resolveProvider(modelConfigId);
        String providerType = provider.getProviderType();
        String model = request.getModel();
        log.info("action=llm_stream_start modelConfigId={} provider={} model={}", modelConfigId, providerType, model);
        long start = System.currentTimeMillis();
        AtomicBoolean successRef = new AtomicBoolean(false);
        try {
            ProviderAdapter adapter = resolveAdapter(modelConfigId, request);
            adapter.streamChat(provider, request,
                    delta -> {
                        onDelta.accept(delta);
                    },
                    finishReason -> {
                        successRef.set(true);
                        log.info("action=llm_stream_end modelConfigId={} provider={} model={} durationMs={} finishReason={}",
                                modelConfigId, providerType, model, System.currentTimeMillis() - start, finishReason);
                        onFinish.accept(finishReason);
                    });
        } finally {
            long duration = System.currentTimeMillis() - start;
            metrics.llmCallIncrement(providerType, model, successRef.get());
            metrics.llmCallDuration(providerType, model, duration);
        }
    }

    private ProviderAdapter resolveAdapter(Long modelConfigId, ChatRequest request) {
        ModelConfig modelConfig = modelConfigMapper.selectById(modelConfigId);
        if (modelConfig == null || modelConfig.getDeleted() != null && modelConfig.getDeleted() == 1) {
            throw new BizException(ErrorCode.MODEL_CONFIG_NOT_FOUND, "模型配置不存在或已删除");
        }
        request.setModel(modelConfig.getModelName());
        Provider provider = providerMapper.selectById(modelConfig.getProviderId());
        if (provider == null || provider.getDeleted() != null && provider.getDeleted() == 1) {
            throw new BizException(ErrorCode.PROVIDER_NOT_FOUND, "供应商不存在或已禁用");
        }
        return adapterFactory.getAdapter(provider.getProviderType());
    }

    private Provider resolveProvider(Long modelConfigId) {
        ModelConfig modelConfig = modelConfigMapper.selectById(modelConfigId);
        if (modelConfig == null) {
            throw new BizException(ErrorCode.MODEL_CONFIG_NOT_FOUND, "模型配置不存在");
        }
        Provider provider = providerMapper.selectById(modelConfig.getProviderId());
        if (provider == null || provider.getDeleted() != null && provider.getDeleted() == 1) {
            throw new BizException(ErrorCode.PROVIDER_NOT_FOUND, "供应商不存在或已禁用");
        }
        provider.setApiKey(encryptionService.decrypt(provider.getApiKey()));
        return provider;
    }
}
