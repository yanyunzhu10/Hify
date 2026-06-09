package com.hify.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.workflow.entity.WorkflowNode;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowNodeMapper extends BaseMapper<WorkflowNode> {
}
