# Hify 健康问题解决方案

> 基于 `HEALTH_CHECK_REPORT.md` 的系统性修复方案

---

## 执行总览

```
Phase 1（P0 阻断修复）
  ├─ 1.1 Jackson 版本冲突 → MCP 完全不可用
  └─ 1.2 集成测试修复 → CI/CD 无法通过

Phase 2（P1 架构还债）
  ├─ 2.1 hify-knowledge 异常规范化
  ├─ 2.2 补充 llmExecutor 线程池
  └─ 2.3 前端 MCP 页面 + listTools() 对接真实 API

Phase 3（P2 规范补齐）
  ├─ 3.1 数据库 schema 规范化
  └─ 3.2 前端 chunk 优化

Phase 4（P3 技术债务）
  └─ 4.1 清理 TODO
```

---

## Phase 1：P0 阻断修复

---

### 1.1 Jackson 版本冲突（🔴 最高优先级）

#### 问题诊断

```
依赖树冲突：
├─ Spring Boot 3.2.5（parent）
│   └─ jackson-annotations 2.15.4（com.fasterxml.jackson）
│   └─ jackson-databind 2.15.4
├─ io.modelcontextprotocol.sdk:mcp:1.1.1
│   └─ mcp-json-jackson3:1.1.1
│       └─ jackson-databind 3.0.3（tools.jackson）
```

MCP SDK 的 `HttpClientStreamableHttpTransport` 在 `toString()` 方法中调用 `JacksonMcpJsonMapper.writeValueAsString()`，后者使用 Jackson 3 的 `ObjectMapper`，但 Jackson 3 的 `ObjectMapper` 依赖 `com.fasterxml.jackson.annotation.JsonProperty.isRequired()`（Jackson 2.x 2.15.4 中不存在此方法）。

#### 方案 A：升级 Spring Boot 至 3.3.x（推荐 ★）

**原理**：Spring Boot 3.3.x 默认使用 Jackson 2.17.x，其中 `JsonProperty.isRequired()` 已存在。

**修改**：

```xml
<!-- 根 pom.xml -->
<properties>
    <spring-boot.version>3.3.5</spring-boot.version>
</properties>
```

**影响评估**：

| 维度 | 风险 | 说明 |
|------|------|------|
| 兼容性 | 🟡 中 | Spring Boot 3.2→3.3 为次要版本升级，API 兼容，但需验证 MyBatis-Plus、Spring Security 等 starter |
| 工作量 | 🟢 低 | 改一行版本号，重新编译测试即可 |
| 副作用 | 🟢 低 | 3.3 无重大破坏性变更 |

**验证步骤**：
```bash
mvn clean compile -q
mvn test -q -pl hify-app
# 启动后测试 MCP 连通性
curl -X POST http://localhost:8080/api/v1/mcp-servers/1/test
```

---

#### 方案 B：降级 MCP SDK 至兼容 Jackson 2.x 的版本

**原理**：寻找或回退到使用 Jackson 2.x 的 MCP SDK 版本。

**现状**：MCP SDK 1.1.1 官方仅提供 jackson3 适配器，无 jackson2 适配器。1.0.x 版本可能使用 Jackson 2，但 API 不兼容（`McpSyncClient` 等新 API 不存在）。

**结论**：不可行 ❌

---

#### 方案 C：排除 Jackson 3 依赖，手动适配 JSON Mapper（不推荐）

**原理**：在 `hify-mcp/pom.xml` 中排除 `mcp-json-jackson3`，自行实现 `McpJsonMapper` 接口使用 Jackson 2。

**修改**：

