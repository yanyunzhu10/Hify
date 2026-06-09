package com.hify.modules.workflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建工作流请求体（含完整图结构：nodes + edges）。
 */
@Data
public class WorkflowCreateReq {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 500)
    private String description;

    @Valid
    private List<NodeReq> nodes;

    @Valid
    private List<EdgeReq> edges;

    /** 可选：创建后直接发布 */
    private String status;
}
