package com.hify.modules.provider.domain.adapter;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 供应商适配器工厂
 * <p>
 * 根据 {@code providerType} 返回对应的 {@link ProviderAdapter} 策略实例，
 * 彻底消除 ProviderService 中的 if-else / switch 分支。
 */
@Component
@RequiredArgsConstructor
public class ProviderAdapterFactory {

    private final OpenAiAdapter openAiAdapter;
    private final OpenAiCompatibleAdapter openAiCompatibleAdapter;
    private final AnthropicAdapter anthropicAdapter;
    private final OllamaAdapter ollamaAdapter;

    private static final Map<String, Class<? extends ProviderAdapter>> ADAPTER_MAP = Map.of(
            "openai", OpenAiAdapter.class,
            "openai_compatible", OpenAiCompatibleAdapter.class,
            "anthropic", AnthropicAdapter.class,
            "ollama", OllamaAdapter.class
    );

    /**
     * 根据供应商类型编码获取适配器实例
     *
     * @param providerType 协议类型编码，如 openai_compatible / anthropic / ollama
     * @return 对应的 ProviderAdapter 实例
     * @throws BizException 不支持的供应商类型
     */
    public ProviderAdapter getAdapter(String providerType) {
        if (providerType == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "供应商类型不能为空");
        }

        Class<? extends ProviderAdapter> adapterClass = ADAPTER_MAP.get(providerType.toLowerCase());
        if (adapterClass == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的供应商类型: " + providerType);
        }

        // 按类型返回 Spring 容器中已注册的单例 Bean
        if (adapterClass == OpenAiAdapter.class) {
            return openAiAdapter;
        }
        if (adapterClass == OpenAiCompatibleAdapter.class) {
            return openAiCompatibleAdapter;
        }
        if (adapterClass == AnthropicAdapter.class) {
            return anthropicAdapter;
        }
        if (adapterClass == OllamaAdapter.class) {
            return ollamaAdapter;
        }

        throw new BizException(ErrorCode.PARAM_ERROR, "不支持的供应商类型: " + providerType);
    }
}
