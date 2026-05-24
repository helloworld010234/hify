package com.hify.modules.agent.service.impl;

import com.hify.modules.agent.api.AgentService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.controller.PageResult;
import com.hify.common.controller.Result;
import com.hify.modules.agent.dto.request.AgentCreateRequest;
import com.hify.modules.agent.dto.request.AgentListRequest;
import com.hify.modules.agent.dto.request.AgentUpdateRequest;
import com.hify.modules.agent.dto.response.AgentDetailResponse;
import com.hify.modules.agent.dto.response.AgentListResponse;
import com.hify.modules.agent.dto.response.ModelGroupResponse;
import com.hify.modules.agent.dto.response.ToolOptionResponse;
import com.hify.modules.agent.entity.Agent;
import com.hify.modules.agent.entity.AgentKnowledgeRel;
import com.hify.modules.agent.entity.AgentToolRel;
import com.hify.modules.agent.mapper.AgentKnowledgeRelMapper;
import com.hify.modules.agent.mapper.AgentMapper;
import com.hify.modules.agent.mapper.AgentToolRelMapper;
import com.hify.common.service.mcp.McpToolService;
import com.hify.common.service.mcp.McpToolDefinition;
import com.hify.modules.provider.api.ProviderService;
import com.hify.modules.provider.dto.ModelDto;
import com.hify.modules.provider.dto.response.ProviderListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
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
public class AgentServiceImpl implements com.hify.modules.agent.api.AgentService {

