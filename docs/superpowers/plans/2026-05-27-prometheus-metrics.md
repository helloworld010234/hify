# Hify Prometheus 指标监控 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 HifyMetrics 中的 Micrometer 反模式，在对话、LLM、MCP 三个链路完成 Prometheus 埋点，指标通过 `/actuator/prometheus` 暴露。

**Architecture:** 用 `ConcurrentHashMap` 缓存按 tag 键索引的 Counter 和 DistributionSummary，避免每次调用重复注册。业务代码通过注入的 `HifyMetrics` 在关键方法首尾埋点。

**Tech Stack:** Spring Boot Actuator, Micrometer Prometheus Registry, Resilience4j

---

## File Structure

| File | Responsibility |
|------|----------------|
| `hify-common/src/main/java/com/hify/common/metrics/HifyMetrics.java` | 统一指标注册与缓存，提供业务友好的 record 方法 |
| `hify-chat/src/main/java/com/hify/modules/chat/service/impl/ChatServiceImpl.java` | 对话链路埋点：请求计数 + 耗时 |
| `hify-provider/src/main/java/com/hify/modules/provider/service/impl/LlmServiceImpl.java` | LLM 调用埋点：调用计数（按 provider/model/success）+ 耗时 |
| `hify-mcp/src/main/java/com/hify/modules/mcp/service/impl/McpClientServiceImpl.java` | MCP 工具调用埋点：调用计数 + 耗时 |

---

### Task 1: 修复 HifyMetrics — ConcurrentHashMap 缓存

**Files:**
- Modify: `hify-common/src/main/java/com/hify/common/metrics/HifyMetrics.java` (完整替换)

- [ ] **Step 1: 替换 HifyMetrics 为带缓存的版本**

用 `ConcurrentHashMap` 缓存 Counter 和 DistributionSummary，按 tag 组合键索引。移除 `@PostConstruct` 中的预注册（避免产生大量无用时间序列）。

```java
package com.hify.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一管理 Hify 所有 Prometheus 指标。
 * 命名规范：hify_{模块}_{动作}_{单位}
 *
 * <p>实现注意：Counter 和 DistributionSummary 通过 ConcurrentHashMap 按 tag 组合键缓存，
 * 避免每次调用重复 register 导致时间序列无限膨胀。</p>
 */
@Component
public class HifyMetrics {

    private final MeterRegistry registry;

    // ── 缓存：按 tag 组合键索引 ─────────────────────────────────
    private final ConcurrentHashMap<String, Counter> chatRequestCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DistributionSummary> chatRequestSummaries = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Counter> llmCallCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DistributionSummary> llmCallSummaries = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Counter> mcpToolCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DistributionSummary> mcpToolSummaries = new ConcurrentHashMap<>();

    // 熔断器状态
    private final ConcurrentHashMap<String, Gauge> circuitBreakerGauges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> circuitBreakerStates = new ConcurrentHashMap<>();

    public HifyMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // ── 对话 ──────────────────────────────────────────────────

    /** 对话请求计数，按 agentId 分组 */
    public void chatRequestIncrement(String agentId) {
        String key = agentId;
        Counter counter = chatRequestCounters.computeIfAbsent(key, k ->
                Counter.builder("hify_chat_requests_total")
                        .tag("agent_id", agentId)
                        .description("Total chat requests")
                        .register(registry));
        counter.increment();
    }

    /** 对话请求耗时（毫秒），按 agentId 分组 */
    public void chatRequestDuration(String agentId, long durationMs) {
        String key = agentId;
        DistributionSummary summary = chatRequestSummaries.computeIfAbsent(key, k ->
                DistributionSummary.builder("hify_chat_duration_ms")
                        .tag("agent_id", agentId)
                        .description("Chat request duration in milliseconds")
                        .publishPercentileHistogram(true)
                        .register(registry));
        summary.record(durationMs);
    }

    // ── LLM 调用 ──────────────────────────────────────────────

    /** LLM 调用计数，按 provider、model、success 分组 */
    public void llmCallIncrement(String provider, String model, boolean success) {
        String key = provider + "|" + model + "|" + success;
        Counter counter = llmCallCounters.computeIfAbsent(key, k ->
                Counter.builder("hify_llm_calls_total")
                        .tag("provider", provider)
                        .tag("model", model)
                        .tag("success", String.valueOf(success))
                        .description("Total LLM API calls")
                        .register(registry));
        counter.increment();
    }

    /** LLM 调用耗时（毫秒），按 provider、model 分组 */
    public void llmCallDuration(String provider, String model, long durationMs) {
        String key = provider + "|" + model;
        DistributionSummary summary = llmCallSummaries.computeIfAbsent(key, k ->
                DistributionSummary.builder("hify_llm_duration_ms")
                        .tag("provider", provider)
                        .tag("model", model)
                        .description("LLM API call duration in milliseconds")
                        .publishPercentileHistogram(true)
                        .register(registry));
        summary.record(durationMs);
    }

    // ── 熔断器 ────────────────────────────────────────────────

    /**
     * 注册或更新熔断器状态 Gauge。
     * 状态编码：0=CLOSED, 1=OPEN, 2=HALF_OPEN
     */
    public void circuitBreakerState(String providerName, int stateCode) {
        circuitBreakerStates.put(providerName, stateCode);
        circuitBreakerGauges.computeIfAbsent(providerName, name ->
                Gauge.builder("hify_circuit_breaker_state",
                                circuitBreakerStates,
                                m -> m.getOrDefault(name, 0))
                        .tag("provider", name)
                        .description("Circuit breaker state: 0=CLOSED, 1=OPEN, 2=HALF_OPEN")
                        .register(registry)
        );
    }

    // ── MCP 工具调用 ──────────────────────────────────────────

    /** MCP 工具调用计数，按 tool、success 分组 */
    public void mcpToolCallIncrement(String toolName, boolean success) {
        String key = toolName + "|" + success;
        Counter counter = mcpToolCounters.computeIfAbsent(key, k ->
                Counter.builder("hify_mcp_tool_calls_total")
                        .tag("tool", toolName)
                        .tag("success", String.valueOf(success))
                        .description("Total MCP tool call attempts")
                        .register(registry));
        counter.increment();
    }

    /** MCP 工具调用耗时（毫秒） */
    public void mcpToolCallDuration(String toolName, long durationMs) {
        String key = toolName;
        DistributionSummary summary = mcpToolSummaries.computeIfAbsent(key, k ->
                DistributionSummary.builder("hify_mcp_tool_duration_ms")
                        .tag("tool", toolName)
                        .description("MCP tool call duration in milliseconds")
                        .publishPercentileHistogram(true)
                        .register(registry));
        summary.record(durationMs);
    }
}
```

