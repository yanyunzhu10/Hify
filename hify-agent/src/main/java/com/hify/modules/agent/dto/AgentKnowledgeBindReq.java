package com.hify.modules.agent.dto;

import lombok.Data;

/**
 * 绑定/解绑知识库请求（轻量，不走全量更新）。
 */
@Data
public class AgentKnowledgeBindReq {

    /** 知识库 id；传 null 表示解绑知识库 */
    private Long knowledgeBaseId;

    /** 工作流 id；传 null 表示解绑工作流 */
    private Long workflowId;
}
