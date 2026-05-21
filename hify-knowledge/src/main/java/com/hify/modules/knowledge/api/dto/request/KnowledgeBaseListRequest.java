package com.hify.modules.knowledge.api.dto.request;

import lombok.Data;

/**
 * 知识库分页查询请求
 */
@Data
public class KnowledgeBaseListRequest {

    /**
     * 当前页
     */
    private long page = 1;

    /**
     * 每页大小
     */
    private long size = 10;

    /**
     * 名称模糊搜索关键字
     */
    private String name;
}
