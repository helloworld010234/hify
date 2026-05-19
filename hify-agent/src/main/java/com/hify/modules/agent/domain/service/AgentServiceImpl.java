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
import com.hify.modules.agent.api.dto.response.ModelGroupResponse;
import com.hify.modules.agent.api.dto.response.ToolOption;
import com.hify.modules.agent.infra.entity.Agent;
import com.hify.modules.agent.infra.entity.AgentKnowledgeRel;
import com.hify.modules.agent.infra.entity.AgentToolRel;
import com.hify.modules.agent.infra.mapper.AgentKnowledgeRelMapper;
import com.hify.modules.agent.infra.mapper.AgentMapper;
import com.hify.modules.agent.infra.mapper.AgentToolRelMapper;
import com.hify.modules.provider.api.ProviderService;
import com.hify.modules.provider.api.dto.ModelDto;
import com.hify.modules.provider.api.dto.response.ProviderListResponse;
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
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;
    private final AgentKnowledgeRelMapper knowledgeRelMapper;
    private final AgentToolRelMapper toolRelMapper;
    private final ProviderService providerService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "agent-cache", allEntries = true)
    public AgentDetailResponse create(AgentCreateRequest request) {
        // 第一步：校验 name 唯一性
        if (agentMapper.selectByName(request.getName()) != null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "Agent 名称已存在：" + request.getName());
        }

        // 第二步：跨模块校验 modelConfigId（走 ProviderService 接口，不直接查 Mapper）
        String modelName = providerService.getModelNameById(request.getModelConfigId());
        if (modelName == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "模型配置不存在：" + request.getModelConfigId());
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

        agentMapper.insert(agent);

        // 批量插入关联（knowledge + tool）
        saveRelations(agent.getId(), request.getKnowledgeIds(), request.getToolIds());

        log.info("Agent created: id={}, name={}", agent.getId(), agent.getName());

        // 返回 AgentDetailResponse（组装关联数据）
        List<Long> knowledgeIds = knowledgeRelMapper.selectKnowledgeIdsByAgentId(agent.getId());
        List<Long> toolIds = toolRelMapper.selectToolIdsByAgentId(agent.getId());
        return AgentDetailResponse.from(agent, modelName, knowledgeIds, toolIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "agent-cache", allEntries = true)
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

        // 跨模块校验 modelConfigId
        String modelName = providerService.getModelNameById(request.getModelConfigId());
        if (modelName == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "模型配置不存在：" + request.getModelConfigId());
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
    @CacheEvict(cacheNames = "agent-cache", allEntries = true)
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

        String modelName = providerService.getModelNameById(agent.getModelConfigId());
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

        // 跨模块查询模型名称（走 ProviderService 接口）
        List<Long> modelConfigIds = records.stream()
                .map(Agent::getModelConfigId)
                .filter(mid -> mid != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> modelNameMap = modelConfigIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> providerService.getModelNameById(id),
                        (a, b) -> a
                ));

        // 批量查询关联数量
        List<Long> agentIds = records.stream().map(Agent::getId).collect(Collectors.toList());
        Map<Long, Long> knowledgeCountMap = knowledgeRelMapper.countByAgentIds(agentIds).stream()
                .collect(Collectors.toMap(
                        m -> Long.valueOf(m.get("agentId").toString()),
                        m -> Long.valueOf(m.get("cnt").toString()),
                        (a, b) -> a
                ));
        Map<Long, Long> toolCountMap = toolRelMapper.countByAgentIds(agentIds).stream()
                .collect(Collectors.toMap(
                        m -> Long.valueOf(m.get("agentId").toString()),
                        m -> Long.valueOf(m.get("cnt").toString()),
                        (a, b) -> a
                ));

        List<AgentListResponse> list = records.stream().map(agent -> {
            AgentListResponse resp = new AgentListResponse();
            resp.setId(agent.getId());
            resp.setName(agent.getName());
            resp.setDescription(agent.getDescription());
            resp.setModelConfigId(agent.getModelConfigId());
            resp.setModelName(modelNameMap.getOrDefault(agent.getModelConfigId(), ""));
            resp.setEnabled(agent.getEnabled());
            resp.setCreatedAt(agent.getCreatedAt());
            resp.setKnowledgeCount(knowledgeCountMap.getOrDefault(agent.getId(), 0L).intValue());
            resp.setToolCount(toolCountMap.getOrDefault(agent.getId(), 0L).intValue());
            return resp;
        }).collect(Collectors.toList());

        return PageResult.of(resultPage.getTotal(), request.getCurrent(), request.getSize(), list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "agent-cache", allEntries = true)
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
    public List<ToolOption> listTools() {
        // TODO: MCP 模块尚未实现，先返回 mock 数据，后续替换为真实查询
        List<ToolOption> tools = new ArrayList<>();
        ToolOption t1 = new ToolOption();
        t1.setId(1L);
        t1.setName("web_search");
        t1.setDescription("网络搜索：允许 Agent 搜索互联网获取实时信息");
        tools.add(t1);

        ToolOption t2 = new ToolOption();
        t2.setId(2L);
        t2.setName("code_executor");
        t2.setDescription("代码执行：允许 Agent 执行 Python 代码片段");
        tools.add(t2);

        ToolOption t3 = new ToolOption();
        t3.setId(3L);
        t3.setName("file_reader");
        t3.setDescription("文件读取：允许 Agent 读取本地文件内容");
        tools.add(t3);

        return tools;
    }

    // ---------- 私有方法 ----------

    private void saveRelations(Long agentId, List<Long> knowledgeIds, List<Long> toolIds) {
        if (!CollectionUtils.isEmpty(knowledgeIds)) {
            knowledgeRelMapper.batchInsert(agentId, knowledgeIds);
        }
        if (!CollectionUtils.isEmpty(toolIds)) {
            toolRelMapper.batchInsert(agentId, toolIds);
        }
    }
}
