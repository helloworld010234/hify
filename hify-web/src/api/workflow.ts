import { get, post, del } from '@/utils/request'

export interface WorkflowListItem {
  id: number
  name: string
  description: string
  enabled: number
  nodeCount: number
  edgeCount: number
  createdAt: string
}

export interface PageData<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

export interface WorkflowCreatePayload {
  name: string
  description: string
  enabled: number
  nodes: any[]
  edges: any[]
}

/** 分页获取工作流列表 */
export function getWorkflowList(params: { page: number; size: number; keyword?: string; enabled?: number }) {
  return get<PageData<WorkflowListItem>>('/v1/workflows', params)
}

/** 创建工作流 */
export function createWorkflow(data: WorkflowCreatePayload) {
  return post<any>('/v1/workflows', data)
}

/** 删除工作流 */
export function deleteWorkflow(id: number) {
  return del<void>(`/v1/workflows/${id}`)
}
