# hify-common 基础设施补齐设计文档

> 目标：将参考项目 `D:\edgedownload\hify-main\hify-main` 中 `hify-common` 的全部缺失类严格复刻到当前项目 `e:\hify`。
> 策略：严格复刻（完全照搬参考项目的实现和包路径，即使与现有类重复或冲突也覆盖）。
> 实施方式：按功能域批量创建。

---

## 一、范围与清单

当前 `hify-common` 有 19 个类，参考项目有 23 个（含 package-info）。
需补齐 **15 个类**：

| # | 类名 | 参考包路径 | 当前状态 | 冲突说明 |
|---|------|-----------|---------|---------|
| 1 | `CachePenetrationGuard` | `com.hify.common.cache` | 缺失 | 无冲突 |
| 2 | `NullValue` | `com.hify.common.cache` | 缺失 | 无冲突 |
| 3 | `NamedThreadFactory` | `com.hify.common.config` | 缺失 | 无冲突 |
| 4 | `ThreadPoolConfig` | `com.hify.common.config` | 缺失 | **冲突**：当前 `hify-app/AsyncConfig` 已定义同名 Bean `llmExecutor`、`asyncExecutor`；参考的 Bean 将覆盖 |
| 5 | `ThreadPoolMetrics` | `com.hify.common.config` | 缺失 | 无冲突 |
| 6 | `JacksonConfig` | `com.hify.common.config` | 缺失 | 无冲突 |
| 7 | `WebMvcConfig` | `com.hify.common.config` | 缺失 | 无冲突 |
| 8 | `RedisUtil` | `com.hify.common.config` | 缺失 | 无冲突 |
| 9 | `LlmApiException` | `com.hify.common.http` | 缺失 | 无冲突 |
| 10 | `LlmHttpClient` | `com.hify.common.http` | 缺失 | **冲突**：当前 `hify-provider/client/LlmHttpClient.java` 存在同名类（不同包），两者并存 |
| 11 | `RequestLogInterceptor` | `com.hify.common.log` | 缺失 | 无冲突 |
| 12 | `MdcTaskWrapper` | `com.hify.common.log` | 缺失 | **冲突**：当前存在 `com.hify.common.util.MdcTaskWrapper`；参考路径在 `log/` 包下，严格复刻需新建 |
| 13 | `BaseMapper` | `com.hify.common.mapper` | 缺失 | 无冲突（当前各模块 Mapper 直接继承 MP 的 BaseMapper） |
| 14 | `HifyMetrics` | `com.hify.common.metrics` | 缺失 | 无冲突 |
| 15 | `CircuitBreakerService` | `com.hify.common.resilience` | 缺失 | 无冲突 |

---

## 二、按功能域分批计划

### Batch 1：mapper + config 基础设施（无依赖，可独立编译）

| 类 | 文件路径 | 说明 |
|----|---------|------|
| `BaseMapper` | `hify-common/src/main/java/com/hify/common/mapper/BaseMapper.java` | 中间接口，无额外方法 |
| `NamedThreadFactory` | `hify-common/src/main/java/com/hify/common/config/NamedThreadFactory.java` | 线程工厂工具类 |
| `RedisUtil` | `hify-common/src/main/java/com/hify/common/config/RedisUtil.java` | Redis 常用操作封装，`!mock` profile |

### Batch 2：cache 防穿透（依赖 RedisUtil / StringRedisTemplate）

| 类 | 文件路径 | 说明 |
|----|---------|------|
| `NullValue` | `hify-common/src/main/java/com/hify/common/cache/NullValue.java` | 空值标记枚举 |
| `CachePenetrationGuard` | `hify-common/src/main/java/com/hify/common/cache/CachePenetrationGuard.java` | 防穿透/击穿守卫，使用 Spring Cache + Redis 分布式锁 |

### Batch 3：http 客户端 + 异常（独立域）

| 类 | 文件路径 | 说明 |
|----|---------|------|
| `LlmApiException` | `hify-common/src/main/java/com/hify/common/http/LlmApiException.java` | 分类异常枚举：TIMEOUT, AUTH_FAILED, RATE_LIMITED, UNKNOWN |
| `LlmHttpClient` | `hify-common/src/main/java/com/hify/common/http/LlmHttpClient.java` | OkHttp 封装：同步 POST、流式 POST、GET；超时与错误码映射 |

### Batch 4：metrics + resilience（依赖 LlmApiException）

| 类 | 文件路径 | 说明 |
|----|---------|------|
| `HifyMetrics` | `hify-common/src/main/java/com/hify/common/metrics/HifyMetrics.java` | Prometheus 指标统一管理：chat/llm/circuit-breaker/mcp |
| `CircuitBreakerService` | `hify-common/src/main/java/com/hify/common/resilience/CircuitBreakerService.java` | Resilience4j 熔断器：按 providerName 隔离，分层重试策略 |

