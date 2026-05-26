package com.hify.modules.provider.service.adapter.impl;

import com.hify.modules.provider.dto.chat.ChatMessage;
import com.hify.modules.provider.dto.chat.ChatRequest;
import com.hify.modules.provider.dto.chat.ChatResponse;
import com.hify.modules.provider.dto.chat.Usage;
import com.hify.modules.provider.dto.response.ConnectionTestResponse;
import com.hify.modules.provider.entity.Provider;
import com.hify.modules.provider.service.adapter.ProviderAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * mock profile 下替换所有真实 LLM 调用，直接返回假数据。
 * <p>
 * 能感知 system prompt 里的 RAG 参考资料，模拟引用文档回答；
 * 支持 tools 调用的 mock 响应。
 */
@Slf4j
@Component
@Profile("mock")
public class MockProviderAdapter implements ProviderAdapter {

    @Override
    public ConnectionTestResponse testConnection(Provider provider) {
        return ConnectionTestResponse.success(42, 10, List.of(
                new ConnectionTestResponse.ModelInfo("mock-model-1", "Mock Model 1"),
                new ConnectionTestResponse.ModelInfo("mock-model-2", "Mock Model 2")
        ));
    }

    @Override
    public List<ConnectionTestResponse.ModelInfo> listModels(Provider provider) throws IOException {
        return List.of(
                new ConnectionTestResponse.ModelInfo("mock-model-1", "Mock Model 1"),
                new ConnectionTestResponse.ModelInfo("mock-model-2", "Mock Model 2")
        );
    }

    @Override
    public ChatResponse chat(Provider provider, ChatRequest request) throws IOException {
        return doChat(request);
    }

    @Override
    public void streamChat(Provider provider, ChatRequest request,
                           Consumer<String> onDelta, Consumer<String> onFinish) throws IOException {
        ChatResponse response = doChat(request);
        String content = response.getContent();
        if (content != null && !content.isEmpty()) {
            for (String ch : content.split("")) {
                onDelta.accept(ch);
                try {
                    Thread.sleep(18);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        onFinish.accept(response.getFinishReason() != null ? response.getFinishReason() : "stop");
    }

    // ── 内部工具 ──────────────────────────────────────────────────

    private ChatResponse doChat(ChatRequest request) {
        // 有 tools 且用户问题匹配 → 模拟 tool_calls
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            String userMsg = lastUserMessage(request);
            String matchedTool = matchTool(request.getTools(), userMsg);
            if (matchedTool != null) {
                ChatResponse resp = new ChatResponse();
                resp.setFinishReason("tool_calls");
                resp.setContent(null);
                resp.setToolCalls(List.of(buildToolCall(matchedTool, userMsg)));
                return resp;
            }
        }

        String reply = buildReply(request);
        ChatResponse resp = new ChatResponse();
        resp.setContent(reply);
        resp.setFinishReason("stop");
        Usage usage = new Usage();
        usage.setTotalTokens(reply.length() / 2);
        resp.setUsage(usage);
        return resp;
    }

    private Map<String, Object> buildToolCall(String toolName, String userMsg) {
        String orderId = userMsg.replaceAll("[^0-9]", "");
        if (orderId.isBlank()) orderId = "20240501001";
        Map<String, Object> toolCall = new HashMap<>();
        toolCall.put("id", "call_mock_001");
        toolCall.put("type", "function");
        Map<String, Object> function = new HashMap<>();
        function.put("name", toolName);
        function.put("arguments", String.format("{\"orderId\":\"%s\",\"userId\":\"mock-user\"}", orderId));
        toolCall.put("function", function);
        return toolCall;
    }

    private String buildReply(ChatRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return "您好，请问有什么可以帮助您的？";
        }

        String systemContent = request.getMessages().stream()
                .filter(m -> "system".equals(m.getRole()))
                .map(ChatMessage::getContent)
                .findFirst()
                .orElse("");

        String userContent = request.getMessages().stream()
                .filter(m -> "user".equals(m.getRole()))
                .reduce((a, b) -> b)
                .map(ChatMessage::getContent)
                .orElse("");

        String toolResult = request.getMessages().stream()
                .filter(m -> "tool".equals(m.getRole()))
                .reduce((a, b) -> b)
                .map(ChatMessage::getContent)
                .orElse(null);
        if (toolResult != null) {
            return buildToolReply(toolResult, userContent);
        }

        if (systemContent.contains("【参考资料】")) {
            StringBuilder answer = new StringBuilder();
            List<String> refs = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                String ref = extractRef(systemContent, i);
                if (ref == null || ref.isBlank()) break;
                refs.add(ref);
            }
            if (!refs.isEmpty()) {
                answer.append("根据我们的相关资料，为您解答如下：\n\n");
                for (String ref : refs) {
                    answer.append(ref.trim()).append("\n\n");
                }
                answer.append("如有其他疑问，欢迎继续提问。");
                return answer.toString().trim();
            }
            return "根据现有资料，暂未找到与您问题直接相关的信息。建议您联系人工客服进一步确认，感谢您的理解。";
        }

        return String.format("您好！关于您提到的「%s」，这是一个很好的问题。如需获取更准确的信息，建议您查阅相关文档或联系专业支持团队。",
                userContent.length() > 30 ? userContent.substring(0, 30) + "…" : userContent);
    }

