package com.hify.modules.provider.service.impl;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
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

    @Override
    @CircuitBreaker(name = "llm-default")
    @Retry(name = "timeout-retry")
    public ChatResponse chat(Long modelConfigId, ChatRequest request) throws IOException {
        log.info("action=llm_chat_start modelConfigId={} model={}", modelConfigId, request.getModel());
        long start = System.currentTimeMillis();
        try {
            ProviderAdapter adapter = resolveAdapter(modelConfigId, request);
            ChatResponse response = adapter.chat(resolveProvider(modelConfigId), request);
            log.info("action=llm_chat_end modelConfigId={} model={} durationMs={} finishReason={}",
                    modelConfigId, request.getModel(), System.currentTimeMillis() - start, response.getFinishReason());
            return response;
        } catch (Exception e) {
            log.warn("action=llm_chat_error modelConfigId={} model={} durationMs={} error={}",
                    modelConfigId, request.getModel(), System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }

    @Override
    @CircuitBreaker(name = "llm-default")
    public void streamChat(Long modelConfigId, ChatRequest request,
                           Consumer<String> onDelta, Consumer<String> onFinish) throws IOException {
        log.info("action=llm_stream_start modelConfigId={} model={}", modelConfigId, request.getModel());
        long start = System.currentTimeMillis();
        try {
            ProviderAdapter adapter = resolveAdapter(modelConfigId, request);
            adapter.streamChat(resolveProvider(modelConfigId), request,
                    delta -> {
                        onDelta.accept(delta);
                    },
                    finishReason -> {
                        log.info("action=llm_stream_end modelConfigId={} model={} durationMs={} finishReason={}",
                                modelConfigId, request.getModel(), System.currentTimeMillis() - start, finishReason);
                        onFinish.accept(finishReason);
                    });
        } catch (Exception e) {
            log.warn("action=llm_stream_error modelConfigId={} model={} durationMs={} error={}",
                    modelConfigId, request.getModel(), System.currentTimeMillis() - start, e.getMessage());
            throw e;
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
