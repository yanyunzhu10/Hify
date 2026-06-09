package com.hify.modules.workflow.config;

import java.util.Map;

/**
 * 节点配置 sealed interface：不同类型的节点有各自的配置结构。
 * <p>
 * 存库时序列化为 {@link WorkflowNode#config} JSON 列，
 * 读回时由 {@link NodeConfigParser} 按 {@code type} 分发反序列化。
 * </p>
 */
public sealed interface NodeConfig
        permits NodeConfig.LlmConfig, NodeConfig.ConditionConfig, NodeConfig.ToolConfig,
                NodeConfig.ApiCallConfig, NodeConfig.KnowledgeConfig,
                NodeConfig.StartConfig, NodeConfig.EndConfig {

    /** 从 JSON Map 反序列化回具体 record。仍用 Map 做中转，解析逻辑在 NodeConfigParser 里。 */

    /** LLM 节点：调 LLM 生成回答 */
    record LlmConfig(String prompt,
                     Long modelConfigId,
                     Double temperature,
                     Integer maxTokens) implements NodeConfig {}

    /** CONDITION 节点：根据表达式判断走哪个分支 */
    record ConditionConfig(String expression) implements NodeConfig {}

    /** TOOL 节点：调用 MCP 工具 */
    record ToolConfig(Long toolId, Map<String, Object> inputs) implements NodeConfig {}

    /** API_CALL 节点：调外部 API（区别于 MCP 协议） */
    record ApiCallConfig(String url,
                         String method,
                         Map<String, Object> headers,
                         Map<String, Object> body) implements NodeConfig {}

    /** KNOWLEDGE 节点：从知识库检索相关文档片段（RAG 注入） */
    record KnowledgeConfig(Long knowledgeBaseId,
                           Integer topK,
                           Double minSimilarity) implements NodeConfig {}

    /** START 节点：无配置，仅标记入口 */
    record StartConfig() implements NodeConfig {}

    /** END 节点：无配置，仅标记结束 */
    record EndConfig() implements NodeConfig {}
}
