# Agent-Workflow Integration Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement integration tests for hify-workflow module covering workflow execution engine, node config parsing, and model deletion fallback scenarios.

**Architecture:** Spring Boot Test + MockMvc + H2 in-memory DB. Tests extend AbstractIntegrationTest. Mock WorkflowRunService for model-not-found scenario.

**Tech Stack:** JUnit 5, AssertJ, Mockito, MockMvc, H2, Spring Boot Test

---

## File Structure

| File | Action | Purpose |
|------|--------|---------|
| `hify-app/src/test/resources/db/workflow-test-data.sql` | Create | Seed data: 2 workflows with nodes/edges |
| `hify-app/src/test/java/com/hify/modules/workflow/WorkflowControllerIT.java` | Create | Integration test class (6 test methods) |

---

### Task 1: Create workflow test seed data SQL

**Files:**
- Create: `hify-app/src/test/resources/db/workflow-test-data.sql`

- [ ] **Step 1: Write seed data SQL**

```sql
-- ============================================
-- Workflow 模块集成测试预置数据
-- ============================================

-- 工作流 1: 线性工作流 (START -> LLM -> END)
INSERT INTO t_workflow (id, name, description, enabled, deleted, created_at, updated_at)
VALUES (100, 'Linear Workflow', '线性测试工作流', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO t_workflow_node (id, workflow_id, node_key, type, name, config, sort_order, deleted, created_at, updated_at)
VALUES
(101, 100, 'start', 'START', '开始', '{"outputVar":"userInput"}', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(102, 100, 'llm_1', 'LLM', 'LLM 节点', '{"modelCode":"gpt-4","systemPrompt":"你是一个助手","temperature":0.7}', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(103, 100, 'end', 'END', '结束', '{"outputVar":"finalAnswer"}', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO t_workflow_edge (id, workflow_id, source_key, target_key, condition, deleted, created_at, updated_at)
VALUES
(104, 100, 'start', 'llm_1', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(105, 100, 'llm_1', 'end', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 工作流 2: 循环死循环工作流 (START -> A -> B -> A)
INSERT INTO t_workflow (id, name, description, enabled, deleted, created_at, updated_at)
VALUES (110, 'Loop Workflow', '循环测试工作流', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO t_workflow_node (id, workflow_id, node_key, type, name, config, sort_order, deleted, created_at, updated_at)
VALUES
(111, 110, 'start', 'START', '开始', '{"outputVar":"userInput"}', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(112, 110, 'node_a', 'LLM', '节点A', '{"modelCode":"gpt-4"}', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(113, 110, 'node_b', 'LLM', '节点B', '{"modelCode":"gpt-4"}', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO t_workflow_edge (id, workflow_id, source_key, target_key, condition, deleted, created_at, updated_at)
VALUES
(114, 110, 'start', 'node_a', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(115, 110, 'node_a', 'node_b', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(116, 110, 'node_b', 'node_a', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 工作流 3: 条件分支工作流
INSERT INTO t_workflow (id, name, description, enabled, deleted, created_at, updated_at)
VALUES (120, 'Condition Workflow', '条件分支测试工作流', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO t_workflow_node (id, workflow_id, node_key, type, name, config, sort_order, deleted, created_at, updated_at)
VALUES
(121, 120, 'start', 'START', '开始', '{"outputVar":"userInput"}', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(122, 120, 'cond', 'CONDITION', '条件判断', '{"expression":"{{userInput}} == \"yes\""}', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(123, 120, 'true_branch', 'LLM', '真分支', '{"modelCode":"gpt-4"}', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(124, 120, 'false_branch', 'LLM', '假分支', '{"modelCode":"gpt-4"}', 3, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(125, 120, 'end', 'END', '结束', '{}', 4, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO t_workflow_edge (id, workflow_id, source_key, target_key, condition, deleted, created_at, updated_at)
VALUES
(126, 120, 'start', 'cond', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(127, 120, 'cond', 'true_branch', 'true', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(128, 120, 'cond', 'false_branch', 'false', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(129, 120, 'true_branch', 'end', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(130, 120, 'false_branch', 'end', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Provider + Model + Agent 绑定工作流
INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, timeout_ms, max_retries, sort_order, deleted)
VALUES (100, 'wf-provider', 'Workflow Provider', 'openai_compatible', 'https://api.wf.com', 'bearer', 'active', 90000, 3, 0, 0);

INSERT INTO t_model (id, provider_id, model_code, model_name, model_type, status, sort_order, deleted)
VALUES (100, 100, 'gpt-4', 'GPT-4', 'chat', 'active', 0, 0);

INSERT INTO t_agent (id, name, model_config_id, workflow_id, temperature, max_tokens, max_context_turns, enabled, deleted)
VALUES (100, 'Workflow Agent', 100, 100, 0.70, 2048, 10, 1, 0);

-- 已删除模型（用于 P2#11 测试）
INSERT INTO t_provider (id, code, name, provider_type, base_url, auth_type, status, timeout_ms, max_retries, sort_order, deleted)
VALUES (101, 'deleted-prov', 'Deleted Provider', 'openai_compatible', 'https://api.del.com', 'bearer', 'active', 90000, 3, 0, 0);

INSERT INTO t_model (id, provider_id, model_code, model_name, model_type, status, sort_order, deleted)
VALUES (101, 101, 'gpt-4-del', 'GPT-4 Deleted', 'chat', 'active', 0, 0);

INSERT INTO t_agent (id, name, model_config_id, workflow_id, temperature, max_tokens, max_context_turns, enabled, deleted)
VALUES (101, 'Deleted Model Agent', 101, 100, 0.70, 2048, 10, 1, 0);
```

