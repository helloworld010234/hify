package com.hify.modules.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.common.service.agent.AgentBindingApi;
import com.hify.modules.agent.entity.AgentToolRel;
import com.hify.modules.agent.mapper.AgentToolRelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 绑定关系查询实现
 */
@Service
@RequiredArgsConstructor
public class AgentBindingServiceImpl implements AgentBindingApi {

    private final AgentToolRelMapper toolRelMapper;

    @Override
    public boolean hasAgentBoundToTools(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return false;
        }
        return toolRelMapper.selectCount(
                new LambdaQueryWrapper<AgentToolRel>()
                        .in(AgentToolRel::getToolId, toolIds)
        ) > 0;
    }
}
