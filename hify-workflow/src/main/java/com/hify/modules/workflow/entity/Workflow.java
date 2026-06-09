package com.hify.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 工作流（一张图 = 一条记录）。对应表 {@code t_workflow}。
 */
@Getter
@Setter
@TableName("t_workflow")
public class Workflow extends BaseEntity {

    private String name;
    private String description;

    /** DRAFT 草稿 / PUBLISHED 已发布 */
    private String status;

    /** 是否启用：0=否 1=是 */
    private Integer enabled;
}
