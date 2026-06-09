package com.hify.modules.agent.controller;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.modules.agent.dto.AgentCreateReq;
import com.hify.modules.agent.dto.AgentKnowledgeBindReq;
import com.hify.modules.agent.dto.AgentQueryReq;
import com.hify.modules.agent.dto.AgentResp;
import com.hify.modules.agent.dto.AgentToolsUpdateReq;
import com.hify.modules.agent.dto.AgentUpdateReq;
import com.hify.modules.agent.service.AgentService;
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
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping
    public Result<AgentResp> create(@Valid @RequestBody AgentCreateReq req) {
        return Result.ok(agentService.create(req));
    }

    @GetMapping
    public PageResult<AgentResp> page(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      @RequestParam(required = false) String name,
                                      @RequestParam(required = false) Integer enabled) {
        AgentQueryReq req = new AgentQueryReq();
        req.setPage(page);
        req.setSize(size);
        req.setName(name);
        req.setEnabled(enabled);
        return agentService.page(req);
    }

    @GetMapping("/{id}")
    public Result<AgentResp> get(@PathVariable Long id) {
        return Result.ok(agentService.get(id));
    }

    @PutMapping("/{id}")
    public Result<AgentResp> update(@PathVariable Long id,
                                    @Valid @RequestBody AgentUpdateReq req) {
        return Result.ok(agentService.update(id, req));
    }

    /** 独立接口：仅更新工具关联（全删全插） */
    @PutMapping("/{id}/tools")
    public Result<Void> updateTools(@PathVariable Long id,
                                    @RequestBody AgentToolsUpdateReq req) {
        agentService.updateTools(id, req.getToolIds());
        return Result.ok();
    }

    /** 独立接口：轻量绑定/解绑知识库或工作流（只传要改的字段，传了才写） */
    @PutMapping("/{id}/bindings")
    public Result<AgentResp> bindResources(@PathVariable Long id,
                                            @RequestBody AgentKnowledgeBindReq req) {
        return Result.ok(agentService.bindResources(id, req.getKnowledgeBaseId(), req.getWorkflowId()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        agentService.delete(id);
        return Result.ok();
    }
}
