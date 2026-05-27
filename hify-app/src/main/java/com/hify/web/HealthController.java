package com.hify.web;

import com.hify.common.controller.Result;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务层健康检查接口。
 *
 * <p>复用 Spring Boot Actuator {@link HealthEndpoint} 的聚合结果，返回包含 MySQL、Redis、pgvector
 * 各组件状态的 {@link Result} 包装响应。不破坏现有 {@code Result<T>} 封装规范。
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final HealthEndpoint healthEndpoint;

    public HealthController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        HealthComponent healthComponent = healthEndpoint.health();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", healthComponent.getStatus().getCode());

        if (healthComponent instanceof CompositeHealth compositeHealth) {
            Map<String, Object> components = new LinkedHashMap<>();
            compositeHealth.getComponents().forEach((name, component) -> {
                Map<String, Object> comp = new LinkedHashMap<>();
                comp.put("status", component.getStatus().getCode());
                if (component instanceof CompositeHealth nested) {
                    comp.put("components", nested.getComponents());
                }
                components.put(name, comp);
            });
            data.put("components", components);
        }

        return Result.ok(data);
    }
}
