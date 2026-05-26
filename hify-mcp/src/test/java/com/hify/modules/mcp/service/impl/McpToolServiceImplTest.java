package com.hify.modules.mcp.service.impl;


import com.hify.modules.mcp.entity.McpServer;
import com.hify.modules.mcp.entity.McpTool;
import com.hify.modules.mcp.mapper.McpServerMapper;
import com.hify.modules.mcp.mapper.McpToolMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * McpToolServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class McpToolServiceImplTest {

    @Mock
    private McpToolMapper mcpToolMapper;

    @Mock
    private McpServerMapper mcpServerMapper;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private McpToolServiceImpl mcpToolService;

    // ==================== findInvalidToolIds ====================

    @Test
    void should_returnEmptyList_when_toolIdsEmpty() {
        // Given
        List<Long> toolIds = Collections.emptyList();

        // When
        List<Long> invalidIds = mcpToolService.findInvalidToolIds(toolIds);

        // Then
        assertThat(invalidIds).isEmpty();
    }

    @Test
    void should_returnMissingIds_when_someToolsNotExist() {
        // Given
        List<Long> toolIds = List.of(1L, 2L, 3L);
        McpTool tool1 = new McpTool();
        tool1.setId(1L);
        tool1.setServerId(10L);

        when(mcpToolMapper.selectList(any())).thenReturn(List.of(tool1));

        McpServer server = new McpServer();
        server.setId(10L);
        server.setEnabled(1);
        when(mcpServerMapper.selectList(any())).thenReturn(List.of(server));

        // When
        List<Long> invalidIds = mcpToolService.findInvalidToolIds(toolIds);

        // Then
        assertThat(invalidIds).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void should_returnToolIds_when_serverDisabled() {
        // Given
        List<Long> toolIds = List.of(1L, 2L);

        McpTool tool1 = new McpTool();
        tool1.setId(1L);
        tool1.setServerId(10L);

        McpTool tool2 = new McpTool();
        tool2.setId(2L);
        tool2.setServerId(11L);

        when(mcpToolMapper.selectList(any())).thenReturn(List.of(tool1, tool2));

        // Server 10 enabled, Server 11 disabled
        McpServer server10 = new McpServer();
        server10.setId(10L);
        server10.setEnabled(1);

        McpServer server11 = new McpServer();
        server11.setId(11L);
        server11.setEnabled(0);

        when(mcpServerMapper.selectList(any())).thenReturn(List.of(server10, server11));

        // When
        List<Long> invalidIds = mcpToolService.findInvalidToolIds(toolIds);

        // Then: tool2's server is disabled, so 2L is invalid
        assertThat(invalidIds).contains(2L);
        assertThat(invalidIds).doesNotContain(1L);
    }
}
