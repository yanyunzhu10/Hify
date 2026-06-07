package com.hify.modules.knowledge.controller;

import com.hify.common.web.Result;
import com.hify.modules.knowledge.dto.ChunkResp;
import com.hify.modules.knowledge.dto.DocumentResp;
import com.hify.modules.knowledge.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 文档管理 RESTful 接口。
 * <p>
 * 上传接口只做接收 → 创建记录 → 提交异步任务，立即返回。
 * 前端根据返回的 status 字段轮询（PENDING → PROCESSING → DONE / FAILED）。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService docService;

    // ── 上传 ──
    @PostMapping("/api/v1/knowledge-bases/{kbId}/documents")
    public Result<DocumentResp> upload(@PathVariable Long kbId,
                                       @RequestParam("file") MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            return Result.fail(400, "文件名不能为空");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("读取上传文件失败", e);
        }
        return Result.ok(docService.upload(kbId, filename, content));
    }

    // ── 文档列表 ──
    @GetMapping("/api/v1/knowledge-bases/{kbId}/documents")
    public Result<List<DocumentResp>> list(@PathVariable Long kbId,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return Result.ok(docService.listByKb(kbId, page, size));
    }

    // ── 文档详情 ──
    @GetMapping("/api/v1/documents/{id}")
    public Result<DocumentResp> getById(@PathVariable Long id) {
        return Result.ok(docService.getById(id));
    }

    // ── 切片列表 ──
    @GetMapping("/api/v1/documents/{id}/chunks")
    public Result<List<ChunkResp>> listChunks(@PathVariable Long id) {
        return Result.ok(docService.listChunks(id));
    }

    // ── 删除 ──
    @DeleteMapping("/api/v1/documents/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        docService.delete(id);
        return Result.ok();
    }
}
