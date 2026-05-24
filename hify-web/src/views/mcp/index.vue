<template>
  <div class="mcp-page">
    <div class="page-header">
      <h2>MCP Server 管理</h2>
      <el-button type="primary" @click="handleAdd">新增 MCP Server</el-button>
    </div>

    <HifyTable
      ref="tableRef"
      :columns="columns"
      :api="fetchMcpServerList"
    >
      <template #toolbar>
        <el-input
          v-model="searchKey"
          placeholder="搜索 MCP Server 名称"
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
          {{ row.enabled ? '启用' : '禁用' }}
        </el-tag>
      </template>

      <template #action="{ row }">
        <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
        <el-button link type="success" @click="handleTest(row)">连通测试</el-button>
        <el-button link type="warning" @click="handleDebug(row)">调试</el-button>
        <el-button link type="danger" class="delete-btn" @click="handleDelete(row)">删除</el-button>
      </template>
    </HifyTable>

    <HifyFormDialog
      ref="dialogRef"
      title="MCP Server"
      :rules="formRules"
      @submit="handleSubmit"
    >
      <template #default="{ form }">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：本地搜索服务" />
        </el-form-item>
        <el-form-item label="Endpoint" prop="endpoint">
          <el-input v-model="form.endpoint" placeholder="如：http://localhost:9001/mcp" />
        </el-form-item>
        <el-form-item label="启用状态">
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

const router = useRouter()
const tableRef = ref<InstanceType<typeof HifyTable>>()
const dialogRef = ref<InstanceType<typeof HifyFormDialog>>()
const searchKey = ref('')

const columns = [
  { prop: 'name', label: '名称', minWidth: '160' },
  { prop: 'endpoint', label: 'Endpoint', minWidth: '240' },
  { prop: 'toolCount', label: '工具数', width: '80' },
  { prop: 'status', label: '状态', width: '100', slot: 'status' },
  { prop: 'enabled', label: '启用', width: '80', slot: 'enabled' },
  { prop: 'action', label: '操作', width: '200', slot: 'action' },
]

const formRules = {
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  endpoint: [{ required: true, message: 'Endpoint 不能为空', trigger: 'blur' }],
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
    case 'connected': return '已连接'
    case 'error': return '异常'
    default: return '未知'
  }
}

const fetchMcpServerList = async (params: { page: number; size: number }) => {
  const res = await fetch(`/api/v1/mcp-servers?page=${params.page}&size=${params.size}&keyword=${encodeURIComponent(searchKey.value)}`)
  return res.json()
}

const handleAdd = () => {
  dialogRef.value?.open({ enabled: true })
}

const handleEdit = (row: any) => {
  dialogRef.value?.open({ ...row })
}

const handleTest = async (row: any) => {
  try {
    const res = await fetch(`/api/v1/mcp-servers/${row.id}/test`, { method: 'POST' })
    const data = await res.json()
    if (data.code === 200) {
      ElMessage.success(data.data.success ? '连通成功' : `连通失败：${data.data.errorMessage}`)
    } else {
      ElMessage.error(data.message || '测试失败')
    }
  } catch (e) {
    ElMessage.error('网络异常')
  }
}

const handleDebug = (row: any) => {
  router.push(`/mcp-servers/${row.id}`)
}

const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确定删除 MCP Server「${row.name}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await fetch(`/api/v1/mcp-servers/${row.id}`, { method: 'DELETE' })
      ElMessage.success('删除成功')
      tableRef.value?.refresh()
    })
    .catch(() => {})
}

const handleSubmit = async (form: any) => {
  const url = form._isEdit ? `/api/v1/mcp-servers/${form.id}` : '/api/v1/mcp-servers'
  const method = form._isEdit ? 'PUT' : 'POST'
  const res = await fetch(url, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(form),
  })
  const data = await res.json()
  if (data.code === 200) {
    ElMessage.success(form._isEdit ? '保存成功' : '创建成功')
    dialogRef.value?.close()
    tableRef.value?.refresh()
  } else {
    ElMessage.error(data.message || '操作失败')
  }
}
</script>
