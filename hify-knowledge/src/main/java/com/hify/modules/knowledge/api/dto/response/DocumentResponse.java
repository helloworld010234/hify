package com.hify.modules.knowledge.api.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档响应
 */
@Data
public class DocumentResponse {

    private Long id;
    private Long knowledgeBaseId;
    private String name;
    private String fileType;
    private Long fileSize;

    /**
     * 解析状态：PENDING / PROCESSING / DONE / FAILED
     */
    private String status;

    private Integer chunkCount;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
