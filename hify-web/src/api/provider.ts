/**
 * Provider API
 */
import { get, post, put, del } from '@/utils/request'
import type { PageQuery, ProviderConfig, ConnectionTestResult } from '@/types'

export interface ProviderUpsert {
  name: string
  type: string
  baseUrl: string
  authConfig?: Record<string, unknown>
  enabled: number
}

export interface ProviderListQuery extends PageQuery {
  type?: string
  enabled?: number
}

export function getProviderList(query: ProviderListQuery) {
  return get<{ records: ProviderConfig[]; total: number; page: number; size: number }>(
    '/v1/providers',
    { params: query },
  )
}

export function getProviderDetail(id: number) {
  return get<ProviderConfig>(`/v1/providers/${id}`)
}

export function createProvider(payload: ProviderUpsert) {
  return post<ProviderConfig>('/v1/providers', payload)
}

export function updateProvider(id: number, payload: ProviderUpsert) {
  return put<ProviderConfig>(`/v1/providers/${id}`, payload)
}

export function deleteProvider(id: number) {
  return del<void>(`/v1/providers/${id}`)
}

export function testConnection(id: number) {
  return post<ConnectionTestResult>(`/v1/providers/${id}/test-connection`)
}
