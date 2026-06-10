package com.hify.modules.mcp.service;

import com.hify.modules.mcp.entity.McpServer;
import com.hify.modules.mcp.entity.McpTool;

import java.util.List;
import java.util.Map;

/**
 * MCP 客户端服务：封装 SDK 的连接/工具列表/调用。
 */
public interface McpClientService {

    /**
     * 获取 MCP Server 上的全部工具列表（调 tools/list）。
     * @return McpTool 列表（不含 mcpServerId，由调用方设置）
     */
    List<McpTool> listTools(McpServer server);

    /**
     * 调用指定工具。
     * @param server    MCP Server
     * @param toolName  工具名称
     * @param arguments 参数（key→value）
     * @return 工具执行结果文本
     */
    String callTool(McpServer server, String toolName, Map<String, Object> arguments);
}
