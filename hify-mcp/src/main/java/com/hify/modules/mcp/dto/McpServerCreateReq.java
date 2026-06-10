package com.hify.modules.mcp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 MCP Server 请求。
 */
@Data
public class McpServerCreateReq {

    @NotBlank(message = "服务名称不能为空")
    @Size(max = 64, message = "名称长度不能超过 64")
    private String name;

    @Size(max = 256, message = "描述长度不能超过 256")
    private String description;

    @NotBlank(message = "endpoint 不能为空")
    @Size(max = 500, message = "endpoint 长度不能超过 500")
    private String endpoint;

    /** 是否启用，默认 1 */
    private Integer enabled;
}
