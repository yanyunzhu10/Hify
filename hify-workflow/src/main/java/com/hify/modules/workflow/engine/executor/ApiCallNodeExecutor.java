package com.hify.modules.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.http.LlmHttpClient;
import com.hify.modules.workflow.config.NodeConfig;
import com.hify.modules.workflow.engine.ExecutionContext;
import com.hify.modules.workflow.entity.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * API 调用节点：用 LlmHttpClient 发 HTTP 请求，响应体写入变量池。
 */
@Slf4j
@Component
public class ApiCallNodeExecutor implements NodeExecutor {

    private final LlmHttpClient httpClient;

    public ApiCallNodeExecutor(LlmHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String nodeType() {
        return "API_CALL";
    }

    @Override
    public void execute(WorkflowNode node, NodeConfig config, ExecutionContext ctx) {
        NodeConfig.ApiCallConfig cfg = (NodeConfig.ApiCallConfig) config;
        String nodeKey = node.getNodeKey();

        try {
            // 1) 模板变量替换 url 和 headers
            String url = ctx.resolve(cfg.url());
            String method = cfg.method() != null ? cfg.method().toUpperCase() : "GET";

            Map<String, String> headers = new HashMap<>();
            if (cfg.headers() != null) {
                for (Map.Entry<String, Object> e : cfg.headers().entrySet()) {
                    headers.put(e.getKey(), String.valueOf(e.getValue()));
                }
            }
            // 对 header value 做 resolve
            for (Map.Entry<String, String> e : headers.entrySet()) {
                e.setValue(ctx.resolve(e.getValue()));
            }

            // 2) 构造 body（body 里的模板变量也 resolve）
            String body = null;
            if (cfg.body() != null && !cfg.body().isEmpty()) {
                body = ctx.resolve(String.valueOf(cfg.body()));
            }

            // 3) 发请求
            String respBody;
            switch (method) {
                case "POST":
                    respBody = httpClient.post(url, headers, body != null ? body : "");
                    break;
                case "PUT":
                    // LlmHttpClient 没有 put 方法，POST 兜底（API_CALL 本质是业务系统调用）
                    respBody = httpClient.post(url, headers, body != null ? body : "");
                    break;
                case "GET":
                default:
                    respBody = httpClient.get(url, headers);
                    break;
            }

            // 4) 写入变量池
            if (node.getOutputVariable() != null) {
                ctx.set(nodeKey, node.getOutputVariable(), respBody);
            }

            log.info("[WF-API] node={} {} {} → respLen={}",
                    nodeKey, method, url,
                    respBody != null ? respBody.length() : 0);

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[WF-API] node={} 执行失败", nodeKey, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "API 调用节点执行失败: " + e.getMessage(), e);
        }
    }
}
