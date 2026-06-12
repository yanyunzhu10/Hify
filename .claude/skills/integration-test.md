# Skill: 集成测试渐进式交付

## 触发方式

当用户输入以下命令时触发本技能：

```text
/集成测试 [模块名或核心链路]
```

示例：

```text
/集成测试 provider
/集成测试 chat
/集成测试 对话链路
/集成测试 knowledge 文档上传到检索
```

触发后必须按本文件流程执行：**先读配置、再规划清单、不直接写代码；从最简单场景开始，单场景红绿推进，不批量生成测试。**

---

## 总体原则

1. **先规划，不写代码**：第一轮只输出测试清单，按 P0/P1/P2 排序。
2. **先确认技术基础**：必须先读取 mock profile、H2、测试 SQL、已有测试配置。
3. **真实 DB，mock 外部 API**：数据库使用真实 H2；LLM/MCP/HTTP/Redis/文件系统等外部依赖按策略 mock。
4. **每个测试类独立数据**：使用 `@Sql` 准备独立数据；测试之间不共享数据。
5. **场景递进**：从最简单 happy path 开始，跑通后再写下一个场景。
6. **已知 bug 走红绿流程**：先写失败测试，确认红，再修 bug，最后确认绿。
7. **不批量生成**：一次只实现一个 IT 场景，避免大量失败难以定位。

---

## Step 1 — 读取配置文件并确认技术基础

触发后第一步必须读取以下文件，确认测试运行基础，不得跳过：

### 1. 项目规范

- `CLAUDE.md`
  - 确认模块边界、核心链路地图、数据库规范、mock profile 约定。
  - 基于核心链路地图划分 P0/P1/P2。

### 2. 测试 profile / H2 配置

优先查找并读取：

- `hify-app/src/test/resources/application-mock.yml`
- `hify-*/src/test/resources/application-mock.yml`
- `hify-*/src/test/resources/application-test.yml`
- `hify-*/src/test/resources/application.yml`

确认内容：

| 检查项 | 需要确认 |
|--------|----------|
| Active profile | 是否存在 `mock` 或 test profile |
| 数据源 | 是否使用 H2 内存库 |
| MyBatis-Plus | mapper scan、逻辑删除、字段映射是否与测试一致 |
| Redis | mock profile 是否会连真实 Redis；如会，测试中需 mock `RedisTemplate` / cache service |
| pgvector | 是否有独立 datasource；集成测试是否需要 mock repository 或 Testcontainers |
| 外部 API | 是否已有 mock adapter / fake client / test config |

### 3. H2 schema 和测试 SQL

读取：

- `**/src/test/resources/sql/schema-h2.sql`
- `**/src/test/resources/sql/*test-data.sql`
- 目标模块已有 `*IntegrationTest.java`
- 目标模块已有 `TestConfig.java` / `Mock*Adapter.java`

确认内容：

| 检查项 | 需要确认 |
|--------|----------|
| schema 完整性 | H2 表字段是否覆盖当前 Entity 字段 |
| 测试数据隔离 | 每个数据脚本是否清理自身表数据，避免主键冲突 |
| JSON 字段 | H2 中是否用 `TEXT` / `CLOB` 替代 JSON/JSONB |
| 时间字段 | `created_at` / `updated_at` 是否有默认值或 MyBatis 自动填充 |
| 逻辑删除 | `deleted` 字段和 MP 查询条件是否一致 |

### 4. 输出技术基础确认

在测试清单前，先输出：

```markdown
## 技术基础确认

| 项 | 结论 | 文件 |
|----|------|------|
| mock profile | ✅ 使用 `mock` | `hify-app/src/test/resources/application-mock.yml` |
| DB | ✅ H2 内存库 | `...` |
| schema | ✅/⚠️ | `.../schema-h2.sql` |
| 外部 API mock | ✅/⚠️ | `.../TestConfig.java` |
| Redis/pgvector | mock / 不涉及 / 需补充 | `...` |

### 风险点
- [列出可能影响集成测试启动或断言的配置问题]
```

---

## Step 2 — 规划测试清单（不写代码）

基于 CLAUDE.md 的核心链路地图和目标模块职责，输出 P0/P1/P2 测试清单。

### 优先级定义

| 优先级 | 含义 | 典型场景 |
|--------|------|----------|
| P0 | 核心链路，不通则模块不可用 | CRUD 主流程、对话发送、文档上传、模型连通性 |
| P1 | 重要分支，影响真实使用 | 异常路径、分页、状态过滤、上下文、工具调用 |
| P2 | 边界/健壮性/回归 | 参数边界、并发、缓存失效、兼容性、历史 bug |

### 清单输出格式

必须用表格输出：

