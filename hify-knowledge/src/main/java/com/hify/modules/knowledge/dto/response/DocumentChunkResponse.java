package com.hify.modules.knowledge.dto.response;

import lombok.Data;

/**
 * 文档分块响应
 */
@Data
public class DocumentChunkResponse {

    private Long id;
    private Integer chunkIndex;
    private String content;
    private Integer tokenCount;
    private Double similarity;
}
