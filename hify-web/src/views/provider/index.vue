<template>
  <div class="provider-list">
    <div class="page-header">
      <h2>Provider Management</h2>
      <el-button data-testid="provider-add-button" type="primary" @click="handleAdd">Add Provider</el-button>
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
          placeholder="Search provider name"
          clearable
          style="width: 240px"
          @clear="tableRef?.refresh()"
        />
      </template>

      <template #status="{ row }">
        <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
          {{ row.status === 'active' ? 'Active' : 'Inactive' }}
        </el-tag>
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
          {{ row.modelCount ?? 0 }} models
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
            <el-table-column prop="modelName" label="Model Name" min-width="160" />
            <el-table-column prop="modelCode" label="Model Code" min-width="140" />
            <el-table-column prop="modelType" label="Type" width="100" />
            <el-table-column prop="status" label="Status" width="80">
              <template #default="{ row: model }">
                <el-tag :type="model.status === 'active' ? 'success' : 'info'" size="small">
                  {{ model.status === 'active' ? 'Active' : 'Inactive' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="No models" :image-size="60" />
        </div>
      </template>

      <template #action="{ row }">
        <el-button :data-testid="`provider-edit-button-${row.name}`" link type="primary" @click="handleEdit(row)">Edit</el-button>
        <el-button
          link
          type="success"
          :loading="testingProviders[row.id]"
          @click="handleTestConnection(row)"
        >
          Test
        </el-button>
        <el-button link type="danger" class="delete-btn" @click="handleDelete(row)">Delete</el-button>
      </template>
    </HifyTable>

    <HifyFormDialog
      ref="dialogRef"
      title="Provider"
      :rules="formRules"
      @submit="handleSubmit"
    >
      <template #default="{ form, isEdit }">
        <el-form-item label="Name" prop="name">
          <el-input data-testid="provider-name-input" v-model="form.name" placeholder="Example: OpenAI" />
        </el-form-item>
        <el-form-item label="Provider Type" prop="providerType">
          <el-select
            data-testid="provider-type-select"
            v-model="form.providerType"
            placeholder="Select provider type"
            :disabled="isEdit"
            style="width: 100%"
          >
            <el-option label="OpenAI Compatible" value="openai_compatible" />
            <el-option label="Anthropic" value="anthropic" />
            <el-option label="Azure OpenAI" value="azure_openai" />
            <el-option label="Ollama" value="ollama" />
          </el-select>
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input data-testid="provider-base-url-input" v-model="form.baseUrl" placeholder="https://api.openai.com" />
        </el-form-item>
        <el-form-item label="Auth Type" prop="authType">
          <el-select data-testid="provider-auth-type-select" v-model="form.authType" placeholder="Select auth type" style="width: 100%">
            <el-option label="Bearer Token" value="bearer" />
            <el-option label="API Key Header" value="api_key" />
            <el-option label="Azure API Key" value="azure_api_key" />
            <el-option label="No Auth" value="none" />
          </el-select>
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input
            data-testid="provider-api-key-input"
            v-model="form.apiKey"
            type="password"
            show-password
            placeholder="Leave blank while editing to keep the original key"
          />
        </el-form-item>
        <el-form-item label="Status" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio label="active">Active</el-radio>
            <el-radio label="inactive">Inactive</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="Remark" prop="remark">
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
const testingProviders = ref<Record<number, boolean>>({})

const columns = [
  { prop: 'name', label: 'Name', minWidth: 160 },
  { prop: 'providerType', label: 'Provider Type', width: 140 },
  { prop: 'baseUrl', label: 'Base URL', minWidth: 240 },
  { prop: 'status', label: 'Status', width: 90, slot: 'status' },
  { prop: 'healthStatus', label: 'Health', width: 140, slot: 'healthStatus' },
  { prop: 'modelCount', label: 'Models', width: 100, slot: 'modelCount' },
  { prop: 'action', label: 'Actions', width: 220, slot: 'action' }
]

const formRules = {
  name: [{ required: true, message: 'Please enter a provider name', trigger: 'blur' }],
  providerType: [{ required: true, message: 'Please select a provider type', trigger: 'change' }],
  baseUrl: [{ required: true, message: 'Please enter a base URL', trigger: 'blur' }],
  authType: [{ required: true, message: 'Please select an auth type', trigger: 'change' }],
  status: [{ required: true, message: 'Please select a status', trigger: 'change' }]
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
  ;(tableRef.value as any)?.$refs?.table?.toggleRowExpansion?.(row)
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
      ElMessage.success('Provider updated successfully')
    } else {
      await createProvider(payload)
      ElMessage.success('Provider created successfully')
    }
    dialogRef.value?.close()
    tableRef.value?.refresh()
  } catch (e: any) {
    // request interceptor already shows the failure.
  } finally {
    dialogRef.value?.finishSubmit()
  }
}

const handleDelete = useConfirm(
  async (row: ProviderListItem) => {
    await deleteProvider(row.id)
    tableRef.value?.refresh()
  },
  { title: 'Delete provider', message: 'Deleting this provider cannot be undone. Continue?' }
)

const handleTestConnection = async (row: ProviderListItem) => {
  testingProviders.value[row.id] = true
  try {
    const result = await testConnection(row.id)
    if (result.success) {
      const latencyText = result.latencyMs ? `, latency ${result.latencyMs}ms` : ''
      const modelText = result.modelCount !== undefined ? `, discovered ${result.modelCount} models` : ''
      ElMessage.success(`Connection succeeded${latencyText}${modelText}`)
    } else {
      ElMessage.error(result.errorMessage || 'Connection failed, please check the API key, base URL, or network.')
    }
    tableRef.value?.refresh()
    modelMap.value[row.id] = []
  } catch (e: any) {
    // request interceptor already shows the failure.
  } finally {
    testingProviders.value[row.id] = false
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

:deep(.el-table__row) {
  height: 52px;
}

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
