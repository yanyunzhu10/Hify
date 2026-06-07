/**
 * 文档管理 API
 */
import { get, post, del } from '@/utils/request'
import type { PageQuery, DocumentInfo, ChunkInfo } from '@/types'

export function listDocuments(kbId: number, query: PageQuery) {
  return get<{ records: DocumentInfo[]; total: number }>(
    `/v1/knowledge-bases/${kbId}/documents`,
    { params: query },
  )
}

export function uploadDocument(kbId: number, file: File) {
  const form = new FormData()
  form.append('file', file)
  return post<DocumentInfo>(`/v1/knowledge-bases/${kbId}/documents`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function getDocumentDetail(id: number) {
  return get<DocumentInfo>(`/v1/documents/${id}`)
}

export function listChunks(documentId: number) {
  return get<ChunkInfo[]>(`/v1/documents/${documentId}/chunks`)
}

export function deleteDocument(id: number) {
  return del<void>(`/v1/documents/${id}`)
}
