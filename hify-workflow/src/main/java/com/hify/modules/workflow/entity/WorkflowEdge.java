package com.hify.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 工作流连线（做完去哪）。对应表 {@code t_workflow_edge}。
 */
@Getter
@Setter
@TableName("t_workflow_edge")
public class WorkflowEdge extends BaseEntity {

    private Long workflowId;

    /** 源节点 node_key */
    private String sourceNodeKey;

    /** 目标节点 node_key */
    private String targetNodeKey;

    /** 跳转条件（condition 是 MySQL 保留字，列名用 condition_expr）；null=无条件直接走 */
    private String conditionExpr;
}
