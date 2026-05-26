# hify-common 基础设施补齐实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将参考项目 `hify-common` 中 15 个缺失的基础设施类严格复刻到当前项目，并补齐缺失的 Maven 依赖，确保编译通过。

**Architecture:** 按功能域分批创建（mapper/config → cache → http → metrics/resilience → threadpool → webmvc/log），参考与当前项目并存，不删除现有类。

**Tech Stack:** Spring Boot 3.3.5, Java 17, MyBatis-Plus, Redis, Micrometer, Resilience4j, OkHttp

---

## 文件清单

### 需创建的 15 个类

| 批次 | 文件路径 |
|------|---------|
| Batch 0 | `hify-common/pom.xml`（修改：补充缺失依赖） |
| Batch 1 | `hify-common/src/main/java/com/hify/common/mapper/BaseMapper.java` |
| Batch 1 | `hify-common/src/main/java/com/hify/common/config/NamedThreadFactory.java` |
| Batch 1 | `hify-common/src/main/java/com/hify/common/config/RedisUtil.java` |
| Batch 2 | `hify-common/src/main/java/com/hify/common/cache/NullValue.java` |
| Batch 2 | `hify-common/src/main/java/com/hify/common/cache/CachePenetrationGuard.java` |
| Batch 3 | `hify-common/src/main/java/com/hify/common/http/LlmApiException.java` |
| Batch 3 | `hify-common/src/main/java/com/hify/common/http/LlmHttpClient.java` |
| Batch 4 | `hify-common/src/main/java/com/hify/common/metrics/HifyMetrics.java` |
| Batch 4 | `hify-common/src/main/java/com/hify/common/resilience/CircuitBreakerService.java` |
| Batch 5 | `hify-common/src/main/java/com/hify/common/config/ThreadPoolConfig.java` |
| Batch 5 | `hify-common/src/main/java/com/hify/common/config/ThreadPoolMetrics.java` |
| Batch 6 | `hify-common/src/main/java/com/hify/common/config/JacksonConfig.java` |
| Batch 6 | `hify-common/src/main/java/com/hify/common/log/RequestLogInterceptor.java` |
| Batch 6 | `hify-common/src/main/java/com/hify/common/config/WebMvcConfig.java` |
| Batch 6 | `hify-common/src/main/java/com/hify/common/log/MdcTaskWrapper.java` |

### 需同步创建的 3 个测试类（参考项目已有）

| 文件路径 |
|---------|
| `hify-common/src/test/java/com/hify/common/cache/CachePenetrationGuardTest.java` |
| `hify-common/src/test/java/com/hify/common/config/JacksonConfigTest.java` |
| `hify-common/src/test/java/com/hify/common/config/ThreadPoolConfigTest.java` |

---

## Task 1: 补齐 hify-common/pom.xml 依赖

**Files:**
- Modify: `hify-common/pom.xml`

**说明：** 当前 `hify-common/pom.xml` 可能缺少 `spring-boot-starter-cache`、`opentelemetry-api`、`micrometer-core` 等依赖。参考项目的这些类依赖以下 artifact：

- `CachePenetrationGuard` → `spring-boot-starter-cache`, `spring-boot-starter-data-redis`
- `ThreadPoolMetrics` / `HifyMetrics` → `micrometer-core`
- `CircuitBreakerService` → `resilience4j-spring-boot3`, `resilience4j-circuitbreaker`, `resilience4j-retry`
- `LlmHttpClient` → `okhttp`
- `RequestLogInterceptor` → `opentelemetry-api`（可选，若当前项目无 OTel 依赖，可改为纯 UUID 实现 fallback）

- [ ] **Step 1: 读取当前 hify-common/pom.xml**

Run: `cat hify-common/pom.xml`

- [ ] **Step 2: 补充缺失依赖**

在 `hify-common/pom.xml` 的 `<dependencies>` 中追加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-core</artifactId>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-circuitbreaker</artifactId>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-retry</artifactId>
</dependency>
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
</dependency>
```

> 注：`opentelemetry-api` 若当前项目未引入，则 `RequestLogInterceptor` 中的 `Span.current()` 逻辑需调整为纯 UUID fallback。先尝试编译，若报 `io.opentelemetry` 包不存在，则修改 `RequestLogInterceptor.resolveTraceId()` 为仅使用 `UUID.randomUUID()`。

- [ ] **Step 3: 验证根 pom 已声明 resilience4j 和 okhttp 版本**

确认根 `pom.xml` 的 `<dependencyManagement>` 中已存在：
- `resilience4j-spring-boot3` `${resilience4j.version}`
- `okhttp` `${okhttp.version}`

若缺少，需先在根 pom 中补版本管理。

---

## Task 2: Batch 1 — mapper + config 基础设施

**Files:**
- Create: `hify-common/src/main/java/com/hify/common/mapper/BaseMapper.java`
- Create: `hify-common/src/main/java/com/hify/common/config/NamedThreadFactory.java`
- Create: `hify-common/src/main/java/com/hify/common/config/RedisUtil.java`

- [ ] **Step 1: 创建 BaseMapper.java**

```java
package com.hify.common.mapper;

