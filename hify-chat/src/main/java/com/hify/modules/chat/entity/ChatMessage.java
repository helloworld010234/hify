package com.hify.modules.chat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 对话消息实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_chat_message")
public class ChatMessage extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private Long sessionId;
    private String role;
    private String content;
    private Integer tokens;
}
