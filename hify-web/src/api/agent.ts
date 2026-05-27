import { get, post, put, del, patch } from '@/utils/request'

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
  toolIds?: number[]
}

export interface AgentListItem {
  id: number
  name: string
  description: string
  modelName: string
  toolCount: number
  toolIds?: number[]
  temperature: number
  maxContextTurns: number
  maxTokens: number
  systemPrompt?: string
  enabled: number
  createdAt: string
}

export interface AgentDetail extends Agent {
  modelName: string
  knowledgeIds: number[]
  toolIds: number[]
  createdAt: string
  updatedAt: string
}

export interface ModelGroup {
  providerId: number
  providerName: string
  models: {
    id: number
    modelCode: string
    modelName: string
  }[]
}

export interface ToolOption {
  id: number
  name: string
  description: string
}

export interface PageData<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
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

/** 获取可用模型（按供应商分组） */
export function getModelGroups() {
  return get<ModelGroup[]>('/v1/agent-meta/models')
}

/** 快捷修改上下文轮数 */
export function updateMaxContextTurns(id: number, maxContextTurns: number) {
  return patch<void>(`/v1/agents/${id}/max-context-turns`, { maxContextTurns })
}

/** 快捷修改温度 */
export function updateTemperature(id: number, temperature: number) {
  return patch<void>(`/v1/agents/${id}/temperature`, { temperature })
}

/** 快捷修改工具绑定 */
export function updateTools(id: number, toolIds: number[]) {
  return patch<void>(`/v1/agents/${id}/tools`, { toolIds })
}

/** 获取可用工具列表 */
export function getTools() {
  return get<ToolOption[]>('/v1/agent-meta/tools')
}
