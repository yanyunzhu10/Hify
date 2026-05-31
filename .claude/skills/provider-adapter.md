# Skill: 新增 LLM 供应商（Provider Adapter）

触发方式：当用户说"接入 XX 供应商"、"加一个 XX 模型供应商"、"支持 XX 的连通性测试"时，按此流程推进。

---

## 背景：为什么是策略模式

连通性测试最初用 `switch (type)` 分发 URL、认证头、响应解析，每加一个供应商就要改三处 `switch`，违反开闭原则。现已重构为**策略模式 + 工厂**：

- 每个供应商 = 一个 `ProviderAdapter` 实现，封装该供应商的全部 API 差异
- `ProviderAdapterFactory` 按 `type` 路由，Spring 自动注入所有 Adapter
- 新增供应商**只新增一个文件**，不改动任何已有 Adapter、Factory 或 Service

代码位置：`hify-provider/src/main/java/com/hify/modules/provider/adapter/`

```
adapter/
├── ProviderAdapter.java          ← 接口：supports / buildUrl / buildAuthHeaders / parseModelCount
├── AbstractProviderAdapter.java  ← 基类：extractApiKey / trimTrailingSlash / countArrayField
├── ProviderAdapterFactory.java   ← 工厂：按 type 路由，未匹配抛 PARAM_ERROR
├── OpenAiAdapter.java            ← openai + openai_compatible
├── AnthropicAdapter.java         ← anthropic
├── OllamaAdapter.java            ← ollama（本地无鉴权）
└── GeminiAdapter.java            ← gemini
```

编排逻辑（HTTP 调用、健康状态保存）留在 `service/impl/ProviderConnectivityServiceImpl.java`，新增供应商**不需要碰它**。

---

## 总体原则

- 新增供应商的流程是固定的四步，**绝不修改已有 Adapter**
- 每步有明确产出物，编译通过再进下一步
- API 端点 / 认证方式 / 响应结构若有不确定，先查官方文档确认，不靠猜

---

## Step 1 — 分析供应商 API（不写代码）

**目标**：搞清三个差异点，这正是 `ProviderAdapter` 接口的三个方法。

**产出物**：一张差异表
| 维度 | 待确认内容 | 对应接口方法 |
|------|-----------|-------------|
| 探测端点 | "列出模型"的 URL 路径（如 `/v1/models`、`/api/tags`） | `buildUrl(baseUrl)` |
| 认证方式 | 请求头还是 query 参数？header 名是什么？（如 `Authorization: Bearer`、`x-api-key`、`x-goog-api-key`） | `buildAuthHeaders(provider)` |
| 响应结构 | 模型列表在哪个 JSON 字段（如 `data`、`models`） | `parseModelCount(body)` |
| 类型标识 | `provider.type` 取值（小写，如 `mistral`、`cohere`） | `supports(type)` |

**确认方式**：
- 查供应商官方 API 文档的 "List Models" 端点
- 必要时用 curl 实测一次：`curl -H 'Authorization: Bearer sk-xxx' https://api.xxx.com/v1/models`

**对照现有 Adapter 找最接近的范本**：
- 走 `Authorization: Bearer` + 响应 `data` 字段 → 仿 `OpenAiAdapter`（很多供应商兼容 OpenAI 协议，直接归到 `openai_compatible` 可能就够了，无需新建）
- 自定义 header + 版本头 → 仿 `AnthropicAdapter`
- 无鉴权 → 仿 `OllamaAdapter`
- query 参数或特殊 header 鉴权 → 仿 `GeminiAdapter`

> ⚠️ **先判断是否真的需要新 Adapter**：若新供应商完全兼容 OpenAI 协议（`/v1/models` + `Bearer` + `data`），让用户直接用 `openai_compatible` 类型即可，**不写代码**。

---

## Step 2 — 实现 Adapter

**目标**：新增一个 `ProviderAdapter` 实现，封装 Step 1 的差异。

**产出物**：`adapter/XxxAdapter.java`

**模板**（继承 `AbstractProviderAdapter` 复用 apiKey 提取和响应解析）：
```java
package com.hify.modules.provider.adapter;

import com.hify.modules.provider.entity.Provider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Xxx 适配器。
 * <ul>
 *   <li>探测端点：{baseUrl}/v1/models</li>
 *   <li>认证：Authorization: Bearer {apiKey}</li>
 *   <li>响应：{"data": [...]}</li>
 * </ul>
 */
@Component
public class XxxAdapter extends AbstractProviderAdapter {

    @Override
    public boolean supports(String type) {
        return "xxx".equals(type);   // type 已由 Factory 规范化为小写
    }

    @Override
    public String buildUrl(String baseUrl) {
        return trimTrailingSlash(baseUrl) + "/v1/models";
    }

    @Override
    public Map<String, String> buildAuthHeaders(Provider provider) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + extractApiKey(provider));
        return headers;
    }

    @Override
    public int parseModelCount(String responseBody) {
        return countArrayField(responseBody, "data");
    }
}
```

