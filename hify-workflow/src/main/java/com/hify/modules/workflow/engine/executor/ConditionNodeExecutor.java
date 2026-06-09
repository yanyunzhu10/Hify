package com.hify.modules.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.workflow.config.NodeConfig;
import com.hify.modules.workflow.engine.ExecutionContext;
import com.hify.modules.workflow.entity.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 条件判断节点：解析表达式，做字符串匹配，结果写入变量池。
 * <p>
 * 支持的表达式格式：
 * <ul>
 *   <li>{@code "true"} / {@code "false"} — 字面量</li>
 *   <li>{@code "{{classify.intent}} == 'ORDER_QUERY'"} — 等于判断</li>
 *   <li>{@code "{{classify.intent}} != 'OTHER'"} — 不等于判断</li>
 *   <li>纯布尔值（不带引号）也视为字面量</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class ConditionNodeExecutor implements NodeExecutor {

    @Override
    public String nodeType() {
        return "CONDITION";
    }

    @Override
    public void execute(WorkflowNode node, NodeConfig config, ExecutionContext ctx) {
        NodeConfig.ConditionConfig cfg = (NodeConfig.ConditionConfig) config;
        String nodeKey = node.getNodeKey();

        try {
            // 1) 模板变量替换
            String raw = cfg.expression();
            if (raw == null || raw.isBlank()) {
                ctx.set(nodeKey, node.getOutputVariable(), false);
                return;
            }
            String expr = ctx.resolve(raw).strip();

            // 2) 求值
            boolean result = evaluate(expr);
            ctx.set(nodeKey, node.getOutputVariable(), result);

            log.info("[WF-COND] node={} expr=\"{}\" → resolved=\"{}\" → result={}",
                    nodeKey, raw, expr, result);

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[WF-COND] node={} 执行失败", nodeKey, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "条件节点执行失败: " + e.getMessage(), e);
        }
    }

    /** 简单表达式求值 */
    private boolean evaluate(String expr) {
        if (expr.isEmpty()) return false;

        // 字面量 true/false
        if ("true".equalsIgnoreCase(expr)) return true;
        if ("false".equalsIgnoreCase(expr)) return false;

        // L == R
        if (expr.contains("==")) {
            String[] parts = expr.split("==", 2);
            return unquote(parts[0].strip()).equals(unquote(parts[1].strip()));
        }

        // L != R
        if (expr.contains("!=")) {
            String[] parts = expr.split("!=", 2);
            return !unquote(parts[0].strip()).equals(unquote(parts[1].strip()));
        }

        // 兜底：非空字符串视作 true
        return !expr.isEmpty();
    }

    /** 去掉首尾引号（单引号或双引号） */
    private static String unquote(String s) {
        if (s.length() >= 2) {
            char first = s.charAt(0);
            char last = s.charAt(s.length() - 1);
            if ((first == '\'' || first == '"') && first == last) {
                return s.substring(1, s.length() - 1);
            }
        }
        return s;
    }
}
