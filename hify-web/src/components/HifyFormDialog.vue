<script setup lang="ts">
import { ref, nextTick } from 'vue'

interface Props {
  title?: string
  width?: string
  rules?: Record<string, any>
}

withDefaults(defineProps<Props>(), {
  title: '',
  width: '560px',
  rules: () => ({})
})

const visible = defineModel<boolean>({ default: false })

const isEdit = ref(false)
const formData = ref<Record<string, any>>({})
const formRef = ref<any>(null)

const open = (data?: Record<string, any>) => {
  visible.value = true
  isEdit.value = !!(data && data.id)
  nextTick(() => {
    formData.value = data ? { ...data } : {}
    formRef.value?.clearValidate?.()
  })
}

const close = () => {
  visible.value = false
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate?.().catch(() => false)
  if (!valid) return
  emit('submit', { ...formData.value, _isEdit: isEdit.value })
}

const emit = defineEmits<{
  submit: [{ [key: string]: any; _isEdit: boolean }]
}>()

defineExpose({
  open,
  close
})
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="(isEdit ? '编辑' : '新增') + title"
    :width="width"
    :close-on-click-modal="false"
    destroy-on-close
    class="hify-form-dialog"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="100px"
      class="form"
    >
      <slot :form="formData" :is-edit="isEdit" />
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">
          {{ isEdit ? '保存' : '创建' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.form {
  padding: var(--space-2) 0;
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
}
</style>
