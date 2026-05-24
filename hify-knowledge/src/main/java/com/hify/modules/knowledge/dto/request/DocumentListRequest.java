package com.hify.modules.knowledge.dto.request;

import lombok.Data;

/**
 * 文档分页查询请求
 */
@Data
public class DocumentListRequest {

    /**
     * 当前页
     */
    private long page = 1;

    /**
     * 每页大小
     */
    private long size = 10;
}
