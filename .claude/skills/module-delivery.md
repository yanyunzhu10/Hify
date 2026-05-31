# module-delivery

## 功能描述
完整的 Hify 模块开发交付流程，从需求分析到前后端联调验收。基于 Provider 模块的实际开发经验沉淀。

## 使用方式
```bash
/module-delivery agent           # 开发 Agent 模块
/module-delivery knowledge       # 开发知识库模块
/module-delivery workflow        # 开发工作流模块
```

## 参数说明
- `module-name`: 模块名称（agent/knowledge/workflow/mcp/chat）

## 工作流程

---

## 阶段 0：需求分析与设计（咨询模式）

### 目标
明确模块边界、数据模型、API 设计，避免返工

### 执行步骤

1. **供应商/技术选型**
   - 调研业界方案（如 Provider 模块调研 OpenAI/Claude/Gemini/Ollama）
   - 对比技术方案优劣
   - 确定技术栈和依赖

2. **数据模型设计**
   - 设计核心实体表（参考 CLAUDE.md 数据库规范）
   - 确定字段类型、索引、约束
   - 处理关联关系（1:1 / 1:N / N:N）
   - 考虑扩展性（如 authConfig 用 JSON 存储）

3. **边界问题澄清**
   - 模块职责边界（如 Provider 只管配置，不管调用）
   - 跨模块依赖（如 Agent 依赖 Provider 的 api/ 接口）
   - 异常处理策略（如连通性测试失败的处理）
   - 性能要求（如分页、缓存、批量查询）

### 产出物
- [ ] 技术选型文档（Markdown）
- [ ] 数据模型 ER 图或表结构说明
- [ ] API 接口清单（URL、Method、Request/Response）
- [ ] 边界问题决策记录

### 验证方式
- [ ] 与 CLAUDE.md 规范对齐（包结构、命名、数据库规范）
- [ ] 跨模块依赖符合架构约束（只通过 api/ 接口调用）

### ⚠️ 注意事项
- **等待用户确认**：数据模型设计完成后，必须确认字段类型、索引设计、关联关系
- **坑点 1**：`updated_at` 字段必须有默认值 `DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)`
- **坑点 2**：JSON 字段用 `Map<String, Object>` 而非 `String`，MyBatis-Plus 自动处理序列化
- **坑点 3**：健康状态等高频写入的表不要加缓存，与主表读写分离

---

## 阶段 1：数据库 Schema 实现

### 目标
创建数据库表结构，确保符合规范

### 执行步骤

1. **更新 schema.sql**
   - 在 `deploy/sql/hify.sql` 中添加表定义
   - 遵循通用字段约定（id/created_at/updated_at/deleted）
   - 添加必要的索引（参考 CLAUDE.md 索引设计原则）
   - 添加表注释和字段注释

2. **执行 SQL 脚本**
   ```bash
   mysql -u root -p hify < deploy/sql/hify.sql
   ```

3. **验证表结构**
   ```sql
   DESC t_provider;
   DESC t_provider_health;
   DESC t_model_config;
   SHOW INDEX FROM t_provider;
   ```

### 产出物
- [ ] `deploy/sql/hify.sql` 更新（包含新表定义）
- [ ] 数据库表创建成功

### 验证方式
- [ ] 表结构符合规范（通用字段、字符集 utf8mb4、排序规则 utf8mb4_unicode_ci）
- [ ] 索引覆盖主要查询场景
- [ ] 外键约束正确（如果使用）

### ⚠️ 注意事项
- **坑点 4**：`updated_at` 必须有默认值，否则 INSERT 时报错 "Field 'updated_at' doesn't have a default value"
- **坑点 5**：区分度低的字段（如 deleted、status）不单独建索引，必须与高区分度字段组合
- **坑点 6**：健康状态表不需要 `created_at` 和 `deleted` 字段（只保留最新快照）

---

## 阶段 2：后端实现 - Entity + Mapper

### 目标
创建实体类和 Mapper 接口，建立数据访问层

### 执行步骤

1. **创建 Entity 类**
   - 位置：`hify-{module}/src/main/java/com/hify/modules/{module}/entity/`
   - 使用 Lombok 注解（@Getter/@Setter/@TableName）
   - 字段类型与数据库对应（LocalDateTime、Integer、Map<String, Object>）
   - 添加 MyBatis-Plus 注解（@TableId/@TableField）

