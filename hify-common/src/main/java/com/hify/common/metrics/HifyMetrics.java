package com.hify.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

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
