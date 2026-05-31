package com.hify.modules.provider.dto;

import lombok.Data;

/**
 * 嵌入 ProviderResp 的模型配置摘要，避免暴露完整实体。
 */
@Data
public class ModelConfigBrief {

    private Long id;
    private String name;
    private String modelId;
    private Integer contextSize;
    private Integer enabled;
}
