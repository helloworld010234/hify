<template>
  <div class="provider-list">
    <div class="page-header">
      <h2>模型管理</h2>
      <el-button type="primary" @click="handleAdd">新增模型</el-button>
    </div>

    <HifyTable ref="tableRef" :columns="columns" :api="fetchProviderList">
      <template #toolbar>
        <el-input
          v-model="searchKey"
          placeholder="搜索模型名称"
          clearable
          style="width: 240px"
        />
      </template>
      <template #action="{ row }">
        <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
        <el-button link type="danger" class="delete-btn" @click="handleDelete(row)">删除</el-button>
      </template>
    </HifyTable>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import HifyTable from '@/components/HifyTable.vue'
import { get, del } from '@/utils/request'
import { useConfirm } from '@/composables/useConfirm'

const tableRef = ref<InstanceType<typeof HifyTable>>()
const searchKey = ref('')

const columns = [
  { prop: 'name', label: '名称', minWidth: 180 },
  { prop: 'type', label: '类型', width: 120 },
  { prop: 'baseUrl', label: 'Base URL', minWidth: 240 },
  { prop: 'status', label: '状态', width: 100 },
  { prop: 'action', label: '操作', width: 140, slot: 'action' }
]

const fetchProviderList = (params: { page: number; size: number }) => {
  return get('/providers', { ...params, keyword: searchKey.value }) as Promise<any>
}

const handleAdd = () => {
  console.log('add')
}

const handleEdit = (row: any) => {
  console.log('edit', row)
}

const handleDelete = useConfirm(
  (id: string) => del(`/providers/${id}`),
  { title: '删除模型', message: '删除后该模型不可恢复，确认吗？' }
)
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
</style>
