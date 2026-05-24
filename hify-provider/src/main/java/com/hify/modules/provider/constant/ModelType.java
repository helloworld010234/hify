package com.hify.modules.provider.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 模型类型
 */
@Getter
@RequiredArgsConstructor
public enum ModelType {

    CHAT("chat", "对话模型"),
    EMBEDDING("embedding", "嵌入模型"),
    VISION("vision", "视觉模型"),
    REASONING("reasoning", "推理模型"),
    MULTIMODAL("multimodal", "多模态模型");

    private final String code;
    private final String label;

    public static ModelType fromCode(String code) {
        for (ModelType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown model type: " + code);
    }
}
