# Hify 项目健康检查报告

> 生成时间：2026-05-23  
> 检查分支：`feature/mcp`  
> 检查范围：全量后端模块（hify-common ~ hify-app）+ 前端  
> 规范依据：`CLAUDE.md` + `AGENTS.md`

---

## 一、执行摘要

| 维度 | 状态 | 说明 |
|------|------|------|
| 编译 | 🟢 通过 | `mvn compile` 全量通过 |
| 安全审计 | 🟢 通过 | 无敏感信息泄露 |
| 单元/集成测试 | 🔴 严重 | hify-app 45 测，4 失败 + 21 错误 |
| 架构合规 | 🟡 警告 | 存在历史遗留的表名不规范、RuntimeException 滥用 |
| 代码规范 | 🟡 警告 | 3 处 TODO，hify-knowledge 违反异常规范 |
| 数据库规范 | 🟡 警告 | 2 张表无前缀，3 张表缺必需字段 |
| 前端编译 | 🟢 通过 | `npm run build` 成功（仅 chunk 大小警告） |
| 依赖冲突 | 🟢 通过 | 未发现 Maven 循环依赖或显式依赖冲突 |

**总体评级：🟡 亚健康（测试大面积失败，存在架构债务）**

---

## 二、测试健康度详情

### 2.1 测试结果统计

| 模块 | 测试数 | 失败 | 错误 | 跳过 |
|------|--------|------|------|------|
| hify-common | — | 0 | 0 | 0 |
| hify-provider | — | 0 | 0 | 0 |
| hify-agent | — | 0 | 0 | 0 |
| hify-chat | — | 0 | 0 | 0 |
| hify-mcp | — | 0 | 0 | 0 |
| hify-knowledge | — | 0 | 0 | 0 |
| hify-workflow | — | 0 | 0 | 0 |
| **hify-app（集成测试）** | **45** | **4** | **21** | **0** |

### 2.2 失败根因分类

#### 🔴 类型 A：Agent 创建/更新返回 400（4 例）

**代表用例**：
- `AgentCreateIntegrationTest.createAgent_basicFieldsAndRelations_shouldPersistAll`
- `AgentCloneIntegrationTest.cloneAgent_shouldCreateCopyWithSuffixAndDisabled`
- `AgentUpdateIntegrationTest.updateAgent_shouldUpdateFieldsAndReplaceRelations`

**根因**：
`AgentServiceImpl.saveRelations()` 在 `feature/mcp` 分支中新增了对 `McpToolService.findInvalidToolIds()` 的调用。测试数据中的 `toolIds: [1001, 1002, 1003]` 在 `t_mcp_tool` 表中不存在，因此被判定为无效工具，返回：
```
400 PARAM_ERROR: 工具不存在或所属 MCP Server 未启用: [1001, 1002, 1003]
```

**结论**：这是**功能变更导致测试数据失效**，属于预期内的行为变更，需同步更新测试脚本（在 H2 测试数据中插入对应 MCP 工具）。

#### 🔴 类型 B：H2 连接池耗尽（21 例）

**症状**：`CannotGetJdbcConnectionException: Failed to obtain JDBC Connection`

**根因**：
- hify-app 集成测试使用了 `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@Transactional`
- 测试类未正确限制并行度，多个 Spring Context 同时启动，H2 内存数据库连接数被耗尽
- 受影响模块：Agent、Chat、Provider

**结论**：测试基础设施问题，非业务逻辑缺陷。

#### 🟡 类型 C：PostgreSQL 数据库不存在（1 例）

**症状**：`FATAL: database "myhify" does not exist`

**根因**：`application-test.yml` 或 `application.yml` 中配置了 pgvector 数据源指向 `jdbc:postgresql://8.136.34.168:5432/myhify`，该远程数据库在测试环境不可达。

**结论**：测试环境配置问题。

---

## 三、架构合规性检查（基于 CLAUDE.md）

### 3.1 包结构合规 ✅

| 模块 | api/ | domain/ | infra/ | web/ | 合规 |
|------|------|---------|--------|------|------|
| hify-agent | ✅ | ✅ | ✅ | ✅ | 是 |
| hify-chat | ✅ | ✅ | ✅ | ✅ | 是 |
| hify-mcp | ✅ | ✅ | ✅ | ✅ | 是 |
| hify-provider | ✅ | ✅ | ✅ | ✅ | 是 |
| hify-knowledge | ✅ | ✅ | ✅ | ✅ | 是 |
| hify-workflow | ✅ | ✅ | ✅ | ✅ | 是 |

### 3.2 跨模块调用合规 ✅

- 未发现任何模块直接 `import` 其他模块的 `domain/` 或 `infra/` 类
- 未发现 Maven 循环依赖
- **注**：本次检查前已修复 `hify-agent ↔ hify-mcp` 循环依赖（将 `AgentBindingApi` 和 `McpToolService` 提取至 `hify-common`）

