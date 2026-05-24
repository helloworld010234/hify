package com.hify.modules.workflow.service.impl.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 节点执行器注册表。
 * <p>
 * Spring 自动注入所有 {@link NodeExecutor} 实现类，按 {@link NodeExecutor#nodeType()} 建立索引。
 */
@Component
public class NodeExecutorRegistry {

    private final Map<String, NodeExecutor> executorMap;

    public NodeExecutorRegistry(List<NodeExecutor> executors) {
        this.executorMap = executors.stream()
                .collect(Collectors.toMap(NodeExecutor::nodeType, e -> e));
    }

    /**
     * 根据节点类型获取对应的执行器。
     *
     * @param type 节点类型 code，如 "LLM"、"CONDITION"
     * @return 对应的执行器实例
     * @throws BizException 未知节点类型
     */
    public NodeExecutor get(String type) {
        NodeExecutor executor = executorMap.get(type);
        if (executor == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "未知节点类型，无对应执行器: " + type);
        }
        return executor;
    }
}
