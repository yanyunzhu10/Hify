/**
 * 通用请求状态管理 composable
 * 替代每个页面里的 try-catch-finally 样板，统一管理 data/loading/error 三态
 *
 * 注意：底层 utils/request.ts 已在拦截器中通过 ElMessage 抛出错误提示，
 * 这里只捕获状态，不再重复 toast，避免双弹。
 *
 * 用法：
 *   const { data, loading, execute } = useRequest(getProviderList)
 *   await execute({ page: 1, size: 10 })
 *
 *   // 立即执行
 *   const { data, loading } = useRequest(getStats, { immediate: true })
 */
import { ref, shallowRef, type Ref, type ShallowRef } from 'vue'

export interface UseRequestOptions<T, P extends unknown[]> {
  /** 创建时立即调用一次 execute */
  immediate?: boolean
  /** immediate 模式下传给 execute 的参数 */
  immediateArgs?: P
  /** data 初始值 */
  initialData?: T
  /** 请求成功后回调（拿到最新 data） */
  onSuccess?: (data: T) => void
  /** 请求失败后回调（已被全局拦截器 toast 过） */
  onError?: (error: Error) => void
}

export interface UseRequestReturn<T, P extends unknown[]> {
  /** 响应数据（shallowRef，避免大对象深度响应开销） */
  data: ShallowRef<T | null>
  /** 请求进行中 */
  loading: Ref<boolean>
  /** 上次请求的错误（成功后清空） */
  error: ShallowRef<Error | null>
  /** 触发请求；失败时返回 undefined */
  execute: (...args: P) => Promise<T | undefined>
}

export function useRequest<T, P extends unknown[] = []>(
  apiMethod: (...args: P) => Promise<T>,
  options: UseRequestOptions<T, P> = {},
): UseRequestReturn<T, P> {
  const data = shallowRef<T | null>(options.initialData ?? null)
  const loading = ref(false)
  const error = shallowRef<Error | null>(null)

  const execute = async (...args: P): Promise<T | undefined> => {
    loading.value = true
    error.value = null
    try {
      const result = await apiMethod(...args)
      data.value = result
      options.onSuccess?.(result)
      return result
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e))
      error.value = err
      options.onError?.(err)
      return undefined
    } finally {
      loading.value = false
    }
  }

  if (options.immediate) {
    execute(...((options.immediateArgs ?? []) as P))
  }

  return { data, loading, error, execute }
}