```markdown
## 集成测试清单：{模块名}

| IT 编号 | 场景 | 验证点 | 优先级 | 数据准备 | 外部依赖处理 |
|---------|------|--------|--------|----------|--------------|
| IT-01 | 创建 Provider | HTTP 200、Result.code=0、DB 落库、敏感字段不返回 | P0 | @Sql 插入前置数据或空库 | 无 |
| IT-02 | Provider 连通性测试 | 状态更新、health 记录、错误路径 | P0 | @Sql provider 数据 | mock LlmHttpClient |
| IT-03 | 对话上下文多轮正确性 | 第 3 轮携带前两轮历史，历史升序，最后一条是当前输入 | P1 | @Sql session + agent + model | mock ProviderAdapter/LLM 流 |
```

清单后必须给出下一步建议：

```markdown
## 建议执行顺序

先做 `IT-01 {最简单场景}`。
原因：依赖最少，只验证 SpringBootTest + H2 + MockMvc 基础链路是否可用。

请确认是否从 IT-01 开始；确认后我只写 IT-01，不会批量生成其他场景。
```

---

## Step 3 — Mock 策略决策表

集成测试默认 **真实 Spring 容器 + 真实 H2 DB + Mock 外部系统**。

| 依赖类型 | 默认策略 | 原因 | 常用实现 |
|----------|----------|------|----------|
| 主业务 DB | 真实 H2 | 验证 Mapper、事务、SQL、MyBatis-Plus 映射 | `@Sql` + H2 schema |
| Controller/Service | 真实 Bean | 验证完整 HTTP → Service → DB 链路 | `@SpringBootTest` + `MockMvc` |
| Mapper | 真实 Mapper | 验证真实 SQL 和实体映射 | MyBatis-Plus + H2 |
| LLM Provider HTTP | mock | 外部网络不稳定、费用、速度慢 | `@MockBean LlmHttpClient` 或 Test Adapter |
| ProviderAdapter | 可真实 Test Adapter / mock | 需要捕获 `ChatRequest` 或构造响应 | `TestConfig.MockProviderAdapter` |
| MCP 外部服务 | mock | 外部工具不可依赖 | `@MockBean McpClientService` |
| Redis / Cache | mock 或 fake | 单测环境通常无 Redis；避免状态污染 | `@MockBean ChatContextCache` / embedded fake |
| pgvector / 向量库 | mock repository，除非专门测 RAG SQL | H2 不支持 pgvector 算子 | `@MockBean ChunkRepository` 或 Testcontainers |
| 文件上传落盘 | 临时目录 | 需要验证文件流程时用真实临时目录 | `@TempDir` / test upload-dir |
| 异步线程池 | 真实或同步替身 | SSE/异步要验证完整链路；复杂时可换同步 Executor | `ThreadPoolConfig` 或 test executor |
| 时间/随机 ID | 固定值或断言非空 | 避免脆弱断言 | 不断言具体时间，只断言顺序/非空 |

### 决策规则

1. **只要是外部服务、网络、付费 API，都 mock。**
2. **只要是本模块数据库读写，都走真实 H2。**
3. **跨模块如果只是读配置/实体，可真实 Mapper；如果会触发外部系统，则 mock 到边界。**
4. **mock 必须服务于验证目标**：例如要验证 LLM 请求上下文，就 mock ProviderAdapter 并捕获 `ChatRequest`，不要只 mock 最终返回。

---

## Step 4 — 测试基类模板

标准模板如下，具体模块按实际依赖裁剪：

```java
package com.hify.modules.xxx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("mock")
@MapperScan("com.hify.**.mapper")
@Transactional
@DisplayName("{模块名}集成测试")
class XxxControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // 外部 API mock 掉；DB Mapper 不 mock，走真实 H2
    @MockBean
    private ExternalApiClient externalApiClient;

    @Test
    @Sql(scripts = {"/sql/schema-h2.sql", "/sql/xxx-test-data.sql"})
    @DisplayName("IT-01：最简单 happy path")
    void should_complete_happy_path() throws Exception {
        // Given
        given(externalApiClient.call(any())).willReturn("mock-response");

        XxxReq req = new XxxReq();
        req.setName("test");

        // When
        String body = objectMapper.writeValueAsString(req);
        String response = mockMvc.perform(post("/api/v1/xxx")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Then
        assertThat(response).contains("test");
        // 继续查 H2 DB 验证落库
    }
}
```

### SSE / 异步接口补充模板

如果接口返回 `SseEmitter` 或开启异步，需要使用 `asyncDispatch`：

```java
MvcResult result = mockMvc.perform(post("/api/v1/chat/sessions/{id}/messages", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andReturn();

if (result.getRequest().isAsyncStarted()) {
    result = mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andReturn();
}

assertThat(result.getResponse().getContentType()).startsWith("text/event-stream");
```

---

## Step 5 — 场景递进原则

