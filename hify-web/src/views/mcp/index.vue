<template>
  <div class="mcp-page">
    <div class="page-header">
      <h2>MCP Server Management</h2>
      <el-button data-testid="mcp-add-button" type="primary" @click="handleAdd">Add MCP Server</el-button>
    </div>

    <HifyTable
      ref="tableRef"
      :columns="columns"
      :api="fetchMcpServerList"
    >
      <template #toolbar>
        <el-input
          v-model="searchKey"
          placeholder="Search MCP server name"
          clearable
          style="width: 240px"
        />
      </template>

      <template #status="{ row }">
        <el-tag :type="statusTagType(row.status)" size="small">
          {{ statusLabel(row.status) }}
        </el-tag>
      </template>

      <template #enabled="{ row }">
        <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
          {{ row.enabled ? 'Enabled' : 'Disabled' }}
        </el-tag>
      </template>

      <template #action="{ row }">
        <el-button :data-testid="`mcp-edit-button-${row.name}`" link type="primary" @click="handleEdit(row)">Edit</el-button>
        <el-button link type="success" @click="handleTest(row)">Test</el-button>
        <el-button link type="warning" @click="handleDebug(row)">Debug</el-button>
        <el-button link type="danger" class="delete-btn" @click="handleDelete(row)">Delete</el-button>
      </template>
    </HifyTable>

    <HifyFormDialog
      ref="dialogRef"
      title="MCP Server"
      :rules="formRules"
      @submit="handleSubmit"
    >
      <template #default="{ form }">
        <el-form-item label="Name" prop="name">
          <el-input data-testid="mcp-name-input" v-model="form.name" placeholder="Example: Local Search Service" />
        </el-form-item>
        <el-form-item label="Endpoint" prop="endpoint">
          <el-input data-testid="mcp-endpoint-input" v-model="form.endpoint" placeholder="Example: http://localhost:9001/mcp" />
        </el-form-item>
        <el-form-item label="Enabled">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </template>
    </HifyFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import HifyTable from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMcpServerList, createMcpServer, updateMcpServer, deleteMcpServer, testMcpConnection } from '@/api/mcp'

const router = useRouter()
const tableRef = ref<InstanceType<typeof HifyTable>>()
const dialogRef = ref<InstanceType<typeof HifyFormDialog>>()
const searchKey = ref('')

const columns = [
  { prop: 'name', label: 'Name', minWidth: '160' },
  { prop: 'endpoint', label: 'Endpoint', minWidth: '240' },
  { prop: 'toolCount', label: 'Tools', width: '80' },
  { prop: 'status', label: 'Status', width: '100', slot: 'status' },
  { prop: 'enabled', label: 'Enabled', width: '80', slot: 'enabled' },
  { prop: 'action', label: 'Actions', width: '200', slot: 'action' },
]

const formRules = {
  name: [{ required: true, message: 'Name is required', trigger: 'blur' }],
  endpoint: [{ required: true, message: 'Endpoint is required', trigger: 'blur' }],
}

const statusTagType = (status: string) => {
  switch (status) {
    case 'connected': return 'success'
    case 'error': return 'danger'
    default: return 'info'
  }
}

const statusLabel = (status: string) => {
  switch (status) {
    case 'connected': return 'Connected'
    case 'error': return 'Error'
    default: return 'Unknown'
  }
}

const fetchMcpServerList = async (params: { page: number; size: number }) => {
  return getMcpServerList({ ...params, keyword: searchKey.value })
}

const handleAdd = () => {
  dialogRef.value?.open({ enabled: true })
}

const handleEdit = (row: any) => {
  dialogRef.value?.open({ ...row })
}

const handleTest = async (row: any) => {
  try {
    const res = await testMcpConnection(row.id)
    ElMessage.success(res.success ? 'Connection succeeded' : `Connection failed: ${res.errorMessage}`)
  } catch (e: any) {
    ElMessage.error(e.message || 'Network error')
  }
}

const handleDebug = (row: any) => {
  router.push(`/mcp-servers/${row.id}`)
}

const handleDelete = (row: any) => {
  ElMessageBox.confirm(`Delete MCP Server "${row.name}"?`, 'Confirm', { type: 'warning' })
    .then(async () => {
      await deleteMcpServer(row.id)
      ElMessage.success('Deleted successfully')
      tableRef.value?.refresh()
    })
    .catch(() => {})
}

const handleSubmit = async (form: any) => {
  try {
    if (form._isEdit) {
      await updateMcpServer(form.id, { name: form.name, endpoint: form.endpoint, enabled: form.enabled })
    } else {
      await createMcpServer({ name: form.name, endpoint: form.endpoint, enabled: form.enabled })
    }
    ElMessage.success(form._isEdit ? 'Saved successfully' : 'Created successfully')
    dialogRef.value?.close()
    tableRef.value?.refresh()
  } catch (e: any) {
    ElMessage.error(e.message || 'Operation failed')
  }
}
</script>