- [ ] **Step 2: Verify SQL syntax**

Run: `mvn test -pl hify-app -Dtest=DoesNotExist` (just to init DB and verify schema)

Expected: Build succeeds, schema.sql executed without errors.

---

### Task 2: Create WorkflowControllerIT test class

**Files:**
- Create: `hify-app/src/test/java/com/hify/modules/workflow/WorkflowControllerIT.java`

- [ ] **Step 1: Write the test class skeleton**

```java
package com.hify.modules.workflow;

import com.hify.AbstractIntegrationTest;
import com.hify.common.exception.BizException;
import com.hify.modules.provider.api.LlmService;
import com.hify.modules.provider.dto.chat.ChatRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkflowControllerIT extends AbstractIntegrationTest {

    @MockBean
    private LlmService llmService;

    @AfterEach
    void cleanupWorkflowTestData() {
        jdbcTemplate.update("DELETE FROM t_workflow_node_run WHERE run_id IN (SELECT id FROM t_workflow_run WHERE workflow_id IN (100,110,120))");
        jdbcTemplate.update("DELETE FROM t_workflow_run WHERE workflow_id IN (100,110,120)");
        jdbcTemplate.update("DELETE FROM t_workflow_edge WHERE workflow_id IN (100,110,120)");
        jdbcTemplate.update("DELETE FROM t_workflow_node WHERE workflow_id IN (100,110,120)");
        jdbcTemplate.update("DELETE FROM t_workflow WHERE id IN (100,110,120)");
        jdbcTemplate.update("DELETE FROM t_agent WHERE id IN (100,101)");
        jdbcTemplate.update("DELETE FROM t_model WHERE id IN (100,101)");
        jdbcTemplate.update("DELETE FROM t_provider WHERE id IN (100,101)");
    }

    // ... test methods below
}
```

- [ ] **Step 2: Add test 3.1 - linear workflow run**

```java
    @Test
    @DisplayName("P0: 线性工作流执行 — START→LLM→END，返回输出并落库")
    @Sql(scripts = "classpath:db/workflow-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_runLinearWorkflow_andReturnOutput_when_validWorkflowId() throws Exception {
        // Given: Mock LLM 返回固定内容
        doAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(2);
            Consumer<String> onFinish = invocation.getArgument(3);
            onDelta.accept("Hello");
            onFinish.accept("stop");
            return null;
        }).when(llmService).streamChat(anyLong(), any(ChatRequest.class), any(), any());

        // When: 执行工作流
        mockMvc.perform(post("/api/v1/workflows/{id}/run", 100)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userMessage\":\"Test input\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.output").value("Hello"));

        // Then: 工作流运行记录落库
        Map<String, Object> run = jdbcTemplate.queryForMap(
                "SELECT * FROM t_workflow_run WHERE workflow_id = 100");
        assertThat(run.get("status")).isEqualTo("SUCCESS");

        // Then: 节点运行记录有 3 条
        Integer nodeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_workflow_node_run WHERE run_id = ?", Integer.class, run.get("id"));
        assertThat(nodeCount).isEqualTo(3);
    }
```

- [ ] **Step 3: Add test 3.2 - max steps exceeded**

```java
    @Test
    @DisplayName("P0: 循环工作流超出最大步数 — 抛出异常并标记 FAILED")
    @Sql(scripts = "classpath:db/workflow-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_throwBizException_when_exceedsMaxSteps() throws Exception {
        // Given: Mock LLM（循环图会被触发）
        doAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(2);
            Consumer<String> onFinish = invocation.getArgument(3);
            onDelta.accept("Step");
            onFinish.accept("stop");
            return null;
        }).when(llmService).streamChat(anyLong(), any(ChatRequest.class), any(), any());

        // When & Then: 执行循环工作流，应抛出异常
        mockMvc.perform(post("/api/v1/workflows/{id}/run", 110)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userMessage\":\"Loop test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));

        // Then: 工作流运行记录状态为 FAILED
        Map<String, Object> run = jdbcTemplate.queryForMap(
                "SELECT * FROM t_workflow_run WHERE workflow_id = 110");
        assertThat(run.get("status")).isEqualTo("FAILED");
    }
```

