package com.hify.modules.workflow.entity;

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
 * 工作流节点执行记录。对应表 {@code t_workflow_node_run}。
 */
@Getter
@Setter
@TableName(value = "t_workflow_node_run", autoResultMap = true)
public class WorkflowNodeRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowRunId;
    private String nodeKey;
    private String nodeType;
    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> outputs;

    private String error;
    private Integer elapsedMs;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
