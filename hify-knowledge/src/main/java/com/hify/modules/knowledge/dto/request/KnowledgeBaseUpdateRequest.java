package com.hify.modules.knowledge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新知识库请求
 */
@Data
public class KnowledgeBaseUpdateRequest {

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 100, message = "名称最多 100 个字符")
    private String name;

    @Size(max = 500, message = "描述最多 500 个字符")
    private String description = "";

    /**
     * 是否启用：1-启用 0-禁用
     */
    private Integer enabled = 1;
}