    private final AgentMapper agentMapper;
    private final AgentKnowledgeRelMapper knowledgeRelMapper;
    private final AgentToolRelMapper toolRelMapper;
    private final ProviderService providerService;
    private final McpToolService mcpToolService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "agent-cache", allEntries = true)
    public AgentDetailResponse create(AgentCreateRequest request) {
        // 第一步：校验 name 唯一性
        if (agentMapper.selectByName(request.getName()) != null) {
            throw new BizException(ErrorCode.AGENT_NAME_EXISTS, "Agent 名称已存在：" + request.getName());
        }

        // 第二步：跨模块校验 modelConfigId（走 ProviderService 接口，不直接查 Mapper）
        String modelName = providerService.getModelNameById(request.getModelConfigId());
        if (modelName == null) {
            throw new BizException(ErrorCode.MODEL_CONFIG_NOT_FOUND, "模型配置不存在：" + request.getModelConfigId());
        }

        // 第三步：在 @Transactional 事务中 INSERT agent + 批量 INSERT agent_tool
        Agent agent = new Agent();
        agent.setName(request.getName());
        agent.setDescription(request.getDescription());
        agent.setSystemPrompt(request.getSystemPrompt());
        agent.setModelConfigId(request.getModelConfigId());
        agent.setTemperature(request.getTemperature());
        agent.setMaxTokens(request.getMaxTokens());
        agent.setMaxContextTurns(request.getMaxContextTurns());
        agent.setEnabled(request.getEnabled());
        agent.setKnowledgeBaseId(request.getKnowledgeBaseId());
        agent.setWorkflowId(request.getWorkflowId());

        agentMapper.insert(agent);

        // 批量插入关联（knowledge + tool）
        saveRelations(agent.getId(), request.getKnowledgeIds(), request.getToolIds());

        log.info("Agent created: id={}, name={}", agent.getId(), agent.getName());

        // 返回 AgentDetailResponse（组装关联数据）
        List<Long> knowledgeIds = knowledgeRelMapper.selectKnowledgeIdsByAgentId(agent.getId());
        List<Long> toolIds = toolRelMapper.selectToolIdsByAgentId(agent.getId());
        return AgentAssembler.toDetailResponse(agent, modelName, knowledgeIds, toolIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "agent-cache", allEntries = true)
    public void update(Long id, AgentUpdateRequest request) {
        Agent agent = agentMapper.selectById(id);
        if (agent == null || agent.getDeleted() != null && agent.getDeleted() == 1) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND, "Agent 不存在：" + id);
        }

        // 校验名称唯一性（排除自己）
        Agent exist = agentMapper.selectByName(request.getName());
        if (exist != null && !exist.getId().equals(id)) {
            throw new BizException(ErrorCode.AGENT_NAME_EXISTS, "Agent 名称已存在：" + request.getName());
        }

        // 跨模块校验 modelConfigId
        String modelName = providerService.getModelNameById(request.getModelConfigId());
        if (modelName == null) {
            throw new BizException(ErrorCode.MODEL_CONFIG_NOT_FOUND, "模型配置不存在：" + request.getModelConfigId());
        }

        agent.setName(request.getName());
        agent.setDescription(request.getDescription());
        agent.setSystemPrompt(request.getSystemPrompt());
        agent.setModelConfigId(request.getModelConfigId());
        agent.setTemperature(request.getTemperature());
        agent.setMaxTokens(request.getMaxTokens());
        agent.setMaxContextTurns(request.getMaxContextTurns());
        agent.setEnabled(request.getEnabled());
        agent.setKnowledgeBaseId(request.getKnowledgeBaseId());
        agent.setWorkflowId(request.getWorkflowId());

        agentMapper.updateById(agent);

        // 关联全量覆盖：先删后插
        knowledgeRelMapper.deleteByAgentId(id);
        toolRelMapper.deleteByAgentId(id);
        saveRelations(id, request.getKnowledgeIds(), request.getToolIds());

        log.info("Agent updated: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "agent-cache", allEntries = true)
    public void delete(Long id) {
        Agent agent = agentMapper.selectById(id);
        if (agent == null || agent.getDeleted() != null && agent.getDeleted() == 1) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND, "Agent 不存在：" + id);
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
            throw new BizException(ErrorCode.AGENT_NOT_FOUND, "Agent 不存在：" + id);
        }

        String modelName = providerService.getModelNameById(agent.getModelConfigId());
        List<Long> knowledgeIds = knowledgeRelMapper.selectKnowledgeIdsByAgentId(id);
        List<Long> toolIds = toolRelMapper.selectToolIdsByAgentId(id);

        return AgentAssembler.toDetailResponse(agent, modelName, knowledgeIds, toolIds);
    }

    @Override
    public Result<PageResult<AgentListResponse>> list(AgentListRequest request) {
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
            return PageResult.of(Collections.emptyList(), 0, (int) request.getCurrent(), (int) request.getSize());
        }

        // 跨模块查询模型名称（走 ProviderService 接口）
        List<Long> modelConfigIds = records.stream()
                .map(Agent::getModelConfigId)
                .filter(mid -> mid != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> modelNameMap = new java.util.HashMap<>();
        for (Long modelId : modelConfigIds) {
            modelNameMap.put(modelId, providerService.getModelNameById(modelId));
        }

        // 批量查询关联数量
        List<Long> agentIds = records.stream().map(Agent::getId).collect(Collectors.toList());
        Map<Long, Long> knowledgeCountMap = new java.util.HashMap<>();
        for (Map<String, Object> m : knowledgeRelMapper.countByAgentIds(agentIds)) {
            Long aid = extractLongFromMap(m, "agentId");
            Long cnt = extractLongFromMap(m, "cnt");
            if (aid != null) {
                knowledgeCountMap.put(aid, cnt);
            }
        }
        Map<Long, Long> toolCountMap = new java.util.HashMap<>();
        for (Map<String, Object> m : toolRelMapper.countByAgentIds(agentIds)) {
            Long aid = extractLongFromMap(m, "agentId");
            Long cnt = extractLongFromMap(m, "cnt");
            if (aid != null) {
                toolCountMap.put(aid, cnt);
            }
        }
        Map<Long, List<Long>> toolIdsMap = new java.util.HashMap<>();
        for (Map<String, Object> m : toolRelMapper.selectToolIdsByAgentIds(agentIds)) {
            Long aid = extractLongFromMap(m, "agentId");
            Long tid = extractLongFromMap(m, "toolId");
            if (aid != null && tid != null) {
                toolIdsMap.computeIfAbsent(aid, k -> new ArrayList<>()).add(tid);
            }
        }

        List<AgentListResponse> list = records.stream().map(agent -> {
            AgentListResponse resp = new AgentListResponse();
            resp.setId(agent.getId());
            resp.setName(agent.getName());
            resp.setDescription(agent.getDescription());
            resp.setModelConfigId(agent.getModelConfigId());
            resp.setModelName(modelNameMap.getOrDefault(agent.getModelConfigId(), ""));
            resp.setSystemPrompt(agent.getSystemPrompt());
            resp.setKnowledgeBaseId(agent.getKnowledgeBaseId());
            resp.setMaxContextTurns(agent.getMaxContextTurns());
            resp.setMaxTokens(agent.getMaxTokens());
            resp.setTemperature(agent.getTemperature());
            resp.setEnabled(agent.getEnabled());
            resp.setCreatedAt(agent.getCreatedAt());
            resp.setKnowledgeCount(knowledgeCountMap.getOrDefault(agent.getId(), 0L).intValue());
            resp.setToolCount(toolCountMap.getOrDefault(agent.getId(), 0L).intValue());
            resp.setToolIds(toolIdsMap.getOrDefault(agent.getId(), Collections.emptyList()));
            return resp;
        }).collect(Collectors.toList());

        return PageResult.of(list, resultPage.getTotal(), (int) request.getCurrent(), (int) request.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "agent-cache", allEntries = true)
    public Long clone(Long id) {
        Agent source = agentMapper.selectById(id);
        if (source == null || source.getDeleted() != null && source.getDeleted() == 1) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND, "Agent 不存在：" + id);
        }

        Agent agent = new Agent();
        agent.setName(source.getName() + " 副本");
        agent.setDescription(source.getDescription());
        agent.setSystemPrompt(source.getSystemPrompt());
        agent.setModelConfigId(source.getModelConfigId());
        agent.setTemperature(source.getTemperature());
        agent.setMaxTokens(source.getMaxTokens());
        agent.setMaxContextTurns(source.getMaxContextTurns());
        agent.setKnowledgeBaseId(source.getKnowledgeBaseId());
        agent.setEnabled(0); // 克隆后默认禁用，需手动启用

        agentMapper.insert(agent);

        // 复制关联
        List<Long> knowledgeIds = knowledgeRelMapper.selectKnowledgeIdsByAgentId(id);
        List<Long> toolIds = toolRelMapper.selectToolIdsByAgentId(id);
        saveRelations(agent.getId(), knowledgeIds, toolIds);

        log.info("Agent cloned: sourceId={}, newId={}", id, agent.getId());
        return agent.getId();
    }

    @Override
    public List<ModelGroupResponse> listModelGroups() {
        // 跨模块查询：走 ProviderService 接口，不直接查 Mapper
        List<ProviderListResponse> providers = providerService.listAllActiveProviders();
        List<ModelDto> models = providerService.listAllActiveModels();

        Map<Long, String> providerNameMap = providers.stream()
                .collect(Collectors.toMap(ProviderListResponse::getId, ProviderListResponse::getName));

        Map<Long, List<ModelDto>> modelMap = models.stream()
                .collect(Collectors.groupingBy(ModelDto::getProviderId));

        List<ModelGroupResponse> groups = new ArrayList<>();
        for (ProviderListResponse provider : providers) {
            List<ModelDto> providerModels = modelMap.getOrDefault(provider.getId(), Collections.emptyList());
            if (providerModels.isEmpty()) continue;

            ModelGroupResponse group = new ModelGroupResponse();
            group.setProviderId(provider.getId());
            group.setProviderName(provider.getName());
            group.setModels(providerModels.stream().map(m -> {
                ModelGroupResponse.ModelOption opt = new ModelGroupResponse.ModelOption();
                opt.setId(m.getId());
                opt.setModelCode(m.getModelCode());
                opt.setModelName(m.getModelName());
                return opt;
            }).collect(Collectors.toList()));
            groups.add(group);
        }
        return groups;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "agent-cache", allEntries = true)
    public void updateMaxContextTurns(Long id, Integer maxContextTurns) {
        Agent agent = agentMapper.selectById(id);
        if (agent == null || agent.getDeleted() != null && agent.getDeleted() == 1) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND, "Agent 不存在：" + id);
        }
        agent.setMaxContextTurns(maxContextTurns);
        agentMapper.updateById(agent);
        log.info("Agent maxContextTurns updated: id={}, maxContextTurns={}", id, maxContextTurns);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "agent-cache", allEntries = true)
    public void updateTemperature(Long id, java.math.BigDecimal temperature) {
        Agent agent = agentMapper.selectById(id);
        if (agent == null || agent.getDeleted() != null && agent.getDeleted() == 1) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND, "Agent 不存在：" + id);
        }
        agent.setTemperature(temperature);
        agentMapper.updateById(agent);
        log.info("Agent temperature updated: id={}, temperature={}", id, temperature);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "agent-cache", allEntries = true)
    public void updateToolIds(Long id, java.util.List<Long> toolIds) {
        Agent agent = agentMapper.selectById(id);
        if (agent == null || agent.getDeleted() != null && agent.getDeleted() == 1) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND, "Agent 不存在：" + id);
        }
        toolRelMapper.deleteByAgentId(id);
        saveRelations(id, null, toolIds);
        log.info("Agent tools updated: id={}, toolCount={}", id, toolIds == null ? 0 : toolIds.size());
    }

    @Override
    public List<ToolOptionResponse> listTools() {
        List<McpToolDefinition> defs = mcpToolService.listAllTools();
        return defs.stream().map(def -> {
            ToolOptionResponse opt = new ToolOptionResponse();
            opt.setId(def.getId());
            opt.setName(def.getName());
            opt.setDescription(def.getDescription());
            return opt;
        }).collect(Collectors.toList());
    }

    // ---------- 私有方法 ----------

    private void saveRelations(Long agentId, List<Long> knowledgeIds, List<Long> toolIds) {
        if (!CollectionUtils.isEmpty(knowledgeIds)) {
            knowledgeRelMapper.batchInsert(agentId, knowledgeIds);
        }
        if (!CollectionUtils.isEmpty(toolIds)) {
            // 1. 数量上限校验
            if (toolIds.size() > 10) {
                throw new BizException(ErrorCode.PARAM_ERROR, "一个 Agent 最多绑定 10 个工具");
            }
            // 2. 工具有效性校验（存在且所属 Server 启用）
            List<Long> invalidIds = mcpToolService.findInvalidToolIds(toolIds);
            if (!invalidIds.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "工具不存在或所属 MCP Server 未启用: " + invalidIds);
            }
            toolRelMapper.batchInsert(agentId, toolIds);
        }
    }

    /**
     * 大小写不敏感地从 Map 中获取 Long 值（适配 H2 / MySQL 列名差异）
     */
    private static Long extractLongFromMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            value = map.get(key.toUpperCase());
        }
        if (value == null) {
            value = map.get(key.toLowerCase());
        }
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.valueOf(value.toString());
    }
}
