package com.hify.modules.provider.dto;

import lombok.Data;

/**
 * 连通性测试统一返回结果。
 */
@Data
public class ConnectionTestResult {

    /** 是否连通成功 */
    private boolean success;

    /** 请求延迟（毫秒） */
    private long latencyMs;

    /** 可用模型数量（成功时返回） */
    private Integer modelCount;

    /** 失败原因（失败时返回） */
    private String errorMessage;

    public static ConnectionTestResult ok(long latencyMs, int modelCount) {
        ConnectionTestResult r = new ConnectionTestResult();
        r.success = true;
        r.latencyMs = latencyMs;
        r.modelCount = modelCount;
        return r;
    }

    public static ConnectionTestResult fail(long latencyMs, String errorMessage) {
        ConnectionTestResult r = new ConnectionTestResult();
        r.success = false;
        r.latencyMs = latencyMs;
        r.errorMessage = errorMessage;
        return r;
    }
}
