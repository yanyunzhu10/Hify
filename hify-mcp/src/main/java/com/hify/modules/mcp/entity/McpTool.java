package com.hify.modules.mcp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * MCP 工具列表。对应表 {@code t_mcp_tool}。
 * <p>inputSchema 为 MySQL JSON 列，用 JacksonTypeHandler。</p>
 */
@Getter
@Setter
@TableName(value = "t_mcp_tool", autoResultMap = true)
public class McpTool {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long mcpServerId;
    private String name;
    private String description;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> inputSchema;

    private LocalDateTime createdAt;
}
