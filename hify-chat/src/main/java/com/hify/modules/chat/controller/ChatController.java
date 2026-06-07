package com.hify.modules.chat.controller;

import com.hify.common.web.Result;
import com.hify.modules.chat.dto.MessageResp;
import com.hify.modules.chat.dto.SendMessageReq;
import com.hify.modules.chat.dto.SessionCreateReq;
import com.hify.modules.chat.dto.SessionResp;
import com.hify.modules.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 对话引擎 RESTful 接口。会话有状态：显式创建会话，消息按会话定位。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat/sessions")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // ── 创建会话 ──
    @PostMapping
    public Result<SessionResp> createSession(@Valid @RequestBody SessionCreateReq req) {
        return Result.ok(chatService.createSession(req));
    }

    // ── 会话列表（按最近活跃倒序） ──
    @GetMapping
    public Result<List<SessionResp>> listSessions(@RequestParam(required = false) Long agentId) {
        return Result.ok(chatService.listSessions(agentId));
    }

    // ── 历史消息（游标分页，beforeId 为上一页最小 id，首页不传） ──
    @GetMapping("/{id}/messages")
    public Result<List<MessageResp>> listMessages(@PathVariable Long id,
                                                  @RequestParam(required = false) Long beforeId,
                                                  @RequestParam(defaultValue = "20") int size) {
        return Result.ok(chatService.listMessages(id, beforeId, size));
    }

    // ── 删除会话 ──
    @DeleteMapping("/{id}")
    public Result<Void> deleteSession(@PathVariable Long id) {
        chatService.deleteSession(id);
        return Result.ok();
    }

    // ── 发消息（流式 SSE） ──
    @PostMapping("/{id}/messages")
    public SseEmitter sendMessage(@PathVariable Long id,
                                  @Valid @RequestBody SendMessageReq req) {
        // emitter 生命周期、异步执行、超时/断开回调均由 service 内部管理
        return chatService.sendMessage(id, req.getContent());
    }
}
