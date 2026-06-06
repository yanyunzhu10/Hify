# Hify 项目开发规范

## 项目概览

Hify 是一个简化版内部 AI Agent 平台，基于 Dify 思路设计。

- **团队规模**：1 人开发，20-50 人内部使用，本地部署
- **技术栈**：Spring Boot + MyBatis-Plus + Vue + PostgreSQL 16 + Redis + pgvector
- **架构模式**：模块化单体（Modular Monolith），代码边界清晰，可平滑拆分为微服务

---

## 核心功能模块（MVP 范围）

| 模块 | 说明 |
|------|------|
| 模型管理 (model) | 管理 OpenAI / Claude / Gemini / Ollama 等 LLM 提供商配置，支持连通性测试 |
| Agent 配置 (agent) | 配置 Agent 名称、系统提示词、绑定模型、关联知识库和 MCP 工具 |
| 对话引擎 (conversation) | 多轮对话、历史记录、SSE 流式响应 |
| 知识库 RAG (knowledge) | 文档上传 → 异步向量化 → pgvector 余弦搜索 → 注入 LLM 上下文 |
| 简版工作流 (workflow) | 顺序节点执行：开始 → LLM → 条件分支 → 工具调用 → 结束 |
| MCP 工具接入 (mcp) | 接入外部 MCP 工具，供 Agent 和工作流调用 |

**砍掉的功能**：多租户、自定义插件市场、实时协作、企业 SSO、精细化权限控制、数据集版本管理。

---

## 代码组织规范

### 包结构

hify/                      ← artifactId: hify（父 POM）
├── hify-common/           ← Result<T>、BizException、GlobalExceptionHandler
├── hify-provider/         ← LLM 提供商（OkHttp + Resilience4j）
├── hify-mcp/              ← MCP 工具接入
├── hify-agent/            ← Agent 配置  → provider + mcp
├── hify-knowledge/        ← RAG（pgvector）
├── hify-workflow/         ← 工作流节点  → provider + mcp
├── hify-chat/             ← 对话引擎    → agent + provider + workflow + knowledge
├── hify-app/              ← 启动模块    → 全部 + Redis + Actuator
├── hify-web/src/          ← Vue 前端（无 pom）
└── deploy/docker | k8s/   ← 部署配置


完成。每个业务模块的最终结构：

hify-{module}/src/main/java/com/hify/modules/{module}/
├── {Module}Module.java      ← 占位类
├── controller/
├── service/
│   └── impl/
├── mapper/
├── entity/
├── dto/
├── config/
├── exception/
└── constant/


### 各层职责边界

| 层 | 职责 | 禁止 |
|----|------|------|
| web/ | 接收请求、参数校验（@Valid）、调用本模块 api/ 接口、返回 Result<T> | 直接调用其他模块 domain/、直接操作数据库 |
| api/ | 定义跨模块调用的 interface 和 DTO | 包含业务逻辑实现 |
| domain/ | 业务逻辑、领域对象、事务边界（@Transactional） | 直接依赖 Mapper、依赖 web 层 |
| infra/ | Mapper、RepositoryImpl（PO ↔ 领域对象转换）、外部调用 | 包含业务逻辑 |

### 跨模块调用规则

- **只能**通过目标模块的 `api/` 接口调用，禁止直接 import 其他模块的 `domain/` 或 `infra/` 类
- 跨模块传递使用 `api/` 包下定义的 DTO，不传递 PO 或领域对象
- 循环依赖视为架构错误，立即重构

```java
// 正确：agent 模块通过 ModelService（api/ 接口）调用 model 模块
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {
    private final ModelService modelService; // 来自 model 模块的 api/ 接口
}
```

---

## LLM 调用规范

### 线程池配置

```java
// llm-pool: 非流式调用（阻塞等待完整响应）
@Bean("llmExecutor")
public ThreadPoolExecutor llmExecutor() {
    return new ThreadPoolExecutor(20, 50, 60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(100),
        new ThreadFactoryBuilder().setNameFormat("llm-pool-%d").setDaemon(true).build(),
        new ThreadPoolExecutor.CallerRunsPolicy()  // 满载时调用方线程执行，不丢任务
    );
}

// llm-stream: 流式 SSE 调用（长连接）
@Bean("llmStreamExecutor")
public ThreadPoolExecutor llmStreamExecutor() {
    return new ThreadPoolExecutor(30, 80, 60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(50),
        new ThreadFactoryBuilder().setNameFormat("llm-stream-%d").setDaemon(true).build(),
        new AbortPolicy()  // 流式超限直接拒绝，由上层返回 503
    );
}
```

