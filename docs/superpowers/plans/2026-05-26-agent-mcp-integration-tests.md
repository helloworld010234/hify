# Agent-MCP Integration Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement integration tests for hify-mcp module covering tool sync transaction, disabled tool binding validation, and tool call error fallback.

**Architecture:** Spring Boot Test + MockMvc + H2. Mock McpClientService for tool execution failure scenario.

**Tech Stack:** JUnit 5, AssertJ, Mockito, MockMvc, H2

---

## File Structure

| File | Action | Purpose |
|------|--------|---------|
| `hify-app/src/test/resources/db/mcp-test-data.sql` | Create | Seed data: 2 MCP servers with tools |
| `hify-app/src/test/java/com/hify/modules/mcp/McpServerControllerIT.java` | Create | MCP tool sync + binding validation tests (5 test methods) |

---

### Task 1: Create MCP test seed data SQL

**Files:**
- Create: `hify-app/src/test/resources/db/mcp-test-data.sql`

- [ ] **Step 1: Write seed data SQL**

```sql
-- ============================================
-- MCP 模块集成测试预置数据
-- ============================================

-- MCP Server 1: 启用状态
INSERT INTO t_mcp_server (id, name, endpoint, enabled, status, tool_count, deleted, created_at, updated_at)
VALUES (200, 'Active Server', 'http://localhost:2000/sse', 1, 'active', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- MCP Server 1 的工具
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted, created_at, updated_at)
VALUES
(201, 200, 'tool_a', 'Tool A description', '{"type":"object","properties":{"arg1":{"type":"string"}}}', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(202, 200, 'tool_b', 'Tool B description', '{"type":"object","properties":{"arg2":{"type":"number"}}}', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- MCP Server 2: 禁用状态
INSERT INTO t_mcp_server (id, name, endpoint, enabled, status, tool_count, deleted, created_at, updated_at)
VALUES (210, 'Disabled Server', 'http://localhost:2100/sse', 0, 'inactive', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- MCP Server 2 的工具（Server 禁用但工具存在）
INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted, created_at, updated_at)
VALUES (211, 210, 'disabled_tool', 'Disabled tool', '{"type":"object"}', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Provider + Model + Agent（用于绑定测试）
INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, timeout_ms, max_retries, sort_order, deleted)
VALUES (200, 'mcp-provider', 'MCP Provider', 'openai_compatible', 'https://api.mcp.com', 'bearer', 'active', 90000, 3, 0, 0);

INSERT INTO t_model (id, provider_id, model_code, model_name, model_type, status, sort_order, deleted)
VALUES (200, 200, 'gpt-4', 'GPT-4', 'chat', 'active', 0, 0);

INSERT INTO t_agent (id, name, model_config_id, temperature, max_tokens, max_context_turns, enabled, deleted)
VALUES (200, 'MCP Agent', 200, 0.70, 2048, 10, 1, 0);

-- Chat session for tool call test
INSERT INTO t_chat_session (id, agent_id, title, deleted)
VALUES (200, 200, 'MCP Test Session', 0);
```

- [ ] **Step 2: Verify SQL**

Run: `mvn test -pl hify-app -Dtest=DoesNotExist`
Expected: Build succeeds.

---

### Task 2: Create McpServerControllerIT

**Files:**
- Create: `hify-app/src/test/java/com/hify/modules/mcp/McpServerControllerIT.java`

- [ ] **Step 1: Write class skeleton**

