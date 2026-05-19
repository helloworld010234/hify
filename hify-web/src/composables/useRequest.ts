import { ref, type Ref } from 'vue'

export interface UseRequestReturn<T> {
  data: Ref<T | null>
  loading: Ref<boolean>
  error: Ref<Error | null>
  execute: (request: () => Promise<T>) => Promise<T | undefined>
}

/**
 * 请求状态管理 Composable
 * @returns { data, loading, error, execute }
 *
 * 示例：
 * const { data, loading, execute } = useRequest<Provider[]>()
 * await execute(() => fetchProviders())
 */
export function useRequest<T = any>(): UseRequestReturn<T> {
  const data = ref<T | null>(null)
  const loading = ref(false)
  const error = ref<Error | null>(null)

  const execute = async (request: () => Promise<T>): Promise<T | undefined> => {
    loading.value = true
    error.value = null
    try {
      const result = await request()
      data.value = result
      return result
    } catch (err: any) {
      error.value = err instanceof Error ? err : new Error(String(err))
      throw err
    } finally {
      loading.value = false
    }
  }

  return {
    data: data as Ref<T | null>,
    loading: loading as Ref<boolean>,
    error: error as Ref<Error | null>,
    execute
  }
}

export default useRequest
