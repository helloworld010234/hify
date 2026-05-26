# Hify 集成测试全量补齐设计文档

## 日期

2026-05-26

## 背景

Hify 项目当前有 22 个集成测试，覆盖 agent(4)、chat(3)、provider(10)。但 **workflow、knowledge、mcp 三个模块 0 个集成测试**。根据 `docs/testing.md` 中的核心链路（5 条）和风险地图（5 个区域），需要补齐 14 条集成测试，覆盖从 Controller 到数据库的完整链路，mock 掉 LLM API 和 MCP Server。

## 目标

- 补齐 P0（5 条）+ P1（5 条）+ P2（4 条）共 14 条集成测试
- 使用 Spring Boot Test + MockMvc + H2
- 所有外部 HTTP 调用通过 `@MockBean` 拦截
- 不破坏现有 22 个集成测试

## 整体架构

### Agent 分组（4 个并行）

| Agent | 模块 | 测试类 | 覆盖清单 |
|-------|------|--------|---------|
| **Agent-Workflow** | hify-workflow | `WorkflowControllerIT` | P0#3, P1#9, P2#11 |
| **Agent-Knowledge** | hify-knowledge | `KnowledgeBaseControllerIT` + `DocumentProcessIT` | P0#5, P1#10 |
| **Agent-MCP** | hify-mcp | `McpServerControllerIT` | P0#4, P1#6(MCP部分), P2#12 |
| **Agent-ChatProvider** | hify-chat + hify-provider | `ChatControllerEnhancedIT` + `ProviderControllerEnhancedIT` + `AgentCrossModuleIT` | P0#1, P0#2, P1#7, P1#8, P2#13, P2#14 |

### 基础设施复用

基于现有 `AbstractIntegrationTest` 基类（`@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional` + H2），每个模块补充 schema.sql 和 seed data SQL。

### 文件隔离原则

每个 Agent 只修改本模块目录下的文件（test class + SQL），天然零冲突。

---

## MockBean 策略

```java
@MockBean private LlmService llmService;
@MockBean private McpClientService mcpClientService;      // Agent-MCP, Agent-Chat
@MockBean private EmbeddingClient embeddingClient;         // Agent-Knowledge
@MockBean private ProviderAdapter providerAdapter;         // Agent-ChatProvider
@MockBean private ProviderHealthCheckTask healthCheckTask; // Agent-ChatProvider (P2#14)
```

### Mock 返回值规范

- `LlmService.streamChat()` → 返回固定 `SseEmitter` 内容（验证 Controller 正确建立连接 + 数据落库即可）
- `EmbeddingClient.embedBatch()` → 返回固定维度向量（如 `[0.1, 0.2, ...]` 1536 维）
- `McpClientService.callTool()` → 正常返回工具结果；异常场景抛出 `McpToolException`

---

## SQL Seed Data

| Agent | SQL 文件 | 内容 |
|-------|---------|------|
| Agent-Workflow | `db/workflow-test-data.sql` | 2 个工作流定义（线性 workflow_1、带条件分支 workflow_2）、各 3-4 个节点和边 |
| Agent-Knowledge | `db/knowledge-test-data.sql` | 1 个知识库、2 个文档（1 个 PENDING、1 个 DONE）、若干 chunk 数据 |
| Agent-MCP | `db/mcp-test-data.sql` | 2 个 MCP Server（1 启用、1 禁用）、各 2 个工具定义 |
| Agent-ChatProvider | `db/chat-enhanced-test-data.sql` | Agent 绑定 knowledge/workflow/mcp、Provider 含加密 api_key、多页 message |

### 已有测试数据复用

- Provider CRUD/连接测试的 7 个 SQL 文件 → Agent-ChatProvider 复用扩展
- Chat 的 3 个 SQL 文件 → Agent-ChatProvider 复用扩展
- Agent 的 3 个 SQL 文件 → Agent-ChatProvider 复用扩展

