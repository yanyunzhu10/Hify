package com.hify.modules.chat.service;

import com.hify.modules.provider.dto.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话上下文工作内存（Redis）。
 * <p>
 * 专用于组装 LLM 请求：以 Redis List 维护每个会话最近 N 轮消息的滑动窗口，
 * 读写 O(1)，避免每轮对话都查 PG + 排序。PostgreSQL 仍是全量持久化的 source of truth，
 * 本缓存失效（TTL 到期或未命中）时由调用方从 PG 回填。
 * </p>
 * <ul>
 *   <li>key：{@code chat:ctx:{sessionId}}</li>
 *   <li>value：每条消息序列化为 {@link ChatRequest.Message}（role + content）</li>
 *   <li>窗口：maxContextTurns × 2 条（user/assistant 成对）</li>
 *   <li>TTL：2 小时无活动自动清理</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatContextCache {

    private static final String KEY_PREFIX = "chat:ctx:";
    private static final Duration TTL = Duration.ofHours(2);

    private final RedisTemplate<String, Object> redisTemplate;

    private String key(Long sessionId) {
        return KEY_PREFIX + sessionId;
    }

    /**
     * 取会话的上下文窗口。返回 null 表示缓存未命中（key 不存在），调用方应从 PG 回填。
     *
     * @return 窗口内消息（正序，最旧在前），或 null（未命中）
     */
    @SuppressWarnings("unchecked")
    public List<ChatRequest.Message> getWindow(Long sessionId) {
        String key = key(sessionId);
        Boolean exists = redisTemplate.hasKey(key);
        if (exists == null || !exists) {
            return null; // 未命中
        }
        List<Object> raw = redisTemplate.opsForList().range(key, 0, -1);
        if (raw == null) {
            return null;
        }
        List<ChatRequest.Message> messages = new ArrayList<>(raw.size());
        for (Object o : raw) {
            if (o instanceof ChatRequest.Message m) {
                messages.add(m);
            }
        }
        // 读到即续命，避免活跃会话被 TTL 清理
        redisTemplate.expire(key, TTL);
        return messages;
    }

    /**
     * 用 PG 查出的历史回填缓存（覆盖式：先删后写）。
     *
     * @param sessionId 会话 id
     * @param messages  正序的历史消息（最旧在前）
     */
    public void warmUp(Long sessionId, List<ChatRequest.Message> messages) {
        String key = key(sessionId);
        redisTemplate.delete(key);
        if (messages == null || messages.isEmpty()) {
            return;
        }
        redisTemplate.opsForList().rightPushAll(key, messages.toArray());
        redisTemplate.expire(key, TTL);
    }

    /**
     * 追加一轮对话（user + assistant），并裁剪到窗口大小。
     *
     * @param sessionId   会话 id
     * @param userMsg     用户消息
     * @param assistantMsg 助手回复
     * @param maxTurns    保留的最大轮数（每轮 2 条）
     */
    public void appendTurn(Long sessionId, ChatRequest.Message userMsg,
                           ChatRequest.Message assistantMsg, int maxTurns) {
        String key = key(sessionId);
        try {
            redisTemplate.opsForList().rightPush(key, userMsg);
            redisTemplate.opsForList().rightPush(key, assistantMsg);
            // 滑动窗口：只保留最近 maxTurns*2 条
            redisTemplate.opsForList().trim(key, -(maxTurns * 2L), -1);
            redisTemplate.expire(key, TTL);
        } catch (Exception e) {
            // 缓存写失败不影响主流程（PG 已落库），下次读未命中会从 PG 回填
            log.warn("对话上下文缓存写入失败 sessionId={}", sessionId, e);
        }
    }

    /** 会话删除时清理缓存。 */
    public void evict(Long sessionId) {
        redisTemplate.delete(key(sessionId));
    }
}
