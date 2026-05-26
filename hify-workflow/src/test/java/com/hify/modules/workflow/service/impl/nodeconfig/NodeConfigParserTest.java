package com.hify.modules.workflow.service.impl.nodeconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class NodeConfigParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NodeConfigParser parser = new NodeConfigParser(objectMapper);

    @Test
    void should_returnCorrectConfigType_when_knownNodeType() {
        // Given
        String type = "LLM";
        String configJson = "{\"modelConfigId\":1,\"prompt\":\"Hello\",\"outputVariable\":\"out\"}";

        // When
        NodeConfig config = parser.parse(type, configJson);

        // Then
        assertThat(config).isInstanceOf(LlmNodeConfig.class);
        LlmNodeConfig llmConfig = (LlmNodeConfig) config;
        assertThat(llmConfig.modelConfigId()).isEqualTo(1L);
        assertThat(llmConfig.prompt()).isEqualTo("Hello");
        assertThat(llmConfig.outputVariable()).isEqualTo("out");
    }

    @Test
    void should_returnStartConfig_when_startNodeType() {
        // Given
        String type = "START";
        String configJson = "{\"inputVariables\":[]}";

        // When
        NodeConfig config = parser.parse(type, configJson);

        // Then
        assertThat(config).isInstanceOf(StartNodeConfig.class);
    }

    @Test
    void should_throwBizException_when_unknownNodeType() {
        // Given
        String type = "UNKNOWN_TYPE";
        String configJson = "{}";

        // When / Then
        assertThatThrownBy(() -> parser.parse(type, configJson))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未知节点类型");
    }

    @Test
    void should_throwBizException_when_jsonInvalid() {
        // Given
        String type = "LLM";
        String configJson = "{invalid json";

        // When / Then
        assertThatThrownBy(() -> parser.parse(type, configJson))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("节点配置解析失败");
    }
}
