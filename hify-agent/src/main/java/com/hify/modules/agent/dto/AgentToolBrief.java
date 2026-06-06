package com.hify.modules.agent.dto;

import lombok.Data;

/**
 * Agent 关联工具的精简视图，用于嵌套在 {@link AgentResp} 中。
 */
@Data
public class AgentToolBrief {

    /** MCP 工具 id（t_mcp_server.id） */
    private Long toolId;

    /** 工具名称，由 mcp 模块富化填充；未富化时为 null */
    private String toolName;

    public static AgentToolBrief of(Long toolId, String toolName) {
        AgentToolBrief b = new AgentToolBrief();
        b.toolId = toolId;
        b.toolName = toolName;
        return b;
    }
}
