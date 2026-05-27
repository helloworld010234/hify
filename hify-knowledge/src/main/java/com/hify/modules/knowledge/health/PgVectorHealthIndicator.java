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
