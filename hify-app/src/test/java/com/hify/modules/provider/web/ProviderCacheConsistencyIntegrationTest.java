package com.hify.modules.provider.web;

import com.hify.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Sql(scripts = {
        "classpath:db/provider-schema-h2.sql",
        "classpath:db/provider-cache-test-data.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ProviderCacheConsistencyIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("P1-2: Provider 详情缓存 — 更新后缓存失效，再次查询返回最新数据")
    void getProviderDetail_cacheShouldBeEvictedAfterUpdate() throws Exception {
        Long providerId = 1000L;

        // 1. 首次查询，建立缓存
        mockMvc.perform(get("/api/v1/providers/{id}", providerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Cached Provider"));

        // 2. 绕过 Service 直接修改数据库
        jdbcTemplate.update("UPDATE t_provider SET name = 'Modified Name' WHERE id = ?", providerId);

        // 3. 再次查询（缓存未失效时，应返回旧值）
        mockMvc.perform(get("/api/v1/providers/{id}", providerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Cached Provider"));

        // 4. 调用更新接口触发 @CacheEvict
        String updateJson = """
                {
                    "name": "Updated Name",
                    "baseUrl": "https://api.updated.com",
                    "authType": "bearer",
                    "status": "active"
                }
                """;
        mockMvc.perform(put("/api/v1/providers/{id}", providerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk());

        // 5. 再次查询，缓存已失效，应返回数据库最新值
        mockMvc.perform(get("/api/v1/providers/{id}", providerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Name"));
    }

    @Test
    @DisplayName("P1-2: Provider 列表缓存 — 创建后缓存失效，列表包含新数据")
    void listProvider_cacheShouldBeEvictedAfterCreate() throws Exception {
        // 1. 首次查询列表
        mockMvc.perform(get("/api/v1/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        // 2. 直接插入新数据（绕过 Service，不触发 @CacheEvict）
        jdbcTemplate.update("""
                INSERT INTO t_provider (code, name, provider_type, base_url, auth_type, status, timeout_ms, max_retries, sort_order, deleted)
                VALUES ('cache-new', 'Cache New', 'ollama', 'http://local', 'none', 'active', 90000, 3, 0, 0)
                """);

        // 3. 再次查询列表（缓存未失效，应仍只有 1 条）
        mockMvc.perform(get("/api/v1/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        // 4. 调用创建接口触发 @CacheEvict(allEntries = true)
        String createJson = """
                {
                    "name": "Created Provider",
                    "providerType": "ollama",
                    "baseUrl": "http://localhost:11434",
                    "status": "active"
                }
                """;
        mockMvc.perform(post("/api/v1/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isOk());

        // 5. 再次查询列表，缓存已失效，应返回 3 条（含预置 + 直接插入 + 新创建）
        mockMvc.perform(get("/api/v1/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));
    }
}
