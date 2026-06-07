/**
 * 知识库 API
 */
import { get, post, put, del } from '@/utils/request'
import type { PageQuery, KnowledgeBase } from '@/types'

export interface KnowledgeBaseCreateReq {
  name: string
  description?: string
}

export interface KnowledgeBaseUpdateReq {
  name?: string
  description?: string
  enabled?: number
}

export function listKnowledgeBases(query: PageQuery & { name?: string }) {
  return get<{ records: KnowledgeBase[]; total: number }>(
    '/v1/knowledge-bases',
    { params: query },
  )
}

export function createKnowledgeBase(payload: KnowledgeBaseCreateReq) {
  return post<KnowledgeBase>('/v1/knowledge-bases', payload)
}

export function updateKnowledgeBase(id: number, payload: KnowledgeBaseUpdateReq) {
  return put<KnowledgeBase>(`/v1/knowledge-bases/${id}`, payload)
}

export function deleteKnowledgeBase(id: number) {
  return del<void>(`/v1/knowledge-bases/${id}`)
}

export function getKnowledgeBase(id: number) {
  return get<KnowledgeBase>(`/v1/knowledge-bases/${id}`)
}