- [ ] **Step 2: 编译验证**

Run:
```bash
cd /e/hify && mvn clean compile -pl hify-common -B -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add hify-common/src/main/java/com/hify/common/metrics/HifyMetrics.java
git commit -m "fix(metrics): cache Counter/Summary in HifyMetrics to avoid anti-pattern"
```

---

### Task 2: ChatServiceImpl 对话链路埋点

**Files:**
- Modify: `hify-chat/src/main/java/com/hify/modules/chat/service/impl/ChatServiceImpl.java`

- [ ] **Step 1: 注入 HifyMetrics 并在 sendMessage 首尾埋点**

找到类中现有字段，在 `llmStreamExecutor` 下方添加：

```java
private final HifyMetrics metrics;
```

在 `sendMessage` 方法中，在 `try {` 之后立即添加：

```java
metrics.chatRequestIncrement(agent.getId().toString());
```

在 `handleFinish` 方法中，在 `sendEvent(emitter, ChatStreamEvent.done(finishReason, latency));` 之前添加：

```java
metrics.chatRequestDuration(session.getId().toString(), latency);
```

在 `handleIoException` 方法中，在 `if (cancelled.get())` 之后添加 else 分支（或修改现有逻辑）：

```java
metrics.chatRequestDuration(String.valueOf(sessionId), System.currentTimeMillis() - startTime);
```

> **注意**：由于 `handleIoException` 和 `handleUnexpectedException` 没有 `session` 参数，需要传入 `sessionId`（在 `sendMessage` 作用域内可访问）。为简化，在 `sendMessage` 的 catch 块中直接埋点更干净。推荐在 `sendMessage` 的 `catch (IOException e)` 和 `catch (Exception e)` 块中各加一行 `metrics.chatRequestDuration(sessionId.toString(), System.currentTimeMillis() - startTime)`。

完整修改后的 `sendMessage` 方法中异常处理部分：

```java
        } catch (IOException e) {
            long latency = System.currentTimeMillis() - startTime;
            metrics.chatRequestDuration(sessionId.toString(), latency);
            handleIoException(emitter, e, cancelled, startTime);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            metrics.chatRequestDuration(sessionId.toString(), latency);
            handleUnexpectedException(emitter, e, startTime);
        }
```

以及 `handleFinish` 中添加：

