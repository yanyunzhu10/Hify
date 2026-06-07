package com.hify.common.http;

import com.hify.common.exception.LlmApiException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;

/**
 * LLM 通用 HTTP 客户端：
 *   - 普通请求走 RestTemplate（connect=5s, read=60s），上层用 CompletableFuture 兜底总超时
 *   - 流式 SSE 走 OkHttp（connect=5s, read=0，长连接不能有读超时）
 * 异常统一转为 {@link LlmApiException}，按 TIMEOUT / AUTH_FAILED / RATE_LIMITED 区分。
 */
@Slf4j
@Component
public class LlmHttpClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final RestTemplate restTemplate;
    private final OkHttpClient okHttpClient;

    /** 短超时 GET 专用（连通性测试）：connect=5s, read=10s */
    private final OkHttpClient shortTimeoutClient;

    public LlmHttpClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(60_000);
        this.restTemplate = new RestTemplate(factory);

        // 流式 SSE：readTimeout 必须为 0，否则 LLM 长输出会被客户端误断
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build();

        // 短超时客户端：连通性测试等非流式 GET 场景
        this.shortTimeoutClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    /** 非流式 POST，返回响应体字符串 */
    public String post(String url, Map<String, String> headers, String body) {
        long start = System.currentTimeMillis();
        int statusCode = -1;
        try {
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            if (headers != null) {
                headers.forEach(httpHeaders::add);
            }
            HttpEntity<String> entity = new HttpEntity<>(body, httpHeaders);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            statusCode = response.getStatusCode().value();
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            statusCode = e.getStatusCode().value();
            throw mapHttpError(statusCode, e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException || cause instanceof ConnectException) {
                throw new LlmApiException(LlmApiException.Type.TIMEOUT, -1,
                        "LLM 请求超时: " + url, e);
            }
            throw new LlmApiException(LlmApiException.Type.OTHER, -1,
                    "LLM 网络错误: " + e.getMessage(), e);
        } catch (LlmApiException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmApiException(LlmApiException.Type.OTHER, statusCode,
                    "LLM 调用失败: " + e.getMessage(), e);
        } finally {
            log.info("LLM POST url={} status={} costMs={}",
                    url, statusCode, System.currentTimeMillis() - start);
        }
    }

    /** 流式 POST：逐行回调（适用于 SSE，调用方自行解析 "data: ..." 等前缀） */
    public void stream(String url, Map<String, String> headers, String body, Consumer<String> callback) {
        long start = System.currentTimeMillis();
        int statusCode = -1;
        try {
            Request.Builder builder = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body, JSON));
            if (headers != null) {
                headers.forEach(builder::addHeader);
            }

            try (Response response = okHttpClient.newCall(builder.build()).execute()) {
                statusCode = response.code();
                ResponseBody respBody = response.body();
                if (!response.isSuccessful()) {
                    String errorBody = respBody == null ? "" : respBody.string();
                    throw mapHttpError(statusCode, errorBody, null);
                }
                if (respBody == null) {
                    throw new LlmApiException(LlmApiException.Type.OTHER, statusCode,
                            "LLM 流式响应体为空");
                }
                BufferedSource source = respBody.source();
                String line;
                while ((line = source.readUtf8Line()) != null) {
                    callback.accept(line);
                }
            }
        } catch (LlmApiException e) {
            throw e;
        } catch (SocketTimeoutException | ConnectException e) {
            throw new LlmApiException(LlmApiException.Type.TIMEOUT, statusCode,
                    "LLM 流式请求超时: " + url, e);
        } catch (Exception e) {
            throw new LlmApiException(LlmApiException.Type.OTHER, statusCode,
                    "LLM 流式调用失败: " + e.getMessage(), e);
        } finally {
            log.info("LLM STREAM url={} status={} costMs={}",
                    url, statusCode, System.currentTimeMillis() - start);
        }
    }

    /**
     * 流式 POST（可取消版），与 {@link #stream(String, Map, String, Consumer)} 相同，
     * 但每读取一行后检查 {@code shouldCancel}，若返回 true 则中断读取并正常返回。
     * 适用于客户端断开时主动中止 HTTP 连接，避免 llm 线程空跑。
     *
     * @return true 表示正常结束，false 表示被中断（shouldCancel 返回了 true）
     */
    public boolean streamCancellable(String url, Map<String, String> headers, String body,
                                      Consumer<String> callback,
                                      BooleanSupplier shouldCancel) {
        long start = System.currentTimeMillis();
        int statusCode = -1;
        try {
            Request.Builder builder = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body, JSON));
            if (headers != null) {
                headers.forEach(builder::addHeader);
            }

            try (Response response = okHttpClient.newCall(builder.build()).execute()) {
                statusCode = response.code();
                ResponseBody respBody = response.body();
                if (!response.isSuccessful()) {
                    String errorBody = respBody == null ? "" : respBody.string();
                    throw mapHttpError(statusCode, errorBody, null);
                }
                if (respBody == null) {
                    throw new LlmApiException(LlmApiException.Type.OTHER, statusCode,
                            "LLM 流式响应体为空");
                }
                BufferedSource source = respBody.source();
                String line;
                while ((line = source.readUtf8Line()) != null) {
                    if (shouldCancel.getAsBoolean()) {
                        log.info("LLM STREAM cancelled url={} status={} costMs={}",
                                url, statusCode, System.currentTimeMillis() - start);
                        return false;
                    }
                    callback.accept(line);
                }
            }
            return true;
        } catch (LlmApiException e) {
            throw e;
        } catch (SocketTimeoutException | ConnectException e) {
            throw new LlmApiException(LlmApiException.Type.TIMEOUT, statusCode,
                    "LLM 流式请求超时: " + url, e);
        } catch (Exception e) {
            throw new LlmApiException(LlmApiException.Type.OTHER, statusCode,
                    "LLM 流式调用失败: " + e.getMessage(), e);
        } finally {
            log.info("LLM STREAM url={} status={} costMs={}",
                    url, statusCode, System.currentTimeMillis() - start);
        }
    }

    /** 非流式 GET（连通性测试），使用短超时客户端。 */
    public String get(String url, Map<String, String> headers) {
        long start = System.currentTimeMillis();
        int statusCode = -1;
        try {
            Request.Builder builder = new Request.Builder().url(url).get();
            if (headers != null) {
                headers.forEach(builder::addHeader);
            }

            try (Response response = shortTimeoutClient.newCall(builder.build()).execute()) {
                statusCode = response.code();
                if (!response.isSuccessful()) {
                    ResponseBody respBody = response.body();
                    String errorBody = respBody == null ? "" : respBody.string();
                    throw mapHttpError(statusCode, errorBody, null);
                }
                ResponseBody respBody = response.body();
                return respBody != null ? respBody.string() : "";
            }
        } catch (LlmApiException e) {
            throw e;
        } catch (SocketTimeoutException | ConnectException e) {
            throw new LlmApiException(LlmApiException.Type.TIMEOUT, statusCode,
                    "LLM GET 请求超时: " + url, e);
        } catch (Exception e) {
            throw new LlmApiException(LlmApiException.Type.OTHER, statusCode,
                    "LLM GET 调用失败: " + e.getMessage(), e);
        } finally {
            log.info("LLM GET url={} status={} costMs={}",
                    url, statusCode, System.currentTimeMillis() - start);
        }
    }

    private LlmApiException mapHttpError(int statusCode, String errorBody, Throwable cause) {
        if (statusCode == 401 || statusCode == 403) {
            return new LlmApiException(LlmApiException.Type.AUTH_FAILED, statusCode,
                    "LLM 认证失败: " + errorBody, cause);
        }
        if (statusCode == 429) {
            return new LlmApiException(LlmApiException.Type.RATE_LIMITED, statusCode,
                    "LLM 限流: " + errorBody, cause);
        }
        return new LlmApiException(LlmApiException.Type.OTHER, statusCode,
                "LLM 调用失败 HTTP " + statusCode + ": " + errorBody, cause);
    }
}
