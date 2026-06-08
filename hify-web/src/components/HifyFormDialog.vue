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
const submitting = ref(false)

const open = (data?: Record<string, any>) => {
  visible.value = true
  submitting.value = false
  isEdit.value = !!(data && data.id)
  nextTick(() => {
    formData.value = data ? { ...data } : {}
    formRef.value?.clearValidate?.()
  })
}

const close = () => {
  submitting.value = false
  visible.value = false
}

const finishSubmit = () => {
  submitting.value = false
}

const handleSubmit = async () => {
  if (submitting.value) return
  const valid = await formRef.value?.validate?.().catch(() => false)
  if (!valid) return
  submitting.value = true
  emit('submit', { ...formData.value, _isEdit: isEdit.value })
}

const emit = defineEmits<{
  submit: [data: { [key: string]: any; _isEdit: boolean }]
}>()

defineExpose({
  open,
  close,
  finishSubmit
})
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="(isEdit ? '缂栬緫' : '鏂板') + title"
    :width="width"
    :close-on-click-modal="false"
    :show-close="!submitting"
    :close-on-press-escape="!submitting"
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
        <el-button data-testid="dialog-cancel-button" :disabled="submitting" @click="close">鍙栨秷</el-button>
        <el-button data-testid="dialog-submit-button" type="primary" :loading="submitting" @click="handleSubmit">
          {{ isEdit ? '淇濆瓨' : '鍒涘缓' }}
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
