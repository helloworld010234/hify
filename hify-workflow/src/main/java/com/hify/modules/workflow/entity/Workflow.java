package com.hify.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流定义实体（对应 t_workflow 表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_workflow")
public class Workflow extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 工作流名称
     */
    private String name;

    /**
     * 工作流描述
     */
    private String description;

    /**
     * 是否启用：1-启用 0-禁用
     */
    private Integer enabled;
}
