# Hify E2E 测试问题记录

> 测试日期：2026-05-27
> 测试人：Agent

## 问题列表

| 序号 | 模块 | 接口/功能 | 问题描述 | 状态 | 修复 commit |
|------|------|----------|----------|------|------------|
| 1 | 前端通用 | 所有分页列表 | 前后端分页数据结构不匹配：后端返回 `PageResult{list,total,page,pageSize}`，前端 `PageData` 期望 `{data,total,page,size}`，导致所有表格显示 "No Data" 但 Total 不为 0 | fixed | 未提交 |
| 2 | Provider | 连通性测试 | `EncryptionService.decrypt()` 遇到明文 API Key（数据库历史数据未加密）时抛 RuntimeException，被 GlobalExceptionHandler 捕获返回 1004 内部错误 | fixed | 未提交 |
| 3 | Provider | 连通性测试 | 连通性测试成功后同步远程模型列表到 t_model，使用 MyBatis-Plus 逻辑删除（UPDATE deleted=1），但 `t_model.uk_provider_model_deleted` 唯一索引 `(provider_id, model_code, deleted)` 已存在同 provider+model 的软删除记录，导致 DuplicateKeyException | fixed | 未提交 |
| 4 | Agent | 元数据查询 | `AgentMetaController` 的 `@RequestMapping` 误写为 `/api/v1/agents`，与 `AgentController` 路径冲突，导致 `/api/v1/agent-meta/models` 和 `/api/v1/agent-meta/tools` 返回 404（No static resource） | fixed | 未提交 |
| 5 | Knowledge | 文档分块/向量检索 | `@Qualifier("pgvectorJdbcTemplate")` 未生效，`DocumentChunkRepository` 和 `PgVectorHealthIndicator` 实际注入的是 MySQL 的 `JdbcTemplate`，导致所有 pgvector 操作都落到 MySQL（表不存在 1004） | fixed | 未提交 |
| 6 | Knowledge | 远程 pgvector 连接 | 修复 `@Qualifier` 后发现远程 PostgreSQL (8.136.34.168:5432) 密码认证失败：`FATAL: password authentication failed for user "postgres"`。当前配置密码为 `123456`，与实际密码不符 | fixed | 服务器终端创建 myhify 用户并设置密码 123456 |
| 7 | MCP | MCP Server 创建 | 前端 `enabled` 字段为布尔值 `true/false`，后端 `McpServerCreateRequest.enabled` 为 `Integer`，JSON 反序列化失败：`Cannot deserialize value of type java.lang.Integer from Boolean value` | fixed | 未提交 |
| 8 | Workflow | 工作流执行记录表结构 | `t_workflow_node_run` 和 `t_workflow_run` 表缺少 `updated_at` 字段，但实体类 `WorkflowNodeRun` / `WorkflowRun` 继承 `BaseEntity`（含 `updatedAt`），MyBatis-Plus 插入/更新时抛出 `Unknown column 'updated_at' in 'field list'` | fixed | 未提交 |
| 9 | Workflow | 工作流节点模型配置引用 | `t_workflow_node` 表中 LLM 节点的 `modelConfigId` 引用的是旧模型 ID（3），Provider 连通性测试同步后该模型被删除，导致工作流执行时 `模型配置不存在` | fixed | 未提交 |
| 10 | Knowledge | pgvector 缺少 document_chunk 表 | 修复问题 6 后 pgvector 连接成功，但查询 `document_chunk` 表报错 `ERROR: relation "document_chunk" does not exist`。远程 pgvector 数据库未初始化表结构 | fixed | 未提交 |
| 11 | MCP | MCP Server enabled 类型不匹配（后端） | 前端 `mcp.ts` 已做布尔值转整数，但直接 API 调用仍失败。根因是后端 `McpServerCreateRequest.enabled` / `McpServerUpdateRequest.enabled` 为 `Integer` 类型 | fixed | 未提交 |

## 问题 1 详情

**影响范围：** 模型管理、Agent 管理、知识库、MCP Server、工作流等所有使用 `HifyTable` 的分页页面。

**根因：**
- 后端 `PageResult.java` 字段：`list`, `total`, `page`, `pageSize`
- 前端 `PageData` 接口定义：`data`, `total`, `page`, `size`
- 同时存在两种 API 调用方式：
  - `axios.get()` 直接调用（获取完整 `Result` 对象）
  - `get()` 经过拦截器（只返回 `Result.data` 即 `PageResult`）

