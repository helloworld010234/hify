package com.hify.modules.provider.domain.adapter;

import com.hify.modules.provider.infra.client.LlmHttpClient;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容适配器
 * <p>
 * 与 {@link OpenAiAdapter} 共用完全相同的逻辑，仅用于工厂映射区分。
 * 适用：DeepSeek、Kimi、通义千问、豆包、Gemini、智谱等所有 OpenAI 兼容供应商。
 */
@Component
public class OpenAiCompatibleAdapter extends OpenAiAdapter {

    public OpenAiCompatibleAdapter(LlmHttpClient httpClient) {
        super(httpClient);
    }
}
