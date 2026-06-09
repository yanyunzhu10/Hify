package com.hify.modules.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 节点执行器注册中心：Spring 自动注入所有 {@link NodeExecutor} 实现，按 {@code nodeType()} 分发。
 * <p>
 * 对标 {@code ProviderAdapterFactory}：Spring List 注入 → 按 key 路由。
 * 新增 Executor 只需加 {@code @Component}，不需改注册中心。
 * </p>
 */
@Component
public class NodeExecutorRegistry {

    private final Map<String, NodeExecutor> registry;

    public NodeExecutorRegistry(List<NodeExecutor> executors) {
        this.registry = executors.stream()
                .collect(Collectors.toMap(
                        e -> e.nodeType().toUpperCase(),
                        Function.identity(),
                        (a, b) -> { throw new IllegalStateException(
                                "重复的 NodeExecutor nodeType: " + a.nodeType()); }));
    }

    /**
     * 按节点类型获取执行器。
     *
     * @param type 节点类型，大小写不敏感
     * @return 匹配的 NodeExecutor
     * @throws BizException 未找到对应执行器
     */
    public NodeExecutor get(String type) {
        String key = type != null ? type.toUpperCase() : "";
        NodeExecutor executor = registry.get(key);
        if (executor == null) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "不支持的节点类型: " + type + "，已注册: " + registry.keySet());
        }
        return executor;
    }
}
