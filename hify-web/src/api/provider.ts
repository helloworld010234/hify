import { get, post, put, del } from '@/utils/request'

export interface Provider {
  id?: number
  code?: string
  name: string
  providerType: string
  baseUrl: string
  authType: string
  apiKey?: string
  authConfig?: {
    apiKey?: string
    apiVersion?: string
    deploymentName?: string
    azureApiVersion?: string
    extra?: string
  }
  timeoutMs?: number
  maxRetries?: number
  status: string
  sortOrder?: number
  remark?: string
}

export interface ProviderListItem {
  id: number
  code: string
  name: string
  providerType: string
  baseUrl: string
  authType: string
  status: string
  healthStatus: string
  consecutiveFailures: number
  lastCheckTime: string
  sortOrder: number
  createdAt: string
  modelCount: number
  responseTimeMs: number
  remark?: string
}

export interface PageData<T> {
  code: number
  message: string
  data: T[]
  total: number
  page: number
  size: number
}

export interface ConnectionTestResult {
  success: boolean
  latencyMs: number
  modelCount: number
  errorMessage: string
}

/** 分页获取供应商列表 */
export function getProviderList(params: { page: number; size: number; keyword?: string }) {
  return get<PageData<ProviderListItem>>('/v1/providers', params)
}

/** 创建供应商 */
export function createProvider(data: Provider) {
  return post<number>('/v1/providers', data)
}

/** 更新供应商 */
export function updateProvider(id: number, data: Provider) {
  return put<void>(`/v1/providers/${id}`, data)
}

/** 删除供应商 */
export function deleteProvider(id: number) {
  return del<void>(`/v1/providers/${id}`)
}

/** 连通性测试 */
export function testConnection(id: number) {
  return post<ConnectionTestResult>(`/v1/providers/${id}/test-connection`)
}

/** 获取供应商详情 */
export function getProviderDetail(id: number) {
  return get<Provider & {
    modelConfigs: any[]
    providerHealth: any
    apiKeyMask: string
    healthStatus: string
    consecutiveFailures: number
    lastCheckTime: string
    lastErrorMsg: string
    fallbackProviderCode: string
    createdAt: string
  }>(`/v1/providers/${id}`)
}
