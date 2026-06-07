package com.hify.modules.provider.adapter;

import com.hify.modules.provider.dto.ChatRequest;
import com.hify.modules.provider.dto.ChatResponse;
import com.hify.modules.provider.dto.ChatStreamChunk;
import com.hify.modules.provider.dto.ModelInfo;
import com.hify.modules.provider.entity.Provider;

import java.util.List;
import java.util.Map;

/**
 * 供应商适配器：封装单个 LLM 供应商的 API 差异。
 * <p>
 * 覆盖连通性探测、模型列表同步、Chat（流式/非流式）四个方向。
 * 每新增一个供应商即新增一个实现，由 {@link ProviderAdapterFactory} 路由。
 * </p>
 *
 * @see ProviderAdapterFactory
 */
public interface ProviderAdapter {

    // ==================== 路由 ====================

    boolean supports(String type);

    // ==================== 连通性探测 & 模型同步 ====================

    String buildUrl(String baseUrl);

    Map<String, String> buildAuthHeaders(Provider provider);

    int parseModelCount(String responseBody);

    List<ModelInfo> parseModels(String responseBody);

    // ==================== Chat ====================

    /**
     * 构建 Chat 请求的完整 URL。
     *
     * @param baseUrl 供应商 API 基础地址
     * @param model   模型标识（如 gpt-4o），Gemini 等需嵌入 URL 的适配器使用
     * @return Chat 端点的完整 URL
     */
    String buildChatUrl(String baseUrl, String model);

    /**
     * 将通用 {@link ChatRequest} 转换为供应商原生 JSON 请求体。
     *
     * @param request 通用 Chat 请求（OpenAI 兼容消息格式）
     * @return 供应商原生 JSON 字符串
     */
    String buildChatRequestBody(ChatRequest request);

    /**
     * 解析非流式 Chat 的完整响应体，转为统一 {@link ChatResponse}。
     *
     * @param responseBody 供应商返回的完整 JSON
     * @return 统一响应
     */
    ChatResponse parseChatResponse(String responseBody);

    /**
     * 解析流式 Chat 的一条原始行，转为统一 {@link ChatStreamChunk}。
     * <p>
     * 各行格式由供应商自行定义：
     * <ul>
     *   <li>OpenAI / Anthropic：合法的内容行以 {@code data: } 开头，正文为 JSON</li>
     *   <li>Ollama：每行即一个完整 JSON，无 SSE 封装</li>
     *   <li>Gemini（?alt=sse）：标准 SSE 行，{@code data: } 前缀</li>
     * </ul>
     * 返回 {@code null} 表示该行不包含内容（空行、注释、[DONE]、元数据事件等），调用方跳过。
     * </p>
     *
     * @param rawLine OkHttp 流读取到的一行（不含换行符）
     * @return 解析后的 chunk，或 null
     */
    ChatStreamChunk parseStreamLine(String rawLine);
}