```xml
<!-- hify-mcp/pom.xml -->
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
    <version>1.1.1</version>
    <exclusions>
        <exclusion>
            <groupId>io.modelcontextprotocol.sdk</groupId>
            <artifactId>mcp-json-jackson3</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

**代价**：
- 需自行实现 `McpJsonMapper` 接口（约 200 行代码）
- 每次 MCP SDK 升级都需维护适配层
- 长期技术债务

**结论**：仅在无法升级 Spring Boot 时作为备选 🟡

---

#### ★ 推荐方案：A（升级 Spring Boot 至 3.3.5）

**理由**：
1. 一行修改解决根本问题
2. Spring Boot 3.3 为官方维护版本，与当前 3.2 无 API 不兼容
3. 避免引入长期维护的 JSON 适配层
4. 同时获得 3.3 的性能和安全补丁

---

### 1.2 hify-app 集成测试修复

#### 1.2.1 Agent 测试返回 400（4 例）

#### 问题诊断

`AgentServiceImpl.saveRelations()` 新增逻辑：
```java
List<Long> invalidIds = mcpToolService.findInvalidToolIds(toolIds);
if (!invalidIds.isEmpty()) {
    throw new BizException(ErrorCode.PARAM_ERROR,
            "工具不存在或所属 MCP Server 未启用: " + invalidIds);
}
```

测试数据中的 `toolIds: [1001, 1002, 1003]` 在 H2 测试数据库中无对应记录。

#### 方案：更新测试 SQL 数据

**修改文件**：`hify-app/src/test/resources/db/agent-test-data.sql`

```sql
-- 在 agent-test-data.sql 末尾追加 MCP 工具记录
INSERT INTO t_mcp_server (id, name, endpoint, enabled, status, tool_count, deleted)
VALUES (100, 'TestMcpServer', 'http://localhost:9001/mcp', 1, 'connected', 3, 0);

INSERT INTO t_mcp_tool (id, server_id, name, description, input_schema, deleted)
VALUES
(1001, 100, 'web_search', '网络搜索', '{"type":"object"}', 0),
(1002, 100, 'code_executor', '代码执行', '{"type":"object"}', 0),
(1003, 100, 'file_reader', '文件读取', '{"type":"object"}', 0);
```

**依赖**：需同步更新 `agent-validation-test-data.sql`、`agent-update-test-data.sql` 中涉及 toolIds 的测试数据。

**验证**：
```bash
mvn test -q -pl hify-app -Dtest=AgentCreateIntegrationTest
```

---

#### 1.2.2 H2 连接池耗尽（21 例）

#### 问题诊断

- `@SpringBootTest` 默认 fork 模式会启动多个 JVM
- 每个 JVM 持有独立的 H2 内存数据库实例
- HikariCP 默认 `maximumPoolSize=10`，当 8+ 个测试类并行运行时，连接数耗尽

#### 方案 A：限制 Surefire 并行度（推荐 ★）

**修改**：`hify-app/pom.xml`

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <configuration>
                <!-- 串行执行集成测试，避免 H2 连接池竞争 -->
                <forkCount>1</forkCount>
                <reuseForks>false</reuseForks>
                <!-- 或限制并行线程数 -->
                <threadCount>1</threadCount>
                <parallel>classes</parallel>
            </configuration>
        </plugin>
    </plugins>
</build>
```

**影响**：测试执行时间增加（串行化），但稳定性提升。

---

#### 方案 B：增大 H2 连接池

**修改**：`hify-app/src/test/resources/application-test.yml`

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:hify_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE
    hikari:
      maximum-pool-size: 20
      connection-timeout: 10000
      minimum-idle: 5
```

**影响**：单次测试上下文内连接充足，但多个 Spring Context 同时运行时仍可能耗尽。

---

#### ★ 推荐方案：A + B 组合

- 先限制 `forkCount=1`（避免多个 H2 实例竞争）
- 同时增大 `maximum-pool-size=20`
- 若 CI 环境需要加速，可配置 `maven-surefire-plugin` 按环境变量动态选择并行度

---

#### 1.2.3 PostgreSQL 数据库不存在（1 例）

#### 问题诊断

`application-test.yml` 中无 pgvector 配置，但 `application.yml` 中配置了：
```yaml
pgvector:
  datasource:
    url: jdbc:postgresql://8.136.34.168:5432/myhify