public interface BaseMapper<T> extends com.baomidou.mybatisplus.core.mapper.BaseMapper<T> {
}
```

- [ ] **Step 2: 创建 NamedThreadFactory.java**

```java
package com.hify.common.config;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedThreadFactory implements ThreadFactory {

    private final String prefix;
    private final AtomicInteger counter = new AtomicInteger(1);

    public NamedThreadFactory(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, prefix + counter.getAndIncrement());
        t.setDaemon(true);
        return t;
    }
}
```

- [ ] **Step 3: 创建 RedisUtil.java**

```java
package com.hify.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@Profile("!mock")
@RequiredArgsConstructor
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key) {
        return Optional.ofNullable((T) redisTemplate.opsForValue().get(key));
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public void expire(String key, Duration ttl) {
        redisTemplate.expire(key, ttl);
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
```

---

## Task 3: Batch 2 — cache 防穿透

**Files:**
- Create: `hify-common/src/main/java/com/hify/common/cache/NullValue.java`
- Create: `hify-common/src/main/java/com/hify/common/cache/CachePenetrationGuard.java`

- [ ] **Step 1: 创建 NullValue.java**

```java
package com.hify.common.cache;

import java.io.Serializable;

/**
 * 缓存空值标记，用于区分"缓存未命中"和"DB 中不存在该记录"。
 */
public enum NullValue implements Serializable {
    INSTANCE
}
```

- [ ] **Step 2: 创建 CachePenetrationGuard.java**

> 内容从参考项目 `D:\edgedownload\hify-main\hify-main\hify-common\src\main\java\com\hify\common\cache\CachePenetrationGuard.java` 完整复制。
> 文件较长（~182 行），直接复制参考文件内容，确保：
> - package 为 `com.hify.common.cache`
> - 导入 `org.springframework.cache.CacheManager`, `org.springframework.data.redis.core.StringRedisTemplate`

---

## Task 4: Batch 3 — http 客户端 + 异常

**Files:**
- Create: `hify-common/src/main/java/com/hify/common/http/LlmApiException.java`
- Create: `hify-common/src/main/java/com/hify/common/http/LlmHttpClient.java`

- [ ] **Step 1: 创建 LlmApiException.java**

```java
package com.hify.common.http;

import lombok.Getter;

@Getter
public class LlmApiException extends RuntimeException {

    public enum Type {
        TIMEOUT,
        AUTH_FAILED,
        RATE_LIMITED,
        UNKNOWN
    }

    private final Type type;
    private final int statusCode;

    public LlmApiException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
        this.statusCode = -1;
    }

    public LlmApiException(Type type, int statusCode, String message) {
        super(message);
        this.type = type;
        this.statusCode = statusCode;
    }
}
```

- [ ] **Step 2: 创建 LlmHttpClient.java**

> 内容从参考项目 `D:\edgedownload\hify-main\hify-main\hify-common\src\main\java\com\hify\common\http\LlmHttpClient.java` 完整复制。
> 文件较长（~156 行），直接复制参考文件内容，确保 package 为 `com.hify.common.http`。

---

## Task 5: Batch 4 — metrics + resilience

**Files:**
- Create: `hify-common/src/main/java/com/hify/common/metrics/HifyMetrics.java`
- Create: `hify-common/src/main/java/com/hify/common/resilience/CircuitBreakerService.java`

- [ ] **Step 1: 创建 HifyMetrics.java**

> 内容从参考项目 `D:\edgedownload\hify-main\hify-main\hify-common\src\main\java\com\hify\common\metrics\HifyMetrics.java` 完整复制。
> package 为 `com.hify.common.metrics`。

- [ ] **Step 2: 创建 CircuitBreakerService.java**

> 内容从参考项目 `D:\edgedownload\hify-main\hify-main\hify-common\src\main\java\com\hify\common\resilience\CircuitBreakerService.java` 完整复制。
> package 为 `com.hify.common.resilience`。

---

## Task 6: Batch 5 — 线程池配置 + 监控

**Files:**
- Create: `hify-common/src/main/java/com/hify/common/config/ThreadPoolConfig.java`
- Create: `hify-common/src/main/java/com/hify/common/config/ThreadPoolMetrics.java`

- [ ] **Step 1: 创建 ThreadPoolConfig.java**

```java
package com.hify.common.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolConfig {

    @Bean
    @Qualifier("llmExecutor")
    public ThreadPoolExecutor llmExecutor() {
        return new ThreadPoolExecutor(
                10, 50,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new NamedThreadFactory("llm-"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Bean
    @Qualifier("asyncExecutor")
    public ThreadPoolExecutor asyncExecutor() {
        return new ThreadPoolExecutor(
                5, 20,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                new NamedThreadFactory("async-"),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
```

- [ ] **Step 2: 创建 ThreadPoolMetrics.java**

> 内容从参考项目 `D:\edgedownload\hify-main\hify-main\hify-common\src\main\java\com\hify\common\config\ThreadPoolMetrics.java` 完整复制。
> package 为 `com.hify.common.config`。

---

## Task 7: Batch 6 — WebMvc + 日志拦截 + Jackson + MDC

**Files:**
- Create: `hify-common/src/main/java/com/hify/common/config/JacksonConfig.java`
- Create: `hify-common/src/main/java/com/hify/common/log/RequestLogInterceptor.java`
- Create: `hify-common/src/main/java/com/hify/common/config/WebMvcConfig.java`
- Create: `hify-common/src/main/java/com/hify/common/log/MdcTaskWrapper.java`

- [ ] **Step 1: 创建 JacksonConfig.java**

> 内容从参考项目 `D:\edgedownload\hify-main\hify-main\hify-common\src\main\java\com\hify\common\config\JacksonConfig.java` 完整复制。

- [ ] **Step 2: 创建 RequestLogInterceptor.java**

> 内容从参考项目 `D:\edgedownload\hify-main\hify-main\hify-common\src\main\java\com\hify\common\log\RequestLogInterceptor.java` 完整复制。
> **注意**：若编译时 `io.opentelemetry` 包不存在，将 `resolveTraceId()` 方法替换为纯 UUID：

```java
private String resolveTraceId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
}
```

- [ ] **Step 3: 创建 WebMvcConfig.java**

```java
package com.hify.common.config;

import com.hify.common.log.RequestLogInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestLogInterceptor())
                .addPathPatterns("/**");
    }
}
```

- [ ] **Step 4: 创建 MdcTaskWrapper.java**

> 内容从参考项目 `D:\edgedownload\hify-main\hify-main\hify-common\src\main\java\com\hify\common\log\MdcTaskWrapper.java` 复制（或从当前 `com.hify.common.util.MdcTaskWrapper` 迁移到 `com.hify.common.log` 包下）。

---

## Task 8: 补齐参考项目的 3 个测试类

**Files:**
- Create: `hify-common/src/test/java/com/hify/common/cache/CachePenetrationGuardTest.java`
- Create: `hify-common/src/test/java/com/hify/common/config/JacksonConfigTest.java`
- Create: `hify-common/src/test/java/com/hify/common/config/ThreadPoolConfigTest.java`

- [ ] **Step 1: 从参考项目复制 3 个测试类**

从 `D:\edgedownload\hify-main\hify-main\hify-common\src\test\java\com\hify\common\` 对应路径下复制。

---

## Task 9: 编译验证

**Files:** N/A

- [ ] **Step 1: 编译 hify-common**

Run: `mvn compile -pl hify-common -am`

Expected: BUILD SUCCESS

- [ ] **Step 2: 若出现 opentelemetry 包缺失错误**

修改 `RequestLogInterceptor.java`，移除 `io.opentelemetry` 相关 import 和 `resolveTraceId()` 中的 OTel 逻辑，改为纯 UUID。

- [ ] **Step 3: 运行 hify-common 测试**

Run: `mvn test -pl hify-common`

Expected: 参考项目的 3 个测试通过（若存在）。

---

## Self-Review

**Spec coverage check:**
- 15 个类全部有对应 Task ✅
- 3 个测试类有对应 Task ✅
- pom.xml 依赖补充有对应 Task ✅
- 编译验证有对应 Task ✅

**Placeholder scan:**
- 无 TBD/TODO ✅
- `CachePenetrationGuard` / `LlmHttpClient` / `HifyMetrics` / `CircuitBreakerService` / `ThreadPoolMetrics` / `RequestLogInterceptor` / `MdcTaskWrapper` 等长文件标注了"从参考项目完整复制"，实际执行时需读取参考文件内容写入 ✅

**Type consistency:**
- `BaseMapper` 泛型签名一致 ✅
- `LlmApiException.Type` 枚举在 `CircuitBreakerService` 和 `LlmHttpClient` 中使用一致 ✅
- `NamedThreadFactory` 被 `ThreadPoolConfig` 引用，包路径一致 ✅
