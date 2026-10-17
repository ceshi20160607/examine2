<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api, ApiError } from '../api'
import { systemContext, systemTokens } from '../session'

interface SummaryPoint { text: string; sourceEventIds: number[] }
interface ApprovalSummary {
  instanceId: number
  status: 'SUMMARIZED' | 'UNAVAILABLE'
  summary: string
  points: SummaryPoint[]
  modelUsed: boolean
  sourceCount: number
}

const props = defineProps<{ instanceId: number; instanceVersion: number; historyCount: number }>()
const emit = defineEmits<{ source: [eventId: number] }>()
const token = computed(() => systemTokens.value?.accessToken || '')
const identity = computed(() => {
  const context = systemContext.value
  return JSON.stringify([context?.accountId, context?.systemId, context?.tenantId,
    context?.memberId, context?.tenantMemberId, context?.contextRevision,
    context?.roleIds, context?.permissions, context?.dataScopes])
})
const loading = ref(false)
const error = ref('')
const result = ref<ApprovalSummary>()
let generation = 0

/** 切换实例或身份时清除上一条审批的模型结果，避免旧摘要短暂显示给新成员。 */
watch([() => props.instanceId, () => props.instanceVersion, () => props.historyCount, identity, token], () => {
  generation++
  result.value = undefined
  error.value = ''
  loading.value = false
})

async function summarize() {
  if (!props.instanceId || !token.value || loading.value) return
  const requestId = ++generation
  const instanceId = props.instanceId
  const requestToken = token.value
  const requestIdentity = identity.value
  result.value = undefined
  error.value = ''
  loading.value = true
  try {
    const response = await api<ApprovalSummary>(
      `/api/system/ai/approval-summaries/instances/${instanceId}`, {
        method: 'POST', body: JSON.stringify({}),
      }, requestToken)
    if (requestId !== generation || instanceId !== props.instanceId
      || requestToken !== token.value || requestIdentity !== identity.value) return
    if (response.instanceId !== instanceId) { error.value = '摘要与当前审批不匹配，请刷新后重试'; return }
    result.value = response
  } catch (reason) {
    if (requestId !== generation || requestToken !== token.value || requestIdentity !== identity.value) return
    result.value = undefined
    error.value = reason instanceof ApiError && [401, 403, 404].includes(reason.httpStatus || 0)
      ? '当前身份已无法查看该审批记录。请刷新审批列表。'
      : reason instanceof ApiError ? `${reason.message}${reason.traceId ? `（追踪号 ${reason.traceId}）` : ''}`
        : '暂时无法生成摘要，请按下方原始审批历史查看。'
  } finally { if (requestId === generation) loading.value = false }
}
</script>

<template>
  <section class="approval-summary panel-card">
    <div class="approval-summary__heading">
      <div><strong>审批记录智能摘要</strong><small>按当前可查看的历史生成，原审批记录仍是核对依据。</small></div>
      <a-button :loading="loading" @click="summarize">{{ result ? '重新生成' : '生成摘要' }}</a-button>
    </div>
    <a-alert v-if="error" type="warning" show-icon :message="error" />
    <a-alert v-else-if="result?.status === 'UNAVAILABLE'" type="info" show-icon
      :message="result.summary || '智能摘要暂不可用，请查看原始审批历史。'" />
    <div v-else-if="result" class="approval-summary__result">
      <p>{{ result.summary }}</p>
      <ol v-if="result.points.length">
        <li v-for="(point, index) in result.points" :key="index">
          <span>{{ point.text }}</span>
          <span class="approval-summary__sources">
            <a-button v-for="eventId in point.sourceEventIds" :key="eventId" type="link" size="small"
              @click="emit('source', eventId)">历史 #{{ eventId }}</a-button>
          </span>
        </li>
      </ol>
      <small>依据 {{ result.sourceCount }} 条当前可查看的审批事件；请以原始历史为准。</small>
    </div>
  </section>
</template>

<style scoped>
.approval-summary { display: grid; gap: 12px; padding: 16px; border: 1px solid #dbeafe; background: #f8fbff; }
.approval-summary__heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.approval-summary__heading > div { display: grid; gap: 4px; }
.approval-summary small { color: #64748b; }
.approval-summary__result { display: grid; gap: 10px; }
.approval-summary__result p { margin: 0; color: #1e293b; }
.approval-summary__result ol { display: grid; gap: 8px; margin: 0; padding-left: 20px; }
.approval-summary__result li { line-height: 1.5; }
.approval-summary__sources { display: inline-flex; flex-wrap: wrap; margin-left: 8px; }
@media (max-width: 650px) { .approval-summary__heading { align-items: start; flex-direction: column; } }
</style>
