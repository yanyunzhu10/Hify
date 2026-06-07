package com.hify.modules.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 知识库文档（MySQL 主库，MyBatis-Plus CRUD）。
 */
@Getter
@Setter
@TableName("t_document")
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long knowledgeBaseId;
    private String name;
    private String fileType;
    private Long fileSize;
    private String status;
    private String errorMessage;
    private Integer chunkCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
