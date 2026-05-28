package com.hify.demo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResult;
import com.hify.demo.dto.DemoItemCreateReq;
import com.hify.demo.dto.DemoItemResp;
import com.hify.demo.dto.DemoItemUpdateReq;
import com.hify.demo.entity.DemoItem;
import com.hify.demo.mapper.DemoItemMapper;
import com.hify.demo.service.DemoItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemoItemServiceImpl implements DemoItemService {

    private final DemoItemMapper demoItemMapper;

    @Override
    @Transactional
    public DemoItemResp create(DemoItemCreateReq req) {
        DemoItem entity = new DemoItem();
        entity.setName(req.getName());
        entity.setStatus(req.getStatus());
        demoItemMapper.insert(entity);
        log.info("DemoItem 创建成功 id={}, name={}", entity.getId(), entity.getName());
        return toResp(entity);
    }

    @Override
    @Transactional
    public DemoItemResp update(Long id, DemoItemUpdateReq req) {
        DemoItem entity = requireExists(id);
        entity.setName(req.getName());
        entity.setStatus(req.getStatus());
        demoItemMapper.updateById(entity);
        return toResp(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireExists(id);
        demoItemMapper.deleteById(id);
    }

    @Override
    public DemoItemResp get(Long id) {
        return toResp(requireExists(id));
    }

    @Override
    public PageResult<DemoItemResp> page(int page, int size) {
        Page<DemoItem> p = new Page<>(page, size);
        Page<DemoItem> result = demoItemMapper.selectPage(p, null);
        List<DemoItemResp> list = result.getRecords().stream()
                .map(this::toResp)
                .toList();
        return PageResult.ok(list, result.getTotal(), page, size);
    }

    private DemoItem requireExists(Long id) {
        DemoItem entity = demoItemMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "DemoItem 不存在: " + id);
        }
        return entity;
    }

    private DemoItemResp toResp(DemoItem entity) {
        DemoItemResp resp = new DemoItemResp();
        BeanUtils.copyProperties(entity, resp);
        return resp;
    }
}
