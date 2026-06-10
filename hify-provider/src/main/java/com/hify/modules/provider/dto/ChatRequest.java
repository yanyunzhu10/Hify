package com.hify.modules.provider.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 通用 Chat 请求，Adapter 转换为供应商特定的请求体。
 * <p>
 * 内部使用 OpenAI 兼容的消息格式（system / user / assistant 均作为 messages[] 元素），
 * 各 Adapter 实现 {@link com.hify.modules.provider.adapter.ProviderAdapter#buildChatRequestBody(ChatRequest)}
 * 时将转换为供应商原生格式。
 * </p>
 */
@Data
public class ChatRequest {

    /** LLM 模型标识，如 gpt-4o、claude-sonnet-4-6、llama3 */
    private String model;

    /** 消息列表，含 system 提示、历史消息、当前用户输入 */
    private List<Message> messages;

    /** 采样温度 0~1 */
    private BigDecimal temperature;

    /** 最大生成 token 数 */
    private Integer maxTokens;

    /** 是否流式返回 */
    private boolean stream;

    /** Function calling 工具定义列表（OpenAI 兼容 JSON Schema 数组） */
    private List<Map<String, Object>> tools;

    /** tool_choice：null="auto" / "none" / "required" */
    private String toolChoice;

    @Data
    public static class Message {
        /** user | assistant | system | tool */
        private String role;
        /** 消息内容（当 tool_calls 存在时可为 null） */
        private String content;
        /** assistant 消息中的工具调用列表 */
        private List<Map<String, Object>> toolCalls;
        /** tool 消息中对应的 tool_call_id */
        private String toolCallId;
    }
}
