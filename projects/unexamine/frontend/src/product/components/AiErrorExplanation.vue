<script setup lang="ts">
import { ref, watch } from 'vue'
import { api, ApiError } from '../api'

interface Explanation {
  errorCode: string
  originalMessage: string
  status: 'EXPLAINED' | 'UNAVAILABLE'
  explanation: string
  suggestions: string[]
  modelUsed: boolean
}

const props = defineProps<{ error: ApiError; operation: string; token: string }>()
const result = ref<Explanation>()
const loading = ref(false)
const unavailable = ref('')

/** 只解释已发生的业务失败；原错误和请求结果由调用页面保留。 */
async function explain() {
  if (!props.token || loading.value) return
  loading.value = true
  unavailable.value = ''
  result.value = undefined
  try {
    const response = await api<Explanation>('/api/system/ai/error-explanations', {
      method: 'POST', body: JSON.stringify({ errorCode: props.error.code,
        errorMessage: props.error.message, operation: props.operation,
        requestId: props.error.traceId }),
    }, props.token)
    result.value = response
    if (response.status !== 'EXPLAINED') unavailable.value = '智能解释暂不可用，请按原错误提示处理或联系管理员。'
  } catch {
    unavailable.value = '智能解释暂不可用，请按原错误提示处理或联系管理员。'
  } finally {
    loading.value = false
  }
}

watch(() => [props.error.code, props.error.message, props.error.traceId, props.operation], () => {
  result.value = undefined
  unavailable.value = ''
})
</script>

<template>
  <section class="ai-error-explanation">
    <div class="ai-error-head"><span>错误码：{{ error.code }}</span>
      <a-button size="small" :loading="loading" @click="explain">智能解释原因</a-button>
    </div>
    <a-alert v-if="unavailable" type="info" show-icon :message="unavailable" />
    <div v-if="result?.status === 'EXPLAINED'" class="ai-error-result">
      <strong>原因解释</strong><p>{{ result.explanation }}</p>
      <template v-if="result.suggestions.length"><strong>可以这样处理</strong>
        <ul><li v-for="(suggestion, index) in result.suggestions" :key="index">{{ suggestion }}</li></ul>
      </template>
      <small>以上为辅助说明，请以原错误提示和当前权限为准。</small>
    </div>
  </section>
</template>

<style scoped>
.ai-error-explanation { display: grid; gap: 10px; margin: 10px 0 16px; padding: 12px; border: 1px solid #fcd34d; border-radius: 8px; background: #fffbeb; }
.ai-error-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap; }
.ai-error-head span, .ai-error-result small { color: #92400e; font-size: 12px; }
.ai-error-result { display: grid; gap: 6px; color: #374151; }
.ai-error-result p, .ai-error-result ul { margin: 0; }
.ai-error-result ul { padding-left: 20px; }
</style>