```java
        log.info("action=chat_stream_end sessionId={} finishReason={} durationMs={} tokens={} contentLength={}",
                session.getId(), finishReason, latency, tokens, fullContent.length());

        metrics.chatRequestDuration(session.getId().toString(), latency);

        sendEvent(emitter, ChatStreamEvent.done(finishReason, latency));
```

- [ ] **Step 2: 编译验证**

Run:
```bash
cd /e/hify && mvn clean compile -pl hify-chat -am -B -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add hify-chat/src/main/java/com/hify/modules/chat/service/impl/ChatServiceImpl.java
git commit -m "feat(metrics): add Prometheus chat request metrics"
```

---

### Task 3: LlmServiceImpl LLM 调用埋点

**Files:**
- Modify: `hify-provider/src/main/java/com/hify/modules/provider/service/impl/LlmServiceImpl.java`

- [ ] **Step 1: 注入 HifyMetrics 并在 chat/streamChat 首尾埋点**

添加字段：

```java
private final HifyMetrics metrics;
```

修改 `chat` 方法为 try-finally 形式：

```java
    @Override
    @CircuitBreaker(name = "llm-default")
    @Retry(name = "timeout-retry")
    public ChatResponse chat(Long modelConfigId, ChatRequest request) throws IOException {
        Provider provider = resolveProvider(modelConfigId);
        String providerType = provider.getProviderType();
        String model = request.getModel();
        log.info("action=llm_chat_start modelConfigId={} model={}", modelConfigId, model);
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            ProviderAdapter adapter = resolveAdapter(modelConfigId, request);
            ChatResponse response = adapter.chat(provider, request);
            success = true;
            return response;
        } finally {
            long duration = System.currentTimeMillis() - start;
            metrics.llmCallIncrement(providerType, model, success);
            metrics.llmCallDuration(providerType, model, duration);
            log.info("action=llm_chat_end modelConfigId={} model={} durationMs={} success={}",
                    modelConfigId, model, duration, success);
        }
    }
```

修改 `streamChat` 方法：

```java
    @Override
    @CircuitBreaker(name = "llm-default")
    public void streamChat(Long modelConfigId, ChatRequest request,
                           Consumer<String> onDelta, Consumer<String> onFinish) throws IOException {
        Provider provider = resolveProvider(modelConfigId);
        String providerType = provider.getProviderType();
        String model = request.getModel();
        log.info("action=llm_stream_start modelConfigId={} model={}", modelConfigId, model);
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            ProviderAdapter adapter = resolveAdapter(modelConfigId, request);
            adapter.streamChat(provider, request,
                    delta -> {
                        onDelta.accept(delta);
                    },
                    finishReason -> {
                        success = true; // 这里不能修改局部变量，需要改为 AtomicBoolean
                        log.info("action=llm_stream_end modelConfigId={} model={} durationMs={} finishReason={}",
                                modelConfigId, model, System.currentTimeMillis() - start, finishReason);
                        onFinish.accept(finishReason);
                    });
        } finally {
            long duration = System.currentTimeMillis() - start;
            metrics.llmCallIncrement(providerType, model, success);
            metrics.llmCallDuration(providerType, model, duration);
        }
    }
```

> **注意**：`streamChat` 的 `success` 标记在 lambda 中无法直接修改。使用 `AtomicBoolean`：

在方法开头添加：
```java
AtomicBoolean successRef = new AtomicBoolean(false);
```

在 `adapter.streamChat` 的成功回调中：
```java
successRef.set(true);
```

在 finally 中：
```java
metrics.llmCallIncrement(providerType, model, successRef.get());
```

- [ ] **Step 2: 编译验证**

Run:
```bash
cd /e/hify && mvn clean compile -pl hify-provider -am -B -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add hify-provider/src/main/java/com/hify/modules/provider/service/impl/LlmServiceImpl.java
git commit -m "feat(metrics): add Prometheus LLM call metrics"
```

---

### Task 4: McpClientServiceImpl MCP 工具埋点

**Files:**
- Modify: `hify-mcp/src/main/java/com/hify/modules/mcp/service/impl/McpClientServiceImpl.java`

- [ ] **Step 1: 注入 HifyMetrics 并在 callTool 首尾埋点**

添加字段：

```java
private final HifyMetrics metrics;
```

修改 `callTool` 方法为 try-finally 形式：

