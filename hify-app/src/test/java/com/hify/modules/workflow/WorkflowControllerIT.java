package com.hify.modules.workflow;

import com.hify.AbstractIntegrationTest;
import com.hify.modules.provider.api.LlmService;
import com.hify.modules.provider.dto.chat.ChatRequest;
import com.hify.modules.provider.dto.chat.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkflowControllerIT extends AbstractIntegrationTest {

    @MockBean
    private LlmService llmService;

    @AfterEach
    void cleanupWorkflowTestData() {
        jdbcTemplate.update("DELETE FROM t_workflow_node_run WHERE workflow_run_id IN (SELECT id FROM t_workflow_run WHERE workflow_id IN (100,110,120))");
        jdbcTemplate.update("DELETE FROM t_workflow_run WHERE workflow_id IN (100,110,120)");
        jdbcTemplate.update("DELETE FROM t_workflow_edge WHERE workflow_id IN (100,110,120)");
        jdbcTemplate.update("DELETE FROM t_workflow_node WHERE workflow_id IN (100,110,120)");
        jdbcTemplate.update("DELETE FROM t_workflow WHERE id IN (100,110,120)");
        jdbcTemplate.update("DELETE FROM t_agent WHERE id IN (100,101)");
        jdbcTemplate.update("DELETE FROM t_model WHERE id IN (100,101)");
        jdbcTemplate.update("DELETE FROM t_provider WHERE id IN (100,101)");
    }

    @Test
    @DisplayName("P0: 线性工作流执行 - START->LLM->END，返回输出并落库")
    @Sql(scripts = "classpath:db/workflow-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_runLinearWorkflow_andReturnOutput_when_validWorkflowId() throws Exception {
        // Given: Mock LLM 返回固定内容
        ChatResponse mockResponse = new ChatResponse();
        mockResponse.setContent("Hello");
        when(llmService.chat(anyLong(), any(ChatRequest.class))).thenReturn(mockResponse);

        // When: 执行工作流
        mockMvc.perform(post("/api/v1/workflows/{id}/run", 100)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userMessage\":\"Test input\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        // Then: 工作流运行记录落库
        Map<String, Object> run = jdbcTemplate.queryForMap(
                "SELECT * FROM t_workflow_run WHERE workflow_id = 100");
        assertThat(run.get("status")).isEqualTo("SUCCESS");

        // Then: 节点运行记录有 3 条
        Integer nodeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_workflow_node_run WHERE workflow_run_id = ?", Integer.class, run.get("id"));
        assertThat(nodeCount).isEqualTo(3);
    }

    @Test
    @DisplayName("P0: 循环工作流超出最大步数 - 标记 FAILED")
    @Sql(scripts = "classpath:db/workflow-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_markFailed_when_exceedsMaxSteps() throws Exception {
        // Given: Mock LLM（循环图会被触发）
        ChatResponse mockResponse = new ChatResponse();
        mockResponse.setContent("Step");
        when(llmService.chat(anyLong(), any(ChatRequest.class))).thenReturn(mockResponse);

        // When: 执行循环工作流
        mockMvc.perform(post("/api/v1/workflows/{id}/run", 110)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userMessage\":\"Loop test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("FAILED"));

        // Then: 工作流运行记录状态为 FAILED
        Map<String, Object> run = jdbcTemplate.queryForMap(
                "SELECT * FROM t_workflow_run WHERE workflow_id = 110");
        assertThat(run.get("status")).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("P0: 工作流节点执行记录完整性 - 每个节点一条记录")
    @Sql(scripts = "classpath:db/workflow-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_insertNodeRunRecords_when_nodeExecutionCompletes() throws Exception {
        // Given: Mock LLM
        ChatResponse mockResponse = new ChatResponse();
        mockResponse.setContent("Result");
        when(llmService.chat(anyLong(), any(ChatRequest.class))).thenReturn(mockResponse);

        // When: 执行工作流
        mockMvc.perform(post("/api/v1/workflows/{id}/run", 100)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userMessage\":\"Node record test\"}"))
                .andExpect(status().isOk());

        // Then: 查询节点运行记录
        Long runId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_workflow_run WHERE workflow_id = 100", Long.class);

        var nodeRuns = jdbcTemplate.queryForList(
                "SELECT node_key, status FROM t_workflow_node_run WHERE workflow_run_id = ? ORDER BY id", runId);

        assertThat(nodeRuns).hasSize(3);
        assertThat(nodeRuns.get(0).get("node_key")).isEqualTo("start");
        assertThat(nodeRuns.get(1).get("node_key")).isEqualTo("llm_1");
        assertThat(nodeRuns.get(2).get("node_key")).isEqualTo("end");
    }

    @Test
    @DisplayName("P1: 保存工作流时节点配置解析正确")
    @Sql(scripts = "classpath:db/workflow-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_parseNodeConfigCorrectly_when_saveWorkflowWithNodes() throws Exception {
        String requestJson = """
                {
                    "name": "Test Save Workflow",
                    "description": "Test desc",
                    "nodes": [
                        {"nodeKey": "start", "type": "START", "name": "开始", "config": {"inputVariables": [{"name": "userMessage", "type": "string", "description": "用户输入", "required": true}]}},
                        {"nodeKey": "llm", "type": "LLM", "name": "LLM", "config": {"modelConfigId": 100, "prompt": "{{start.userMessage}}", "outputVariable": "llmOut"}},
                        {"nodeKey": "end", "type": "END", "name": "结束", "config": {"outputVariable": "llmOut"}}
                    ],
                    "edges": [
                        {"sourceNodeKey": "start", "targetNodeKey": "llm"},
                        {"sourceNodeKey": "llm", "targetNodeKey": "end"}
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // Then: 数据库存在工作流及节点
        Long wfId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_workflow WHERE name = ?", Long.class, "Test Save Workflow");
        assertThat(wfId).isNotNull();

        Integer nodeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_workflow_node WHERE workflow_id = ?", Integer.class, wfId);
        assertThat(nodeCount).isEqualTo(3);
    }

    @Test
    @DisplayName("P1: 保存含未知节点类型时返回参数错误")
    @Sql(scripts = "classpath:db/workflow-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_returnParamError_when_unknownNodeTypeInConfig() throws Exception {
        String requestJson = """
                {
                    "name": "Bad Workflow",
                    "nodes": [
                        {"nodeKey": "start", "type": "UNKNOWN_TYPE", "name": "未知", "config": {}}
                    ],
                    "edges": []
                }
                """;

        mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000));
    }

    @Test
    @DisplayName("P2: 工作流执行时模型不存在返回错误")
    @Sql(scripts = "classpath:db/workflow-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void should_returnError_when_runWithDeletedModelBinding() throws Exception {
        // Given: 删除模型 100（工作流 100 的 LLM 节点使用 modelConfigId=100）
        jdbcTemplate.update("UPDATE t_model SET deleted = 1 WHERE id = 100");

        // When: 执行工作流
        mockMvc.perform(post("/api/v1/workflows/{id}/run", 100)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userMessage\":\"Test with deleted model\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("FAILED"));
    }
}
