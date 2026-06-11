# 单元测试生成技能

## 技能说明

本技能用于为任何 Service 方法自动生成单元测试，完整流程包括：
1. 读代码分析执行路径和边界条件
2. 输出测试计划（不写代码）
3. 技术确认和计划调整
4. 写测试代码（Service 逻辑和 DTO 约束分文件）
5. 跑测试，失败时分析原因

## 触发方式

```
/单测 [类全名或简单类名].[方法名]
```

**示例**：
- `/单测 ProviderServiceImpl.create`
- `/单测 ChatServiceImpl.doStream`
- `/单测 AgentService.createAgent`
- `/单测 WorkflowEngine.execute`

---

## 流程脚本

### 第一步：读代码和分析

执行以下代码读取被测类及其依赖：

```javascript
// 1. 确定目标类和方法
const input = args;  // 用户输入，如 "ProviderServiceImpl.create"
const [className, methodName] = input.includes('.')
    ? input.split('.')
    : [input, null];  // 如果只传类名，默认分析所有 public 方法

// 2. 查找被测类文件
const targetFiles = await glob(`**/${className}.java`, {
    cwd: '/Users/zyy/java/Hify',
    matchBase: true
});

if (targetFiles.length === 0) {
    console.log(`未找到类文件: ${className}`);
    return;
}

const targetFile = targetFiles[0];
console.log(`\n📖 分析类: ${targetFile}`);

// 3. 读取被测类
const targetContent = await readFile(targetFile);
const classNameFull = extractClassName(targetContent);

// 4. 识别并读取依赖类
const dependencies = identifyDependencies(targetContent, classNameFull);

for (const dep of dependencies) {
    const depFiles = await glob(`**/${dep}.java`, { cwd: '/Users/zyy/java/Hify', matchBase: true });
    if (depFiles.length > 0) {
        dependencies[dep].content = await readFile(depFiles[0]);
    }
}

// 5. 输出分析结果
console.log(`\n🔍 分析方法: ${methodName || '所有 public 方法'}`);
```

### 第二步：执行路径分析

分析被测方法的执行路径，输出：

```markdown
# [类名].[方法名] 测试计划

## 执行路径分析

### 正常路径（N 条）
[每条路径的步骤描述]

### 异常路径（N 条）
| 路径 | 触发条件 | 异常类型 | 事务行为 |
|------|----------|----------|----------|
| 路径 1 | [条件] | BizException(RuntimeException) | 回滚/提交 |

## 关键变量分析
| 变量 | 来源 | 默认值处理 | 验证规则 |
|------|------|-----------|----------|
| var1 | req.getVar1() | 无默认值 | 规则1 |

## 边界条件分析
| 检查项 | 风险说明 | 当前代码处理 |
|--------|----------|-------------|
| null 校验 | 字段为 null 时的行为 | ✅ 已校验 / ❌ 未校验 |
| 并发场景 | 是否有并发竞争 | ✅ 有保护 / ❌ 无保护 |

## 测试场景

### 正常场景（N 个）
| 场景 | 验证内容 | 关键断言 |

### 异常场景（N 个）
| 场景 | 验证内容 | 关键断言 |

### 边界条件场景（N 个）
| 场景 | 验证内容 | 关键断言 |

## 测试优先级
| 优先级 | 场景 | 理由 |
|--------|------|------|
| P0 | [场景] | 核心业务逻辑 |
```

### 第三步：技术确认清单

在输出测试计划后，主动检查以下技术问题：

```markdown
---

## 🔧 技术确认

- **Mock 方式**: Service 层使用 @Mock，Controller 层使用 @MockBean
- **断言库**: AssertJ（不用 assertTrue）
- **测试框架**: JUnit 5 + Mockito
- **Bean Validation**: [检查 DTO 后填写]

### 检查 Bean Validation 状态

正在检查 DTO 文件...

[如果 DTO 有 Bean Validation 注解]
⚠️  **注意**: DTO 上有 Bean Validation 注解（@NotNull/@NotBlank/@Size 等）

| 字段 | 注解 | 测试位置 |
|------|------|----------|
| name | @NotBlank | ProviderCreateReqTest.java（单独文件） |
| type | @NotNull | ProviderCreateReqTest.java（单独文件） |

**Service 业务逻辑测试（本文件）不测试**以下场景：
- ❌ name 为 null（已被 @NotBlank 拦截）
- ❌ name 为空字符串（已被 @NotBlank 拦截）
- ❌ type 为 null（已被 @NotNull 拦截）

[如果 DTO 没有 Bean Validation 注解]
✅  DTO 无 Bean Validation 注解，所有校验由 Service 层处理。

---

### 待确认事项

1. 是否需要测试并发场景？（需要多线程 + 真实数据库，建议用集成测试）
2. 是否有缓存相关逻辑？（如有，需要验证 @CacheEvict）
3. 是否有外部依赖调用？（如有，需要 mock 外部服务）

---

## 下一步

请确认测试计划：
- 输入 "确认" 继续写测试代码
- 输入 "调整 [说明]" 修改测试场景
- 输入 "跳过" 取消本次任务
```

### 第四步：写测试代码

用户确认后，生成测试代码：

