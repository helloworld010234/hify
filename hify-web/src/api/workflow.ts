import { post, del } from '@/utils/request'
import axios from 'axios'

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
  code: number
  message: string
  data: T[]
  total: number
  page: number
  size: number
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
  return axios.get('/api/v1/workflows', { params }).then(res => res.data as PageData<WorkflowListItem>)
}

/** 创建工作流 */
export function createWorkflow(data: WorkflowCreatePayload) {
  return post<any>('/v1/workflows', data)
}

/** 删除工作流 */
export function deleteWorkflow(id: number) {
  return del<void>(`/v1/workflows/${id}`)
}
