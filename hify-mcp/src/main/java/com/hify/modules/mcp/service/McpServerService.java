package com.hify.modules.mcp.service;

import com.hify.common.web.PageResult;
import com.hify.modules.mcp.dto.McpServerCreateReq;
import com.hify.modules.mcp.dto.McpServerResp;
import com.hify.modules.mcp.dto.McpServerUpdateReq;
import com.hify.modules.mcp.dto.McpTestResult;

public interface McpServerService {

    McpServerResp create(McpServerCreateReq req);
    PageResult<McpServerResp> page(int page, int size, String name);
    McpServerResp get(Long id);
    McpServerResp update(Long id, McpServerUpdateReq req);
    void delete(Long id);

    /** 连通性测试：调 tools/list，将结果同步到 mcp_tool 表 */
    McpTestResult testConnection(Long id);

    /** 校验 MCP Server 是否已启用（供 agent 等模块跨模块调用） */
    boolean existsEnabled(Long serverId);
}
