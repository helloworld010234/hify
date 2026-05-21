<template>
  <div class="workflow-list">
    <div class="page-header">
      <h2>工作流管理</h2>
      <el-button type="primary" @click="handleAdd">新建工作流</el-button>
    </div>

    <HifyTable
      ref="tableRef"
      :columns="columns"
      :api="fetchWorkflowList"
    >
      <template #toolbar>
        <el-input
          v-model="searchKey"
          placeholder="搜索工作流名称"
          clearable
          style="width: 240px"
        />
      </template>

      <template #enabled="{ row }">
        <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
          {{ row.enabled === 1 ? '启用' : '禁用' }}
        </el-tag>
      </template>

      <template #action="{ row }">
        <el-button link type="danger" class="delete-btn" @click="handleDelete(row)">删除</el-button>
      </template>
    </HifyTable>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import HifyTable from '@/components/HifyTable.vue'
import { useConfirm } from '@/composables/useConfirm'
import {
  getWorkflowList,
  deleteWorkflow,
  type WorkflowListItem
} from '@/api/workflow'

const router = useRouter()
const tableRef = ref<InstanceType<typeof HifyTable>>()
const searchKey = ref('')

const columns = [
  { prop: 'name', label: '名称', minWidth: 180 },
  { prop: 'enabled', label: '状态', width: 90, slot: 'enabled' },
  { prop: 'nodeCount', label: '节点数', width: 90 },
  { prop: 'edgeCount', label: '边数', width: 90 },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { prop: 'action', label: '操作', width: 100, slot: 'action' }
]

const fetchWorkflowList = (params: { page: number; size: number }) => {
  return getWorkflowList({
    ...params,
    keyword: searchKey.value || undefined
  })
}

const handleAdd = () => {
  router.push('/workflows/create')
}

const handleDelete = useConfirm(
  async (row: WorkflowListItem) => {
    await deleteWorkflow(row.id)
    tableRef.value?.refresh()
  },
  { title: '删除工作流', message: '删除后不可恢复，确认吗？' }
)
</script>

<style scoped>
.workflow-list {
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
.delete-btn {
  margin-left: 8px;
}
:deep(.el-table__row) {
  height: 52px;
}
</style>