    private String lastUserMessage(ChatRequest request) {
        if (request.getMessages() == null) return "";
        return request.getMessages().stream()
                .filter(m -> "user".equals(m.getRole()))
                .reduce((a, b) -> b)
                .map(ChatMessage::getContent)
                .orElse("");
    }

    @SuppressWarnings("unchecked")
    private String matchTool(List<Map<String, Object>> tools, String userMsg) {
        if (userMsg == null || userMsg.isBlank()) return null;
        String lower = userMsg.toLowerCase();
        boolean hasQueryKeyword = lower.contains("订单") || lower.contains("物流")
                || lower.contains("快递") || lower.contains("库存")
                || lower.contains("工单") || lower.contains("查")
                || lower.contains("到哪") || lower.contains("状态")
                || lower.contains("退款") || lower.contains("退货")
                || lower.contains("退钱") || lower.contains("能退") || lower.contains("申请退");
        if (!hasQueryKeyword) return null;

        String preferredTool = null;
        if (lower.contains("退款状态") || lower.contains("退款进度") || lower.contains("退到哪")) {
            preferredTool = "get_refund_status";
        } else if (lower.contains("申请退") || lower.contains("我要退") || lower.contains("帮我退")) {
            preferredTool = "submit_refund";
        } else if (lower.contains("能退") || lower.contains("可以退") || lower.contains("退款吗") || lower.contains("退货")) {
            preferredTool = "check_refund_eligibility";
        } else if (lower.contains("取消退")) {
            preferredTool = "cancel_refund";
        }

        for (Map<String, Object> tool : tools) {
            Object fn = tool.get("function");
            if (fn instanceof Map<?, ?> fnMap) {
                Object name = fnMap.get("name");
                if (name != null && name.toString().equals(preferredTool)) return name.toString();
            }
        }
        for (Map<String, Object> tool : tools) {
            Object fn = tool.get("function");
            if (fn instanceof Map<?, ?> fnMap) {
                Object name = fnMap.get("name");
                if (name != null) return name.toString();
            }
        }
        return null;
    }

    private String buildToolReply(String toolResultJson, String userQuestion) {
        if (toolResultJson.contains("eligible")) {
            if (toolResultJson.contains("\"eligible\":true")) {
                return "好消息！我帮您查询了一下，您的订单 **20240501001**（无线蓝牙耳机，¥299.00）**符合退款条件**。\n\n" +
                       "该商品在7天无理由退货期内，您可以直接申请退款。\n\n" +
                       "请问需要我现在帮您提交退款申请吗？";
            } else {
                return "抱歉，经查询您的订单目前**不符合退款条件**，可能已超过退货期或属于不可退商品。\n\n" +
                       "如有疑问，建议联系人工客服进一步处理。";
            }
        }
        if (toolResultJson.contains("refundId") && toolResultJson.contains("审核中")) {
            return "退款申请已成功提交！\n\n" +
                   "- 退款单号：**RF20260408001**\n" +
                   "- 退款金额：**¥299.00**\n" +
                   "- 当前状态：审核中\n" +
                   "- 预计到账：**1-3个工作日**退回原支付账户\n\n" +
                   "退款到账后系统会短信通知您，请注意查收。";
        }
        if (toolResultJson.contains("\"status\":\"已退款\"")) {
            return "您的退款已处理完成！\n\n" +
                   "- 退款单号：**RF20260408001**\n" +
                   "- 退款金额：**¥299.00**\n" +
                   "- 退款状态：**已退款** ✓\n" +
                   "- 退款时间：2026-04-08 16:30:00\n" +
                   "- 退款账户：尾号 **6379**\n\n" +
                   "如果长时间未到账，请联系您的银行确认。";
        }
        if (toolResultJson.contains("已成功取消")) {
            return "您的退款申请已成功取消，订单已恢复正常状态。\n\n如后续还需要退款，随时告诉我即可。";
        }
        return "已查询到相关信息：\n\n```json\n" + toolResultJson + "\n```\n\n如需进一步操作，请告诉我。";
    }

    private String extractRef(String systemContent, int n) {
        String marker = "[" + n + "] ";
        int start = systemContent.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        int end = systemContent.indexOf("[" + (n + 1) + "] ", start);
        return (end > start ? systemContent.substring(start, end) : systemContent.substring(start)).trim();
    }
}
