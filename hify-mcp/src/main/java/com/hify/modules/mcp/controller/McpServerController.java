package com.hify.modules.mcp.controller;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.modules.mcp.dto.McpDebugReq;
import com.hify.modules.mcp.dto.McpDebugResp;
import com.hify.modules.mcp.dto.McpServerCreateReq;
import com.hify.modules.mcp.dto.McpServerResp;
import com.hify.modules.mcp.dto.McpServerUpdateReq;
import com.hify.modules.mcp.dto.McpTestResult;
import com.hify.modules.mcp.service.McpServerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * MCP Server RESTful 接口。
 */
@RestController
@RequestMapping("/api/v1/mcp-servers")
@RequiredArgsConstructor
public class McpServerController {

    private final McpServerService mcpService;

    @PostMapping
    public Result<McpServerResp> create(@Valid @RequestBody McpServerCreateReq req) {
        return Result.ok(mcpService.create(req));
    }

    @GetMapping
    public PageResult<McpServerResp> page(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestParam(required = false) String name) {
        return mcpService.page(page, size, name);
    }

    @GetMapping("/{id}")
    public Result<McpServerResp> get(@PathVariable Long id) {
        return Result.ok(mcpService.get(id));
    }

    @PutMapping("/{id}")
    public Result<McpServerResp> update(@PathVariable Long id,
                                         @Valid @RequestBody McpServerUpdateReq req) {
        return Result.ok(mcpService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mcpService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/test")
    public Result<McpTestResult> testConnection(@PathVariable Long id) {
        return Result.ok(mcpService.testConnection(id));
    }

    @PostMapping("/{id}/debug")
    public Result<McpDebugResp> debug(@PathVariable Long id,
                                       @Valid @RequestBody McpDebugReq req) {
        return Result.ok(mcpService.debug(id, req));
    }
}
