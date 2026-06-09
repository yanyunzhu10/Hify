package com.hify.modules.agent.dto;

import com.hify.modules.agent.entity.Agent;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 详情/列表响应。
 * <p>
 * {@link #from(Agent)} 仅拷贝主表字段；关联工具（{@link #tools}）与模型名（{@link #modelName}）
 * 需跨模块查询，由 Service 富化填充，不在 DTO 工厂内做 IO。
 * </p>
 */
@Data
public class AgentResp {

    private Long id;
    private String name;
    private String description;
    private String systemPrompt;
    private Long modelConfigId;
    /** 关联的知识库 id，未绑定为 null */
    private Long knowledgeBaseId;
    /** 关联的工作流 id，未绑定为 null */
    private Long workflowId;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Integer maxContextTurns;
    /** 是否启用：0=不可用 1=可用 */
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 绑定模型的展示名，由 model 模块富化填充；未富化时为 null */
    private String modelName;

    /** 关联的 MCP 工具列表（详情场景填充；列表场景通常为空，只给数量） */
    private List<AgentToolBrief> tools;

    /** 关联工具数量（列表场景填充，避免传完整工具列表） */
    private int toolCount;

    /**
     * 由实体构建响应，仅拷贝主表字段。关联与富化字段由调用方后续设置。
     */
    public static AgentResp from(Agent agent) {
        AgentResp r = new AgentResp();
        r.id = agent.getId();
        r.name = agent.getName();
        r.description = agent.getDescription();
        r.systemPrompt = agent.getSystemPrompt();
        r.modelConfigId = agent.getModelConfigId();
        r.knowledgeBaseId = agent.getKnowledgeBaseId();
        r.workflowId = agent.getWorkflowId();
        r.temperature = agent.getTemperature();
        r.maxTokens = agent.getMaxTokens();
        r.maxContextTurns = agent.getMaxContextTurns();
        r.enabled = agent.getEnabled();
        r.createdAt = agent.getCreatedAt();
        r.updatedAt = agent.getUpdatedAt();
        return r;
    }

    /**
     * 由实体 + 关联工具构建响应。
     */
    public static AgentResp from(Agent agent, List<AgentToolBrief> tools) {
        AgentResp r = from(agent);
        r.tools = tools;
        return r;
    }
}