---

## 各 Agent 详细测试用例

### Agent-Workflow（`WorkflowControllerIT`）

| # | 来源 | 测试方法名 | Given-When-Then 概要 |
|---|------|-----------|---------------------|
| 3.1 | P0#3 | `should_runLinearWorkflow_andReturnOutput_when_validWorkflowId` | Given: 线性工作流（START→LLM→END）；When: `POST /api/v1/workflows/{id}/run`；Then: 返回 200 + 输出内容，`t_workflow_run` 状态 `SUCCESS` |
| 3.2 | P0#3 | `should_throwBizException_when_exceedsMaxSteps` | Given: 循环图（START→A→B→A）；When: run；Then: 抛出异常含 `EXCEED_MAX_STEPS`，状态 `FAILED` |
| 3.3 | P0#3 | `should_insertNodeRunRecords_when_nodeExecutionCompletes` | Given: 3 节点线性工作流；When: run；Then: `t_workflow_node_run` 有 3 条记录，各节点状态正确 |
| 3.4 | P1#9 | `should_parseNodeConfigCorrectly_when_saveWorkflowWithNodes` | Given: 各类型节点 JSON 配置；When: `POST /api/v1/workflows` 保存；Then: 落库后解析成功，返回 200 |
| 3.5 | P1#9 | `should_returnParamError_when_unknownNodeTypeInConfig` | Given: 含未知类型节点；When: save；Then: 返回 400 + `PARAM_ERROR` |
| 3.6 | P2#11 | `should_returnModelNotFound_when_runWithDeletedModelBinding` | Given: Agent 绑定已删除模型 + workflow；When: 对话触发 workflow；Then: `BizException` `MODEL_NOT_FOUND` |

### Agent-Knowledge（`KnowledgeBaseControllerIT` + `DocumentProcessIT`）

| # | 来源 | 测试方法名 | Given-When-Then 概要 |
|---|------|-----------|---------------------|
| 5.1 | P0#5 | `should_createDocumentRecordWithPendingStatus_when_upload` | Given: 知识库已创建；When: `POST /api/v1/knowledge-bases/{id}/documents` 上传文件；Then: 返回 200，`t_document` 状态 `PENDING`，元数据正确 |
| 5.2 | P0#5 | `should_transitionToDone_when_processDocumentCompletes` | Given: PENDING 文档 + Mock EmbeddingClient；When: 异步处理完成；Then: 状态变为 `DONE` |
| 5.3 | P0#5 | `should_transitionToFailed_when_embedChunksThrows` | Given: PENDING 文档 + Mock EmbeddingClient 抛异常；When: 异步处理；Then: 状态变为 `FAILED` |
| 10.1 | P1#10 | `should_returnDocumentsWithStatus_when_listDocuments` | Given: 多状态文档；When: `GET /api/v1/knowledge-bases/{id}/documents`；Then: 分页返回含状态、进度 |
| 10.2 | P1#10 | `should_assembleRagConditionCorrectly_when_chatWithKbBinding` | Given: Agent 绑定知识库；When: 对话请求；Then: RAG 检索条件含 `knowledgeBaseId` + `deleted=0` |

### Agent-MCP（`McpServerControllerIT`）

| # | 来源 | 测试方法名 | Given-When-Then 概要 |
|---|------|-----------|---------------------|
| 4.1 | P0#4 | `should_deleteOldToolsAndInsertNew_when_syncTools` | Given: 已有旧工具；When: 连通性测试触发 sync；Then: 旧工具 deleted=1、新工具存在 |
| 4.2 | P0#4 | `should_rollbackToolState_when_syncFails` | Given: 已有旧工具；When: sync 中途抛异常；Then: 工具表不被清空（事务回滚） |
| 4.3 | P0#4 | `should_handleInvalidSchemaGracefully_when_syncTools` | Given: 含非法 JSON schema 的工具；When: sync；Then: 该工具 setNull 跳过，其他正常插入 |
| 6.1 | P1#6 | `should_returnParamError_when_agentBindDisabledMcpTool` | Given: Agent 绑定已禁用 Server 的工具；When: `POST /api/v1/agents`；Then: 返回 400 |
| 12.1 | P2#12 | `should_returnErrorInContext_when_toolCallThrows` | Given: 对话触发工具调用 + Mock McpClientService 抛异常；When: 执行 tool calls；Then: 错误信息进入 LLM 上下文，对话不被中断 |

