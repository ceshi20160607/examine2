import { ref, type Ref } from 'vue'

/**
 * 页面级异步请求：统一 loading / error，配合 ErrorBlock 展示。
 */
export function usePageRequest(externalLoading?: Ref<boolean>) {
  const loading = externalLoading ?? ref(false)
  const error = ref<string | null>(null)

  function clearError() {
    error.value = null
  }

  function capture(e: unknown) {
    error.value = e instanceof Error ? e.message : String(e)
  }

  async function run<T>(fn: () => Promise<T>, opts?: { manageLoading?: boolean }): Promise<T | undefined> {
    const manageLoading = opts?.manageLoading ?? true
    if (manageLoading) loading.value = true
    clearError()
    try {
      return await fn()
    } catch (e: unknown) {
      capture(e)
      return undefined
    } finally {
      if (manageLoading) loading.value = false
    }
  }

  return { loading, error, run, clearError, capture }
}