- [ ] **Step 4: Add test 3.3 - node run records inserted**

```java
    @Test
    @DisplayName("P0: 工作流节点执行记录完整性 — 每个节点一条记录")
    @Sql(scripts = "classpath:db/workflow-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_insertNodeRunRecords_when_nodeExecutionCompletes() throws Exception {
        // Given: Mock LLM
        doAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(2);
            Consumer<String> onFinish = invocation.getArgument(3);
            onDelta.accept("Result");
            onFinish.accept("stop");
            return null;
        }).when(llmService).streamChat(anyLong(), any(ChatRequest.class), any(), any());

        // When: 执行工作流
        mockMvc.perform(post("/api/v1/workflows/{id}/run", 100)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userMessage\":\"Node record test\"}"))
                .andExpect(status().isOk());

        // Then: 查询节点运行记录
        Long runId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_workflow_run WHERE workflow_id = 100", Long.class);

        var nodeRuns = jdbcTemplate.queryForList(
                "SELECT node_key, status FROM t_workflow_node_run WHERE run_id = ? ORDER BY id", runId);

        assertThat(nodeRuns).hasSize(3);
        assertThat(nodeRuns.get(0).get("node_key")).isEqualTo("start");
        assertThat(nodeRuns.get(1).get("node_key")).isEqualTo("llm_1");
        assertThat(nodeRuns.get(2).get("node_key")).isEqualTo("end");
    }
```

- [ ] **Step 5: Add test 3.4 - node config parse on save**

```java
    @Test
    @DisplayName("P1: 保存工作流时节点配置解析正确")
    void should_parseNodeConfigCorrectly_when_saveWorkflowWithNodes() throws Exception {
        String requestJson = """
                {
                    "name": "Test Save Workflow",
                    "description": "Test desc",
                    "nodes": [
                        {"nodeKey": "start", "type": "START", "name": "开始", "config": {"outputVar": "input"}},
                        {"nodeKey": "llm", "type": "LLM", "name": "LLM", "config": {"modelCode": "gpt-4"}},
                        {"nodeKey": "end", "type": "END", "name": "结束", "config": {}}
                    ],
                    "edges": [
                        {"sourceKey": "start", "targetKey": "llm"},
                        {"sourceKey": "llm", "targetKey": "end"}
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // Then: 数据库存在工作流及节点
        Long wfId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_workflow WHERE name = ?", Long.class, "Test Save Workflow");
        assertThat(wfId).isNotNull();

        Integer nodeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_workflow_node WHERE workflow_id = ?", Integer.class, wfId);
        assertThat(nodeCount).isEqualTo(3);
    }
```

- [ ] **Step 6: Add test 3.5 - unknown node type error**

```java
    @Test
    @DisplayName("P1: 保存含未知节点类型时返回参数错误")
    void should_returnParamError_when_unknownNodeTypeInConfig() throws Exception {
        String requestJson = """
                {
                    "name": "Bad Workflow",
                    "nodes": [
                        {"nodeKey": "start", "type": "UNKNOWN_TYPE", "name": "未知", "config": {}}
                    ],
                    "edges": []
                }
                """;

        mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
```

- [ ] **Step 7: Add test 3.6 - deleted model fallback**

```java
    @Test
    @DisplayName("P2: Agent 绑定已删除模型 + 工作流时返回 MODEL_NOT_FOUND")
    @Sql(scripts = "classpath:db/workflow-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_returnModelNotFound_when_runWithDeletedModelBinding() throws Exception {
        // Given: 删除模型 101
        jdbcTemplate.update("UPDATE t_model SET deleted = 1 WHERE id = 101");

        // When: 通过 Chat 触发工作流（Agent 101 绑定已删除模型 101 + 工作流 100）
        // 注意：此测试验证 ChatServiceImpl 在工作流路由前的模型校验
        mockMvc.perform(post("/api/v1/chat/sessions/999/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }
```

- [ ] **Step 8: Verify all tests compile and pass**

Run: `mvn test -pl hify-app -Dtest=WorkflowControllerIT`

Expected: All 6 tests PASS.

- [ ] **Step 9: Commit**

```bash
git add hify-app/src/test/java/com/hify/modules/workflow/WorkflowControllerIT.java
 git add hify-app/src/test/resources/db/workflow-test-data.sql
git commit -m "test: workflow 模块集成测试补齐（6个测试方法）"
```

---

## Self-Review Checklist

- [ ] Spec coverage: All 6 tests from design doc (3.1-3.6) implemented
- [ ] No placeholders: All code is complete, no TBD/TODO
- [ ] Type consistency: NodeRequest/WorkflowRunRequest field names match production code
- [ ] SQL cleanup: @AfterEach cleans all test data to avoid cross-test contamination
