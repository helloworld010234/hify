package com.hify.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 错误码设计：四位数字，按模块分段
 * <ul>
 *   <li>1000-1999：通用</li>
 *   <li>2000-2999：Provider（模型提供商）</li>
 *   <li>3000-3999：Agent</li>
 *   <li>4000-4999：Chat（对话引擎）</li>
 *   <li>5000-5999：MCP</li>
 *   <li>6000-6999：Workflow</li>
 *   <li>7000-7999：Knowledge</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ========== 通用 1000-1999 ==========
    PARAM_ERROR(1000, "参数错误"),
    UNAUTHORIZED(1001, "未授权"),
    FORBIDDEN(1002, "禁止访问"),
    NOT_FOUND(1003, "资源不存在"),
    INTERNAL_ERROR(1004, "系统内部错误"),

    // ========== Provider 2000-2999 ==========
    PROVIDER_NOT_FOUND(2000, "模型提供商不存在"),
    PROVIDER_TYPE_NOT_SUPPORTED(2001, "不支持的提供商类型"),
    PROVIDER_CONNECTION_FAILED(2002, "提供商连接失败"),
    MODEL_CONFIG_NOT_FOUND(2003, "模型配置不存在"),

    // ========== Agent 3000-3999 ==========
    AGENT_NOT_FOUND(3000, "Agent 不存在"),
    AGENT_NAME_EXISTS(3001, "Agent 名称已存在"),

    // ========== Chat 4000-4999 ==========
    CHAT_SESSION_NOT_FOUND(4000, "对话会话不存在"),
    CHAT_MESSAGE_NOT_FOUND(4001, "对话消息不存在"),

    // ========== MCP 5000-5999 ==========
    MCP_SERVER_NOT_FOUND(5000, "MCP Server 不存在"),
    MCP_TOOL_CALL_FAILED(5001, "MCP 工具调用失败"),
    MCP_SERVER_BOUND_BY_AGENT(5002, "该 MCP Server 的工具已被 Agent 绑定，无法删除"),

    // ========== Workflow 6000-6999 ==========
    WORKFLOW_NOT_FOUND(6000, "工作流不存在"),

    // ========== Knowledge 7000-7999 ==========
    KNOWLEDGE_BASE_NOT_FOUND(7000, "知识库不存在"),
    DOCUMENT_NOT_FOUND(7001, "文档不存在");

    private final int code;
    private final String defaultMessage;
}
