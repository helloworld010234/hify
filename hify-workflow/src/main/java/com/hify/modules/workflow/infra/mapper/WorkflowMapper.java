package com.hify.modules.workflow.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.workflow.infra.entity.Workflow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 工作流定义 Mapper
 */
@Mapper
public interface WorkflowMapper extends BaseMapper<Workflow> {

    /**
     * 根据名称查询（排除已删除）
     */
    @Select("SELECT * FROM t_workflow WHERE deleted = 0 AND name = #{name} LIMIT 1")
    Workflow selectByName(@Param("name") String name);
}
