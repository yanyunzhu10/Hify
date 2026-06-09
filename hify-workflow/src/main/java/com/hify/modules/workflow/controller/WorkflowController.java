package com.hify.modules.workflow.controller;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.modules.workflow.dto.WorkflowCreateReq;
import com.hify.modules.workflow.dto.WorkflowResp;
import com.hify.modules.workflow.dto.WorkflowUpdateReq;
import com.hify.modules.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 工作流 RESTful 接口。
 */
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService wfService;

    @PostMapping
    public Result<WorkflowResp> create(@Valid @RequestBody WorkflowCreateReq req) {
        return Result.ok(wfService.create(req));
    }

    @GetMapping
    public PageResult<WorkflowResp> page(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size,
                                          @RequestParam(required = false) String name) {
        return wfService.page(page, size, name);
    }

    @GetMapping("/{id}")
    public Result<WorkflowResp> getById(@PathVariable Long id) {
        return Result.ok(wfService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<WorkflowResp> update(@PathVariable Long id,
                                        @Valid @RequestBody WorkflowUpdateReq req) {
        return Result.ok(wfService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        wfService.delete(id);
        return Result.ok();
    }
}
