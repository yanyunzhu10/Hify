package com.hify.modules.mcp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * MCP 服务配置。对应表 {@code t_mcp_server}。
 */
@Getter
@Setter
@TableName("t_mcp_server")
public class McpServer extends BaseEntity {

    private String name;
    private String description;
    private String endpoint;
    private Integer enabled;
}
