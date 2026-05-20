package com.hify.modules.chat.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 对话会话实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_chat_session")
public class ChatSession extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private Long agentId;
    private String title;
    private Long userId;
}
