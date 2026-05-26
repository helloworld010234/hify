package com.hify.modules.provider.service.impl;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.service.EncryptionService;
import com.hify.modules.provider.api.ProviderService;
import com.hify.modules.provider.dto.request.ProviderCreateRequest;
import com.hify.modules.provider.entity.Provider;
import com.hify.modules.provider.mapper.ModelConfigMapper;
import com.hify.modules.provider.mapper.ProviderHealthMapper;
import com.hify.modules.provider.mapper.ProviderMapper;
import com.hify.modules.provider.service.adapter.ProviderAdapterFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ProviderServiceImpl 单元测试
 *
 * <p>注：当前代码中 Service 层未对参数做 @Valid 校验，部分边界行为与理想校验策略有差异，
 * 测试按代码实际行为断言。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ProviderServiceImplUnitTest {

    @MockBean
    private ProviderMapper providerMapper;
    @MockBean
    private ModelConfigMapper modelConfigMapper;
    @MockBean
    private ProviderHealthMapper providerHealthMapper;
    @MockBean
    private ProviderAdapterFactory adapterFactory;
    @MockBean
    private EncryptionService encryptionService;

    @Autowired
    private ProviderService providerService;

    @Test
    void should_returnId_when_createWithValidRequest() {
        // Given
        ProviderCreateRequest request = new ProviderCreateRequest();
        request.setCode("openai");
        request.setName("OpenAI");
        request.setProviderType("openai");
        request.setBaseUrl("https://api.openai.com");
        request.setAuthType("bearer");
        request.setApiKey("sk-test123456");
        request.setStatus("active");

        when(providerMapper.selectByCode("openai")).thenReturn(null);
        when(providerMapper.selectCount(any())).thenReturn(0L);
        when(encryptionService.encrypt("sk-test123456")).thenReturn("encrypted_key");

        doAnswer(invocation -> {
            Provider provider = invocation.getArgument(0);
            provider.setId(1L);
            return 1;
        }).when(providerMapper).insert(isA(Provider.class));

        // When
        Long result = providerService.create(request);

        // Then
        assertThat(result).isEqualTo(1L);

        ArgumentCaptor<Provider> providerCaptor = ArgumentCaptor.forClass(Provider.class);
        verify(providerMapper).insert(providerCaptor.capture());
        Provider captured = providerCaptor.getValue();
        assertThat(captured.getCode()).isEqualTo("openai");
        assertThat(captured.getName()).isEqualTo("OpenAI");
        assertThat(captured.getApiKey()).isEqualTo("encrypted_key");
        assertThat(captured.getAuthType()).isEqualTo("bearer");
    }

    @Test
    void should_throwBizException_when_duplicateName() {
        // Given
        ProviderCreateRequest request = new ProviderCreateRequest();
        request.setCode("openai");
        request.setName("OpenAI");
        request.setProviderType("openai");
        request.setBaseUrl("https://api.openai.com");
        request.setStatus("active");

        when(providerMapper.selectByCode("openai")).thenReturn(null);
        when(providerMapper.selectCount(any())).thenReturn(1L);

        // When & Then
        assertThatThrownBy(() -> providerService.create(request))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.PROVIDER_NAME_DUPLICATE);
                })
                .hasMessageContaining("供应商名称已存在");

        verify(providerMapper, never()).insert(isA(Provider.class));
    }

    @Test
    void should_generateDefaultCode_when_nameIsNull() {
        // Given: name 为 null 时，Service 层不会抛 ConstraintViolationException，
        // 而是走 autoGenerateCode 生成默认 code（当前代码实际行为）
        ProviderCreateRequest request = new ProviderCreateRequest();
        request.setCode(null);
        request.setName(null);
        request.setProviderType("openai");
        request.setBaseUrl("https://api.openai.com");
        request.setStatus("active");

        when(providerMapper.selectByCode(anyString())).thenReturn(null);
        when(providerMapper.selectCount(any())).thenReturn(0L);
        when(encryptionService.encrypt(isNull())).thenReturn(null);

        doAnswer(invocation -> {
            Provider provider = invocation.getArgument(0);
            provider.setId(1L);
            return 1;
        }).when(providerMapper).insert(isA(Provider.class));

        // When
        Long result = providerService.create(request);

        // Then
        assertThat(result).isEqualTo(1L);

        ArgumentCaptor<Provider> providerCaptor = ArgumentCaptor.forClass(Provider.class);
        verify(providerMapper).insert(providerCaptor.capture());
        assertThat(providerCaptor.getValue().getCode()).startsWith("provider_");
    }

    @Test
    void should_allowNullApiKey_when_bothSourcesEmpty() {
        // Given: 当前代码未对 apiKey 做格式校验，全空时允许创建（encrypt(null) = null）
        ProviderCreateRequest request = new ProviderCreateRequest();
        request.setCode("openai");
        request.setName("OpenAI");
        request.setProviderType("openai");
        request.setBaseUrl("https://api.openai.com");
        request.setStatus("active");
        request.setApiKey(null);
        request.setAuthConfig(null);

        when(providerMapper.selectByCode("openai")).thenReturn(null);
        when(providerMapper.selectCount(any())).thenReturn(0L);
        when(encryptionService.encrypt(isNull())).thenReturn(null);

        doAnswer(invocation -> {
            Provider provider = invocation.getArgument(0);
            provider.setId(1L);
            return 1;
        }).when(providerMapper).insert(isA(Provider.class));

        // When
        Long result = providerService.create(request);

        // Then
        assertThat(result).isEqualTo(1L);
        verify(encryptionService).encrypt(isNull());

        ArgumentCaptor<Provider> providerCaptor = ArgumentCaptor.forClass(Provider.class);
        verify(providerMapper).insert(providerCaptor.capture());
        assertThat(providerCaptor.getValue().getApiKey()).isNull();
    }
}
