package com.hify.modules.agent.service;

import com.hify.modules.agent.dto.AgentCreateReq;
import com.hify.modules.agent.dto.AgentResp;

public interface AgentService {

    /**
     * 创建 Agent。
     * <p>
     * 校验名称唯一性、跨模块校验绑定的模型配置，事务内插入主表与关联工具表。
     * </p>
     *
     * @param req 创建请求
     * @return 创建后的 Agent 详情
     */
    AgentResp create(AgentCreateReq req);
}
