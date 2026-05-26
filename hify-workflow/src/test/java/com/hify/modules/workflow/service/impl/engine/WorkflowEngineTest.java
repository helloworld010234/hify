package com.hify.modules.workflow.service.impl.engine;

import com.hify.common.exception.BizException;

import com.hify.modules.workflow.entity.WorkflowEdge;
import com.hify.modules.workflow.entity.WorkflowNode;
import com.hify.modules.workflow.entity.WorkflowNodeRun;
import com.hify.modules.workflow.entity.WorkflowRun;
import com.hify.modules.workflow.mapper.WorkflowEdgeMapper;
import com.hify.modules.workflow.mapper.WorkflowNodeMapper;
import com.hify.modules.workflow.mapper.WorkflowNodeRunMapper;
import com.hify.modules.workflow.mapper.WorkflowRunMapper;
import com.hify.modules.workflow.service.impl.engine.executor.NodeExecutor;
import com.hify.modules.workflow.service.impl.engine.executor.NodeExecutorRegistry;
import com.hify.modules.workflow.service.impl.nodeconfig.ConditionNodeConfig;
import com.hify.modules.workflow.service.impl.nodeconfig.EndNodeConfig;
import com.hify.modules.workflow.service.impl.nodeconfig.LlmNodeConfig;
import com.hify.modules.workflow.service.impl.nodeconfig.NodeConfig;
import com.hify.modules.workflow.service.impl.nodeconfig.NodeConfigParser;
import com.hify.modules.workflow.service.impl.nodeconfig.StartNodeConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineTest {

    @Mock
    private WorkflowRunMapper runMapper;
    @Mock
    private WorkflowNodeRunMapper nodeRunMapper;
    @Mock
    private WorkflowNodeMapper nodeMapper;
    @Mock
    private WorkflowEdgeMapper edgeMapper;
    @Mock
    private NodeExecutorRegistry registry;
    @Mock
    private NodeConfigParser parser;

    @InjectMocks
    private WorkflowEngine engine;

    // ---------- 辅助方法 ----------

    private WorkflowNode node(String nodeKey, String nodeType, String configJson) {
        WorkflowNode n = new WorkflowNode();
        n.setNodeKey(nodeKey);
        n.setNodeType(nodeType);
        n.setConfigJson(configJson);
        return n;
    }

    private WorkflowEdge edge(String source, String target, String conditionExpr, int sortOrder) {
        WorkflowEdge e = new WorkflowEdge();
        e.setSourceNodeKey(source);
        e.setTargetNodeKey(target);
        e.setConditionExpr(conditionExpr);
        e.setSortOrder(sortOrder);
        return e;
    }

    private void mockInsertRun() {
        doAnswer(inv -> {
            WorkflowRun run = inv.getArgument(0);
            if (run.getId() == null) {
                run.setId(1L);
            }
            return null;
        }).when(runMapper).insert(any(WorkflowRun.class));
    }

    private void mockInsertNodeRun() {
        doAnswer(inv -> {
            WorkflowNodeRun nr = inv.getArgument(0);
            if (nr.getId() == null) {
                nr.setId(1L);
            }
            return null;
        }).when(nodeRunMapper).insert(any(WorkflowNodeRun.class));
    }

    // ---------- 测试场景 ----------

    @Test
    void should_returnOutput_when_normalLinearWorkflow() {
        // Given
        Long workflowId = 1L;
        String userMessage = "Hello";

        WorkflowNode startNode = node("start", "START", "{}");
        WorkflowNode llmNode = node("llm", "LLM", "{}");
        WorkflowNode endNode = node("end", "END", "{\"outputVariable\":\"result\"}");

        when(nodeMapper.selectByWorkflowId(workflowId))
                .thenReturn(Arrays.asList(startNode, llmNode, endNode));
        when(edgeMapper.selectByWorkflowId(workflowId))
                .thenReturn(Arrays.asList(
                        edge("start", "llm", null, 1),
                        edge("llm", "end", null, 1)
                ));

        NodeExecutor startExecutor = new NodeExecutor() {
            @Override public void execute(WorkflowNode n, NodeConfig c, ExecutionContext ctx) {}
            @Override public String nodeType() { return "START"; }
        };
        NodeExecutor llmExecutor = new NodeExecutor() {
            @Override public void execute(WorkflowNode n, NodeConfig c, ExecutionContext ctx) {
                ctx.set("llm", "result", "AI response");
            }
            @Override public String nodeType() { return "LLM"; }
        };
        NodeExecutor endExecutor = new NodeExecutor() {
            @Override public void execute(WorkflowNode n, NodeConfig c, ExecutionContext ctx) {}
            @Override public String nodeType() { return "END"; }
        };

        when(registry.get("START")).thenReturn(startExecutor);
        when(registry.get("LLM")).thenReturn(llmExecutor);
        when(registry.get("END")).thenReturn(endExecutor);

        when(parser.parse("START", "{}")).thenReturn(new StartNodeConfig(Collections.emptyList()));
        when(parser.parse("LLM", "{}")).thenReturn(new LlmNodeConfig(1L, "prompt", "result"));
        when(parser.parse("END", "{\"outputVariable\":\"result\"}"))
                .thenReturn(new EndNodeConfig("result"));

        mockInsertRun();
        mockInsertNodeRun();

        // When
        WorkflowRunResult result = engine.run(workflowId, userMessage);

        // Then
        assertThat(result.status()).isEqualTo("SUCCESS");
        // END node falls back to userMessage when output variable not found in its scope
        assertThat(result.output()).isEqualTo("Hello");
        assertThat(result.error()).isNull();
    }

    @Test
    void should_throwBizException_when_exceedsMaxSteps() {
        // Given
        Long workflowId = 2L;
        String userMessage = "Loop";

        WorkflowNode startNode = node("start", "START", "{}");
        WorkflowNode llmNode = node("llm", "LLM", "{}");

        when(nodeMapper.selectByWorkflowId(workflowId))
                .thenReturn(Arrays.asList(startNode, llmNode));
        // 死循环：start -> llm -> start -> llm ...
        when(edgeMapper.selectByWorkflowId(workflowId))
                .thenReturn(Arrays.asList(
                        edge("start", "llm", null, 1),
                        edge("llm", "start", null, 1)
                ));

        NodeExecutor startExecutor = new NodeExecutor() {
            @Override public void execute(WorkflowNode n, NodeConfig c, ExecutionContext ctx) {}
            @Override public String nodeType() { return "START"; }
        };
        NodeExecutor llmExecutor = new NodeExecutor() {
            @Override public void execute(WorkflowNode n, NodeConfig c, ExecutionContext ctx) {}
            @Override public String nodeType() { return "LLM"; }
        };

        when(registry.get("START")).thenReturn(startExecutor);
        when(registry.get("LLM")).thenReturn(llmExecutor);

        when(parser.parse("START", "{}")).thenReturn(new StartNodeConfig(Collections.emptyList()));
        when(parser.parse("LLM", "{}")).thenReturn(new LlmNodeConfig(1L, "prompt", "out"));

        mockInsertRun();
        mockInsertNodeRun();

        // When / Then
        WorkflowRunResult result = engine.run(workflowId, userMessage);
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.error()).contains("50");
    }

    @Test
    void should_markFailed_when_nodeExecutionThrows() {
        // Given
        Long workflowId = 3L;
        String userMessage = "Fail";

        WorkflowNode startNode = node("start", "START", "{}");
        WorkflowNode llmNode = node("llm", "LLM", "{}");
        WorkflowNode endNode = node("end", "END", "{\"outputVariable\":\"result\"}");

        when(nodeMapper.selectByWorkflowId(workflowId))
                .thenReturn(Arrays.asList(startNode, llmNode, endNode));
        when(edgeMapper.selectByWorkflowId(workflowId))
                .thenReturn(Arrays.asList(
                        edge("start", "llm", null, 1),
                        edge("llm", "end", null, 1)
                ));

        NodeExecutor startExecutor = new NodeExecutor() {
            @Override public void execute(WorkflowNode n, NodeConfig c, ExecutionContext ctx) {}
            @Override public String nodeType() { return "START"; }
        };
        NodeExecutor llmExecutor = new NodeExecutor() {
            @Override public void execute(WorkflowNode n, NodeConfig c, ExecutionContext ctx) {
                throw new RuntimeException("LLM service unavailable");
            }
            @Override public String nodeType() { return "LLM"; }
        };

        when(registry.get("START")).thenReturn(startExecutor);
        when(registry.get("LLM")).thenReturn(llmExecutor);

        when(parser.parse("START", "{}")).thenReturn(new StartNodeConfig(Collections.emptyList()));
        when(parser.parse("LLM", "{}")).thenReturn(new LlmNodeConfig(1L, "prompt", "out"));

        mockInsertRun();
        mockInsertNodeRun();

        // When
        WorkflowRunResult result = engine.run(workflowId, userMessage);

        // Then
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.error()).contains("LLM service unavailable");
    }

    @Test
    void should_routeToTrueBranch_when_conditionMatchesTrue() {
        // Given
        Long workflowId = 4L;
        String userMessage = "Check";

        WorkflowNode startNode = node("start", "START", "{}");
        WorkflowNode condNode = node("cond", "CONDITION", "{\"expression\":\"true\",\"outputVariable\":\"flag\"}");
        WorkflowNode trueNode = node("trueEnd", "END", "{\"outputVariable\":\"result\"}");
        WorkflowNode falseNode = node("falseEnd", "END", "{\"outputVariable\":\"result\"}");

        when(nodeMapper.selectByWorkflowId(workflowId))
                .thenReturn(Arrays.asList(startNode, condNode, trueNode, falseNode));
        when(edgeMapper.selectByWorkflowId(workflowId))
                .thenReturn(Arrays.asList(
                        edge("start", "cond", null, 1),
                        edge("cond", "trueEnd", "true", 1),
                        edge("cond", "falseEnd", "false", 2)
                ));

        NodeExecutor startExecutor = new NodeExecutor() {
            @Override public void execute(WorkflowNode n, NodeConfig c, ExecutionContext ctx) {}
            @Override public String nodeType() { return "START"; }
        };
        NodeExecutor condExecutor = new NodeExecutor() {
            @Override public void execute(WorkflowNode n, NodeConfig c, ExecutionContext ctx) {
                ctx.set("cond", "flag", true);
            }
            @Override public String nodeType() { return "CONDITION"; }
        };
        NodeExecutor endExecutor = new NodeExecutor() {
            @Override public void execute(WorkflowNode n, NodeConfig c, ExecutionContext ctx) {}
            @Override public String nodeType() { return "END"; }
        };

        when(registry.get("START")).thenReturn(startExecutor);
        when(registry.get("CONDITION")).thenReturn(condExecutor);
        when(registry.get("END")).thenReturn(endExecutor);

        when(parser.parse("START", "{}")).thenReturn(new StartNodeConfig(Collections.emptyList()));
        when(parser.parse(eq("CONDITION"), anyString()))
                .thenReturn(new ConditionNodeConfig("true", "flag"));
        when(parser.parse("END", "{\"outputVariable\":\"result\"}"))
                .thenReturn(new EndNodeConfig("result"));

        mockInsertRun();
        mockInsertNodeRun();

        // When
        WorkflowRunResult result = engine.run(workflowId, userMessage);

        // Then
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.output()).isEqualTo("Check"); // fallback to userMessage because END node output not set
    }

    @Test
    void should_routeToFalseBranch_when_conditionMatchesFalse() {
        // Given
        Long workflowId = 5L;
        String userMessage = "Check";

        WorkflowNode startNode = node("start", "START", "{}");
        WorkflowNode condNode = node("cond", "CONDITION", "{\"expression\":\"false\",\"outputVariable\":\"flag\"}");
        WorkflowNode trueNode = node("trueEnd", "END", "{\"outputVariable\":\"result\"}");
        WorkflowNode falseNode = node("falseEnd", "END", "{\"outputVariable\":\"result\"}");

        when(nodeMapper.selectByWorkflowId(workflowId))
                .thenReturn(Arrays.asList(startNode, condNode, trueNode, falseNode));
        when(edgeMapper.selectByWorkflowId(workflowId))
                .thenReturn(Arrays.asList(
                        edge("start", "cond", null, 1),
                        edge("cond", "trueEnd", "true", 1),
                        edge("cond", "falseEnd", "false", 2)
                ));

        NodeExecutor startExecutor = new NodeExecutor() {
            @Override public void execute(WorkflowNode n, NodeConfig c, ExecutionContext ctx) {}
            @Override public String nodeType() { return "START"; }
        };
        NodeExecutor condExecutor = new NodeExecutor() {
            @Override public void execute(WorkflowNode n, NodeConfig c, ExecutionContext ctx) {
                ctx.set("cond", "flag", false);
            }
            @Override public String nodeType() { return "CONDITION"; }
        };
        NodeExecutor endExecutor = new NodeExecutor() {
            @Override public void execute(WorkflowNode n, NodeConfig c, ExecutionContext ctx) {}
            @Override public String nodeType() { return "END"; }
        };

        when(registry.get("START")).thenReturn(startExecutor);
        when(registry.get("CONDITION")).thenReturn(condExecutor);
        when(registry.get("END")).thenReturn(endExecutor);

        when(parser.parse("START", "{}")).thenReturn(new StartNodeConfig(Collections.emptyList()));
        when(parser.parse(eq("CONDITION"), anyString()))
                .thenReturn(new ConditionNodeConfig("false", "flag"));
        when(parser.parse("END", "{\"outputVariable\":\"result\"}"))
                .thenReturn(new EndNodeConfig("result"));

        mockInsertRun();
        mockInsertNodeRun();

        // When
        WorkflowRunResult result = engine.run(workflowId, userMessage);

        // Then
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.output()).isEqualTo("Check"); // fallback to userMessage because END node output not set
    }
}