### Batch 5：线程池配置 + 监控（依赖 NamedThreadFactory）

| 类 | 文件路径 | 说明 |
|----|---------|------|
| `ThreadPoolConfig` | `hify-common/src/main/java/com/hify/common/config/ThreadPoolConfig.java` | `llmExecutor`(10,50,100) + `asyncExecutor`(5,20,200) |
| `ThreadPoolMetrics` | `hify-common/src/main/java/com/hify/common/config/ThreadPoolMetrics.java` | Micrometer Gauge 注册线程池指标：`hify_thread_pool_*` |

### Batch 6：WebMvc + 日志拦截 + MDC（独立域）

| 类 | 文件路径 | 说明 |
|----|---------|------|
| `JacksonConfig` | `hify-common/src/main/java/com/hify/common/config/JacksonConfig.java` | 修复 `LocalDateTime` 数组序列化问题，输出 ISO-8601 字符串 |
| `RequestLogInterceptor` | `hify-common/src/main/java/com/hify/common/log/RequestLogInterceptor.java` | HTTP 请求日志拦截，MDC traceId，慢请求阈值 1000ms |
| `WebMvcConfig` | `hify-common/src/main/java/com/hify/common/config/WebMvcConfig.java` | 注册 `RequestLogInterceptor`，拦截 `/**` |
| `MdcTaskWrapper` | `hify-common/src/main/java/com/hify/common/log/MdcTaskWrapper.java` | 线程池任务 MDC 上下文传递包装器 |

---

## 三、关键冲突与处理策略

### 3.1 ThreadPoolConfig 与 AsyncConfig Bean 冲突

- **参考项目**：`ThreadPoolConfig` 在 `hify-common` 中定义 `llmExecutor`、`asyncExecutor`
- **当前项目**：`hify-app/src/main/java/com/hify/common/config/AsyncConfig.java` 已定义同名 Bean
- **处理**：严格复刻下，新建 `hify-common/ThreadPoolConfig.java`。Spring 同名 Bean 后加载者覆盖先加载者。由于 `hify-common` 作为依赖被 `hify-app` 先加载，`hify-app` 的 `AsyncConfig` 会覆盖 `hify-common` 的 `ThreadPoolConfig`（如果 Bean 名相同）。
- **建议**：若需让参考配置生效，后续需删除或重命名 `AsyncConfig` 中的冲突 Bean。本阶段先按参考项目补齐文件，不改动现有 `AsyncConfig`。

### 3.2 LlmHttpClient 包冲突

- **参考项目**：`com.hify.common.http.LlmHttpClient`
- **当前项目**：`com.hify.modules.provider.client.LlmHttpClient`
- **处理**：包名不同，无编译冲突，两者并存。当前 provider 中的实现可继续使用，参考的 common 版本供其他模块使用。

### 3.3 MdcTaskWrapper 路径差异

- **参考项目**：`com.hify.common.log.MdcTaskWrapper`
- **当前项目**：`com.hify.common.util.MdcTaskWrapper`
- **处理**：严格复刻要求在 `log` 包下新建。当前 `util` 下的版本保留，不删除。

---

## 四、依赖检查

参考项目 `hify-common/pom.xml` 中已存在的依赖，当前项目是否具备：

| 依赖 | 参考用途 | 当前项目状态 |
|------|---------|-------------|
| `spring-boot-starter-cache` | CachePenetrationGuard 使用 `CacheManager` | 需检查 |
| `spring-boot-starter-data-redis` | StringRedisTemplate、RedisTemplate | 已存在 |
| `micrometer-registry-prometheus` | HifyMetrics、ThreadPoolMetrics | 已存在（在 hify-app） |
| `resilience4j-spring-boot3` | CircuitBreakerService | 已存在（在根 pom） |
| `okhttp` | LlmHttpClient | 已存在（在根 pom） |
| `jackson-datatype-jsr310` | JacksonConfig LocalDateTime 序列化 | 已存在（Spring Boot 自带） |
| `opentelemetry-api` | RequestLogInterceptor 的 `Span.current()` | 需检查 |

---

## 五、验收标准

1. 15 个类全部创建在指定的包路径下
2. 代码内容与参考项目一致（严格复刻）
3. `mvn compile -pl hify-common` 编译通过
4. `mvn test -pl hify-common` 若存在测试则通过（参考项目有 3 个测试）
5. 不删除当前项目已有的任何类（如 `util/MdcTaskWrapper`、`provider/client/LlmHttpClient`）
