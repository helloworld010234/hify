package com.hify.modules.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文档实体（对应 document 表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_document")
public class Document extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 所属知识库 ID
     */
    private Long knowledgeBaseId;

    /**
     * 原始文件名
     */
    private String name;

    /**
     * 文件类型：pdf / word / txt / md
     */
    private String fileType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 解析状态：PENDING / PROCESSING / DONE / FAILED
     */
    private String status;

    /**
     * 解析失败原因
     */
    private String errorMessage;

    /**
     * 分块数量
     */
    private Integer chunkCount;
}
