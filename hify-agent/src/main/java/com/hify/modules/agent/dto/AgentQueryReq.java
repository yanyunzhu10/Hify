package com.hify.modules.agent.dto;

import lombok.Data;

/**
 * Agent 分页查询请求。
 * <p>字段名 page/size 与 {@link com.hify.common.web.PageResult} 对齐，避免分页参数命名分歧。</p>
 */
@Data
public class AgentQueryReq {

    /** 页码，从 1 开始 */
    private int page = 1;

    /** 每页条数 */
    private int size = 20;

    /** 按名称模糊筛选，可空 */
    private String name;

    /** 按启用状态筛选，可空 */
    private Boolean enabled;
}
