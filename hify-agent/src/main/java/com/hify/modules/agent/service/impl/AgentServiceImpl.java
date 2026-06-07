package com.hify.modules.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResult;
import com.hify.modules.agent.dto.AgentCreateReq;
import com.hify.modules.agent.dto.AgentQueryReq;
import com.hify.modules.agent.dto.AgentResp;
import com.hify.modules.agent.dto.AgentToolBrief;
import com.hify.modules.agent.dto.AgentUpdateReq;
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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;
    private final AgentToolMapper agentToolMapper;
    /** 跨模块依赖：通过 provider 模块的 Service 接口校验模型配置，禁止直接访问其 mapper */
    private final ProviderService providerService;

    // ============================================================
    // 增删改（触发缓存失效）
    // ============================================================

    @Override
    @Transactional
    @CacheEvict(cacheNames = "agent-cache", allEntries = true)
    public AgentResp create(AgentCreateReq req) {
        // 第一步：名称唯一性校验
        checkNameUnique(req.getName(), null);

        // 第二步：跨模块校验 modelConfigId（走 ProviderService 接口，不直接查 model_config mapper）
        requireModelConfig(req.getModelConfigId());

        // 第三步：事务内 insert agent + 批量 insert agent_tool
        Agent agent = new Agent();
        agent.setName(req.getName());
        agent.setDescription(req.getDescription());
        agent.setSystemPrompt(req.getSystemPrompt());
        agent.setModelConfigId(req.getModelConfigId());
        agent.setTemperature(req.getTemperature());
        agent.setMaxTokens(req.getMaxTokens());
        agent.setMaxContextTurns(req.getMaxContextTurns());
        agent.setEnabled(req.getEnabled() != null ? req.getEnabled() : 1);
        agentMapper.insert(agent);

        List<AgentToolBrief> toolBriefs = insertAgentTools(agent.getId(), req.getToolIds());

        log.info("Agent 创建成功 id={}, name={}, modelConfigId={}, toolCount={}",
                agent.getId(), agent.getName(), agent.getModelConfigId(), toolBriefs.size());

        AgentResp resp = AgentResp.from(agent, toolBriefs);
        resp.setToolCount(toolBriefs.size());
        return resp;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "agent-cache", allEntries = true)
    public AgentResp update(Long id, AgentUpdateReq req) {
        Agent agent = requireExists(id);
        checkNameUnique(req.getName(), id);
        requireModelConfig(req.getModelConfigId());

        agent.setName(req.getName());
        agent.setDescription(req.getDescription());
        agent.setSystemPrompt(req.getSystemPrompt());
        agent.setModelConfigId(req.getModelConfigId());
        agent.setTemperature(req.getTemperature());
        agent.setMaxTokens(req.getMaxTokens());
        agent.setMaxContextTurns(req.getMaxContextTurns());
        if (req.getEnabled() != null) {
            agent.setEnabled(req.getEnabled());
        }
        agentMapper.updateById(agent);

        log.info("Agent 更新成功 id={}, name={}", agent.getId(), agent.getName());

        // 更新基本信息不动工具关联，返回详情时带上现有工具
        List<AgentToolBrief> tools = listToolBriefs(id);
        AgentResp resp = AgentResp.from(agent, tools);
        resp.setToolCount(tools.size());
        return resp;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "agent-cache", allEntries = true)
    public void updateTools(Long id, List<Long> toolIds) {
        requireExists(id);
        // 全删全插：先清空该 agent 的所有关联，再批量插入新列表
        agentToolMapper.delete(new LambdaQueryWrapper<AgentTool>().eq(AgentTool::getAgentId, id));
        List<AgentToolBrief> tools = insertAgentTools(id, toolIds);
        log.info("Agent 工具关联更新成功 id={}, toolCount={}", id, tools.size());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "agent-cache", allEntries = true)
    public void delete(Long id) {
        requireExists(id);
        // 关联表物理删除（无 deleted 字段），主表逻辑删除（MyBatis-Plus @TableLogic 自动转 UPDATE）
        agentToolMapper.delete(new LambdaQueryWrapper<AgentTool>().eq(AgentTool::getAgentId, id));
        agentMapper.deleteById(id);
        log.info("Agent 删除成功 id={}（关联工具已级联物理删除，主表逻辑删除）", id);
    }

    // ============================================================
    // 查
    // ============================================================

    @Override
    public AgentResp get(Long id) {
        Agent agent = requireExists(id);
        List<AgentToolBrief> tools = listToolBriefs(id);
        AgentResp resp = AgentResp.from(agent, tools);
        resp.setToolCount(tools.size());
        return resp;
    }

    @Override
    public PageResult<AgentResp> page(AgentQueryReq req) {
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<Agent>();
        if (req.getName() != null && !req.getName().isBlank()) {
            wrapper.like(Agent::getName, req.getName());
        }
        if (req.getEnabled() != null) {
            wrapper.eq(Agent::getEnabled, req.getEnabled());
        }
        wrapper.orderByDesc(Agent::getCreatedAt);

        Page<Agent> p = new Page<>(req.getPage(), req.getSize());
        Page<Agent> result = agentMapper.selectPage(p, wrapper);

        List<AgentResp> list = result.getRecords().stream().map(AgentResp::from).toList();
        fillToolCount(list);

        return PageResult.ok(list, result.getTotal(), req.getPage(), req.getSize());
    }

    // ============================================================
    // 内部方法
    // ============================================================

    /** 校验名称唯一性（MyBatis-Plus 自动追加 deleted=0）。excludeId 为更新场景排除自身。 */
    private void checkNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<Agent>().eq(Agent::getName, name);
        if (excludeId != null) {
            wrapper.ne(Agent::getId, excludeId);
        }
        if (agentMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.AGENT_NAME_EXISTS, "Agent 名称已存在: " + name);
        }
    }

    private Agent requireExists(Long id) {
        Agent agent = agentMapper.selectById(id);
        if (agent == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND, "Agent 不存在: " + id);
        }
        return agent;
    }

    /** 跨模块校验：模型配置必须存在且启用。 */
    private void requireModelConfig(Long modelConfigId) {
        if (!providerService.existsEnabledModelConfig(modelConfigId)) {
            throw new BizException(ErrorCode.MODEL_CONFIG_NOT_FOUND,
                    "模型配置不存在或未启用: " + modelConfigId);
        }
    }

    /**
     * 批量插入 Agent-工具关联。空列表直接返回。
     *
     * @return 关联工具 Brief 列表（toolName 待 mcp 模块就绪后富化）
     */
    private List<AgentToolBrief> insertAgentTools(Long agentId, List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return Collections.emptyList();
        }
        for (Long toolId : toolIds) {
            AgentTool agentTool = new AgentTool();
            agentTool.setAgentId(agentId);
            agentTool.setToolId(toolId);
            agentToolMapper.insert(agentTool);
        }
        return toolIds.stream().map(toolId -> AgentToolBrief.of(toolId, null)).toList();
    }

    /** 查询单个 Agent 的关联工具 Brief 列表。 */
    private List<AgentToolBrief> listToolBriefs(Long agentId) {
        List<AgentTool> tools = agentToolMapper.selectList(
                new LambdaQueryWrapper<AgentTool>().eq(AgentTool::getAgentId, agentId));
        return tools.stream().map(t -> AgentToolBrief.of(t.getToolId(), null)).toList();
    }

    /** 列表场景批量填充工具数量，一次 IN 查询避免 N+1。 */
    private void fillToolCount(List<AgentResp> list) {
        if (list.isEmpty()) {
            return;
        }
        List<Long> agentIds = list.stream().map(AgentResp::getId).toList();
        List<AgentTool> all = agentToolMapper.selectList(
                new LambdaQueryWrapper<AgentTool>().in(AgentTool::getAgentId, agentIds));
        Map<Long, Long> countMap = all.stream()
                .collect(Collectors.groupingBy(AgentTool::getAgentId, Collectors.counting()));
        for (AgentResp resp : list) {
            resp.setToolCount(countMap.getOrDefault(resp.getId(), 0L).intValue());
        }
    }
}
