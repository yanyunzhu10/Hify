package com.hify.modules.provider.dto;

import lombok.Data;

/**
 * 非流式 Chat 的统一返回。各 Adapter 的 {@code parseChatResponse} 将供应商原生响应转为此结构。
 */
@Data
public class ChatResponse {

    /** 完整回复文本 */
    private String content;

    /** 结束原因：stop / length / error / end_turn / STOP */
    private String finishReason;

    /** prompt tokens 消耗 */
    private Integer promptTokens;

    /** completion tokens 消耗 */
    private Integer completionTokens;
}