2. **创建 Mapper 接口**
   - 位置：`hify-{module}/src/main/java/com/hify/modules/{module}/mapper/`
   - 继承 `BaseMapper<Entity>`
   - 添加 `@Mapper` 注解
   - 复杂查询可添加自定义方法

3. **编译验证**
   ```bash
   cd /Users/zyy/java/Hify
   mvn clean compile -pl hify-{module} -am -DskipTests
   ```

### 产出物
- [ ] Entity 类（如 Provider.java、ProviderHealth.java、ModelConfig.java）
- [ ] Mapper 接口（如 ProviderMapper.java、ProviderHealthMapper.java）
- [ ] 编译通过

### 验证方式
- [ ] 编译无错误
- [ ] Entity 字段与数据库表字段一致
- [ ] Mapper 继承 BaseMapper 正确

### ⚠️ 注意事项
- **坑点 7**：JSON 字段用 `Map<String, Object>` 类型，MyBatis-Plus 自动序列化
- **坑点 8**：`@TableField(fill = FieldFill.UPDATE)` 用于 `updated_at` 自动填充
- **坑点 9**：健康状态表不继承 BaseEntity（因为没有 created_at 和 deleted）

---

## 阶段 3：后端实现 - DTO

### 目标
创建数据传输对象，定义 API 契约

### 执行步骤

1. **创建 Request DTO**
   - 位置：`hify-{module}/src/main/java/com/hify/modules/{module}/dto/`
   - 命名：`{Entity}CreateReq.java`、`{Entity}UpdateReq.java`
   - 添加 JSR-303 校验注解（@NotNull/@NotBlank/@Size）

2. **创建 Response DTO**
   - 命名：`{Entity}Resp.java`
   - 包含关联数据（如 Provider 包含 modelConfigs 和 health）
   - 使用 Brief 类型嵌套（如 ModelConfigBrief、ProviderHealthBrief）

3. **创建 Brief DTO**
   - 用于嵌套在其他 Response 中
   - 只包含必要字段，减少数据传输量

4. **编译验证**
   ```bash
   mvn clean compile -pl hify-{module} -am -DskipTests
   ```

### 产出物
- [ ] Request DTO（CreateReq、UpdateReq）
- [ ] Response DTO（Resp）
- [ ] Brief DTO（用于嵌套）
- [ ] 编译通过

### 验证方式
- [ ] DTO 字段类型与前端 TypeScript 类型对应
- [ ] 校验注解覆盖必填字段
- [ ] 编译无错误

### ⚠️ 注意事项
- **坑点 10**：Response DTO 的时间字段用 `LocalDateTime`，Jackson 自动序列化为 ISO 8601 格式
- **坑点 11**：嵌套的关联数据用 Brief 类型，避免循环引用和数据冗余
- **坑点 12**：分页查询的 Response 用 `PageResult<T>`，包含 records/total/page/size

---

## 阶段 4：后端实现 - Service（CRUD）

### 目标
实现业务逻辑层，提供 CRUD 操作

### 执行步骤

1. **创建 Service 接口**
   - 位置：`hify-{module}/src/main/java/com/hify/modules/{module}/service/`
   - 定义 CRUD 方法签名

2. **创建 ServiceImpl 实现类**
   - 位置：`hify-{module}/src/main/java/com/hify/modules/{module}/service/impl/`
   - 实现 CRUD 方法（create/update/delete/get/list/page）
   - 添加缓存注解（@Cacheable/@CacheEvict）
   - 实现 Entity ↔ DTO 转换（使用 BeanUtils.copyProperties）

3. **实现关键逻辑**
   - 唯一性校验（如 Provider 名称唯一）
   - 关联数据填充（如 Provider 填充 modelConfigs 和 health）
   - 批量查询优化（避免 N+1 问题）

4. **编译验证**
   ```bash
   mvn clean compile -pl hify-{module} -am -DskipTests
   ```

### 产出物
- [ ] Service 接口
- [ ] ServiceImpl 实现类
- [ ] CRUD 方法完整实现
- [ ] 编译通过

### 验证方式
- [ ] 编译无错误
- [ ] 缓存注解正确（查询用 @Cacheable，增删改用 @CacheEvict）
- [ ] 批量查询避免 N+1 问题

### ⚠️ 注意事项
- **坑点 13**：批量查询时一次性加载关联数据，避免循环单查（N+1 问题）
- **坑点 14**：缓存只加在读多写少的表（如 Provider），高频写入的表（如 ProviderHealth）不加缓存
- **坑点 15**：删除操作要检查关联数据（如删除 Provider 前检查是否有 Agent 在使用）

