package com.hify.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 对话消息 Mapper
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 查询指定会话的最近消息（按时间正序，用于上下文组装）
     *
     * @param sessionId 会话 ID
     * @param limit     查询条数
     * @return 消息列表
     */
    @Select("SELECT id, session_id, role, content, tokens, created_at, updated_at, deleted " +
            "FROM (" +
            "  SELECT id, session_id, role, content, tokens, created_at, updated_at, deleted, " +
            "         ROW_NUMBER() OVER (ORDER BY created_at DESC) AS rn " +
            "  FROM t_chat_message " +
            "  WHERE session_id = #{sessionId} AND deleted = 0 " +
            ") t " +
            "WHERE rn <= #{limit} " +
            "ORDER BY created_at ASC")
    List<ChatMessage> selectRecentBySessionId(@Param("sessionId") Long sessionId,
                                               @Param("limit") Integer limit);
}
