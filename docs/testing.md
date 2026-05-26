## 核心链路、风险区域与测试重心

> 以下分析由 AI Agent 扫描全量代码后生成，基于当前 `master` 分支代码现状。
> 分析日期：2026-05-24

### 核心链路清单

以下 5 条链路覆盖了 90% 的用户价值和故障影响面。

#### 链路 1：SSE 流式对话（最重要）

| 项目 | 内容 |
|------|------|
| **名称** | SSE 流式对话与工具调用 |
| **入口** | `ChatController.createStreamEmitter()` → `ChatServiceImpl.sendMessage()` |
| **涉及模块与类** | `hify-chat`: `ChatServiceImpl`, `ChatPersistenceService`, `ChatContextAssembler`<br>`hify-provider`: `LlmServiceImpl`, `ProviderAdapterFactory`, `OpenAiAdapter`<br>`hify-knowledge`: `KnowledgeRetrievalService`<br>`hify-mcp`: `McpClientService`, `McpToolService`<br>`hify-workflow`: `WorkflowRunService` |
| **为什么是核心** | 这是用户唯一的高频交互入口。集成了 RAG 检索、MCP 工具两阶段调用、工作流路由三大能力，任意环节失败都会直接暴露给用户。SSE 长连接 + 异步线程池使调试和异常定位难度倍增。 |

#### 链路 2：文档处理管线（知识库基础）

| 项目 | 内容 |
|------|------|
| **名称** | 文档上传 → 解析 → 切分 → 向量化 → 存储 |
| **入口** | `DocumentController.upload()` → `DocumentServiceImpl.upload()` |
| **涉及模块与类** | `hify-knowledge`: `DocumentServiceImpl`（`processDocument`, `splitChunks`, `embedChunks`, `saveChunks`）<br>`hify-knowledge`: `DocumentChunkRepository`, `EmbeddingClient`<br>`hify-provider`: `ProviderService`（获取 Embedding 用 API Key） |
| **为什么是核心** | 知识库价值的上限取决于这条管线的可靠性。异步线程执行、文件系统 IO、外部 Embedding API、pgvector 写入四个异构环节串联，任意一步失败都会导致文档状态卡死或数据不一致。 |

#### 链路 3：工作流执行引擎

| 项目 | 内容 |
|------|------|
| **名称** | 工作流图遍历与节点执行 |
| **入口** | `ChatServiceImpl.sendMessage()`（当 `agent.workflowId != null`）→ `WorkflowRunService.run()` |
| **涉及模块与类** | `hify-workflow`: `WorkflowEngine`, `NodeExecutorRegistry`, `NodeConfigParser`<br>`hify-workflow`: `LlmNodeExecutor`, `ConditionNodeExecutor`, `ApiCallNodeExecutor`, `KnowledgeNodeExecutor` |
| **为什么是核心** | 一旦 Agent 绑定工作流，所有对话流量都会被路由到这里。引擎包含条件分支路由、50 步死循环保护、节点级落库容错，复杂图结构下的边界条件极多。 |

#### 链路 4：Provider 管理与 LLM 调用

| 项目 | 内容 |
|------|------|
| **名称** | Provider 配置 → 健康检查 → 模型同步 → LLM 适配器分发 |
| **入口** | `ProviderController.testConnection()` / `ChatServiceImpl` 任意对话 |
| **涉及模块与类** | `hify-provider`: `ProviderServiceImpl`, `LlmServiceImpl`, `ProviderAdapterFactory`<br>`hify-provider`: `OpenAiAdapter`, `AnthropicAdapter`, `OllamaAdapter`, `OpenAiCompatibleAdapter` |
| **为什么是核心** | 所有 LLM 能力（对话、Embedding、工作流 LLM 节点）的根基。API Key 加密存储、熔断器（Resilience4j）、健康状态同步、模型自动发现，任一环节失效会导致全平台不可用。 |

#### 链路 5：MCP 工具发现与调用

| 项目 | 内容 |
|------|------|
| **名称** | MCP Server 连通性测试 → 工具同步 → 对话中工具调用 |
| **入口** | `McpServerController.testConnection()` / `ChatServiceImpl` 内工具触发 |
| **涉及模块与类** | `hify-mcp`: `McpServerServiceImpl`, `McpClientServiceImpl`<br>`hify-chat`: `ChatServiceImpl.loadToolSchemas()`, `executeToolCalls()` |
| **为什么是核心** | MCP 是系统区别于普通 Chatbot 的核心差异化能力。工具同步涉及外部 HTTP 端点状态变化，对话中的工具调用是同步阻塞调用（在 SSE 线程内执行），超时或异常会拖垮整个流式响应。 |

---

### 风险集中区域

以下区域是故障高发区，出问题后影响面最大。

#### 风险 1：ChatServiceImpl SSE 线程中的状态一致性

| 属性 | 说明 |
|------|------|
| **位置** | `ChatServiceImpl.sendMessage()` / `handleFinish()` |
| **风险类型** | 并发 + 数据一致性 |
| **失败场景** | 1. `handleFinish` 中 `saveAssistantMessage` 与 `updateSessionTitle` 是两个独立事务调用，若中间进程崩溃，消息已存但标题未更新。<br>2. MCP 工具调用（`executeToolCalls`）在 SSE 线程内同步执行，若工具端点超时（默认无超时控制），会阻塞线程池导致后续请求排队。<br>3. 客户端中途断开时 `cancelled` 标记为 true，但 `handleFinish` 的 `compareAndSet` 竞争可能导致消息重复入库。 |

#### 风险 2：EncryptionService 加密方案（安全红线）

| 属性 | 说明 |
|------|------|
| **位置** | `EncryptionService`（`hify-common`） |
| **风险类型** | 安全 |
| **失败场景** | 1. 使用 `AES/ECB/PKCS5Padding`，ECB 模式不使用 IV，相同明文产生相同密文，可被模式分析攻击。<br>2. 密钥从 `application.yml` 读取，默认值为 `HifyDefaultKey16`，若用户未修改，所有部署实例使用相同密钥，数据库泄露后 API Key 可被批量解密。<br>3. 密钥格式化逻辑 `String.format("%-16s", key).substring(0, 16)` 对短密钥进行空格填充，降低熵值。 |

#### 风险 3：DocumentServiceImpl 异步管线无重试与补偿

