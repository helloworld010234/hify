package com.hify.modules.workflow.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResult;
import com.hify.modules.workflow.api.dto.request.EdgeRequest;
import com.hify.modules.workflow.api.dto.request.NodeRequest;
import com.hify.modules.workflow.api.dto.request.WorkflowCreateRequest;
import com.hify.modules.workflow.api.dto.request.WorkflowListRequest;
import com.hify.modules.workflow.api.dto.request.WorkflowUpdateRequest;
import com.hify.modules.workflow.api.dto.response.EdgeResponse;
import com.hify.modules.workflow.api.dto.response.NodeResponse;
import com.hify.modules.workflow.api.dto.response.WorkflowDetailResponse;
import com.hify.modules.workflow.api.dto.response.WorkflowListResponse;
import com.hify.modules.workflow.domain.nodeconfig.NodeConfig;
import com.hify.modules.workflow.domain.nodeconfig.NodeConfigParser;
import com.hify.modules.workflow.infra.entity.Workflow;
import com.hify.modules.workflow.infra.entity.WorkflowEdge;
import com.hify.modules.workflow.infra.entity.WorkflowNode;
import com.hify.modules.workflow.infra.mapper.WorkflowEdgeMapper;
import com.hify.modules.workflow.infra.mapper.WorkflowMapper;
import com.hify.modules.workflow.infra.mapper.WorkflowNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowMapper workflowMapper;
    private final WorkflowNodeMapper nodeMapper;
    private final WorkflowEdgeMapper edgeMapper;
    private final ObjectMapper objectMapper;
    private final NodeConfigParser nodeConfigParser;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDetailResponse create(WorkflowCreateRequest request) {
        // 校验名称唯一性
        if (workflowMapper.selectByName(request.getName()) != null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工作流名称已存在：" + request.getName());
        }

        // 插入工作流主表
        Workflow workflow = new Workflow();
        workflow.setName(request.getName());
        workflow.setDescription(request.getDescription());
        workflow.setEnabled(request.getEnabled());
        workflowMapper.insert(workflow);

        Long workflowId = workflow.getId();

        // 批量插入节点
        saveNodes(workflowId, request.getNodes());

        // 批量插入边
        saveEdges(workflowId, request.getEdges());

        log.info("Workflow created: id={}, name={}", workflowId, workflow.getName());

        return buildDetailResponse(workflow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, WorkflowUpdateRequest request) {
        Workflow workflow = workflowMapper.selectById(id);
        if (workflow == null || isDeleted(workflow)) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流不存在：" + id);
        }

        // 校验名称唯一性（排除自己）
        Workflow exist = workflowMapper.selectByName(request.getName());
        if (exist != null && !exist.getId().equals(id)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工作流名称已存在：" + request.getName());
        }

        // 逻辑删除旧节点和边
        nodeMapper.deleteByWorkflowId(id);
        edgeMapper.deleteByWorkflowId(id);

        // 更新主表
        workflow.setName(request.getName());
        workflow.setDescription(request.getDescription());
        workflow.setEnabled(request.getEnabled());
        workflowMapper.updateById(workflow);

        // 批量插入新节点和边
        saveNodes(id, request.getNodes());
        saveEdges(id, request.getEdges());

        log.info("Workflow updated: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Workflow workflow = workflowMapper.selectById(id);
        if (workflow == null || isDeleted(workflow)) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流不存在：" + id);
        }

        // 逻辑删除主表
        workflowMapper.deleteById(id);

        // 级联逻辑删除节点和边
        nodeMapper.deleteByWorkflowId(id);
        edgeMapper.deleteByWorkflowId(id);

        log.info("Workflow deleted: id={}", id);
    }

    @Override
    public WorkflowDetailResponse getById(Long id) {
        Workflow workflow = workflowMapper.selectById(id);
        if (workflow == null || isDeleted(workflow)) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流不存在：" + id);
        }

        return buildDetailResponse(workflow);
    }

    @Override
    public PageResult<WorkflowListResponse> list(WorkflowListRequest request) {
        Page<Workflow> page = new Page<>(request.getCurrent(), request.getSize());
        LambdaQueryWrapper<Workflow> wrapper = new LambdaQueryWrapper<Workflow>()
                .eq(Workflow::getDeleted, 0)
                .like(org.springframework.util.StringUtils.hasText(request.getKeyword()), Workflow::getName, request.getKeyword())
                .eq(request.getEnabled() != null, Workflow::getEnabled, request.getEnabled())
                .orderByDesc(Workflow::getCreatedAt);

        Page<Workflow> resultPage = workflowMapper.selectPage(page, wrapper);

        List<Workflow> records = resultPage.getRecords();
        if (CollectionUtils.isEmpty(records)) {
            return PageResult.of(0, request.getCurrent(), request.getSize(), Collections.emptyList());
        }

        List<Long> workflowIds = records.stream().map(Workflow::getId).collect(Collectors.toList());

        // 批量查询节点数量
        Map<Long, Integer> nodeCountMap = countMapFromResult(nodeMapper.countByWorkflowIds(workflowIds));
        // 批量查询边数量
        Map<Long, Integer> edgeCountMap = countMapFromResult(edgeMapper.countByWorkflowIds(workflowIds));

        List<WorkflowListResponse> list = records.stream().map(w -> {
            WorkflowListResponse resp = new WorkflowListResponse();
            resp.setId(w.getId());
            resp.setName(w.getName());
            resp.setDescription(w.getDescription());
            resp.setEnabled(w.getEnabled());
            resp.setNodeCount(nodeCountMap.getOrDefault(w.getId(), 0));
            resp.setEdgeCount(edgeCountMap.getOrDefault(w.getId(), 0));
            resp.setCreatedAt(w.getCreatedAt());
            return resp;
        }).collect(Collectors.toList());

        return PageResult.of(resultPage.getTotal(), request.getCurrent(), request.getSize(), list);
    }

    // ---------- 私有方法 ----------

    private boolean isDeleted(Workflow workflow) {
        return workflow.getDeleted() != null && workflow.getDeleted() == 1;
    }

    private void saveNodes(Long workflowId, List<NodeRequest> nodes) {
        if (CollectionUtils.isEmpty(nodes)) {
            return;
        }
        List<WorkflowNode> nodeList = new ArrayList<>();
        for (NodeRequest nr : nodes) {
            String configJson;
            try {
                configJson = objectMapper.writeValueAsString(nr.getConfig());
            } catch (JsonProcessingException e) {
                throw new BizException(ErrorCode.PARAM_ERROR, "节点配置序列化失败: " + nr.getNodeKey());
            }
            // 验证配置格式
            nodeConfigParser.parse(nr.getType(), configJson);

            WorkflowNode node = new WorkflowNode();
            node.setWorkflowId(workflowId);
            node.setNodeKey(nr.getNodeKey());
            node.setNodeType(nr.getType());
            node.setName(nr.getName() == null ? "" : nr.getName());
            node.setConfigJson(configJson);
            nodeList.add(node);
        }
        nodeMapper.batchInsert(nodeList);
    }

    private void saveEdges(Long workflowId, List<EdgeRequest> edges) {
        if (CollectionUtils.isEmpty(edges)) {
            return;
        }
        List<WorkflowEdge> edgeList = new ArrayList<>();
        for (EdgeRequest er : edges) {
            WorkflowEdge edge = new WorkflowEdge();
            edge.setWorkflowId(workflowId);
            edge.setSourceNodeKey(er.getSourceNodeKey());
            edge.setTargetNodeKey(er.getTargetNodeKey());
            edge.setConditionExpr(er.getConditionExpr() == null ? "" : er.getConditionExpr());
            edge.setSortOrder(er.getSortOrder() == null ? 0 : er.getSortOrder());
            edgeList.add(edge);
        }
        edgeMapper.batchInsert(edgeList);
    }

    private WorkflowDetailResponse buildDetailResponse(Workflow workflow) {
        Long workflowId = workflow.getId();

        List<WorkflowNode> nodes = nodeMapper.selectByWorkflowId(workflowId);
        List<NodeResponse> nodeResponses = nodes.stream().map(n -> {
            NodeConfig config = nodeConfigParser.parse(n.getNodeType(), n.getConfigJson());
            NodeResponse resp = new NodeResponse();
            resp.setNodeKey(n.getNodeKey());
            resp.setType(n.getNodeType());
            resp.setName(n.getName());
            resp.setConfig(config);
            return resp;
        }).collect(Collectors.toList());

        List<WorkflowEdge> edges = edgeMapper.selectByWorkflowId(workflowId);
        List<EdgeResponse> edgeResponses = edges.stream().map(e -> {
            EdgeResponse resp = new EdgeResponse();
            resp.setSourceNodeKey(e.getSourceNodeKey());
            resp.setTargetNodeKey(e.getTargetNodeKey());
            resp.setConditionExpr(e.getConditionExpr());
            resp.setSortOrder(e.getSortOrder());
            return resp;
        }).collect(Collectors.toList());

        WorkflowDetailResponse response = new WorkflowDetailResponse();
        response.setId(workflow.getId());
        response.setName(workflow.getName());
        response.setDescription(workflow.getDescription());
        response.setEnabled(workflow.getEnabled());
        response.setNodes(nodeResponses);
        response.setEdges(edgeResponses);
        response.setCreatedAt(workflow.getCreatedAt());
        response.setUpdatedAt(workflow.getUpdatedAt());
        return response;
    }

    private Map<Long, Integer> countMapFromResult(List<Map<String, Object>> resultList) {
        if (CollectionUtils.isEmpty(resultList)) {
            return Collections.emptyMap();
        }
        return resultList.stream().collect(Collectors.toMap(
                m -> extractLongFromMap(m, "workflowId"),
                m -> ((Number) m.get("cnt")).intValue()
        ));
    }

    private static Long extractLongFromMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            value = map.get(key.toUpperCase());
        }
        if (value == null) {
            value = map.get(key.toLowerCase());
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return value == null ? null : Long.valueOf(value.toString());
    }
}
