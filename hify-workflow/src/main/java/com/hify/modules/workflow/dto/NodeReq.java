package com.hify.modules.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 创建/更新工作流时的节点定义。
 */
@Data
public class NodeReq {

    /** 工作流内稳定标识（如 classify/router），边用它引用 */
    @NotBlank
    @Size(max = 64)
    private String nodeKey;

    /** 节点类型：START/LLM/CONDITION/TOOL/END */
    @NotBlank
    private String type;

    /** 节点展示名 */
    @NotBlank
    @Size(max = 100)
    private String name;

    /** 节点配置（JSON），结构按 type 不同 */
    private Map<String, Object> config;

    /** 执行结果写入的变量名 */
    @Size(max = 64)
    private String outputVariable;
}
