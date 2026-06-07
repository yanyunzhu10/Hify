package com.hify.modules.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建知识库请求。
 */
@Data
public class KnowledgeBaseCreateReq {

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 100, message = "知识库名称最长 100 个字符")
    private String name;

    @Size(max = 500, message = "描述最长 500 个字符")
    private String description;
}
