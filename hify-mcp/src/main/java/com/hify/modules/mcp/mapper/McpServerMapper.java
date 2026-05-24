package com.hify.modules.mcp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.mcp.entity.McpServer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MCP Server Mapper
 */
@Mapper
public interface McpServerMapper extends BaseMapper<McpServer> {

    /**
     * 根据名称查询（排除已删除）
     */
    @Select("SELECT * FROM t_mcp_server WHERE name = #{name} AND deleted = 0 LIMIT 1")
    McpServer selectByName(@Param("name") String name);
}