### 3.3 线程池配置 ⚠️

| 检查项 | 规范要求 | 实际情况 | 状态 |
|--------|---------|---------|------|
| llmStreamExecutor | 显式 ThreadPoolExecutor | `AsyncConfig` 已配置 | ✅ |
| llmExecutor | 显式 ThreadPoolExecutor | **缺失** | ❌ |
| documentParseExecutor | 显式 ThreadPoolExecutor | 已配置 | ✅ |
| CompletableFuture | 必须指定自定义线程池 | 未使用默认 ForkJoinPool | ✅ |

**问题**：`AsyncConfig` 中只定义了 `llmStreamExecutor`，缺少 CLAUDE.md 要求的 `llmExecutor`（非流式 LLM 调用线程池）。

### 3.4 LLM 超时与重试 ⚠️

- `standardLlmClient` / `streamLlmClient` 的 OkHttp 配置已按规范实现
- Resilience4j 熔断器配置在 YAML 中存在
- **未验证**：熔断器是否在实际 LLM 调用路径上生效

---

## 四、代码规范检查

### 4.1 命名规范 ✅

- 类名 UpperCamelCase、方法/变量 lowerCamelCase、常量 UPPER_SNAKE_CASE
- 未发现拼音命名
- Service 接口无 `I` 前缀，实现类有 `Impl` 后缀
- 数据库表名 `t_` 前缀覆盖率高（仅 2 张历史表例外）

### 4.2 异常处理 ❌

| 检查项 | 规范要求 | 实际情况 | 状态 |
|--------|---------|---------|------|
| 业务异常 | 统一抛 `BizException(ErrorCode)` | `hify-knowledge` 大量使用 `RuntimeException` | ❌ |
| 空 catch | 禁止空 catch / `e.printStackTrace()` | 未发现 | ✅ |
| 顶层处理 | `GlobalExceptionHandler` 统一转换 | 存在 | ✅ |

**违规详情**（`hify-knowledge` 模块）：
```
DocumentServiceImpl.java:140  throw new RuntimeException("文件内容为空...")
DocumentServiceImpl.java:146  throw new RuntimeException("文本切分后为空")
DocumentServiceImpl.java:195  throw new RuntimeException("不支持的文件类型: " + fileType)
DocumentServiceImpl.java:205  throw new RuntimeException("PDF 无文字层...")
DocumentServiceImpl.java:348  throw new RuntimeException("未配置 Embedding Provider ID")
DocumentServiceImpl.java:352  throw new RuntimeException("Embedding Provider 不存在...")
DocumentServiceImpl.java:361  throw new RuntimeException("Embedding API Key 为空...")
EmbeddingClient.java:104      throw new RuntimeException("Embedding 返回缺少 index=" + i)
EmbeddingClient.java:113      throw new RuntimeException("Embedding 调用失败: " + e.getMessage(), e)
EmbeddingClient.java:165      throw new RuntimeException(...)
```

**共计 10+ 处**，违反了 CLAUDE.md 第 7 条。

### 4.3 日志规范 ✅

- 未发现 `System.out.println`
- 未发现字符串拼接日志（均使用 `{}` 占位符）
- 未发现循环体内打日志
- LLM 调用关键节点有耗时/Token 记录

### 4.4 TODO 清单（3 处）

| 位置 | 内容 | 优先级 |
|------|------|--------|
| `AgentServiceImpl.listTools()` | MCP 模块尚未实现，返回 mock 数据 | 高 |
| `ProviderServiceImpl.save()` x2 | apiKey 需要通过 EncryptionService 加密后存储 | 中 |

---

## 五、数据库规范检查

### 5.1 表名前缀

| 表名 | 前缀合规 | 说明 |
|------|---------|------|
| `document` | ❌ | 历史遗留，应为 `t_document` |
| `knowledge_base` | ❌ | 历史遗留，应为 `t_knowledge_base` |
| 其余 16 张表 | ✅ | 均带 `t_` 前缀 |

### 5.2 必需字段检查

CLAUDE.md 要求每张表必须包含：`id`, `created_at`, `updated_at`, `deleted`。

| 表名 | 缺失字段 | 说明 |
|------|---------|------|
| `t_provider_health` | `deleted` | 健康检查记录表，无逻辑删除 |
| `t_workflow_run` | `updated_at` | 工作流执行记录表 |
| `t_workflow_node_run` | `updated_at` | 节点执行记录表 |
| `t_agent_knowledge_rel` | `deleted`, `updated_at` | 关联表（通常可豁免） |
| `t_agent_tool` | `deleted`, `updated_at` | 关联表（通常可豁免） |

### 5.3 索引规范

