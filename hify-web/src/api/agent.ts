import { get, post, put, del } from '@/utils/request'

export interface Agent {
  id?: number
  name: string
  description: string
  systemPrompt?: string
  modelConfigId: number
  temperature?: number
  maxTokens?: number
  maxContextTurns?: number
  enabled?: number
  knowledgeIds?: number[]
  toolIds?: number[]
}

export interface AgentListItem {
  id: number
  name: string
  description: string
  modelConfigId: number
  modelName: string
  enabled: number
  knowledgeCount: number
  toolCount: number
  createdAt: string
}

export interface AgentDetail extends Agent {
  modelName: string
  knowledgeIds: number[]
  toolIds: number[]
  createdAt: string
  updatedAt: string
}

export interface PageData<T> {
  code: number
  message: string
  data: T[]
  total: number
  page: number
  size: number
}

/** 分页获取 Agent 列表 */
export function getAgentList(params: { page: number; size: number; keyword?: string; enabled?: number; modelConfigId?: number }) {
  return get<PageData<AgentListItem>>('/v1/agents', params)
}

/** 创建 Agent */
export function createAgent(data: Agent) {
  return post<number>('/v1/agents', data)
}

/** 更新 Agent */
export function updateAgent(id: number, data: Agent) {
  return put<void>(`/v1/agents/${id}`, data)
}

/** 删除 Agent */
export function deleteAgent(id: number) {
  return del<void>(`/v1/agents/${id}`)
}

/** 获取 Agent 详情 */
export function getAgentDetail(id: number) {
  return get<AgentDetail>(`/v1/agents/${id}`)
}

/** 克隆 Agent */
export function cloneAgent(id: number) {
  return post<number>(`/v1/agents/${id}/clone`)
}
