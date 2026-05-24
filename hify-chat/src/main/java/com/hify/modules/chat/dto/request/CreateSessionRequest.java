package com.hify.modules.chat.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建会话请求
 */
@Data
public class CreateSessionRequest {

    @NotNull(message = "必须指定 Agent ID")
    private Long agentId;
}
