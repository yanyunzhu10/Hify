package com.hify.modules.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.modules.chat.dto.SendMessageReq;
import com.hify.modules.chat.entity.ChatMessage;
import com.hify.modules.chat.entity.ChatSession;
import com.hify.modules.chat.mapper.ChatMessageMapper;
import com.hify.modules.chat.mapper.ChatSessionMapper;
import com.hify.modules.mcp.entity.McpServer;
import com.hify.modules.mcp.entity.McpTool;
import com.hify.modules.mcp.mapper.McpServerMapper;
import com.hify.modules.mcp.mapper.McpToolMapper;
import com.hify.modules.mcp.service.McpClientService;
import com.hify.modules.provider.entity.Provider;
import com.hify.modules.provider.entity.ModelConfig;
import com.hify.modules.provider.mapper.ProviderMapper;
import com.hify.modules.provider.mapper.ModelConfigMapper;
import com.hify.modules.agent.entity.Agent;
import com.hify.modules.agent.entity.AgentTool;
import com.hify.modules.agent.mapper.AgentMapper;
import com.hify.modules.agent.mapper.AgentToolMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
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
 * - @MockBean 用于模拟依赖
 *
 * 测试场景：
 * 1. 完整对话链路：创建会话 → 发送消息 → 验证 SSE 流 → 验证数据库
 * 2. Function Calling 两轮链路：工具调用 → 验证工具执行 → 验证最终回答
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("mock")
@Import({com.hify.modules.chat.config.TestConfig.class})  // 导入测试配置
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

    @Autowired
    private McpServerMapper mcpServerMapper;

    @Autowired
    private McpToolMapper mcpToolMapper;

    @Autowired
    private AgentToolMapper agentToolMapper;

    @Autowired
    private com.hify.modules.chat.config.TestConfig.MockProviderAdapter mockProviderAdapter;

    // 使用简化的模拟，不依赖实际的 McpClientService
    private McpClientService mcpClientService = mock(McpClientService.class);

    @Captor
    private ArgumentCaptor<McpServer> serverCaptor;

    @Captor
    private ArgumentCaptor<String> toolNameCaptor;

    @Captor
    private ArgumentCaptor<Map<String, Object>> argsCaptor;

    @Nested
    @DisplayName("场景1：完整对话链路测试")
    class ConversationTest {

        @Test
        @Sql(scripts = {"/sql/schema-h2.sql", "/sql/chat-test-data.sql"})
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
            String sseContent = response.getContentAsString();
            String deltaContent = collectSseContent(sseContent);
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

    @Nested
    @DisplayName("场景2：Function Calling 两轮链路测试")
    class FunctionCallingTest {

        @Test
        @Sql(scripts = {"/sql/schema-h2.sql", "/sql/function-calling-test-data.sql"})
        @DisplayName("工具调用：第一轮返回 tool_calls，第二轮返回最终答案")
        void should_complete_function_calling_flow() throws Exception {
            // 配置 MockProviderAdapter 在第一轮返回 tool_calls
            mockProviderAdapter.setShouldReturnToolCalls(true);
            // Given - SQL 已插入 provider + model_config + agent + mcp_server + agent_tool + chat_session
            Long sessionId = 1L;
            String userMessage = "帮我查询一下订单123的退款资格";

            // Mock McpClientService 的 callTool
            when(mcpClientService.callTool(any(McpServer.class), eq("check_refund_eligibility"), any()))
                    .thenReturn("该订单符合退款条件，可以申请退款");

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

            // Then - 收集 SSE 事件流
            String sseContent = response.getContentAsString();
            String finalContent = collectSseContent(sseContent);
            assertThat(finalContent).isNotEmpty();
            assertThat(finalContent).contains("退款条件");

            // 等待异步消息保存完成
            Thread.sleep(1000);

            // 验证 ① SSE 流正常结束，最后事件是 type=done
            assertThat(sseContent).contains("event:done");

            // 验证 ② MySQL 只有一条 assistant 消息（两轮 LLM 只落一次）
            List<ChatMessage> messages = chatMessageMapper.selectList(null)
                    .stream()
                    .filter(msg -> msg.getSessionId().equals(sessionId))
                    .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .toList();

            assertThat(messages).hasSize(2); // user + assistant

            ChatMessage userMsg = messages.stream()
                    .filter(msg -> "user".equals(msg.getRole()))
                    .findFirst()
                    .orElseThrow();

            ChatMessage assistantMsg = messages.stream()
                    .filter(msg -> "assistant".equals(msg.getRole()))
                    .findFirst()
                    .orElseThrow();

            assertThat(userMsg.getContent()).isEqualTo(userMessage);
            assertThat(assistantMsg.getContent()).isEqualTo(finalContent);
            assertThat(assistantMsg.getFinishReason()).isEqualTo("stop");

            // 验证 ③ verify(mcpService).callTool()
            verify(mcpClientService, times(1)).callTool(
                    serverCaptor.capture(),
                    toolNameCaptor.capture(),
                    argsCaptor.capture()
            );

            assertThat(toolNameCaptor.getValue()).isEqualTo("check_refund_eligibility");
            @SuppressWarnings("unchecked")
            Map<String, Object> args = argsCaptor.getValue();
            assertThat(args).containsKey("orderId");
            assertThat(args.get("orderId")).isEqualTo("123");
        }

        @Test
        @Sql(scripts = {"/sql/schema-h2.sql", "/sql/function-calling-test-data.sql"})
        @DisplayName("工具调用失败时，SSE 流仍然正常结束，不挂起")
        void should_complete_function_calling_flow_when_tool_fails() throws Exception {
            // Given - SQL 已插入 provider + model_config + agent + mcp_server + agent_tool + chat_session
            Long sessionId = 1L;
            String userMessage = "帮我查询一下订单123的退款资格";

            // Mock McpClientService 的 callTool（这次 callTool 抛异常）
            when(mcpClientService.callTool(any(McpServer.class), eq("check_refund_eligibility"), any()))
                    .thenThrow(new RuntimeException("工具调用失败：连接超时"));

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

            // Then - 收集 SSE 事件流
            String sseContent = response.getContentAsString();
            String finalContent = collectSseContent(sseContent);
            assertThat(finalContent).isNotEmpty();
            assertThat(finalContent).contains("工具调用失败");

            // 验证 ① SSE 流正常结束，最后事件是 type=done
            assertThat(sseContent).contains("event:done");

            // 等待异步消息保存完成
            Thread.sleep(1000);

            // 验证 ② MySQL 只有一条 assistant 消息，finish_reason 为 error
            List<ChatMessage> messages = chatMessageMapper.selectList(null)
                    .stream()
                    .filter(msg -> msg.getSessionId().equals(sessionId))
                    .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .toList();

            assertThat(messages).hasSize(2); // user + assistant

            ChatMessage assistantMsg = messages.stream()
                    .filter(msg -> "assistant".equals(msg.getRole()))
                    .findFirst()
                    .orElseThrow();

            assertThat(assistantMsg.getContent()).contains("工具调用失败");
            assertThat(assistantMsg.getFinishReason()).isEqualTo("error");

            // 验证 ③ verify(mcpService).callTool() 被调用了
            verify(mcpClientService, times(1)).callTool(
                    any(McpServer.class),
                    eq("check_refund_eligibility"),
                    any(Map.class)
            );
        }

        private McpTool createMockToolSchema() {
            McpTool tool = new McpTool();
            tool.setId(1L);
            tool.setMcpServerId(1L);
            tool.setName("check_refund_eligibility");
            tool.setDescription("检查订单是否符合退款条件");

            // 创建 input schema
            Map<String, Object> schema = Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "orderId", Map.of(
                                    "type", "string",
                                    "description", "订单ID"
                            )
                    ),
                    "required", List.of("orderId")
            );

            tool.setInputSchema(schema);
            return tool;
        }
    }

    /**
     * 从 SSE 流中收集所有增量内容
     */
    private String collectSseContent(String sseContent) {
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