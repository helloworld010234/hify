<template>
  <div class="documents-page">
    <div class="page-header">
      <div class="header-left">
        <el-button link @click="$router.push('/knowledge-bases')">
          <el-icon><ArrowLeft /></el-icon> Back
        </el-button>
        <h2>{{ kbName }} - Documents</h2>
      </div>
      <div class="header-actions">
        <el-button :disabled="!hasReadyDocuments" @click="goToAgents">Bind To Agent</el-button>
        <el-button data-testid="document-upload-open-button" type="primary" @click="uploadVisible = true">Upload Document</el-button>
      </div>
    </div>

    <div class="table-card">
      <el-alert
        v-if="hasProcessingDocuments"
        type="info"
        title="Documents are being processed"
        description="Once processing completes, you can bind this knowledge base to an agent for chat retrieval."
        show-icon
        :closable="false"
        class="path-alert"
      />
      <el-table :data="documentList" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="name" label="File Name" min-width="200" show-overflow-tooltip />
        <el-table-column prop="fileType" label="Type" width="80">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.fileType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="Size" width="100">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="Chunks" width="80" />
        <el-table-column prop="status" label="Status" width="120">
          <template #default="{ row }">
            <el-tooltip v-if="row.status === 'FAILED'" :content="row.errorMessage || 'Processing failed'" placement="top">
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
        <el-table-column prop="createdAt" label="Created At" width="170" />
        <el-table-column label="Actions" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewChunks(row)">View Chunks</el-button>
            <el-button
              link
              type="danger"
              :disabled="row.status === 'PROCESSING'"
              @click="handleDelete(row)"
            >
              Delete
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && documentList.length === 0" description="No documents yet. Upload one to get started." :image-size="100" />

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

    <el-dialog
      v-model="uploadVisible"
      title="Upload Document"
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
        <div class="upload-text">Drop a file here or <em>click to upload</em></div>
        <div class="upload-hint">Supports txt / md / pdf, max 10MB per file</div>
      </el-upload>

      <div v-if="selectedFile" class="file-preview">
        <el-icon><Document /></el-icon>
        <span class="file-name">{{ selectedFile.name }}</span>
        <span class="file-size">{{ formatFileSize(selectedFile.size) }}</span>
      </div>

      <template #footer>
        <el-button @click="uploadVisible = false">Cancel</el-button>
        <el-button data-testid="document-upload-submit-button" type="primary" :disabled="!selectedFile" :loading="uploading" @click="handleUpload">
          Start Upload
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="chunksVisible"
      :title="`Document Chunks - ${currentDocName}`"
      width="700px"
      destroy-on-close
    >
      <el-table :data="chunkList" v-loading="chunksLoading" stripe max-height="500">
        <el-table-column type="index" label="#" width="60" />
        <el-table-column label="Content" min-width="400">
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
                {{ row._expanded ? 'Collapse' : 'Expand' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="tokenCount" label="Tokens" width="100" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
const router = useRouter()
const kbId = Number(route.params.id)

const kbName = ref('Knowledge Base')

const loadKbInfo = async () => {
  try {
    const res = await getKnowledgeBaseDetail(kbId)
    kbName.value = res.name
  } catch (e) {
    // Silently fall back to the default title.
  }
}

const loading = ref(false)
const documentList = ref<DocumentItem[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const hasReadyDocuments = computed(() => documentList.value.some(doc => doc.status === 'DONE'))
const hasProcessingDocuments = computed(() =>
  documentList.value.some(doc => doc.status === 'PENDING' || doc.status === 'PROCESSING')
)

const goToAgents = () => {
  router.push('/agents')
}

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
    case 'PENDING': return 'Pending'
    case 'PROCESSING': return 'Processing'
    case 'DONE': return 'Done'
    case 'FAILED': return 'Failed'
    default: return status
  }
}

const formatFileSize = (size: number) => {
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / (1024 * 1024)).toFixed(2) + ' MB'
}

const uploadVisible = ref(false)
const selectedFile = ref<File | null>(null)
const uploading = ref(false)

const handleFileChange = (uploadFile: any) => {
  const file = uploadFile.raw as File
  if (!file) return

  const ext = file.name.substring(file.name.lastIndexOf('.') + 1).toLowerCase()
  if (!['txt', 'md', 'pdf'].includes(ext)) {
    ElMessage.error('Unsupported file type. Please upload txt, md, or pdf.')
    selectedFile.value = null
    return
  }

  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('File size exceeds the 10MB limit')
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
    ElMessage.success('Upload succeeded, document processing has started')
    uploadVisible.value = false
    selectedFile.value = null

    await fetchDocumentList()

    if (res?.data) {
      startPolling(res.data)
    }
  } catch (e: any) {
    // request interceptor already shows the failure.
  } finally {
    uploading.value = false
  }
}

const pollIntervals = ref<Map<number, number>>(new Map())

const startPolling = (documentId: number) => {
  if (pollIntervals.value.has(documentId)) return

  const intervalId = window.setInterval(async () => {
    try {
      const doc = await getDocumentDetail(documentId)
      const idx = documentList.value.findIndex(d => d.id === documentId)
      if (idx !== -1) {
        documentList.value[idx] = { ...documentList.value[idx], ...doc }
      }

      if (doc.status === 'DONE' || doc.status === 'FAILED') {
        stopPolling(documentId)
        if (doc.status === 'DONE') {
          ElMessage.success(`Document "${doc.name}" finished processing`)
        } else if (doc.status === 'FAILED') {
          ElMessage.error(`Document "${doc.name}" failed: ${doc.errorMessage}`)
        }
        fetchDocumentList()
      }
    } catch (e) {
      // Keep polling on transient failures.
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
    // request interceptor already shows the failure.
  } finally {
    chunksLoading.value = false
  }
}

const truncate = (text: string, len: number) => {
  if (!text || text.length <= len) return text
  return text.substring(0, len) + '...'
}

const handleDelete = useConfirm(
  async (row: DocumentItem) => {
    await deleteDocument(row.id)
    ElMessage.success('Deleted successfully')
    fetchDocumentList()
  },
  { title: 'Delete document', message: 'Deleting this document will also remove its vector chunks. Continue?' }
)

onMounted(() => {
  loadKbInfo()
  fetchDocumentList()
})

onUnmounted(() => {
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

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
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

.path-alert {
  margin-bottom: var(--space-4);
}

.pagination-wrapper {
  margin-top: var(--space-5);
  display: flex;
  justify-content: flex-end;
}

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
}

.upload-hint {
  margin-top: var(--space-2);
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
}

.file-preview {
  margin-top: var(--space-4);
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-secondary);
}

.file-name {
  flex: 1;
}

.file-size {
  font-size: var(--text-sm);
}

.chunk-content {
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