### HTTP 客户端（LlmHttpClient）

LLM 通用 HTTP 调用统一走 `com.hify.common.http.LlmHttpClient`：

- **普通请求 → RestTemplate**：connectTimeout = 5s，readTimeout = 60s
- **流式 SSE → OkHttp**：connectTimeout = 5s，readTimeout = 0（长连接不能有读超时，否则 LLM 长输出会被误断）

```java
// 非流式：RestTemplate
SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
factory.setConnectTimeout(5_000);
factory.setReadTimeout(60_000);
this.restTemplate = new RestTemplate(factory);

// 流式：OkHttp
this.okHttpClient = new OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.SECONDS)
    .build();
```

异常统一转为 `LlmApiException`，区分 `TIMEOUT` / `AUTH_FAILED` (401/403) / `RATE_LIMITED` (429) / `OTHER`。

### 超时层次（三层保护）

| 层 | 非流式 | 流式 SSE |
|----|--------|----------|
| 1. TCP 握手 | RestTemplate connectTimeout = 5s | OkHttp connectTimeout = 5s |
| 2. 单次读取 | RestTemplate readTimeout = 60s | 无（readTimeout = 0，SSE 必须） |
| 3. 总体兜底 | CompletableFuture.get(90s) 包 post() 调用 | 由 controller 层的 SseEmitter timeout 控制 |

**调用方必须用 `CompletableFuture.supplyAsync(..., llmExecutor).get(90, SECONDS)` 包住 `post()`**，因为 RestTemplate 没有"总超时"概念，单请求最长会卡到 60s read。

### 重试策略（Resilience4j）

- 普通 LLM：最多 3 次，初始等待 500ms，指数退避 2x，最大等待 10s
- Ollama（本地）：最多 5 次，初始等待 2s
- 仅对网络异常和 5xx 重试，4xx（参数错误）不重试

### 熔断器配置

```yaml
# COUNT_BASED 滑动窗口，20 次请求内失败率 >50% 触发熔断
# 慢调用（>30s）超过 80% 也触发熔断
# 熔断后等待 30s 进入 half-open，放行 5 次探测
failure-rate-threshold: 50
slow-call-duration-threshold: 30s
slow-call-rate-threshold: 80
wait-duration-in-open-state: 30s
permitted-calls-in-half-open-state: 5
```

### Fallback 路由

```yaml
hify.llm.fallback:
  openai: ollama
  claude: openai
  gemini: ollama
```

主 Provider 熔断或异常时自动切换 fallback，fallback 失败则抛出 BizException。

---

## 部署架构

用户浏览器
│
▼
Ingress Nginx（L7 负载均衡 + SSL 终止 + SSE 支持）
│
├──▶ hify-frontend（Vue SPA，Nginx 静态文件服务，2 副本）
│
└──▶ hify-backend（Spring Boot，2 副本）
│
├──▶ PostgreSQL 16 + pgvector（主数据存储 + 向量存储）
├──▶ Redis（Session / 缓存 / 限流）

**Ingress 关键配置（SSE 必须）**：

```yaml
nginx.ingress.kubernetes.io/proxy-read-timeout: "300"
nginx.ingress.kubernetes.io/proxy-buffering: "off"
nginx.ingress.kubernetes.io/limit-rps: "20"
```

**Backend 容器规格**：requests 512Mi/250m，limits 1Gi/1000m，replicas=2

**JVM 启动参数**：

```dockerfile
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+UseG1GC",
            "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
```

---

## 数据库规范

### SQL 文件管理规范

**唯一 Schema 文件**：`deploy/sql/hify.sql`

