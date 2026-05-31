package com.hify.modules.provider.adapter;

import com.hify.modules.provider.entity.Provider;

import java.util.Map;

/**
 * 供应商适配器：封装单个 LLM 供应商的 API 差异。
 * <p>
 * 不同供应商在连通性探测端点、认证头格式、响应结构上各不相同，
 * 每新增一个供应商即新增一个本接口的实现，由 {@link ProviderAdapterFactory}
 * 按 {@link #supports(String)} 路由。新增供应商无需改动已有 Adapter 或编排逻辑。
 * </p>
 *
 * @see ProviderAdapterFactory
 * @see com.hify.modules.provider.service.impl.ProviderConnectivityServiceImpl
 */
public interface ProviderAdapter {

    /**
     * 是否支持该供应商类型。type 已由 Factory 规范化为小写。
     *
     * @param type 供应商类型，如 "openai" / "anthropic" / "ollama" / "gemini"
     * @return true 表示本 Adapter 负责处理该类型
     */
    boolean supports(String type);

    /**
     * 构建连通性探测的完整 URL（通常是"列出模型"端点）。
     *
     * @param baseUrl 供应商配置的 API 基础地址（可能以 / 结尾，实现需自行规整）
     * @return 探测请求的完整 URL
     */
    String buildUrl(String baseUrl);

    /**
     * 构建鉴权请求头。无需鉴权的供应商（如本地 Ollama）返回空 Map。
     *
     * @param provider 供应商配置，鉴权信息从 {@link Provider#getAuthConfig()} 提取
     * @return 请求头键值对，不会为 null
     */
    Map<String, String> buildAuthHeaders(Provider provider);

    /**
     * 从探测响应体解析可用模型数量。解析失败应返回 0 而非抛异常
     * （连通性已经成功，模型数仅为附加信息）。
     *
     * @param responseBody 探测端点返回的响应体
     * @return 可用模型数量，解析失败返回 0
     */
    int parseModelCount(String responseBody);
}
