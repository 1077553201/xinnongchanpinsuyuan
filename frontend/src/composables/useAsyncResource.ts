import { onScopeDispose, ref, shallowRef } from 'vue'
import { apiErrorMessage } from '@/api/http'

/** 每次刷新清除旧结果；只接收最后一次请求，避免旧响应覆盖新页面。 */
export function useAsyncResource<T>(fetcher: () => Promise<T>) {
  const data = shallowRef<T | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const updatedAt = ref<Date | null>(null)
  let revision = 0

  async function load() {
    const current = ++revision
    loading.value = true
    error.value = null
    data.value = null
    updatedAt.value = null
    try {
      const result = await fetcher()
      if (current === revision) {
        data.value = result
        updatedAt.value = new Date()
      }
    } catch (cause) {
      if (current === revision) error.value = apiErrorMessage(cause)
    } finally {
      if (current === revision) loading.value = false
    }
  }

  onScopeDispose(() => { revision += 1 })
  return { data, loading, error, updatedAt, load }
}
