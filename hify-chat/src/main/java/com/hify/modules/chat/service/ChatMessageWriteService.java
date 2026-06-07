package com.hify.modules.chat.service;

import com.hify.modules.chat.entity.ChatMessage;
import com.hify.modules.chat.entity.ChatSession;
import com.hify.modules.chat.mapper.ChatMessageMapper;
import com.hify.modules.chat.mapper.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 对话消息写入（独立事务 Bean）。
 * <p>
 * 必须独立于 {@code ChatServiceImpl} 存在，否则 {@code streamChat} 内通过
 * {@code this.xxx()} 调用会导致 Spring 事务代理失效。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageWriteService {

    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMapper chatSessionMapper;

    /** 保存用户消息，独立事务，方法结束即提交。 */
    @Transactional
    public ChatMessage saveUserMessage(Long sessionId, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole("user");
        msg.setContent(content);
        msg.setTokens(0);
        chatMessageMapper.insert(msg);
        return msg;
    }

    /** 保存 assistant 回复 + 更新会话活跃时间，独立事务，方法结束即提交。 */
    @Transactional
    public void saveAssistantMessage(Long sessionId, String content, int tokens,
                                      String finishReason, int latencyMs) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole("assistant");
        msg.setContent(content);
        msg.setTokens(tokens);
        msg.setFinishReason(finishReason);
        msg.setLatencyMs(latencyMs);
        chatMessageMapper.insert(msg);

        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session != null) {
            session.setUpdatedAt(LocalDateTime.now());
            chatSessionMapper.updateById(session);
        }
    }
}
