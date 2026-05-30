/**
 * Provider API（当前为 mock 实现，后续切真实后端时仅替换实现即可）
 */
import type { PageQuery, PageResult, ProviderConfig } from '@/types'

export type ProviderUpsert = Pick<
  ProviderConfig,
  'name' | 'provider' | 'baseUrl' | 'apiKey' | 'modelId' | 'enabled'
>

// ============================================================
// Mock 数据
// ============================================================
const seed: ProviderConfig[] = [
  {
    id: 1,
    name: 'OpenAI GPT-4o',
    provider: 'openai',
    baseUrl: 'https://api.openai.com/v1',
    apiKey: 'sk-****d3f1',
    modelId: 'gpt-4o',
    enabled: true,
    createdAt: '2026-04-08 10:32:18',
    updatedAt: '2026-05-21 14:10:02',
  },
  {
    id: 2,
    name: 'Claude 3.5 Sonnet',
    provider: 'claude',
    baseUrl: 'https://api.anthropic.com',
    apiKey: 'sk-ant-****8b2c',
    modelId: 'claude-3-5-sonnet-20241022',
    enabled: true,
    createdAt: '2026-04-12 09:14:50',
    updatedAt: '2026-05-18 11:42:33',
  },
  {
    id: 3,
    name: 'Gemini 1.5 Pro',
    provider: 'gemini',
    baseUrl: 'https://generativelanguage.googleapis.com/v1beta',
    apiKey: 'AIza****Xq91',
    modelId: 'gemini-1.5-pro',
    enabled: true,
    createdAt: '2026-04-20 16:48:09',
    updatedAt: '2026-05-09 08:21:15',
  },
  {
    id: 4,
    name: 'Ollama Local (Llama 3.1)',
    provider: 'ollama',
    baseUrl: 'http://127.0.0.1:11434',
    apiKey: '',
    modelId: 'llama3.1:8b',
    enabled: true,
    createdAt: '2026-05-02 13:05:27',
    updatedAt: '2026-05-25 19:38:44',
  },
  {
    id: 5,
    name: 'OpenAI GPT-3.5 (Backup)',
    provider: 'openai',
    baseUrl: 'https://api.openai.com/v1',
    apiKey: 'sk-****a07e',
    modelId: 'gpt-3.5-turbo',
    enabled: false,
    createdAt: '2026-03-18 11:00:00',
    updatedAt: '2026-04-30 17:25:10',
  },
]

let store: ProviderConfig[] = [...seed]
let nextId = seed.length + 1

// 模拟网络延时
const delay = <T>(value: T, ms = 250): Promise<T> =>
  new Promise((resolve) => setTimeout(() => resolve(value), ms))

const nowIso = (): string => {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// ============================================================
// API
// ============================================================
export function listProviderPage(query: PageQuery): Promise<PageResult<ProviderConfig>> {
  const { page, size } = query
  const start = (page - 1) * size
  const records = store.slice(start, start + size)
  return delay({ records, total: store.length, page, size })
}

export function createProvider(payload: ProviderUpsert): Promise<ProviderConfig> {
  const now = nowIso()
  const item: ProviderConfig = {
    id: nextId++,
    ...payload,
    createdAt: now,
    updatedAt: now,
  }
  // 新增置顶，符合直觉
  store = [item, ...store]
  return delay(item)
}

export function updateProvider(
  id: number,
  payload: ProviderUpsert,
): Promise<ProviderConfig> {
  const idx = store.findIndex((p) => p.id === id)
  if (idx < 0) return Promise.reject(new Error('提供商不存在'))
  const updated: ProviderConfig = {
    ...store[idx],
    ...payload,
    updatedAt: nowIso(),
  }
  store[idx] = updated
  return delay(updated)
}

export function deleteProvider(id: number): Promise<void> {
  store = store.filter((p) => p.id !== id)
  return delay(undefined as void)
}
