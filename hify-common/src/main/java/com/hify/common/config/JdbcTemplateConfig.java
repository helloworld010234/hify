package com.hify.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * JdbcTemplate 配置
 * <p>
 * 显式声明主 JdbcTemplate，确保 @Autowired JdbcTemplate 注入的是主数据源。
 * 避免 pgvectorJdbcTemplate 抢占默认 Bean 导致事务测试中的连接隔离问题。
 */
@Configuration
public class JdbcTemplateConfig {

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
