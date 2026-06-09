package com.hify.modules.workflow.engine.executor;

import com.hify.modules.workflow.config.NodeConfig;
import com.hify.modules.workflow.engine.ExecutionContext;
import com.hify.modules.workflow.entity.WorkflowNode;

/**
 * 节点执行器。每种节点类型一个实现，由 {@link NodeExecutorRegistry} 路由。
 * <p>
 * 执行失败时由本方法内部 catch，不改外层 WorkflowEngine 的控制流。
 * </p>
 */
public interface NodeExecutor {

    /** 这个 Executor 处理的节点类型（对应 t_workflow_node.type） */
    String nodeType();

    /**
     * 执行节点。
     *
     * @param node    当前节点实体
     * @param config  解析后的强类型配置
     * @param ctx     执行上下文（含变量池、用户输入）
     */
    void execute(WorkflowNode node, NodeConfig config, ExecutionContext ctx);
}
