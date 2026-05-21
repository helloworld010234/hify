<template>
  <div class="knowledge-list">
    <div class="page-header">
      <h2>知识库管理</h2>
      <el-button type="primary" @click="handleAdd">新建知识库</el-button>
    </div>

    <HifyTable
      ref="tableRef"
      :columns="columns"
      :api="fetchKnowledgeBaseList"
    >
      <template #toolbar>
        <el-input
          v-model="searchName"
          placeholder="搜索知识库名称"
          clearable
          style="width: 240px"
        />
      </template>

      <template #name="{ row }">
        <router-link :to="`/knowledge-bases/${row.id}/documents`" class="name-link">
          {{ row.name }}
        </router-link>
      </template>

      <template #enabled="{ row }">
        <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
          {{ row.enabled === 1 ? '启用' : '禁用' }}
        </el-tag>
      </template>

      <template #action="{ row }">
        <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
        <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
      </template>
    </HifyTable>

    <HifyFormDialog
      ref="dialogRef"
      title="知识库"
      width="560px"
      :rules="formRules"
      @submit="handleSubmit"
    >
      <template #default="{ form }">
        <el-form-item label="名称" prop="name">
          <el-input
            v-model="form.name"
            placeholder="如 产品手册"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="简短描述该知识库的用途"
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
  { prop: 'name', label: '名称', minWidth: 180, slot: 'name' },
  { prop: 'description', label: '描述', minWidth: 200 },
  { prop: 'enabled', label: '状态', width: 80, slot: 'enabled' },
  { prop: 'documentCount', label: '文档数量', width: 100 },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { prop: 'action', label: '操作', width: 140, slot: 'action' }
]

const formRules = {
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }]
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
    // request interceptor 已显示错误
  }
}

const handleSubmit = async (data: any) => {
  try {
    const { _isEdit, ...payload } = data
    if (_isEdit && data.id) {
      await updateKnowledgeBase(data.id, payload)
      ElMessage.success('更新成功')
    } else {
      await createKnowledgeBase(payload)
      ElMessage.success('创建成功')
    }
    dialogRef.value?.close()
    tableRef.value?.refresh()
  } catch (e: any) {
    // request interceptor 已显示错误
  }
}

const handleDelete = useConfirm(
  async (row: KnowledgeBaseItem) => {
    await deleteKnowledgeBase(row.id)
    ElMessage.success('删除成功')
    tableRef.value?.refresh()
  },
  { title: '删除知识库', message: '删除后该知识库下的所有文档和向量数据将被清空，确认吗？' }
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
