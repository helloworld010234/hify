import { ElMessageBox, ElMessage } from 'element-plus'

export interface ConfirmOptions {
  title?: string
  message?: string
  confirmButtonText?: string
  cancelButtonText?: string
  type?: 'warning' | 'error' | 'info'
}

/**
 * 删除确认 Composable
 * @param api 删除接口方法
 * @param options 确认框配置
 * @returns 调用函数，传入 id 即可执行完整流程
 *
 * 示例：
 * const handleDelete = useConfirm(deleteProviderApi, { title: '删除模型' })
 * handleDelete(row.id)
 */
export function useConfirm<T = any>(
  api: (id: T) => Promise<any>,
  options: ConfirmOptions = {}
) {
  const {
    title = '确认删除',
    message = '此操作不可恢复，是否继续？',
    confirmButtonText = '确定',
    cancelButtonText = '取消',
    type = 'warning'
  } = options

  return async (id: T): Promise<any | undefined> => {
    try {
      await ElMessageBox.confirm(message, title, {
        confirmButtonText,
        cancelButtonText,
        type,
        confirmButtonClass: 'el-button--danger'
      })
      const res = await api(id)
      ElMessage.success('操作成功')
      return res
    } catch (error: any) {
      if (error === 'cancel' || error?.message === 'cancel') return
      ElMessage.error(error?.message || '操作失败')
      throw error
    }
  }
}

export default useConfirm
