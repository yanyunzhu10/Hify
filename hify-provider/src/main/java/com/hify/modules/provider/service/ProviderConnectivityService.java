package com.hify.modules.provider.service;

import com.hify.modules.provider.dto.ConnectionTestResult;
import com.hify.modules.provider.entity.Provider;

public interface ProviderConnectivityService {

    /**
     * 对指定供应商执行连通性测试。
     * 根据 provider.type 分发到不同的认证方式和端点。
     */
    ConnectionTestResult test(Provider provider);
}
