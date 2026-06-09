package com.hify.modules.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResult;
import com.hify.modules.workflow.dto.*;
import com.hify.modules.workflow.entity.Workflow;
import com.hify.modules.workflow.entity.WorkflowEdge;
import com.hify.modules.workflow.entity.WorkflowNode;
import com.hify.modules.workflow.mapper.WorkflowEdgeMapper;
import com.hify.modules.workflow.mapper.WorkflowMapper;
import com.hify.modules.workflow.mapper.WorkflowNodeMapper;
import com.hify.modules.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工作流 CRUD。
 * <p>
 * 存库是平铺的三张表；查询详情时从三张表组装回完整图结构（nodes + edges），
 * 结构完整还原，与创建请求一致。执行时再由引擎加载进内存跑 Map + while 循环。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowMapper wfMapper;
    private final WorkflowNodeMapper nodeMapper;
    private final WorkflowEdgeMapper edgeMapper;

    // ================================================================
    // Create
    // ================================================================

    @Override
    @Transactional
    public WorkflowResp create(WorkflowCreateReq req) {
        assertNameUnique(req.getName(), null);
        validateGraph(req.getNodes(), req.getEdges());

        Workflow wf = new Workflow();
        wf.setName(req.getName());
        wf.setDescription(req.getDescription() != null ? req.getDescription() : "");
        wf.setStatus(req.getStatus() != null ? req.getStatus() : "DRAFT");
        wf.setEnabled(1);
        wfMapper.insert(wf);

        // 批量插 nodes
        List<WorkflowNode> nodes = toNodes(wf.getId(), req.getNodes());
        if (!nodes.isEmpty()) {
            nodeMapper.insert(nodes);
        }

        // 批量插 edges
        List<WorkflowEdge> edges = toEdges(wf.getId(), req.getEdges());
        if (!edges.isEmpty()) {
            edgeMapper.insert(edges);
        }

        log.info("工作流创建 id={} name={} nodes={} edges={}",
                wf.getId(), wf.getName(), nodes.size(), edges.size());
        return assemble(wf, nodes, edges);
    }

    // ================================================================
    // Page
    // ================================================================

    @Override
    public PageResult<WorkflowResp> page(int page, int size, String name) {
        LambdaQueryWrapper<Workflow> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isBlank()) {
            wrapper.like(Workflow::getName, name);
        }
        wrapper.orderByDesc(Workflow::getUpdatedAt);
        Page<Workflow> p = wfMapper.selectPage(new Page<>(page, size), wrapper);
        List<WorkflowResp> list = p.getRecords().stream()
                .map(WorkflowResp::from).collect(Collectors.toList());
        return PageResult.ok(list, p.getTotal(), page, size);
    }

    // ================================================================
    // Get By Id (详情：含 nodes + edges)
    // ================================================================

    @Override
    public WorkflowResp getById(Long id) {
        Workflow wf = requireExists(id);
        List<WorkflowNode> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<WorkflowNode>().eq(WorkflowNode::getWorkflowId, id));
        List<WorkflowEdge> edges = edgeMapper.selectList(
                new LambdaQueryWrapper<WorkflowEdge>().eq(WorkflowEdge::getWorkflowId, id));
        return assemble(wf, nodes, edges);
    }

    // ================================================================
    // Update（直接替换：逻辑删旧的 + 重插新的）
    // ================================================================

    @Override
    @Transactional
    public WorkflowResp update(Long id, WorkflowUpdateReq req) {
        Workflow wf = requireExists(id);
        assertNameUnique(req.getName(), id);
        validateGraph(req.getNodes(), req.getEdges());

        // 更新主表字段
        wf.setName(req.getName());
        wf.setDescription(req.getDescription() != null ? req.getDescription() : wf.getDescription());
        if (req.getStatus() != null) wf.setStatus(req.getStatus());
        if (req.getEnabled() != null) wf.setEnabled(req.getEnabled());
        wfMapper.updateById(wf);

        // 逻辑删除旧 nodes + edges
        softDeleteNodes(id);
        softDeleteEdges(id);

        // 批量重插新 nodes + edges
        List<WorkflowNode> nodes = toNodes(id, req.getNodes());
        if (!nodes.isEmpty()) {
            nodeMapper.insert(nodes);
        }
        List<WorkflowEdge> edges = toEdges(id, req.getEdges());
        if (!edges.isEmpty()) {
            edgeMapper.insert(edges);
        }

        log.info("工作流更新 id={} name={} nodes={} edges={}", id, wf.getName(), nodes.size(), edges.size());
        return assemble(wf, nodes, edges);
    }

    // ================================================================
    // Delete（级联逻辑删）
    // ================================================================

    @Override
    @Transactional
    public void delete(Long id) {
        requireExists(id);
        softDeleteNodes(id);
        softDeleteEdges(id);
        wfMapper.deleteById(id);
        log.info("工作流删除 id={}（nodes + edges 已级联逻辑删除）", id);
    }

    // ============ 校验 ============

    private Workflow requireExists(Long id) {
        Workflow wf = wfMapper.selectById(id);
        if (wf == null) throw new BizException(ErrorCode.WORKFLOW_NOT_FOUND);
        return wf;
    }

    private void assertNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<Workflow> w = new LambdaQueryWrapper<Workflow>().eq(Workflow::getName, name);
        if (excludeId != null) w.ne(Workflow::getId, excludeId);
        if (wfMapper.selectCount(w) > 0) throw new BizException(ErrorCode.WORKFLOW_NAME_EXISTS);
    }

    /**
     * 简单图校验：
     * 1) nodeKey 在图内唯一；
     * 2) 每条 edge 的 source/target 必须引用图内的 nodeKey。
     */
    private void validateGraph(List<NodeReq> nodeReqs, List<EdgeReq> edgeReqs) {
        Set<String> nodeKeys = new HashSet<>();
        if (nodeReqs != null) {
            for (NodeReq n : nodeReqs) {
                if (!nodeKeys.add(n.getNodeKey())) {
                    throw new BizException(ErrorCode.WORKFLOW_GRAPH_INVALID,
                            "节点 key 重复: " + n.getNodeKey());
                }
            }
        }
        if (edgeReqs != null) {
            for (EdgeReq e : edgeReqs) {
                if (!nodeKeys.contains(e.getSourceNodeKey())) {
                    throw new BizException(ErrorCode.WORKFLOW_GRAPH_INVALID,
                            "边的 sourceNodeKey 不存在: " + e.getSourceNodeKey());
                }
                if (!nodeKeys.contains(e.getTargetNodeKey())) {
                    throw new BizException(ErrorCode.WORKFLOW_GRAPH_INVALID,
                            "边的 targetNodeKey 不存在: " + e.getTargetNodeKey());
                }
            }
        }
    }

    // ============ 实体构建 ============

    private List<WorkflowNode> toNodes(Long wfId, List<NodeReq> reqs) {
        if (reqs == null) return List.of();
        List<WorkflowNode> list = new ArrayList<>(reqs.size());
        for (NodeReq n : reqs) {
            WorkflowNode wn = new WorkflowNode();
            wn.setWorkflowId(wfId);
            wn.setNodeKey(n.getNodeKey());
            wn.setType(n.getType().toUpperCase());
            wn.setName(n.getName());
            wn.setConfig(n.getConfig());
            wn.setOutputVariable(n.getOutputVariable());
            list.add(wn);
        }
        return list;
    }

    private List<WorkflowEdge> toEdges(Long wfId, List<EdgeReq> reqs) {
        if (reqs == null) return List.of();
        List<WorkflowEdge> list = new ArrayList<>(reqs.size());
        for (EdgeReq e : reqs) {
            WorkflowEdge we = new WorkflowEdge();
            we.setWorkflowId(wfId);
            we.setSourceNodeKey(e.getSourceNodeKey());
            we.setTargetNodeKey(e.getTargetNodeKey());
            we.setConditionExpr(e.getConditionExpr());
            list.add(we);
        }
        return list;
    }

    // ============ 组装响应 ============

    private WorkflowResp assemble(Workflow wf, List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        WorkflowResp r = WorkflowResp.from(wf);
        r.setNodes(nodes.stream().map(NodeResp::from).collect(Collectors.toList()));
        r.setEdges(edges.stream().map(EdgeResp::from).collect(Collectors.toList()));
        return r;
    }

    // ============ 批量逻辑删除 ============

    private void softDeleteNodes(Long wfId) {
        WorkflowNode patch = new WorkflowNode();
        patch.setDeleted(1);
        nodeMapper.update(patch, new LambdaUpdateWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowId, wfId));
    }

    private void softDeleteEdges(Long wfId) {
        WorkflowEdge patch = new WorkflowEdge();
        patch.setDeleted(1);
        edgeMapper.update(patch, new LambdaUpdateWrapper<WorkflowEdge>()
                .eq(WorkflowEdge::getWorkflowId, wfId));
    }
}
