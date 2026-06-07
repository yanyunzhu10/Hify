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

    /**
     * 校验模型配置是否存在且已启用。供 agent 等模块绑定模型前做跨模块校验，
     * 避免直接访问 model_config 的数据访问层。
     *
     * @param modelConfigId 模型配置 id（t_model_config.id）
     * @return true 表示存在且 enabled=1
     */
    boolean existsEnabledModelConfig(Long modelConfigId);

    /**
     * 查询模型配置的展示名。供 agent 等模块富化 modelName 字段。
     *
     * @param modelConfigId 模型配置 id
     * @return 展示名（如 GPT-4o），不存在返回 null
     */
    String getModelName(Long modelConfigId);
}
