package com.hify.modules.workflow.dto;

import com.hify.modules.workflow.entity.Workflow;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流响应（列表场景不带 nodes/edges，详情场景带完整图）。
 */
@Data
public class WorkflowResp {

    private Long id;
    private String name;
    private String description;
    private String status;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 详情场景：完整节点列表（列表场景 null） */
    private List<NodeResp> nodes;
    /** 详情场景：完整连线列表（列表场景 null） */
    private List<EdgeResp> edges;

    public static WorkflowResp from(Workflow wf) {
        WorkflowResp r = new WorkflowResp();
        r.id = wf.getId();
        r.name = wf.getName();
        r.description = wf.getDescription();
        r.status = wf.getStatus();
        r.enabled = wf.getEnabled();
        r.createdAt = wf.getCreatedAt();
        r.updatedAt = wf.getUpdatedAt();
        return r;
    }
}
