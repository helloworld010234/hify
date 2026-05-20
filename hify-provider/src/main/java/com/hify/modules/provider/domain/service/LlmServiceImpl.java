package com.hify.modules.provider.domain.service;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.provider.api.LlmService;
import com.hify.modules.provider.api.dto.chat.ChatRequest;
import com.hify.modules.provider.api.dto.chat.ChatResponse;
import com.hify.modules.provider.domain.adapter.ProviderAdapter;
import com.hify.modules.provider.domain.adapter.ProviderAdapterFactory;
import com.hify.modules.provider.infra.entity.ModelConfig;
import com.hify.modules.provider.infra.entity.Provider;
import com.hify.modules.provider.infra.mapper.ModelConfigMapper;
import com.hify.modules.provider.infra.mapper.ProviderMapper;
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

    @Override
    public ChatResponse chat(Long modelConfigId, ChatRequest request) throws IOException {
        ProviderAdapter adapter = resolveAdapter(modelConfigId, request);
        return adapter.chat(resolveProvider(modelConfigId), request);
    }

    @Override
    public void streamChat(Long modelConfigId, ChatRequest request,
                           Consumer<String> onDelta, Consumer<String> onFinish) throws IOException {
        ProviderAdapter adapter = resolveAdapter(modelConfigId, request);
        adapter.streamChat(resolveProvider(modelConfigId), request, onDelta, onFinish);
    }

    private ProviderAdapter resolveAdapter(Long modelConfigId, ChatRequest request) {
        ModelConfig modelConfig = modelConfigMapper.selectById(modelConfigId);
        if (modelConfig == null || modelConfig.getDeleted() != null && modelConfig.getDeleted() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型配置不存在或已删除");
        }
        request.setModel(modelConfig.getModelName());
        Provider provider = providerMapper.selectById(modelConfig.getProviderId());
        if (provider == null || provider.getDeleted() != null && provider.getDeleted() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "供应商不存在或已禁用");
        }
        return adapterFactory.getAdapter(provider.getProviderType());
    }

    private Provider resolveProvider(Long modelConfigId) {
        ModelConfig modelConfig = modelConfigMapper.selectById(modelConfigId);
        if (modelConfig == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型配置不存在");
        }
        Provider provider = providerMapper.selectById(modelConfig.getProviderId());
        if (provider == null || provider.getDeleted() != null && provider.getDeleted() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "供应商不存在或已禁用");
        }
        return provider;
    }
}