**修复内容：**
1. `HifyTable.vue`: `PageData` 接口字段改为 `list`, `pageSize`; `fetchData` 使用 `res.list`
2. `provider.ts`: `PageData` 接口字段对齐; 保持不变（已使用 `get()`）
3. `agent.ts`: `PageData` 接口字段对齐; `getAgentList` 改用 `get()` 替代 `axios.get()`
4. `knowledge.ts`: `PageData` 接口字段对齐; `getKnowledgeBaseList`/`getDocumentList` 改用 `get()`
5. `mcp.ts`: `PageData` 接口字段对齐
6. `workflow.ts`: `PageData` 接口字段对齐; `getWorkflowList` 改用 `get()`

## 问题 2 详情

**影响范围：** 所有需要调用 LLM 的功能（Provider 连通性测试、Agent 对话、工作流 LLM 节点等）。

**根因：**
- 数据库中历史 API Key 为明文存储（如 `sk-a0b43ec02f2e45c382683fbc5812f9e3`）
- `EncryptionService.decrypt()` 期望输入为 Base64 编码的 AES 密文，对明文调用会抛出 `RuntimeException("解密失败")`
- `ProviderConnectionTestService.test()`、`ProviderServiceImpl.testConnection()`、`LlmServiceImpl.resolveProvider()` 等均在调用链路中执行解密

**修复内容：**
- `EncryptionService.java`: `decrypt()` 捕获异常后原样返回输入值（兼容明文与密文两种形态），并补充 JavaDoc 说明

## 问题 3 详情

**影响范围：** Provider 连通性测试（当该供应商已存在模型配置且再次执行连通性测试时）。

**根因：**
- `ProviderServiceImpl.testConnection()` 在同步远程模型列表时使用 `modelConfigMapper.delete(...)`，MyBatis-Plus 默认执行逻辑删除（`UPDATE t_model SET deleted=1`）
- `t_model` 表存在唯一索引 `uk_provider_model_deleted`（字段组合：`provider_id`, `model_code`, `deleted`）
- 若此前已存在同 `provider_id` + `model_code` 且 `deleted=1` 的记录，再次软删除会触发 `DuplicateKeyException`
- 初步修复使用物理删除，但导致外键引用（`t_agent.model_config_id`）失效

**修复内容：**
1. `ProviderServiceImpl.java`: 放弃“先删除再插入”策略，改为：
   - 查询该供应商下所有现有模型（含软删除），按 `modelCode` 索引
   - 远程返回的模型若本地已存在，则更新原记录（恢复软删除、更新名称、保留原 ID）
   - 远程返回的模型若本地不存在，则插入新记录
   - 本地存在但远程未返回的活跃模型，执行软删除
2. 数据修复：将 `t_agent` 中引用已删除旧模型 ID 的记录更新为新的有效模型 ID

## 问题 4 详情

**影响范围：** Agent 管理页面的模型下拉框和工具下拉框无法加载。

**根因：**
- `AgentMetaController` 的 `@RequestMapping` 注解值为 `/api/v1/agents`，与 `AgentController` 相同
- 前端调用 `/api/v1/agent-meta/models` 和 `/api/v1/agent-meta/tools` 时，Spring Boot 找不到匹配的控制器方法，回退到静态资源处理，返回 `NoResourceFoundException`

**修复内容：**
- `AgentMetaController.java`: 将 `@RequestMapping("/api/v1/agents")` 改为 `@RequestMapping("/api/v1/agent-meta")`

## 问题 5 详情

**影响范围：** 所有依赖 pgvector 的功能：文档分块查看、知识库向量检索、文档删除（级联删除 chunk）、文档上传后的异步向量化。

**根因：**
- `DocumentChunkRepository` 使用 `@RequiredArgsConstructor`，字段上的 `@Qualifier("pgvectorJdbcTemplate")` 不会被 Lombok 复制到构造参数
- Spring 构造器注入时忽略字段注解，按类型查找 `JdbcTemplate`，找到 Spring Boot 自动配置的主数据源（MySQL）`JdbcTemplate`（唯一候选或带 `@Primary`）
- 结果所有 pgvector SQL（含 `document_chunk` 表查询）都被执行到 MySQL，返回 `Table 'hify.document_chunk' doesn't exist`
- `PgVectorHealthIndicator` 同样注入错误，`SELECT 1` 在 MySQL 上成功，导致健康检查误报 UP

**修复内容：**
1. `DocumentChunkRepository.java`: 移除 `@RequiredArgsConstructor`，显式声明构造方法并在参数上加 `@Qualifier("pgvectorJdbcTemplate")`
2. `PgVectorHealthIndicator.java`: 在构造参数上加 `@Qualifier("pgvectorJdbcTemplate")`

