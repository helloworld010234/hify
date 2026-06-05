<template>
  <div class="agent-list">
    <div class="page-header">
      <h2>Agent 管理</h2>
      <el-button type="primary" @click="handleAdd">新增 Agent</el-button>
    </div>

    <el-alert
      v-if="metaError"
      type="warning"
      :title="metaError"
      description="请先确认模型供应商已配置并测试通过。"
      show-icon
      :closable="false"
      class="path-alert"
    />
    <el-alert
      v-else-if="!metaLoading && !hasAvailableModels()"
      type="info"
      title="暂无可用模型"
      description="请先到模型供应商管理中新增供应商并测试连接，再创建 Agent。"
      show-icon
      :closable="false"
      class="path-alert"
    />

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
          @clear="tableRef?.refresh()"
        />
      </template>

      <template #temperature="scope">
        <el-input-number
          :model-value="scope.row.temperature"
          :min="0"
          :max="1"
          :step="0.1"
          :precision="2"
          size="small"
          style="width: 80px"
          controls-position="right"
          @change="handleTemperatureChange(scope.row, $event)"
        />
      </template>

      <template #toolCount="scope">
        <AgentToolSelect
          :agent-id="scope.row.id"
          :model-value="scope.row.toolIds || []"
          :tool-options="toolOptions"
          @refresh="tableRef?.refresh()"
        />
      </template>

      <template #knowledgeBaseId="{ row }">
        <el-tag :type="row.knowledgeBaseId ? 'success' : 'info'" size="small">
          {{ row.knowledgeBaseId ? '已绑定' : '未绑定' }}
        </el-tag>
      </template>

      <template #maxContextTurns="scope">
        <el-input-number
          :model-value="scope.row.maxContextTurns"
          :min="1"
          :max="100"
          size="small"
          style="width: 90px"
          controls-position="right"
          @change="handleMaxContextTurnsChange(scope.row, $event)"
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
      width="680px"
      :rules="formRules"
      @submit="handleSubmit"
    >
      <template #default="{ form, isEdit: _isEdit }">
        <el-tabs v-model="activeTab" class="agent-tabs">
          <el-tab-pane label="基础配置" name="basic">
            <el-form-item label="名称" prop="name">
              <el-input v-model="form.name" placeholder="例如 代码助手" maxlength="100" show-word-limit />
            </el-form-item>
            <el-form-item label="描述" prop="description">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="2"
                placeholder="简短描述该 Agent 的用途"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="模型配置" prop="modelConfigId">
              <el-select v-model="form.modelConfigId" placeholder="选择模型" style="width: 100%">
                <el-option-group
                  v-for="group in modelGroups"
                  :key="group.providerId"
                  :label="group.providerName"
                >
                  <el-option
                    v-for="model in group.models"
                    :key="model.id"
                    :label="model.modelName"
                    :value="model.id"
                  />
                </el-option-group>
              </el-select>
            </el-form-item>
            <el-form-item label="知识库" prop="knowledgeBaseId">
              <el-select
                v-model="form.knowledgeBaseId"
                placeholder="不绑定知识库"
                clearable
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="knowledge in knowledgeOptions"
                  :key="knowledge.id"
                  :label="knowledge.name"
                  :value="knowledge.id"
                >
                  <div class="knowledge-option">
                    <span class="knowledge-option__name">{{ knowledge.name }}</span>
                    <span class="knowledge-option__meta">{{ knowledge.documentCount }} 个文档</span>
                  </div>
                </el-option>
              </el-select>
              <div v-if="!knowledgeOptions.length" class="field-hint">
                暂无知识库，可先保存 Agent，后续上传文档后再绑定。
              </div>
            </el-form-item>
            <el-form-item label="System Prompt" prop="systemPrompt">
              <el-input
                v-model="form.systemPrompt"
                type="textarea"
                :rows="6"
                placeholder="输入系统提示词，定义 Agent 的角色和行为"
              />
            </el-form-item>
            <el-form-item label="温度" prop="temperature">
              <div class="slider-row">
                <el-slider
                  v-model="form.temperature"
                  :min="0"
                  :max="1"
                  :step="0.1"
                  show-stops
                  style="flex: 1"
                />
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
          </el-tab-pane>

          <el-tab-pane label="工具绑定" name="tools">
            <div class="tool-bind-area">
              <p class="tool-hint">选择该 Agent 可以调用的 MCP 工具（可多选）</p>
              <el-checkbox-group v-model="form.toolIds">
                <el-checkbox
                  v-for="tool in toolOptions"
                  :key="tool.id"
                  :label="tool.id"
                  class="tool-checkbox"
                >
                  <div class="tool-label">
                    <span class="tool-name">{{ tool.name }}</span>
                    <span class="tool-desc">{{ tool.description }}</span>
                  </div>
                </el-checkbox>
              </el-checkbox-group>
              <el-empty v-if="!toolOptions.length" description="暂无可用工具" :image-size="60" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </HifyFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import HifyTable from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import AgentToolSelect from '@/components/AgentToolSelect.vue'
