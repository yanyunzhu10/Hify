package com.hify.modules.mcp.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.mcp.entity.McpServer;
import com.hify.modules.mcp.entity.McpTool;
import com.hify.modules.mcp.service.McpClientService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * MCP 客户端实现（基于 JSON-RPC over HTTP，OkHttp 直调）。
 * <p>
 * MCP 协议核心是 JSON-RPC 2.0：initialize → tools/list / tools/call，
 * 然后解析返回的 JSON 结果。不依赖外部 SDK jar。
 * </p>
 * <p>
 * 网络通后建议切换为 SDK 版，接口 {@link McpClientService} 不变：
 * 见 {@code McpClientServiceImplSdk.java}（ready-to-swap，需配 pom 依赖）。
 * </p>
 */
@Slf4j
@Service
public class McpClientServiceImpl implements McpClientService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public List<McpTool> listTools(McpServer server) {
        try {
            String sessionId = initialize(server.getEndpoint());
            Map<String, Object> req = rpcReq("tools/list", 1, Map.of());
            JsonNode resp = rpcCall(server.getEndpoint(), req, sessionId);
            JsonNode tools = resp.path("result").path("tools");

            List<McpTool> list = new ArrayList<>();
            if (tools.isArray()) {
                for (JsonNode t : tools) {
                    McpTool mt = new McpTool();
                    mt.setName(t.path("name").asText(""));
                    mt.setDescription(t.path("description").asText(""));
                    if (t.path("inputSchema").isObject()) {
                        mt.setInputSchema(MAPPER.convertValue(t.path("inputSchema"), Map.class));
                    }
                    mt.setCreatedAt(LocalDateTime.now());
                    list.add(mt);
                }
            }
            return list;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.MCP_CONNECTION_FAILED,
                    "获取工具列表失败 [" + server.getName() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public String callTool(McpServer server, String toolName, Map<String, Object> arguments) {
        try {
            String sessionId = initialize(server.getEndpoint());
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("name", toolName);
            params.put("arguments", arguments != null ? arguments : Map.of());

            JsonNode resp = rpcCall(server.getEndpoint(),
                    rpcReq("tools/call", 2, params), sessionId);
            JsonNode content = resp.path("result").path("content");
            StringBuilder sb = new StringBuilder();
            if (content.isArray()) {
                for (JsonNode node : content) {
                    String text = node.path("text").asText("");
                    if (!text.isEmpty()) {
                        if (!sb.isEmpty()) sb.append("\n");
                        sb.append(text);
                    }
                }
            }
            return sb.toString();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.MCP_TOOL_CALL_FAILED,
                    "工具调用失败 [" + toolName + "]: " + e.getMessage(), e);
        }
    }

    // ============ JSON-RPC helpers ============

    private Map<String, Object> rpcReq(String method, int id, Map<String, Object> params) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("jsonrpc", "2.0");
        req.put("method", method);
        req.put("id", id);
        req.put("params", params);
        return req;
    }

    private String initialize(String endpoint) throws Exception {
        Map<String, Object> req = rpcReq("initialize", 0,
                Map.of("protocolVersion", "2024-11-05",
                        "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "hify", "version", "1.0")));

        RequestBody body = RequestBody.create(MAPPER.writeValueAsString(req), JSON);
        try (Response r = httpClient.newCall(
                new Request.Builder().url(endpoint).post(body).build()).execute()) {
            if (!r.isSuccessful())
                throw new BizException(ErrorCode.MCP_CONNECTION_FAILED, "initialize HTTP " + r.code());
            String respBody = r.body() != null ? r.body().string() : "";
            JsonNode node = MAPPER.readTree(respBody);
            if (node.has("error"))
                throw new BizException(ErrorCode.MCP_CONNECTION_FAILED,
                        "initialize 失败: " + node.path("error").path("message").asText("unknown"));
            String sessionHeader = r.header("Mcp-Session-Id");
            return sessionHeader != null ? sessionHeader : "";
        }
    }

    private JsonNode rpcCall(String endpoint, Map<String, Object> req, String sessionId) throws Exception {
        Request.Builder builder = new Request.Builder().url(endpoint)
                .post(RequestBody.create(MAPPER.writeValueAsString(req), JSON));
        if (sessionId != null && !sessionId.isEmpty()) builder.header("Mcp-Session-Id", sessionId);
        try (Response r = httpClient.newCall(builder.build()).execute()) {
            String respBody = r.body() != null ? r.body().string() : "";
            if (!r.isSuccessful())
                throw new BizException(ErrorCode.MCP_CONNECTION_FAILED, "HTTP " + r.code() + ": " + respBody);
            JsonNode node = MAPPER.readTree(respBody);
            if (node.has("error"))
                throw new BizException(ErrorCode.MCP_CONNECTION_FAILED,
                        node.path("error").path("message").asText("unknown error"));
            return node;
        }
    }
}
