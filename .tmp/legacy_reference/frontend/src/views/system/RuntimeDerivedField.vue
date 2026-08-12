<script setup lang="ts">
import { AlertCircle, CheckCircle2, Clock3, RefreshCw } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'

import { runtimeApi } from '@/services/config'
import type { RuntimeFieldCapability, RuntimeFieldValue } from '@/types/config'
import RuntimeFieldDisplay from './RuntimeFieldDisplay.vue'
import {
  derivedFailureCorrelationId,
  derivedRuntimeResult,
  derivedRuntimeState,
  runtimeErrorMessage,
} from './runtimeRecordModel'

const props = defineProps<{
  systemId: string
  moduleCode: string
  recordId?: string
  recordVersion?: number
  field: RuntimeFieldCapability
  value?: RuntimeFieldValue
  canRetry?: boolean
}>()

const emit = defineEmits<{ refreshed: [value: RuntimeFieldValue | undefined, version: number] }>()

const current = ref<RuntimeFieldValue>()
const retrying = ref(false)
const error = ref('')

const state = computed(() => current.value ? derivedRuntimeState(current.value) : 'UNAVAILABLE')
const result = computed(() => derivedRuntimeResult(current.value))
const lookupItems = computed(() => props.field.type === 'LOOKUP' && Array.isArray(result.value)
  ? result.value.filter((item) => item !== null && item !== undefined).map(String) : [])
const resultField = computed<RuntimeFieldValue>(() => ({
  fieldCode: props.field.fieldCode,
  fieldName: props.field.fieldName,
  type: String(props.field.schema.derivedQueryType ?? 'TEXT'),
  value: result.value,
  displayValue: current.value?.displayValue,
}))
const hasLastValid = computed(() => Array.isArray(result.value) ? result.value.length > 0
  : result.value !== null && result.value !== undefined && result.value !== '')
const correlationId = computed(() => derivedFailureCorrelationId(current.value))
const stateLabel = computed(() => ({
  READY: 'READY · 已计算',
  PENDING: 'PENDING · 正在重新计算',
  FAILED: 'FAILED · 重新计算失败',
  UNAVAILABLE: '当前无权查看此派生结果',
})[state.value])

async function reloadUntilSettled() {
  if (!props.recordId) return
  for (let attempt = 0; attempt < 10; attempt += 1) {
    const record = await runtimeApi.record(props.systemId, props.moduleCode, props.recordId)
    const next = record.values.find((item) => item.fieldCode === props.field.fieldCode)
    current.value = next
    emit('refreshed', next, record.version)
    if (derivedRuntimeState(next) !== 'PENDING') return
    await new Promise((resolve) => window.setTimeout(resolve, 700))
  }
}

async function retry() {
  if (!props.recordId || props.recordVersion === undefined) return
  retrying.value = true
  error.value = ''
  try {
    const response = await runtimeApi.retryDerived(
      props.systemId,
      props.moduleCode,
      props.recordId,
      props.field.fieldCode,
      props.recordVersion,
    )
    if (current.value?.value && typeof current.value.value === 'object') {
      current.value = {
        ...current.value,
        value: { ...(current.value.value as Record<string, unknown>), recalculationState: response.recalculationState },
      }
    }
    await reloadUntilSettled()
  } catch (cause) {
    error.value = runtimeErrorMessage(cause, '派生字段重新计算请求失败，请稍后重试。')
  } finally {
    retrying.value = false
  }
}

watch(() => props.value, (value) => { current.value = value }, { immediate: true, deep: true })
</script>

<template>
  <div class="derived-field" :data-state="state.toLowerCase()">
    <div v-if="field.type === 'LOOKUP'" class="lookup-values" aria-label="有序查找结果">
      <span v-for="(item, index) in lookupItems" :key="`${index}-${item}`" class="lookup-chip">{{ item }}</span>
      <span v-if="!lookupItems.length" class="empty-value">-</span>
    </div>
    <div v-else class="derived-value"><RuntimeFieldDisplay :field="resultField" /></div>
    <div class="derived-state">
      <CheckCircle2 v-if="state === 'READY'" :size="15" />
      <Clock3 v-else-if="state === 'PENDING'" :size="15" />
      <AlertCircle v-else :size="15" />
      <span>{{ stateLabel }}</span>
      <small v-if="state !== 'READY' && hasLastValid">当前展示上次有效值</small>
      <a-button v-if="state === 'FAILED' && canRetry" size="small" :loading="retrying" @click="retry"><RefreshCw :size="14" />重试</a-button>
    </div>
    <small v-if="state === 'FAILED' && correlationId" class="failure-id">故障编号：{{ correlationId }}</small>
    <a-alert v-if="error" type="error" show-icon :message="error" />
  </div>
</template>

<style scoped>
.derived-field{display:grid;min-width:0;gap:6px}.derived-value{min-height:24px;font-weight:650;overflow-wrap:anywhere}.lookup-values{display:flex;min-width:0;flex-wrap:wrap;gap:6px}.lookup-chip{display:inline-flex;max-width:100%;align-items:center;padding:3px 8px;border:1px solid #cddfda;border-radius:999px;background:#edf7f4;color:#0b6d62;font-size:12px;overflow-wrap:anywhere}.empty-value{color:#7a8790}.derived-state{display:flex;min-width:0;align-items:center;flex-wrap:wrap;gap:6px;color:#147362;font-size:12px}.derived-state small{color:#697782}.derived-state .ant-btn{display:inline-flex;align-items:center;gap:5px;margin-left:2px}.derived-field[data-state="pending"] .derived-state{color:#9a6712}.derived-field[data-state="failed"] .derived-state,.failure-id{color:#a53b33}.derived-field[data-state="unavailable"] .derived-state{color:#697782}.failure-id{overflow-wrap:anywhere}@media(max-width:600px){.derived-state{align-items:flex-start}.derived-state small{flex-basis:100%}}
</style>
