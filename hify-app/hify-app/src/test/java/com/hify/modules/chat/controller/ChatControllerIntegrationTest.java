package com.hify.modules.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.modules.chat.dto.SendMessageReq;
import com.hify.modules.chat.dto.SessionCreateReq;
import com.hify.modules.chat.entity.ChatMessage;
import com.hify.modules.chat.entity.ChatSession;
import com.hify.modules.chat.mapper.ChatMessageMapper;
import com.hify.modules.chat.mapper.ChatSessionMapper;
import com.hify.modules.provider.entity.Provider;
import com.hify.modules.provider.entity.ModelConfig;
import com.hify.modules.provider.mapper.ProviderMapper;
import com.hify.modules.provider.mapper.ModelConfigMapper;
import com.hify.modules.agent.entity.Agent;
import com.hify.modules.agent.mapper.AgentMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 对话引擎集成测试
 *
 * 技术基础：
 * - @SpringBootTest(webEnvironment = RANDOM_PORT) + MockMvc
 * - @ActiveProfiles("mock") 使用 H2 内存库
 * - @Sql 插入独立测试数据
 * - @Transactional + @Rollback 回滚
 *
 * 测试场景：
 * 1. 完整对话链路：创建会话 → 发送消息 → 验证 SSE 流 → 验证数据库
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("mock")
@Transactional
@DisplayName("对话引擎集成测试")
class ChatControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ProviderMapper providerMapper;

    @Autowired
    private ModelConfigMapper modelConfigMapper;

    @Autowired
    private AgentMapper agentMapper;

    @Nested
    @DisplayName("场景1：完整对话链路测试")
    class ConversationTest {

        @Test
        @Sql(scripts = {"/schema-h2.sql", "/chat-test-data.sql"})
        @DisplayName("普通问答：从会话创建到 SSE 消息的完整流程")
        void should_complete_conversation_flow() throws Exception {
            // Given - SQL 已插入 provider + model_config + agent + chat_session 数据
            Long sessionId = 1L;
            String userMessage = "你好";

            // When - 发送消息
            SendMessageReq request = new SendMessageReq();
            request.setContent(userMessage);
            MvcResult result = mockMvc.perform(post("/api/v1/chat/sessions/{id}/messages", sessionId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andReturn();

            MockHttpServletResponse response = result.getResponse();
            assertThat(response.getContentType()).isEqualTo("text/event-stream");
            assertThat(response.getContentAsString()).isNotEmpty();

            // Then - 收集 SSE 事件流
            String deltaContent = collectSseContent(response);
            assertThat(deltaContent).isNotEmpty();

            // 等待异步消息保存完成
            Thread.sleep(1000); // 等待 LLM 模拟响应完成

            // 验证 ③ 查询 chat_message 表，role=user 和 role=assistant 各有一条记录
            List<ChatMessage> messages = chatMessageMapper.selectList(null)
                    .stream()
                    .filter(msg -> msg.getSessionId().equals(sessionId))
                    .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .toList();

            assertThat(messages).hasSize(2);

            ChatMessage userMsg = messages.stream()
                    .filter(msg -> "user".equals(msg.getRole()))
                    .findFirst()
                    .orElseThrow();

            ChatMessage assistantMsg = messages.stream()
                    .filter(msg -> "assistant".equals(msg.getRole()))
                    .findFirst()
                    .orElseThrow();

            assertThat(userMsg.getContent()).isEqualTo(userMessage);
            assertThat(assistantMsg.getContent()).isNotEmpty();
            assertThat(userMsg.getCreatedAt()).isNotNull();
            assertThat(assistantMsg.getCreatedAt()).isAfter(userMsg.getCreatedAt());

            // 验证 ④ assistant 消息的 content 与所有 delta 事件的 content 拼接结果一致
            assertThat(assistantMsg.getContent()).isEqualTo(deltaContent);
        }
    }

    /**
     * 从 SSE 流中收集所有增量内容
     */
    private String collectSseContent(MockHttpServletResponse response) throws Exception {
        String sseContent = response.getContentAsString();
        StringBuilder deltaContent = new StringBuilder();

        // SSE 流按 \n\n 分割事件
        String[] events = sseContent.split("\n\n");

        for (String event : events) {
            if (event.trim().isEmpty()) continue;

            // 检查是否是 delta 事件
            if (event.contains("event:delta") && event.contains("data: ")) {
                // 提取 JSON 字符串
                String[] lines = event.split("\n");
                for (String line : lines) {
                    if (line.startsWith("data: ")) {
                        String jsonData = line.substring(6); // 移除 "data: "
                        try {
                            // 去除 JSON 引号
                            String content = objectMapper.readTree(jsonData).asText();
                            deltaContent.append(content);
                        } catch (Exception e) {
                            // 忽略解析错误
                        }
                        break;
                    }
                }
            }

            // 检查是否是 done 事件
            if (event.contains("event:done")) {
                // 收集完成
                break;
            }
        }

        return deltaContent.toString();
    }
}