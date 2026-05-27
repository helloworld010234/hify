<template>
  <div class="documents-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <el-button link @click="$router.push('/knowledge-bases')">
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <h2>{{ kbName }} - 文档管理</h2>
      </div>
      <el-button type="primary" @click="uploadVisible = true">上传文档</el-button>
    </div>

    <!-- 文档列表 -->
    <div class="table-card">
      <el-table :data="documentList" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="name" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="fileType" label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.fileType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="大小" width="100">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="分块数" width="80" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tooltip v-if="row.status === 'FAILED'" :content="row.errorMessage || '处理失败'" placement="top">
              <el-tag :type="statusTagType(row.status)" size="small">
                <el-icon v-if="row.status === 'PROCESSING'" class="is-loading"><Loading /></el-icon>
                {{ statusText(row.status) }}
              </el-tag>
            </el-tooltip>
            <el-tag v-else :type="statusTagType(row.status)" size="small">
              <el-icon v-if="row.status === 'PROCESSING'" class="is-loading"><Loading /></el-icon>
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewChunks(row)">查看分块</el-button>
            <el-button
              link
              type="danger"
              :disabled="row.status === 'PROCESSING'"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空状态 -->
      <el-empty v-if="!loading && documentList.length === 0" description="暂无文档，请点击右上角上传" :image-size="100" />

      <!-- 分页 -->
      <div v-if="total > 0" class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- 上传弹窗 -->
    <el-dialog
      v-model="uploadVisible"
      title="上传文档"
      width="520px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-upload
        drag
        action="#"
        :auto-upload="false"
        :on-change="handleFileChange"
        :before-upload="() => false"
        :show-file-list="false"
        accept=".txt,.md,.pdf"
        class="upload-area"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-text">将文件拖到此处，或<em>点击上传</em></div>
        <div class="upload-hint">支持 txt / md / pdf，单个文件不超过 10MB</div>
      </el-upload>

      <div v-if="selectedFile" class="file-preview">
        <el-icon><Document /></el-icon>
        <span class="file-name">{{ selectedFile.name }}</span>
        <span class="file-size">{{ formatFileSize(selectedFile.size) }}</span>
      </div>

      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!selectedFile" :loading="uploading" @click="handleUpload">
          开始上传
        </el-button>
      </template>
    </el-dialog>

    <!-- 查看分块弹窗 -->
    <el-dialog
      v-model="chunksVisible"
      :title="`文档分块 - ${currentDocName}`"
      width="700px"
      destroy-on-close
    >
      <el-table :data="chunkList" v-loading="chunksLoading" stripe max-height="500">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column label="内容" min-width="400">
          <template #default="{ row }">
            <div class="chunk-content">
              <span v-if="!row._expanded">{{ truncate(row.content, 200) }}</span>
              <span v-else>{{ row.content }}</span>
              <el-button
                v-if="row.content.length > 200"
                link
                type="primary"
                size="small"
                @click="row._expanded = !row._expanded"
              >
                {{ row._expanded ? '收起' : '展开全文' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="tokenCount" label="Token 数" width="100" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Loading, UploadFilled, Document } from '@element-plus/icons-vue'
import { useConfirm } from '@/composables/useConfirm'
import {
  getKnowledgeBaseDetail,
  getDocumentList,
  uploadDocument,
  getDocumentDetail,
  getDocumentChunks,
  deleteDocument,
  type DocumentItem,
  type DocumentChunkItem
} from '@/api/knowledge'

const route = useRoute()
const kbId = Number(route.params.id)

// ==================== 知识库信息 ====================
const kbName = ref('知识库')

const loadKbInfo = async () => {
  try {
    const res = await getKnowledgeBaseDetail(kbId)
    kbName.value = res.name
  } catch (e) {
    // 静默失败，使用默认名称
  }
}

// ==================== 文档列表 ====================
const loading = ref(false)
const documentList = ref<DocumentItem[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const fetchDocumentList = async () => {
  loading.value = true
  try {
    const res = await getDocumentList(kbId, {
      page: currentPage.value,
      size: pageSize.value
    })
    documentList.value = res.list || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  fetchDocumentList()
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchDocumentList()
}

// ==================== 状态展示 ====================
const statusTagType = (status: string) => {
  switch (status) {
    case 'PENDING': return 'info'
    case 'PROCESSING': return 'primary'
    case 'DONE': return 'success'
    case 'FAILED': return 'danger'
    default: return 'info'
  }
}

const statusText = (status: string) => {
  switch (status) {
    case 'PENDING': return '待处理'
    case 'PROCESSING': return '处理中'
    case 'DONE': return '已完成'
    case 'FAILED': return '失败'
    default: return status
  }
}

const formatFileSize = (size: number) => {
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / (1024 * 1024)).toFixed(2) + ' MB'
}

// ==================== 上传 ====================
const uploadVisible = ref(false)
const selectedFile = ref<File | null>(null)
const uploading = ref(false)

const handleFileChange = (uploadFile: any) => {
  const file = uploadFile.raw as File
  if (!file) return

  // 校验文件类型
  const ext = file.name.substring(file.name.lastIndexOf('.') + 1).toLowerCase()
  if (!['txt', 'md', 'pdf'].includes(ext)) {
    ElMessage.error('不支持的文件类型，仅支持 txt / md / pdf')
    selectedFile.value = null
    return
  }

  // 校验文件大小（10MB）
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('文件大小超过 10MB 限制')
    selectedFile.value = null
    return
  }

  selectedFile.value = file
}

const handleUpload = async () => {
  if (!selectedFile.value) return
  uploading.value = true
  try {
    const res: any = await uploadDocument(kbId, selectedFile.value)
    ElMessage.success('上传成功')
    uploadVisible.value = false
    selectedFile.value = null

    // 立即刷新列表，新文档状态为 PENDING
    await fetchDocumentList()

    // 启动轮询跟踪最新上传的文档
    if (res?.data) {
      startPolling(res.data)
    }
  } catch (e: any) {
    // request interceptor 已显示错误
  } finally {
    uploading.value = false
  }
}

// ==================== 轮询 ====================
const pollIntervals = ref<Map<number, number>>(new Map())

const startPolling = (documentId: number) => {
  if (pollIntervals.value.has(documentId)) return

  const intervalId = window.setInterval(async () => {
    try {
      const doc = await getDocumentDetail(documentId)
      // 更新列表中对应文档的状态
      const idx = documentList.value.findIndex(d => d.id === documentId)
      if (idx !== -1) {
        documentList.value[idx] = { ...documentList.value[idx], ...doc }
      }

      // 状态变为 DONE 或 FAILED 时停止轮询
      if (doc.status === 'DONE' || doc.status === 'FAILED') {
        stopPolling(documentId)
        if (doc.status === 'DONE') {
          ElMessage.success(`文档「${doc.name}」处理完成`)
        } else if (doc.status === 'FAILED') {
          ElMessage.error(`文档「${doc.name}」处理失败：${doc.errorMessage}`)
        }
        // 刷新列表以更新 chunkCount 等字段
        fetchDocumentList()
      }
    } catch (e) {
      // 轮询失败不中断，继续下次
    }
  }, 3000)

  pollIntervals.value.set(documentId, intervalId)
}

const stopPolling = (documentId: number) => {
  const intervalId = pollIntervals.value.get(documentId)
  if (intervalId) {
    clearInterval(intervalId)
    pollIntervals.value.delete(documentId)
  }
}

// ==================== 查看分块 ====================
const chunksVisible = ref(false)
const chunksLoading = ref(false)
const chunkList = ref<(DocumentChunkItem & { _expanded?: boolean })[]>([])
const currentDocName = ref('')

const handleViewChunks = async (row: DocumentItem) => {
  chunksVisible.value = true
  currentDocName.value = row.name
  chunksLoading.value = true
  try {
    const res = await getDocumentChunks(row.id)
    chunkList.value = (res || []).map(c => ({ ...c, _expanded: false }))
  } catch (e) {
    // request interceptor 已显示错误
  } finally {
    chunksLoading.value = false
  }
}

const truncate = (text: string, len: number) => {
  if (!text || text.length <= len) return text
  return text.substring(0, len) + '...'
}

// ==================== 删除 ====================
const handleDelete = useConfirm(
  async (row: DocumentItem) => {
    await deleteDocument(row.id)
    ElMessage.success('删除成功')
    fetchDocumentList()
  },
  { title: '删除文档', message: '删除后该文档的向量数据也将被清空，确认吗？' }
)

// ==================== 生命周期 ====================
onMounted(() => {
  loadKbInfo()
  fetchDocumentList()
})

onUnmounted(() => {
  // 组件销毁时清理所有轮询，避免内存泄漏
  pollIntervals.value.forEach((intervalId) => {
    clearInterval(intervalId)
  })
  pollIntervals.value.clear()
})
</script>

<style scoped>
.documents-page {
  padding: var(--space-4);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-5);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.page-header h2 {
  font-size: var(--text-2xl);
  font-weight: var(--font-semibold);
  color: var(--color-text-primary);
  margin: 0;
}

.table-card {
  background: var(--color-bg-elevated);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border-default);
  padding: var(--space-5);
}

.pagination-wrapper {
  margin-top: var(--space-5);
  display: flex;
  justify-content: flex-end;
}

/* 上传区域 */
.upload-area {
  width: 100%;
}

.upload-area :deep(.el-upload-dragger) {
  width: 100%;
  padding: var(--space-8) 0;
}

.upload-icon {
  font-size: 48px;
  color: var(--el-color-primary);
  margin-bottom: var(--space-3);
}

.upload-text {
  font-size: var(--text-base);
  color: var(--color-text-secondary);
}

.upload-text em {
  color: var(--el-color-primary);
  font-style: normal;
  cursor: pointer;
}

.upload-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: var(--space-2);
}

/* 文件预览 */
.file-preview {
  margin-top: var(--space-4);
  padding: var(--space-3);
  background: var(--color-bg-secondary);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.file-name {
  flex: 1;
  font-size: var(--text-sm);
  color: var(--color-text-primary);
}

.file-size {
  font-size: 12px;
  color: var(--color-text-secondary);
}

/* 分块内容 */
.chunk-content {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  line-height: 1.6;
}

/* loading 图标动画 */
.is-loading {
  animation: rotating 2s linear infinite;
  margin-right: 4px;
}

@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
