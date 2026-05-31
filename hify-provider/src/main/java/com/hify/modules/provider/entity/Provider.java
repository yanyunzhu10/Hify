package com.hify.modules.provider.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 模型供应商配置。
 * <p>
 * auth_config 为 JSON 列，不同 type 结构不同：
 * <ul>
 *   <li>OPENAI / OPENAI_COMPATIBLE — {"apiKey": "sk-..."}</li>
 *   <li>ANTHROPIC — {"apiKey": "sk-ant-..."}</li>
 *   <li>GEMINI — {"apiKey": "AIza...", "projectId": "..."}</li>
 *   <li>OLLAMA — {}（本地无需鉴权）</li>
 * </ul>
 * </p>
 */
@Getter
@Setter
@TableName(value = "t_provider", autoResultMap = true)
public class Provider extends BaseEntity {

    /** 供应商展示名称，如"OpenAI 主账号" */
    private String name;

    /** 供应商类型：OPENAI / ANTHROPIC / GEMINI / OLLAMA / OPENAI_COMPATIBLE */
    private String type;

    /** API 基础地址 */
    private String baseUrl;

    /** 鉴权配置（JSON），结构按 type 不同 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> authConfig;

    /** 0=禁用 1=启用 */
    private Integer enabled;
}
