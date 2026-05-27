# Hify 健康检查接口完善 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完善 `/api/v1/health` 接口，检查 MySQL、Redis、pgvector 连通性，返回各组件状态，不破坏现有 `Result<T>` 封装。

**Architecture:** 复用 Spring Boot Actuator 的 `DataSourceHealthIndicator`（MySQL）和 `RedisHealthIndicator`（Redis），自定义 `PgVectorHealthIndicator`（pgvector）。`HealthController` 注入 `HealthEndpoint` 获取聚合结果并包装为 `Result<Map>`。

**Tech Stack:** Spring Boot Actuator, HealthIndicator, JdbcTemplate

---

## File Structure

| File | Responsibility |
|------|----------------|
| `hify-knowledge/src/main/java/com/hify/modules/knowledge/health/PgVectorHealthIndicator.java` | 自定义 HealthIndicator，通过 pgvectorJdbcTemplate 执行 `SELECT 1` |
| `hify-app/src/main/java/com/hify/web/HealthController.java` | 注入 HealthEndpoint，聚合 Actuator 健康结果，包装为 Result<Map> |

---

### Task 1: 创建 PgVectorHealthIndicator

**Files:**
- Create: `hify-knowledge/src/main/java/com/hify/modules/knowledge/health/PgVectorHealthIndicator.java`

- [ ] **Step 1: 创建文件**

```java
package com.hify.modules.knowledge.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * pgvector 健康检查指示器。
 *
 * <p>通过 {@code pgvectorJdbcTemplate} 执行轻量 SQL {@code SELECT 1} 验证连通性。
 * 异常时返回 DOWN 并携带错误详情。
 */
@Component
public class PgVectorHealthIndicator implements HealthIndicator {

    private final JdbcTemplate pgvectorJdbcTemplate;

    public PgVectorHealthIndicator(JdbcTemplate pgvectorJdbcTemplate) {
        this.pgvectorJdbcTemplate = pgvectorJdbcTemplate;
    }

    @Override
    public Health health() {
        try {
            pgvectorJdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Health.up().build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run:
```bash
cd /e/hify && mvn clean compile -pl hify-knowledge -am -B -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add hify-knowledge/src/main/java/com/hify/modules/knowledge/health/PgVectorHealthIndicator.java
git commit -m "feat(knowledge): add PgVectorHealthIndicator"
```

---

### Task 2: 改造 HealthController

**Files:**
- Modify: `hify-app/src/main/java/com/hify/web/HealthController.java`

- [ ] **Step 1: 替换 HealthController**

```java
package com.hify.web;

import com.hify.common.controller.Result;
import org.springframework.boot.actuate.health.Health;
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
        Health health = healthEndpoint.health();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", health.getStatus().getCode());

        Map<String, Object> components = new LinkedHashMap<>();
        health.getComponents().forEach((name, component) -> {
            Map<String, Object> comp = new LinkedHashMap<>();
            comp.put("status", component.getStatus().getCode());
            if (component.getDetails() != null && !component.getDetails().isEmpty()) {
                comp.put("details", component.getDetails());
            }
            components.put(name, comp);
        });
        data.put("components", components);

        return Result.ok(data);
    }
}
```

- [ ] **Step 2: 编译验证**

Run:
```bash
cd /e/hify && mvn clean compile -pl hify-app -am -B -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add hify-app/src/main/java/com/hify/web/HealthController.java
git commit -m "feat(web): enhance health check to aggregate MySQL/Redis/pgvector status"
```

---

### Task 3: 全量编译与验证

- [ ] **Step 1: 全量打包**

Run:
```bash
cd /e/hify && mvn clean package -pl hify-app -am -DskipTests -B -q
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 本地启动验证**

```bash
cd /e/hify/hify-app && java -jar target/hify-app-1.0.0-SNAPSHOT.jar
```

等待启动后：

```bash
curl -s http://localhost:8080/api/v1/health | python3 -m json.tool
```

Expected 输出示例：
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "status": "UP",
        "components": {
            "db": {
                "status": "UP"
            },
            "redis": {
                "status": "UP"
            },
            "pgvector": {
                "status": "UP"
            }
        }
    }
}
```

- [ ] **Step 3: 验证 Actuator 端点**

```bash
curl -s http://localhost:8081/actuator/health | python3 -m json.tool
```

Expected: 包含 `status`、`components.db`、`components.redis`、`components.pgvector`

- [ ] **Step 4: Commit**

```bash
git commit --allow-empty -m "chore: verify health check integration"
```

---

### Task 4: 部署到服务器

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
ssh root@8.136.34.168 "curl -s http://localhost:8080/api/v1/health"
```

Expected: JSON 包含 `status` 和 `components`（db、redis、pgvector）

```bash
ssh root@8.136.34.168 "curl -s http://localhost:8081/actuator/health"
```

Expected: 同样包含 pgvector 组件状态

---

## Spec Coverage Check

| Spec 要求 | 对应 Task |
|-----------|----------|
| 检查 MySQL 连通性 | Task 2（复用 Actuator DataSourceHealthIndicator） |
| 检查 Redis 连通性 | Task 2（复用 Actuator RedisHealthIndicator） |
| 检查 pgvector 连通性 | Task 1 + Task 2（自定义 PgVectorHealthIndicator） |
| 返回各依赖状态和错误信息 | Task 2（遍历 components，含 details） |
| 整体状态：全部 UP 才 UP | Task 2（Actuator HealthEndpoint 自动聚合） |
| 不破坏 Result<T> 封装 | Task 2（返回 Result.ok(data)） |

## Placeholder Scan

- [x] 无 TBD/TODO/implement later
- [x] 无 "Add appropriate error handling"
- [x] 无 "Similar to Task N"
- [x] 所有代码块包含完整内容

## Type Consistency

- [x] `HealthController.health()` 返回 `Result<Map<String, Object>>` — 与 Result 泛型签名一致
- [x] `PgVectorHealthIndicator` 构造函数参数 `JdbcTemplate` — 与现有 Bean 名称一致
