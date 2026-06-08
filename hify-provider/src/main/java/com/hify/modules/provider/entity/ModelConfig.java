package com.hify.modules.provider.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 供应商下的具体模型配置。
 * <p>
 * 一个 Provider 可配多个模型（如 OpenAI 下同时配 GPT-4o 和 GPT-4o-mini），
 * Agent 通过 model_config_id 绑定到具体模型。
 * </p>
 */
@Getter
@Setter
@TableName(value = "t_model_config", autoResultMap = true)
public class ModelConfig extends BaseEntity {

    /** 所属供应商 ID */
    private Long providerId;

    /** 展示名，如 GPT-4o、Claude Sonnet 4.6 */
    private String name;

    /** 调用时传给 LLM API 的 model 值，如 gpt-4o、claude-sonnet-4-6 */
    private String modelId;

    /** 上下文窗口大小（token 数），用于 token 预算计算 */
    private Integer contextSize;

    /** 模型级别扩展参数（JSON）。t_model_config 在 MySQL，用 JacksonTypeHandler（setString） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraParams;

    /** 0=禁用 1=启用 */
    private Integer enabled;
}
