package com.hify.modules.chat.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建会话请求。
 */
@Data
public class SessionCreateReq {

    @NotNull(message = "agentId 不能为空")
    private Long agentId;

    /** 可选：初始标题，不传则用"新对话"，首次发消息后自动以前 20 字覆盖 */
    private String title;
}