### Agent-ChatProvider（`ChatControllerEnhancedIT` + `ProviderControllerEnhancedIT` + `AgentCrossModuleIT`）

| # | 来源 | 测试方法名 | Given-When-Then 概要 |
|---|------|-----------|---------------------|
| 1.1 | P0#1 | `should_streamAssistantMessage_andPersist_when_noToolBound` | Given: Agent 无工具；When: SSE 对话；Then: emitter 200 + 用户/助手消息落库 |
| 1.2 | P0#1 | `should_triggerRag_when_kbBound` | Given: Agent 绑定知识库；When: 对话；Then: `KnowledgeRetrievalService.retrieve()` 被调用 |
| 1.3 | P0#1 | `should_triggerWorkflow_when_workflowBound` | Given: Agent 绑定工作流；When: 对话；Then: `WorkflowRunService.run()` 被调用 |
| 2.1 | P0#2 | `should_deleteOldModelsAndInsertNewAtomically_when_testConnection` | Given: Provider 含旧模型；When: `POST /api/v1/providers/{id}/test-connection`；Then: 旧模型 deleted=1、新模型存在 |
| 2.2 | P0#2 | `should_rollbackAndKeepConsistent_when_modelInsertFails` | Given: Provider；When: sync 时唯一索引冲突；Then: 事务回滚，Provider 状态与实际一致 |
| 7.1 | P1#7 | `should_storeEncryptedApiKey_when_createProvider` | Given: Provider 请求含 apiKey；When: `POST /api/v1/providers`；Then: DB 中 api_key 为密文 |
| 7.2 | P1#7 | `should_decryptApiKeyCorrectly_when_queryProvider` | Given: 含加密 apiKey 的 Provider；When: `GET /api/v1/providers/{id}`；Then: 返回解密后的明文（或掩码） |
| 8.1 | P1#8 | `should_returnMessagesByCursorPagination_when_queryHistory` | Given: 50 条消息；When: 游标分页查询；Then: 按 `(created_at, id)` 正确分页 |
| 13.1 | P2#13 | `should_useFallbackProvider_when_primaryReturns503` | Given: 主 Provider Mock 返回 503；When: LLM 调用；Then: fallback Provider 被调用 |
| 14.1 | P2#14 | `should_updateHealthStatus_when_scheduledCheckRuns` | Given: Provider 健康任务；When: 定时触发；Then: `t_provider_health` 状态更新 |

---

## 错误处理与断言规范

### 异常断言（所有 Agent 遵循）

```java
assertThatThrownBy(() -> workflowEngine.run(1L, "input"))
    .isInstanceOf(BizException.class)
    .hasMessageContaining("缺少 START 节点")
    .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
        .isEqualTo(ErrorCode.PARAM_ERROR));
```

### 数据库状态验证（JdbcTemplate）

```java
// 验证单条记录
Map<String, Object> record = jdbcTemplate.queryForMap(
    "SELECT status, deleted FROM t_workflow_run WHERE id = ?", runId);
assertThat(record.get("status")).isEqualTo("SUCCESS");
assertThat(record.get("deleted")).isEqualTo(0);

// 验证记录数
Integer count = jdbcTemplate.queryForObject(
    "SELECT COUNT(*) FROM t_workflow_node_run WHERE run_id = ?", Integer.class, runId);
assertThat(count).isEqualTo(3);

// 验证不存在（事务回滚验证）
assertThatThrownBy(() -> jdbcTemplate.queryForMap(
    "SELECT * FROM t_mcp_tool WHERE server_id = ? AND deleted = 0", serverId))
    .isInstanceOf(EmptyResultDataAccessException.class);
```

