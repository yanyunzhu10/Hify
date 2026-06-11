package com.hify.modules.mcp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 调试工具调用请求。
 */
@Data
public class McpDebugReq {

    @NotBlank(message = "工具名不能为空")
    private String toolName;

    /** 调用参数（key→value），不传默认空 Map */
    private Map<String, Object> arguments;
}
