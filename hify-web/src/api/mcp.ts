/**
 * MCP Server API
 */
import { get, post, put, del } from '@/utils/request'
import type { PageQuery } from '@/types'

export interface McpServerInfo {
  id: number
  name: string
  description: string
  endpoint: string
  enabled: number
  createdAt: string
  updatedAt: string
  tools?: McpToolBrief[]
}

export interface McpToolBrief {
  id: number
  name: string
  description: string
  inputSchema?: Record<string, unknown>
}

export interface McpTestResult {
  success: boolean
  errorMessage?: string
  toolCount: number
  tools: McpToolBrief[]
}

export function listMcpServers(query: PageQuery & { name?: string }) {
  return get<{ records: McpServerInfo[]; total: number }>('/v1/mcp-servers', { params: query })
}

export function createMcpServer(payload: { name: string; description?: string; endpoint: string; enabled?: number }) {
  return post<McpServerInfo>('/v1/mcp-servers', payload)
}

export function updateMcpServer(id: number, payload: Record<string, unknown>) {
  return put<McpServerInfo>(`/v1/mcp-servers/${id}`, payload)
}

export function deleteMcpServer(id: number) {
  return del<void>(`/v1/mcp-servers/${id}`)
}

export function testMcpServer(id: number) {
  return post<McpTestResult>(`/v1/mcp-servers/${id}/test`)
}

export interface DebugResult { result: string; elapsedMs: number }

export function debugMcpTool(id: number, toolName: string, args: Record<string, unknown>) {
  return post<DebugResult>(`/v1/mcp-servers/${id}/debug`, { toolName, arguments: args })
}
