<script setup lang="ts">
import { AlertCircle, CheckCircle2, Clock3, RefreshCw } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'

import { runtimeApi } from '@/services/config'
import type { RuntimeFieldCapability, RuntimeFieldValue } from '@/types/config'
import RuntimeFieldDisplay from './RuntimeFieldDisplay.vue'
import { runtimeErrorMessage } from './runtimeRecordModel'

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

const envelope = computed(() => current.value?.value && typeof current.value.value === 'object'
  ? current.value.value as Record<string, unknown> : {})
const state = computed(() => String(envelope.value.recalculationState ?? 'READY'))
const resultField = computed<RuntimeFieldValue>(() => ({
  fieldCode: props.field.fieldCode,
  fieldName: props.field.fieldName,
  type: String(props.field.schema.referenceResultType ?? 'TEXT'),
  value: envelope.value.result,
  displayValue: current.value?.displayValue,
}))

async function reloadUntilSettled() {
  if (!props.recordId) return
  for (let attempt = 0; attempt < 10; attempt += 1) {
    const record = await runtimeApi.record(props.systemId, props.moduleCode, props.recordId)
    const next = record.values.find((item) => item.fieldCode === props.field.fieldCode)
    current.value = next
    emit('refreshed', next, record.version)
    const nextState = next?.value && typeof next.value === 'object'
      ? String((next.value as Record<string, unknown>).recalculationState ?? 'READY') : 'READY'
    if (nextState !== 'PENDING') return
    await new Promise((resolve) => window.setTimeout(resolve, 700))
  }
}

async function retry() {
  if (!props.recordId || props.recordVersion === undefined) return
  retrying.value = true
  error.value = ''
  try {
    const response = await runtimeApi.retryReference(props.systemId, props.moduleCode, props.recordId, props.field.fieldCode, props.recordVersion)
    if (current.value?.value && typeof current.value.value === 'object') {
      current.value = { ...current.value, value: { ...(current.value.value as Record<string, unknown>), recalculationState: response.recalculationState } }
    }
    await reloadUntilSettled()
  } catch (cause) {
    error.value = runtimeErrorMessage(cause, '引用重新计算请求失败，请重试。')
  } finally {
    retrying.value = false
  }
}

watch(() => props.value, (value) => { current.value = value }, { immediate: true, deep: true })
</script>

<template>
  <div class="reference-field" :data-state="state.toLowerCase()">
    <div class="reference-value"><RuntimeFieldDisplay :field="resultField" /></div>
    <div class="reference-state">
      <CheckCircle2 v-if="state === 'READY'" :size="15" /><Clock3 v-else-if="state === 'PENDING'" :size="15" /><AlertCircle v-else :size="15" />
      <span>{{ state === 'READY' ? '已同步' : state === 'PENDING' ? '正在重新计算' : '重新计算失败' }}</span>
      <a-button v-if="state === 'FAILED' && canRetry" size="small" :loading="retrying" @click="retry"><RefreshCw :size="14" />重试</a-button>
    </div>
    <small v-if="state === 'FAILED' && current?.failureCorrelationId">故障编号：{{ current.failureCorrelationId }}</small>
    <a-alert v-if="error" type="error" show-icon :message="error" />
  </div>
</template>

<style scoped>
.reference-field{display:grid;gap:6px}.reference-value{min-height:24px;font-weight:650;overflow-wrap:anywhere}.reference-state{display:flex;align-items:center;gap:6px;color:#147362;font-size:12px}.reference-state .ant-btn{display:inline-flex;align-items:center;gap:5px;margin-left:4px}.reference-field[data-state="pending"] .reference-state{color:#9a6712}.reference-field[data-state="failed"] .reference-state,.reference-field>small{color:#a53b33}.reference-field>small{overflow-wrap:anywhere}
</style>
