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

export interface AgentToolBrief {
  toolId: number
  toolName?: string
}

export interface AgentConfig {
  id: number
  name: string
  description?: string
  systemPrompt: string
  modelConfigId: number
  modelName?: string
  temperature: number
  maxTokens: number
  maxContextTurns: number
  enabled: number          // 0=不可用 1=可用
  toolCount: number
  tools?: AgentToolBrief[]
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

// ============ 对话引擎（chat 模块，后端 SseEmitter 流式接口） ============

export interface ChatSession {
  id: number
  agentId: number
  title: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface ChatMessage {
  id: number
  sessionId: number
  role: 'user' | 'assistant' | 'system'
  content: string
  tokens?: number
  finishReason?: string
  latencyMs?: number
  createdAt: string
}

// ============ 知识库管理 ============

export interface KnowledgeBase {
  id: number
  name: string
  description: string
  enabled: number
  createdAt: string
  updatedAt: string
}

// ============ 文档管理 ============

export interface DocumentInfo {
  id: number
  knowledgeBaseId: number
  name: string
  fileType: string
  fileSize: number
  /** PENDING | PROCESSING | DONE | FAILED */
  status: string
  errorMessage?: string
  chunkCount: number
  createdAt: string
  updatedAt: string
}

export interface ChunkInfo {
  id: number
  documentId: number
  chunkIndex: number
  content: string
  tokenCount: number
  createdAt: string
}

