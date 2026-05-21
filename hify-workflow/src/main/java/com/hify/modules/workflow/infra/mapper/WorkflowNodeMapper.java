package com.hify.modules.workflow.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.workflow.infra.entity.WorkflowNode;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 工作流节点 Mapper
 */
@Mapper
public interface WorkflowNodeMapper extends BaseMapper<WorkflowNode> {

    /**
     * 批量插入节点
     */
    @Insert("<script>INSERT INTO t_workflow_node (workflow_id, node_key, node_type, name, config_json) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.workflowId}, #{item.nodeKey}, #{item.nodeType}, #{item.name}, #{item.configJson})" +
            "</foreach></script>")
    void batchInsert(@Param("list") List<WorkflowNode> nodes);

    /**
     * 物理删除指定工作流下的所有节点（更新时全量替换，无需保留历史版本）
     */
    @Update("DELETE FROM t_workflow_node WHERE workflow_id = #{workflowId}")
    void deleteByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 查询指定工作流下的所有有效节点
     */
    @Select("SELECT * FROM t_workflow_node WHERE workflow_id = #{workflowId} AND deleted = 0 ORDER BY id")
    List<WorkflowNode> selectByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 批量统计各工作流的节点数量
     */
    @Select("<script>SELECT workflow_id as `workflowId`, COUNT(*) as `cnt` FROM t_workflow_node WHERE workflow_id IN <foreach collection='workflowIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> AND deleted = 0 GROUP BY workflow_id</script>")
    List<java.util.Map<String, Object>> countByWorkflowIds(@Param("workflowIds") List<Long> workflowIds);
}
