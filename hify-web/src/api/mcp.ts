import { get, post, put, del } from '@/utils/request'

export interface McpServer {
  id: number
  name: string
  endpoint: string
  enabled: number
  status: string
  toolCount: number
  lastCheckTime: string
  lastErrorMsg: string
  createdAt: string
}

export interface McpTool {
  id: number
  name: string
  description: string
  inputSchema: any
}

export interface McpServerDetail extends McpServer {
  tools: McpTool[]
}

export interface McpDebugRequest {
  toolName: string
  arguments: Record<string, any>
}

export interface McpDebugResponse {
  success: boolean
  result: string
  errorMessage: string
  latencyMs: number
}

export interface PageData<T> {
  code: number
  message: string
  data: T[]
  total: number
  page: number
  size: number
}

/** 分页获取 MCP Server 列表 */
export function getMcpServerList(params: { page: number; size: number; keyword?: string }) {
  return get<PageData<McpServer>>('/v1/mcp-servers', params)
}

/** 创建 MCP Server */
export function createMcpServer(data: { name: string; endpoint: string; enabled?: boolean }) {
  return post<number>('/v1/mcp-servers', data)
}

/** 更新 MCP Server */
export function updateMcpServer(id: number, data: { name: string; endpoint: string; enabled?: boolean }) {
  return put<void>(`/v1/mcp-servers/${id}`, data)
}

/** 删除 MCP Server */
export function deleteMcpServer(id: number) {
  return del<void>(`/v1/mcp-servers/${id}`)
}

/** 获取 MCP Server 详情 */
export function getMcpServerDetail(id: number) {
  return get<McpServerDetail>(`/v1/mcp-servers/${id}`)
}

/** 连通性测试 */
export function testMcpConnection(id: number) {
  return post<{ success: boolean; latencyMs: number; toolCount: number; errorMessage: string }>(`/v1/mcp-servers/${id}/test`)
}

/** 调试调用工具 */
export function debugMcpTool(id: number, data: McpDebugRequest) {
  return post<McpDebugResponse>(`/v1/mcp-servers/${id}/debug`, data)
}
