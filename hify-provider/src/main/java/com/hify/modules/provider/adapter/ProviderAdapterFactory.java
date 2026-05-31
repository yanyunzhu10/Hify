package com.hify.modules.provider.adapter;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 供应商适配器工厂：按 type 路由到对应的 {@link ProviderAdapter}。
 * <p>
 * Spring 自动注入所有 {@link ProviderAdapter} 实现，新增供应商只需新增一个
 * 带 {@code @Component} 的 Adapter，无需改动本工厂。
 * </p>
 */
@Component
public class ProviderAdapterFactory {

    private final List<ProviderAdapter> adapters;

    public ProviderAdapterFactory(List<ProviderAdapter> adapters) {
        this.adapters = adapters;
    }

    /**
     * 按供应商类型获取适配器。
     *
     * @param type 供应商类型（大小写不敏感）
     * @return 匹配的 Adapter
     * @throws BizException 未找到匹配的 Adapter 时抛 PARAM_ERROR
     */
    public ProviderAdapter get(String type) {
        String normalized = type != null ? type.toLowerCase() : "";
        return adapters.stream()
                .filter(adapter -> adapter.supports(normalized))
                .findFirst()
                .orElseThrow(() -> new BizException(
                        ErrorCode.PARAM_ERROR, "不支持的供应商类型: " + type));
    }
}
