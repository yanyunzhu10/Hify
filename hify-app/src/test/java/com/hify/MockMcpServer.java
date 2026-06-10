package com.hify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 模拟 MCP Server（JSON-RPC 2.0 over HTTP），用于本地测试 function calling 整条链路。
 * <p>
 * 启动：直接 run main()，监听 9001 端口。
 * 支持 initialize / tools/list / tools/call 三个方法。
 * </p>
 */
public class MockMcpServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(9001), 0);
        server.createContext("/mcp", exchange -> {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Mcp-Session-Id", "mock-session-" + UUID.randomUUID().toString().substring(0, 8));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> req = MAPPER.readValue(body, Map.class);
            String method = (String) req.get("method");
            int id = req.get("id") instanceof Number n ? n.intValue() : 0;

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("jsonrpc", "2.0");
            resp.put("id", id);

            switch (method) {
                case "initialize" -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("protocolVersion", "2024-11-05");
                    result.put("capabilities", Map.of("tools", Map.of()));
                    result.put("serverInfo", Map.of("name", "mock-order-service", "version", "1.0"));
                    resp.put("result", result);
                }
                case "tools/list" -> {
                    List<Map<String, Object>> tools = new ArrayList<>();

                    // 工具1：query_order
                    Map<String, Object> t1 = new LinkedHashMap<>();
                    t1.put("name", "query_order");
                    t1.put("description", "查询用户订单状态，根据订单号返回物流信息");
                    Map<String, Object> schema1 = new LinkedHashMap<>();
                    schema1.put("type", "object");
                    schema1.put("properties", Map.of(
                            "orderId", Map.of("type", "string", "description", "订单号"),
                            "userId", Map.of("type", "string", "description", "用户标识")
                    ));
                    schema1.put("required", List.of("orderId"));
                    t1.put("inputSchema", schema1);
                    tools.add(t1);

                    resp.put("result", Map.of("tools", tools));
                }
                case "tools/call" -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> params = (Map<String, Object>) req.getOrDefault("params", Map.of());
                    String toolName = (String) params.get("name");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", Map.of());

                    List<Map<String, Object>> content = new ArrayList<>();
                    Map<String, Object> textContent = new LinkedHashMap<>();
                    textContent.put("type", "text");

                    if ("query_order".equals(toolName)) {
                        String orderId = (String) arguments.getOrDefault("orderId", "未知");
                        textContent.put("text", String.format(
                                "订单%s：已发货，顺丰快递 SF%d，运输中，预计明天送达。", orderId, 1000000 + orderId.hashCode() % 9000000));
                    } else {
                        textContent.put("text", "未知工具：" + toolName);
                    }
                    content.add(textContent);
                    resp.put("result", Map.of("content", content));
                }
                default -> resp.put("error", Map.of("code", -32601, "message", "Method not found: " + method));
            }

            String respStr = MAPPER.writeValueAsString(resp);
            byte[] bytes = respStr.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        System.out.println("Mock MCP Server started on http://localhost:9001/mcp");
    }
}
