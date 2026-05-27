# Hify Prometheus 指标监控设计

## Goal
为 Hify 接入 Prometheus 指标监控，修复现有 HifyMetrics 中的 Micrometer 反模式，并在对话链路、LLM 调用、熔断器、MCP 工具四个关键位置完成埋点。

## Architecture
基于 Spring Boot Actuator + Micrometer Prometheus Registry。指标通过 `/actuator/prometheus`（端口 8081）暴露，由外部 Prometheus 抓取。命名前缀统一为 `hify_`。

当前已存在 `HifyMetrics` 组件定义了全部指标，但存在反模式（每次调用重新 register）。本次修复缓存机制，并在业务代码中接入埋点。

## Tech Stack
- Spring Boot Actuator (已存在)
- Micrometer Prometheus Registry (已存在)
- Resilience4j Micrometer 原生集成 (自动暴露)

## Metrics Catalog

### 1. 对话请求 (Chat)
| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `hify_chat_requests_total` | Counter | `agent_id` | 对话请求总数 |
| `hify_chat_duration_ms` | DistributionSummary | `agent_id` | 对话请求耗时（含 P99 histogram bucket） |

**埋点位置**: `ChatServiceImpl.sendMessage()` — 进入时 increment counter，完成时 record duration。

### 2. LLM 调用
| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `hify_llm_calls_total` | Counter | `provider`, `model`, `success` | LLM API 调用总数 |
| `hify_llm_duration_ms` | DistributionSummary | `provider`, `model` | LLM API 调用耗时 |

**埋点位置**: `LlmServiceImpl.chat()` / `streamChat()` — 进入时 start timer，成功/异常结束时 record counter + duration。

### 3. 熔断器状态
| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `hify_circuit_breaker_state` | Gauge | `provider` | 0=CLOSED, 1=OPEN, 2=HALF_OPEN |

**状态**: 已在 `CircuitBreakerService` 中通过 `HifyMetrics.circuitBreakerState()` 接入，事件监听器会在状态转换时自动更新 Gauge。

### 4. MCP 工具调用
| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `hify_mcp_tool_calls_total` | Counter | `tool`, `success` | MCP 工具调用总数 |
| `hify_mcp_tool_duration_ms` | DistributionSummary | `tool` | MCP 工具调用耗时 |

**埋点位置**: `McpClientServiceImpl.callTool()` — 进入时 start timer，成功/异常结束时 record。

### 5. Resilience4j 原生指标（自动，不额外埋点）
Resilience4j spring-boot3 starter 会自动向 Micrometer 注册以下前缀指标：
- `resilience4j_circuitbreaker_calls` (by `name`, `kind`, `outcome`)
- `resilience4j_circuitbreaker_state` (by `name`, `state`)
- `resilience4j_retry_calls` (by `name`, `outcome`)

作为补充保留，与 `hify_circuit_breaker_state` 不冲突。

## HifyMetrics 缓存修复

当前问题：`Counter.builder(...).register(registry).increment()` 每次调用都生成新的 Meter，导致时间序列无限膨胀。

修复方案：用 `ConcurrentHashMap<String, Counter>` 和 `ConcurrentHashMap<String, DistributionSummary>` 按 tag 组合键缓存。

```java
// 示例：对话请求 Counter 缓存
private final ConcurrentHashMap<String, Counter> chatRequestCounters = new ConcurrentHashMap<>();

public void chatRequestIncrement(String agentId) {
    String key = "agent_id=" + agentId;
    Counter counter = chatRequestCounters.computeIfAbsent(key, k ->
        Counter.builder("hify_chat_requests_total")
               .tag("agent_id", agentId)
               .description("Total chat requests")
               .register(registry));
    counter.increment();
}
```

LLM、MCP 同理，分别用 `provider={}&model={}` 和 `tool={}` 作为缓存键。

## 埋点实现细节

### ChatServiceImpl
- `sendMessage()` 方法开头：`metrics.chatRequestIncrement(agentId)`
- `handleFinish()` / `handleIoException()` / `handleUnexpectedException()` 方法中记录 duration
- 使用 `System.currentTimeMillis()` 计算耗时（与现有日志保持一致）

### LlmServiceImpl
- `chat()` 方法 try-finally 包裹：`metrics.llmCallIncrement(provider, model, success)` + `metrics.llmCallDuration(provider, model, duration)`
- `streamChat()` 方法在成功回调和异常 catch 中分别记录
- provider 从 `resolveProvider()` 的 `provider.getProviderType()` 获取
- model 从 `request.getModel()` 获取

### McpClientServiceImpl
- `callTool()` 方法 try-finally 包裹：`metrics.mcpToolCallIncrement(toolName, success)` + `metrics.mcpToolCallDuration(toolName, duration)`

## 测试与验证

1. 启动应用后访问 `http://localhost:8081/actuator/prometheus`
2. 确认以下指标出现（初始值为 0 或占位 tag）：
   - `hify_chat_requests_total`
   - `hify_llm_calls_total`
   - `hify_mcp_tool_calls_total`
   - `hify_circuit_breaker_state`
3. 发起一次对话请求，确认各 tag 正确（agent_id、provider、model、tool）
4. 触发一次异常（如断开 LLM 服务），确认 `success=false` 计数增加

## 文件变更清单

| File | Action |
|------|--------|
| `hify-common/src/main/java/com/hify/common/metrics/HifyMetrics.java` | 修改：修复反模式，加 ConcurrentHashMap 缓存 |
| `hify-chat/src/main/java/com/hify/modules/chat/service/impl/ChatServiceImpl.java` | 修改：接入 chat 埋点 |
| `hify-provider/src/main/java/com/hify/modules/provider/service/impl/LlmServiceImpl.java` | 修改：接入 LLM 埋点 |
| `hify-mcp/src/main/java/com/hify/modules/mcp/service/impl/McpClientServiceImpl.java` | 修改：接入 MCP 埋点 |