| 属性 | 说明 |
|------|------|
| **位置** | `DocumentServiceImpl.processDocument()` / `embedChunks()` |
| **风险类型** | 性能 + 数据一致性 |
| **失败场景** | 1. `embedChunks` 批量调用外部 Embedding API，若网络抖动或限流，整个文档处理直接进入 `FAILED` 状态，无分段重试机制。<br>2. 异步线程 `documentParseExecutor` 执行，`upload()` 方法的事务已提交，但后续异步步骤失败，仅通过 `updateStatus` 回写状态，无事件补偿或死信队列。<br>3. 大 PDF 文本提取后若产生数万 chunks，`embedBatch` 一次性传入全部文本，可能导致内存溢出或 Embedding API 超时。 |

#### 风险 4：ProviderServiceImpl.testConnection 的模型同步事务边界

| 属性 | 说明 |
|------|------|
| **位置** | `ProviderServiceImpl.testConnection()` |
| **风险类型** | 数据一致性 |
| **失败场景** | 1. 方法标记 `@Transactional`，但内部先 `modelConfigMapper.delete()` 软删除旧模型，再循环 `insert()` 新模型。若插入中途抛出异常，事务回滚，但此时外部健康状态表（`t_provider_health`）可能已独立更新（因为健康状态更新在 catch 之前）。<br>2. 连通性测试成功但模型同步时遇到重复 `modelCode`，可能导致唯一索引冲突回滚整个事务，使 Provider 状态与实际可用性不一致。 |

#### 风险 5：WorkflowEngine 节点执行副作用不可回滚

| 属性 | 说明 |
|------|------|
| **位置** | `WorkflowEngine.executeNode()` / `LlmNodeExecutor` / `ApiCallNodeExecutor` |
| **风险类型** | 数据一致性 + 性能 |
| **失败场景** | 1. `executeNode` 执行节点后若抛异常，外层会标记整个工作流 `FAILED`，但 LLM 调用或外部 API 调用的副作用（如扣费、写数据）无法回滚。<br>2. `findNextNode` 对 CONDITION 节点的路由依赖于 `ctx.get()` 的布尔结果，若上游节点未正确写入变量或类型不匹配，会落入 `null` 分支导致工作流异常终止。<br>3. `MAX_EXECUTION_STEPS = 50` 是最后一道防线，但步数计数在内存中，若引擎重启会丢失运行中状态。 |

---

### 测试重心建议

基于"核心链路优先、风险高的优先"原则，测试精力分配如下：

#### 🔴 必须有测试覆盖（P0）

| 测试目标 | 为什么必须 | 测试类型建议 |
|----------|-----------|-------------|
| `ChatServiceImpl` 两阶段工具调用 | 工具调用是差异化能力，无工具/有工具不调用/有工具调用失败三种分支必须验证 | 单元测试（Mock `LlmService`, `McpClientService`） |
| `ChatServiceImpl.handleFinish` 并发安全 | `AtomicBoolean` 竞争条件、客户端断开后消息不重复入库 | 单元测试（模拟 `SseEmitter` 超时/完成） |
| `DocumentServiceImpl.splitChunks` 切分算法 | 边界条件极多（空文本、超长无标点文本、overlap 回退逻辑），当前无测试 | 纯单元测试（多种输入文本断言输出 chunk 边界） |
| `WorkflowEngine.run` 核心执行路径 | 死循环保护、CONDITION 路由、异常传播、正常 END 输出 | 单元测试（Mock Mapper + 内存节点/边定义） |
| `LlmServiceImpl.resolveAdapter` | Provider/Model 被删除后的降级行为、缓存一致性 | 单元测试 |
| `EncryptionService` 加解密一致性 | 密钥格式化、特殊字符、空值处理，确保加密可逆 | 单元测试 |

#### 🟡 强烈建议补充（P1）

| 测试目标 | 为什么强烈建议 | 测试类型建议 |
|----------|---------------|-------------|
| `DocumentServiceImpl.processDocument` 异常管线 | 模拟 `extractText` / `embedChunks` / `saveChunks` 各环节失败，断言最终状态正确 | 集成测试（H2 + Mock EmbeddingClient） |
| `ProviderServiceImpl.testConnection` 模型同步 | 验证旧模型删除 + 新模型插入的原子性，以及部分失败时的回滚行为 | 集成测试（H2） |
| `McpServerServiceImpl.syncTools` 工具同步 | 验证旧工具清理 + 新工具插入的完整性 | 集成测试（H2） |
| `WorkflowEngine` 各 `NodeExecutor` | `LlmNodeExecutor` 配置解析异常、`ApiCallNodeExecutor` HTTP 超时回退 | 单元测试（Mock 外部依赖） |

#### 🟢 可以暂时跳过（当前阶段 ROI 低）

| 跳过目标 | 理由 |
|----------|------|
| 纯 CRUD Controller（Agent/Provider/MCP/KB 列表与详情） | MyBatis-Plus 生成的标准逻辑，框架层面已验证 |
| 前端 Vue 组件单元测试 | 当前项目无 Vitest/Jest 配置，搭建成本高于收益 |
| Provider 适配器的真实 HTTP 调用 | 依赖外部服务不稳定，应在 Adapter 层用契约测试或 MockServer 代替 |
| 健康检查定时任务 | 属于运维旁路，不影响核心业务流程 |

#### 当前测试分布与缺口

- **已有测试（18 个）**：Provider Controller 集成测试较充分（10 个），Chat 有 3 个，Agent 有 4 个。
- **明显缺口**：`hify-workflow`（0 个）、`hify-knowledge`（0 个）、`hify-mcp`（0 个）。这三个模块恰好是近期新合并的 feature 分支，测试债务最高。
- **建议顺序**：Workflow 引擎单元测试 → Knowledge 切分算法 + 管线集成测试 → MCP 工具同步测试 → Chat 工具调用单元测试。

---

## 单元测试规范

> 基于上文"核心链路"和"风险地图"推导得出。所有规范适用于后端 Java 模块（`hify-chat`, `hify-knowledge`, `hify-workflow`, `hify-provider`, `hify-mcp`, `hify-agent`, `hify-common`）。

### 1. 必须写单测的代码

以下代码如果缺少单元测试，PR 禁止合并。判断标准：**链路越核心、风险越高，测试越不可跳过**。

