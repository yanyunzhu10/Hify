package com.hify.modules.provider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResult;
import com.hify.modules.provider.dto.ModelConfigBrief;
import com.hify.modules.provider.dto.ProviderCreateReq;
import com.hify.modules.provider.dto.ProviderHealthBrief;
import com.hify.modules.provider.dto.ProviderResp;
import com.hify.modules.provider.dto.ProviderUpdateReq;
import com.hify.modules.provider.entity.ModelConfig;
import com.hify.modules.provider.entity.Provider;
import com.hify.modules.provider.entity.ProviderHealth;
import com.hify.modules.provider.mapper.ModelConfigMapper;
import com.hify.modules.provider.mapper.ProviderHealthMapper;
import com.hify.modules.provider.mapper.ProviderMapper;
import com.hify.modules.provider.service.ProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderServiceImpl implements ProviderService {

    private final ProviderMapper providerMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final ProviderHealthMapper providerHealthMapper;

    // ============================================================
    // 增删改（会触发缓存失效）
    // ============================================================

    @Override
    @Transactional
    @CacheEvict(cacheNames = "provider-cache", allEntries = true)
    public ProviderResp create(ProviderCreateReq req) {
        checkNameUnique(req.getName(), null);

        Provider entity = new Provider();
        entity.setName(req.getName());
        entity.setType(req.getType());
        entity.setBaseUrl(req.getBaseUrl());
        entity.setAuthConfig(req.getAuthConfig());
        entity.setEnabled(req.getEnabled() != null ? req.getEnabled() : 1);
        providerMapper.insert(entity);

        log.info("Provider 创建成功 id={}, name={}, type={}", entity.getId(), entity.getName(), entity.getType());
        return toResp(entity);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "provider-cache", allEntries = true)
    public ProviderResp update(Long id, ProviderUpdateReq req) {
        Provider entity = requireExists(id);
        checkNameUnique(req.getName(), id);

        entity.setName(req.getName());
        entity.setType(req.getType());
        entity.setBaseUrl(req.getBaseUrl());
        entity.setAuthConfig(req.getAuthConfig());
        entity.setEnabled(req.getEnabled());
        providerMapper.updateById(entity);

        log.info("Provider 更新成功 id={}, name={}", entity.getId(), entity.getName());
        return toResp(entity);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "provider-cache", allEntries = true)
    public void delete(Long id) {
        Provider entity = requireExists(id);
        providerMapper.deleteById(id);
        log.info("Provider 删除成功 id={}, name={}", entity.getId(), entity.getName());
    }

    // ============================================================
    // 查（带缓存）
    // ============================================================

    @Override
    @Cacheable(cacheNames = "provider-cache", key = "#id")
    public ProviderResp get(Long id) {
        Provider entity = requireExists(id);
        ProviderResp resp = toResp(entity);
        enrichModelConfigs(resp);
        enrichHealth(resp);
        return resp;
    }

    @Override
    public List<ProviderResp> list(String type, Integer enabled) {
        LambdaQueryWrapper<Provider> wrapper = buildQueryWrapper(type, enabled);
        List<Provider> providers = providerMapper.selectList(wrapper);
        List<ProviderResp> list = providers.stream().map(this::toResp).toList();
        batchEnrich(list);
        return list;
    }

    @Override
    public PageResult<ProviderResp> page(int page, int size, String type, Integer enabled) {
        LambdaQueryWrapper<Provider> wrapper = buildQueryWrapper(type, enabled);
        Page<Provider> p = new Page<>(page, size);
        Page<Provider> result = providerMapper.selectPage(p, wrapper);
        List<ProviderResp> list = result.getRecords().stream()
                .map(this::toResp)
                .toList();
        batchEnrich(list);
        return PageResult.ok(list, result.getTotal(), page, size);
    }

    @Override
    public boolean existsEnabledModelConfig(Long modelConfigId) {
        if (modelConfigId == null) {
            return false;
        }
        return modelConfigMapper.selectCount(
                new LambdaQueryWrapper<ModelConfig>()
                        .eq(ModelConfig::getId, modelConfigId)
                        .eq(ModelConfig::getEnabled, 1)
        ) > 0;
    }

    @Override
    public String getModelName(Long modelConfigId) {
        if (modelConfigId == null) {
            return null;
        }
        ModelConfig config = modelConfigMapper.selectById(modelConfigId);
        return config != null ? config.getName() : null;
    }

    // ============================================================
    // 内部方法
    // ============================================================

    private LambdaQueryWrapper<Provider> buildQueryWrapper(String type, Integer enabled) {
        LambdaQueryWrapper<Provider> wrapper = new LambdaQueryWrapper<>();
        if (type != null && !type.isBlank()) {
            wrapper.eq(Provider::getType, type);
        }
        if (enabled != null) {
            wrapper.eq(Provider::getEnabled, enabled);
        }
        wrapper.orderByDesc(Provider::getCreatedAt);
        return wrapper;
    }

    /**
     * 校验名称唯一性。
     *
     * @param name         供应商名称
     * @param excludeId    更新场景需排除自身，创建传 null
     */
    private void checkNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<Provider> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Provider::getName, name);
        if (excludeId != null) {
            wrapper.ne(Provider::getId, excludeId);
        }
        if (providerMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PROVIDER_NAME_EXISTS, "供应商名称已存在: " + name);
        }
    }

    private Provider requireExists(Long id) {
        Provider entity = providerMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "供应商不存在: " + id);
        }
        return entity;
    }

    /** 填充关联的模型配置列表 */
    private void enrichModelConfigs(ProviderResp resp) {
        List<ModelConfig> configs = modelConfigMapper.selectList(
                new LambdaQueryWrapper<ModelConfig>()
                        .eq(ModelConfig::getProviderId, resp.getId())
        );
        resp.setModelConfigs(configs.stream().map(this::toModelBrief).toList());
    }

    /** 填充健康状态（可能为 null） */
    private void enrichHealth(ProviderResp resp) {
        ProviderHealth health = providerHealthMapper.selectOne(
                new LambdaQueryWrapper<ProviderHealth>()
                        .eq(ProviderHealth::getProviderId, resp.getId())
        );
        if (health != null) {
            resp.setHealth(toHealthBrief(health));
        }
    }

    /** 列表/分页场景的批量富化：一次查询加载所有 provider 的 health 和 model 数据。 */
    private void batchEnrich(List<ProviderResp> list) {
        if (list.isEmpty()) return;

        List<Long> ids = list.stream().map(ProviderResp::getId).toList();

        // 批量查询健康状态
        List<ProviderHealth> healthList = providerHealthMapper.selectList(
                new LambdaQueryWrapper<ProviderHealth>().in(ProviderHealth::getProviderId, ids));
        Map<Long, ProviderHealth> healthMap = healthList.stream()
                .collect(Collectors.toMap(ProviderHealth::getProviderId, Function.identity()));

        // 批量查询模型配置
        List<ModelConfig> configs = modelConfigMapper.selectList(
                new LambdaQueryWrapper<ModelConfig>().in(ModelConfig::getProviderId, ids));
        Map<Long, List<ModelConfig>> configMap = configs.stream()
                .collect(Collectors.groupingBy(ModelConfig::getProviderId));

        for (ProviderResp resp : list) {
            // 健康状态
            ProviderHealth health = healthMap.get(resp.getId());
            if (health != null) {
                resp.setHealth(toHealthBrief(health));
            }

            // 模型列表 + 启用的模型数
            List<ModelConfig> models = configMap.getOrDefault(resp.getId(), Collections.emptyList());
            resp.setModelConfigs(models.stream().map(this::toModelBrief).toList());
            resp.setModelCount((int) models.stream().filter(m -> m.getEnabled() != null && m.getEnabled() == 1).count());
        }
    }

    // ============================================================
    // Entity → DTO 映射
    // ============================================================

    private ProviderResp toResp(Provider entity) {
        ProviderResp resp = new ProviderResp();
        BeanUtils.copyProperties(entity, resp);
        resp.setModelConfigs(Collections.emptyList());
        return resp;
    }

    private ModelConfigBrief toModelBrief(ModelConfig entity) {
        ModelConfigBrief brief = new ModelConfigBrief();
        BeanUtils.copyProperties(entity, brief);
        return brief;
    }

    private ProviderHealthBrief toHealthBrief(ProviderHealth entity) {
        ProviderHealthBrief brief = new ProviderHealthBrief();
        BeanUtils.copyProperties(entity, brief);
        return brief;
    }
}
