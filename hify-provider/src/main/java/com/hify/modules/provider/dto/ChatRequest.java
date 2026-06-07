package com.hify.modules.provider.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 通用 Chat 请求，Adpater 转换为供应商特定的请求体。
 * <p>
 * 内部使用 OpenAI 兼容的消息格式（system / user / assistant 均作为 messages[] 元素），
 * 各 Adapter 实现 {@link com.hify.modules.provider.adapter.ProviderAdapter#buildChatRequestBody(ChatRequest)}
 * 时将转换为供应商原生格式：
 * <ul>
 *   <li>OpenAI / openai_compatible：直接序列化</li>
 *   <li>Anthropic：system 提取为顶层字段，messages 不含 system 角色</li>
 *   <li>Ollama：增加 options 嵌套</li>
 *   <li>Gemini：转为 contents/parts + systemInstruction + generationConfig</li>
 * </ul>
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

    @Data
    public static class Message {
        /** user | assistant | system */
        private String role;
        /** 消息内容 */
        private String content;
    }
}
