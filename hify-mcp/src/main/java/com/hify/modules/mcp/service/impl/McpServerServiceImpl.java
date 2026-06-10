package com.hify.modules.mcp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResult;
import com.hify.modules.mcp.dto.*;
import com.hify.modules.mcp.entity.McpServer;
import com.hify.modules.mcp.entity.McpTool;
import com.hify.modules.mcp.mapper.McpServerMapper;
import com.hify.modules.mcp.mapper.McpToolMapper;
import com.hify.modules.mcp.service.McpClientService;
import com.hify.modules.mcp.service.McpServerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerServiceImpl implements McpServerService {

    private final McpServerMapper serverMapper;
    private final McpToolMapper toolMapper;
    private final McpClientService clientService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public McpServerResp create(McpServerCreateReq req) {
        assertNameUnique(req.getName(), null);
        McpServer s = new McpServer();
        s.setName(req.getName());
        s.setDescription(req.getDescription() != null ? req.getDescription() : "");
        s.setEndpoint(req.getEndpoint());
        s.setEnabled(req.getEnabled() != null ? req.getEnabled() : 1);
        serverMapper.insert(s);
        log.info("MCP Server 创建 id={} name={}", s.getId(), s.getName());
        return McpServerResp.from(s);
    }

    @Override
    public PageResult<McpServerResp> page(int page, int size, String name) {
        LambdaQueryWrapper<McpServer> w = new LambdaQueryWrapper<>();
        if (name != null && !name.isBlank()) w.like(McpServer::getName, name);
        w.orderByDesc(McpServer::getCreatedAt);
        Page<McpServer> p = serverMapper.selectPage(new Page<>(page, size), w);
        List<McpServerResp> list = p.getRecords().stream()
                .map(r -> enrichTools(McpServerResp.from(r))).toList();
        return PageResult.ok(list, p.getTotal(), page, size);
    }

    @Override
    public McpServerResp get(Long id) {
        McpServer s = requireExists(id);
        return enrichTools(McpServerResp.from(s));
    }

    @Override
    @Transactional
    public McpServerResp update(Long id, McpServerUpdateReq req) {
        McpServer s = requireExists(id);
        if (req.getName() != null) { assertNameUnique(req.getName(), id); s.setName(req.getName()); }
        if (req.getDescription() != null) s.setDescription(req.getDescription());
        if (req.getEndpoint() != null) s.setEndpoint(req.getEndpoint());
        if (req.getEnabled() != null) s.setEnabled(req.getEnabled());
        serverMapper.updateById(s);
        log.info("MCP Server 更新 id={}", id);
        return enrichTools(McpServerResp.from(s));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireExists(id);
        // 检查是否有 Agent 绑定了该 Server 的工具（跨模块：agent_tool 在 mysql 主库，用 JdbcTemplate 直查）
        Integer refCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_agent_tool WHERE tool_id = ?", Integer.class, id);
        if (refCount != null && refCount > 0) {
            throw new BizException(ErrorCode.MCP_SERVER_IN_USE);
        }
        // 物理删工具列表
        toolMapper.delete(new LambdaQueryWrapper<McpTool>().eq(McpTool::getMcpServerId, id));
        // 逻辑删 server
        serverMapper.deleteById(id);
        log.info("MCP Server 删除 id={}", id);
    }

    @Override
    public boolean existsEnabled(Long serverId) {
        McpServer s = serverMapper.selectById(serverId);
        return s != null && s.getEnabled() != null && s.getEnabled() == 1;
    }

    @Override
    public McpTestResult testConnection(Long id) {
        McpServer s = requireExists(id);
        McpTestResult r = new McpTestResult();
        try {
            List<McpTool> tools = clientService.listTools(s);
            // 全删全插
            toolMapper.delete(new LambdaQueryWrapper<McpTool>().eq(McpTool::getMcpServerId, id));
            for (McpTool t : tools) {
                t.setMcpServerId(id);
                toolMapper.insert(t);
            }
            // 返回最新工具列表
            List<McpTool> saved = toolMapper.selectList(
                    new LambdaQueryWrapper<McpTool>().eq(McpTool::getMcpServerId, id));
            r.setSuccess(true);
            r.setToolCount(saved.size());
            r.setTools(saved.stream().map(McpToolBrief::from).toList());
            log.info("MCP 连接测试成功 id={} tools={}", id, saved.size());
        } catch (Exception e) {
            r.setSuccess(false);
            r.setErrorMessage(e.getMessage());
            r.setTools(List.of());
            log.warn("MCP 连接测试失败 id={}", id, e);
        }
        return r;
    }

    // ============ helpers ============

    private McpServer requireExists(Long id) {
        McpServer s = serverMapper.selectById(id);
        if (s == null) throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND);
        return s;
    }

    private void assertNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<McpServer> w = new LambdaQueryWrapper<McpServer>().eq(McpServer::getName, name);
        if (excludeId != null) w.ne(McpServer::getId, excludeId);
        if (serverMapper.selectCount(w) > 0) throw new BizException(ErrorCode.MCP_SERVER_NAME_EXISTS);
    }

    private McpServerResp enrichTools(McpServerResp resp) {
        List<McpTool> tools = toolMapper.selectList(
                new LambdaQueryWrapper<McpTool>().eq(McpTool::getMcpServerId, resp.getId()));
        resp.setTools(tools.stream().map(McpToolBrief::from).toList());
        return resp;
    }
}
