import { ElMessage } from 'element-plus'

export const notifySuccess = (message: string) => {
  ElMessage.success({ message, plain: true })
}

export const notifyError = (message: string) => {
  ElMessage.error({ message, plain: true })
}

export const notifyWarning = (message: string) => {
  ElMessage.warning({ message, plain: true })
}

export const notifyInfo = (message: string) => {
  ElMessage.info({ message, plain: true })
}

export default {
  success: notifySuccess,
  error: notifyError,
  warning: notifyWarning,
  info: notifyInfo
}
