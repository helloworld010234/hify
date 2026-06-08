<template>
  <div class="workflow-list">
    <div class="page-header">
      <h2>Workflow Management</h2>
      <el-button data-testid="workflow-add-button" type="primary" @click="handleAdd">Create Workflow</el-button>
    </div>

    <HifyTable
      ref="tableRef"
      :columns="columns"
      :api="fetchWorkflowList"
    >
      <template #toolbar>
        <el-input
          v-model="searchKey"
          placeholder="Search workflow name"
          clearable
          style="width: 240px"
        />
      </template>

      <template #enabled="{ row }">
        <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
          {{ row.enabled === 1 ? 'Active' : 'Inactive' }}
        </el-tag>
      </template>

      <template #action="{ row }">
        <el-button link type="danger" class="delete-btn" @click="handleDelete(row)">Delete</el-button>
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
  { prop: 'name', label: 'Name', minWidth: 180 },
  { prop: 'enabled', label: 'Status', width: 90, slot: 'enabled' },
  { prop: 'nodeCount', label: 'Nodes', width: 90 },
  { prop: 'edgeCount', label: 'Edges', width: 90 },
  { prop: 'createdAt', label: 'Created At', width: 170 },
  { prop: 'action', label: 'Actions', width: 100, slot: 'action' }
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
  { title: 'Delete workflow', message: 'Deleting this workflow cannot be undone. Continue?' }
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
