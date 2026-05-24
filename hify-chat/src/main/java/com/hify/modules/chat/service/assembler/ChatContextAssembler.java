package com.hify.modules.chat.service.assembler;

import com.hify.common.util.TokenUtil;
import com.hify.modules.chat.entity.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 对话上下文组装器
 * <p>
 * 将历史消息组装为 LLM 可用的 messages[] 格式，支持两种策略：
 * - SLIDING_WINDOW（默认）：按 token 预算滑动窗口
 * - FIXED_TURNS：固定轮次限制
 */
@Component
public class ChatContextAssembler {

    private static final double SLIDING_WINDOW_BUDGET_RATIO = 0.7;

    /**
     * 组装 LLM 请求消息列表
     *
     * @param history          历史消息（按时间正序）
     * @param systemPrompt     系统提示词
     * @param currentUserMsg   当前用户消息
     * @param strategy         策略：SLIDING_WINDOW / FIXED_TURNS
     * @param maxContextTokens 最大上下文 token 数（来自 Agent 配置）
     * @param maxContextTurns  最大上下文轮数（来自 Agent 配置）
     * @return LLM 消息列表
     */
    public List<com.hify.modules.provider.dto.chat.ChatMessage> assemble(List<ChatMessage> history, String systemPrompt,
                                          String currentUserMsg, String strategy,
                                          Integer maxContextTokens, Integer maxContextTurns) {
        List<com.hify.modules.provider.dto.chat.ChatMessage> result = new ArrayList<>();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            result.add(new com.hify.modules.provider.dto.chat.ChatMessage("system", systemPrompt));
        }

        List<ChatMessage> selected;
        if ("FIXED_TURNS".equals(strategy)) {
            selected = selectByFixedTurns(history, maxContextTurns);
        } else {
            selected = selectBySlidingWindow(history, maxContextTokens);
        }

        for (ChatMessage msg : selected) {
            result.add(new com.hify.modules.provider.dto.chat.ChatMessage(msg.getRole(), msg.getContent()));
        }

        result.add(new com.hify.modules.provider.dto.chat.ChatMessage("user", currentUserMsg));
        return result;
    }

    private List<ChatMessage> selectByFixedTurns(List<ChatMessage> history, Integer maxContextTurns) {
        if (maxContextTurns == null || maxContextTurns <= 0) {
            maxContextTurns = 10;
        }
        int limit = maxContextTurns * 2;
        if (history.size() <= limit) {
            return new ArrayList<>(history);
        }
        return new ArrayList<>(history.subList(history.size() - limit, history.size()));
    }

    private List<ChatMessage> selectBySlidingWindow(List<ChatMessage> history, Integer maxContextTokens) {
        if (maxContextTokens == null || maxContextTokens <= 0) {
            maxContextTokens = 4096;
        }
        int budget = (int) (maxContextTokens * SLIDING_WINDOW_BUDGET_RATIO);

        List<ChatMessage> reversed = new ArrayList<>(history);
        Collections.reverse(reversed);

        List<ChatMessage> selected = new ArrayList<>();
        int totalTokens = 0;
        for (ChatMessage msg : reversed) {
            int tokens = msg.getTokens() != null ? msg.getTokens() : TokenUtil.estimateTokens(msg.getContent());
            if (totalTokens + tokens > budget && !selected.isEmpty()) {
                break;
            }
            selected.add(msg);
            totalTokens += tokens;
        }

        Collections.reverse(selected);
        return selected;
    }


}
