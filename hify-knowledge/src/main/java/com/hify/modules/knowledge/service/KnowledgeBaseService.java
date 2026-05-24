package com.hify.modules.knowledge.service;

import com.hify.common.controller.PageResult;
import com.hify.common.controller.Result;
import com.hify.modules.knowledge.dto.request.KnowledgeBaseCreateRequest;
import com.hify.modules.knowledge.dto.request.KnowledgeBaseListRequest;
import com.hify.modules.knowledge.dto.request.KnowledgeBaseUpdateRequest;
import com.hify.modules.knowledge.dto.response.KnowledgeBaseResponse;

/**
 * 知识库管理 Service 接口
 */
public interface KnowledgeBaseService {

    /**
     * 创建知识库
     */
    KnowledgeBaseResponse create(KnowledgeBaseCreateRequest request);

    /**
     * 更新知识库
     */
    void update(Long id, KnowledgeBaseUpdateRequest request);

    /**
     * 删除知识库（逻辑删除，同时级联删除关联的 document）
     */
    void delete(Long id);

    /**
     * 查询知识库详情
     */
    KnowledgeBaseResponse getById(Long id);

    /**
     * 分页查询知识库列表
     */
    Result<PageResult<KnowledgeBaseResponse>> list(KnowledgeBaseListRequest request);
}
