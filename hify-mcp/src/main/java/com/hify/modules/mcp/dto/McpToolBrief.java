package com.hify.modules.mcp.dto;

import com.hify.modules.mcp.entity.McpTool;
import lombok.Data;

import java.util.Map;

/**
 * MCP 工具简要信息。
 */
@Data
public class McpToolBrief {

    private Long id;
    private String name;
    private String description;
    private Map<String, Object> inputSchema;

    public static McpToolBrief from(McpTool t) {
        McpToolBrief b = new McpToolBrief();
        b.id = t.getId();
        b.name = t.getName();
        b.description = t.getDescription();
        b.inputSchema = t.getInputSchema();
        return b;
    }
}
