package com.hify.modules.provider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.modules.provider.dto.ModelInfo;
import com.hify.modules.provider.entity.ModelConfig;
import com.hify.modules.provider.mapper.ModelConfigMapper;
import com.hify.modules.provider.service.ModelSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 以 API 返回的模型列表为真源，全量同步。
 * <p>
 * 同步策略：新增模型 insert、已有模型 update（展示名可能变）、不在 API 中的模型标记 enabled=0（下线）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelSyncServiceImpl implements ModelSyncService {

    private final ModelConfigMapper modelConfigMapper;

    @Override
    @Transactional
    public int sync(Long providerId, List<ModelInfo> models) {
        if (models == null || models.isEmpty()) {
            log.info("模型同步 providerId={} 模型列表为空，跳过", providerId);
            return 0;
        }

        // 1. 查出该供应商所有已有模型配置
        List<ModelConfig> existingList = modelConfigMapper.selectList(
                new LambdaQueryWrapper<ModelConfig>()
                        .eq(ModelConfig::getProviderId, providerId)
        );
        Map<String, ModelConfig> existingByModelId = existingList.stream()
                .collect(Collectors.toMap(ModelConfig::getModelId, Function.identity(), (a, b) -> a));

        Set<String> apiModelIds = new HashSet<>();
        int insertCount = 0;
        int updateCount = 0;
        int disableCount = 0;

        // 2. 遍历 API 返回的模型：新增或更新
        for (ModelInfo info : models) {
            apiModelIds.add(info.getModelId());
            ModelConfig existing = existingByModelId.get(info.getModelId());

            if (existing == null) {
                // 新模型 → insert
                ModelConfig config = new ModelConfig();
                config.setProviderId(providerId);
                config.setModelId(info.getModelId());
                config.setName(info.getName());
                config.setEnabled(1);
                modelConfigMapper.insert(config);
                insertCount++;
            } else if (!info.getName().equals(existing.getName())) {
                // 已有模型但展示名变化 → update
                existing.setName(info.getName());
                // 之前因下线被标记为 enabled=0 的，重新上线
                if (existing.getEnabled() == null || existing.getEnabled() != 1) {
                    existing.setEnabled(1);
                }
                modelConfigMapper.updateById(existing);
                updateCount++;
            } else if (existing.getEnabled() == null || existing.getEnabled() != 1) {
                // 模型名未变但之前已下线 → 重新启用
                existing.setEnabled(1);
                modelConfigMapper.updateById(existing);
                updateCount++;
            }
        }

        // 3. 库中有但 API 未返回的 → 标记下线
        for (ModelConfig existing : existingList) {
            if (!apiModelIds.contains(existing.getModelId())
                    && existing.getEnabled() != null && existing.getEnabled() == 1) {
                existing.setEnabled(0);
                modelConfigMapper.updateById(existing);
                disableCount++;
                log.info("模型下线 providerId={} modelId={} name={}", providerId,
                        existing.getModelId(), existing.getName());
            }
        }

        log.info("模型同步完成 providerId={} insert={} update={} disable={} totalApiModels={}",
                providerId, insertCount, updateCount, disableCount, models.size());

        return models.size() - disableCount;
    }
}
