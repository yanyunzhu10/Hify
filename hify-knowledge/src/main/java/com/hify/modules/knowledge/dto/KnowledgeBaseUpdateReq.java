package com.hify.modules.knowledge.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新知识库请求（所有字段可选，传了就更新）。enabled 允许置 0（禁用）。
 */
@Data
public class KnowledgeBaseUpdateReq {

    @Size(max = 100, message = "知识库名称最长 100 个字符")
    private String name;

    @Size(max = 500, message = "描述最长 500 个字符")
    private String description;

    /** 是否启用：1=启用 0=禁用 */
    private Integer enabled;
}
