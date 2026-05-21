package com.hify.modules.workflow.domain.nodeconfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 节点配置解析器。
 * <p>
 * 根据节点类型 code 将 JSON 字符串反序列化为对应的强类型 record。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeConfigParser {

    private final ObjectMapper objectMapper;

    /**
     * 解析节点配置 JSON。
     *
     * @param type       节点类型 code（如 "LLM" / "CONDITION"）
     * @param configJson 配置 JSON 字符串
     * @return 具体类型的 NodeConfig 实例
     */
    public NodeConfig parse(String type, String configJson) {
        NodeConfigType nodeType = NodeConfigType.fromCode(type);
        if (nodeType == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "未知节点类型: " + type);
        }

        try {
            return objectMapper.readValue(configJson, nodeType.getConfigClass());
        } catch (JsonProcessingException e) {
            log.warn("节点配置解析失败, type={}, json={}", type, configJson, e);
            throw new BizException(ErrorCode.PARAM_ERROR,
                "节点配置解析失败 [" + type + "]: " + e.getOriginalMessage());
        }
    }

    /**
     * 将 NodeConfig 序列化为 JSON 字符串。
     *
     * @param config 节点配置对象
     * @return JSON 字符串
     */
    public String serialize(NodeConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            log.warn("节点配置序列化失败", e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "节点配置序列化失败");
        }
    }
}
