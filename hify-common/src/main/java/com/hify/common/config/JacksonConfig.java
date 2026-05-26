package com.hify.common.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/**
 * 全局 Jackson 序列化配置。
 * <p>
 * 统一将 {@link java.time.LocalDateTime} 序列化为 ISO-8601 字符串 {@code yyyy-MM-dd'T'HH:mm:ss}，
 * 避免 Spring Boot 默认输出数组 {@code [2024,5,25,19,0,0]} 导致前端无法解析。
 * <p>
 * 本配置通过 {@link Jackson2ObjectMapperBuilderCustomizer} 扩展，不声明 ObjectMapper Bean，
 * 因此不会覆盖 Spring Boot 自动配置，也不会影响 RedisConfig 中独立构建的 ObjectMapper。
 */
@Configuration
public class JacksonConfig {

    private static final String PATTERN = "yyyy-MM-dd'T'HH:mm:ss";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(PATTERN);

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeCustomizer() {
        return builder -> {
            builder.serializerByType(java.time.LocalDateTime.class, new LocalDateTimeSerializer(FORMATTER));
            builder.deserializerByType(java.time.LocalDateTime.class, new LocalDateTimeDeserializer(FORMATTER));
        };
    }
}
