import { get, post, del } from '@/utils/request'

export interface KnowledgeBase {
  id?: number
  name: string
  description: string
  enabled?: number
}

export interface KnowledgeBaseItem {
  id: number
  name: string
  description: string
  enabled: number
  documentCount: number
  createdAt: string
  updatedAt: string
}

export interface DocumentItem {
  id: number
  knowledgeBaseId: number
  name: string
  fileType: string
  fileSize: number
  status: 'PENDING' | 'PROCESSING' | 'DONE' | 'FAILED'
  chunkCount: number
  errorMessage: string
  createdAt: string
  updatedAt: string
}

export interface DocumentChunkItem {
  id: number
  chunkIndex: number
  content: string
  tokenCount: number
}

export interface PageData<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

/** 分页获取知识库列表 */
export function getKnowledgeBaseList(params: { page: number; size: number; name?: string }) {
  return get<PageData<KnowledgeBaseItem>>('/v1/knowledge-bases', params)
}

/** 创建知识库 */
export function createKnowledgeBase(data: KnowledgeBase) {
  return post<number>('/v1/knowledge-bases', data)
}

/** 更新知识库 */
export function updateKnowledgeBase(id: number, data: KnowledgeBase) {
  return post<void>(`/v1/knowledge-bases/${id}`, data)
}

/** 删除知识库 */
export function deleteKnowledgeBase(id: number) {
  return del<void>(`/v1/knowledge-bases/${id}`)
}

/** 获取知识库详情 */
export function getKnowledgeBaseDetail(id: number) {
  return get<KnowledgeBaseItem>(`/v1/knowledge-bases/${id}`)
}

// ==================== 文档管理 API ====================

/** 分页获取知识库下的文档列表 */
export function getDocumentList(kbId: number, params: { page: number; size: number }) {
  return get<PageData<DocumentItem>>(`/v1/knowledge-bases/${kbId}/documents`, params)
}

/** 上传文档 */
export function uploadDocument(kbId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return axios.post(`/api/v1/knowledge-bases/${kbId}/documents`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }).then(res => res.data)
}

/** 获取文档详情（用于轮询状态） */
export function getDocumentDetail(id: number) {
  return get<DocumentItem>(`/v1/documents/${id}`)
}

/** 获取文档分块列表 */
export function getDocumentChunks(id: number) {
  return get<DocumentChunkItem[]>(`/v1/documents/${id}/chunks`)
}

/** 删除文档 */
export function deleteDocument(id: number) {
  return del<void>(`/v1/documents/${id}`)
}
