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

export interface ProviderConfig {
  id: number
  name: string
  provider: 'openai' | 'claude' | 'gemini' | 'ollama'
  baseUrl: string
  apiKey: string
  modelId: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface ProviderTestResult {
  success: boolean
  latencyMs: number
  message?: string
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
