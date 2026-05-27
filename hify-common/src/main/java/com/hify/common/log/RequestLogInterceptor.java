package com.hify.common.log;

import io.opentelemetry.api.trace.Span;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 统一请求日志拦截器。
 * <p>
 * 职责：
 * <ol>
 *   <li>生成 / 复用 traceId，写入 MDC（供日志 JSON 输出）</li>
 *   <li>响应头回写 {@code X-Trace-Id}，便于客户端追踪</li>
 *   <li>记录请求耗时与状态码（慢请求单独 warn）</li>
 *   <li>异常时记录 error 级别日志</li>
 * </ol>
 * <p>
 * traceId 生成策略（为 OTel 预留扩展）：
 * <ol>
 *   <li>优先取当前 OpenTelemetry Span 的 traceId（接入 SDK 后自动生效）</li>
 *   <li>无有效 Span 时 fallback 到标准 32 位 hex UUID</li>
 * </ol>
 */
@Slf4j
public class RequestLogInterceptor implements HandlerInterceptor {

    public static final String MDC_TRACE_ID  = "traceId";
    public static final String MDC_SESSION_ID = "sessionId";
    public static final String MDC_AGENT_ID   = "agentId";

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String ATTR_START_TIME = "reqStartTime";
    private static final long   SLOW_THRESHOLD_MS = 1000;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = resolveTraceId();
        MDC.put(MDC_TRACE_ID, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        request.setAttribute(ATTR_START_TIME, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        long elapsed = System.currentTimeMillis() - (Long) request.getAttribute(ATTR_START_TIME);
        String method = request.getMethod();
        String path   = request.getRequestURI();
        int    status = response.getStatus();

        if (ex != null) {
            log.error("action=http_request method={} path={} status={} durationMs={} error={}",
                    method, path, status, elapsed, ex.getMessage());
        } else if (elapsed >= SLOW_THRESHOLD_MS) {
            log.warn("action=http_slow method={} path={} status={} durationMs={}",
                    method, path, status, elapsed);
        } else {
            log.info("action=http_request method={} path={} status={} durationMs={}",
                    method, path, status, elapsed);
        }

        MDC.clear();
    }

    /**
     * 优先使用 OTel 当前 Span 的 traceId；无有效 Span 时生成 32 位 hex。
     */
    private String resolveTraceId() {
        Span span = Span.current();
        if (span != null && span.getSpanContext().isValid()) {
            return span.getSpanContext().getTraceId();
        }
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