| 优先级 | 目标代码 | 理由 | 示例类 |
|--------|---------|------|--------|
| **P0** | 含复杂分支判断的 Service 核心方法 | 分支覆盖率直接影响线上稳定性 | `ChatServiceImpl.sendMessage()`<br>`WorkflowEngine.run()`<br>`DocumentServiceImpl.splitChunks()` |
| **P0** | 工具调用编排逻辑 | MCP 两阶段调用、工作流节点路由等编排逻辑错误会直接导致功能不可用 | `ChatServiceImpl.executeToolCalls()`<br>`WorkflowEngine.findNextNode()` |
| **P0** | 安全相关工具类 | 加密/解密、签名、Token 估算出错会导致数据泄露或计费错误 | `EncryptionService`<br>`TokenUtil` |
| **P1** | 数据转换/组装器 | 边界条件多，容易因字段遗漏产生静默错误 | `ChatContextAssembler`<br>`NodeConfigParser` |
| **P1** | 状态机流转 | 文档处理管线状态流转、工作流节点状态流转 | `DocumentServiceImpl.processDocument()`（状态分支）<br>`WorkflowEngine.executeNode()` |
| **P2** | 纯工具类（无外部依赖） | 成本低、收益确定 | `DateUtil`<br>`JsonUtil` |

**豁免规则**：以下情况可以**不写**单元测试，但必须写集成测试或契约测试：
- 仅做 CRUD 透传的 Controller（参数校验除外）
- MyBatis-Plus 自动生成的 Mapper 方法（框架已保证）
- Spring 配置类（`@Configuration`）

### 2. 不写单测、用集成测试替代的场景

Hify 的外部依赖具有**异构性强、状态难 Mock、网络行为复杂**的特点，以下场景优先用集成测试（H2 + Testcontainers / MockServer）：

| 场景 | 外部依赖 | 为什么不适合单测 | 替代方案 |
|------|---------|-----------------|---------|
| Provider 适配器真实 HTTP 调用 | OpenAI / Claude / Ollama API | HTTP 协议细节、SSE 流式行为、状态码差异难以用 Mock 还原 | MockServer / WireMock 集成测试 |
| pgvector 向量检索 | PostgreSQL + pgvector 扩展 | 向量运算和 ivfflat 索引行为是数据库特性，内存数据库无法精确模拟 | Testcontainers（PostgreSQL） |
| MCP Server 工具发现与调用 | 外部 MCP HTTP 端点 | MCP SDK 内部状态机复杂，Mock 容易与实际行为脱节 | 启动本地 Mock MCP Server 做集成测试 |
| 文档处理全流程 | 文件系统 + Embedding API | 文件 IO + 异步线程 + 外部 API 串联，单测割裂后失去意义 | H2 + Mock EmbeddingClient 集成测试 |
| 数据库事务边界 | MySQL / H2 | 事务回滚、悲观锁、唯一索引冲突只能在真实数据库中验证 | `@SpringBootTest` + H2 |

**判定口诀**：
> 如果一个测试需要 Mock **3 个以上**外部依赖才能运行，或者 Mock 后测试只剩"调用了某方法"的断言，**改为集成测试**。

### 3. 测试命名规范

统一采用 **should_[期望结果]_when_[输入条件]** 格式。

```java
// ✅ 正确
@Test
void should_returnEmptyChunks_when_textIsBlank() { }

@Test
void should_routeToFalseBranch_when_conditionEvaluatesToFalse() { }

@Test
void should_throwBizException_when_providerNotFound() { }

// ❌ 错误
@Test
void testSplitChunks() { }           // 语义模糊
@Test
void splitChunksTest() { }            // 无 should/when
@Test
void shouldWork() { }                 // 期望结果不具体
@Test
void should_return_empty_chunks() { } // 缺少 when 条件
```

**特殊情况**：
- 异常测试：`should_throw[异常类型]_when_[条件]`
- 并发测试：`should_[结果]_concurrently_when_[条件]`
- 状态流转：`should_transitionTo[目标状态]_when_[事件]`

### 4. 测试结构：Given-When-Then

每个测试方法内部必须按三段式组织，用空行分隔。

```java
@Test
void should_saveAssistantMessage_when_streamFinishesNormally() {
    // Given
    ChatSession session = new ChatSession();
    session.setId(1L);
    session.setTitle("新会话");

    ChatStreamRequest request = new ChatStreamRequest();
    request.setMessage("Hello");

    StringBuilder contentBuilder = new StringBuilder("Response content");
    AtomicBoolean finished = new AtomicBoolean(false);
    AtomicBoolean cancelled = new AtomicBoolean(false);

    when(persistenceService.saveAssistantMessage(any(), any(), any())).thenReturn(1L);

    // When
    chatService.handleFinish(emitter, session, request, contentBuilder,
            "stop", System.currentTimeMillis(), finished, cancelled);

    // Then
    verify(persistenceService).saveAssistantMessage(eq(1L), eq("Response content"), anyInt());
    verify(persistenceService).updateSessionTitle(eq(1L), eq("Hello"));
    assertThat(finished).isTrue();
}
```

**强制要求**：
- `Given` 段负责**准备输入数据**和**配置 Mock 期望**
- `When` 段**只放被测方法调用**，一行最佳，最多不超过 3 行
- `Then` 段负责**断言结果** + **验证交互**（`verify`），不允许再调用被测对象

### 5. Mock 使用规范

#### 必须 Mock 的场景

| 场景 | 原因 | 示例 |
|------|------|------|
| 外部 HTTP 调用 | 不稳定、慢、有副作用 | `ProviderAdapter`, `EmbeddingClient` |
| 数据库 Mapper（单测中） | 避免启动 Spring 上下文，保持测试快速 | `ChatMessageMapper`, `WorkflowNodeMapper` |
| 跨模块 Service | 隔离被测单元，避免级联失败 | `AgentService`, `KnowledgeRetrievalService` |
| 线程/时间相关 | 保证测试确定性 | `System.currentTimeMillis()`（用 Clock 替代） |

#### 禁止 Mock 的场景

| 场景 | 原因 | 正确处理 |
|------|------|---------|
| 被测类本身 | 失去测试意义 | 直接 new 被测实例 |
| 简单 POJO / DTO | 无行为，Mock 增加复杂度 | `new ChatRequest()` |
| 纯工具类（无外部依赖） | 测试的是真实逻辑，Mock 会隐藏 bug | 直接调用 |

#### Mock 框架选择

- **默认**：Mockito（`@ExtendWith(MockitoExtension.class)`）
- **静态方法**：Mockito `mockStatic`（Java 17+）或 wrapper 模式
- **禁止**：PowerMock（已停止维护，与 Java 17+ 不兼容）

```java
// ✅ 正确：构造器注入 + @InjectMocks
@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {
    @Mock private LlmService llmService;
    @Mock private McpClientService mcpClientService;
    @InjectMocks private ChatServiceImpl chatService;
}

// ❌ 错误：@MockBean（只在集成测试中使用）
@MockBean
private LlmService llmService;
```

