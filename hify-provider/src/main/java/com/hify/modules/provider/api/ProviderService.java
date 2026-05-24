package com.hify.modules.provider.api;

import com.hify.common.controller.PageResult;
import com.hify.common.controller.Result;
import com.hify.modules.provider.dto.ModelDto;
import com.hify.modules.provider.dto.request.ProviderCreateRequest;
import com.hify.modules.provider.dto.request.ProviderListRequest;
import com.hify.modules.provider.dto.request.ProviderUpdateRequest;
import com.hify.modules.provider.dto.response.ProviderDetailResponse;
import com.hify.modules.provider.dto.response.ProviderListResponse;

import java.util.List;

/**
 * Provider 管理 Service 接口
 * <p>
 * 供本模块 Controller 和其他模块调用。
 */
public interface ProviderService {

    /**
     * 创建供应商
     *
     * @param request 创建请求
     * @return 新建供应商 ID
     */
    Long create(ProviderCreateRequest request);

    /**
     * 更新供应商
     *
     * @param id      供应商 ID
     * @param request 更新请求
     */
    void update(Long id, ProviderUpdateRequest request);

    /**
     * 删除供应商（逻辑删除）
     *
     * @param id 供应商 ID
     */
    void delete(Long id);

    /**
     * 获取供应商详情（含关联模型列表和健康状态）
     *
     * @param id 供应商 ID
     * @return 详情响应
     */
    ProviderDetailResponse getById(Long id);

    /**
     * 分页查询供应商列表
     *
     * @param request 列表查询条件
     * @return 分页结果
     */
    Result<PageResult<ProviderListResponse>> list(ProviderListRequest request);

    /**
     * 连通性测试
     *
     * @param id 供应商 ID
     * @return 测试结果（success、latencyMs、modelCount、errorMessage）
     */
    com.hify.modules.provider.dto.response.ConnectionTestResponse testConnection(Long id);

    /**
     * 根据模型配置 ID 查询模型名称
     *
     * @param modelConfigId 模型配置 ID（t_model.id）
     * @return 模型名称，不存在或已删除返回 null
     */
    String getModelNameById(Long modelConfigId);

    /**
     * 查询所有可用供应商（不分页，用于下拉选择等辅助场景）
     */
    List<ProviderListResponse> listAllActiveProviders();

    /**
     * 查询所有可用模型（不分页，用于下拉选择等辅助场景）
     */
    List<ModelDto> listAllActiveModels();

    /**
     * 获取供应商原始 API Key（明文，仅限内部服务使用）
     *
     * @param id 供应商 ID
     * @return API Key 明文，不存在返回 null
     */
    String getApiKey(Long id);
}