### SSE 端点断言（Agent-Chat 专用）

```java
mockMvc.perform(post("/api/v1/chat/sessions/{id}/messages", sessionId)
        .contentType(APPLICATION_JSON)
        .content(jsonBody))
    .andExpect(status().isOk())
    .andExpect(header().string("Content-Type", containsString("text/event-stream")));

// 消息落库验证
Map<String, Object> msg = jdbcTemplate.queryForMap(
    "SELECT role, content FROM t_message WHERE session_id = ? ORDER BY id DESC LIMIT 1", sessionId);
assertThat(msg.get("role")).isEqualTo("assistant");
```

### 异步操作验证（Agent-Knowledge 专用）

```java
Awaitility.await()
    .atMost(Duration.ofSeconds(5))
    .pollInterval(Duration.ofMillis(200))
    .untilAsserted(() -> {
        String status = jdbcTemplate.queryForObject(
            "SELECT status FROM t_document WHERE id = ?", String.class, docId);
        assertThat(status).isIn("DONE", "FAILED");
    });
```

### MockBean 交互验证

```java
verify(knowledgeRetrievalService, times(1))
    .retrieve(eq(kbId), anyString(), eq(5));

verify(llmService, times(1)).streamChat(eq("ollama"), any(), any());
```

---

## 验收标准

1. **所有测试通过**：`mvn test -pl hify-app` 本模块新增测试 100% 通过
2. **不破坏现有测试**：原有 22 个集成测试全部保持通过
3. **覆盖率目标**：新增测试的 Service 层分支覆盖 >= 60%（Jacoco 报告）
4. **代码规范**：命名 `should_[期望]_when_[条件]`，Given-When-Then 三段式，AssertJ 断言
5. **无外部依赖**：所有 HTTP 调用通过 `@MockBean` 拦截，无真实 LLM/MCP 调用

---

## 回退策略

- 若某 Agent 遇到阻塞（如 schema.sql 需要新增表字段），先在本 Agent 内做最小化 DDL 修改，标记 `/* Added by Agent-X */`
- 若需要修改共享文件（如 `AbstractIntegrationTest`），由主 Agent（当前 session）统一处理，子 Agent 只读不修改

---

## 文件清单（新增）

| 文件 | 说明 |
|------|------|
| `hify-app/src/test/java/com/hify/modules/workflow/WorkflowControllerIT.java` | Agent-Workflow |
| `hify-app/src/test/java/com/hify/modules/knowledge/KnowledgeBaseControllerIT.java` | Agent-Knowledge |
| `hify-app/src/test/java/com/hify/modules/knowledge/DocumentProcessIT.java` | Agent-Knowledge |
| `hify-app/src/test/java/com/hify/modules/mcp/McpServerControllerIT.java` | Agent-MCP |
| `hify-app/src/test/java/com/hify/modules/chat/ChatControllerEnhancedIT.java` | Agent-ChatProvider |
| `hify-app/src/test/java/com/hify/modules/provider/ProviderControllerEnhancedIT.java` | Agent-ChatProvider |
| `hify-app/src/test/java/com/hify/modules/agent/AgentCrossModuleIT.java` | Agent-ChatProvider |
| `hify-app/src/test/resources/db/workflow-test-data.sql` | Agent-Workflow seed data |
| `hify-app/src/test/resources/db/knowledge-test-data.sql` | Agent-Knowledge seed data |
| `hify-app/src/test/resources/db/mcp-test-data.sql` | Agent-MCP seed data |
| `hify-app/src/test/resources/db/chat-enhanced-test-data.sql` | Agent-ChatProvider seed data |
