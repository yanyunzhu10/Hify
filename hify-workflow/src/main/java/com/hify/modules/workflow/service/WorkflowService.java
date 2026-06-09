package com.hify.modules.workflow.service;

import com.hify.common.web.PageResult;
import com.hify.modules.workflow.dto.WorkflowCreateReq;
import com.hify.modules.workflow.dto.WorkflowResp;
import com.hify.modules.workflow.dto.WorkflowUpdateReq;

public interface WorkflowService {

    /**
     * 创建工作流（含 nodes + edges，同一事务内写入三张表）。
     */
    WorkflowResp create(WorkflowCreateReq req);

    /**
     * 分页查询工作流列表（不含 nodes/edges）。
     */
    PageResult<WorkflowResp> page(int page, int size, String name);

    /**
     * 查询工作流详情（含完整 nodes + edges）。
     */
    WorkflowResp getById(Long id);

    /**
     * 更新工作流：逻辑删除旧 nodes/edges → 重插，整体替换。
     */
    WorkflowResp update(Long id, WorkflowUpdateReq req);

    /**
     * 逻辑删除工作流 + 关联的 nodes + edges。
     */
    void delete(Long id);
}
