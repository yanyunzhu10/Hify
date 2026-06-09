package com.hify.modules.workflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 更新工作流：直接替换整个图结构（逻辑删除旧 nodes/edges + 重插）。
 */
@Data
public class WorkflowUpdateReq {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 500)
    private String description;

    @Valid
    private List<NodeReq> nodes;

    @Valid
    private List<EdgeReq> edges;

    private String status;
    private Integer enabled;
}