import { useConfirm } from '@/composables/useConfirm'
import {
  getAgentList,
  createAgent,
  updateAgent,
  deleteAgent,
  cloneAgent,
  getAgentDetail,
  getModelGroups,
  getTools,
  updateMaxContextTurns,
  updateTemperature,
  type AgentListItem,
  type ModelGroup,
  type ToolOption
} from '@/api/agent'
import {
  getKnowledgeBaseList,
  type KnowledgeBaseItem
} from '@/api/knowledge'

const tableRef = ref<InstanceType<typeof HifyTable>>()
const dialogRef = ref<InstanceType<typeof HifyFormDialog>>()
const searchKey = ref('')
const activeTab = ref('basic')

const modelGroups = ref<ModelGroup[]>([])
const toolOptions = ref<ToolOption[]>([])
const knowledgeOptions = ref<KnowledgeBaseItem[]>([])
const metaLoading = ref(true)
const metaError = ref('')

const columns = [
  { prop: 'name', label: '名称', minWidth: 160 },
  { prop: 'modelName', label: '模型', width: 160 },
  { prop: 'knowledgeBaseId', label: '知识库', width: 110, slot: 'knowledgeBaseId' },
  { prop: 'toolCount', label: '工具数', width: 180, slot: 'toolCount' },
  { prop: 'temperature', label: '温度', width: 100, slot: 'temperature' },
  { prop: 'maxContextTurns', label: '上下文轮数', width: 120, slot: 'maxContextTurns' },
  { prop: 'enabled', label: '状态', width: 80, slot: 'enabled' },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { prop: 'action', label: '操作', width: 220, slot: 'action' }
]

const formRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  modelConfigId: [{ required: true, message: '请选择模型配置', trigger: 'change', type: 'number' }],
  temperature: [{ required: true, message: '请设置温度', trigger: 'change', type: 'number' }],
  maxTokens: [{ required: true, message: '请设置最大 Token', trigger: 'change', type: 'number' }],
  maxContextTurns: [{ required: true, message: '请设置上下文轮数', trigger: 'change', type: 'number' }]
}

const hasAvailableModels = () => modelGroups.value.some(group => group.models?.length)

const loadMetaData = async () => {
  metaLoading.value = true
  metaError.value = ''
  try {
    const [modelRes, toolRes] = await Promise.all([getModelGroups(), getTools()])
    modelGroups.value = modelRes || []
    toolOptions.value = toolRes || []
    try {
      const knowledgeRes = await getKnowledgeBaseList({ page: 1, size: 100 })
      knowledgeOptions.value = knowledgeRes?.list || []
    } catch (knowledgeError) {
      knowledgeOptions.value = []
      ElMessage.warning('知识库列表加载失败，可先创建普通 Agent')
    }
  } catch (e: any) {
    metaError.value = e.message || '模型和工具元数据加载失败'
    ElMessage.warning('模型列表加载失败，请先检查供应商配置')
  } finally {
    metaLoading.value = false
  }
}

