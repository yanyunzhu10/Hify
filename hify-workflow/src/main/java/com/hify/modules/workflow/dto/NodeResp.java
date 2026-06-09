package com.hify.modules.workflow.dto;

import com.hify.modules.workflow.entity.WorkflowNode;
import lombok.Data;

import java.util.Map;

/**
 * 节点响应。
 */
@Data
public class NodeResp {

    private Long id;
    private String nodeKey;
    private String type;
    private String name;
    private Map<String, Object> config;
    private String outputVariable;

    public static NodeResp from(WorkflowNode n) {
        NodeResp r = new NodeResp();
        r.id = n.getId();
        r.nodeKey = n.getNodeKey();
        r.type = n.getType();
        r.name = n.getName();
        r.config = n.getConfig();
        r.outputVariable = n.getOutputVariable();
        return r;
    }
}
