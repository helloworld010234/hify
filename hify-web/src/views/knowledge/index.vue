<template>
  <div class="knowledge-list">
    <div class="page-header">
      <h2>Knowledge Base Management</h2>
      <el-button data-testid="knowledge-add-button" type="primary" @click="handleAdd">Add Knowledge Base</el-button>
    </div>

    <HifyTable
      ref="tableRef"
      :columns="columns"
      :api="fetchKnowledgeBaseList"
    >
      <template #toolbar>
        <el-input
          v-model="searchName"
          placeholder="Search knowledge base"
          clearable
          style="width: 240px"
        />
      </template>

      <template #name="{ row }">
        <router-link :to="`/knowledge-bases/${row.id}/documents`" :data-testid="`knowledge-name-link-${row.name}`" class="name-link">
          {{ row.name }}
        </router-link>
      </template>

      <template #enabled="{ row }">
        <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
          {{ row.enabled === 1 ? 'Active' : 'Inactive' }}
        </el-tag>
      </template>

      <template #action="{ row }">
        <el-button :data-testid="`knowledge-edit-button-${row.name}`" link type="primary" @click="handleEdit(row)">Edit</el-button>
        <el-button link type="danger" @click="handleDelete(row)">Delete</el-button>
      </template>
    </HifyTable>

    <HifyFormDialog
      ref="dialogRef"
      title="Knowledge Base"
      width="560px"
      :rules="formRules"
      @submit="handleSubmit"
    >
      <template #default="{ form }">
        <el-form-item label="Name" prop="name">
          <el-input
            data-testid="knowledge-name-input"
            v-model="form.name"
            placeholder="Example: Product Handbook"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="Description" prop="description">
          <el-input
            data-testid="knowledge-description-input"
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="Describe the purpose of this knowledge base"
            maxlength="500"
            show-word-limit
          />
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
  getKnowledgeBaseList,
  createKnowledgeBase,
  updateKnowledgeBase,
  deleteKnowledgeBase,
  getKnowledgeBaseDetail,
  type KnowledgeBaseItem
} from '@/api/knowledge'

const tableRef = ref<InstanceType<typeof HifyTable>>()
const dialogRef = ref<InstanceType<typeof HifyFormDialog>>()
const searchName = ref('')

const columns = [
  { prop: 'name', label: 'Name', minWidth: 180, slot: 'name' },
  { prop: 'description', label: 'Description', minWidth: 200 },
  { prop: 'enabled', label: 'Status', width: 80, slot: 'enabled' },
  { prop: 'documentCount', label: 'Documents', width: 100 },
  { prop: 'createdAt', label: 'Created At', width: 170 },
  { prop: 'action', label: 'Actions', width: 140, slot: 'action' }
]

const formRules = {
  name: [{ required: true, message: 'Please enter a knowledge base name', trigger: 'blur' }]
}

const fetchKnowledgeBaseList = (params: { page: number; size: number }) => {
  return getKnowledgeBaseList({
    ...params,
    name: searchName.value || undefined
  })
}

const handleAdd = () => {
  dialogRef.value?.open({
    name: '',
    description: ''
  })
}

const handleEdit = async (row: KnowledgeBaseItem) => {
  try {
    const detail = await getKnowledgeBaseDetail(row.id)
    dialogRef.value?.open({
      id: detail.id,
      name: detail.name,
      description: detail.description
    })
  } catch (e: any) {
    // request interceptor already shows the failure.
  }
}

const handleSubmit = async (data: any) => {
  try {
    const { _isEdit, ...payload } = data
    if (_isEdit && data.id) {
      await updateKnowledgeBase(data.id, payload)
      ElMessage.success('Knowledge base updated successfully')
    } else {
      await createKnowledgeBase(payload)
      ElMessage.success('Knowledge base created successfully')
    }
    dialogRef.value?.close()
    tableRef.value?.refresh()
  } catch (e: any) {
    // request interceptor already shows the failure.
  }
}

const handleDelete = useConfirm(
  async (row: KnowledgeBaseItem) => {
    await deleteKnowledgeBase(row.id)
    ElMessage.success('Deleted successfully')
    tableRef.value?.refresh()
  },
  { title: 'Delete knowledge base', message: 'Deleting this knowledge base will also remove all related documents and chunks. Continue?' }
)
</script>

<style scoped>
.knowledge-list {
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
.name-link {
  color: var(--el-color-primary);
  text-decoration: none;
  font-weight: 500;
}
.name-link:hover {
  text-decoration: underline;
}
</style>
