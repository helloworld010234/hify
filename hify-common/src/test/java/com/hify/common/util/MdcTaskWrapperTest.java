package com.hify.common.util;

import com.hify.common.test.AbstractUnitTest;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MdcTaskWrapper 单元测试
 */
class MdcTaskWrapperTest extends AbstractUnitTest {

    @Test
    void should_propagateMdcContext_toChildThread() throws InterruptedException {
        // Given
        MDC.put("traceId", "test-trace-123");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch latch = new CountDownLatch(1);
        final String[] capturedTraceId = new String[1];

        Runnable task = MdcTaskWrapper.wrap(() -> {
            capturedTraceId[0] = MDC.get("traceId");
            latch.countDown();
        });

        // When
        executor.submit(task);
        boolean completed = latch.await(2, TimeUnit.SECONDS);

        // Then
        assertThat(completed).isTrue();
        assertThat(capturedTraceId[0]).isEqualTo("test-trace-123");

        executor.shutdown();
        MDC.clear();
    }

    @Test
    void should_clearMdc_afterTaskCompletion() throws InterruptedException {
        // Given
        MDC.put("traceId", "test-trace-456");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch latch = new CountDownLatch(1);
        final String[] traceIdAfterTask = new String[1];

        Runnable task = MdcTaskWrapper.wrap(() -> {
            // task body
        });

        // When
        executor.submit(() -> {
            task.run();
            traceIdAfterTask[0] = MDC.get("traceId");
            latch.countDown();
        });
        boolean completed = latch.await(2, TimeUnit.SECONDS);

        // Then
        assertThat(completed).isTrue();
        assertThat(traceIdAfterTask[0]).isNull();

        executor.shutdown();
        MDC.clear();
    }
}
