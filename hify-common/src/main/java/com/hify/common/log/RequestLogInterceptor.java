package com.hify.common.log;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.opentelemetry.sdk.trace.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 请求级访问日志：
 *   - preHandle：生成 traceId 写入 MDC，并回写到响应头 X-Trace-Id
 *   - afterCompletion：打印 method / path / status / costMs，>1s 标 WARN，最后清理 MDC
 */
@Slf4j
@Component
public class RequestLogInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "_hify_request_start";

    private static final long SLOW_REQUEST_THRESHOLD_MS = 1_000L;

    private static final IdGenerator ID_GENERATOR = IdGenerator.random();

    private static final String TRACE_ID_PATTERN = "^[0-9a-f]{32}$";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = request.getHeader(TraceContext.HEADER_TRACE_ID);
        if (traceId == null || !traceId.matches(TRACE_ID_PATTERN)) {
            traceId = ID_GENERATOR.generateTraceId();
        }
        MDC.put(TraceContext.TRACE_ID, traceId);
        response.setHeader(TraceContext.HEADER_TRACE_ID, traceId);
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            Object startAttr = request.getAttribute(START_TIME_ATTR);
            long cost = startAttr == null ? -1 : System.currentTimeMillis() - (long) startAttr;
            int status = response.getStatus();
            String method = request.getMethod();
            String path = request.getRequestURI();

            if (cost >= SLOW_REQUEST_THRESHOLD_MS) {
                log.warn("SLOW {} {} status={} costMs={}", method, path, status, cost);
            } else {
                log.info("{} {} status={} costMs={}", method, path, status, cost);
            }
        } finally {
            MDC.remove(TraceContext.TRACE_ID);
        }
    }
}