### 6. 断言规范（AssertJ）

强制使用 **AssertJ**（`org.assertj.core.api.Assertions`），禁止使用 JUnit 4 的 `Assert` 和 Hamcrest。

#### 基本规则

```java
// ✅ 正确：链式断言、语义明确
assertThat(result.getStatus()).isEqualTo("SUCCESS")
                              .hasSize(7);
assertThat(result.getChunks()).isNotEmpty()
                              .hasSize(3)
                              .extracting(ChunkDTO::getContent)
                              .containsExactly("chunk1", "chunk2", "chunk3");

// ❌ 错误：无意义断言
assertThat(result).isNotNull();   // 除非被测方法可能返回 null，否则是废话
assertTrue(true);                 // 永远通过
assertEquals(expected, actual);   // JUnit 风格，错误信息不如 AssertJ 清晰
```

#### 异常断言

```java
// ✅ 正确：assertThatThrownBy + 链式验证
assertThatThrownBy(() -> workflowEngine.run(1L, "input"))
    .isInstanceOf(BizException.class)
    .hasMessageContaining("缺少 START 节点")
    .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
        .isEqualTo(ErrorCode.PARAM_ERROR));

// ❌ 错误：try-catch + fail()
try {
    workflowEngine.run(1L, "input");
    fail("应该抛出异常");   // 容易遗漏
} catch (BizException e) {
    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_ERROR);
}
```

#### 集合与复杂对象断言

```java
// ✅ 正确：提取字段做语义断言
assertThat(agentList)
    .hasSize(2)
    .extracting(Agent::getName, Agent::getStatus)
    .containsExactly(
        tuple("Agent-A", "active"),
        tuple("Agent-B", "inactive")
    );

// ✅ 正确：自定义条件
assertThat(document.getStatus())
    .satisfies(status -> List.of("PENDING", "PROCESSING", "DONE", "FAILED").contains(status));
```

### 7. 禁止事项

以下写法在代码审查中**一票否决**，必须修正：

| 编号 | 禁止事项 | 反面示例 | 正确做法 |
|------|---------|---------|---------|
| **T1** | 测试方法内调用被测方法超过一次 | 在 `Given` 和 `Then` 中各调一次 | `When` 段只调用一次，结果存局部变量 |
| **T2** | 使用 `Thread.sleep()` 等待异步结果 | `Thread.sleep(100); assertThat(...)` | 用 `CountDownLatch`、`Awaitility` 或同步 mock |
| **T3** | 单测依赖外部真实服务 | 直接调用 OpenAI API | Mock 或移到集成测试 |
| **T4** | 测试之间相互依赖或共享可变状态 | 静态变量累加、测试顺序敏感 | 每个测试独立，`@BeforeEach` 重置状态 |
| **T5** | 无断言或只有 `isNotNull()` | 测完不知道验证了啥 | 至少有一个语义断言（值、状态、交互） |
| **T6** | 使用 `@SpringBootTest` 写纯单元测试 | 启动整个容器测一个工具方法 | 纯单元测试用 `@ExtendWith(MockitoExtension.class)` |
| **T7** | 忽略或吞掉被测方法的异常 | `catch (Exception e) { }` | 用 `assertThatThrownBy` 显式断言异常 |
| **T8** | Mock 验证不精确 | `verify(anyService).anyMethod(any())` | `verify(exactly(1), service).specificMethod(eq(expected))` |
| **T9** | 测试数据魔法值无注释 | `agent.setMaxTokens(4096);` | 魔法值用命名常量或注释说明业务含义 |
| **T10** | 单测与生产代码混放或命名不规范 | 测试类放在 `main/java` 下 | 严格遵循 `src/test/java`，测试类名以 `Test` 结尾 |

### 附：测试金字塔在 Hify 的落地比例

```
      /\
     /  \     E2E（Playwright）—— 5%  （核心对话链路冒烟）
    /____\
   /      \   集成测试 —— 25% （数据库事务、外部 HTTP Mock、异步管线）
  /________\ 
 /          \ 单元测试 —— 70% （Service 分支、工具类、状态机、加密）
/____________\
```

**当前缺口最大的模块**（应优先补单测）：
1. `hify-workflow`：0 个测试 → 先补 `WorkflowEngine` + `NodeExecutor`
2. `hify-knowledge`：0 个测试 → 先补 `splitChunks` + `processDocument` 状态分支
3. `hify-mcp`：0 个测试 → 先补 `syncTools` + `McpClientService`
4. `hify-chat`：3 个测试 → 补工具调用两阶段 + `handleFinish` 并发安全

---

## 单测价值分析清单

> 基于"核心链路清单"和"单元测试规范"，对每个类/方法逐一判定是否值得写单元测试。
> 判定标准：① 是否位于核心链路；② 是否包含不可由框架保证的分支/算法/状态机；③ Mock 成本是否低于集成测试。

### hify-chat

| 类/方法 | 是否值得单测 | 理由 | 最重要的 2-3 个测试场景 |
|---------|-------------|------|----------------------|
| `ChatServiceImpl.sendMessage()` | **是** | 核心链路 1（SSE 对话），含 RAG、工具两阶段调用、工作流路由三大分支 | 1. `should_streamAssistantMessage_when_noToolBound`<br>2. `should_executeToolCallsAndStreamFinalAnswer_when_toolCallsReturned`<br>3. `should_returnWorkflowOutput_when_agentHasWorkflowId` |
| `ChatServiceImpl.handleFinish()` | **是** | 并发风险点，涉及 `AtomicBoolean` CAS、消息持久化、标题更新三事独立事务 | 1. `should_saveMessageAndUpdateTitle_when_firstUserMessage`<br>2. `should_notDuplicateSave_when_finishCalledConcurrently`<br>3. `should_skipSave_when_clientCancelled` |
| `ChatServiceImpl.buildSystemPromptWithRag()` | **是** | 核心链路 1，RAG 注入策略的边界判断（kbId 为空、chunks 为空、有 chunks 拼接） | 1. `should_returnOriginalPrompt_when_kbNotBound`<br>2. `should_appendRagContext_when_chunksRetrieved`<br>3. `should_returnOriginalPrompt_when_chunksEmpty` |
| `ChatServiceImpl.loadToolSchemas()` | **是** | 工具 schema 组装逻辑，含 JSON 转换和 name→serverId 映射 | 1. `should_returnEmptyContext_when_noToolIds`<br>2. `should_assembleOpenAiFunctionSchema_when_toolsBound` |
| `ChatServiceImpl.executeToolCalls()` | **是** | MCP 工具调用编排，含异常捕获（工具失败不阻断对话） | 1. `should_returnToolResult_when_callSucceeds`<br>2. `should_returnErrorMessage_when_toolCallThrowsException` |
| `ChatContextAssembler.assemble()` | **是** | 两种上下文策略（SLIDING_WINDOW / FIXED_TURNS）的切换与预算计算 | 1. `should_selectFixedTurns_when_strategyIsFixedTurns`<br>2. `should_truncateByTokenBudget_when_slidingWindowExceeds`<br>3. `should_includeAllHistory_when_withinBudget` |
| `ChatContextAssembler.selectBySlidingWindow()` | **是** | 纯算法：按 token 预算逆序选取消息，边界复杂 | 1. `should_returnEmptyList_when_budgetTooSmallForAnyMessage`<br>2. `should_stopAtBudgetBoundary_when_accumulatedTokensExceed` |
| `ChatPersistenceService` | **否** | 纯 Mapper CRUD 透传 + 简单对象构造，无分支逻辑 | — |

