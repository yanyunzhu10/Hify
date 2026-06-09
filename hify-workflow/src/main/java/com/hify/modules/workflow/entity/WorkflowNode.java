package com.hify.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 工作流节点（做什么）。对应表 {@code t_workflow_node}。
 */
@Getter
@Setter
@TableName(value = "t_workflow_node", autoResultMap = true)
public class WorkflowNode extends BaseEntity {

    private Long workflowId;

    /** 工作流内稳定标识（如 classify/router），边用它引用 */
    private String nodeKey;

    /** 节点类型：START/LLM/CONDITION/TOOL/END */
    private String type;

    /** 节点展示名 */
    private String name;

    /** 节点配置（JSON）。t_workflow_node 在 MySQL，JSON 列用 JacksonTypeHandler */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> config;

    /** 执行结果写入的变量名，供后续节点用 {@code {{var}}} 引用 */
    private String outputVariable;
}
