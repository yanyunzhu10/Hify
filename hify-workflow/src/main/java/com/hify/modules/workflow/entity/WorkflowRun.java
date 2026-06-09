package com.hify.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 工作流整次执行记录。对应表 {@code t_workflow_run}。
 */
@Getter
@Setter
@TableName("t_workflow_run")
public class WorkflowRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowId;
    private String status;
    private String input;
    private String output;
    private String error;
    private Integer elapsedMs;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
