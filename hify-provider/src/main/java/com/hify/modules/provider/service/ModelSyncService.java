package com.hify.modules.provider.service;

import com.hify.modules.provider.dto.ModelInfo;

import java.util.List;

/**
 * 模型同步：连通性测试成功后，以 API 返回的模型列表为真源，
 * 全量同步到 t_model_config（新增/更新/下线）。
 */
public interface ModelSyncService {

    /**
     * 同步指定供应商的模型列表。
     * <ul>
     *   <li>新模型（modelId 不在库中）→ INSERT enabled=1</li>
     *   <li>已有模型（modelId 存在）→ UPDATE name（展示名可能变化）</li>
     *   <li>库中有但 API 未返回的模型 → UPDATE enabled=0（标记下线）</li>
     * </ul>
     *
     * @param providerId 供应商 ID
     * @param models     API 返回的模型列表
     * @return 同步后的模型数量（启用状态的）
     */
    int sync(Long providerId, List<ModelInfo> models);
}
