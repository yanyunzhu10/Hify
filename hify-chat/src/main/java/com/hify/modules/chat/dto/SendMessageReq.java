package com.hify.modules.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送消息请求（极简）。会话由路径上的 sessionId 定位，agentId 从会话反查。
 */
@Data
public class SendMessageReq {

    @NotBlank(message = "消息内容不能为空")
    private String content;

    /** 是否流式，默认 true */
    private boolean stream = true;
}