### hify-knowledge

| 类/方法 | 是否值得单测 | 理由 | 最重要的 2-3 个测试场景 |
|---------|-------------|------|----------------------|
| `DocumentServiceImpl.splitChunks()` | **是** | 核心链路 2，纯文本切分算法，边界极多（段落/句子/字符截断、overlap 回退） | 1. `should_splitByParagraphBoundary_when_textHasParagraphs`<br>2. `should_fallbackToCharTruncation_when_noNaturalBoundary`<br>3. `should_forceAdvance_when_overlapEqualsChunkSize_avoidingInfiniteLoop` |
| `DocumentServiceImpl.findChunkEnd()` | **是** | 切分算法的核心子方法，三级优先级（段落→句子→字符） | 1. `should_preferParagraphBreak_when_withinTokenLimit`<br>2. `should_preferSentenceBreak_when_noParagraph`<br>3. `should_fallbackToCharByChar_when_tokenStillExceeds` |
| `DocumentServiceImpl.estimateCharsForTokens()` | **是** | 二分查找估算 overlap 字符数，有 off-by-one 风险 | 1. `should_returnZero_when_startEqualsEnd`<br>2. `should_findCorrectOverlapBoundary_viaBinarySearch` |
| `DocumentServiceImpl.processDocument()` | **否** | 异步管线串联，需测的是各环节组合而非单环节，且依赖文件系统+Embedding API，用集成测试替代 | — |
| `DocumentServiceImpl.embedChunks()` | **否** | 主要是配置读取 + 外部 HTTP 调用，Mock 3 个以上依赖后测试只剩"调用了 embedBatch"，用集成测试替代 | — |
| `DocumentServiceImpl.extractText()` | **否** | 含 PDFBox 真实文件 IO，Mock 后无意义，用集成测试替代 | — |
| `KnowledgeRetrievalServiceImpl.retrieve()` | **否** | 防御性分支多但均为空值判断，核心业务是 EmbeddingClient + Repository 串联，Mock 成本过高，用集成测试替代 | — |
| `KnowledgeBaseServiceImpl` | **否** | 纯 CRUD + 级联删除，框架已保证 | — |
| `DocumentChunkRepository` | **否** | 纯 SQL 拼接 + JdbcTemplate，行为依赖 pgvector 扩展，用 Testcontainers 集成测试替代 | — |
| `EmbeddingClient` | **否** | 外部 HTTP 调用 + JSON 解析，需 Mock RestTemplate 且响应结构复杂，用 MockServer 集成测试替代 | — |

### hify-workflow

| 类/方法 | 是否值得单测 | 理由 | 最重要的 2-3 个测试场景 |
|---------|-------------|------|----------------------|
| `WorkflowEngine.run()` | **是** | 核心链路 3，图遍历+步数保护+异常处理+落库容错，分支极多 | 1. `should_returnOutput_when_normalLinearWorkflow`<br>2. `should_throwBizException_when_exceedsMaxSteps`<br>3. `should_markFailedAndUpdateRecord_when_nodeExecutionThrows` |
| `WorkflowEngine.findNextNode()` | **是** | 条件路由核心逻辑，CONDITION 节点 vs 普通节点的分支差异大 | 1. `should_routeToTrueBranch_when_conditionMatchesTrue`<br>2. `should_routeToFalseBranch_when_conditionMatchesFalse`<br>3. `should_followUnconditionalEdge_when_nonConditionNode` |
| `WorkflowEngine.executeNode()` | **是** | 节点级落库容错（插入/更新 nodeRun 各有一次 try-catch），需验证记录完整性 | 1. `should_insertNodeRunAndUpdateStatus_when_executionSucceeds`<br>2. `should_updateNodeRunWithError_when_executionFails` |
| `WorkflowEngine.resolveConditionMatchKey()` | **是** | 类型转换逻辑（Boolean/String→String），有静默失败风险 | 1. `should_returnTrueString_when_valueIsBooleanTrue`<br>2. `should_returnLowercaseString_when_valueIsString`<br>3. `should_returnEmptyString_when_configParseFails` |
| `ConditionNodeExecutor.evaluate()` | **是** | 表达式求值：字面量、比较运算、字符串透传三种模式 | 1. `should_returnTrue_when_literalTrue`<br>2. `should_returnBoolean_when_comparisonExpression`<br>3. `should_returnStringAsIs_when_nonComparisonNonBoolean` |
| `NodeConfigParser.parse()` | **是** | JSON 反序列化 + 类型映射，含未知类型和解析失败异常转换 | 1. `should_returnCorrectConfigType_when_knownNodeType`<br>2. `should_throwBizException_when_unknownNodeType`<br>3. `should_throwBizException_when_jsonInvalid` |
| `WorkflowServiceImpl.saveNodes()` | **否** | 主要是对象字段映射 + `nodeConfigParser.parse()` 调用（已覆盖），框架批量插入已保证 | — |
| `WorkflowRunServiceImpl` | **否** | 纯透调 `WorkflowEngine` + 状态转换，无独立逻辑 | — |
| `ExecutionContext` | **否** | 纯数据结构操作（Map get/put），`resolve()` 为简单字符串替换 | — |
| `NodeExecutorRegistry` | **否** | 简单 Map.get()，异常情况（未知类型）由 `WorkflowEngine` 覆盖 | — |
| `LlmNodeExecutor` | **否** | 模板替换 + LLM 调用，依赖外部 HTTP，集成测试替代 | — |
| `ApiCallNodeExecutor` | **否** | HTTP 调用 + RestTemplate，集成测试替代 | — |
| `StartNodeExecutor` / `EndNodeExecutor` | **否** | 无逻辑或仅做上下文写入 | — |

