package com.hify.modules.workflow.constant;

/**
 * 工作流节点类型。
 */
public enum NodeType {
    /** 开始节点（图入口） */
    START,
    /** 调 LLM */
    LLM,
    /** 条件判断（分支） */
    CONDITION,
    /** 调 MCP 工具 */
    TOOL,
    /** 调外部 API */
    API_CALL,
    /** 知识库检索（RAG） */
    KNOWLEDGE,
    /** 结束节点 */
    END
}
