package com.hify.modules.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.http.LlmHttpClient;
import com.hify.modules.provider.adapter.ProviderAdapter;
import com.hify.modules.provider.adapter.ProviderAdapterFactory;
import com.hify.modules.provider.dto.ChatRequest;
import com.hify.modules.provider.dto.ChatResponse;
import com.hify.modules.provider.entity.ModelConfig;
import com.hify.modules.provider.entity.Provider;
import com.hify.modules.provider.mapper.ModelConfigMapper;
import com.hify.modules.provider.mapper.ProviderMapper;
import com.hify.modules.workflow.config.NodeConfig;
import com.hify.modules.workflow.engine.ExecutionContext;
import com.hify.modules.workflow.entity.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * LLM 节点执行器：同步调 LLM（非流式），结果写入变量池。
 */
@Slf4j
@Component
public class LlmNodeExecutor implements NodeExecutor {

    private final LlmHttpClient httpClient;
    private final ModelConfigMapper modelConfigMapper;
    private final ProviderMapper providerMapper;
    private final ProviderAdapterFactory adapterFactory;

    public LlmNodeExecutor(LlmHttpClient httpClient,
                           ModelConfigMapper modelConfigMapper,
                           ProviderMapper providerMapper,
                           ProviderAdapterFactory adapterFactory) {
        this.httpClient = httpClient;
        this.modelConfigMapper = modelConfigMapper;
        this.providerMapper = providerMapper;
        this.adapterFactory = adapterFactory;
    }

    @Override
    public String nodeType() {
        return "LLM";
    }

    @Override
    public void execute(WorkflowNode node, NodeConfig config, ExecutionContext ctx) {
        NodeConfig.LlmConfig cfg = (NodeConfig.LlmConfig) config;
        String nodeKey = node.getNodeKey();

        try {
            // 1) 模板变量替换
            String prompt = cfg.prompt() != null ? ctx.resolve(cfg.prompt()) : "";

            // 2) 加载模型配置 + 供应商
            ModelConfig mc = modelConfigMapper.selectById(cfg.modelConfigId());
            if (mc == null) {
                throw new BizException(ErrorCode.MODEL_CONFIG_NOT_FOUND,
                        "模型配置不存在: " + cfg.modelConfigId());
            }
            Provider provider = providerMapper.selectById(mc.getProviderId());
            if (provider == null) {
                throw new BizException(ErrorCode.NOT_FOUND,
                        "供应商不存在: " + mc.getProviderId());
            }

            // 3) 拼装请求
            ProviderAdapter adapter = adapterFactory.get(provider.getType());
            String url = adapter.buildChatUrl(provider.getBaseUrl(), mc.getModelId());
            Map<String, String> headers = adapter.buildAuthHeaders(provider);

            ChatRequest chatReq = new ChatRequest();
            chatReq.setModel(mc.getModelId());
            chatReq.setMessages(List.of(msg("user", prompt)));
            chatReq.setTemperature(cfg.temperature() != null
                    ? BigDecimal.valueOf(cfg.temperature()) : BigDecimal.valueOf(0.7));
            chatReq.setMaxTokens(cfg.maxTokens() != null ? cfg.maxTokens() : 2048);
            chatReq.setStream(false);
            String body = adapter.buildChatRequestBody(chatReq);

            // 4) 同步调 LLM
            String respBody = httpClient.post(url, headers, body);
            ChatResponse resp = adapter.parseChatResponse(respBody);

            // 5) 写入变量池
            if (node.getOutputVariable() != null) {
                ctx.set(nodeKey, node.getOutputVariable(), resp.getContent());
            }

            log.info("[WF-LLM] node={} 执行完成 outputVar={}.{} len={}",
                    nodeKey, nodeKey, node.getOutputVariable(),
                    resp.getContent() != null ? resp.getContent().length() : 0);

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[WF-LLM] node={} 执行失败", nodeKey, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "LLM 节点执行失败: " + e.getMessage(), e);
        }
    }

    private static ChatRequest.Message msg(String role, String content) {
        ChatRequest.Message m = new ChatRequest.Message();
        m.setRole(role);
        m.setContent(content);
        return m;
    }
}
