package com.hify.modules.workflow.engine;

/**
 * 工作流执行状态。
 */
public enum ExecutionStatus {
    /** 运行中 */
    RUNNING,
    /** 正常结束 */
    COMPLETED,
    /** 执行失败 */
    FAILED,
    /** 执行超时 */
    TIMEOUT
}
