package com.hify.modules.provider.dto;

import lombok.Data;

/**
 * 流式 Chat 的单条增量数据，由 Adapter 从原始 SSE 行解析得到。
 * <p>
 * {@code content == null} 表示该行不是内容增量（如元数据事件、心跳），调用方应跳过。
 * </p>
 */
@Data
public class ChatStreamChunk {

    /** 增量文本内容；null 表示非内容事件 */
    private String content;

    /** 结束原因（仅最后一个 chunk 有值）：stop / length / error / end_turn */
    private String finishReason;

    /** completion tokens（仅最后一个 chunk 可能包含 usage 信息） */
    private Integer completionTokens;

    public static ChatStreamChunk content(String text) {
        ChatStreamChunk c = new ChatStreamChunk();
        c.content = text;
        return c;
    }

    public static ChatStreamChunk finish(String reason, Integer tokens) {
        ChatStreamChunk c = new ChatStreamChunk();
        c.finishReason = reason;
        c.completionTokens = tokens;
        return c;
    }
}
