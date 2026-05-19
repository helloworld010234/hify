package com.hify.modules.provider.domain.adapter;

import com.hify.modules.provider.api.dto.response.ConnectionTestResult;
import com.hify.modules.provider.infra.entity.Provider;

import java.io.IOException;
import java.util.List;

/**
 * 供应商适配器接口（策略模式）
 * <p>
 * 每个 LLM 供应商实现此接口，封装该供应商特有的：
 * - 连通性测试（HTTP 端点、鉴权头、响应解析）
 * - 模型列表拉取
 */
public interface ProviderAdapter {

    /**
     * 执行连通性测试并返回结果（含延迟、模型数量、模型列表）
     */
    ConnectionTestResult testConnection(Provider provider);

    /**
     * 从远程拉取该供应商下的模型列表
     */
    List<ConnectionTestResult.ModelInfo> listModels(Provider provider) throws IOException;
}
