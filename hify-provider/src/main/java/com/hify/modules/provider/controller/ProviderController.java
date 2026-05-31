package com.hify.modules.provider.controller;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.modules.provider.dto.ConnectionTestResult;
import com.hify.modules.provider.dto.ProviderCreateReq;
import com.hify.modules.provider.dto.ProviderResp;
import com.hify.modules.provider.dto.ProviderUpdateReq;
import com.hify.modules.provider.entity.Provider;
import com.hify.modules.provider.mapper.ProviderMapper;
import com.hify.modules.provider.service.ProviderConnectivityService;
import com.hify.modules.provider.service.ProviderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;
    private final ProviderConnectivityService connectivityService;
    private final ProviderMapper providerMapper;

    // ============================================================
    // CRUD
    // ============================================================

    @PostMapping
    public Result<ProviderResp> create(@Valid @RequestBody ProviderCreateReq req) {
        return Result.ok(providerService.create(req));
    }

    @GetMapping
    public PageResult<ProviderResp> page(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) String type,
                                          @RequestParam(required = false) Integer enabled) {
        return providerService.page(page, size, type, enabled);
    }

    @GetMapping("/{id}")
    public Result<ProviderResp> get(@PathVariable Long id) {
        return Result.ok(providerService.get(id));
    }

    @PutMapping("/{id}")
    public Result<ProviderResp> update(@PathVariable Long id,
                                        @Valid @RequestBody ProviderUpdateReq req) {
        return Result.ok(providerService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        providerService.delete(id);
        return Result.ok();
    }

    // ============================================================
    // 连通性测试
    // ============================================================

    @PostMapping("/{id}/test-connection")
    public Result<ConnectionTestResult> testConnection(@PathVariable Long id) {
        Provider provider = providerMapper.selectById(id);
        if (provider == null) {
            return Result.fail(404, "供应商不存在: " + id);
        }
        ConnectionTestResult result = connectivityService.test(provider);
        return Result.ok(result);
    }
}