- 未执行 EXPLAIN 扫描（需要运行中的数据库 + 生产量级数据）
- 所有表均定义了主键
- 组合索引设计未发现明显违规

---

## 六、依赖健康

### 6.1 Maven 依赖

- **无显式依赖冲突**（`mvn dependency:tree` 未报告 conflict/omitted）
- **Jackson 版本隐患**：`mcp-json-jackson3-1.1.1` 引入 `jackson-databind 3.0.3`，与 Spring Boot 3.2.5 的 `jackson-annotations 2.15.4` 共存，运行时触发 `NoSuchMethodError: JsonProperty.isRequired()`
- **风险等级**：🔴 高（导致 MCP 连通性测试不可用）

### 6.2 前端依赖

- `npm run build` 成功
- 警告：单个 chunk 超过 500KB（`index-DvFKWX17.js` 1.03MB），建议代码分割

---

## 七、测试基础设施问题

### 7.1 H2 测试数据库连接池

**现象**：21 个集成测试在并发执行时 `CannotGetJdbcConnectionException`
**建议**：
```yaml
# application-test.yml 中增加 HikariCP 配置
spring.datasource.hikari.maximum-pool-size: 5
spring.datasource.hikari.connection-timeout: 5000
```
或在 `pom.xml` 中限制 Surefire 并行度：
```xml
<configuration>
    <forkCount>1</forkCount>
    <reuseForks>false</reuseForks>
</configuration>
```

### 7.2 测试数据与业务逻辑不同步

**现象**：Agent 测试的 `toolIds: [1001, 1002, 1003]` 在新逻辑下被判定为无效
**建议**：更新 `agent-test-data.sql`，在测试前置阶段插入对应的 MCP Server + MCP Tool 记录。

### 7.3 pgvector 数据库不可达

**现象**：远程 PostgreSQL `myhify` 不存在
**建议**：测试环境使用本地 H2 或 Testcontainers 替代远程 pgvector。

---

## 八、前端状态

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 编译 | ✅ | `vite build` 成功 |
| MCP 管理页 | ❌ | `/mcp` 为空白 placeholder |
| Agent 工具绑定 | ✅ | `/agents` 编辑弹窗有"工具绑定"Tab |
| Chat 对话 | ✅ | 可正常发送消息并接收 SSE 流式回复 |
| 工具选项数据 | ⚠️ | 前端 `listTools()` 仍返回硬编码 mock（web_search/code_executor/file_reader） |

---

## 九、修复优先级建议

### P0（阻断发布）

1. **修复 Jackson 版本冲突**
   - 方案 A：升级 Spring Boot 至 3.3.x（默认 Jackson 2.17+）
   - 方案 B：在 `hify-mcp/pom.xml` 中排除 `mcp-json-jackson3`，使用 Jackson 2.x 兼容的 JSON Mapper
   - 影响：MCP 连通性测试和工具调用完全不可用

2. **修复 hify-app 集成测试**
   - 更新测试 SQL 数据（补充 MCP 工具记录）
   - 限制测试并行度或增大 H2 连接池
   - 影响：CI/CD 无法通过

### P1（高优先级）

3. **hify-knowledge 异常规范化**
   - 将 10+ 处 `RuntimeException` 替换为 `BizException(ErrorCode)`
   - 影响：代码规范、异常监控可观测性

4. **补充 `llmExecutor` 线程池**
   - 在 `AsyncConfig` 中增加非流式 LLM 调用的线程池配置
   - 影响：与 CLAUDE.md 规范不一致

5. **前端 MCP 页面开发**
   - 将 `/mcp` placeholder 替换为真实的 MCP Server CRUD 页面
   - 前端 `listTools()` 需对接真实 API

### P2（中优先级）

6. **数据库 schema 规范化**
   - `document` → `t_document`
   - `knowledge_base` → `t_knowledge_base`
   - 补充 `t_provider_health.deleted`、`t_workflow_run.updated_at`、`t_workflow_node_run.updated_at`

7. **前端 chunk 优化**
   - 对 1MB+ 的 JS chunk 进行动态导入拆分

### P3（低优先级）

8. **清理 3 处 TODO**
   - `AgentServiceImpl.listTools()` 对接真实 MCP 查询
   - `ProviderServiceImpl` 接入 EncryptionService 加密 apiKey

---

## 十、附录：检查命令清单

```bash
# 编译
mvn compile -q

# 测试
mvn test -q
mvn test -q -pl hify-common,hify-provider,hify-agent,hify-chat,hify-mcp,hify-knowledge,hify-workflow

# 安全审计
bash scripts/security-audit.sh

# 依赖冲突
mvn dependency:tree -q | grep -i "conflict\|omitted"

# 前端编译
cd hify-web && npm run build
```

---

*报告生成完毕。如需针对某一类问题进行深度排查或修复，请告知具体维度。*
