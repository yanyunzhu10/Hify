package com.hify.modules.agent.dto;

import lombok.Data;

import java.util.List;

/**
 * 独立更新 Agent 工具关联的请求。
 */
@Data
public class AgentToolsUpdateReq {

    /** 新的工具 id 列表，全量覆盖（全删全插）；传空列表表示清空关联 */
    private List<Long> toolIds;
}
