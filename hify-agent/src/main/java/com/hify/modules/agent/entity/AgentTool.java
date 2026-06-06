package com.hify.modules.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Agent 与 MCP 工具的关联（M:N）。
 * <p>
 * 对应表 {@code t_agent_tool}。关联表为硬删除，无 {@code updated_at} / {@code deleted} 字段，
 * 故不继承 {@link com.hify.common.entity.BaseEntity}；解除关联即物理删除该行。
 * 唯一约束 {@code uk_agent_tool(agent_id, tool_id)} 防止重复关联。
 * </p>
 */
@Getter
@Setter
@TableName("t_agent_tool")
public class AgentTool {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的 Agent t_agent.id */
    private Long agentId;

    /** 关联的 MCP 工具 t_mcp_server.id */
    private Long toolId;

    /** 关联建立时间（仅插入时写入，无更新） */
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
}
