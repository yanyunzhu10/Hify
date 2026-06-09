package com.hify.modules.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建/更新工作流时的连线定义。
 */
@Data
public class EdgeReq {

    @NotBlank
    @Size(max = 64)
    private String sourceNodeKey;

    @NotBlank
    @Size(max = 64)
    private String targetNodeKey;

    /** 跳转条件：null=无条件直接走，非 null=条件满足时走此边 */
    @Size(max = 255)
    private String conditionExpr;
}