测试实现必须按以下节奏推进：

1. **IT-01：最小 happy path**
   - 目标：证明 SpringBootTest、MockMvc、H2、@Sql 能跑通。
   - 只断言核心结果，不叠加复杂分支。

2. **IT-02：主流程增强验证**
   - 增加 DB 断言、响应字段断言、状态变化断言。

3. **IT-03：关键分支**
   - 如 function calling、fallback、分页、状态过滤、上下文窗口。

4. **IT-04+：异常和边界**
   - 外部 API 失败、参数非法、资源不存在、重复创建、权限/状态不允许。

5. **P2：回归和历史 bug**
   - 只有核心链路稳定后再补。

每完成一个场景后必须执行：

```bash
mvn -pl hify-{module} test -Dtest=XxxControllerIntegrationTest
```

或只跑当前方法：

```bash
mvn -pl hify-{module} test -Dtest='XxxControllerIntegrationTest#should_xxx'
```

如果测试失败，先分析失败原因，不继续写下一个测试。

---

## Step 6 — 已知 bug 的红绿流程

当用户说明“这是已知 bug”或“先写测试，应该失败，再修”时，必须执行红绿流程：

### 1. 先写失败测试

只写覆盖该 bug 的最小测试，不修代码。

输出：

```markdown
已写回归测试：`IT-03 对话上下文多轮正确性`
预期：当前应该失败，因为 `selectRecentBySessionId`/历史查询取错了消息窗口。
下一步：运行测试确认红。
```

### 2. 运行并确认红

必须运行目标测试，并把失败信息摘要给用户：

```markdown
## 红灯确认

命令：`mvn -pl hify-chat test -Dtest='ChatControllerIntegrationTest#should_pass_multi_turn_context_to_provider_in_chronological_order'`
结果：❌ 失败
失败点：第 3 次 ChatRequest.messages 未包含 `第一条` / `第二条`，或顺序错误。
结论：测试成功复现已知 bug。
```

### 3. 再修 bug

只修导致该测试失败的最小代码，不顺手重构无关逻辑。

### 4. 再跑并确认绿

```markdown
## 绿灯确认

命令：`...`
结果：✅ 通过
验证：第 3 轮 ChatRequest.messages 包含前两轮历史，历史按时间升序，最后一条是当前输入。
```

### 5. 最后跑相关测试类

如果当前方法绿，再跑整个相关测试类，确认没有破坏已有集成测试。

---

## 标准汇报格式

每完成一个场景后，用以下格式汇报：

```markdown
## IT-01 完成

| 项 | 结果 |
|----|------|
| 测试文件 | `...IntegrationTest.java` |
| 数据脚本 | `...test-data.sql` |
| Mock 策略 | LLM API mock，DB 真实 H2 |
| 运行命令 | `mvn -pl hify-provider test -Dtest=ProviderControllerIntegrationTest` |
| 结果 | ✅ 通过 / ❌ 失败 |

### 关键断言
- HTTP 200
- Result.code = 0
- DB 落库成功
- 外部 API 未真实调用

### 下一步
建议继续 IT-02：{场景名}
```

---

## 常见坑速查

| 现象 | 常见原因 | 处理方式 |
|------|----------|----------|
| `Unable to find @SpringBootConfiguration` | 子模块测试找不到启动类 | 指定 `@SpringBootTest(classes=...)` 或引入最小测试配置 |
| H2 主键冲突 | 多个 `@Sql` 共享内存库且未清理数据 | test-data.sql 开头 `DELETE FROM` 相关表 |
| H2 字段不存在 | schema-h2.sql 落后于 Entity | 先补 H2 schema，再跑测试 |
| `created_at` 为 null | 未引入 MyBatis-Plus 自动填充配置 | 引入 `MybatisPlusConfig` 或给 H2 默认值 |
| 异步接口响应为空 | 未使用 `asyncDispatch` | 对 `SseEmitter` / async MVC 使用 asyncDispatch |
| SSE content type 多 charset | `text/event-stream;charset=UTF-8` | 断言 `startsWith("text/event-stream")` |
| 外部 API 被真实调用 | mock 边界选错 | mock HTTP client / adapter / external service |
| Redis 连接失败 | mock profile 仍自动连 Redis | mock `RedisTemplate` 或封装的 cache service |
| pgvector SQL 在 H2 失败 | H2 不支持 vector 算子 | mock `ChunkRepository`，专测 pgvector 用 Testcontainers |
| Java 26 下 Mockito/ByteBuddy 失败 | ByteBuddy 不支持当前 class version | 使用 JDK 17/21 运行测试，或升级依赖 |

---

## 更新日志

- 2026-06-12：固化 Provider CRUD 与对话链路集成测试流程，支持 `/集成测试` 渐进式触发。