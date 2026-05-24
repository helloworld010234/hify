<template>
  <div class="mcp-detail">
    <div class="page-header">
      <div class="header-left">
        <el-button link @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <h2>{{ server?.name || 'MCP Server 详情' }}</h2>
        <el-tag v-if="server" :type="statusTagType(server.status)" size="small">
          {{ statusLabel(server.status) }}
        </el-tag>
      </div>
      <div class="header-right">
        <el-button size="small" @click="loadDetail">刷新</el-button>
        <el-button type="primary" size="small" @click="handleTest">连通测试</el-button>
      </div>
    </div>

    <div v-loading="loading" class="debug-panel">
      <div class="tool-sidebar">
        <div class="sidebar-title">工具列表</div>
        <el-empty v-if="!tools.length" description="暂无工具" :image-size="60" />
        <div
          v-for="tool in tools"
          :key="tool.id"
          class="tool-item"
          :class="{ active: selectedTool?.id === tool.id }"
          @click="selectTool(tool)"
        >
          <div class="tool-name">{{ tool.name }}</div>
          <div class="tool-desc">{{ tool.description }}</div>
        </div>
      </div>

      <div class="debug-main">
        <template v-if="selectedTool">
          <div class="tool-header">
            <h3>{{ selectedTool.name }}</h3>
            <p class="tool-description">{{ selectedTool.description }}</p>
          </div>

          <el-divider />

          <el-form label-position="top" class="param-form">
            <el-form-item
              v-for="field in schemaFields"
              :key="field.name"
              :label="field.label"
              :required="field.required"
            >
              <template #label>
                <span>{{ field.label }}</span>
                <span v-if="field.required" class="required-star"> *</span>
              </template>
              <el-input
                v-if="field.type === 'string'"
                v-model="formValues[field.name]"
                :placeholder="field.description"
                clearable
              />
              <el-input-number
                v-else-if="field.type === 'number' || field.type === 'integer'"
                v-model="formValues[field.name]"
                :placeholder="field.description"
                style="width: 100%"
                controls-position="right"
              />
              <el-switch
                v-else-if="field.type === 'boolean'"
                v-model="formValues[field.name]"
              />
              <el-input
                v-else
                v-model="formValues[field.name]"
                :placeholder="field.description"
                clearable
              />
            </el-form-item>

            <el-form-item>
              <el-button
                type="primary"
                :loading="calling"
                @click="handleCall"
              >
                调用
              </el-button>
            </el-form-item>
          </el-form>

          <el-divider />

          <div class="result-section">
            <div class="section-title">调用结果</div>
            <div v-if="lastResult" class="result-card" :class="{ error: !lastResult.success }">
              <div class="result-meta">
                <el-tag :type="lastResult.success ? 'success' : 'danger'" size="small">
                  {{ lastResult.success ? '成功' : '失败' }}
                </el-tag>
                <span class="latency">{{ lastResult.latencyMs }}ms</span>
              </div>
              <pre v-if="lastResult.result" class="result-body">{{ lastResult.result }}</pre>
              <pre v-if="lastResult.errorMessage" class="result-body error-text">{{ lastResult.errorMessage }}</pre>
            </div>
            <el-empty v-else description="点击「调用」查看结果" :image-size="60" />
          </div>

          <div v-if="history.length" class="history-section">
            <div class="section-title">最近调用记录（保留5次）</div>
            <div
              v-for="(item, idx) in history"
              :key="idx"
              class="history-item"
              :class="{ error: !item.success }"
            >
              <div class="history-meta">
                <el-tag :type="item.success ? 'success' : 'danger'" size="small">
                  {{ item.success ? '成功' : '失败' }}
                </el-tag>
                <span class="latency">{{ item.latencyMs }}ms</span>
                <span class="time">{{ item.time }}</span>
              </div>
              <pre v-if="item.result" class="history-body">{{ item.result }}</pre>
              <pre v-if="item.errorMessage" class="history-body error-text">{{ item.errorMessage }}</pre>
            </div>
          </div>
        </template>

        <el-empty v-else description="请从左侧选择工具" :image-size="80" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import {
  getMcpServerDetail,
  testMcpConnection,
  debugMcpTool,
  type McpServerDetail,
  type McpTool,
  type McpDebugResponse,
} from '@/api/mcp'