```

测试启动时会加载 `application.yml`，即使 `application-test.yml` 未覆盖该配置，Spring 仍会尝试初始化 `DocumentChunkRepository`。

#### 方案：在 test profile 中禁用 pgvector

**修改**：`hify-app/src/test/resources/application-test.yml`

```yaml
# 禁用 pgvector 数据源初始化
pgvector:
  datasource:
    url: jdbc:postgresql://localhost:5432/test_db_not_exist
    username: test
    password: test

# 或在 DocumentChunkRepository 上添加 @ConditionalOnProperty
```

**更优雅的方案**：在 `application-test.yml` 中覆盖 pgvector 配置指向本地 H2（H2 不支持 vector 类型，但至少不会报错）：

```yaml
pgvector:
  datasource:
    url: jdbc:h2:mem:hify_test;MODE=PostgreSQL
```

---

## Phase 2：P1 架构还债

---

### 2.1 hify-knowledge 异常规范化

#### 问题诊断

`DocumentServiceImpl.java` 和 `EmbeddingClient.java` 共 10+ 处使用 `RuntimeException` 传递业务语义，违反 CLAUDE.md 第 7 条：
> "业务异常统一抛 BizException(ErrorCode)，不用 RuntimeException 传递业务语义"

#### 方案：批量替换为 BizException

**步骤 1**：确认 `ErrorCode` 枚举中是否已有对应的错误码

```java
// hify-common/src/main/java/com/hify/common/exception/ErrorCode.java
// 需新增以下错误码（如不存在）：

DOCUMENT_EMPTY("文档内容为空，可能为扫描版 PDF"),
TEXT_SPLIT_EMPTY("文本切分后为空"),
UNSUPPORTED_FILE_TYPE("不支持的文件类型"),
PDF_NO_TEXT_LAYER("PDF 无文字层，可能为扫描版"),
EMBEDDING_PROVIDER_NOT_CONFIG("未配置 Embedding Provider"),
EMBEDDING_PROVIDER_NOT_FOUND("Embedding Provider 不存在"),
EMBEDDING_API_KEY_EMPTY("Embedding API Key 为空"),
EMBEDDING_RESPONSE_INVALID("Embedding 返回数据异常"),
EMBEDDING_CALL_FAILED("Embedding 调用失败");
```

**步骤 2**：批量替换 `DocumentServiceImpl.java`

```java
// 修改前
throw new RuntimeException("文件内容为空，可能为扫描版 PDF（暂不支持）");

// 修改后
throw new BizException(ErrorCode.DOCUMENT_EMPTY, "文件内容为空，可能为扫描版 PDF（暂不支持）");
```

**步骤 3**：批量替换 `EmbeddingClient.java`

```java
// 修改前
throw new RuntimeException("Embedding 调用失败: " + e.getMessage(), e);

