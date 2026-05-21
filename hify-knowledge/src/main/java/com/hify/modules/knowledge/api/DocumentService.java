package com.hify.modules.knowledge.api;

import com.hify.common.web.PageResult;
import com.hify.modules.knowledge.api.dto.request.DocumentListRequest;
import com.hify.modules.knowledge.api.dto.response.DocumentChunkResponse;
import com.hify.modules.knowledge.api.dto.response.DocumentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档管理 Service 接口
 */
public interface DocumentService {

    /**
     * 上传文档
     *
     * @param kbId 知识库 ID
     * @param file 文件
     * @return 文档 ID
     */
    Long upload(Long kbId, MultipartFile file);

    /**
     * 分页查询知识库下的文档列表
     */
    PageResult<DocumentResponse> list(Long kbId, DocumentListRequest request);

    /**
     * 查询文档详情
     */
    DocumentResponse getById(Long id);

    /**
     * 查询文档的分块列表
     */
    List<DocumentChunkResponse> listChunks(Long id);

    /**
     * 删除文档（逻辑删除，级联删除 pgvector chunk）
     */
    void delete(Long id);
}
