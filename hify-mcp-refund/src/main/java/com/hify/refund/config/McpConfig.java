package com.hify.refund.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.refund.service.RefundService;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@Configuration
public class McpConfig {

    @Bean
    public WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider() {
        return WebMvcStreamableServerTransportProvider.builder()
                .mcpEndpoint("/mcp")
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> mcpRouterFunction(WebMvcStreamableServerTransportProvider transportProvider) {
        return transportProvider.getRouterFunction();
    }

    @Bean
    public McpSyncServer mcpSyncServer(
            WebMvcStreamableServerTransportProvider transportProvider,
            RefundService refundService,
            ObjectMapper objectMapper) {
        return McpServer.sync(transportProvider)
                .serverInfo("refund-mcp-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .tools(
                    buildTool("check_refund_eligibility",
                        "查询订单退款资格。用户说'我要退款'时，必须先调此工具确认是否符合条件。",
                        schema("order_id","string","订单编号"), (ex, req) -> {
                            Map<String,Object> r = refundService.checkEligibility(arg(req,"order_id"));
                            return toResult(r, objectMapper);
                        }),
                    buildTool("submit_refund",
                        "提交退款申请。仅在 check_refund_eligibility 返回 eligible=true 后才可调用。",
                        schemaWithAmount(), (ex, req) -> {
                            Map<String,Object> r = refundService.submitRefund(
                                arg(req,"order_id"), arg(req,"user_id",""),
                                new BigDecimal(((Number)req.arguments().get("amount")).toString()),
                                arg(req,"reason"));
                            return toResult(r, objectMapper);
                        }),
                    buildTool("get_refund_status",
                        "查询指定订单最新的退款申请状态。用户问'退款到哪了'时调用。",
                        schema("order_id","string","订单编号"), (ex, req) -> {
                            Map<String,Object> r = refundService.getStatus(arg(req,"order_id"));
                            return toResult(r, objectMapper);
                        }),
                    buildTool("cancel_refund",
                        "撤销退款申请。仅在用户明确要求'取消退款'时调用。只有 PENDING 可撤销。",
                        schema("refund_id","string","退款单号"), (ex, req) -> {
                            Map<String,Object> r = refundService.cancelRefund(arg(req,"refund_id"));
                            return toResult(r, objectMapper);
                        })
                )
                .build();
    }

    private McpServerFeatures.SyncToolSpecification buildTool(String name, String desc,
            McpSchema.JsonSchema schema, BiFunction<io.modelcontextprotocol.server.McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> handler) {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder().name(name).description(desc).inputSchema(schema).build())
                .callHandler(handler)
                .build();
    }

    private McpSchema.CallToolResult toResult(Map<String,Object> result, ObjectMapper om) {
        try {
            String json = om.writeValueAsString(result);
            boolean isError = !Boolean.TRUE.equals(result.get("success"));
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(json))).isError(isError).build();
        } catch (Exception e) {
            return errorResult(e.getMessage());
        }
    }

    private McpSchema.CallToolResult errorResult(String msg) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent("{\"error\":\""+msg+"\"}"))).isError(true).build();
    }

    private String arg(McpSchema.CallToolRequest req, String key) {
        Object v = req.arguments().get(key);
        return v == null ? "" : v.toString();
    }

    private String arg(McpSchema.CallToolRequest req, String key, String def) {
        Object v = req.arguments().get(key);
        return v == null ? def : v.toString();
    }

    private McpSchema.JsonSchema schema(String name, String type, String desc) {
        return new McpSchema.JsonSchema(
                "object",
                Map.of(name, prop(type, desc)),
                List.of(name),
                null, null, null
        );
    }

    private McpSchema.JsonSchema schemaWithAmount() {
        return new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "order_id", prop("string", "订单编号"),
                        "user_id", prop("string", "用户ID，可选"),
                        "amount", prop("number", "退款金额"),
                        "reason", prop("string", "退款原因")
                ),
                List.of("order_id", "amount", "reason"),
                null, null, null
        );
    }

    private Map<String, Object> prop(String type, String desc) {
        return Map.of("type", type, "description", desc);
    }
}