### hify-provider

| 类/方法 | 是否值得单测 | 理由 | 最重要的 2-3 个测试场景 |
|---------|-------------|------|----------------------|
| `ProviderServiceImpl.autoGenerateCode()` | **是** | 纯字符串算法（特殊字符替换、重复检测、时间戳回退） | 1. `should_normalizeSpecialCharsToUnderscore`<br>2. `should_appendTimestamp_when_codeAlreadyExists`<br>3. `should_returnDefault_when_nameIsBlank` |
| `ProviderServiceImpl.maskApiKey()` | **是** | 纯字符串处理，边界条件（空、短于 12 位） | 1. `should_returnOriginal_when_apiKeyTooShort`<br>2. `should_maskMiddleWithAsterisks_when_apiKeyLongEnough` |
| `ProviderServiceImpl.inferAuthType()` | **是** | 纯字符串匹配策略，分支清晰 | 1. `should_returnApiKey_when_anthropic`<br>2. `should_returnNone_when_ollama`<br>3. `should_returnBearer_when_unknown` |
| `ProviderServiceImpl.resolveApiKey()` | **是** | 配置回退逻辑（request.apiKey → authConfig.apiKey → null） | 1. `should_returnRequestApiKey_when_present`<br>2. `should_fallbackToAuthConfig_when_requestKeyBlank`<br>3. `should_returnNull_when_bothBlank` |
| `LlmServiceImpl.resolveAdapter()` | **是** | 核心链路 4，Provider/Model 存在性校验 + 适配器分发 | 1. `should_returnAdapter_when_modelAndProviderValid`<br>2. `should_throwBizException_when_modelNotFound`<br>3. `should_throwBizException_when_providerDeleted` |
| `ProviderAdapterFactory.getAdapter()` | **否** | Map 查找 + 固定 if-else，异常情况已在 `LlmServiceImpl` 覆盖 | — |
| `ProviderServiceImpl` (CRUD) | **否** | 已有 10 个集成测试覆盖，且主要为框架级 CRUD | — |
| `ProviderHealthCheckTask` | **否** | 定时任务，依赖真实 Service + DB，集成测试替代 | — |
| `LlmHttpClient` | **否** | OkHttp 封装，真实网络行为用 MockServer 集成测试替代 | — |
| `OpenAiAdapter` / `AnthropicAdapter` / `OllamaAdapter` | **否** | HTTP 协议细节 + JSON 序列化，Mock 后失去测试价值，用契约测试/集成测试替代 | — |

### hify-mcp

| 类/方法 | 是否值得单测 | 理由 | 最重要的 2-3 个测试场景 |
|---------|-------------|------|----------------------|
| `McpToolServiceImpl.findInvalidToolIds()` | **是** | 复合查询逻辑：存在性校验 + Server 启用状态校验，含空值防御 | 1. `should_returnAllIds_when_toolIdsEmpty`<br>2. `should_returnMissingIds_when_someToolsNotExist`<br>3. `should_returnToolIds_when_serverDisabled` |
| `McpToolServiceImpl.convertToDefinition()` | **是** | JSON 解析异常吞掉（catch 后 set null），有静默数据丢失风险 | 1. `should_parseInputSchema_when_validJson`<br>2. `should_setNull_when_inputSchemaInvalid` |
| `McpServerServiceImpl.syncTools()` | **否** | 先 delete 再 insert 的组合操作，数据一致性需真实 DB 验证，集成测试替代 | — |
| `McpServerServiceImpl` (CRUD) | **否** | 框架级 CRUD，已有连通性测试集成覆盖 | — |
| `McpClientServiceImpl` | **否** | 外部 MCP SDK 调用，行为依赖真实 HTTP 端点，集成测试替代 | — |

### hify-agent

| 类/方法 | 是否值得单测 | 理由 | 最重要的 2-3 个测试场景 |
|---------|-------------|------|----------------------|
| `AgentServiceImpl` (整体) | **否** | 已有 4 个集成测试覆盖；主要为 CRUD + 跨模块校验（走 ProviderService 接口），单测 Mock 后只剩字段赋值断言 | — |
| `AgentBindingServiceImpl` | **否** | 单条 count 查询透传，无分支 | — |
| `AgentAssembler` | **否** | 纯字段映射，无计算逻辑 | — |

### hify-common

| 类/方法 | 是否值得单测 | 理由 | 最重要的 2-3 个测试场景 |
|---------|-------------|------|----------------------|
| `EncryptionService` | **是** | 安全红线，加密可逆性必须 100% 保证 | 1. `should_decryptToOriginal_when_encryptWithDefaultKey`<br>2. `should_handleNullAndBlankGracefully`<br>3. `should_produceDifferentCipher_when_samePlainText`（验证 ECB 弱点存在但行为一致） |
| `TokenUtil.estimateTokens()` | **是** | 纯算法，对话 token 计费与上下文预算都依赖它 | 1. `should_returnZero_when_textIsNullOrEmpty`<br>2. `should_countChineseCharsCorrectly`<br>3. `should_countEnglishWordsCorrectly` |
| `MdcTaskWrapper.wrap()` | **是** | 线程上下文传递，线程池场景的 TraceId 连续性关键 | 1. `should_propagateMdcContext_toChildThread`<br>2. `should_clearMdc_afterTaskCompletion` |
| `GlobalExceptionHandler` | **否** | Spring Web 框架类，行为由 Spring 保证 | — |
| `Result` / `PageResult` | **否** | 纯 POJO，无逻辑 | — |
| `BizException` / `ErrorCode` | **否** | 异常枚举定义，无逻辑 | — |
| `TraceIdFilter` | **否** | Servlet Filter，依赖 Servlet 容器，集成测试替代 | — |
| `MyBatisMetaObjectHandler` | **否** | MyBatis-Plus 回调，框架已保证 | — |
| `CacheConfig` / `RedisConfig` 等 | **否** | Spring 配置类，无业务逻辑 | — |

---

### 汇总：值得单测的类（按优先级排序）

