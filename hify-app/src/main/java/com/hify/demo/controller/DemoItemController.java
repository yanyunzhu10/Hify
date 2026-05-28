package com.hify.demo.controller;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.demo.dto.DemoItemCreateReq;
import com.hify.demo.dto.DemoItemResp;
import com.hify.demo.dto.DemoItemUpdateReq;
import com.hify.demo.service.DemoItemService;
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

@RestController
@RequestMapping("/api/v1/demo-items")
@RequiredArgsConstructor
public class DemoItemController {

    private final DemoItemService demoItemService;

    @PostMapping
    public Result<DemoItemResp> create(@Valid @RequestBody DemoItemCreateReq req) {
        return Result.ok(demoItemService.create(req));
    }

    @PutMapping("/{id}")
    public Result<DemoItemResp> update(@PathVariable Long id,
                                       @Valid @RequestBody DemoItemUpdateReq req) {
        return Result.ok(demoItemService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        demoItemService.delete(id);
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<DemoItemResp> get(@PathVariable Long id) {
        return Result.ok(demoItemService.get(id));
    }

    @GetMapping
    public PageResult<DemoItemResp> page(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return demoItemService.page(page, size);
    }
}
