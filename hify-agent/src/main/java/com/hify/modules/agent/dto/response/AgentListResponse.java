package com.hify.modules.agent.dto.response;

import lombok.Data;

import java.util.List;
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
     * 绑定的知识库 ID
     */
    private Long knowledgeBaseId;

    /**
     * 模型显示名称（跨模块查询填充）
     */
    private String modelName;

    private String systemPrompt;

    private Integer maxContextTurns;

    private Integer maxTokens;

    private java.math.BigDecimal temperature;

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

    /**
     * 关联的工具 ID 列表（用于列表页快捷编辑）
     */
    private List<Long> toolIds;

    private LocalDateTime createdAt;
}
