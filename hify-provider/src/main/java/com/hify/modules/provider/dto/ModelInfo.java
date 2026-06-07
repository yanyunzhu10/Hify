package com.hify.modules.provider.dto;

import lombok.Data;

/**
 * 供应商 API 返回的单个模型信息，供同步到 t_model_config。
 */
@Data
public class ModelInfo {

    /** 调用 LLM API 时传的 model 值，如 gpt-4o / claude-sonnet-4-6 */
    private String modelId;

    /** 展示名称，优先取 API 返回的 displayName，无则用 modelId */
    private String name;

    public static ModelInfo of(String modelId, String name) {
        ModelInfo info = new ModelInfo();
        info.modelId = modelId;
        info.name = name != null ? name : modelId;
        return info;
    }
}