```java
package com.hify.modules.mcp;

import com.hify.AbstractIntegrationTest;
import com.hify.common.service.mcp.McpToolDefinition;
import com.hify.common.service.mcp.McpToolService;
import com.hify.modules.mcp.api.McpClientService;
import com.hify.modules.provider.api.LlmService;
import com.hify.modules.provider.dto.chat.ChatRequest;
import com.hify.modules.provider.dto.chat.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class McpServerControllerIT extends AbstractIntegrationTest {

    @MockBean
    private McpClientService mcpClientService;

    @MockBean
    private McpToolService mcpToolService;

    @MockBean
    private LlmService llmService;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @AfterEach
    void cleanupMcpTestData() {
        jdbcTemplate.update("DELETE FROM t_chat_message WHERE session_id = 200");
        jdbcTemplate.update("DELETE FROM t_chat_session WHERE id = 200");
        jdbcTemplate.update("DELETE FROM t_agent WHERE id = 200");
        jdbcTemplate.update("DELETE FROM t_model WHERE id = 200");
        jdbcTemplate.update("DELETE FROM t_provider WHERE id = 200");
        jdbcTemplate.update("DELETE FROM t_mcp_tool WHERE id IN (201,202,211)");
        jdbcTemplate.update("DELETE FROM t_mcp_server WHERE id IN (200,210)");
    }
}
```

- [ ] **Step 2: Add test 4.1 - sync tools deletes old and inserts new**

```java
    @Test
    @DisplayName("P0: 工具同步 — 旧工具软删除 + 新工具插入")
    @Sql(scripts = "classpath:db/mcp-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_deleteOldToolsAndInsertNew_when_syncTools() throws Exception {
        // Given: Mock McpClientService 返回新工具列表
        McpToolDefinition newTool = new McpToolDefinition();
        newTool.setName("new_tool");
        newTool.setDescription("New tool desc");
        newTool.setInputSchema(objectMapper.readTree("{\"type\":\"object\"}"));
        when(mcpClientService.discoverTools(anyLong())).thenReturn(List.of(newTool));

        // When: 触发连通性测试（会同步工具）
        mockMvc.perform(post("/api/v1/mcp-servers/{id}/test", 200)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Then: 旧工具被软删除
        Integer oldDeleted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_mcp_tool WHERE server_id = 200 AND deleted = 1", Integer.class);
        assertThat(oldDeleted).isEqualTo(2);

        // Then: 新工具存在
        Integer newCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_mcp_tool WHERE server_id = 200 AND name = 'new_tool' AND deleted = 0",
                Integer.class);
        assertThat(newCount).isEqualTo(1);
    }
```

- [ ] **Step 3: Add test 4.2 - sync rollback on failure**

```java
    @Test
    @DisplayName("P0: 工具同步失败 — 事务回滚，工具表不处于半清空状态")
    @Sql(scripts = "classpath:db/mcp-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_rollbackToolState_when_syncFails() throws Exception {
        // Given: Mock  discoverTools 抛异常
        when(mcpClientService.discoverTools(anyLong()))
                .thenThrow(new RuntimeException("MCP server unreachable"));

        // When: 触发连通性测试
        mockMvc.perform(post("/api/v1/mcp-servers/{id}/test", 200)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));

        // Then: 旧工具仍然存在（未被删除）
        Integer oldCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_mcp_tool WHERE server_id = 200 AND deleted = 0", Integer.class);
        assertThat(oldCount).isEqualTo(2);
    }
```

- [ ] **Step 4: Add test 4.3 - invalid schema handled gracefully**

```java
    @Test
    @DisplayName("P0: 非法 JSON schema 工具 — 单条容错，其他正常插入")
    @Sql(scripts = "classpath:db/mcp-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_handleInvalidSchemaGracefully_when_syncTools() throws Exception {
        // Given: Mock 返回含非法 schema 的工具
        McpToolDefinition validTool = new McpToolDefinition();
        validTool.setName("valid_tool");
        validTool.setDescription("Valid");
        validTool.setInputSchema(objectMapper.readTree("{\"type\":\"object\"}"));

        McpToolDefinition invalidTool = new McpToolDefinition();
        invalidTool.setName("invalid_tool");
        invalidTool.setDescription("Invalid");
        invalidTool.setInputSchema(null); // 模拟解析失败

        when(mcpClientService.discoverTools(anyLong())).thenReturn(List.of(validTool, invalidTool));

        // When: 触发同步
        mockMvc.perform(post("/api/v1/mcp-servers/{id}/test", 200)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Then: valid_tool 存在
        Integer validCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_mcp_tool WHERE server_id = 200 AND name = 'valid_tool' AND deleted = 0",
                Integer.class);
        assertThat(validCount).isEqualTo(1);
    }
```

