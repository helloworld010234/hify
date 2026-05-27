# Hify API 接口目录

> 生成日期：2026-05-27
> 版本：v1.0
> 总计：11 个 Controller，42 个接口

---

## 目录

- [1. Agent 管理](#1-agent-管理)
- [2. 对话引擎](#2-对话引擎)
- [3. 知识库 RAG](#3-知识库-rag)
- [4. MCP 工具](#4-mcp-工具)
- [5. 模型管理](#5-模型管理)
- [6. 简版工作流](#6-简版工作流)
- [7. 健康检查](#7-健康检查)
- [附录：全局响应格式](#附录全局响应格式)
- [附录：HTTP 状态码约定](#附录http-状态码约定)

---

## 1. Agent 管理

模块：`hify-agent` | 包路径：`com.hify.modules.agent.controller`

### 1.1 AgentController

路径前缀：`/api/v1/agents`

| 序号 | 方法 | 路径 | 方法名 | 参数 | 返回类型 | 说明 |
|------|------|------|--------|------|----------|------|
| 1 | POST | `/api/v1/agents` | create | `@Valid @RequestBody AgentCreateRequest` | `Result<AgentDetailResponse>` | 创建 Agent |
| 2 | GET | `/api/v1/agents` | list | `AgentListRequest`（Query） | `Result<PageResult<AgentListResponse>>` | 分页查询 Agent 列表 |
| 3 | GET | `/api/v1/agents/{id}` | getById | `@PathVariable Long id` | `Result<AgentDetailResponse>` | 获取 Agent 详情 |
| 4 | PUT | `/api/v1/agents/{id}` | update | `@PathVariable Long id`, `@Valid @RequestBody AgentUpdateRequest` | `Result<Void>` | 更新 Agent |
| 5 | DELETE | `/api/v1/agents/{id}` | delete | `@PathVariable Long id` | `Result<Void>` | 删除 Agent（逻辑删除） |
| 6 | POST | `/api/v1/agents/{id}/clone` | clone | `@PathVariable Long id` | `Result<Long>` | 克隆 Agent |
| 7 | PATCH | `/api/v1/agents/{id}/max-context-turns` | updateMaxContextTurns | `@PathVariable Long id`, `@Valid @RequestBody AgentMaxContextTurnsPatchRequest` | `Result<Void>` | 快捷修改上下文轮数 |
| 8 | PATCH | `/api/v1/agents/{id}/temperature` | updateTemperature | `@PathVariable Long id`, `@Valid @RequestBody AgentTemperaturePatchRequest` | `Result<Void>` | 快捷修改温度参数 |
| 9 | PUT | `/api/v1/agents/{id}/tools` | updateTools | `@PathVariable Long id`, `@Valid @RequestBody AgentToolsPatchRequest` | `Result<Void>` | 修改 Agent 工具绑定（全量替换） |

### 1.2 AgentMetaController

路径前缀：`/api/v1/agents`

| 序号 | 方法 | 路径 | 方法名 | 参数 | 返回类型 | 说明 |
|------|------|------|--------|------|----------|------|
| 10 | GET | `/api/v1/agents/models` | listModelGroups | 无 | `Result<List<ModelGroupResponse>>` | 获取所有可用模型（按供应商分组） |
| 11 | GET | `/api/v1/agents/tools` | listTools | 无 | `Result<List<ToolOptionResponse>>` | 获取所有可用工具 |

---

## 2. 对话引擎

模块：`hify-chat` | 包路径：`com.hify.modules.chat.controller`

### 2.1 ChatSessionController

路径前缀：`/api/v1/chat`

| 序号 | 方法 | 路径 | 方法名 | 参数 | 返回类型 | 说明 |
|------|------|------|--------|------|----------|------|
| 12 | POST | `/api/v1/chat/sessions` | create | `@Valid @RequestBody CreateSessionRequest` | `Result<ChatSessionResponse>` | 创建对话会话 |

### 2.2 ChatController

路径前缀：`/api/v1/chat/sessions`

| 序号 | 方法 | 路径 | 方法名 | 参数 | 返回类型 | 说明 |
|------|------|------|--------|------|----------|------|
| 13 | POST | `/api/v1/chat/sessions/{sessionId}/messages` | sendMessage | `@PathVariable Long sessionId`, `@Valid @RequestBody ChatStreamRequest` | `SseEmitter` | 发送消息（SSE 流式响应） |

> **注意**：`ChatController.sendMessage` 返回 `text/event-stream`，非 `Result<T>` 封装，前端需按 SSE 协议处理。

---

## 3. 知识库 RAG

模块：`hify-knowledge` | 包路径：`com.hify.modules.knowledge.controller`

### 3.1 KnowledgeBaseController

路径前缀：`/api/v1/knowledge-bases`

| 序号 | 方法 | 路径 | 方法名 | 参数 | 返回类型 | 说明 |
|------|------|------|--------|------|----------|------|
| 14 | POST | `/api/v1/knowledge-bases` | create | `@Valid @RequestBody KnowledgeBaseCreateRequest` | `Result<KnowledgeBaseResponse>` | 创建知识库 |
| 15 | GET | `/api/v1/knowledge-bases` | list | `KnowledgeBaseListRequest`（Query） | `Result<PageResult<KnowledgeBaseResponse>>` | 分页查询知识库列表 |
| 16 | GET | `/api/v1/knowledge-bases/{id}` | getById | `@PathVariable Long id` | `Result<KnowledgeBaseResponse>` | 查询知识库详情 |
| 17 | PUT | `/api/v1/knowledge-bases/{id}` | update | `@PathVariable Long id`, `@Valid @RequestBody KnowledgeBaseUpdateRequest` | `Result<Void>` | 更新知识库 |
| 18 | DELETE | `/api/v1/knowledge-bases/{id}` | delete | `@PathVariable Long id` | `Result<Void>` | 删除知识库（逻辑删除，级联文档） |

### 3.2 DocumentController

路径前缀：`/api/v1`

| 序号 | 方法 | 路径 | 方法名 | 参数 | 返回类型 | 说明 |
|------|------|------|--------|------|----------|------|
| 19 | POST | `/api/v1/knowledge-bases/{kbId}/documents` | upload | `@PathVariable Long kbId`, `@RequestParam("file") MultipartFile file` | `Result<Long>` | 上传文档到知识库 |
| 20 | GET | `/api/v1/knowledge-bases/{kbId}/documents` | list | `@PathVariable Long kbId`, `DocumentListRequest`（Query） | `Result<PageResult<DocumentResponse>>` | 分页查询知识库下的文档列表 |
| 21 | GET | `/api/v1/documents/{id}` | getById | `@PathVariable Long id` | `Result<DocumentResponse>` | 查询文档详情 |
| 22 | GET | `/api/v1/documents/{id}/chunks` | listChunks | `@PathVariable Long id` | `Result<List<DocumentChunkResponse>>` | 查询文档分块列表（pgvector） |
| 23 | DELETE | `/api/v1/documents/{id}` | delete | `@PathVariable Long id` | `Result<Void>` | 删除文档（逻辑删除，级联 chunk） |

---

## 4. MCP 工具

模块：`hify-mcp` | 包路径：`com.hify.modules.mcp.controller`

### 4.1 McpServerController

路径前缀：`/api/v1/mcp-servers`

| 序号 | 方法 | 路径 | 方法名 | 参数 | 返回类型 | 说明 |
|------|------|------|--------|------|----------|------|
| 24 | POST | `/api/v1/mcp-servers` | create | `@Valid @RequestBody McpServerCreateRequest` | `Result<Long>` | 创建 MCP Server |
| 25 | GET | `/api/v1/mcp-servers` | list | `McpServerListRequest`（Query） | `Result<PageResult<McpServerListResponse>>` | 分页查询 MCP Server 列表 |
| 26 | GET | `/api/v1/mcp-servers/{id}` | getById | `@PathVariable Long id` | `Result<McpServerDetailResponse>` | 获取 MCP Server 详情（含工具列表） |
| 27 | PUT | `/api/v1/mcp-servers/{id}` | update | `@PathVariable Long id`, `@Valid @RequestBody McpServerUpdateRequest` | `Result<Void>` | 更新 MCP Server |
| 28 | DELETE | `/api/v1/mcp-servers/{id}` | delete | `@PathVariable Long id` | `Result<Void>` | 删除 MCP Server（逻辑删除） |
| 29 | POST | `/api/v1/mcp-servers/{id}/test` | testConnection | `@PathVariable Long id` | `Result<ConnectionTestResponse>` | 连通性测试 |
| 30 | POST | `/api/v1/mcp-servers/{id}/debug` | debug | `@PathVariable Long id`, `@Valid @RequestBody McpDebugRequest` | `Result<McpDebugResponse>` | 调试调用指定工具 |

---

## 5. 模型管理

模块：`hify-provider` | 包路径：`com.hify.modules.provider.controller`

### 5.1 ProviderController

路径前缀：`/api/v1/providers`

| 序号 | 方法 | 路径 | 方法名 | 参数 | 返回类型 | 说明 |
|------|------|------|--------|------|----------|------|
| 31 | POST | `/api/v1/providers` | create | `@Valid @RequestBody ProviderCreateRequest` | `Result<Long>` | 创建 LLM 供应商 |
| 32 | GET | `/api/v1/providers` | list | `ProviderListRequest`（Query） | `Result<PageResult<ProviderListResponse>>` | 分页查询供应商列表 |
| 33 | GET | `/api/v1/providers/{id}` | getById | `@PathVariable Long id` | `Result<ProviderDetailResponse>` | 获取供应商详情（含模型配置和健康状态） |
| 34 | PUT | `/api/v1/providers/{id}` | update | `@PathVariable Long id`, `@Valid @RequestBody ProviderUpdateRequest` | `Result<Void>` | 更新供应商 |
| 35 | DELETE | `/api/v1/providers/{id}` | delete | `@PathVariable Long id` | `Result<Void>` | 删除供应商（逻辑删除） |
| 36 | POST | `/api/v1/providers/{id}/test-connection` | testConnection | `@PathVariable Long id` | `Result<ConnectionTestResponse>` | 连通性测试 |

---

## 6. 简版工作流

模块：`hify-workflow` | 包路径：`com.hify.modules.workflow.controller`

### 6.1 WorkflowController

路径前缀：`/api/v1/workflows`

| 序号 | 方法 | 路径 | 方法名 | 参数 | 返回类型 | 说明 |
|------|------|------|--------|------|----------|------|
| 37 | POST | `/api/v1/workflows` | create | `@Valid @RequestBody WorkflowCreateRequest` | `Result<WorkflowDetailResponse>` | 创建工作流 |
| 38 | GET | `/api/v1/workflows` | list | `WorkflowListRequest`（Query） | `Result<PageResult<WorkflowListResponse>>` | 分页查询工作流列表 |
| 39 | GET | `/api/v1/workflows/{id}` | getById | `@PathVariable Long id` | `Result<WorkflowDetailResponse>` | 查询工作流详情（含完整节点和边） |
| 40 | PUT | `/api/v1/workflows/{id}` | update | `@PathVariable Long id`, `@Valid @RequestBody WorkflowUpdateRequest` | `Result<Void>` | 更新工作流 |
| 41 | DELETE | `/api/v1/workflows/{id}` | delete | `@PathVariable Long id` | `Result<Void>` | 删除工作流（逻辑删除） |
| 42 | POST | `/api/v1/workflows/{id}/run` | run | `@PathVariable Long id`, `@Valid @RequestBody WorkflowRunRequest` | `Result<WorkflowRunResponse>` | 执行工作流 |

---

## 7. 健康检查

模块：`hify-app` | 包路径：`com.hify.web`

### 7.1 HealthController

路径前缀：`/api/v1`

| 序号 | 方法 | 路径 | 方法名 | 参数 | 返回类型 | 说明 |
|------|------|------|--------|------|----------|------|
| 43 | GET | `/api/v1/health` | health | 无 | `Result<Map<String, Object>>` | 业务层健康检查，聚合 MySQL/Redis/pgvector 状态 |

> **Actuator 端点**（不在上表统计内）：
> - `GET /actuator/health` — Spring Boot Actuator 原生健康端点（端口 8081）
> - `GET /actuator/prometheus` — Prometheus 指标暴露端点（端口 8081）

---

## 附录：全局响应格式

### Result<T> 统一响应

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

### PageResult<T> 分页响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [ ... ],
    "total": 100,
    "size": 20,
    "current": 1,
    "pages": 5
  }
}
```

### SSE 流式响应（ChatController）

```
data: {"type":"delta","content":"..."}

data: {"type":"finish","reason":"stop"}
```

### 健康检查响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "UP",
    "components": {
      "db": { "status": "UP" },
      "redis": { "status": "UP" },
      "pgvector": { "status": "UP" }
    }
  }
}
```

---

## 附录：HTTP 状态码约定

| HTTP 状态码 | 含义 | 触发场景 |
|------------|------|----------|
| 200 | OK | 业务成功（含 Result 包装） |
| 400 | Bad Request | 参数校验失败、请求格式错误 |
| 401 | Unauthorized | 未登录或 Token 过期 |
| 403 | Forbidden | 权限不足 |
| 404 | Not Found | 资源不存在（BizException 抛出） |
| 500 | Internal Server Error | 系统内部异常 |
| 503 | Service Unavailable | LLM 流式调用超限、熔断器 OPEN |

---

## 附录：模块依赖关系

```
hify-app (入口模块)
  ├── hify-common (公共基础设施)
  ├── hify-agent (Agent 配置)
  ├── hify-chat (对话引擎)
  ├── hify-knowledge (知识库 RAG)
  ├── hify-mcp (MCP 工具接入)
  ├── hify-provider (LLM 模型管理)
  └── hify-workflow (简版工作流)
```

> **跨模块调用规则**：只能通过目标模块 `api/` 包下的接口调用，禁止直接 import `domain/` 或 `infra/` 类。
