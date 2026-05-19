package com.hify.modules.agent.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResult;
import com.hify.modules.agent.api.dto.request.AgentCreateRequest;
import com.hify.modules.agent.api.dto.request.AgentListRequest;
import com.hify.modules.agent.api.dto.request.AgentUpdateRequest;
import com.hify.modules.agent.api.dto.response.AgentDetailResponse;
import com.hify.modules.agent.api.dto.response.AgentListResponse;
import com.hify.modules.agent.infra.entity.Agent;
import com.hify.modules.agent.infra.entity.AgentKnowledgeRel;
import com.hify.modules.agent.infra.entity.AgentToolRel;
import com.hify.modules.agent.infra.mapper.AgentKnowledgeRelMapper;
import com.hify.modules.agent.infra.mapper.AgentMapper;
import com.hify.modules.agent.infra.mapper.AgentToolRelMapper;
import com.hify.modules.provider.infra.mapper.ModelMapper;
import com.hify.modules.provider.infra.po.ModelPo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;
    private final AgentKnowledgeRelMapper knowledgeRelMapper;
    private final AgentToolRelMapper toolRelMapper;
    private final ModelMapper modelMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(AgentCreateRequest request) {
        // 校验名称唯一性
        if (agentMapper.selectByName(request.getName()) != null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "Agent 名称已存在：" + request.getName());
        }

        Agent agent = new Agent();
        agent.setName(request.getName());
        agent.setDescription(request.getDescription());
        agent.setSystemPrompt(request.getSystemPrompt());
        agent.setModelConfigId(request.getModelConfigId());
        agent.setTemperature(request.getTemperature());
        agent.setMaxTokens(request.getMaxTokens());
        agent.setMaxContextTurns(request.getMaxContextTurns());
        agent.setEnabled(request.getEnabled());

        agentMapper.insert(agent);

        // 保存关联
        saveRelations(agent.getId(), request.getKnowledgeIds(), request.getToolIds());

        log.info("Agent created: id={}, name={}", agent.getId(), agent.getName());
        return agent.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AgentUpdateRequest request) {
        Agent agent = agentMapper.selectById(id);
        if (agent == null || agent.getDeleted() != null && agent.getDeleted() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent 不存在：" + id);
        }

        // 校验名称唯一性（排除自己）
        Agent exist = agentMapper.selectByName(request.getName());
        if (exist != null && !exist.getId().equals(id)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "Agent 名称已存在：" + request.getName());
        }

        agent.setName(request.getName());
        agent.setDescription(request.getDescription());
        agent.setSystemPrompt(request.getSystemPrompt());
        agent.setModelConfigId(request.getModelConfigId());
        agent.setTemperature(request.getTemperature());
        agent.setMaxTokens(request.getMaxTokens());
        agent.setMaxContextTurns(request.getMaxContextTurns());
        agent.setEnabled(request.getEnabled());

        agentMapper.updateById(agent);

        // 关联全量覆盖：先删后插
        knowledgeRelMapper.deleteByAgentId(id);
        toolRelMapper.deleteByAgentId(id);
        saveRelations(id, request.getKnowledgeIds(), request.getToolIds());

        log.info("Agent updated: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Agent agent = agentMapper.selectById(id);
        if (agent == null || agent.getDeleted() != null && agent.getDeleted() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent 不存在：" + id);
        }

        agentMapper.deleteById(id);
        knowledgeRelMapper.deleteByAgentId(id);
        toolRelMapper.deleteByAgentId(id);

        log.info("Agent deleted: id={}", id);
    }

    @Override
    public AgentDetailResponse getById(Long id) {
        Agent agent = agentMapper.selectById(id);
        if (agent == null || agent.getDeleted() != null && agent.getDeleted() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent 不存在：" + id);
        }

        String modelName = getModelName(agent.getModelConfigId());
        List<Long> knowledgeIds = knowledgeRelMapper.selectKnowledgeIdsByAgentId(id);
        List<Long> toolIds = toolRelMapper.selectToolIdsByAgentId(id);

        return AgentDetailResponse.from(agent, modelName, knowledgeIds, toolIds);
    }

    @Override
    public PageResult<AgentListResponse> list(AgentListRequest request) {
        Page<Agent> page = new Page<>(request.getCurrent(), request.getSize());
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<Agent>()
                .eq(Agent::getDeleted, 0)
                .like(org.springframework.util.StringUtils.hasText(request.getKeyword()), Agent::getName, request.getKeyword())
                .eq(request.getEnabled() != null, Agent::getEnabled, request.getEnabled())
                .eq(request.getModelConfigId() != null, Agent::getModelConfigId, request.getModelConfigId())
                .orderByDesc(Agent::getCreatedAt);

        Page<Agent> resultPage = agentMapper.selectPage(page, wrapper);

        List<Agent> records = resultPage.getRecords();
        if (CollectionUtils.isEmpty(records)) {
            return PageResult.of(0, request.getCurrent(), request.getSize(), Collections.emptyList());
        }

        // 批量查询模型名称
        Set<Long> modelIds = records.stream()
                .map(Agent::getModelConfigId)
                .filter(mid -> mid != null)
                .collect(Collectors.toSet());
        Map<Long, String> modelNameMap = modelIds.isEmpty() ? Collections.emptyMap() :
                modelMapper.selectBatchIds(modelIds).stream()
                        .collect(Collectors.toMap(ModelPo::getId, ModelPo::getModelName, (a, b) -> a));

        // 批量查询关联数量
        List<Long> agentIds = records.stream().map(Agent::getId).collect(Collectors.toList());
        // TODO: 批量查询 knowledge/tool 数量，目前逐条查询

        List<AgentListResponse> list = records.stream().map(agent -> {
            AgentListResponse resp = new AgentListResponse();
            resp.setId(agent.getId());
            resp.setName(agent.getName());
            resp.setDescription(agent.getDescription());
            resp.setModelConfigId(agent.getModelConfigId());
            resp.setModelName(modelNameMap.getOrDefault(agent.getModelConfigId(), ""));
            resp.setEnabled(agent.getEnabled());
            resp.setCreatedAt(agent.getCreatedAt());
            // 关联数量暂置 0，后续批量优化
            resp.setKnowledgeCount(0);
            resp.setToolCount(0);
            return resp;
        }).collect(Collectors.toList());

        return PageResult.of(resultPage.getTotal(), request.getCurrent(), request.getSize(), list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long clone(Long id) {
        Agent source = agentMapper.selectById(id);
        if (source == null || source.getDeleted() != null && source.getDeleted() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent 不存在：" + id);
        }

        Agent agent = new Agent();
        agent.setName(source.getName() + " 副本");
        agent.setDescription(source.getDescription());
        agent.setSystemPrompt(source.getSystemPrompt());
        agent.setModelConfigId(source.getModelConfigId());
        agent.setTemperature(source.getTemperature());
        agent.setMaxTokens(source.getMaxTokens());
        agent.setMaxContextTurns(source.getMaxContextTurns());
        agent.setEnabled(0); // 克隆后默认禁用，需手动启用

        agentMapper.insert(agent);

        // 复制关联
        List<Long> knowledgeIds = knowledgeRelMapper.selectKnowledgeIdsByAgentId(id);
        List<Long> toolIds = toolRelMapper.selectToolIdsByAgentId(id);
        saveRelations(agent.getId(), knowledgeIds, toolIds);

        log.info("Agent cloned: sourceId={}, newId={}", id, agent.getId());
        return agent.getId();
    }

    // ---------- 私有方法 ----------

    private void saveRelations(Long agentId, List<Long> knowledgeIds, List<Long> toolIds) {
        if (!CollectionUtils.isEmpty(knowledgeIds)) {
            for (Long knowledgeId : knowledgeIds) {
                AgentKnowledgeRel rel = new AgentKnowledgeRel();
                rel.setAgentId(agentId);
                rel.setKnowledgeId(knowledgeId);
                knowledgeRelMapper.insert(rel);
            }
        }
        if (!CollectionUtils.isEmpty(toolIds)) {
            for (Long toolId : toolIds) {
                AgentToolRel rel = new AgentToolRel();
                rel.setAgentId(agentId);
                rel.setToolId(toolId);
                toolRelMapper.insert(rel);
            }
        }
    }

    private String getModelName(Long modelConfigId) {
        if (modelConfigId == null) {
            return "";
        }
        ModelPo model = modelMapper.selectById(modelConfigId);
        return model != null ? model.getModelName() : "";
    }
}