---

## 阶段 5：后端实现 - Service（业务逻辑）

### 目标
实现模块特定的业务逻辑

### 执行步骤

1. **识别业务逻辑**
   - Provider 模块：连通性测试、模型同步、健康检查
   - Agent 模块：提示词验证、模型绑定检查
   - Knowledge 模块：文档解析、向量化、相似度搜索

2. **创建专用 Service**
   - 如 `ProviderConnectivityService`（连通性测试）
   - 如 `ModelSyncService`（模型同步）
   - 如 `ProviderHealthService`（健康检查定时任务）

3. **实现业务逻辑**
   - 使用 LlmHttpClient 调用外部 API
   - 处理异常和重试（Resilience4j）
   - 保存业务数据（如健康状态）

4. **编译验证**
   ```bash
   mvn clean compile -pl hify-{module} -am -DskipTests
   ```

### 产出物
- [ ] 业务逻辑 Service 实现
- [ ] 异常处理完整
- [ ] 编译通过

### 验证方式
- [ ] 编译无错误
- [ ] 异常处理覆盖主要场景
- [ ] 日志记录关键操作

### ⚠️ 注意事项
- **等待用户确认**：业务逻辑实现完成后，确认异常处理策略和重试机制
- **坑点 16**：连通性测试成功/失败后，必须保存健康状态到 `t_provider_health` 表
- **坑点 17**：保存健康状态时，手动设置 `updated_at` 字段（如果没有默认值）
- **坑点 18**：使用 `@Transactional` 确保数据一致性

---

## 阶段 6：后端实现 - Controller

### 目标
暴露 RESTful API，提供前端调用接口

### 执行步骤

1. **创建 Controller 类**
   - 位置：`hify-{module}/src/main/java/com/hify/modules/{module}/controller/`
   - 添加 `@RestController` 和 `@RequestMapping` 注解
   - 注入 Service 依赖

2. **实现 CRUD 接口**
   - POST `/api/v1/{module}` - 创建
   - GET `/api/v1/{module}` - 分页列表
   - GET `/api/v1/{module}/{id}` - 详情
   - PUT `/api/v1/{module}/{id}` - 更新
   - DELETE `/api/v1/{module}/{id}` - 删除

3. **实现业务接口**
   - 如 POST `/api/v1/providers/{id}/test-connection` - 连通性测试

4. **编译验证**
   ```bash
   mvn clean compile -pl hify-{module} -am -DskipTests
   ```

### 产出物
- [ ] Controller 类
- [ ] CRUD 接口完整
- [ ] 业务接口实现
- [ ] 编译通过

### 验证方式
- [ ] 编译无错误
- [ ] 接口路径符合 RESTful 规范
- [ ] 参数校验注解正确（@Valid/@PathVariable/@RequestParam）

### ⚠️ 注意事项
- **坑点 19**：分页参数使用 `@RequestParam(defaultValue = "1") int page`
- **坑点 20**：返回值统一用 `Result<T>` 或 `PageResult<T>` 包装
- **坑点 21**：异常由 GlobalExceptionHandler 统一处理，Controller 不捕获

---

## 阶段 7：后端验证 - curl 测试

### 目标
验证后端 API 功能正确性

### 执行步骤

1. **启动后端服务**
   ```bash
   cd /Users/zyy/java/Hify/hify-app
   mvn spring-boot:run
   ```

2. **测试创建接口**
   ```bash
   curl -X POST http://localhost:8080/api/v1/providers \
     -H "Content-Type: application/json" \
     -d '{
       "name": "OpenAI",
       "type": "openai",
       "baseUrl": "https://api.openai.com/v1",
       "authConfig": {"apiKey": "sk-xxx"},
       "enabled": 1
     }'
   ```

3. **测试查询接口**
   ```bash
   curl http://localhost:8080/api/v1/providers?page=1&size=20
   curl http://localhost:8080/api/v1/providers/1
   ```

4. **测试更新接口**
   ```bash
   curl -X PUT http://localhost:8080/api/v1/providers/1 \
     -H "Content-Type: application/json" \
     -d '{
       "name": "OpenAI Updated",
       "type": "openai",
       "baseUrl": "https://api.openai.com/v1",
       "authConfig": {"apiKey": "sk-xxx"},
       "enabled": 1
     }'
   ```

