export interface PageQuery {
  page: number
  size: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface ModelConfigBrief {
  id: number
  name: string
  modelId: string
  contextSize?: number
  enabled: number
}

export interface ProviderHealthBrief {
  status: 'UP' | 'DOWN' | 'DEGRADED' | 'UNKNOWN'
  lastCheckAt?: string
  lastSuccessAt?: string
  failCount: number
  latencyMs?: number
  errorMessage?: string
}

export interface ProviderConfig {
  id: number
  name: string
  type: string
  baseUrl: string
  authConfig?: Record<string, unknown>
  enabled: number
  createdAt: string
  updatedAt: string
  modelCount: number
  modelConfigs?: ModelConfigBrief[]
  health?: ProviderHealthBrief
}

export interface ConnectionTestResult {
  success: boolean
  latencyMs: number
  modelCount?: number
  errorMessage?: string
}

export interface AgentConfig {
  id: number
  name: string
  description?: string
  systemPrompt: string
  providerId: number
  providerName?: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface Conversation {
  id: number
  agentId: number
  agentName?: string
  title: string
  createdAt: string
  updatedAt: string
}

export interface Message {
  id: number
  conversationId: number
  role: 'user' | 'assistant'
  content: string
  createdAt: string
}