- [ ] **Step 5: Add test 6.1 - bind disabled MCP tool returns error**

```java
    @Test
    @DisplayName("P1: Agent 绑定禁用 Server 的工具时返回参数错误")
    @Sql(scripts = "classpath:db/mcp-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_returnParamError_when_agentBindDisabledMcpTool() throws Exception {
        // When: 创建 Agent 并绑定禁用 Server 的工具
        String requestJson = """
                {
                    "name": "Bad Agent",
                    "modelConfigId": 200,
                    "toolIds": [211],
                    "temperature": 0.7,
                    "maxTokens": 2048,
                    "maxContextTurns": 10
                }
                """;

        mockMvc.perform(post("/api/v1/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
```

- [ ] **Step 6: Add test 12.1 - tool call error in context**

```java
    @Test
    @DisplayName("P2: MCP 工具调用异常 — 错误信息进入上下文，对话不被中断")
    @Sql(scripts = "classpath:db/mcp-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_returnErrorInContext_when_toolCallThrows() throws Exception {
        // Given: Mock 工具 schema
        McpToolDefinition toolDef = new McpToolDefinition();
        toolDef.setId(201L);
        toolDef.setServerId(200L);
        toolDef.setName("tool_a");
        toolDef.setDescription("Tool A");
        toolDef.setInputSchema(objectMapper.readTree(
                "{\"type\":\"object\",\"properties\":{\"arg1\":{\"type\":\"string\"}}}"));
        when(mcpToolService.getToolDefinitions(List.of(201L))).thenReturn(List.of(toolDef));

        // Given: Mock 工具调用抛异常
        doThrow(new RuntimeException("Tool execution failed"))
                .when(mcpClientService).callTool(eq(200L), eq("tool_a"), any());

        // Given: Mock LLM 返回 tool_calls
        ChatResponse firstResponse = new ChatResponse();
        firstResponse.setFinishReason("tool_calls");
        firstResponse.setToolCalls(List.of(
                Map.of("id", "call_1",
                        "function", Map.of("name", "tool_a", "arguments", "{\"arg1\":\"test\"}"))
        ));
        when(llmService.chat(anyLong(), any(ChatRequest.class))).thenReturn(firstResponse);

        // Given: Mock 第二轮流式返回
        doAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(2);
            Consumer<String> onFinish = invocation.getArgument(3);
            onDelta.accept("Error handled");
            onFinish.accept("stop");
            return null;
        }).when(llmService).streamChat(anyLong(), any(ChatRequest.class), any(), any());

        // When: 发起 SSE 请求
        var asyncResult = mockMvc.perform(post("/api/v1/chat/sessions/200/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Use tool\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk());

        // Then: 数据库有 assistant 消息（对话未被中断）
        List<Map<String, Object>> messages = jdbcTemplate.queryForList(
                "SELECT * FROM t_chat_message WHERE session_id = 200 AND deleted = 0 ORDER BY id");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).get("role")).isEqualTo("assistant");

        // Then: 工具确实被调用了（即使失败）
        verify(mcpClientService).callTool(any(), eq("tool_a"), any());
    }
```

- [ ] **Step 7: Verify all tests**

Run: `mvn test -pl hify-app -Dtest=McpServerControllerIT`

Expected: All 5 tests PASS.

- [ ] **Step 8: Commit**

```bash
git add hify-app/src/test/java/com/hify/modules/mcp/McpServerControllerIT.java
git add hify-app/src/test/resources/db/mcp-test-data.sql
git commit -m "test: mcp 模块集成测试补齐（5个测试方法）"
```

---

## Self-Review Checklist

- [ ] Spec coverage: All 5 tests from design doc (4.1-4.3, 6.1, 12.1) implemented
- [ ] No placeholders: All code complete
- [ ] Mock consistency: McpClientService.discoverTools mock matches actual service signature
- [ ] Transactional: @Transactional(NOT_SUPPORTED) because sync tools runs in its own tx
