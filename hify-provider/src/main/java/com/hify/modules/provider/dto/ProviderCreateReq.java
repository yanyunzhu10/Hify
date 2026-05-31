package com.hify.modules.provider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class ProviderCreateReq {

    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 100, message = "名称长度不能超过 100")
    private String name;

    @NotBlank(message = "供应商类型不能为空")
    private String type;

    @NotBlank(message = "Base URL 不能为空")
    private String baseUrl;

    /** 鉴权配置（JSON），如 {"apiKey":"sk-..."}，Ollama 可为空 */
    private Map<String, Object> authConfig;

    /** 0=禁用 1=启用 */
    private Integer enabled;
}
