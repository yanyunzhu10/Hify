package com.hify.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 4xx - 客户端错误
    PARAM_ERROR(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权，请先登录"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),

    // 5xx - 服务端错误
    INTERNAL_ERROR(500, "系统内部错误"),
    SERVICE_UNAVAILABLE(503, "服务暂不可用"),
    LLM_CALL_FAILED(502, "LLM 调用失败"),
    LLM_TIMEOUT(504, "LLM 调用超时"),
    LLM_AUTH_FAILED(401, "LLM 认证失败"),
    LLM_RATE_LIMITED(429, "LLM 限流"),

    // ===== 业务错误：从 600 起，按模块分段，禁止复用 HTTP 状态码 =====
    // 610-629 provider / model
    PROVIDER_NAME_EXISTS(610, "供应商名称已存在"),
    MODEL_CONFIG_NOT_FOUND(611, "模型配置不存在或未启用"),

    // 630-649 agent
    AGENT_NAME_EXISTS(630, "Agent 名称已存在"),
    AGENT_NOT_FOUND(631, "Agent 不存在"),

    // 650-669 knowledge
    KNOWLEDGE_BASE_NAME_EXISTS(650, "知识库名称已存在"),
    KNOWLEDGE_BASE_NOT_FOUND(651, "知识库不存在"),
    DOCUMENT_NOT_FOUND(652, "文档不存在"),

    // 690-709 workflow
    WORKFLOW_NOT_FOUND(690, "工作流不存在"),
    WORKFLOW_NAME_EXISTS(691, "工作流名称已存在"),
    WORKFLOW_NODE_CONFIG_INVALID(692, "节点配置不合法"),
    WORKFLOW_GRAPH_INVALID(693, "工作流图结构不合法");

    private final int code;
    private final String message;
}
