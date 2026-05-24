package com.hify.modules.knowledge.dto;

import lombok.Data;

/**
 * 文档分块中间对象（管线内部传递）
 */
@Data
public class ChunkDTO {

    /**
     * 分块序号（从 0 开始）
     */
    private Integer chunkIndex;

    /**
     * 分块文本内容
     */
    private String content;

    /**
     * Token 估算数
     */
    private Integer tokenCount;

    /**
     * Embedding 向量（向量化环节后填充）
     */
    private float[] embedding;
}
