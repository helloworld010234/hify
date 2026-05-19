<template>
  <div class="agent-list">
    <div class="page-header">
      <h2>Agent 管理</h2>
      <el-button type="primary" @click="handleAdd">新增 Agent</el-button>
    </div>

    <HifyTable
      ref="tableRef"
      :columns="columns"
      :api="fetchAgentList"
    >
      <template #toolbar>
        <el-input
          v-model="searchKey"
          placeholder="搜索 Agent 名称"
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
        <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
        <el-button link type="warning" @click="handleClone(row)">克隆</el-button>
        <el-button link type="danger" class="delete-btn" @click="handleDelete(row)">删除</el-button>
      </template>
    </HifyTable>

    <HifyFormDialog
      ref="dialogRef"
      title="Agent"
      width="640px"
      :rules="formRules"
      @submit="handleSubmit"
    >
      <template #default="{ form, isEdit }">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如 代码助手" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" placeholder="简短描述该 Agent 的用途" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="系统提示词" prop="systemPrompt">
          <el-input
            v-model="form.systemPrompt"
            type="textarea"
            :rows="4"
            placeholder="输入系统提示词（System Prompt），定义 Agent 的角色和行为"
          />
        </el-form-item>
        <el-form-item label="模型配置" prop="modelConfigId">
          <el-select v-model="form.modelConfigId" placeholder="选择模型" style="width: 100%" :disabled="isEdit && false">
            <el-option
              v-for="opt in modelOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="温度" prop="temperature">
          <div class="slider-row">
            <el-slider v-model="form.temperature" :min="0" :max="1" :step="0.01" style="flex: 1" />
            <span class="slider-value">{{ form.temperature }}</span>
          </div>
        </el-form-item>
        <el-form-item label="最大 Token" prop="maxTokens">
          <el-input-number v-model="form.maxTokens" :min="1" :max="32768" style="width: 100%" />
        </el-form-item>
        <el-form-item label="上下文轮数" prop="maxContextTurns">
          <el-input-number v-model="form.maxContextTurns" :min="1" :max="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="enabled">
          <el-switch
            v-model="form.enabled"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </template>
    </HifyFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import HifyTable from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import { useConfirm } from '@/composables/useConfirm'
import {
  getAgentList,
  createAgent,
  updateAgent,
  deleteAgent,
  cloneAgent,
  getAgentDetail,
  type AgentListItem
} from '@/api/agent'
import { getProviderList, getProviderDetail } from '@/api/provider'

const tableRef = ref<InstanceType<typeof HifyTable>>()
const dialogRef = ref<InstanceType<typeof HifyFormDialog>>()
const searchKey = ref('')

const modelOptions = ref<{ label: string; value: number }[]>([])

const columns = [
  { prop: 'name', label: '名称', minWidth: 160 },
  { prop: 'description', label: '描述', minWidth: 200 },
  { prop: 'modelName', label: '模型', width: 160 },
  { prop: 'enabled', label: '状态', width: 90, slot: 'enabled' },
  { prop: 'action', label: '操作', width: 200, slot: 'action' }
]

const formRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  modelConfigId: [{ required: true, message: '请选择模型配置', trigger: 'change', type: 'number' }],
  temperature: [{ required: true, message: '请设置温度', trigger: 'change', type: 'number' }],
  maxTokens: [{ required: true, message: '请设置最大 Token', trigger: 'change', type: 'number' }],
  maxContextTurns: [{ required: true, message: '请设置上下文轮数', trigger: 'change', type: 'number' }]
}

/** 加载所有可用模型选项 */
const loadModelOptions = async () => {
  try {
    const providerRes = await getProviderList({ page: 1, size: 100 })
    if (providerRes.code !== 200) return
    const providers = providerRes.data || []
    const opts: { label: string; value: number }[] = []
    for (const p of providers) {
      const detail = await getProviderDetail(p.id)
      const models = detail.modelConfigs || []
      for (const m of models) {
        if (m.status === 'active') {
          opts.push({
            label: `${p.name} / ${m.modelName || m.modelCode}`,
            value: m.id
          })
        }
      }
    }
    modelOptions.value = opts
  } catch (e) {
    // 静默失败，模型选择框显示为空
  }
}

const fetchAgentList = (params: { page: number; size: number }) => {
  return getAgentList({
    ...params,
    keyword: searchKey.value || undefined
  })
}

const handleAdd = () => {
  dialogRef.value?.open({
    description: '',
    systemPrompt: '',
    temperature: 0.70,
    maxTokens: 2048,
    maxContextTurns: 10,
    enabled: 1
  })
}

const handleEdit = async (row: AgentListItem) => {
  try {
    const detail = await getAgentDetail(row.id)
    dialogRef.value?.open({
      id: detail.id,
      name: detail.name,
      description: detail.description,
      systemPrompt: detail.systemPrompt,
      modelConfigId: detail.modelConfigId,
      temperature: detail.temperature,
      maxTokens: detail.maxTokens,
      maxContextTurns: detail.maxContextTurns,
      enabled: detail.enabled
    })
  } catch (e: any) {
    // request interceptor 已显示错误
  }
}

const handleSubmit = async (data: any) => {
  try {
    const { _isEdit, ...payload } = data
    if (_isEdit && data.id) {
      await updateAgent(data.id, payload)
      ElMessage.success('更新成功')
    } else {
      await createAgent(payload)
      ElMessage.success('创建成功')
    }
    dialogRef.value?.close()
    tableRef.value?.refresh()
  } catch (e: any) {
    // request interceptor 已显示错误
  }
}

const handleDelete = useConfirm(
  async (row: AgentListItem) => {
    await deleteAgent(row.id)
    tableRef.value?.refresh()
  },
  { title: '删除 Agent', message: '删除后不可恢复，确认吗？' }
)

const handleClone = useConfirm(
  async (row: AgentListItem) => {
    const newId = await cloneAgent(row.id)
    ElMessage.success(`克隆成功，新 Agent ID: ${newId}`)
    tableRef.value?.refresh()
  },
  { title: '克隆 Agent', message: `确认克隆 "${row.name}" 吗？克隆后的 Agent 默认禁用。` }
)

onMounted(() => {
  loadModelOptions()
})
</script>

<style scoped>
.agent-list {
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

.slider-row {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  width: 100%;
}
.slider-value {
  min-width: 40px;
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

.delete-btn {
  margin-left: 8px;
}

:deep(.el-table__row) {
  height: 52px;
}
</style>
