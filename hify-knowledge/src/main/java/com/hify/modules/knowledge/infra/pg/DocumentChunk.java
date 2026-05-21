package com.hify.modules.knowledge.infra.pg;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档分块（对应 PostgreSQL document_chunk 表）
 */
@Data
public class DocumentChunk {

    private Long id;
    private Long knowledgeBaseId;
    private Long documentId;
    private Integer chunkIndex;
    private String content;

    /**
     * 查询时动态计算的余弦相似度（0~1，越接近 1 越相关）
     */
    private Double similarity;

    private Integer tokenCount;
    private Integer deleted;
    private LocalDateTime createdAt;

    /**
     * Embedding 向量（仅在插入/更新时使用，查询时不返回）
     */
    private float[] embedding;
}
