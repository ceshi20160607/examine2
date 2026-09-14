<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { api } from '../api'
import { systemContext, systemTokens } from '../session'
import { businessActionMessage, type ActionDelivery, type BusinessActionResult } from '../runtime-actions'

const props = defineProps<{ moduleCode: string; result?: BusinessActionResult<unknown> }>()
const emit = defineEmits<{ executed: [] }>()
const actionId = ref<number>()
const delivery = ref<ActionDelivery>()
const busy = ref(false)
const error = ref('')
let revision = 0
const storageKey = computed(() => {
  const context = systemContext.value
  return `unexamine.action-receipt.${context?.accountId}.${context?.systemId}.${context?.tenantMemberId}.${props.moduleCode}`
})
const failed = computed(() => ['CONFLICT', 'EXECUTION_FAILED'].includes(delivery.value?.businessStatus || props.result?.status || ''))
const visible = computed(() => Boolean(props.result || actionId.value))
const completed = computed(() => delivery.value?.businessStatus === 'EXECUTED'
  || props.result?.mode === 'DIRECT' && ['COMPLETED', 'EXECUTED'].includes(props.result.status))
const description = computed(() => {
  if (error.value) return error.value
  if (delivery.value?.message) return delivery.value.message
  return props.result?.mode === 'DIRECT' && completed.value
    ? '业务数据已保存，并已返回服务端的最新结果。'
    : '可刷新查看业务是否已生效。重试不会跳过审批、权限或数据版本检查。'
})
const title = computed(() => businessActionMessage({
  idempotencyRecordId: actionId.value || 0,
  mode: delivery.value?.mode || props.result?.mode || 'AFTER_APPROVAL',
  status: delivery.value?.businessStatus || props.result?.status || 'PENDING_APPROVAL',
  replayed: false, value: null, flowInstanceId: delivery.value?.flowInstanceId || null, delivery: delivery.value,
}, '业务变更已生效'))

watch([storageKey, () => props.result], () => {
  revision++
  delivery.value = props.result?.delivery
  error.value = ''
  busy.value = false
  try {
    if (props.result) {
      actionId.value = props.result.mode === 'DIRECT' ? undefined : props.result.idempotencyRecordId
      if (actionId.value) sessionStorage.setItem(storageKey.value, String(actionId.value))
      else sessionStorage.removeItem(storageKey.value)
    } else {
      const saved = Number(sessionStorage.getItem(storageKey.value))
      actionId.value = Number.isSafeInteger(saved) && saved > 0 ? saved : undefined
    }
  } catch { actionId.value = props.result?.idempotencyRecordId }
  if (actionId.value && !delivery.value) void refresh()
}, { immediate: true })

async function refresh(retry = false) {
  if (!actionId.value || busy.value) return
  const current = ++revision
  busy.value = true
  error.value = ''
  const previous = delivery.value?.businessStatus
  try {
    const path = `/api/runtime/modules/${props.moduleCode}/records/action-results/${actionId.value}${retry ? '/retry' : ''}`
    const result = await api<ActionDelivery>(path, retry ? { method: 'POST' } : {}, systemTokens.value?.accessToken)
    if (current !== revision) return
    delivery.value = result
    if (result.businessStatus === 'EXECUTED' && previous !== 'EXECUTED') emit('executed')
  } catch (reason) {
    if (current === revision) error.value = reason instanceof Error ? reason.message : '暂时无法读取处理结果，请稍后刷新'
  } finally { if (current === revision) busy.value = false }
}

onBeforeUnmount(() => { revision++ })
</script>

<template>
  <a-alert v-if="visible" class="section-alert" show-icon
    :type="failed ? 'error' : completed ? 'success' : 'info'"
    :message="title" :description="description">
    <template v-if="actionId" #action>
      <a-space>
        <a-button size="small" :loading="busy" @click="refresh()">刷新结果</a-button>
        <a-button v-if="failed" size="small" :disabled="busy" @click="refresh(true)">重试原操作</a-button>
      </a-space>
    </template>
  </a-alert>
</template>
