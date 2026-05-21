package com.hify.modules.workflow.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.workflow.infra.entity.WorkflowEdge;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 工作流节点连接关系 Mapper
 */
@Mapper
public interface WorkflowEdgeMapper extends BaseMapper<WorkflowEdge> {

    /**
     * 批量插入边
     */
    @Insert("<script>INSERT INTO t_workflow_edge (workflow_id, source_node_key, target_node_key, condition_expr, sort_order) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.workflowId}, #{item.sourceNodeKey}, #{item.targetNodeKey}, #{item.conditionExpr}, #{item.sortOrder})" +
            "</foreach></script>")
    void batchInsert(@Param("list") List<WorkflowEdge> edges);

    /**
     * 物理删除指定工作流下的所有边（更新时全量替换，无需保留历史版本）
     */
    @Update("DELETE FROM t_workflow_edge WHERE workflow_id = #{workflowId}")
    void deleteByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 查询指定工作流下的所有有效边
     */
    @Select("SELECT * FROM t_workflow_edge WHERE workflow_id = #{workflowId} AND deleted = 0 ORDER BY source_node_key, sort_order")
    List<WorkflowEdge> selectByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 批量统计各工作流的边数量
     */
    @Select("<script>SELECT workflow_id as `workflowId`, COUNT(*) as `cnt` FROM t_workflow_edge WHERE workflow_id IN <foreach collection='workflowIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> AND deleted = 0 GROUP BY workflow_id</script>")
    List<java.util.Map<String, Object>> countByWorkflowIds(@Param("workflowIds") List<Long> workflowIds);
}
