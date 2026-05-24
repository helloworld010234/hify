package com.hify.modules.chat.dto.response;

import lombok.Data;

/**
 * 会话创建响应
 */
@Data
public class ChatSessionResponse {

    private Long id;
    private Long agentId;
    private String title;
}
