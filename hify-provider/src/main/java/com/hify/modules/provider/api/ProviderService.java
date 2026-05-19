package com.hify.modules.provider.api;

import com.hify.common.web.PageResult;
import com.hify.modules.provider.api.dto.request.ProviderCreateRequest;
import com.hify.modules.provider.api.dto.request.ProviderListRequest;
import com.hify.modules.provider.api.dto.request.ProviderUpdateRequest;
import com.hify.modules.provider.api.dto.response.ProviderDetailResponse;
import com.hify.modules.provider.api.dto.response.ProviderListResponse;

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
    PageResult<ProviderListResponse> list(ProviderListRequest request);
}
