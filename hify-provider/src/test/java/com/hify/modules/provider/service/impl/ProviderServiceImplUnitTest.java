package com.hify.modules.provider.service.impl;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.service.EncryptionService;

import com.hify.modules.provider.dto.request.ProviderCreateRequest;
import com.hify.modules.provider.dto.vo.AuthConfig;
import com.hify.modules.provider.entity.Provider;
import com.hify.modules.provider.mapper.ModelConfigMapper;
import com.hify.modules.provider.mapper.ProviderHealthMapper;
import com.hify.modules.provider.mapper.ProviderMapper;
import com.hify.modules.provider.service.adapter.ProviderAdapterFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderServiceImplUnitTest {

    @Mock
    private ProviderMapper providerMapper;

    @Mock
    private ModelConfigMapper modelConfigMapper;

    @Mock
    private ProviderHealthMapper providerHealthMapper;

    @Mock
    private ProviderAdapterFactory adapterFactory;

    @Mock
    private EncryptionService encryptionService;

    @InjectMocks
    private ProviderServiceImpl providerService;

    // ==================== autoGenerateCode ====================

    @Test
    void should_returnNormalizedCode_when_nameContainsSpecialChars() {
        // Given
        String name = "OpenAI Provider!@#";

        when(providerMapper.selectByCode(any())).thenReturn(null);

        // When
        String code = invokeAutoGenerateCode(name);

        // Then
        assertThat(code).isEqualTo("openai_provider");
    }

    @Test
    void should_appendTimestamp_when_codeAlreadyExists() {
        // Given
        String name = "duplicate";

        when(providerMapper.selectByCode("duplicate")).thenReturn(new Provider());

        // When
        String code = invokeAutoGenerateCode(name);

        // Then
        assertThat(code).startsWith("duplicate_");
        assertThat(code).hasSizeGreaterThan("duplicate".length());
    }

    @Test
    void should_returnDefaultCode_when_nameIsBlank() {
        // Given
        String name = "   ";

        // When
        String code = invokeAutoGenerateCode(name);

        // Then
        assertThat(code).startsWith("provider_");
    }

    // ==================== maskApiKey ====================

    @Test
    void should_maskMiddlePart_when_apiKeyLengthAtLeast12() {
        // Given
        String apiKey = "1234567890123456";

        // When
        String masked = invokeMaskApiKey(apiKey);

        // Then
        assertThat(masked).isEqualTo("123456****3456");
    }

    @Test
    void should_returnOriginal_when_apiKeyTooShort() {
        // Given
        String apiKey = "short";

        // When
        String masked = invokeMaskApiKey(apiKey);

        // Then
        assertThat(masked).isEqualTo("short");
    }

    @Test
    void should_returnNull_when_apiKeyIsBlank() {
        // Given
        String apiKey = null;

        // When
        String masked = invokeMaskApiKey(apiKey);

        // Then
        assertThat(masked).isNull();
    }

    // ==================== inferAuthType ====================

    @Test
    void should_returnApiKey_when_providerTypeContainsAnthropic() {
        // Given
        String providerType = "anthropic";

        // When
        String authType = invokeInferAuthType(providerType);

        // Then
        assertThat(authType).isEqualTo("api_key");
    }

    @Test
    void should_returnAzureApiKey_when_providerTypeContainsAzure() {
        // Given
        String providerType = "azure_openai";

        // When
        String authType = invokeInferAuthType(providerType);

        // Then
        assertThat(authType).isEqualTo("azure_api_key");
    }

    @Test
    void should_returnNone_when_providerTypeContainsOllama() {
        // Given
        String providerType = "ollama";

        // When
        String authType = invokeInferAuthType(providerType);

        // Then
        assertThat(authType).isEqualTo("none");
    }

    @Test
    void should_returnBearer_when_providerTypeIsGeneric() {
        // Given
        String providerType = "openai";

        // When
        String authType = invokeInferAuthType(providerType);

        // Then
        assertThat(authType).isEqualTo("bearer");
    }

    @Test
    void should_returnBearer_when_providerTypeIsBlank() {
        // Given
        String providerType = null;

        // When
        String authType = invokeInferAuthType(providerType);

        // Then
        assertThat(authType).isEqualTo("bearer");
    }

    // ==================== resolveApiKey (ProviderCreateRequest) ====================

    @Test
    void should_returnDirectApiKey_when_requestApiKeyPresent() {
        // Given
        ProviderCreateRequest request = new ProviderCreateRequest();
        request.setApiKey("direct-key");

        // When
        String key = invokeResolveApiKey(request);

        // Then
        assertThat(key).isEqualTo("direct-key");
    }

    @Test
    void should_returnAuthConfigApiKey_when_directApiKeyBlank() {
        // Given
        ProviderCreateRequest request = new ProviderCreateRequest();
        request.setApiKey("   ");
        AuthConfig authConfig = new AuthConfig();
        authConfig.setApiKey("config-key");
        request.setAuthConfig(authConfig);

        // When
        String key = invokeResolveApiKey(request);

        // Then
        assertThat(key).isEqualTo("config-key");
    }

    @Test
    void should_returnNull_when_noApiKeyAvailable() {
        // Given
        ProviderCreateRequest request = new ProviderCreateRequest();

        // When
        String key = invokeResolveApiKey(request);

        // Then
        assertThat(key).isNull();
    }

    // ==================== create (public method, indirect coverage) ====================

    @Test
    void should_createProvider_when_requestValid() {
        // Given
        ProviderCreateRequest request = new ProviderCreateRequest();
        request.setName("Test Provider");
        request.setBaseUrl("http://localhost:8080");
        request.setProviderType("openai");
        request.setStatus("active");
        request.setApiKey("sk-test-key");

        when(providerMapper.selectByCode(any())).thenReturn(null);
        when(providerMapper.selectCount(any())).thenReturn(0L);
        when(encryptionService.encrypt("sk-test-key")).thenReturn("encrypted-key");
        when(providerMapper.insert(any(Provider.class))).thenAnswer(inv -> {
            Provider p = inv.getArgument(0);
            p.setId(1L);
            return 1;
        });

        // When
        Long id = providerService.create(request);

        // Then
        assertThat(id).isEqualTo(1L);
        verify(providerMapper).insert(any(Provider.class));
        verify(encryptionService).encrypt("sk-test-key");
    }

    @Test
    void should_throwBizException_when_providerNameDuplicate() {
        // Given
        ProviderCreateRequest request = new ProviderCreateRequest();
        request.setName("Duplicate Name");
        request.setBaseUrl("http://localhost:8080");
        request.setStatus("active");
        request.setCode("code");

        when(providerMapper.selectByCode(any())).thenReturn(null);
        when(providerMapper.selectCount(any())).thenReturn(1L);

        // When & Then
        assertThatThrownBy(() -> providerService.create(request))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.PROVIDER_NAME_DUPLICATE);
                });
    }

    // ==================== getModelNameById (public method with branching) ====================

    @Test
    void should_returnModelName_when_modelAndProviderActive() {
        // Given
        Long modelConfigId = 1L;
        Long providerId = 10L;

        com.hify.modules.provider.entity.ModelConfig model = new com.hify.modules.provider.entity.ModelConfig();
        model.setId(modelConfigId);
        model.setModelName("gpt-4o");
        model.setProviderId(providerId);
        model.setDeleted(0);

        Provider provider = new Provider();
        provider.setId(providerId);
        provider.setDeleted(0);

        when(modelConfigMapper.selectById(modelConfigId)).thenReturn(model);
        when(providerMapper.selectById(providerId)).thenReturn(provider);

        // When
        String name = providerService.getModelNameById(modelConfigId);

        // Then
        assertThat(name).isEqualTo("gpt-4o");
    }

    @Test
    void should_returnNull_when_modelConfigNotFound() {
        // Given
        Long modelConfigId = 99L;

        when(modelConfigMapper.selectById(modelConfigId)).thenReturn(null);

        // When
        String name = providerService.getModelNameById(modelConfigId);

        // Then
        assertThat(name).isNull();
    }

    @Test
    void should_returnNull_when_providerDeleted() {
        // Given
        Long modelConfigId = 1L;
        Long providerId = 10L;

        com.hify.modules.provider.entity.ModelConfig model = new com.hify.modules.provider.entity.ModelConfig();
        model.setId(modelConfigId);
        model.setModelName("gpt-4o");
        model.setProviderId(providerId);
        model.setDeleted(0);

        Provider provider = new Provider();
        provider.setId(providerId);
        provider.setDeleted(1);

        when(modelConfigMapper.selectById(modelConfigId)).thenReturn(model);
        when(providerMapper.selectById(providerId)).thenReturn(provider);

        // When
        String name = providerService.getModelNameById(modelConfigId);

        // Then
        assertThat(name).isNull();
    }

    // ==================== 辅助方法：通过反射调用 private 方法 ====================

    private String invokeAutoGenerateCode(String name) {
        return ReflectionTestUtils.invokeMethod(providerService, "autoGenerateCode", name);
    }

    private String invokeMaskApiKey(String apiKey) {
        return ReflectionTestUtils.invokeMethod(providerService, "maskApiKey", apiKey);
    }

    private String invokeInferAuthType(String providerType) {
        return ReflectionTestUtils.invokeMethod(providerService, "inferAuthType", providerType);
    }

    private String invokeResolveApiKey(ProviderCreateRequest request) {
        return ReflectionTestUtils.invokeMethod(providerService, "resolveApiKey", request);
    }
}
