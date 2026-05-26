package com.hify.modules.provider.service.impl;

import com.hify.common.service.EncryptionService;
import com.hify.modules.provider.dto.response.ConnectionTestResponse;
import com.hify.modules.provider.entity.Provider;
import com.hify.modules.provider.service.adapter.ProviderAdapterFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 供应商连通性测试服务（轻量级）。
 *
 * <p>职责边界：仅执行 HTTP 连通性测试并返回结果，<b>不</b>执行以下副作用：
 * <ul>
 *   <li>不更新供应商主表健康状态</li>
 *   <li>不同步远程模型列表到 t_model</li>
 *   <li>不写入 t_provider_health（由调用方决定）</li>
 * </ul>
 *
 * <p>适用于定时健康检查等高频场景，避免每次检查都触发模型同步等重操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderConnectionTestService {

    private final ProviderAdapterFactory adapterFactory;
    private final EncryptionService encryptionService;

    /**
     * 对给定供应商执行连通性测试。
     *
     * @param provider 供应商实体（apiKey 为密文）
     * @return 测试结果
     */
    public ConnectionTestResponse test(Provider provider) {
        log.debug("连通性测试 provider={} type={}", provider.getCode(), provider.getProviderType());

        // 临时解密 apiKey 供 Adapter 使用
        String encryptedKey = provider.getApiKey();
        provider.setApiKey(encryptionService.decrypt(encryptedKey));

        try {
            return adapterFactory.getAdapter(provider.getProviderType())
                    .testConnection(provider);
        } finally {
            // 恢复加密状态，避免误存明文
            provider.setApiKey(encryptedKey);
        }
    }
}
