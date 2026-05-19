package com.hify.modules.agent.api.dto.response;

import lombok.Data;

/**
 * 工具选项（用于前端多选绑定）
 */
@Data
public class ToolOption {

    private Long id;
    private String name;
    private String description;
}