| 优先级 | 类/方法 | 所属模块 | 预计测试数 |
|--------|---------|---------|-----------|
| P0 | `ChatServiceImpl.sendMessage` / `handleFinish` / `buildSystemPromptWithRag` / `loadToolSchemas` / `executeToolCalls` | hify-chat | 8-10 |
| P0 | `WorkflowEngine.run` / `findNextNode` / `executeNode` / `resolveConditionMatchKey` | hify-workflow | 6-8 |
| P0 | `DocumentServiceImpl.splitChunks` / `findChunkEnd` / `estimateCharsForTokens` | hify-knowledge | 5-7 |
| P0 | `EncryptionService` | hify-common | 3-4 |
| P1 | `ChatContextAssembler.assemble` / `selectBySlidingWindow` | hify-chat | 3-4 |
| P1 | `ConditionNodeExecutor.evaluate` | hify-workflow | 3 |
| P1 | `NodeConfigParser.parse` | hify-workflow | 3 |
| P1 | `LlmServiceImpl.resolveAdapter` | hify-provider | 3 |
| P1 | `ProviderServiceImpl.autoGenerateCode` / `maskApiKey` / `inferAuthType` / `resolveApiKey` | hify-provider | 4-5 |
| P1 | `McpToolServiceImpl.findInvalidToolIds` / `convertToDefinition` | hify-mcp | 3-4 |
| P2 | `TokenUtil.estimateTokens` | hify-common | 2-3 |
| P2 | `MdcTaskWrapper.wrap` | hify-common | 2 |

**总计**：约 18 个类 / 45-55 个测试方法。

---

## 单元测试编写规范（执行版）

> 基于上文"核心链路清单""风险集中区域""测试重心建议"推导的落地规范。
> 所有后端 Java 模块（`hify-chat`、`hify-provider`、`hify-workflow`、`hify-knowledge`、`hify-mcp`、`hify-agent`、`hify-common`）均适用。

---

### 1. 必须写单测的代码

以下代码如果缺少单元测试，PR 禁止合并。

| 优先级 | 代码类型 | 判定标准 | 当前必须覆盖的类 |
|--------|---------|---------|----------------|
| **P0** | 核心链路 Service 方法 | 含复杂分支（>=2 个独立逻辑分支）、状态机、并发控制 | `ChatServiceImpl`（`sendMessage`、`handleFinish`、`buildSystemPromptWithRag`、`executeToolCalls`）<br>`WorkflowEngine`（`run`、`findNextNode`、`executeNode`）<br>`LlmServiceImpl`（`resolveAdapter`） |
| **P0** | 安全/加密相关 | 出错即红线，必须 100% 可逆 | `EncryptionService` |
| **P0** | 纯算法/切分逻辑 | 边界条件极多，肉眼难以覆盖 | `DocumentServiceImpl.splitChunks`<br>`ChatContextAssembler.selectBySlidingWindow` |
| **P1** | 数据转换/组装器 | 字段遗漏会产生静默错误 | `ChatContextAssembler`<br>`NodeConfigParser` |
| **P1** | 状态流转判断 | 条件路由、有效性校验 | `ConditionNodeExecutor.evaluate`<br>`McpToolServiceImpl.findInvalidToolIds` |
| **P2** | 纯工具类（无外部依赖） | 成本低、收益确定 | `TokenUtil.estimateTokens`<br>`MdcTaskWrapper.wrap` |

**豁免规则（可跳过单测，但需有集成测试）**：
- 仅做 CRUD 透传的 Controller（参数校验除外）
- MyBatis-Plus 自动生成的 Mapper 方法
- Spring 配置类（`@Configuration`）
- 简单 POJO / DTO（无计算逻辑）

---

### 2. 不写单测、用集成测试替代的场景

Hify 的外部依赖具有**异构性强、状态难 Mock、网络行为复杂**的特点，以下场景优先用集成测试（H2 / Testcontainers / MockServer）：

| 场景 | 外部依赖 | 为什么不适合单测 | 替代方案 |
|------|---------|-----------------|---------|
| Provider 适配器真实 HTTP 调用 | OpenAI / Claude / Ollama API | SSE 流式行为、状态码差异难以 Mock | MockServer / WireMock |
| pgvector 向量检索 | PostgreSQL + pgvector 扩展 | 向量运算是数据库特性，内存库无法模拟 | Testcontainers（PostgreSQL） |
| MCP Server 工具发现与调用 | 外部 MCP HTTP 端点 | MCP SDK 内部状态机复杂，Mock 易脱节 | 本地 Mock MCP Server |
| 文档处理全流程 | 文件系统 + Embedding API | 文件 IO + 异步线程 + 外部 API 串联 | H2 + Mock EmbeddingClient |
| 数据库事务边界 | MySQL / H2 | 事务回滚、唯一索引冲突只能在真实 DB 验证 | `@SpringBootTest` + H2 |

**判定口诀**：
> 如果一个测试需要 Mock **3 个以上**外部依赖才能运行，或者 Mock 后测试只剩"调用了某方法"的断言，**改为集成测试**。

---

### 3. 测试命名规范

统一采用 **`should_[期望结果]_when_[输入条件]`** 格式。

```java
// ✅ 正确
@Test
void should_returnEmptyChunks_when_textIsBlank() { }

@Test
void should_routeToFalseBranch_when_conditionEvaluatesToFalse() { }

@Test
void should_throwBizException_when_providerNotFound() { }

// ❌ 错误
@Test
void testSplitChunks() { }           // 语义模糊
@Test
void splitChunksTest() { }            // 无 should/when
@Test
void shouldWork() { }                 // 期望结果不具体
@Test
void should_return_empty_chunks() { } // 缺少 when 条件
```

**特殊情况**：
- 异常测试：`should_throw[异常类型]_when_[条件]`
- 并发测试：`should_[结果]_concurrently_when_[条件]`
- 状态流转：`should_transitionTo[目标状态]_when_[事件]`

---

### 4. 测试结构：Given-When-Then

每个测试方法内部必须按三段式组织，用空行分隔。

```java
@Test
void should_saveAssistantMessage_when_streamFinishesNormally() {
    // Given
    ChatSession session = new ChatSession();
    session.setId(1L);
    session.setTitle("新会话");

    ChatStreamRequest request = new ChatStreamRequest();
    request.setMessage("Hello");

    StringBuilder contentBuilder = new StringBuilder("Response content");
    AtomicBoolean finished = new AtomicBoolean(false);
    AtomicBoolean cancelled = new AtomicBoolean(false);

    when(persistenceService.saveAssistantMessage(any(), any(), any())).thenReturn(1L);

    // When
    chatService.handleFinish(emitter, session, request, contentBuilder,
            "stop", System.currentTimeMillis(), finished, cancelled);

    // Then
    verify(persistenceService).saveAssistantMessage(eq(1L), eq("Response content"), anyInt());
    verify(persistenceService).updateSessionTitle(eq(1L), eq("Hello"));
    assertThat(finished).isTrue();
}
```

