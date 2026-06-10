package com.hify.modules.provider.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 非流式 Chat 的统一返回。各 Adapter 的 {@code parseChatResponse} 将供应商原生响应转为此结构。
 */
@Data
public class ChatResponse {

    /** 完整回复文本 */
    private String content;

    /** 结束原因：stop / length / error / end_turn / STOP / tool_calls */
    private String finishReason;

    /** prompt tokens 消耗 */
    private Integer promptTokens;

    /** completion tokens 消耗 */
    private Integer completionTokens;

    /** tool_calls（当 finish_reason="tool_calls" 时）每项含 id/name/arguments */
    private List<Map<String, Object>> toolCalls;
}
