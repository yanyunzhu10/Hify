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
 * 更新 Agent 请求。全量更新：未传字段以请求值为准（含关联工具的全删全插）。
 */
@Data
public class AgentUpdateReq {

    @NotBlank(message = "Agent 名称不能为空")
    @Size(max = 100, message = "名称长度不能超过 100")
    private String name;

    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    @NotBlank(message = "系统提示词不能为空")
    private String systemPrompt;

    @NotNull(message = "必须绑定模型配置")
    private Long modelConfigId;

    /** 关联的知识库 id（t_knowledge_base.id），可空；传 null 表示不绑定/解绑 */
    private Long knowledgeBaseId;

    @DecimalMin(value = "0.00", message = "temperature 不能小于 0")
    @DecimalMax(value = "1.00", message = "temperature 不能大于 1")
    private BigDecimal temperature;

    @Min(value = 1, message = "maxTokens 至少为 1")
    private Integer maxTokens;

    @Min(value = 0, message = "maxContextTurns 不能为负")
    private Integer maxContextTurns;

    /** 是否启用：0=不可用 1=可用 */
    private Integer enabled;

    /** 关联的 MCP 工具 id 列表，全量覆盖（全删全插）；传空列表表示清空关联 */
    private List<Long> toolIds;
}
