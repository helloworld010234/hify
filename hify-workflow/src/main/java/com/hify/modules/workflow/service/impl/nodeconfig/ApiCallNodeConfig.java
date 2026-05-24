package com.hify.modules.workflow.service.impl.nodeconfig;

import java.util.Map;

/**
 * HTTP 接口调用节点配置。
 *
 * @param url            请求地址，支持 {{variable}} 占位符
 * @param method         HTTP 方法（GET / POST / PUT / DELETE）
 * @param headers        请求头
 * @param body           请求体模板，支持 {{variable}} 占位符
 * @param outputVariable 输出变量名（将 HTTP 响应写入上下文）
 */
public record ApiCallNodeConfig(String url, String method, Map<String, String> headers,
                                String body, String outputVariable)
    implements NodeConfig {
}
