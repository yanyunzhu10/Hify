package com.hify.modules.agent.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建 Agent 请求。
 */
@Data
public class AgentCreateReq {

    @NotBlank(message = "Agent 名称不能为空")
    @Size(max = 100, message = "名称长度不能超过 100")
    private String name;

    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    @NotBlank(message = "系统提示词不能为空")
    private String systemPrompt;

    @NotNull(message = "必须绑定模型配置")
    private Long modelConfigId;

    /** 关联的知识库 id（t_knowledge_base.id），可空 */
    private Long knowledgeBaseId;

    /** 关联的工作流 id（t_workflow.id），可空 */
    private Long workflowId;

    /** 采样温度 0.00~1.00，不传则用 DB 默认值 0.70 */
    @DecimalMin(value = "0.00", message = "temperature 不能小于 0")
    @DecimalMax(value = "1.00", message = "temperature 不能大于 1")
    private BigDecimal temperature;

    /** 单次回复最大 token 数，不传则用 DB 默认值 2048 */
    @Min(value = 1, message = "maxTokens 至少为 1")
    private Integer maxTokens;

    /** 保留最近几轮上下文，不传则用 DB 默认值 10 */
    @Min(value = 0, message = "maxContextTurns 不能为负")
    private Integer maxContextTurns;

    /** 是否启用：0=不可用 1=可用，不传默认 1 */
    private Integer enabled;

    /** 关联的 MCP 工具 id 列表（t_mcp_server.id），可为空 */
    private List<Long> toolIds;
}
