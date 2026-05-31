package com.hify.modules.provider.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ProviderResp {

    private Long id;
    private String name;
    private String type;
    private String baseUrl;
    private Map<String, Object> authConfig;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 关联的模型配置列表 */
    private List<ModelConfigBrief> modelConfigs;

    /** 已启用的模型数量 */
    private int modelCount;

    /** 健康状态（可能为 null 若从未探测） */
    private ProviderHealthBrief health;
}