5. **测试删除接口**
   ```bash
   curl -X DELETE http://localhost:8080/api/v1/providers/1
   ```

6. **测试业务接口**
   ```bash
   curl -X POST http://localhost:8080/api/v1/providers/1/test-connection
   ```

### 产出物
- [ ] 所有接口 curl 测试通过
- [ ] 数据库数据正确

### 验证方式
- [ ] 创建接口返回 201 或 200，包含创建的对象
- [ ] 查询接口返回正确的数据
- [ ] 更新接口返回更新后的对象
- [ ] 删除接口返回 200，数据库记录被删除或标记为 deleted=1
- [ ] 业务接口返回预期结果

### ⚠️ 注意事项
- **坑点 22**：测试前确保数据库表已创建
- **坑点 23**：测试连通性接口时，确保 API Key 有效
- **坑点 24**：检查数据库 `t_provider_health` 表是否有数据（连通性测试后应该保存）

---

## 阶段 8：前端实现 - API 文件

### 目标
创建前端 API 调用封装

### 执行步骤

1. **创建 API 文件**
   - 位置：`hify-web/src/api/{module}.ts`
   - 导入 `get/post/put/del` 方法
   - 定义接口函数

2. **定义 TypeScript 类型**
   - 位置：`hify-web/src/types/index.ts`
   - 定义 Request 和 Response 类型
   - 与后端 DTO 保持一致

3. **实现 API 方法**
   ```typescript
   export function getProviderList(query: ProviderListQuery) {
     return get<PageResult<ProviderConfig>>('/v1/providers', { params: query })
   }
   
   export function createProvider(payload: ProviderUpsert) {
     return post<ProviderConfig>('/v1/providers', payload)
   }
   ```

### 产出物
- [ ] API 文件（如 `provider.ts`）
- [ ] TypeScript 类型定义
- [ ] API 方法完整

### 验证方式
- [ ] TypeScript 编译无错误
- [ ] 类型定义与后端 DTO 一致

### ⚠️ 注意事项
- **坑点 25**：TypeScript 类型字段名必须与后端 DTO 一致（驼峰命名）
- **坑点 26**：时间字段用 `string` 类型（后端返回 ISO 8601 字符串）
- **坑点 27**：分页查询的返回类型用 `PageResult<T>`

---

## 阶段 9：前端实现 - 页面组件

### 目标
实现前端页面，对接真实 API

### 执行步骤

1. **创建页面组件**
   - 位置：`hify-web/src/views/{module}/{Module}List.vue`
   - 使用 HifyTable 组件展示列表
   - 使用 HifyFormDialog 组件实现新增/编辑

2. **替换 mock 数据**
   - 将 `:api="mockFunction"` 改为 `:api="getProviderList"`
   - 将 `@submit="console.log"` 改为 `@submit="handleSubmit"`
   - 实现 `handleSubmit` 调用 `createProvider`/`updateProvider`

3. **实现操作按钮**
   - 编辑按钮：调用 `dialogRef.value?.open(row)`
   - 删除按钮：调用 `deleteProvider(row.id)`
   - 业务按钮：如测试连通性调用 `testConnection(row.id)`

4. **启动前端服务**
   ```bash
   cd /Users/zyy/java/Hify/hify-web
   npm run dev
   ```

### 产出物
- [ ] 页面组件实现
- [ ] API 对接完成
- [ ] 前端服务启动成功

### 验证方式
- [ ] 页面能正常访问（如 http://localhost:5173/provider）
- [ ] 列表数据正确显示
- [ ] 新增/编辑对话框能正常打开

### ⚠️ 注意事项
- **坑点 28**：HifyFormDialog 的 `formData` 初始化为 `ref<any>({})`，避免泛型类型推断问题
- **坑点 29**：插槽参数传递用 `:form="formData"` 而非 `:form="formData.value"`
- **坑点 30**：`el-form` 的 `:model` 属性也用 `formData` 而非 `formData.value`

---

## 阶段 10：完整验收 - 全流程测试

### 目标
验证前后端联调功能完整性

### 执行步骤

1. **测试列表查询**
   - 打开页面，查看列表数据是否正确
   - 测试分页功能
   - 测试筛选功能（如果有）

2. **测试新增功能**
   - 点击"新增"按钮
   - 填写表单
   - 提交，验证数据是否保存到数据库
   - 验证列表是否刷新

