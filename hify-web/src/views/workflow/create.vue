<template>
  <div class="workflow-create">
    <div class="page-header">
      <h2>新建工作流</h2>
    </div>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      class="workflow-form"
    >
      <el-form-item label="名称" prop="name">
        <el-input
          v-model="form.name"
          placeholder="请输入工作流名称"
          maxlength="100"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="描述" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          placeholder="请输入工作流描述（可选）"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="工作流配置" prop="configJson">
        <div class="json-editor-wrapper">
          <el-input
            v-model="form.configJson"
            type="textarea"
            :rows="20"
            placeholder="请输入工作流配置 JSON"
            class="json-editor"
          />
          <div class="json-actions">
            <el-button type="primary" plain size="small" @click="handleFormat">
              格式化
            </el-button>
          </div>
        </div>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" @click="handleSubmit">提交</el-button>
        <el-button @click="handleCancel">取消</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createWorkflow } from '@/api/workflow'

const router = useRouter()
const formRef = ref<FormInstance>()

const defaultConfig = JSON.stringify({
  nodes: [
    {
      nodeKey: 'start',
      type: 'START',
      name: '开始',
      config: {
        inputVariables: [
          { name: 'userMessage', type: 'string', description: '用户输入', required: true }
        ]
      }
    },
    {
      nodeKey: 'classify',
      type: 'LLM',
      name: '问题分类',
      config: {
        modelConfigId: 3,
        prompt: '判断用户问题类型，只回复一个词：售前、售后、技术。',
        outputVariable: 'intent'
      }
    },
    {
      nodeKey: 'route',
      type: 'CONDITION',
      name: '路由分发',
      config: {
        expression: '{{classify.intent}}',
        outputVariable: 'result'
      }
    },
    {
      nodeKey: 'pre_sales',
      type: 'LLM',
      name: '售前咨询',
      config: {
        modelConfigId: 3,
        prompt: '你是售前顾问，请热情专业地回答用户问题。',
        outputVariable: 'reply'
      }
    },
    {
      nodeKey: 'after_sales',
      type: 'LLM',
      name: '售后服务',
      config: {
        modelConfigId: 3,
        prompt: '你是售后客服，请耐心解决用户问题。',
        outputVariable: 'reply'
      }
    },
    {
      nodeKey: 'tech_support',
      type: 'LLM',
      name: '技术支持',
      config: {
        modelConfigId: 3,
        prompt: '你是技术支持工程师，请提供专业的技术解答。',
        outputVariable: 'reply'
      }
    },
    {
      nodeKey: 'end',
      type: 'END',
      name: '结束',
      config: {
        outputVariable: 'reply'
      }
    }
  ],
  edges: [
    { sourceNodeKey: 'start', targetNodeKey: 'classify' },
    { sourceNodeKey: 'classify', targetNodeKey: 'route' },
    { sourceNodeKey: 'route', targetNodeKey: 'pre_sales', condition: '售前' },
    { sourceNodeKey: 'route', targetNodeKey: 'after_sales', condition: '售后' },
    { sourceNodeKey: 'route', targetNodeKey: 'tech_support', condition: '技术' },
    { sourceNodeKey: 'pre_sales', targetNodeKey: 'end' },
    { sourceNodeKey: 'after_sales', targetNodeKey: 'end' },
    { sourceNodeKey: 'tech_support', targetNodeKey: 'end' }
  ]
}, null, 2)

const form = ref({
  name: '',
  description: '',
  configJson: defaultConfig
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入工作流名称', trigger: 'blur' }],
  configJson: [
    { required: true, message: '请输入工作流配置', trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: (error?: Error) => void) => {
        if (!value) {
          callback()
          return
        }
        try {
          const config = JSON.parse(value)
          if (!Array.isArray(config.nodes) || !Array.isArray(config.edges)) {
            callback(new Error('工作流配置必须包含 nodes 和 edges 数组'))
            return
          }
          callback()
        } catch (e) {
          callback(new Error('JSON 格式错误，请检查工作流配置'))
        }
      },
      trigger: 'blur'
    }
  ]
}

const handleFormat = () => {
  try {
    const parsed = JSON.parse(form.value.configJson)
    form.value.configJson = JSON.stringify(parsed, null, 2)
    ElMessage.success('格式化成功')
  } catch (e) {
    ElMessage.error('JSON 格式错误，无法格式化')
  }
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  let config: any
  try {
    config = JSON.parse(form.value.configJson)
  } catch (e) {
    ElMessage.error('JSON 格式错误，请检查工作流配置')
    return
  }

  if (!Array.isArray(config.nodes) || !Array.isArray(config.edges)) {
    ElMessage.error('工作流配置必须包含 nodes 和 edges 数组')
    return
  }

  try {
    await createWorkflow({
      name: form.value.name,
      description: form.value.description,
      enabled: 1,
      nodes: config.nodes,
      edges: config.edges
    })
    ElMessage.success('创建成功')
    router.push('/workflows')
  } catch (e: any) {
    // request interceptor 已显示错误
  }
}

const handleCancel = () => {
  router.push('/workflows')
}
</script>

<style scoped>
.workflow-create {
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
.workflow-form {
  max-width: 960px;
}
.json-editor-wrapper {
  position: relative;
}
.json-editor :deep(textarea) {
  font-family: 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
}
.json-actions {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 1;
}
</style>
