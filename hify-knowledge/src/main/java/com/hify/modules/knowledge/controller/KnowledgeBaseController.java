package com.hify.modules.knowledge.controller;

import com.hify.common.web.Result;
import com.hify.modules.knowledge.dto.KnowledgeBaseCreateReq;
import com.hify.modules.knowledge.dto.KnowledgeBaseResp;
import com.hify.modules.knowledge.dto.KnowledgeBaseUpdateReq;
import com.hify.modules.knowledge.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理 RESTful 接口。
 */
@RestController
@RequestMapping("/api/v1/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;

    @PostMapping
    public Result<KnowledgeBaseResp> create(@Valid @RequestBody KnowledgeBaseCreateReq req) {
        return Result.ok(kbService.create(req));
    }

    @GetMapping
    public Result<List<KnowledgeBaseResp>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name) {
        return Result.ok(kbService.list(page, size, name));
    }

    @GetMapping("/{id}")
    public Result<KnowledgeBaseResp> getById(@PathVariable Long id) {
        return Result.ok(kbService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<KnowledgeBaseResp> update(@PathVariable Long id,
                                            @Valid @RequestBody KnowledgeBaseUpdateReq req) {
        return Result.ok(kbService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        kbService.delete(id);
        return Result.ok();
    }
}