const route = useRoute()
const router = useRouter()
const serverId = computed(() => Number(route.params.id))

const loading = ref(false)
const server = ref<McpServerDetail | null>(null)
const tools = ref<McpTool[]>([])
const selectedTool = ref<McpTool | null>(null)
const formValues = reactive<Record<string, any>>({})
const calling = ref(false)
const lastResult = ref<McpDebugResponse | null>(null)
const history = ref<Array<McpDebugResponse & { time: string }>>([])

interface SchemaField {
  name: string
  label: string
  type: string
  description: string
  required: boolean
}

const schemaFields = computed<SchemaField[]>(() => {
  const tool = selectedTool.value
  if (!tool) return []
  let schema = tool.inputSchema
  if (!schema || !schema.properties) {
    schema = MOCK_SCHEMAS[tool.name] || null
  }
  if (!schema?.properties) return []
  const props = schema.properties
  const required = schema.required || []
  return Object.entries(props).map(([name, config]: [string, any]) => ({
    name,
    label: config.title || name,
    type: config.type || 'string',
    description: config.description || '',
    required: required.includes(name),
  }))
})

const MOCK_SCHEMAS: Record<string, any> = {
  'check_refund_eligibility': { type: 'object', properties: { order_id: { type: 'string', description: '订单编号' } }, required: ['order_id'] },
  'submit_refund': { type: 'object', properties: { order_id: { type: 'string', description: '订单编号' }, user_id: { type: 'string', description: '用户ID，可选' }, amount: { type: 'number', description: '退款金额' }, reason: { type: 'string', description: '退款原因' } }, required: ['order_id', 'amount', 'reason'] },
  'get_refund_status': { type: 'object', properties: { order_id: { type: 'string', description: '订单编号' } }, required: ['order_id'] },
  'cancel_refund': { type: 'object', properties: { refund_id: { type: 'string', description: '退款单号' } }, required: ['refund_id'] },
}

