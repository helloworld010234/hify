# Hify 健康检查接口设计

## Goal
完善 `/api/v1/health` 接口，检查 MySQL、Redis、pgvector 三个依赖的连通性，返回各组件状态，整体状态遵循 "全部 UP 才 UP，任一 DOWN 则 DOWN" 原则。

## Architecture
复用 Spring Boot Actuator 已有的健康检查基础设施：
1. `DataSourceHealthIndicator` — 自动检查主数据源（MySQL）
2. `RedisHealthIndicator` — 自动检查 Redis
3. 自定义 `PgVectorHealthIndicator` — 检查 pgvector（PostgreSQL）

`HealthController` 注入 `HealthEndpoint`，获取 Actuator 聚合后的健康结果，转换为 `Result<HealthStatus>` 返回。不破坏现有 `Result<T>` 封装。

## Tech Stack
- Spring Boot Actuator (已存在)
- `HealthIndicator` 接口
- 现有 `pgvectorJdbcTemplate` Bean

## Response Format

嵌套在 `Result<T>` 中：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "UP",
    "components": {
      "db": {"status": "UP"},
      "redis": {"status": "UP"},
      "pgvector": {"status": "UP"}
    }
  }
}
```

任一组件 DOWN：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "DOWN",
    "components": {
      "db": {"status": "UP"},
      "redis": {"status": "DOWN", "details": "Connection refused"},
      "pgvector": {"status": "UP"}
    }
  }
}
```

## 组件说明

| 组件名 | 来源 | 检查方式 |
|--------|------|----------|
| `db` | Spring Boot 自动配置 | `DataSourceHealthIndicator` 执行 `SELECT 1` |
| `redis` | Spring Boot 自动配置 | `RedisHealthIndicator` 执行 `PING` |
| `pgvector` | 自定义 `HealthIndicator` | `PgVectorHealthIndicator` 执行 `SELECT 1` |

## PgVectorHealthIndicator

使用现有的 `pgvectorJdbcTemplate` Bean 执行轻量级查询 `SELECT 1`，超时 3 秒。

```java
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
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}
```

## HealthController 改造

注入 `HealthEndpoint`（Actuator 提供的聚合端点），调用 `health()` 获取 `org.springframework.boot.actuate.health.Health`，提取 status 和 components。

```java
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

## 验证

1. 启动后访问 `http://localhost:8080/api/v1/health`，确认返回 JSON 包含 `status` 和 `components`
2. 断开 MySQL，确认 `db` 变为 `DOWN`，整体 `status` 变为 `DOWN`
3. 确认 Docker HEALTHCHECK（调用 `/actuator/health`）也能感知 pgvector 状态

## 文件变更

| File | Action |
|------|--------|
| `hify-knowledge/src/main/java/com/hify/modules/knowledge/health/PgVectorHealthIndicator.java` | 创建 |
| `hify-app/src/main/java/com/hify/web/HealthController.java` | 修改：注入 HealthEndpoint，返回聚合结果 |