```java
package com.hify.modules.xxx.service.impl;

import com.hify.common.exception.BizException;
import com.hify.modules.xxx.dto.XxxCreateReq;
import com.hify.modules.xxx.dto.XxxResp;
import com.hify.modules.xxx.entity.Xxx;
import com.hify.modules.xxx.mapper.XxxMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class XxxServiceImplTest {

    @Mock
    private XxxMapper xxxMapper;

    @Mock
    private OtherMapper otherMapper;  // 根据依赖添加

    @InjectMocks
    private XxxServiceImpl xxxService;

    // ──────────────────────────────────────────────────────────────
    // 正常场景
    // ──────────────────────────────────────────────────────────────

    @Test
    void should_return_xxx_when_create_with_valid_input() {
        // Given
        XxxCreateReq req = new XxxCreateReq();
        req.setName("test-name");
        // ... 设置其他字段

        given(xxxMapper.selectCount(any())).willReturn(0L);
        given(xxxMapper.insert(any(Xxx.class))).willAnswer(invocation -> {
            Xxx entity = invocation.getArgument(0);
            entity.setId(1L);
            return null;
        });

        // When
        XxxResp result = xxxService.create(req);

        // Then
        assertThat(result.getId()).isNotNull();
        assertThat(result.getName()).isEqualTo("test-name");
        // ... 其他断言

        then(xxxMapper).should().insert(any(Xxx.class));
    }

    // ──────────────────────────────────────────────────────────────
    // 异常场景
    // ──────────────────────────────────────────────────────────────

    @Test
    void should_throw_biz_exception_when_create_with_duplicate_name() {
        // Given
        XxxCreateReq req = new XxxCreateReq();
        req.setName("existing-name");
        // ... 设置其他字段

        given(xxxMapper.selectCount(any())).willReturn(1L);

        // When & Then
        assertThatThrownBy(() -> xxxService.create(req))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("已存在")
            .hasFieldOrPropertyWithValue("code", 610);

        then(xxxMapper).should(never()).insert(any(Xxx.class));
    }
}
```

### 第五步：跑测试和分析失败

```bash
# 运行测试
mvn test -Dtest=XxxServiceImplTest
```

如果测试失败，输出分析：

```markdown
## ❌ 测试失败

**失败方法**: `should_return_xxx_when_create_with_valid_input()`

**错误信息**:
```
org.mockito.exceptions.verification.WantedButNotInvoked:
Wanted but not invoked:
xxxMapper.insert(<any>);
```

**原因分析**:
- 可能原因 1：Mock 设置不正确，`given(xxxMapper.insert(any()))` 未匹配实际调用
- 可能原因 2：代码执行路径与预期不符，未走到 insert 分支

**建议处理**:
1. 检查被测方法的实际执行路径
2. 检查 mock 的参数匹配是否正确
3. 添加调试日志或断点验证

---

## 📊 测试结果汇总

| 方法 | 结果 | 失败原因 |
|------|------|----------|
| should_return_xxx_when_create_with_valid_input | ❌ | Mock 未被调用 |
| should_throw_biz_exception_when_create_with_duplicate_name | ✅ | - |
| should_set_default_when_field_is_null | ⏭️ | 功能未实现，已标记 @Disabled |

```

---

## 辅助函数

```javascript
// 提取类名
function extractClassName(content) {
    const match = content.match(/public\s+class\s+(\w+)/);
    return match ? match[1] : null;
}

// 识别依赖类
function identifyDependencies(content, className) {
    const deps = {};

    // 匹配 import 语句
    const importRegex = /import\s+([\w.]+)\.(\w+);/g;
    let match;
    while ((match = importRegex.exec(content)) !== null) {
        const pkg = match[1];
        const simpleName = match[2];

        // 只关心本项目内的依赖
        if (pkg.startsWith('com.hify.')) {
            // DTO
            if (simpleName.endsWith('Req') || simpleName.endsWith('Resp')) {
                deps[simpleName] = { type: 'dto', pkg };
            }
            // Entity
            else if (!pkg.endsWith('.mapper') && !pkg.endsWith('.service')) {
                deps[simpleName] = { type: 'entity', pkg };
            }
        }
    }

    return deps;
}

// 读取文件（集成工具）
async function readFile(path) {
    const { Read } = await import('claude-code');
    const result = await Read({ file_path: path });
    return result.content;
}

// 查找文件（集成工具）
async function glob(pattern, options) {
    const { Bash } = await import('claude-code');
    const result = await Bash({
        command: `find ${options.cwd} -type f -name "${pattern.replace('**/', '')}"`,
        description: 'Find Java files'
    });
    return result.stdout.trim().split('\n').filter(Boolean);
}
```

---

## 使用示例

### 示例 1：生成 ProviderServiceImpl.create 的测试

```
用户: /单测 ProviderServiceImpl.create

Claude Code:
📖 分析类: /Users/zyy/java/Hify/hify-provider/src/main/java/com/hify/modules/provider/service/impl/ProviderServiceImpl.java
🔍 分析方法: create

读取依赖:
- ProviderCreateReq.java
- ProviderResp.java
- Provider.java
- ErrorCode.java

[输出测试计划]

🔧 技术确认:
- Mock 方式: @Mock
- Bean Validation: 无
[检查 DTO...]

请确认测试计划...
```

### 示例 2：调整测试计划

```
用户: 调整 并发场景用集成测试，不写单测

Claude Code:
已调整，移除并发场景单测，标记为"集成测试覆盖"。

更新后的测试场景:
- 正常场景（2 个）
- 异常场景（2 个）
- 边界条件场景（4 个）

请确认...
```

---

## 限制和注意事项

1. **只能分析 Java 代码**：本技能专为 Java Spring Boot 项目设计
2. **不生成集成测试**：如需测试并发、事务、缓存一致性，建议用集成测试框架
3. **Bean Validation 分离**：DTO 的 @Valid 校验单独测试，Service 层不重复测试
4. **Mock 限制**：无法真实模拟并发竞争、数据库约束等场景
5. **需要手动调整**：生成的代码可能需要根据项目具体规范调整

---

## 更新日志

- 2024-06-12: 初始版本，支持 Service 层单测自动生成