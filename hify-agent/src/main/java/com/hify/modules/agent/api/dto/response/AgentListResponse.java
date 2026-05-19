package com.hify.modules.agent.api.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 列表项响应
 */
@Data
public class AgentListResponse {

    private Long id;
    private String name;
    private String description;

    /**
     * 绑定的模型配置 ID
     */
    private Long modelConfigId;

    /**
     * 模型显示名称（跨模块查询填充）
     */
    private String modelName;

    /**
     * 是否启用：1-启用 0-禁用
     */
    private Integer enabled;

    /**
     * 关联知识库数量
     */
    private Integer knowledgeCount;

    /**
     * 关联工具数量
     */
    private Integer toolCount;

    private LocalDateTime createdAt;
}
