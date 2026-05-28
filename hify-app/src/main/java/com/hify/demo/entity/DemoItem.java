package com.hify.demo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("t_demo_item")
public class DemoItem extends BaseEntity {

    private String name;

    private Integer status;
}
