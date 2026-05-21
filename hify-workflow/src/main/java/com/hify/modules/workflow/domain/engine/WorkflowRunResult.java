package com.hify.modules.workflow.domain.engine;

/**
 * 工作流执行结果。
 *
 * @param runId   执行记录 ID
 * @param status  状态：SUCCESS / FAILED
 * @param output  最终输出
 * @param error   错误信息（失败时）
 */
public record WorkflowRunResult(Long runId, String status, String output, String error) {
}
