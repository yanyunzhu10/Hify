package com.hify.modules.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.http.EmbeddingClient;
import com.hify.modules.knowledge.repository.ChunkHit;
import com.hify.modules.knowledge.repository.ChunkRepository;
import com.hify.modules.workflow.config.NodeConfig;
import com.hify.modules.workflow.engine.ExecutionContext;
import com.hify.modules.workflow.entity.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识库检索节点：向量化查询文本 → 相似度检索 → 结果拼成字符串写入变量池。
 */
@Slf4j
@Component
public class KnowledgeNodeExecutor implements NodeExecutor {

    private final ChunkRepository chunkRepo;
    private final EmbeddingClient embeddingClient;

    public KnowledgeNodeExecutor(ChunkRepository chunkRepo, EmbeddingClient embeddingClient) {
        this.chunkRepo = chunkRepo;
        this.embeddingClient = embeddingClient;
    }

    @Override
    public String nodeType() {
        return "KNOWLEDGE";
    }

    @Override
    public void execute(WorkflowNode node, NodeConfig config, ExecutionContext ctx) {
        NodeConfig.KnowledgeConfig cfg = (NodeConfig.KnowledgeConfig) config;
        String nodeKey = node.getNodeKey();
        // knowledgeBaseId：节点显式指定优先 → Agent 绑定的兜底
        Long kbId = cfg.knowledgeBaseId() != null ? cfg.knowledgeBaseId() : ctx.getAgentKnowledgeBaseId();
        int topK = cfg.topK() != null ? cfg.topK() : 3;
        double minSim = cfg.minSimilarity() != null ? cfg.minSimilarity() : 0.5;

        if (kbId == null) {
            throw new BizException(ErrorCode.WORKFLOW_NODE_CONFIG_INVALID,
                    "KNOWLEDGE 节点 [" + node.getNodeKey() + "] 缺少 knowledgeBaseId。"
                            + "节点 config 未指定且 Agent 未绑定知识库。");
        }

        try {
            // 1) 用用户输入作为查询文本（KNOWLEDGE 节点通常查用户问题相关的内容）
            String queryText = ctx.resolve("{{start.userMessage}}");

            // 2) 查 query 向量 → 相似度检索
            float[] queryVec = embeddingClient.embed(queryText);
            List<ChunkHit> hits = chunkRepo.searchNearest(queryVec, kbId, topK);

            // 3) 过滤 + 拼接
            StringBuilder sb = new StringBuilder();
            int idx = 1;
            for (ChunkHit h : hits) {
                if (h.getDistance() == null) continue;
                double sim = 1.0 - h.getDistance();
                if (sim < minSim) continue;
                sb.append("[").append(idx++).append("] ").append(h.getContent()).append("\n");
            }

            String result = sb.toString().strip();
            if (node.getOutputVariable() != null) {
                ctx.set(nodeKey, node.getOutputVariable(), result.isEmpty() ? "" : result);
            }

            log.info("[WF-KNOWLEDGE] node={} kbId={} topK={} minSim={} 命中 {} 条 写入 {}",
                    nodeKey, kbId, topK, minSim, idx - 1, node.getOutputVariable());

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[WF-KNOWLEDGE] node={} 执行失败", nodeKey, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "知识库检索节点执行失败: " + e.getMessage(), e);
        }
    }
}
