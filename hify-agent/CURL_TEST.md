# Agent 模块 curl 测试清单

> 前置条件：后端能连上数据库并启动成功。
> 当前 `application.yml` 的 `spring.datasource` 被注释，需先启用 PG 主数据源（见文末"环境前置"）。

---

## 补充：Provider 连通性测试后模型同步验证

连通性测试成功后会自动将 API 返回的模型列表同步到 `t_model_config`。

```bash
# 1. 测试连通性（以已有 Provider id=1 为例）
curl -s -X POST http://localhost:8080/api/v1/providers/1/test-connection | jq .

# 2. 检查模型是否落库
psql -U postgres -d hify -c "
SELECT id, provider_id, model_id, name, enabled, created_at
FROM t_model_config
WHERE provider_id = 1
ORDER BY model_id;
"
```

**预期**：连通性成功 → `t_model_config` 出现该供应商的模型记录，model_id 为 API 标识（如 gpt-4o），name 为展示名（如 Gemini 用 displayName）。

**重复测试**：再测一次连通性 → 模型不会重复插入，只更新 name（若 API 返回变了）；API 中已不再返回的旧模型会被标记 `enabled=0`。

启动后端：
```bash
./mvnw clean install -DskipTests          # 改了 common/provider 后必须先 install
java -jar hify-app/target/hify-app-1.0.0-SNAPSHOT.jar
```

设基础变量：
```bash
BASE=http://localhost:8080/api/v1/agents
```

---

## 1. 创建 Agent（需要一个已启用的 model_config_id）

```bash
curl -s -X POST $BASE \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "客服助手",
    "description": "解答产品咨询",
    "systemPrompt": "你是 Hify 平台的智能客服，专业友好，不编造信息。",
    "modelConfigId": 1,
    "temperature": 0.7,
    "maxTokens": 2048,
    "maxContextTurns": 10,
    "toolIds": [1, 2]
  }' | jq .
```
**预期**：`code=200`，`data` 含自增 id、tools 数组（2 条，toolName 暂为 null）、toolCount=2。
**查库**：`SELECT * FROM t_agent WHERE id=<id>;` `SELECT * FROM t_agent_tool WHERE agent_id=<id>;`（应有 2 行）

**异常用例**：
```bash
# 重名 → AGENT_NAME_EXISTS(630)
curl -s -X POST $BASE -H 'Content-Type: application/json' \
  -d '{"name":"客服助手","systemPrompt":"x","modelConfigId":1}' | jq .
# 不存在的模型 → MODEL_CONFIG_NOT_FOUND(611)
curl -s -X POST $BASE -H 'Content-Type: application/json' \
  -d '{"name":"测试2","systemPrompt":"x","modelConfigId":99999}' | jq .
# 缺 systemPrompt → 校验失败 PARAM_ERROR(400)
curl -s -X POST $BASE -H 'Content-Type: application/json' \
  -d '{"name":"测试3","modelConfigId":1}' | jq .
```

## 2. 详情（带工具列表）

```bash
curl -s $BASE/1 | jq .
```
**预期**：`data.tools` 为工具数组，`data.toolCount` 为数量。
**不存在**：`curl -s $BASE/99999 | jq .` → AGENT_NOT_FOUND(631)

## 3. 列表（带工具数量）

```bash
curl -s "$BASE?page=1&size=20" | jq .
curl -s "$BASE?page=1&size=20&name=客服" | jq .       # 按名称模糊
curl -s "$BASE?page=1&size=20&enabled=true" | jq .    # 按启用状态
```
**预期**：`data` 数组每条带 `toolCount`，`tools` 为空（列表不返回完整工具列表）；外层 `total/page/size`。

## 4. 更新基本信息（不动工具）

```bash
curl -s -X PUT $BASE/1 \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "客服助手V2",
    "description": "改了描述",
    "systemPrompt": "更新后的提示词",
    "modelConfigId": 1,
    "temperature": 0.5,
    "maxTokens": 4096,
    "maxContextTurns": 20,
    "enabled": true
  }' | jq .
```
**预期**：返回更新后详情，tools 保持不变（更新基本信息不影响关联）。
**查库**：确认 t_agent 字段已改，t_agent_tool 行数不变。

## 5. 换工具（独立接口，全删全插）

```bash
curl -s -X PUT $BASE/1/tools \
  -H 'Content-Type: application/json' \
  -d '{"toolIds": [3, 4, 5]}' | jq .
# 清空工具
curl -s -X PUT $BASE/1/tools -H 'Content-Type: application/json' \
  -d '{"toolIds": []}' | jq .
```
**预期**：`code=200`。
**查库**：`SELECT * FROM t_agent_tool WHERE agent_id=1;` 应变为新列表（旧的全删、新的全插）；清空后应为 0 行。

## 6. 删除（级联删工具 + 逻辑删主表）

```bash
curl -s -X DELETE $BASE/1 | jq .
```
**预期**：`code=200`。
**查库**：
- `SELECT deleted FROM t_agent WHERE id=1;` → deleted=1（逻辑删除，行还在）
- `SELECT * FROM t_agent_tool WHERE agent_id=1;` → 0 行（物理删除）
- `curl -s "$BASE?page=1&size=20" | jq .` → 列表不再包含该 Agent

---

## 环境前置：启用 PG 主数据源

`application.yml` 当前主 `datasource` 被注释，需改为：
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/hify
    username: ${PG_USERNAME:postgres}
    password: ${PG_PASSWORD:}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 3000
```
并确认：PG 已启动、hify 库已执行 `deploy/sql/hify.sql`、且 t_model_config 至少有一条 enabled=1 的记录（供 Agent 绑定）。
