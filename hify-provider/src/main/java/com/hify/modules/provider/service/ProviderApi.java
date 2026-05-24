package com.hify.modules.provider.service;

import com.hify.modules.provider.dto.ModelDto;
import com.hify.modules.provider.dto.ProviderDto;
import com.hify.modules.provider.dto.ProviderHealthDto;

import java.util.List;

/**
 * Provider 模块对外暴露的 API 接口
 * <p>
 * 其他模块（agent、chat、workflow）只能通过此接口访问 Provider 模块，
 * 禁止直接引用 Provider 模块的 domain/ 或 infra/ 类。
 */
public interface ProviderApi {

    /**
     * 根据模型 ID 获取完整模型信息（含供应商配置）
     *
     * @param modelId 模型ID
     * @return 模型信息，包含供应商基础信息
     */
    ModelDto getModelById(Long modelId);

    /**
     * 根据模型编码获取模型信息
     *
     * @param modelCode 模型编码，如 "gpt-4o"
     * @return 模型信息
     */
    ModelDto getModelByCode(String modelCode);

    /**
     * 获取所有可用模型列表（供前端下拉选择）
     * <p>
     * 返回条件：供应商 active + 模型 active + 健康状态可用
     */
    List<ModelDto> listAvailableModels();

    /**
     * 获取指定供应商下的所有可用模型
     */
    List<ModelDto> listModelsByProviderCode(String providerCode);

    /**
     * 获取供应商完整配置（含模型列表）
     *
     * @param providerCode 供应商编码
     * @return 供应商配置（不含明文 apiKey）
     */
    ProviderDto getProviderByCode(String providerCode);

    /**
     * 获取所有供应商健康状态（供管理后台看板展示）
     */
    List<ProviderHealthDto> listHealthStatus();

    /**
     * 触发指定供应商的健康检查
     *
     * @param providerCode 供应商编码
     * @return 健康状态结果
     */
    ProviderHealthDto checkHealth(String providerCode);

    /**
     * 获取指定模型的 Fallback 模型（熔断时自动切换）
     *
     * @param modelId 原模型ID
     * @return Fallback 模型信息，无配置时返回 null
     */
    ModelDto getFallbackModel(Long modelId);
}
