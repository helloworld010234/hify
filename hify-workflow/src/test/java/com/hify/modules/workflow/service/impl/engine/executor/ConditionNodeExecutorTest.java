package com.hify.modules.workflow.service.impl.engine.executor;


import com.hify.modules.workflow.entity.WorkflowNode;
import com.hify.modules.workflow.service.impl.engine.ExecutionContext;
import com.hify.modules.workflow.service.impl.nodeconfig.ConditionNodeConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ConditionNodeExecutorTest {

    private final ConditionNodeExecutor executor = new ConditionNodeExecutor();

    @Test
    void should_returnTrue_when_literalTrue() throws Exception {
        // Given
        WorkflowNode node = new WorkflowNode();
        node.setNodeKey("cond");
        node.setNodeType("CONDITION");
        ConditionNodeConfig config = new ConditionNodeConfig("true", "flag");
        ExecutionContext ctx = new ExecutionContext("run-1", "Hello");

        // When
        executor.execute(node, config, ctx);

        // Then
        assertThat(ctx.get("cond", "flag")).isEqualTo(true);
    }

    @Test
    void should_returnBoolean_when_comparisonExpression() throws Exception {
        // Given
        WorkflowNode node = new WorkflowNode();
        node.setNodeKey("cond");
        node.setNodeType("CONDITION");
        ConditionNodeConfig config = new ConditionNodeConfig("{{start.userMessage}} == Hello", "flag");
        ExecutionContext ctx = new ExecutionContext("run-2", "Hello");

        // When
        executor.execute(node, config, ctx);

        // Then
        assertThat(ctx.get("cond", "flag")).isEqualTo(true);
    }

    @Test
    void should_returnStringAsIs_when_nonComparisonNonBoolean() throws Exception {
        // Given
        WorkflowNode node = new WorkflowNode();
        node.setNodeKey("cond");
        node.setNodeType("CONDITION");
        ConditionNodeConfig config = new ConditionNodeConfig("售前", "flag");
        ExecutionContext ctx = new ExecutionContext("run-3", "Hello");

        // When
        executor.execute(node, config, ctx);

        // Then
        assertThat(ctx.get("cond", "flag")).isEqualTo("售前");
    }
}