**注意事项**：
- **必须加 `@Component`**，否则 Spring 不会注入到 Factory，路由时直接抛"不支持的供应商类型"
- `supports()` 里的 type 字面量用**小写**，Factory 已做 `toLowerCase()` 规范化
- 无鉴权供应商：`buildAuthHeaders` 返回 `new HashMap<>()`，不要返回 null
- 鉴权 key 缺失/为空交给基类 `extractApiKey()` 抛 `BizException(PARAM_ERROR)`，不要自己重写校验
- `parseModelCount` 解析失败应返回 0（连通性已成功，模型数只是附加信息），基类 `countArrayField` 已做异常兜底

---

## Step 3 — 注册到 Factory（通常无需改代码）

**目标**：确认 Factory 能路由到新 Adapter。

**关键**：`ProviderAdapterFactory` 通过 `List<ProviderAdapter>` 构造注入，Spring 自动收集所有 `@Component` 的 Adapter。**只要 Step 2 加了 `@Component`，注册就自动完成，无需改 Factory。**

**需要改动 Factory 的唯一情况**：多个 Adapter 的 `supports()` 都返回 true（路由冲突）。`Factory.get()` 用 `findFirst()` 取第一个匹配，顺序不确定。**确保每个 type 只被一个 Adapter 的 `supports()` 命中。**

**验证 type 不冲突**：检查所有 Adapter 的 `supports()`，确认新 type 不会被已有 Adapter 误匹配（尤其注意 `OpenAiAdapter` 同时认 `openai` 和 `openai_compatible`）。

---

## Step 4 — 验证

**目标**：编译通过 + 连通性测试实际可用。

**编译**：
```bash
./mvnw clean compile -pl hify-provider -am -DskipTests
```

**启动后端**（mock profile，H2 内存库）：
```bash
java -jar hify-app/target/hify-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=mock
```

**实测连通性**：先创建一个该类型的 Provider，再调测试接口：
```bash
# 创建（type 用新供应商标识，authConfig 填真实 key）
curl -s -X POST http://localhost:8080/api/v1/providers \
  -H 'Content-Type: application/json' \
  -d '{"name":"Xxx测试","type":"xxx","baseUrl":"https://api.xxx.com","authConfig":{"apiKey":"sk-xxx"},"enabled":1}' | jq .

# 连通性测试（用上一步返回的 id）
curl -s -X POST http://localhost:8080/api/v1/providers/1/test-connection | jq .
```

**预期结果**：
```json
{ "code": 0, "data": { "success": true, "latencyMs": 320, "modelCount": 15 } }
```

**验证清单**：
- [ ] 编译无错误
- [ ] `success: true`，`modelCount > 0`（说明 URL、认证头、响应解析都对了）
- [ ] 故意填错 apiKey → `success: false`，errorMessage 含认证失败（验证错误路径）
- [ ] 数据库 `t_provider_health` 有该 provider 的记录（status=UP / DOWN）

> ⚠️ **等待用户确认**：连通性测试通过后再考虑是否需要前端下拉框加该类型选项。

---

## 常见坑速查

| 现象 | 原因 | 修复 |
|------|------|------|
| 测试报"不支持的供应商类型" | Adapter 没加 `@Component`，或 `supports()` 字面量大小写不符 | 加 `@Component`；type 用小写 |
| 两个供应商互相串了 URL/认证 | 两个 Adapter 的 `supports()` 都命中同一 type | 检查 supports 逻辑，确保互斥 |
| modelCount 总是 0 但 success=true | `parseModelCount` 的字段名传错（如该供应商用 `models` 却传了 `data`） | 对照实测响应体确认数组字段名 |
| apiKey 明明填了却报"缺少 apiKey" | authConfig 的 key 名不是 `apiKey`，或 Entity 的 JSON 反序列化为 null | 确认 `@TableName(autoResultMap=true)`；前端字段名对齐 |
| 新 Adapter 不生效 | 改了代码但跑的是旧 jar | 重新 `./mvnw package` 或 `compile` |

---

## 相关文件

- `adapter/ProviderAdapter.java` — 接口定义（三个差异方法）
- `adapter/AbstractProviderAdapter.java` — 复用基类
- `adapter/ProviderAdapterFactory.java` — 路由工厂
- `service/impl/ProviderConnectivityServiceImpl.java` — 编排（新增供应商不改）
- `entity/Provider.java` — type / baseUrl / authConfig 字段
