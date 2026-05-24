package com.hify.modules.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 流式发送消息请求
 */
@Data
public class ChatStreamRequest {

    @NotBlank(message = "消息内容不能为空")
    private String message;

    private String contextStrategy;
}