## 问题 6 详情

**影响范围：** 知识库 RAG 全流程（文档向量化、分块查看、相似度检索、文档删除级联清理）。

**根因：**
- 修复问题 5 后，`pgvectorJdbcTemplate` 正确指向 PostgreSQL 数据源
- 但远程 PostgreSQL（8.136.34.168:5432）不存在 `myhify` 用户，密码认证失败
- 后端异常：`org.postgresql.util.PSQLException: FATAL: password authentication failed for user "myhify"`

**修复内容：**
- 服务器终端：创建 `myhify` 用户并设置密码 `123456`，授权 public schema
- `application.yml` 中 `pgvector.datasource.username` 已改为 `myhify`

## 问题 7 详情

**影响范围：** MCP Server 新增/编辑功能无法使用。

**根因：**
- 前端使用 `el-switch` 绑定布尔值 `true/false`
- 后端 `McpServerCreateRequest.enabled` 和 `McpServerUpdateRequest.enabled` 类型为 `Integer`
- Jackson 反序列化时无法将 JSON 布尔值转为 Integer

**修复内容：**
1. `mcp.ts`: 在调用 `createMcpServer` / `updateMcpServer` 前将 `enabled` 布尔值转换为 `1/0`（前端兼容层）
2. `McpServerCreateRequest.java` / `McpServerUpdateRequest.java`: 将 `enabled` 类型从 `Integer` 改为 `Boolean`
3. `McpServerServiceImpl.java`: 在 Service 层将 `Boolean` 转换为 `Integer`（`true→1`, `false→0`）后存入数据库

## 问题 8 详情

**影响范围：** 所有工作流执行（包括 Agent 对话触发的工作流）。

**根因：**
- `WorkflowNodeRun` 和 `WorkflowRun` 实体继承 `BaseEntity`，包含 `updatedAt` 字段
- 数据库表 `t_workflow_node_run` 和 `t_workflow_run` 建表时未包含 `updated_at` 列
- MyBatis-Plus 自动填充 `updated_at` 时触发 `Unknown column` 错误

**修复内容：**
- 数据库 DDL：为 `t_workflow_node_run` 和 `t_workflow_run` 添加 `updated_at` 字段

## 问题 9 详情

**影响范围：** 所有绑定工作流的 Agent（如智能客服）。

**根因：**
- Provider 连通性测试同步模型列表后，旧模型 ID（如 3）被软删除，新模型 ID 为 5/6
- `t_workflow_node` 表中 LLM 节点的 `config_json` 仍引用旧 `modelConfigId: 3`
- 工作流执行时根据 `modelConfigId` 查询模型，返回 `模型配置不存在`

**修复内容：**
- 数据库数据修复：将 `t_workflow_node` 中所有 `modelConfigId: 3` 更新为 `5`

## 问题 10 详情

**影响范围：** 知识库文档分块查看、向量检索、文档删除级联清理。

**根因：**
- 修复问题 6 后，pgvector 连接认证成功
- 但远程 PostgreSQL 的 `myhify` 数据库中从未执行过初始化脚本，`document_chunk` 表不存在
- 后端执行 `SELECT ... FROM document_chunk` 时抛出 `BadSqlGrammarException`

**修复内容：**
- 在服务器终端通过 `psql` 以 `postgres` 超级用户执行 `init_document_chunk_1024.sql`
- 安装 `vector` 扩展，创建 `document_chunk` 表及 HNSW 索引

## 问题 11 详情

**影响范围：** MCP Server 新增/编辑的 API 调用（绕过前端时）。

**根因：**
- 前端 `mcp.ts` 已对 `enabled` 做 `boolean → 1/0` 转换，前端调用正常
- 但后端 `McpServerCreateRequest.enabled` 和 `McpServerUpdateRequest.enabled` 仍为 `Integer`
- 直接调用 API 传入 JSON 布尔值时，Jackson 反序列化抛出 `MismatchedInputException`

**修复内容：**
- `McpServerCreateRequest.java`: `private Integer enabled = 1;` → `private Boolean enabled = true;`
- `McpServerUpdateRequest.java`: `private Integer enabled;` → `private Boolean enabled;`
- `McpServerServiceImpl.java`: 在保存到数据库前将 `Boolean` 转为 `Integer`

## 状态说明
- open: 待修复
- fixed: 已修复
- verified: 修复后已验证
