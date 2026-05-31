package com.hify.modules.provider.service;

import com.hify.common.web.PageResult;
import com.hify.modules.provider.dto.ProviderCreateReq;
import com.hify.modules.provider.dto.ProviderResp;
import com.hify.modules.provider.dto.ProviderUpdateReq;

import java.util.List;

public interface ProviderService {

    /** 创建供应商，校验名称唯一性。 */
    ProviderResp create(ProviderCreateReq req);

    /** 更新供应商。 */
    ProviderResp update(Long id, ProviderUpdateReq req);

    /** 逻辑删除供应商。 */
    void delete(Long id);

    /** 查询供应商详情，包含关联的模型配置列表和健康状态。 */
    ProviderResp get(Long id);

    /** 列表查询（不分页），支持按 type 和 enabled 筛选。 */
    List<ProviderResp> list(String type, Integer enabled);

    /** 分页查询，支持按 type 和 enabled 筛选。 */
    PageResult<ProviderResp> page(int page, int size, String type, Integer enabled);
}
