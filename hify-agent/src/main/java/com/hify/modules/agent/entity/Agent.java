package com.hify.modules.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Agent 配置。
 * <p>
 * 对应表 {@code t_agent}。绑定一个模型配置（{@code model_config_id} 指向 t_model_config），
 * 模型参数（temperature / max_tokens / max_context_turns）拆为独立列，便于设默认值与校验。
 * 关联的 MCP 工具通过 {@code t_agent_tool} 关联表维护，见 {@link AgentTool}。
 * </p>
 */
@Getter
@Setter
@TableName("t_agent")
public class Agent extends BaseEntity {

    /** Agent 名称，全局唯一（DB UNIQUE 约束 agent_name_key） */
    private String name;

    /** 描述 */
    private String description;

    /** 系统提示词（角色指令，可以很长） */
    private String systemPrompt;

    /** 绑定的模型配置 t_model_config.id */
    private Long modelConfigId;

    /** 关联的知识库 t_knowledge_base.id（可空，未绑定知识库时为 null） */
    private Long knowledgeBaseId;

    /** 关联的工作流 t_workflow.id（可空，未绑定工作流时为 null） */
    private Long workflowId;

    /** 采样温度 0.00~1.00 */
    private BigDecimal temperature;

    /** 单次回复最大 token 数 */
    private Integer maxTokens;

    /** 保留最近几轮上下文 */
    private Integer maxContextTurns;

    /** 是否启用：0=不可用 1=可用 */
    private Integer enabled;
}
