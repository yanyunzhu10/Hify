package com.hify.modules.workflow.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工作流执行上下文（一次 run 一个实例）。
 * <p>
 * 变量池对标 Dify VariablePool：key = {@code nodeKey.varName}，
 * 如 {@code classify.intent}、{@code orderReply.answer}。
 * 节点可以读任意历史节点的输出，但只能写自己 nodeKey 下的变量。写入只增不改。
 * </p>
 */
public class ExecutionContext {

    private final String workflowRunId;

    /** 变量池：key = nodeKey.varName，value = 节点输出。LinkedHashMap 保持写入顺序。 */
    private final Map<String, Object> variables = new LinkedHashMap<>();

    /** 当前所在节点 key */
    private String currentNodeKey;

    /** 执行状态 */
    private ExecutionStatus status = ExecutionStatus.RUNNING;

    /** 最终输出文本（END 节点拼接写入） */
    private final StringBuilder output = new StringBuilder();

    /**
     * 创建执行上下文。
     *
     * @param workflowRunId 本次执行的唯一 id
     * @param userMessage   用户输入，预写入 {@code start.userMessage}，所有节点默认能读到
     */
    public ExecutionContext(String workflowRunId, String userMessage) {
        this.workflowRunId = workflowRunId;
        this.variables.put("start.userMessage", userMessage);
    }

    // ════════════════════════════════════════════════════
    // 变量池读写
    // ════════════════════════════════════════════════════

    /**
     * 写入变量。key = {@code nodeKey + "." + varName}。
     *
     * @param nodeKey 当前节点的 key（如 "classify"）
     * @param varName 变量名（如 "intent"）
     * @param value   输出值
     */
    public void set(String nodeKey, String varName, Object value) {
        if (nodeKey == null || varName == null) {
            return;
        }
        variables.put(nodeKey + "." + varName, value);
    }

    /**
     * 读取变量。
     *
     * @param nodeKey 节点 key
     * @param varName 变量名
     * @return 变量值，不存在时返回 null
     */
    public Object get(String nodeKey, String varName) {
        if (nodeKey == null || varName == null) {
            return null;
        }
        return variables.get(nodeKey + "." + varName);
    }

    /**
     * 模板变量替换。遍历变量池，将文本中所有 {@code {{nodeKey.varName}}}
     * 替换为对应值，变量不存在时保留原始占位符。
     *
     * @param template 含 {@code {{...}}} 占位符的模板文本
     * @return 替换后的文本
     */
    public String resolve(String template) {
        if (template == null || template.isBlank()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}",
                    String.valueOf(entry.getValue()));
        }
        return result;
    }

    /**
     * 返回变量池的只读快照，用于执行记录落库。
     */
    public Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(variables));
    }

    // ════════════════════════════════════════════════════
    // 状态与输出
    // ════════════════════════════════════════════════════

    public String getWorkflowRunId() {
        return workflowRunId;
    }

    public String getCurrentNodeKey() {
        return currentNodeKey;
    }

    public void setCurrentNodeKey(String currentNodeKey) {
        this.currentNodeKey = currentNodeKey;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    /** 追加一段文本到最终输出 */
    public void appendOutput(String text) {
        if (text != null) {
            output.append(text);
        }
    }

    public String getOutput() {
        return output.toString();
    }

    /** 变量池大小（调试用） */
    public int variableCount() {
        return variables.size();
    }

    @Override
    public String toString() {
        return "ExecutionContext{runId=" + workflowRunId
                + ", node=" + currentNodeKey
                + ", status=" + status
                + ", vars=" + variableCount() + "}";
    }
}
