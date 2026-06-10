package com.hify.modules.mcp.dto;

import lombok.Data;

import java.util.List;

/**
 * MCP 连通性测试结果。
 */
@Data
public class McpTestResult {

    private boolean success;
    private String errorMessage;
    /** 测试时发现的工具数量 */
    private int toolCount;
    private List<McpToolBrief> tools;
}
