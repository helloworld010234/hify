<template>
  <div class="workflow-create">
    <div class="page-header">
      <h2>Create Workflow</h2>
    </div>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      class="workflow-form"
    >
      <el-form-item label="Name" prop="name">
        <el-input
          data-testid="workflow-name-input"
          v-model="form.name"
          placeholder="Enter a workflow name"
          maxlength="100"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="Description" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          placeholder="Enter a workflow description"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="Workflow JSON" prop="configJson">
        <div class="json-editor-wrapper">
          <el-input
            v-model="form.configJson"
            type="textarea"
            :rows="20"
            placeholder="Enter workflow config JSON"
            class="json-editor"
          />
          <div class="json-actions">
            <el-button type="primary" plain size="small" @click="handleFormat">
              Format
            </el-button>
          </div>
        </div>
      </el-form-item>

      <el-form-item>
        <el-button data-testid="workflow-submit-button" type="primary" @click="handleSubmit">Submit</el-button>
        <el-button @click="handleCancel">Cancel</el-button>
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
      name: 'Start',
      config: {
        inputVariables: [
          { name: 'userMessage', type: 'string', description: 'User input', required: true }
        ]
      }
    },
    {
      nodeKey: 'classify',
      type: 'LLM',
      name: 'Classify',
      config: {
        modelConfigId: 3,
        prompt: 'Classify the user question into one word: sales, support, or technical.',
        outputVariable: 'intent'
      }
    },
    {
      nodeKey: 'route',
      type: 'CONDITION',
      name: 'Route',
      config: {
        expression: '{{classify.intent}}',
        outputVariable: 'result'
      }
    },
    {
      nodeKey: 'sales',
      type: 'LLM',
      name: 'Sales',
      config: {
        modelConfigId: 3,
        prompt: 'You are a sales assistant. Answer warmly and professionally.',
        outputVariable: 'reply'
      }
    },
    {
      nodeKey: 'support',
      type: 'LLM',
      name: 'Support',
      config: {
        modelConfigId: 3,
        prompt: 'You are a support assistant. Resolve the user issue patiently.',
        outputVariable: 'reply'
      }
    },
    {
      nodeKey: 'technical',
      type: 'LLM',
      name: 'Technical',
      config: {
        modelConfigId: 3,
        prompt: 'You are a technical assistant. Provide a precise technical answer.',
        outputVariable: 'reply'
      }
    },
    {
      nodeKey: 'end',
      type: 'END',
      name: 'End',
      config: {
        outputVariable: 'reply'
      }
    }
  ],
  edges: [
    { sourceNodeKey: 'start', targetNodeKey: 'classify' },
    { sourceNodeKey: 'classify', targetNodeKey: 'route' },
    { sourceNodeKey: 'route', targetNodeKey: 'sales', condition: 'sales' },
    { sourceNodeKey: 'route', targetNodeKey: 'support', condition: 'support' },
    { sourceNodeKey: 'route', targetNodeKey: 'technical', condition: 'technical' },
    { sourceNodeKey: 'sales', targetNodeKey: 'end' },
    { sourceNodeKey: 'support', targetNodeKey: 'end' },
    { sourceNodeKey: 'technical', targetNodeKey: 'end' }
  ]
}, null, 2)

const form = ref({
  name: '',
  description: '',
  configJson: defaultConfig
})

const rules: FormRules = {
  name: [{ required: true, message: 'Please enter a workflow name', trigger: 'blur' }],
  configJson: [
    { required: true, message: 'Please enter a workflow config', trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: (error?: Error) => void) => {
        if (!value) {
          callback()
          return
        }
        try {
          const config = JSON.parse(value)
          if (!Array.isArray(config.nodes) || !Array.isArray(config.edges)) {
            callback(new Error('Workflow config must contain nodes and edges arrays'))
            return
          }
          callback()
        } catch (e) {
          callback(new Error('Invalid JSON format'))
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
    ElMessage.success('Formatted successfully')
  } catch (e) {
    ElMessage.error('Invalid JSON, unable to format')
  }
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  let config: any
  try {
    config = JSON.parse(form.value.configJson)
  } catch (e) {
    ElMessage.error('Invalid JSON format')
    return
  }

  if (!Array.isArray(config.nodes) || !Array.isArray(config.edges)) {
    ElMessage.error('Workflow config must contain nodes and edges arrays')
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
    ElMessage.success('Workflow created successfully')
    router.push('/workflows')
  } catch (e: any) {
    // request interceptor already shows the failure.
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
