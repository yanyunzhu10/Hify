package com.hify.modules.provider.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 嵌入 ProviderResp 的健康状态摘要。
 */
@Data
public class ProviderHealthBrief {

    private String status;
    private LocalDateTime lastCheckAt;
    private LocalDateTime lastSuccessAt;
    private Integer failCount;
    private Integer latencyMs;
    private String errorMessage;
}
