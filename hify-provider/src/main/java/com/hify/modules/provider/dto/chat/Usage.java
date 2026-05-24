package com.hify.modules.provider.dto.chat;

import lombok.Data;

/**
 * Token 使用量
 */
@Data
public class Usage {

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
}