- **所有数据库表结构变更必须更新此文件**，禁止在其他位置创建 schema.sql
- 新增模块时，在 `hify.sql` 中添加该模块的所有表定义
- 表定义按模块分组，用注释分隔（如 `-- ===== Provider 模块 =====`）
- 每次变更后，必须能在空数据库中完整执行此文件创建所有表

**执行方式**：
```bash
psql -U postgres -d hify -f deploy/sql/hify.sql
```

**禁止**：
- ❌ 在 `hify-app/src/main/resources/db/` 下创建 schema.sql
- ❌ 在各模块下分散维护 SQL 文件
- ❌ 只在数据库中手动建表而不更新 hify.sql

### PostgreSQL 通用字段约定

每张表必须包含以下字段：

```sql
id          int8         NOT NULL GENERATED BY DEFAULT AS IDENTITY,  -- 主键，禁用 UUID
created_at  timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
updated_at  timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),      -- 由 trigger 维护自动更新
deleted     bool         NOT NULL DEFAULT false,                     -- 逻辑删除标志
PRIMARY KEY (id)
```

- `updated_at` 的"更新时自动刷新"由统一 trigger `update_updated_at_column()` 维护（PG 无 `ON UPDATE` 语法），建表后挂 `BEFORE UPDATE` 触发器
- 字符串用 `varchar(n)`，长文本（如 system_prompt、消息内容）用 `text`
- JSON 用 `jsonb`（支持索引和函数），不用 `json` 或 varchar 存 JSON
- 金额用 `numeric(19,4)`，禁用 `float8/real`
- 布尔用 `bool`，不用 `int` 模拟

### 索引设计原则

1. **区分度低的字段不单独建索引**（如 deleted、status 枚举），必须与高区分度字段组合
2. **组合索引遵循最左前缀**：等值查询字段在左，范围查询字段在右
3. **查询条件中含 `deleted`**，必须将 `deleted` 纳入索引
4. **每表索引不超过 5 个**（含主键），写多读少的表控制在 3 个以内
5. **禁止在 `text` 大字段上建普通 B-tree 索引**，全文检索用 GIN 索引，JSON 字段查询用 jsonb 的 GIN 索引

```sql
-- 正确示例：conversation_id 高区分度在左，deleted 次之，created_at 范围在右
CREATE INDEX idx_conv_created ON t_message (conversation_id, deleted, created_at);
```

### 大表处理策略

判断为大表的阈值：行数 > 500 万 或 数据量 > 2GB

| 场景 | 策略 |
|------|------|
| t_message | 按 conversation_id 分区，或按月做声明式分区（PARTITION BY RANGE）归档冷数据 |
| 知识库向量表 | ivfflat 索引，lists = sqrt(行数) |
| 日志类表 | 只保留 90 天，定期 DELETE + VACUUM（必要时 VACUUM FULL）|

### 分页查询规范

- **禁止** `LIMIT offset, size` 深分页（offset > 1000 全表扫描）
- 对话记录类使用**游标分页**：