const fetchAgentList = (params: { page: number; size: number }) => {
  return getAgentList({
    ...params,
    keyword: searchKey.value || undefined
  })
}

const handleAdd = () => {
  if (!hasAvailableModels()) {
    ElMessage.warning('请先配置并测试可用的模型供应商')
    return
  }
  activeTab.value = 'basic'
  dialogRef.value?.open({
    description: '',
    systemPrompt: '',
    temperature: 0.7,
    knowledgeBaseId: null,
    maxTokens: 2048,
    maxContextTurns: 10,
    enabled: 1,
    toolIds: []
  })
}

const handleEdit = async (row: AgentListItem) => {
  try {
    const detail = await getAgentDetail(row.id)
    activeTab.value = 'basic'
    dialogRef.value?.open({
      id: detail.id,
      name: detail.name,
      description: detail.description,
      systemPrompt: detail.systemPrompt,
      modelConfigId: detail.modelConfigId,
      knowledgeBaseId: detail.knowledgeBaseId ?? null,
      temperature: detail.temperature,
      maxTokens: detail.maxTokens,
      maxContextTurns: detail.maxContextTurns,
      enabled: detail.enabled,
      toolIds: detail.toolIds || []
    })
  } catch (e: any) {
    // request interceptor already shows the failure.
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
      ElMessage.success('创建成功，可以前往对话页开始使用')
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
  async (row: AgentListItem) => {
    await deleteAgent(row.id)
    tableRef.value?.refresh()
  },
  { title: '删除 Agent', message: '删除后不可恢复，确认删除该 Agent 吗？' }
)

const handleClone = useConfirm(
  async (row: AgentListItem) => {
    const newId = await cloneAgent(row.id)
    ElMessage.success(`克隆成功，新 Agent ID: ${newId}`)
    tableRef.value?.refresh()
  },
  { title: '克隆 Agent', message: '确认克隆该 Agent 吗？克隆后的 Agent 默认禁用。' }
)

const handleMaxContextTurnsChange = async (row: AgentListItem, val: number | null | undefined) => {
  if (val === undefined || val === null || val < 1) return
  try {
    await updateMaxContextTurns(row.id, val)
    ElMessage.success('上下文轮数更新成功')
  } catch (e) {
    tableRef.value?.refresh()
  }
}

const handleTemperatureChange = async (row: AgentListItem, val: number | null | undefined) => {
  if (val === undefined || val === null || val < 0 || val > 1) return
  try {
    await updateTemperature(row.id, val)
    ElMessage.success('温度更新成功')
  } catch (e) {
    tableRef.value?.refresh()
  }
}

onMounted(() => {
  loadMetaData()
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

.path-alert {
  margin-bottom: var(--space-4);
}

.agent-tabs :deep(.el-tabs__content) {
  padding-top: var(--space-4);
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

.knowledge-option {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
}
.knowledge-option__name {
  color: var(--color-text-primary);
}
.knowledge-option__meta {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.field-hint {
  margin-top: var(--space-2);
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-secondary);
}

.tool-bind-area {
  padding: var(--space-2) 0;
}
.tool-hint {
  margin: 0 0 var(--space-4);
  font-size: 13px;
  color: var(--color-text-secondary);
}
.tool-checkbox {
  display: flex;
  align-items: flex-start;
  margin-bottom: var(--space-3);
  height: auto;
}
.tool-checkbox :deep(.el-checkbox__label) {
  white-space: normal;
  line-height: 1.5;
}
.tool-label {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.tool-name {
  font-weight: 500;
  color: var(--color-text-primary);
}
.tool-desc {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.delete-btn {
  margin-left: 8px;
}

:deep(.el-table__row) {
  height: 52px;
}
</style>
