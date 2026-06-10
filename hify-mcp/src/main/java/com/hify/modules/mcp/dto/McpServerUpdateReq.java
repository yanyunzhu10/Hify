package com.hify.modules.mcp.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新 MCP Server 请求。
 */
@Data
public class McpServerUpdateReq {

    @Size(max = 64, message = "名称长度不能超过 64")
    private String name;

    @Size(max = 256, message = "描述长度不能超过 256")
    private String description;

    @Size(max = 500, message = "endpoint 长度不能超过 500")
    private String endpoint;

    private Integer enabled;
}
