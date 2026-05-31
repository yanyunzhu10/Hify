# 修改记录

## 2026-05-31 - Provider 健康状态保存功能

### 问题描述
点击"测试连通性"按钮后，虽然返回了测试结果，但健康状态没有保存到 `t_provider_health` 表中，导致列表页面无法显示健康状态。

### 修改内容

#### 1. 修复 HifyFormDialog 组件泛型问题
**文件**: `hify-web/src/components/HifyFormDialog.vue`

- **问题**: 第119行使用了错误的类型断言 `as { value: T }`，导致 Vue 响应式系统无法正确处理 `formData`
- **修复**: 
  - 移除错误的类型断言，改为 `const formData = ref<any>({})`
  - 修改 `el-form` 的 `:model` 属性从 `formData` 改为 `formData`（保持一致）
  - 修改插槽传递从 `:form="formData.value"` 改为 `:form="formData"`

#### 2. 添加健康状态保存功能
**文件**: `hify-provider/src/main/java/com/hify/modules/provider/service/impl/ProviderConnectivityServiceImpl.java`

**修改点**:
1. 添加依赖注入 `ProviderHealthMapper`
2. 在 `test()` 方法上添加 `@Transactional` 注解
3. 在测试成功/失败后调用 `saveHealthStatus()` 保存健康状态
4. 新增 `saveHealthStatus()` 方法实现健康状态的保存逻辑

**核心逻辑**:
```java
private void saveHealthStatus(Long providerId, String status, long latencyMs,
                               String errorMessage, boolean success) {
    // 查询是否已存在健康状态记录
    ProviderHealth health = providerHealthMapper.selectOne(...);
    
    if (health == null) {
        // 新建健康状态记录
        health = new ProviderHealth();
        // 设置字段...
        providerHealthMapper.insert(health);
    } else {
        // 更新健康状态记录
        // 成功时：重置 failCount 为 0，更新 lastSuccessAt
        // 失败时：failCount + 1，记录 errorMessage
        providerHealthMapper.updateById(health);
    }
}
```

**状态说明**:
- `UP`: 连通性测试成功
- `DOWN`: 连通性测试失败或异常
- `failCount`: 连续失败次数，成功后重置为 0
- `lastCheckAt`: 最后一次探测时间
- `lastSuccessAt`: 最后一次成功时间
- `latencyMs`: 最近一次调用的延迟（毫秒）
- `errorMessage`: 最近一次失败的原因

### 测试步骤
1. 重启后端服务
2. 在前端页面点击"测试连通性"按钮
3. 查询数据库 `t_provider_health` 表，应该能看到健康状态记录
4. 刷新列表页面，应该能看到"健康状态"列显示绿色的"正常"标签和延迟时间

### 相关文件
- `hify-web/src/components/HifyFormDialog.vue` - 修复表单对话框组件
- `hify-web/src/views/provider/ProviderList.vue` - 已实现健康状态显示（无需修改）
- `hify-provider/src/main/java/com/hify/modules/provider/service/impl/ProviderConnectivityServiceImpl.java` - 添加健康状态保存
- `hify-provider/src/main/java/com/hify/modules/provider/entity/ProviderHealth.java` - 健康状态实体类
- `hify-provider/src/main/java/com/hify/modules/provider/mapper/ProviderHealthMapper.java` - 健康状态 Mapper

### 数据库表结构
```sql
CREATE TABLE t_provider_health (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider_id BIGINT NOT NULL COMMENT '关联 t_provider.id',
    status VARCHAR(20) NOT NULL COMMENT '健康状态：UP / DOWN / DEGRADED / UNKNOWN',
    last_check_at DATETIME(3) COMMENT '最后一次探测时间',
    last_success_at DATETIME(3) COMMENT '最后一次成功时间',
    fail_count INT DEFAULT 0 COMMENT '连续失败次数',
    latency_ms INT COMMENT '最近一次调用的延迟（毫秒）',
    error_message TEXT COMMENT '最近一次失败的原因',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_provider_id (provider_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商健康状态';
```
