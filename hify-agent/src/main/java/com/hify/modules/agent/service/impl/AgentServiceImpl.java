package com.hify.modules.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.agent.dto.AgentCreateReq;
import com.hify.modules.agent.dto.AgentResp;
import com.hify.modules.agent.dto.AgentToolBrief;
import com.hify.modules.agent.entity.Agent;
import com.hify.modules.agent.entity.AgentTool;
import com.hify.modules.agent.mapper.AgentMapper;
import com.hify.modules.agent.mapper.AgentToolMapper;
import com.hify.modules.agent.service.AgentService;
import com.hify.modules.provider.service.ProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;
    private final AgentToolMapper agentToolMapper;
    /** 跨模块依赖：通过 provider 模块的 Service 接口校验模型配置，禁止直接访问其 mapper */
    private final ProviderService providerService;

    @Override
    @Transactional
    @CacheEvict(cacheNames = "agent-cache", allEntries = true)
    public AgentResp create(AgentCreateReq req) {
        // 第一步：名称唯一性校验
        checkNameUnique(req.getName(), null);

        // 第二步：跨模块校验 modelConfigId（走 ProviderService 接口，不直接查 model_config mapper）
        if (!providerService.existsEnabledModelConfig(req.getModelConfigId())) {
            throw new BizException(ErrorCode.MODEL_CONFIG_NOT_FOUND,
                    "模型配置不存在或未启用: " + req.getModelConfigId());
        }

        // 第三步：事务内 insert agent + 批量 insert agent_tool
        Agent agent = new Agent();
        agent.setName(req.getName());
        agent.setDescription(req.getDescription());
        agent.setSystemPrompt(req.getSystemPrompt());
        agent.setModelConfigId(req.getModelConfigId());
        agent.setTemperature(req.getTemperature());
        agent.setMaxTokens(req.getMaxTokens());
        agent.setMaxContextTurns(req.getMaxContextTurns());
        agent.setEnabled(req.getEnabled() != null ? req.getEnabled() : Boolean.TRUE);
        agentMapper.insert(agent);

        List<AgentToolBrief> toolBriefs = saveAgentTools(agent.getId(), req.getToolIds());

        log.info("Agent 创建成功 id={}, name={}, modelConfigId={}, toolCount={}",
                agent.getId(), agent.getName(), agent.getModelConfigId(), toolBriefs.size());

        // 第四步：@CacheEvict 已在方法级清除列表缓存，返回详情
        return AgentResp.from(agent, toolBriefs);
    }

    // ============================================================
    // 内部方法
    // ============================================================

    /**
     * 校验 Agent 名称唯一性（查询带逻辑删除条件，由 MyBatis-Plus 自动追加 deleted=0）。
     *
     * @param name      Agent 名称
     * @param excludeId 更新场景排除自身，创建传 null
     */
    private void checkNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<Agent>()
                .eq(Agent::getName, name);
        if (excludeId != null) {
            wrapper.ne(Agent::getId, excludeId);
        }
        if (agentMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.AGENT_NAME_EXISTS, "Agent 名称已存在: " + name);
        }
    }

    /**
     * 批量保存 Agent 与工具的关联。空列表直接返回，避免无谓写入。
     *
     * @return 关联工具的 Brief 列表（仅含 toolId，工具名由调用方按需富化）
     */
    private List<AgentToolBrief> saveAgentTools(Long agentId, List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return Collections.emptyList();
        }
        for (Long toolId : toolIds) {
            AgentTool agentTool = new AgentTool();
            agentTool.setAgentId(agentId);
            agentTool.setToolId(toolId);
            agentToolMapper.insert(agentTool);
        }
        return toolIds.stream()
                .map(toolId -> AgentToolBrief.of(toolId, null))
                .toList();
    }
}
