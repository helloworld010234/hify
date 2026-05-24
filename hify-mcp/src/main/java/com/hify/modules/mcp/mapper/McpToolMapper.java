package com.hify.modules.mcp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.mcp.entity.McpTool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MCP Tool Mapper
 */
@Mapper
public interface McpToolMapper extends BaseMapper<McpTool> {

    /**
     * 根据 Server ID 查询工具 ID 列表
     */
    @Select("SELECT id FROM t_mcp_tool WHERE server_id = #{serverId} AND deleted = 0")
    List<Long> selectIdsByServerId(@Param("serverId") Long serverId);

    /**
     * 根据 Server ID 查询工具列表
     */
    @Select("SELECT * FROM t_mcp_tool WHERE server_id = #{serverId} AND deleted = 0")
    @ResultMap("mybatis-plus_McpTool")
    List<McpTool> selectByServerId(@Param("serverId") Long serverId);
}
