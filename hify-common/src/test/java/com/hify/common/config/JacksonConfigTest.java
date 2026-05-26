package com.hify.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jackson LocalDateTime 序列化/反序列化单元测试（纯 JUnit，不依赖 Spring 上下文）
 */
class JacksonConfigTest {

    private static ObjectMapper createConfiguredMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        builder.modules(new JavaTimeModule());
        new JacksonConfig().localDateTimeCustomizer().customize(builder);
        return builder.build();
    }

    private final ObjectMapper objectMapper = createConfiguredMapper();

    @Test
    void localDateTime_shouldSerializeToIsoString() throws Exception {
        LocalDateTime dt = LocalDateTime.of(2024, 5, 25, 19, 0, 0);
        String json = objectMapper.writeValueAsString(dt);
        assertThat(json).isEqualTo("\"2024-05-25T19:00:00\"");
    }

    @Test
    void isoString_shouldDeserializeToLocalDateTime() throws Exception {
        String json = "\"2024-05-25T19:00:00\"";
        LocalDateTime dt = objectMapper.readValue(json, LocalDateTime.class);
        assertThat(dt).isEqualTo(LocalDateTime.of(2024, 5, 25, 19, 0, 0));
    }
}
