package com.hify.modules.workflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 节点配置解析器：JSON Map ↔ 强类型 {@link NodeConfig} record。
 * <p>
 * 存库时 Service 层调 {@code toMap()} 序列化为 Map 写入 MySQL JSON 列；
 * 读回时调 {@code parse(Map, NodeType)} 反序列化。
 * </p>
 */
public final class NodeConfigParser {

    private static final Logger log = LoggerFactory.getLogger(NodeConfigParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private NodeConfigParser() {}

    /** JSON 对象 → 强类型配置。type 来自 node.type 列。 */
    @SuppressWarnings("unchecked")
    public static NodeConfig parse(Map<String, Object> raw, String type) {
        if (raw == null) raw = Map.of();
        switch (type.toUpperCase()) {
            case "START":     return new NodeConfig.StartConfig();
            case "END":       return new NodeConfig.EndConfig();
            case "LLM":
                // 诊断日志：打印 raw config 的实际 key 和类型
                logConfig(type, raw);
                return new NodeConfig.LlmConfig(
                        (String) raw.get("prompt"),
                        toLong(raw.get("modelConfigId")),
                        toDouble(raw.get("temperature")),
                        toInt(raw.get("maxTokens")));
            case "CONDITION": return new NodeConfig.ConditionConfig((String) raw.get("expression"));
            case "TOOL":      return new NodeConfig.ToolConfig(
                    toLong(raw.get("toolId")),
                    raw.get("inputs") instanceof Map<?, ?> m
                            ? (Map<String, Object>) m : Map.of());
            case "API_CALL":  return new NodeConfig.ApiCallConfig(
                    (String) raw.get("url"),
                    (String) raw.get("method"),
                    raw.get("headers") instanceof Map<?, ?> m1
                            ? (Map<String, Object>) m1 : Map.of(),
                    raw.get("body") instanceof Map<?, ?> m2
                            ? (Map<String, Object>) m2 : Map.of());
            case "KNOWLEDGE":
                logConfig(type, raw);
                return new NodeConfig.KnowledgeConfig(
                        toLong(raw.get("knowledgeBaseId")),
                        toInt(raw.get("topK")),
                        toDouble(raw.get("minSimilarity")));
            default: throw new BizException(ErrorCode.WORKFLOW_NODE_CONFIG_INVALID,
                    "未知节点类型: " + type);
        }
    }

    private static void logConfig(String type, Map<String, Object> raw) {
        log.info("[NodeConfigParser] type={} keys={} modelConfigId-type={} value={}",
                type, raw.keySet(),
                raw.get("modelConfigId") != null
                        ? raw.get("modelConfigId").getClass().getSimpleName() : "null",
                raw.get("modelConfigId"));
    }

    /** 强类型配置 → JSON 对象（存 MySQL）。 */
    public static Map<String, Object> toMap(NodeConfig config) {
        if (config instanceof NodeConfig.LlmConfig c) {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put("prompt", c.prompt());
            m.put("modelConfigId", c.modelConfigId());
            m.put("temperature", c.temperature());
            m.put("maxTokens", c.maxTokens());
            return m;
        }
        if (config instanceof NodeConfig.ConditionConfig c) {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put("expression", c.expression());
            return m;
        }
        if (config instanceof NodeConfig.ToolConfig c) {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put("toolId", c.toolId());
            m.put("inputs", c.inputs());
            return m;
        }
        if (config instanceof NodeConfig.ApiCallConfig c) {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put("url", c.url());
            m.put("method", c.method());
            m.put("headers", c.headers());
            m.put("body", c.body());
            return m;
        }
        if (config instanceof NodeConfig.KnowledgeConfig c) {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put("knowledgeBaseId", c.knowledgeBaseId());
            m.put("topK", c.topK());
            m.put("minSimilarity", c.minSimilarity());
            return m;
        }
        // StartConfig / EndConfig — 无配置
        return new LinkedHashMap<>();
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    private static Double toDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return null; }
    }

    private static Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }
}
