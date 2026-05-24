package com.hify.common.util;

import org.slf4j.MDC;

import java.util.Map;

/**
 * MDC 上下文传播包装器。
 * <p>
 * 所有提交到线程池的任务必须通过 {@link #wrap(Runnable)} 包装，
 * 确保子线程能继承父线程的 traceId 等 MDC 上下文。
 */
public class MdcTaskWrapper {

    public static Runnable wrap(Runnable task) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            if (context != null) {
                MDC.setContextMap(context);
            }
            try {
                task.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
