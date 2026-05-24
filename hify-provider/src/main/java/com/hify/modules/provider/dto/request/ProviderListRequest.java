package com.hify.modules.provider.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 供应商列表查询请求
 */
@Data
public class ProviderListRequest {

    /**
     * 当前页码
     */
    @JsonProperty("page")
    private Integer current = 1;

    /**
     * 每页大小
     */
    @JsonProperty("size")
    private Integer size = 20;

    /**
     * 按协议类型筛选：openai_compatible / anthropic / azure_openai / ollama
     */
    private String providerType;

    /**
     * 按人工状态筛选：active / inactive
     */
    private String status;

    /**
     * 按名称或编码模糊搜索
     */
    private String keyword;
}
