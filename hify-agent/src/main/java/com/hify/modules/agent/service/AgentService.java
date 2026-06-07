package com.hify.modules.agent.service;

import com.hify.common.web.PageResult;
import com.hify.modules.agent.dto.AgentCreateReq;
import com.hify.modules.agent.dto.AgentQueryReq;
import com.hify.modules.agent.dto.AgentResp;
import com.hify.modules.agent.dto.AgentUpdateReq;

import java.util.List;

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

    /**
     * 查询 Agent 详情，含关联工具列表（toolName 待 mcp 模块就绪后富化）。
     *
     * @param id Agent id
     * @return Agent 详情
     */
    AgentResp get(Long id);

    /**
     * 分页查询 Agent 列表，每条带关联工具数量（不带完整工具列表）。
     *
     * @param req 分页 + 筛选条件
     * @return 分页结果
     */
    PageResult<AgentResp> page(AgentQueryReq req);

    /**
     * 更新 Agent 基本信息（不含工具关联）。校验存在性、名称唯一、模型配置有效。
     *
     * @param id  Agent id
     * @param req 更新请求
     * @return 更新后的 Agent 详情
     */
    AgentResp update(Long id, AgentUpdateReq req);

    /**
     * 独立更新 Agent 的工具关联（全删全插）。
     *
     * @param id      Agent id
     * @param toolIds 新的工具 id 列表，传空表示清空关联
     */
    void updateTools(Long id, List<Long> toolIds);

    /**
     * 删除 Agent：事务内物理删关联工具表、逻辑删主表。
     *
     * @param id Agent id
     */
    void delete(Long id);
}
