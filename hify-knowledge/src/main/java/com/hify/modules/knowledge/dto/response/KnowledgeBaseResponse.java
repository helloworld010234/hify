package com.hify.modules.knowledge.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库响应
 */
@Data
public class KnowledgeBaseResponse {

    private Long id;
    private String name;
    private String description;
    private Integer enabled;
    private Integer documentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
