package com.hify.common.exception;

import lombok.Getter;

@Getter
public class LlmApiException extends BizException {

    public enum Type {
        TIMEOUT,
        AUTH_FAILED,
        RATE_LIMITED,
        OTHER
    }

    private final Type type;

    /** HTTP 状态码；网络/超时类异常无响应时为 -1 */
    private final int httpStatus;

    public LlmApiException(Type type, int httpStatus, String message, Throwable cause) {
        super(toErrorCode(type), message, cause);
        this.type = type;
        this.httpStatus = httpStatus;
    }

    public LlmApiException(Type type, int httpStatus, String message) {
        super(toErrorCode(type), message);
        this.type = type;
        this.httpStatus = httpStatus;
    }

    private static ErrorCode toErrorCode(Type type) {
        return switch (type) {
            case TIMEOUT -> ErrorCode.LLM_TIMEOUT;
            case AUTH_FAILED -> ErrorCode.LLM_AUTH_FAILED;
            case RATE_LIMITED -> ErrorCode.LLM_RATE_LIMITED;
            case OTHER -> ErrorCode.LLM_CALL_FAILED;
        };
    }
}