// 修改后
throw new BizException(ErrorCode.EMBEDDING_CALL_FAILED, "Embedding 调用失败: " + e.getMessage(), e);
```

**影响范围**：
- 仅影响 `hify-knowledge` 模块内部异常抛出
- `GlobalExceptionHandler` 会自动将 `BizException` 转换为标准 HTTP 响应，无需额外修改
- 测试代码中如捕获 `RuntimeException` 需同步改为捕获 `BizException`

---

### 2.2 补充 llmExecutor 线程池

#### 问题诊断

`AsyncConfig.java` 仅配置了 `llmStreamExecutor`（SSE 流式），缺少非流式 LLM 调用所需的 `llmExecutor`。

#### 方案

**修改**：`hify-app/src/main/java/com/hify/common/config/AsyncConfig.java`

```java
@Bean("llmExecutor")
public ThreadPoolExecutor llmExecutor() {
    return new ThreadPoolExecutor(
        20, 50, 60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(100),
        new ThreadFactoryBuilder().setNameFormat("llm-pool-%d").setDaemon(true).build(),
        new ThreadPoolExecutor.CallerRunsPolicy()
    );
}
```

**验证**：
- 确认 `LlmServiceImpl.chat()` 或相关非流式调用是否注入了 `@Qualifier("llmExecutor") Executor`
- 如当前未使用异步非流式调用，可先声明 Bean 作为预留

---

### 2.3 前端 MCP 页面 + listTools() 对接

#### 问题诊断

1. `hify-web/src/views/mcp/index.vue` 为空白 placeholder
2. `AgentServiceImpl.listTools()` 返回硬编码 mock 数据

#### 方案

**后端部分**：

修改 `AgentServiceImpl.listTools()`：
```java
@Override
public List<ToolOption> listTools() {
    // 从 mcpToolService 获取真实工具列表
    List<McpToolDefinition> definitions = mcpToolService.getToolDefinitions(
        // 查询所有有效工具
        mcpToolMapper.selectList(new LambdaQueryWrapper<>())
            .stream()
            .map(McpTool::getId)
            .toList()
    );
    return definitions.stream().map(def -> {
        ToolOption opt = new ToolOption();
        opt.setId(def.getId());
        opt.setName(def.getName());
        opt.setDescription(def.getDescription());
        return opt;
    }).toList();
}
```

**前端部分**：

开发 `views/mcp/index.vue`：
- 表格展示 MCP Server 列表（名称、端点、状态、工具数）
- 新增/编辑 MCP Server（名称、端点、启用状态）
- 连通性测试按钮
- 展开行显示同步的工具列表

**工作量评估**：约 1 个前端开发日。

---

## Phase 3：P2 规范补齐

---

### 3.1 数据库 schema 规范化

#### 修改清单

| # | 对象 | 操作 | SQL 脚本 |
|---|------|------|---------|
| 1 | `document` → `t_document` | RENAME TABLE | `RENAME TABLE document TO t_document;` |
| 2 | `knowledge_base` → `t_knowledge_base` | RENAME TABLE | `RENAME TABLE knowledge_base TO t_knowledge_base;` |
| 3 | `t_provider_health` | 添加 `deleted` | `ALTER TABLE t_provider_health ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0;` |
| 4 | `t_workflow_run` | 添加 `updated_at` | `ALTER TABLE t_workflow_run ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);` |
| 5 | `t_workflow_node_run` | 添加 `updated_at` | `ALTER TABLE t_workflow_node_run ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);` |

**同步修改**：
1. 所有 Mapper XML 中的表名引用
2. 所有 `t_document` / `t_knowledge_base` 的实体类（`Document.java`, `KnowledgeBase.java`）
3. H2 和 MySQL 两套 schema 脚本（`document-schema-h2.sql`, `document-schema.sql`, `knowledge-schema-h2.sql`, `knowledge-schema.sql`）
4. 前端 API 调用中的表名（如有硬编码）

---

### 3.2 前端 chunk 优化

#### 方案

**修改**：`hify-web/vite.config.ts`

```typescript
export default defineConfig({
  // ...
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'element-plus': ['element-plus'],
          'vendor': ['vue', 'vue-router', 'pinia'],
          'echarts': ['echarts'],
        }
      }
    },
    chunkSizeWarningLimit: 500
  }
});
```

**效果**：将 1MB+ 的 vendor 代码拆分为多个 chunk，利用浏览器并行加载和缓存。

---

## Phase 4：P3 技术债务

---

### 4.1 清理 TODO

| # | 位置 | TODO 内容 | 修复方案 |
|---|------|----------|---------|
| 1 | `AgentServiceImpl.listTools()` | MCP 模块尚未实现，返回 mock | 对接 `McpToolService.getToolDefinitions()` |
| 2 | `ProviderServiceImpl.save()` x2 | apiKey 需要 EncryptionService 加密 | 引入 Jasypt 或 Spring Security Crypto 加密存储/解密读取 |

#### apiKey 加密方案

**方案 A：Spring Security Crypto + 环境变量密钥**

```java
@Service
public class EncryptionService {
    @Value("${hify.encryption.key}")
    private String encryptionKey;
    
