package com.hify.modules.knowledge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建知识库请求
 */
@Data
public class KnowledgeBaseCreateRequest {

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 100, message = "名称最多 100 个字符")
    private String name;

    @Size(max = 500, message = "描述最多 500 个字符")
    private String description = "";
}