**强制要求**：
- `Given` 段负责**准备输入数据**和**配置 Mock 期望**
- `When` 段**只放被测方法调用**，一行最佳，最多不超过 3 行
- `Then` 段负责**断言结果** + **验证交互**（`verify`），不允许再调用被测对象

---

### 5. Mock 使用规范

#### 必须 Mock 的场景

| 场景 | 原因 | 示例 |
|------|------|------|
| 外部 HTTP 调用 | 不稳定、慢、有副作用 | `ProviderAdapter`, `EmbeddingClient` |
| 数据库 Mapper（单测中） | 避免启动 Spring 上下文，保持测试快速 | `ChatMessageMapper`, `WorkflowNodeMapper` |
| 跨模块 Service | 隔离被测单元，避免级联失败 | `AgentService`, `KnowledgeRetrievalService` |
| 线程/时间相关 | 保证测试确定性 | `System.currentTimeMillis()`（用 `Clock` 替代） |

#### 禁止 Mock 的场景

| 场景 | 原因 | 正确处理 |
|------|------|---------|
| 被测类本身 | 失去测试意义 | 直接 new 被测实例 |
| 简单 POJO / DTO | 无行为，Mock 增加复杂度 | `new ChatRequest()` |
| 纯工具类（无外部依赖） | 测试的是真实逻辑，Mock 会隐藏 bug | 直接调用 |

#### Mock 框架选择

- **默认**：Mockito（`@ExtendWith(MockitoExtension.class)`）
- **静态方法**：Mockito `mockStatic`（Java 17+）或 wrapper 模式
- **禁止**：PowerMock（已停止维护，与 Java 17+ 不兼容）

```java
// ✅ 正确：构造器注入 + @InjectMocks
@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {
    @Mock private LlmService llmService;
    @Mock private McpClientService mcpClientService;
    @InjectMocks private ChatServiceImpl chatService;
}

// ❌ 错误：@MockBean（只在集成测试中使用）
@MockBean
private LlmService llmService;
```

---

### 6. 断言规范（AssertJ）

强制使用 **AssertJ**（`org.assertj.core.api.Assertions`），禁止使用 JUnit 4 的 `Assert` 和 Hamcrest。

#### 基本规则

```java
// ✅ 正确：链式断言、语义明确
assertThat(result.getStatus()).isEqualTo("SUCCESS")
                              .hasSize(7);
assertThat(result.getChunks()).isNotEmpty()
                              .hasSize(3)
                              .extracting(ChunkDTO::getContent)
                              .containsExactly("chunk1", "chunk2", "chunk3");

// ❌ 错误：无意义断言
assertThat(result).isNotNull();   // 除非被测方法可能返回 null，否则是废话
assertTrue(true);                 // 永远通过
assertEquals(expected, actual);   // JUnit 风格，错误信息不如 AssertJ 清晰
```

#### 异常断言

```java
// ✅ 正确：assertThatThrownBy + 链式验证
assertThatThrownBy(() -> workflowEngine.run(1L, "input"))
    .isInstanceOf(BizException.class)
    .hasMessageContaining("缺少 START 节点")
    .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
        .isEqualTo(ErrorCode.PARAM_ERROR));

// ❌ 错误：try-catch + fail()
try {
    workflowEngine.run(1L, "input");
    fail("应该抛出异常");   // 容易遗漏
} catch (BizException e) {
    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_ERROR);
}
```

#### 集合与复杂对象断言

```java
// ✅ 正确：提取字段做语义断言
assertThat(agentList)
    .hasSize(2)
    .extracting(Agent::getName, Agent::getStatus)
    .containsExactly(
        tuple("Agent-A", "active"),
        tuple("Agent-B", "inactive")
    );

// ✅ 正确：自定义条件
assertThat(document.getStatus())
    .satisfies(status -> List.of("PENDING", "PROCESSING", "DONE", "FAILED").contains(status));
```

---

### 7. 禁止事项

以下写法在代码审查中**一票否决**，必须修正：

| 编号 | 禁止事项 | 反面示例 | 正确做法 |
|------|---------|---------|---------|
| **T1** | 测试方法内调用被测方法超过一次 | 在 `Given` 和 `Then` 中各调一次 | `When` 段只调用一次，结果存局部变量 |
| **T2** | 使用 `Thread.sleep()` 等待异步结果 | `Thread.sleep(100); assertThat(...)` | 用 `CountDownLatch`、`Awaitility` 或同步 mock |
| **T3** | 单测依赖外部真实服务 | 直接调用 OpenAI API | Mock 或移到集成测试 |
| **T4** | 测试之间相互依赖或共享可变状态 | 静态变量累加、测试顺序敏感 | 每个测试独立，`@BeforeEach` 重置状态 |
| **T5** | 无断言或只有 `isNotNull()` | 测完不知道验证了啥 | 至少有一个语义断言（值、状态、交互） |
| **T6** | 使用 `@SpringBootTest` 写纯单元测试 | 启动整个容器测一个工具方法 | 纯单元测试用 `@ExtendWith(MockitoExtension.class)` |
| **T7** | 忽略或吞掉被测方法的异常 | `catch (Exception e) { }` | 用 `assertThatThrownBy` 显式断言异常 |
| **T8** | Mock 验证不精确 | `verify(anyService).anyMethod(any())` | `verify(exactly(1), service).specificMethod(eq(expected))` |
| **T9** | 测试数据魔法值无注释 | `agent.setMaxTokens(4096);` | 魔法值用命名常量或注释说明业务含义 |
| **T10** | 单测与生产代码混放或命名不规范 | 测试类放在 `main/java` 下 | 严格遵循 `src/test/java`，测试类名以 `Test` 结尾 |

---

### 附：测试金字塔在 Hify 的落地比例

```
      /\
     /  \     E2E（Playwright）—— 5%  （核心对话链路冒烟）
    /____\
   /      \   集成测试 —— 25% （数据库事务、外部 HTTP Mock、异步管线）
  /________\ 
 /          \ 单元测试 —— 70% （Service 分支、工具类、状态机、加密）
/____________\
```

**当前缺口最大的模块**（应优先补单测）：
1. `hify-workflow`：0 个测试 → 先补 `WorkflowEngine` + `NodeExecutor`
2. `hify-knowledge`：0 个测试 → 先补 `splitChunks` + `processDocument` 状态分支
3. `hify-mcp`：0 个测试 → 先补 `syncTools` + `McpClientService`
4. `hify-chat`：3 个测试 → 补工具调用两阶段 + `handleFinish` 并发安全
