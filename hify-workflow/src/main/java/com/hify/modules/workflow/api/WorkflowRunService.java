package com.hify.modules.workflow.api;

/**
 * 工作流执行服务（跨模块 API）。
 * <p>
 * hify-chat 等模块通过此接口触发工作流运行，禁止直接依赖 domain/infra 层。
 */
public interface WorkflowRunService {

    /**
     * 同步执行工作流并返回最终输出文本。
     *
     * @param workflowId  工作流 ID
     * @param userMessage 用户输入消息
     * @return 工作流最终输出（END 节点提取的字符串）
     * @throws com.hify.common.exception.BizException 执行失败时抛出
     */
    String run(Long workflowId, String userMessage);
}