```sql
SELECT id, role, content, created_at FROM t_message
WHERE conversation_id = ?
  AND deleted = 0
  AND (created_at < ? OR (created_at = ? AND id < ?))
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

- 管理后台必须分页时，用 `WHERE id > lastId LIMIT size` 替代 offset

### pgvector 索引规范

```sql
-- 余弦相似度索引，lists 值 = sqrt(总行数)，行数 <10 万时 lists=100
CREATE INDEX idx_embedding_ivfflat ON knowledge_embedding
USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- 查询时设置 probes，精度和速度平衡
SET ivfflat.probes = 10;
SELECT * FROM knowledge_embedding
ORDER BY embedding <=> '[...]'::vector LIMIT 5;
```

### 索引检测措施

**开发阶段**：启用 p6spy，拦截执行 >10ms 的查询自动 EXPLAIN，出现 `Seq Scan`（全表扫描）时打印警告日志。

**CI 阶段**：关键查询写 `IndexCoverageTest`，`EXPLAIN` 计划中出现 `Seq Scan` 则测试失败，阻断合并。

**生产阶段**：启用 `pg_stat_statements` 扩展，定期查询找出全表扫描多、耗时高的 SQL。

```sql
-- 找出平均耗时最长的 20 条 SQL（结合 EXPLAIN 排查是否走索引）
SELECT query AS sql语句,
       calls AS 执行次数,
       mean_exec_time AS 平均耗时ms,
       total_exec_time AS 总耗时ms
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 20;
```

---

## 编码规范（基于阿里巴巴 Java 开发手册）

### 命名

1. **类名用 UpperCamelCase**，方法名、变量名用 lowerCamelCase，常量用 UPPER_SNAKE_CASE，包名全小写无下划线。
2. **禁止用拼音或拼音缩写**命名，禁止单字母变量（循环变量 `i/j/k` 除外）。
3. **方法名体现动词**：查询用 `get/list/query`，修改用 `update`，删除用 `delete/remove`，新增用 `create/add`，布尔返回值用 `is/has/can`。
4. **Service 接口不加 I 前缀**，实现类加 `Impl` 后缀（`AgentService` + `AgentServiceImpl`）。
5. **数据库表名用 `t_` 前缀**，列名用 snake_case；PO 类用 `Po` 后缀，DTO 用 `Dto`/`Request`/`Response`，Mapper 用 `Mapper` 后缀。

### 异常处理

6. **禁止 catch 后 `e.printStackTrace()` 或空 catch**，必须记录日志或向上抛出。
7. **业务异常统一抛 `BizException(ErrorCode)`**，不用 RuntimeException 传递业务语义。
8. **异常处理必须使用 ErrorCode 枚举，禁止硬编码错误码和错误信息**
8. **只在顶层（GlobalExceptionHandler）处理并转换为 HTTP 响应**，中间层不捕获再包装。
9. **finally 块不写 return**，不在 finally 中抛出新异常（会吞掉原始异常）。
10. **NPE 防御**：方法返回值优先返回空集合（`Collections.emptyList()`）而非 null，接口入参用 `@NonNull`/`@Valid` 注解声明约束。

### 日志

11. **使用 SLF4J 接口 + Logback 实现**，类中用 `@Slf4j`（Lombok），禁止用 `System.out.println`。
12. **禁止在循环体内打日志**，高频路径只在异常分支记录。
13. **占位符格式 `log.info("xxx {}", var)`**，禁止字符串拼接（避免无效 toString 开销）。
14. **日志分级约定**：DEBUG=详细调试，INFO=关键业务节点，WARN=可恢复异常或配置缺失，ERROR=需人工介入的故障。生产环境 INFO 级别，日志文件按天滚动，保留 30 天。
15. **LLM 调用必须记录**：provider、model、耗时、token 数、是否命中缓存，便于成本分析。

### 并发

16. **线程池必须显式创建**（`ThreadPoolExecutor`），禁止用 `Executors.newFixedThreadPool`（无界队列 OOM）。
17. **ThreadLocal 用完必须 `remove()`**，防止线程池场景下数据泄漏。
18. **加锁粒度最小化**：只锁共享变量操作，不锁 I/O 和 LLM 调用；优先用 `ReentrantLock` 替代 `synchronized`（可设超时）。
19. **单例 Bean 的成员变量必须是线程安全的**：无状态 Service 天然安全；有状态则用 `ThreadLocal` 或局部变量，禁止用实例变量存请求上下文。
20. **`CompletableFuture` 异步调用必须指定线程池**（`supplyAsync(task, llmExecutor)`），禁止用默认 `ForkJoinPool.commonPool()`（会影响其他异步任务）。

---

## 性能瓶颈优先级（一期处理清单）

| 级别 | 瓶颈 | 一期处理方式 |
|------|------|-------------|
| P0 | LLM API 延迟高（3-30s） | 线程隔离 + 熔断 + Fallback（已设计） |
| P0 | 向量检索无索引全表扫描 | 建 ivfflat 索引（建表时必须创建） |
| P1 | 对话消息深分页 | 游标分页（禁止 LIMIT offset） |
| P1 | N+1 查询 | MyBatis-Plus 批量查询，禁止循环单查 |
| P2 | 连接池耗尽 | HikariCP 配置：maximumPoolSize=20，connectionTimeout=3000ms |
| 延后 | 静态资源未压缩 | Nginx gzip，流量大时处理 |
| 延后 | JVM GC 停顿 | G1GC 已启用，暂不调优 |