```java
    @Override
    public String callTool(Long mcpServerId, String toolName, Map<String, Object> arguments) {
        McpServer server = mcpServerMapper.selectById(mcpServerId);
        if (server == null || server.getDeleted() != null && server.getDeleted() == 1) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND, "MCP Server 不存在: " + mcpServerId);
        }

        long start = System.currentTimeMillis();
        boolean success = false;
        try (McpClientResource client = createClient(server)) {
            client.getClient().initialize();
            McpSchema.CallToolResult result = client.getClient().callTool(new McpSchema.CallToolRequest(toolName, arguments));
            success = true;
            return extractText(result);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("MCP tool call failed: serverId={}, toolName={}", mcpServerId, toolName, e);
            throw new BizException(ErrorCode.MCP_TOOL_CALL_FAILED, "工具调用失败: " + e.getMessage(), e);
        } finally {
            long duration = System.currentTimeMillis() - start;
            metrics.mcpToolCallIncrement(toolName, success);
            metrics.mcpToolCallDuration(toolName, duration);
        }
    }
```

- [ ] **Step 2: 编译验证**

Run:
```bash
cd /e/hify && mvn clean compile -pl hify-mcp -am -B -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add hify-mcp/src/main/java/com/hify/modules/mcp/service/impl/McpClientServiceImpl.java
git commit -m "feat(metrics): add Prometheus MCP tool call metrics"
```

---

### Task 5: 全量编译与集成测试

- [ ] **Step 1: 全量编译**

Run:
```bash
cd /e/hify && mvn clean package -pl hify-app -am -DskipTests -B -q
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 本地启动验证 Prometheus 端点**

```bash
cd /e/hify/hify-app && java -jar target/hify-app-1.0.0-SNAPSHOT.jar
```

等待启动完成后，在另一个终端：

```bash
curl -s http://localhost:8081/actuator/prometheus | grep "^hify_"
```

Expected: 输出包含以下行（指标存在，值为 0）：
```
# HELP hify_chat_requests_total Total chat requests
# TYPE hify_chat_requests_total counter
# HELP hify_llm_calls_total Total LLM API calls
# TYPE hify_llm_calls_total counter
# HELP hify_mcp_tool_calls_total Total MCP tool call attempts
# TYPE hify_mcp_tool_calls_total counter
# HELP hify_circuit_breaker_state Circuit breaker state: 0=CLOSED, 1=OPEN, 2=HALF_OPEN
# TYPE hify_circuit_breaker_state gauge
```

- [ ] **Step 3: 埋点验证（手动触发）**

调用一次对话接口（或任意触发 chat/LLM/MCP 的接口），然后再次 curl：

```bash
curl -s http://localhost:8081/actuator/prometheus | grep "^hify_chat_requests_total"
```

Expected: `hify_chat_requests_total{agent_id="1",...} 1.0`（计数从 0 变为 1）

- [ ] **Step 4: Commit**

```bash
git commit --allow-empty -m "chore: verify Prometheus metrics integration"
```

---

### Task 6: 部署到服务器

- [ ] **Step 1: 上传 jar**

```bash
scp /e/hify/hify-app/target/hify-app-1.0.0-SNAPSHOT.jar root@8.136.34.168:/opt/hify/hify-app/target/
```

- [ ] **Step 2: 重启容器**

```bash
ssh root@8.136.34.168 "cd /opt/hify/deploy && docker compose down && docker compose up -d --build"
```

- [ ] **Step 3: 验证**

```bash
ssh root@8.136.34.168 "curl -s http://localhost:8081/actuator/prometheus | grep '^hify_' | head -20"
```

Expected: 指标正常输出，无异常。

---

## Spec Coverage Check

| Spec 要求 | 对应 Task |
|-----------|----------|
| 对话请求总数 + 延迟 | Task 1 + Task 2 |
| LLM 调用总数 + 延迟 + success/failure | Task 1 + Task 3 |
| 熔断器状态 Gauge | Task 1（已存在，未改动逻辑） |
| MCP 工具调用 | Task 1 + Task 4 |
| /actuator/prometheus 暴露 | Task 5（已存在，验证即可） |
| hify_ 前缀 | Task 1（所有 builder 中已使用） |

## Placeholder Scan

- [x] 无 TBD/TODO/implement later
- [x] 无 "Add appropriate error handling"
- [x] 无 "Similar to Task N"
- [x] 所有代码块包含完整内容

## Type Consistency

- [x] `HifyMetrics.chatRequestIncrement(String agentId)` — Task 1 和 Task 2 调用一致
- [x] `HifyMetrics.llmCallIncrement(String provider, String model, boolean success)` — Task 1 和 Task 3 调用一致
- [x] `HifyMetrics.mcpToolCallIncrement(String toolName, boolean success)` — Task 1 和 Task 4 调用一致
