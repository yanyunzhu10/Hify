package com.hify.modules.workflow.dto;

import com.hify.modules.workflow.entity.WorkflowEdge;
import lombok.Data;

/**
 * 连线响应。
 */
@Data
public class EdgeResp {

    private Long id;
    private String sourceNodeKey;
    private String targetNodeKey;
    private String conditionExpr;

    public static EdgeResp from(WorkflowEdge e) {
        EdgeResp r = new EdgeResp();
        r.id = e.getId();
        r.sourceNodeKey = e.getSourceNodeKey();
        r.targetNodeKey = e.getTargetNodeKey();
        r.conditionExpr = e.getConditionExpr();
        return r;
    }
}
