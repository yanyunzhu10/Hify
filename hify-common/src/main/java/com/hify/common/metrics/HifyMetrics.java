package com.hify.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Hify 业务指标统一入口。
 * <p>
 * Micrometer 使用点号命名，Prometheus 导出时会转换为下划线格式。
 * </p>
 */
@Component
public class HifyMetrics {

    private static final String UNKNOWN = "unknown";

    private static final String CHAT_REQUESTS = "hify.chat.requests";
    private static final String CHAT_REQUEST_DURATION = "hify.chat.request.duration";
    private static final String LLM_CALLS = "hify.llm.calls";
    private static final String LLM_CALL_DURATION = "hify.llm.call.duration";
    private static final String MCP_TOOL_CALLS = "hify.mcp.tool.calls";
    private static final String MCP_TOOL_CALL_DURATION = "hify.mcp.tool.call.duration";

    private final MeterRegistry meterRegistry;

    public HifyMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistry = meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new);
    }

    public void recordChatRequest(String agentId, String outcome, Duration duration) {
        String safeAgentId = safeTag(agentId);
        String safeOutcome = safeTag(outcome);
        Counter.builder(CHAT_REQUESTS)
                .tag("agent_id", safeAgentId)
                .tag("outcome", safeOutcome)
                .register(meterRegistry)
                .increment();
        Timer.builder(CHAT_REQUEST_DURATION)
                .tag("agent_id", safeAgentId)
                .tag("outcome", safeOutcome)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(safeDuration(duration));
    }

    public void recordLlmCall(String provider, String model, String stream,
                              String phase, String outcome, Duration duration) {
        String safeProvider = safeTag(provider);
        String safeModel = safeTag(model);
        String safeStream = safeTag(stream);
        String safePhase = safeTag(phase);
        String safeOutcome = safeTag(outcome);
        Counter.builder(LLM_CALLS)
                .tag("provider", safeProvider)
                .tag("model", safeModel)
                .tag("stream", safeStream)
                .tag("phase", safePhase)
                .tag("outcome", safeOutcome)
                .register(meterRegistry)
                .increment();
        Timer.builder(LLM_CALL_DURATION)
                .tag("provider", safeProvider)
                .tag("model", safeModel)
                .tag("stream", safeStream)
                .tag("phase", safePhase)
                .tag("outcome", safeOutcome)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(safeDuration(duration));
    }

    public void recordMcpToolCall(String serverId, String toolName, String outcome, Duration duration) {
        String safeServerId = safeTag(serverId);
        String safeToolName = safeTag(toolName);
        String safeOutcome = safeTag(outcome);
        Counter.builder(MCP_TOOL_CALLS)
                .tag("server_id", safeServerId)
                .tag("tool", safeToolName)
                .tag("outcome", safeOutcome)
                .register(meterRegistry)
                .increment();
        Timer.builder(MCP_TOOL_CALL_DURATION)
                .tag("server_id", safeServerId)
                .tag("tool", safeToolName)
                .tag("outcome", safeOutcome)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(safeDuration(duration));
    }

    private String safeTag(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        return value;
    }

    private Duration safeDuration(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return Duration.ZERO;
        }
        return duration;
    }
}
