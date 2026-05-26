package com.hify.modules.provider.service.impl;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.service.EncryptionService;

import com.hify.modules.provider.dto.chat.ChatRequest;
import com.hify.modules.provider.entity.ModelConfig;
import com.hify.modules.provider.entity.Provider;
import com.hify.modules.provider.mapper.ModelConfigMapper;
import com.hify.modules.provider.mapper.ProviderMapper;
import com.hify.modules.provider.service.adapter.ProviderAdapter;
import com.hify.modules.provider.service.adapter.ProviderAdapterFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmServiceImplTest {

    @Mock
    private ProviderAdapterFactory adapterFactory;

    @Mock
    private ModelConfigMapper modelConfigMapper;

    @Mock
    private ProviderMapper providerMapper;

    @Mock
    private EncryptionService encryptionService;

    @InjectMocks
    private LlmServiceImpl llmService;

    // ==================== resolveAdapter 正常路径 ====================

    @Test
    void should_returnAdapter_when_modelAndProviderValid() {
        // Given
        Long modelConfigId = 1L;
        Long providerId = 10L;
        String modelName = "gpt-4o";
        String providerType = "openai";

        ChatRequest request = new ChatRequest();

        ModelConfig modelConfig = new ModelConfig();
        modelConfig.setId(modelConfigId);
        modelConfig.setModelName(modelName);
        modelConfig.setProviderId(providerId);
        modelConfig.setDeleted(0);

        Provider provider = new Provider();
        provider.setId(providerId);
        provider.setProviderType(providerType);
        provider.setDeleted(0);

        ProviderAdapter adapter = mock(ProviderAdapter.class);

        when(modelConfigMapper.selectById(modelConfigId)).thenReturn(modelConfig);
        when(providerMapper.selectById(providerId)).thenReturn(provider);
        when(adapterFactory.getAdapter(providerType)).thenReturn(adapter);

        // When
        ProviderAdapter result = invokeResolveAdapter(modelConfigId, request);

        // Then
        assertThat(result).isNotNull();
        verify(modelConfigMapper).selectById(modelConfigId);
        verify(providerMapper).selectById(providerId);
        verify(adapterFactory).getAdapter(providerType);
    }

    // ==================== resolveAdapter 异常路径 ====================

    @Test
    void should_throwBizException_when_modelNotFound() {
        // Given
        Long modelConfigId = 99L;
        ChatRequest request = new ChatRequest();

        when(modelConfigMapper.selectById(modelConfigId)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> invokeResolveAdapter(modelConfigId, request))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.MODEL_CONFIG_NOT_FOUND);
                })
                .hasMessageContaining("模型配置不存在或已删除");

        verify(modelConfigMapper).selectById(modelConfigId);
        verifyNoInteractions(providerMapper, adapterFactory);
    }

    @Test
    void should_throwBizException_when_providerDeleted() {
        // Given
        Long modelConfigId = 1L;
        Long providerId = 10L;

        ChatRequest request = new ChatRequest();

        ModelConfig modelConfig = new ModelConfig();
        modelConfig.setId(modelConfigId);
        modelConfig.setModelName("gpt-4o");
        modelConfig.setProviderId(providerId);
        modelConfig.setDeleted(0);

        Provider provider = new Provider();
        provider.setId(providerId);
        provider.setDeleted(1);

        when(modelConfigMapper.selectById(modelConfigId)).thenReturn(modelConfig);
        when(providerMapper.selectById(providerId)).thenReturn(provider);

        // When & Then
        assertThatThrownBy(() -> invokeResolveAdapter(modelConfigId, request))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.PROVIDER_NOT_FOUND);
                })
                .hasMessageContaining("供应商不存在或已禁用");

        verify(modelConfigMapper).selectById(modelConfigId);
        verify(providerMapper).selectById(providerId);
        verifyNoInteractions(adapterFactory);
    }

    // ==================== 辅助方法 ====================

    private ProviderAdapter invokeResolveAdapter(Long modelConfigId, ChatRequest request) {
        return org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                llmService,
                "resolveAdapter",
                modelConfigId,
                request
        );
    }
}
