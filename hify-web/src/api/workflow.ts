/**
 * 工作流 API
 */
import { get, post, del } from '@/utils/request'
import type { PageQuery, WorkflowInfo, WorkflowCreateReq } from '@/types'

export function listWorkflows(query: PageQuery & { name?: string }) {
  return get<{ records: WorkflowInfo[]; total: number }>(
    '/v1/workflows',
    { params: query },
  )
}

export function getWorkflowDetail(id: number) {
  return get<WorkflowInfo>(`/v1/workflows/${id}`)
}

export function createWorkflow(payload: WorkflowCreateReq) {
  return post<WorkflowInfo>('/v1/workflows', payload)
}

export function deleteWorkflow(id: number) {
  return del<void>(`/v1/workflows/${id}`)
}
