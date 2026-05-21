package com.hify.modules.knowledge.infra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * pgvector 数据源配置（PostgreSQL 第二数据源）
 * <p>
 * 设计原则：
 * 1. 主数据源（MySQL）保持 Spring Boot 自动配置，MyBatis-Plus 零感知
 * 2. pgvector 不暴露 DataSource Bean（避免触发 DataSourceAutoConfiguration 退避）
 * 3. 直接创建 JdbcTemplate，所有向量操作通过它执行
 */
@Configuration
public class PgVectorDataSourceConfig {

    @Value("${pgvector.datasource.url}")
    private String url;

    @Value("${pgvector.datasource.username}")
    private String username;

    @Value("${pgvector.datasource.password}")
    private String password;

    @Value("${pgvector.datasource.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    /**
     * pgvector 专用 JdbcTemplate
     * <p>
     * 向量检索、批量插入均通过此模板操作。
     */
    @Bean
    public JdbcTemplate pgvectorJdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return new JdbcTemplate(dataSource);
    }
}
