<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { updateTools } from '@/api/agent'
import type { ToolOption } from '@/api/agent'

interface Props {
  agentId?: number
  modelValue: number[]
  toolOptions: ToolOption[]
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:modelValue': [val: number[]]
  refresh: []
}>()

const handleChange = async (val: number[]) => {
  if (props.agentId == null) return
  try {
    await updateTools(props.agentId, val || [])
    ElMessage.success('工具绑定更新成功')
    emit('update:modelValue', val || [])
  } catch (e) {
    emit('refresh')
  }
}
</script>

<template>
  <el-select
    v-if="props.agentId != null"
    :model-value="props.modelValue"
    multiple
    collapse-tags
    collapse-tags-tooltip
    size="small"
    style="width: 160px"
    @update:modelValue="handleChange"
  >
    <el-option
      v-for="tool in props.toolOptions"
      :key="tool.id"
      :label="tool.name"
      :value="tool.id"
    />
  </el-select>
  <span v-else>--</span>
</template>
