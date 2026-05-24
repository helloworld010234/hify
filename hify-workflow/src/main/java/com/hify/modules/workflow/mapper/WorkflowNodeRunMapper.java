package com.hify.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.workflow.entity.WorkflowNodeRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工作流节点执行记录 Mapper
 */
@Mapper
public interface WorkflowNodeRunMapper extends BaseMapper<WorkflowNodeRun> {

    @Select("SELECT * FROM t_workflow_node_run WHERE workflow_run_id = #{runId} ORDER BY id")
    List<WorkflowNodeRun> selectByRunId(@Param("runId") Long runId);
}
