package com.hify.modules.provider.task;

import com.hify.common.util.MdcTaskWrapper;
import com.hify.modules.provider.dto.response.ConnectionTestResponse;
import com.hify.modules.provider.entity.Provider;
import com.hify.modules.provider.entity.ProviderHealth;
import com.hify.modules.provider.mapper.ProviderHealthMapper;
import com.hify.modules.provider.mapper.ProviderMapper;
import com.hify.modules.provider.service.impl.ProviderConnectionTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Provider 健康检查定时任务。
 * <p>
 * 每分钟遍历所有 active 供应商，异步执行连通性测试，
 * 更新健康快照到 t_provider_health 表。
 * <p>
 * 2026-05-26 重构：不再调用 {@link com.hify.modules.provider.api.ProviderService#testConnection(Long)}，
 * 改为调用轻量的 {@link ProviderConnectionTestService#test(Provider)}，
 * 避免每次健康检查都触发模型同步、供应商主表更新等副作用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderHealthCheckTask {

    private final ProviderMapper providerMapper;
    private final ProviderConnectionTestService connectionTestService;
    private final ProviderHealthMapper healthMapper;
    private final ThreadPoolExecutor asyncExecutor;

    @Scheduled(fixedDelay = 60_000)
    public void checkAll() {
        List<Provider> activeProviders = providerMapper.selectActiveList();
        if (activeProviders.isEmpty()) {
            return;
        }

        List<CompletableFuture<Void>> futures = activeProviders.stream()
                .map(p -> CompletableFuture.runAsync(
                        MdcTaskWrapper.wrap(() -> checkSingle(p)), asyncExecutor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private void checkSingle(Provider provider) {
        long start = System.currentTimeMillis();
        try {
            ConnectionTestResponse result = connectionTestService.test(provider);
            long latency = System.currentTimeMillis() - start;
            if (result.isSuccess()) {
                recordSuccess(provider.getId(), latency);
            } else {
                recordFailure(provider.getId(), result.getErrorMessage(), latency);
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            recordFailure(provider.getId(), e.getMessage(), latency);
        }
    }

    private void recordSuccess(Long providerId, long latencyMs) {
        ProviderHealth health = healthMapper.selectByProviderId(providerId);
        int consecutiveFailures = 0;
        if (health != null) {
            consecutiveFailures = 0;
        }
        updateHealth(providerId, "healthy", consecutiveFailures, latencyMs, null);
    }

    private void recordFailure(Long providerId, String errorMsg, long latencyMs) {
        ProviderHealth health = healthMapper.selectByProviderId(providerId);
        int consecutiveFailures = 1;
        if (health != null && health.getConsecutiveFailures() != null) {
            consecutiveFailures = health.getConsecutiveFailures() + 1;
        }
        String status = consecutiveFailures >= 3 ? "unhealthy" : "degraded";
        updateHealth(providerId, status, consecutiveFailures, latencyMs, errorMsg);
    }

    private void updateHealth(Long providerId, String status, int consecutiveFailures,
                              long latencyMs, String errorMsg) {
        ProviderHealth existing = healthMapper.selectByProviderId(providerId);
        if (existing == null) {
            ProviderHealth health = new ProviderHealth();
            health.setProviderId(providerId);
            health.setHealthStatus(status);
            health.setConsecutiveFailures(consecutiveFailures);
            health.setLastCheckTime(LocalDateTime.now());
            health.setLastErrorMsg(errorMsg);
            health.setResponseTimeMs(latencyMs);
            healthMapper.insert(health);
        } else {
            healthMapper.updateByProviderId(providerId, status, consecutiveFailures,
                    LocalDateTime.now(), errorMsg, latencyMs);
        }
    }
}
