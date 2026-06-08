package com.hify.common.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文本向量化客户端（OpenAI 兼容 /embeddings 接口）。
 * <p>
 * 文档分块入库与对话检索两侧必须使用同一模型 / 同一维度，否则向量空间不一致、检索无意义。
 * 通过 {@code hify.embedding.*} 配置；未启用或缺 api-key 时调用直接抛错（避免再写入零向量）。
 * </p>
 * <p>
 * <b>缓存</b>：embedding 是确定性的——同一模型同一文本每次返回完全相同的向量，故可缓存。
 * 单条 {@link #embed(String)} 按 {@code model + 文本 SHA-256} 缓存到 Redis，命中则跳过 HTTP 调用。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingClient {

    private static final String CACHE_PREFIX = "hify:embed:";
    private static final Duration CACHE_TTL = Duration.ofDays(7);

    private final LlmHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${hify.embedding.enabled:false}")
    private boolean enabled;

    /** OpenAI 兼容服务的基础地址，如 https://api.openai.com/v1 */
    @Value("${hify.embedding.base-url:}")
    private String baseUrl;

    @Value("${hify.embedding.api-key:}")
    private String apiKey;

    @Value("${hify.embedding.model:text-embedding-3-small}")
    private String model;

    /** 向量维度，必须与 t_document_chunk.embedding vector(N) 一致 */
    @Value("${hify.embedding.dimensions:1536}")
    private int dimensions;

    public boolean isEnabled() {
        return enabled;
    }

    public int dimensions() {
        return dimensions;
    }

    /**
     * 单条文本向量化（带 Redis 缓存）。
     * <p>embedding 确定性 → 同文本同模型向量恒等，先查缓存命中即返回，未命中调用后回填。</p>
     */
    public float[] embed(String text) {
        String key = cacheKey(text);
        float[] cached = readCache(key);
        if (cached != null) {
            return cached;
        }
        float[] vec = embedBatch(List.of(text)).get(0);
        writeCache(key, vec);
        return vec;
    }

    /**
     * 批量向量化。返回顺序与入参一致。
     *
     * @throws BizException 未配置 / 调用失败 / 维度不符
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (!enabled || !StringUtils.hasText(baseUrl) || !StringUtils.hasText(apiKey)) {
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "embedding 未配置：请设置 hify.embedding.enabled/base-url/api-key");
        }
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", model);
            payload.put("input", texts);
            String body = objectMapper.writeValueAsString(payload);

            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + apiKey);

            String resp = httpClient.post(embeddingsUrl(), headers, body);

            // 解析 { "data": [ { "embedding": [...], "index": 0 }, ... ] }
            JsonNode data = objectMapper.readTree(resp).path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw new BizException(ErrorCode.INTERNAL_ERROR, "embedding 响应无 data 字段");
            }
            // 按 index 归位，保证顺序与入参一致
            List<float[]> result = new ArrayList<>(texts.size());
            for (int i = 0; i < texts.size(); i++) {
                result.add(null);
            }
            for (JsonNode item : data) {
                int index = item.path("index").asInt();
                JsonNode emb = item.path("embedding");
                float[] vec = new float[emb.size()];
                for (int i = 0; i < emb.size(); i++) {
                    vec[i] = (float) emb.get(i).asDouble();
                }
                if (vec.length != dimensions) {
                    throw new BizException(ErrorCode.INTERNAL_ERROR,
                            "embedding 维度不符：期望 " + dimensions + " 实际 " + vec.length
                                    + "（检查 model 与 t_document_chunk 列维度是否一致）");
                }
                if (index >= 0 && index < result.size()) {
                    result.set(index, vec);
                }
            }
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "embedding 调用失败: " + e.getMessage(), e);
        }
    }

    /** baseUrl 末尾拼 /embeddings（兼容是否带尾斜杠） */
    private String embeddingsUrl() {
        String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return b + "/embeddings";
    }

    // ================================================================
    // 缓存（key = model + 文本 SHA-256；value = 逗号分隔的 float）
    // ================================================================

    private String cacheKey(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((model + ":" + text).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(CACHE_PREFIX);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16))
                  .append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            // 摘要失败不应阻断主流程，退化为不缓存（返回 null key 由调用方跳过）
            return null;
        }
    }

    private float[] readCache(String key) {
        if (key == null) return null;
        try {
            String val = stringRedisTemplate.opsForValue().get(key);
            if (val == null || val.isEmpty()) return null;
            String[] parts = val.split(",");
            float[] vec = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                vec[i] = Float.parseFloat(parts[i]);
            }
            return vec;
        } catch (Exception e) {
            return null; // 缓存读失败不影响主流程
        }
    }

    private void writeCache(String key, float[] vec) {
        if (key == null || vec == null) return;
        try {
            StringBuilder sb = new StringBuilder(vec.length * 8);
            for (int i = 0; i < vec.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(vec[i]);
            }
            stringRedisTemplate.opsForValue().set(key, sb.toString(), CACHE_TTL);
        } catch (Exception e) {
            // 缓存写失败不影响主流程
            log.debug("embedding 缓存写入失败", e);
        }
    }
}
