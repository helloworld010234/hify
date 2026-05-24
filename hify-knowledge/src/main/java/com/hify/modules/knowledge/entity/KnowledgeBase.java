package com.hify.modules.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库实体（对应 knowledge_base 表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_knowledge_base")
public class KnowledgeBase extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 知识库名称
     */
    private String name;

    /**
     * 知识库描述
     */
    private String description;

    /**
     * 是否启用：1-启用 0-禁用
     */
    private Integer enabled;
}
