<template>
  <div class="provider-list">
    <div class="page-header">
      <h2>模型供应商管理</h2>
      <el-button type="primary" @click="handleAdd">新增供应商</el-button>
    </div>

    <HifyTable
      ref="tableRef"
      :columns="columns"
      :api="fetchProviderList"
      @expand-change="handleExpandChange"
    >
      <template #toolbar>
        <el-input
          v-model="searchKey"
          placeholder="搜索供应商名称"
          clearable
          style="width: 240px"
        />
      </template>

      <template #healthStatus="{ row }">
        <el-tag :type="healthTagType(row.healthStatus)" size="small">
          {{ healthLabel(row.healthStatus) }}
        </el-tag>
        <span v-if="row.responseTimeMs > 0" class="latency-text">
          {{ row.responseTimeMs }}ms
        </span>
      </template>

      <template #modelCount="{ row }">
        <el-button
          link
          type="primary"
          @click="toggleExpand(row)"
        >
          {{ row.modelCount ?? 0 }} 个模型
        </el-button>
      </template>

      <template #expand="{ row }">
        <div v-loading="loadingModels[row.id]" class="expand-models">
          <el-table
            v-if="modelMap[row.id]?.length"
            :data="modelMap[row.id]"
            size="small"
            :border="true"
            style="width: 100%"
          >
            <el-table-column prop="modelName" label="模型名称" min-width="160" />
            <el-table-column prop="modelCode" label="模型编码" min-width="140" />
            <el-table-column prop="modelType" label="类型" width="100" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row: m }">
                <el-tag :type="m.status === 'active' ? 'success' : 'info'" size="small">
                  {{ m.status === 'active' ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无模型" :image-size="60" />
        </div>
      </template>

      <template #action="{ row }">
        <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
        <el-button link type="success" @click="handleTestConnection(row)">连通测试</el-button>
        <el-button link type="danger" class="delete-btn" @click="handleDelete(row)">删除</el-button>
      </template>
    </HifyTable>

    <HifyFormDialog
      ref="dialogRef"
      title="供应商"
      :rules="formRules"
      @submit="handleSubmit"
    >
      <template #default="{ form, isEdit }">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如 OpenAI" />
        </el-form-item>
        <el-form-item label="协议类型" prop="providerType">
          <el-select v-model="form.providerType" placeholder="选择协议类型" :disabled="isEdit" style="width: 100%">
            <el-option label="OpenAI 兼容" value="openai_compatible" />
            <el-option label="Anthropic" value="anthropic" />
            <el-option label="Azure OpenAI" value="azure_openai" />
            <el-option label="Ollama" value="ollama" />
          </el-select>
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="https://api.openai.com" />
        </el-form-item>
        <el-form-item label="鉴权类型" prop="authType">
          <el-select v-model="form.authType" placeholder="选择鉴权类型" style="width: 100%">
            <el-option label="Bearer Token" value="bearer" />
            <el-option label="API Key Header" value="api_key" />
            <el-option label="Azure API Key" value="azure_api_key" />
            <el-option label="无鉴权" value="none" />
          </el-select>
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="form.apiKey" type="password" show-password placeholder="为空则不修改原密钥" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio label="active">启用</el-radio>
            <el-radio label="inactive">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </template>
    </HifyFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import HifyTable from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import { useConfirm } from '@/composables/useConfirm'
import {
  getProviderList,
  createProvider,
  updateProvider,
  deleteProvider,
  testConnection,
  getProviderDetail,
  type ProviderListItem
} from '@/api/provider'

const tableRef = ref<InstanceType<typeof HifyTable>>()
const dialogRef = ref<InstanceType<typeof HifyFormDialog>>()
const searchKey = ref('')

const loadingModels = ref<Record<number, boolean>>({})
const modelMap = ref<Record<number, any[]>>({})

const columns = [
  { prop: 'name', label: '名称', minWidth: 160 },
  { prop: 'providerType', label: '协议类型', width: 140 },
  { prop: 'baseUrl', label: 'Base URL', minWidth: 240 },
  { prop: 'status', label: '状态', width: 90 },
  { prop: 'healthStatus', label: '健康状态', width: 140, slot: 'healthStatus' },
  { prop: 'modelCount', label: '模型数', width: 100, slot: 'modelCount' },
  { prop: 'action', label: '操作', width: 200, slot: 'action' }
]

const formRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  providerType: [{ required: true, message: '请选择协议类型', trigger: 'change' }],
  baseUrl: [{ required: true, message: '请输入 Base URL', trigger: 'blur' }],
  authType: [{ required: true, message: '请选择鉴权类型', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const fetchProviderList = (params: { page: number; size: number }) => {
  return getProviderList({ ...params, keyword: searchKey.value || undefined })
}

const healthTagType = (status?: string) => {
  switch (status?.toLowerCase()) {
    case 'healthy': return 'success'
    case 'unhealthy': return 'danger'
    case 'degraded': return 'warning'
    default: return 'info'
  }
}

const healthLabel = (status?: string) => {
  switch (status?.toLowerCase()) {
    case 'healthy': return 'UP'
    case 'unhealthy': return 'DOWN'
    case 'degraded': return 'DEGRADED'
    default: return 'UNKNOWN'
  }
}

const toggleExpand = (row: ProviderListItem) => {
  (tableRef.value as any)?.$refs?.table?.toggleRowExpansion?.(row)
}

const handleExpandChange = async (row: ProviderListItem, expandedRows: ProviderListItem[]) => {
  const isExpanded = expandedRows.some(r => r.id === row.id)
  if (!isExpanded || modelMap.value[row.id]) return
  loadingModels.value[row.id] = true
  try {
    const detail = await getProviderDetail(row.id)
    modelMap.value[row.id] = detail.modelConfigs || []
  } finally {
    loadingModels.value[row.id] = false
  }
}

const handleAdd = () => {
  dialogRef.value?.open({
    providerType: 'openai_compatible',
    authType: 'bearer',
    status: 'active'
  })
}

const handleEdit = (row: ProviderListItem) => {
  dialogRef.value?.open({
    id: row.id,
    name: row.name,
    providerType: row.providerType,
    baseUrl: row.baseUrl,
    authType: row.authType,
    status: row.status,
    remark: row.remark
  })
}

const handleSubmit = async (data: any) => {
  try {
    const { _isEdit, ...payload } = data
    if (_isEdit && data.id) {
      await updateProvider(data.id, payload)
      ElMessage.success('更新成功')
    } else {
      await createProvider(payload)
      ElMessage.success('创建成功')
    }
    dialogRef.value?.close()
    tableRef.value?.refresh()
  } catch (e: any) {
    // request interceptor 已显示错误
  }
}

const handleDelete = useConfirm(
  async (row: ProviderListItem) => {
    await deleteProvider(row.id)
    tableRef.value?.refresh()
  },
  { title: '删除供应商', message: '删除后不可恢复，确认吗？' }
)

const handleTestConnection = async (row: ProviderListItem) => {
  try {
    const res = await testConnection(row.id)
    if (res.success) {
      ElMessage.success(`连通成功，延迟 ${res.latencyMs}ms，发现 ${res.modelCount} 个模型`)
    } else {
      ElMessage.error(`连通失败：${res.errorMessage}`)
    }
    tableRef.value?.refresh()
  } catch (e: any) {
    // request interceptor 已显示错误
  }
}
</script>

<style scoped>
.provider-list {
  padding: var(--space-4);
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-5);
}
.page-header h2 {
  font-size: var(--text-2xl);
  font-weight: var(--font-semibold);
  color: var(--color-text-primary);
  margin: 0;
}

/* 表格行高 52px */
:deep(.el-table__row) {
  height: 52px;
}

/* 删除按钮间距 */
.delete-btn {
  margin-left: 8px;
}

.latency-text {
  margin-left: 6px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.expand-models {
  padding: 12px 24px;
  background: var(--color-bg-secondary);
  border-radius: var(--radius-md);
}
</style>
