/**
 * Agent API
 */
import { get, post, put, del } from '@/utils/request'
import type { PageQuery, AgentConfig } from '@/types'

export interface AgentCreateReq {
  name: string
  description?: string
  systemPrompt: string
  modelConfigId: number
  temperature?: number
  maxTokens?: number
  maxContextTurns?: number
  enabled?: number
  toolIds?: number[]
}

export interface AgentUpdateReq {
  name: string
  description?: string
  systemPrompt: string
  modelConfigId: number
  temperature?: number
  maxTokens?: number
  maxContextTurns?: number
  enabled?: number
}

export interface AgentListQuery extends PageQuery {
  name?: string
  enabled?: number
}

export function getAgentList(query: AgentListQuery) {
  return get<{ records: AgentConfig[]; total: number; page: number; size: number }>(
    '/v1/agents',
    { params: query },
  )
}

export function getAgentDetail(id: number) {
  return get<AgentConfig>(`/v1/agents/${id}`)
}

export function createAgent(payload: AgentCreateReq) {
  return post<AgentConfig>('/v1/agents', payload)
}

export function updateAgent(id: number, payload: AgentUpdateReq) {
  return put<AgentConfig>(`/v1/agents/${id}`, payload)
}

export function updateAgentTools(id: number, toolIds: number[]) {
  return put<void>(`/v1/agents/${id}/tools`, { toolIds })
}

export function deleteAgent(id: number) {
  return del<void>(`/v1/agents/${id}`)
}

// ============ 依赖数据：模型列表（从 Provider 接口拉取） ============

export interface ModelOption {
  value: number          // modelConfigId
  label: string          // 模型展示名
  providerName: string   // 所属供应商名（用于分组）
}

/** 从 Provider 列表接口提取所有可用模型，按供应商分组 */
export async function fetchModelOptions(): Promise<ModelOption[]> {
  // Provider 列表返回带 modelConfigs 的数据，取足够大的 size 一次拉全
  const result = await get<{ records: { name: string; modelConfigs?: { id: number; name: string; enabled: number }[] }[]; total: number }>(
    '/v1/providers',
    { params: { page: 1, size: 100 } },
  )
  const options: ModelOption[] = []
  for (const provider of result.records ?? []) {
    for (const model of provider.modelConfigs ?? []) {
      if (model.enabled === 1) {
        options.push({
          value: model.id,
          label: model.name,
          providerName: provider.name,
        })
      }
    }
  }
  return options
}

// ============ 依赖数据：MCP 工具列表 ============

export interface McpToolOption {
  value: number
  label: string
  description?: string
}

/** 获取可用的 MCP 工具列表（待 MCP 模块后端就绪） */
export async function fetchToolOptions(): Promise<McpToolOption[]> {
  // MCP 模块未实现，暂返回空；就绪后改为调 /v1/mcp 接口
  return []
}
