package com.hify.modules.mcp.dto;

import com.hify.modules.mcp.entity.McpServer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MCP Server 响应（含工具列表）。
 */
@Data
public class McpServerResp {

    private Long id;
    private String name;
    private String description;
    private String endpoint;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 连通性测试后发现并存入的工具列表 */
    private List<McpToolBrief> tools;

    public static McpServerResp from(McpServer s) {
        McpServerResp r = new McpServerResp();
        r.id = s.getId();
        r.name = s.getName();
        r.description = s.getDescription();
        r.endpoint = s.getEndpoint();
        r.enabled = s.getEnabled();
        r.createdAt = s.getCreatedAt();
        r.updatedAt = s.getUpdatedAt();
        return r;
    }
}