3. **测试编辑功能**
   - 点击"编辑"按钮
   - 修改表单
   - 提交，验证数据是否更新
   - 验证列表是否刷新

4. **测试删除功能**
   - 点击"删除"按钮
   - 确认删除
   - 验证数据是否删除（或标记为 deleted=1）
   - 验证列表是否刷新

5. **测试业务功能**
   - 如测试连通性：点击"测试"按钮，验证返回结果
   - 验证健康状态是否保存到数据库
   - 验证列表的健康状态列是否更新

6. **测试异常场景**
   - 提交空表单，验证校验提示
   - 提交重复名称，验证唯一性校验
   - 删除被引用的数据，验证关联检查

### 产出物
- [ ] 所有功能测试通过
- [ ] 异常场景处理正确
- [ ] 数据库数据一致

### 验证方式
- [ ] 前端操作流畅，无报错
- [ ] 后端日志无异常
- [ ] 数据库数据正确

### ⚠️ 注意事项
- **等待用户确认**：全流程测试完成后，确认所有功能符合预期
- **坑点 31**：测试连通性后，检查 `t_provider_health` 表是否有数据
- **坑点 32**：删除操作后，检查关联数据是否正确处理（如 Agent 是否还能使用）
- **坑点 33**：浏览器控制台无 JavaScript 错误，网络请求无 4xx/5xx 错误

---

## 阶段 11：代码提交与文档更新

### 目标
提交代码，更新文档

### 执行步骤

1. **检查代码质量**
   - 运行 `/code-review` 检查代码质量
   - 修复发现的问题

2. **提交代码**
   ```bash
   git add .
   git commit -m "feat: 完成 {module} 模块开发
   
   - 数据模型：{Entity} / {Entity}Health / {RelatedEntity}
   - CRUD 接口：创建/查询/更新/删除
   - 业务功能：{业务功能描述}
   - 前端页面：列表/新增/编辑/删除
   
   Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
   ```

3. **更新文档**
   - 更新 `CLAUDE.md`（如果有新的规范或约定）
   - 更新 `README.md`（如果有新的功能说明）
   - 创建 `CHANGES.md` 记录本次变更

### 产出物
- [ ] 代码提交到 Git
- [ ] 文档更新完成

### 验证方式
- [ ] Git 提交记录清晰
- [ ] 文档更新准确

### ⚠️ 注意事项
- **坑点 34**：提交前运行 `/code-review` 检查代码质量
- **坑点 35**：提交信息遵循 Conventional Commits 规范（feat/fix/docs/refactor）
- **坑点 36**：记录踩过的坑，更新到 CHANGES.md 或 CLAUDE.md

---

## 总结：关键决策点

在以下节点**等待用户确认**，避免返工：

1. ✋ **阶段 0 结束**：数据模型设计、API 接口清单、边界问题
2. ✋ **阶段 5 结束**：业务逻辑实现、异常处理策略
3. ✋ **阶段 10 结束**：全流程测试通过，所有功能符合预期

## 常见问题排查

### 问题 1：编译失败
- 检查依赖是否正确（pom.xml）
- 检查包路径是否正确
- 检查 import 语句是否缺失

### 问题 2：数据库插入失败
- 检查 `updated_at` 字段是否有默认值
- 检查必填字段是否都有值
- 检查唯一索引是否冲突

### 问题 3：前端对话框打开无数据
- 检查 `formData` 初始化是否正确（`ref<any>({})`）
- 检查插槽参数传递是否正确（`:form="formData"`）
- 检查 `el-form` 的 `:model` 是否正确

### 问题 4：健康状态不显示
- 检查连通性测试是否保存健康状态到数据库
- 检查 Service 的 `batchEnrich` 方法是否调用
- 检查前端类型定义是否包含 `health` 字段

### 问题 5：N+1 查询问题
- 使用批量查询（`IN` 语句）一次性加载关联数据
- 避免在循环中调用 Mapper 查询
- 使用 `Map` 缓存关联数据，按 ID 分组

---

## 相关 Skill

- `/code-review` — 代码质量检查
- `/verify` — 验证功能是否正常工作
- `/deep-research` — 技术选型调研
- `/api-contract-check` — 前后端契约一致性检查（待创建）

---

## 参考资料

- `CLAUDE.md` — Hify 项目开发规范
- `deploy/sql/hify.sql` — 数据库 Schema
- Provider 模块实现 — 参考示例