    public String encrypt(String plainText) {
        // 使用 AES-GCM 加密
    }
    
    public String decrypt(String cipherText) {
        // 解密
    }
}
```

**配置**：
```yaml
hify:
  encryption:
    key: ${HIFY_ENCRYPTION_KEY}  # 从环境变量读取，禁止硬编码
```

**影响**：
- 存量 apiKey 需在应用启动时自动迁移（读取明文 → 加密存储）
- Provider 查询时自动解密返回（mask 展示）

---

## 执行顺序与依赖关系

```
Step 1: 1.1 Jackson 冲突（升级 Spring Boot）
        └─ 依赖：无
        └─ 验证：mvn compile + MCP test connectivity

Step 2: 1.2.1 测试 SQL 数据更新
        └─ 依赖：Step 1 完成后（确保编译通过）
        └─ 验证：mvn test -pl hify-app -Dtest=AgentCreateIntegrationTest

Step 3: 1.2.2 H2 连接池 + Surefire 配置
        └─ 依赖：Step 2 完成后
        └─ 验证：mvn test -pl hify-app（全量通过）

Step 4: 1.2.3 PostgreSQL 测试配置
        └─ 依赖：Step 3 完成后
        └─ 验证：mvn test -pl hify-app

Step 5: 2.1 hify-knowledge 异常规范化
        └─ 依赖：Step 4 完成后
        └─ 验证：mvn test -pl hify-knowledge

Step 6: 2.2 llmExecutor 线程池
        └─ 依赖：Step 5 完成后
        └─ 验证：启动后检查 Spring Context 中 Bean 存在

Step 7: 2.3 前端 MCP 页面 + listTools()
        └─ 依赖：Step 1 完成后（MCP API 可用）
        └─ 验证：浏览器访问 /mcp + Agent 编辑页工具列表

Step 8: 3.1 数据库 schema 规范化
        └─ 依赖：无（独立操作）
        └─ 验证：执行 schema 脚本 + 应用启动无报错

Step 9: 3.2 前端 chunk 优化
        └─ 依赖：无
        └─ 验证：npm run build 无警告

Step 10: 4.1 清理 TODO
         └─ 依赖：Step 7 完成后
         └─ 验证：grep -r "TODO" 仅保留设计文档中的 TODO
```

---

## 风险评估

| 方案 | 回滚难度 | 对现有功能影响 | 测试覆盖要求 |
|------|---------|---------------|-------------|
| Spring Boot 3.2→3.3 升级 | 🟢 低（改一行版本号） | 🟡 中（需全量回归测试） | 全量集成测试 |
| 测试 SQL 数据更新 | 🟢 低 | 🟢 无（仅影响测试） | 相关测试类 |
| Surefire 串行化 | 🟢 低 | 🟢 无（仅影响测试速度） | 全量测试 |
| 异常规范化 | 🟢 低 | 🟡 中（修改抛出异常类型） | hify-knowledge 模块测试 |
| 数据库 RENAME | 🔴 高（生产环境需停机迁移） | 🔴 高（所有引用点需同步改） | 全量回归 + 数据迁移脚本 |
| 前端 MCP 页面 | 🟢 低 | 🟢 无（新增功能） | 前端 E2E |

---

## 推荐首批执行范围（最小可行修复集）

若当前目标仅为 **消除 P0 阻断问题**，建议执行：

1. ✅ **升级 Spring Boot 至 3.3.5**（解决 Jackson 冲突）
2. ✅ **更新测试 SQL 数据**（解决 Agent 400 错误）
3. ✅ **配置 Surefire forkCount=1 + H2 连接池**（解决连接池耗尽）
4. ✅ **application-test.yml 禁用 pgvector**（解决 PostgreSQL 报错）

**预计工作量**：半天（主要是 Spring Boot 升级后的兼容性验证）。

---

*方案文档完毕。如确认执行方向，请告知具体的 Phase/Step，我将按顺序实施。*
