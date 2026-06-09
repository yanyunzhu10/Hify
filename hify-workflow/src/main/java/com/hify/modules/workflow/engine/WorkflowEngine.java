package com.hify.modules.workflow.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.workflow.config.NodeConfig;
import com.hify.modules.workflow.config.NodeConfigParser;
import com.hify.modules.workflow.engine.executor.NodeExecutor;
import com.hify.modules.workflow.engine.executor.NodeExecutorRegistry;
import com.hify.modules.workflow.entity.WorkflowEdge;
import com.hify.modules.workflow.entity.WorkflowNode;
import com.hify.modules.workflow.entity.WorkflowNodeRun;
import com.hify.modules.workflow.entity.WorkflowRun;
import com.hify.modules.workflow.mapper.WorkflowEdgeMapper;
import com.hify.modules.workflow.mapper.WorkflowNodeMapper;
import com.hify.modules.workflow.mapper.WorkflowNodeRunMapper;
import com.hify.modules.workflow.mapper.WorkflowRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 工作流执行引擎（同步执行）。
 * <p>
 * 从 DB 平铺表加载图到内存 Map,然后 while 循环遍历。
 * 线程池交由外部（Controller / ChatServiceImpl）管理,本类不引入新的异步机制。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngine {

    private static final int MAX_STEPS = 50;

    private final WorkflowNodeMapper nodeMapper;
    private final WorkflowEdgeMapper edgeMapper;
    private final WorkflowRunMapper runMapper;
    private final WorkflowNodeRunMapper nodeRunMapper;
    private final NodeExecutorRegistry executorRegistry;

    /**
     * 同步执行工作流。
     *
     * @param workflowId  工作流 id
     * @param userMessage 用户输入
     * @return 执行结果(最终输出文本)
     * @throws BizException 图结构不合法或执行失败时抛
     */
    public String execute(Long workflowId, String userMessage) {
        long startMs = System.currentTimeMillis();

        // ============================================================
        // ① 从平铺表加载图 → 内存
        // ============================================================
        Map<String, WorkflowNode> nodeMap = loadGraph(workflowId);
        Map<String, List<WorkflowEdge>> edgeMap = buildEdgeMap(workflowId);

        if (nodeMap.isEmpty()) {
            throw new BizException(ErrorCode.WORKFLOW_GRAPH_INVALID,
                    "工作流没有节点: workflowId=" + workflowId);
        }

        // ============================================================
        // ② 找入口节点：START 节点优先 → 无入边的节点 → 第一个节点
        //    START 不是必须的 —— userMessage 已在 ExecutionContext 构造时注入
        // ============================================================
        String currentKey = findEntry(nodeMap, edgeMap);

        // ============================================================
        // ③ 创建 WorkflowRun（status=RUNNING）
        // ============================================================
        ExecutionContext ctx = new ExecutionContext(UUID.randomUUID().toString(), userMessage);
        WorkflowRun run = createRun(workflowId, userMessage);

        // ============================================================
        // ④ while 循环：结束条件 = findNext 返回 null（无出边 / 自然尽头）
        //    有 END 节点时从它的 outputVariable 取最终输出；
        //    没有时取最后一个节点的 outputVariable。
        // ============================================================
        int step = 0;
        String output = "";

        try {
            while (currentKey != null && step < MAX_STEPS) {

                // 保护：目标节点必须存在
                WorkflowNode node = nodeMap.get(currentKey);
                if (node == null) {
                    throw new BizException(ErrorCode.WORKFLOW_GRAPH_INVALID,
                            "目标节点不存在: " + currentKey);
                }

                ctx.setCurrentNodeKey(currentKey);
                step++;
                log.info("[WF] runId={} step={} node={} type={}",
                        run.getId(), step, currentKey, node.getType());

                // —— 执行当前节点 ——
                long nodeStart = System.currentTimeMillis();
                WorkflowNodeRun nodeRun = createNodeRun(run.getId(), node);
                try {
                    NodeConfig config = NodeConfigParser.parse(node.getConfig(), node.getType());
                    NodeExecutor executor = executorRegistry.get(node.getType());
                    executor.execute(node, config, ctx);
                } catch (Exception e) {
                    int elapsed = (int) (System.currentTimeMillis() - nodeStart);
                    nodeRun.setStatus("FAILED");
                    nodeRun.setError(truncate(e.getMessage(), 500));
                    nodeRun.setElapsedMs(elapsed);
                    nodeRun.setFinishedAt(LocalDateTime.now());
                    safeUpdateNodeRun(nodeRun);
                    run.setStatus("FAILED");
                    run.setError(truncate(e.getMessage(), 500));
                    run.setElapsedMs((int) (System.currentTimeMillis() - startMs));
                    run.setFinishedAt(LocalDateTime.now());
                    runMapper.updateById(run);
                    throw new BizException(ErrorCode.INTERNAL_ERROR,
                            "节点执行失败 [" + node.getNodeKey() + "]: " + e.getMessage(), e);
                }
                // 节点成功
                nodeRun.setStatus("SUCCESS");
                nodeRun.setOutputs(new LinkedHashMap<>(ctx.snapshot()));
                nodeRun.setElapsedMs((int) (System.currentTimeMillis() - nodeStart));
                nodeRun.setFinishedAt(LocalDateTime.now());
                safeUpdateNodeRun(nodeRun);

                // —— 取输出 & 找下一步 ——
                // 有 outputVariable 的节点：把它的输出作为候选最终输出
                if (node.getOutputVariable() != null) {
                    Object val = ctx.get(node.getNodeKey(), node.getOutputVariable());
                    if (val != null) output = String.valueOf(val);
                }

                // END 节点：显式结束
                if ("END".equalsIgnoreCase(node.getType())) {
                    break;
                }

                // 找下一个节点；返回 null 表示无出边，自然结束（不要 END 也能退出）
                currentKey = findNext(node, edgeMap, ctx);
            }

            if (step >= MAX_STEPS) {
                throw new BizException(ErrorCode.INTERNAL_ERROR,
                        "工作流执行超过最大步数 " + MAX_STEPS + "（可能存在死循环）");
            }

            // ============================================================
            // ⑤ 更新 WorkflowRun → SUCCESS
            // ============================================================
            run.setStatus("SUCCESS");
            run.setOutput(output);
            run.setElapsedMs((int) (System.currentTimeMillis() - startMs));
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);

            log.info("[WF] runId={} SUCCESS steps={} elapsedMs={}",
                    run.getId(), step, run.getElapsedMs());
            return output;

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[WF] runId={} 执行异常", run.getId(), e);
            run.setStatus("FAILED");
            run.setError(truncate(e.getMessage(), 500));
            run.setElapsedMs((int) (System.currentTimeMillis() - startMs));
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "工作流执行异常: " + e.getMessage(), e);
        }
    }

    // ════════════════════════════════════════════════════════
    // 图加载
    // ════════════════════════════════════════════════════════

    private Map<String, WorkflowNode> loadGraph(Long workflowId) {
        List<WorkflowNode> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<WorkflowNode>()
                        .eq(WorkflowNode::getWorkflowId, workflowId));
        Map<String, WorkflowNode> map = new LinkedHashMap<>();
        for (WorkflowNode n : nodes) {
            map.put(n.getNodeKey(), n);
        }
        return map;
    }

    private Map<String, List<WorkflowEdge>> buildEdgeMap(Long workflowId) {
        List<WorkflowEdge> edges = edgeMapper.selectList(
                new LambdaQueryWrapper<WorkflowEdge>()
                        .eq(WorkflowEdge::getWorkflowId, workflowId));
        Map<String, List<WorkflowEdge>> map = new LinkedHashMap<>();
        for (WorkflowEdge e : edges) {
            map.computeIfAbsent(e.getSourceNodeKey(), k -> new ArrayList<>()).add(e);
        }
        return map;
    }

    // ════════════════════════════════════════════════════════
    // 入口节点选择
    // ════════════════════════════════════════════════════════

    /**
     * 找入口节点。START 不是必须的。
     * 优先级：显式 START 节点 → 没有入边的节点 → 第一个节点。
     * <p>
     * 同一工作流里被多个节点同时以无条件边引用也合法——每条线都可以执行，
     * 所以这里挑第一个遇到的。
     * </p>
     */
    private String findEntry(Map<String, WorkflowNode> nodeMap,
                             Map<String, List<WorkflowEdge>> edgeMap) {
        // 优先：显式 START
        for (WorkflowNode n : nodeMap.values()) {
            if ("START".equalsIgnoreCase(n.getType())) return n.getNodeKey();
        }
        // 其次：没有入边的节点（收集所有被引用的 target）
        Set<String> targets = new java.util.HashSet<>();
        for (List<WorkflowEdge> edges : edgeMap.values()) {
            for (WorkflowEdge e : edges) {
                targets.add(e.getTargetNodeKey());
            }
        }
        for (WorkflowNode n : nodeMap.values()) {
            if (!targets.contains(n.getNodeKey())) return n.getNodeKey();
        }
        // 兜底
        return nodeMap.keySet().iterator().next();
    }

    // ════════════════════════════════════════════════════════
    // 下一步选择（条件分支）
    // ════════════════════════════════════════════════════════

    /**
     * 找下一个节点。
     * <ul>
     *   <li>CONDITION 节点：从 ctx 取布尔结果，匹配 conditionExpr 为 "true" 或 "false" 的出边</li>
     *   <li>其他节点：先找 conditionExpr 为 null 的无条件边，没有则取第一条</li>
     * </ul>
     */
    private String findNext(WorkflowNode node, Map<String, List<WorkflowEdge>> edgeMap,
                            ExecutionContext ctx) {
        List<WorkflowEdge> outEdges = edgeMap.getOrDefault(node.getNodeKey(), List.of());
        if (outEdges.isEmpty()) {
            return null;
        }

        // CONDITION 节点：从 ctx 取布尔结果
        if ("CONDITION".equalsIgnoreCase(node.getType())) {
            Boolean condValue = null;
            if (node.getOutputVariable() != null) {
                Object val = ctx.get(node.getNodeKey(), node.getOutputVariable());
                if (val instanceof Boolean b) {
                    condValue = b;
                } else if (val != null) {
                    condValue = "true".equalsIgnoreCase(String.valueOf(val));
                }
            }
            String target = condValue != null ? String.valueOf(condValue) : "true";
            // 匹配 conditionExpr
            for (WorkflowEdge e : outEdges) {
                if (target.equalsIgnoreCase(e.getConditionExpr())
                        || (e.getConditionExpr() == null && "true".equalsIgnoreCase(target))) {
                    return e.getTargetNodeKey();
                }
            }
            // 没匹配到：先找无条件边，再取第一条
        }

        // 其他节点：优先无条件边
        for (WorkflowEdge e : outEdges) {
            if (e.getConditionExpr() == null) {
                return e.getTargetNodeKey();
            }
        }

        // 兜底：取第一条
        return outEdges.get(0).getTargetNodeKey();
    }

    // ════════════════════════════════════════════════════════
    // 执行记录（创建 + 安全更新）
    // ════════════════════════════════════════════════════════

    private WorkflowRun createRun(Long workflowId, String input) {
        WorkflowRun r = new WorkflowRun();
        r.setWorkflowId(workflowId);
        r.setStatus("RUNNING");
        r.setInput(input);
        r.setCreatedAt(LocalDateTime.now());
        runMapper.insert(r);
        return r;
    }

    private WorkflowNodeRun createNodeRun(Long runId, WorkflowNode node) {
        WorkflowNodeRun nr = new WorkflowNodeRun();
        nr.setWorkflowRunId(runId);
        nr.setNodeKey(node.getNodeKey());
        nr.setNodeType(node.getType());
        nr.setStatus("RUNNING");
        nr.setCreatedAt(LocalDateTime.now());
        nodeRunMapper.insert(nr);
        return nr;
    }

    /** 第三步：执行记录落库失败只打 log，不抛异常 */
    private void safeUpdateNodeRun(WorkflowNodeRun nr) {
        try {
            nodeRunMapper.updateById(nr);
        } catch (Exception e) {
            log.warn("[WF] 节点执行记录更新失败 id={}", nr.getId(), e);
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