const loadDetail = async () => {
  loading.value = true
  try {
    const detail = await getMcpServerDetail(serverId.value)
    server.value = detail
    tools.value = (detail.tools || []).map((t: any) => ({
      ...t,
      description: t.description || '',
      inputSchema: (t.inputSchema && Object.keys(t.inputSchema).length > 0) ? t.inputSchema : (MOCK_SCHEMAS[t.name] || null),
    }))
    if (tools.value.length && !selectedTool.value) {
      selectTool(tools.value[0])
    }
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const selectTool = (tool: McpTool) => {
  selectedTool.value = tool
  // 兜底：如果 inputSchema 为空，尝试用 mock
  if (!tool.inputSchema && MOCK_SCHEMAS[tool.name]) {
    tool.inputSchema = MOCK_SCHEMAS[tool.name]
  }
  // 重置表单
  Object.keys(formValues).forEach(k => delete formValues[k])
  let schema = tool.inputSchema
  if (!schema || !schema.properties) {
    schema = MOCK_SCHEMAS[tool.name] || null
  }
  if (schema?.properties) {
    const props = schema.properties
    const required = schema.required || []
    Object.entries(props).forEach(([name, config]: [string, any]) => {
      const type = config.type || 'string'
      if (type === 'boolean') {
        formValues[name] = false
      } else if (type === 'number' || type === 'integer') {
        formValues[name] = undefined
      } else {
        formValues[name] = ''
      }
    })
  }
}

const handleTest = async () => {
  try {
    const res = await testMcpConnection(serverId.value)
    if (res.success) {
      ElMessage.success(`连通成功，延迟 ${res.latencyMs}ms，发现 ${res.toolCount} 个工具`)
      loadDetail()
    } else {
      ElMessage.error(`连通失败：${res.errorMessage}`)
    }
  } catch (e: any) {
    // interceptor 已提示
  }
}

const handleCall = async () => {
  if (!selectedTool.value) return

  // 必填校验
  for (const field of schemaFields.value) {
    if (field.required) {
      const val = formValues[field.name]
      if (val === undefined || val === null || val === '') {
        ElMessage.warning(`「${field.label}」为必填项`)
        return
      }
    }
  }

  calling.value = true
  try {
    const args: Record<string, any> = {}
    for (const field of schemaFields.value) {
      if (formValues[field.name] !== undefined && formValues[field.name] !== null) {
        args[field.name] = formValues[field.name]
      }
    }

    const res = await debugMcpTool(serverId.value, {
      toolName: selectedTool.value.name,
      arguments: args,
    })

    lastResult.value = res

    // 加入历史记录，保留最近5次
    history.value.unshift({
      ...res,
      time: new Date().toLocaleTimeString(),
    })
    if (history.value.length > 5) {
      history.value = history.value.slice(0, 5)
    }

    if (res.success) {
      ElMessage.success(`调用成功，耗时 ${res.latencyMs}ms`)
    } else {
      ElMessage.error(res.errorMessage || '调用失败')
    }
  } catch (e: any) {
    const errorMsg = e.message || '网络错误'
    lastResult.value = {
      success: false,
      result: '',
      errorMessage: errorMsg,
      latencyMs: 0,
    }
    history.value.unshift({
      success: false,
      result: '',
      errorMessage: errorMsg,
      latencyMs: 0,
      time: new Date().toLocaleTimeString(),
    })
    if (history.value.length > 5) {
      history.value = history.value.slice(0, 5)
    }
  } finally {
    calling.value = false
  }
}

const goBack = () => {
  router.back()
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

loadDetail()
</script>

<style scoped>
.mcp-detail {
  padding: var(--space-4);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-5);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.header-left h2 {
  font-size: var(--text-2xl);
  font-weight: var(--font-semibold);
  color: var(--color-text-primary);
  margin: 0;
}

.header-right {
  display: flex;
  gap: var(--space-2);
}

.debug-panel {
  display: flex;
  gap: var(--space-4);
  min-height: 600px;
}

.tool-sidebar {
  width: 280px;
  flex-shrink: 0;
  background: var(--color-bg-secondary);
  border-radius: var(--radius-lg);
  padding: var(--space-4);
  overflow-y: auto;
}

.sidebar-title {
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
  color: var(--color-text-secondary);
  margin-bottom: var(--space-3);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.tool-item {
  padding: var(--space-3);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
  margin-bottom: var(--space-2);
}

.tool-item:hover {
  background: var(--color-bg-sidebar-hover);
}

.tool-item.active {
  background: var(--color-bg-sidebar-active);
  border-left: 3px solid var(--color-primary-400);
}

.tool-name {
  font-weight: var(--font-medium);
  color: var(--color-text-primary);
  font-size: var(--text-sm);
  margin-bottom: 2px;
}

.tool-desc {
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.debug-main {
  flex: 1;
  background: var(--color-bg-secondary);
  border-radius: var(--radius-lg);
  padding: var(--space-5);
  overflow-y: auto;
}

.tool-header h3 {
  margin: 0 0 var(--space-2);
  font-size: var(--text-lg);
  color: var(--color-text-primary);
}

.tool-description {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
  line-height: 1.5;
}

.param-form {
  max-width: 600px;
}

.required-star {
  color: var(--color-danger);
}

.result-section,
.history-section {
  margin-top: var(--space-4);
}

.section-title {
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
  color: var(--color-text-secondary);
  margin-bottom: var(--space-3);
}

.result-card,
.history-item {
  background: var(--color-bg-base);
  border-radius: var(--radius-md);
  padding: var(--space-3);
  margin-bottom: var(--space-3);
  border: 1px solid var(--color-border);
}

.result-card.error,
.history-item.error {
  border-color: var(--color-danger-light, #fde2e2);
}

.result-meta,
.history-meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}

.latency {
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.time {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
  margin-left: auto;
}

.result-body,
.history-body {
  margin: 0;
  padding: var(--space-3);
  background: var(--color-bg-sidebar);
  border-radius: var(--radius-sm);
  font-family: var(--font-mono, 'SF Mono', Monaco, monospace);
  font-size: var(--text-xs);
  line-height: 1.6;
  color: var(--color-text-primary);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 300px;
  overflow-y: auto;
}

.error-text {
  color: var(--color-danger);
}
</style>
