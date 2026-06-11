package com.hify.modules.mcp.dto;

import lombok.Data;

/**
 * 调试工具调用结果。
 */
@Data
public class McpDebugResp {

    /** 工具返回文本 */
    private String result;

    /** 执行耗时（毫秒） */
    private int elapsedMs;
}
