<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { api, ApiError } from '../api'
import { systemContext, systemTokens } from '../session'
import { businessActionMessage, type ActionDelivery, type BusinessActionResult } from '../runtime-actions'
import BusinessActionApprovalDrawer from './BusinessActionApprovalDrawer.vue'

const props = defineProps<{ moduleCode: string; result?: BusinessActionResult<unknown> }>()
const emit = defineEmits<{ executed: [] }>()
const actionId = ref<number>()
const delivery = ref<ActionDelivery>()
const currentResult = ref<BusinessActionResult<unknown>>()
const progressOpen = ref(false)
const busy = ref(false)
const error = ref('')
let revision = 0
const identity = computed(() => {
  const context = systemContext.value
  return JSON.stringify([context?.platformId, context?.accountId, context?.systemId, context?.tenantId,
    context?.memberId, context?.tenantMemberId, context?.contextRevision, context?.roleIds, context?.permissions, context?.dataScopes])
})
let resultIdentity = identity.value
const storageKey = computed(() => {
  const context = systemContext.value
  return `unexamine.action-receipt.${context?.accountId}.${context?.systemId}.${context?.tenantMemberId}.${props.moduleCode}`
})
const failed = computed(() => ['CONFLICT', 'EXECUTION_FAILED'].includes(delivery.value?.businessStatus || currentResult.value?.status || ''))
const visible = computed(() => Boolean(currentResult.value || actionId.value))
const completed = computed(() => delivery.value?.businessStatus === 'EXECUTED'
  || currentResult.value?.mode === 'DIRECT' && ['COMPLETED', 'EXECUTED'].includes(currentResult.value.status))
const canViewProgress = computed(() => {
  const mode = delivery.value?.mode || currentResult.value?.mode
  return Boolean(systemTokens.value?.accessToken && actionId.value && mode && mode !== 'DIRECT'
    && (delivery.value?.flowInstanceId || currentResult.value?.flowInstanceId))
})
const description = computed(() => {
  if (error.value) return error.value
  if (delivery.value?.message) return delivery.value.message
  return currentResult.value?.mode === 'DIRECT' && completed.value
    ? '业务数据已保存，并已返回服务端的最新结果。'
    : '可刷新查看业务是否已生效。重试不会跳过审批、权限或数据版本检查。'
})
const title = computed(() => businessActionMessage({
  idempotencyRecordId: actionId.value || 0,
  mode: delivery.value?.mode || currentResult.value?.mode || 'AFTER_APPROVAL',
  status: delivery.value?.businessStatus || currentResult.value?.status || 'PENDING_APPROVAL',
  replayed: false, value: null, flowInstanceId: delivery.value?.flowInstanceId || currentResult.value?.flowInstanceId || null, delivery: delivery.value,
}, '业务变更已生效'))

watch([storageKey, () => props.result, () => systemTokens.value?.accessToken, identity], (current, previous) => {
  revision++
  progressOpen.value = false
  // 身份切换不把旧调用方的回执保存到新成员名下，后续只接受当前身份的新结果。
  if (!previous || current[1] !== previous[1]) resultIdentity = identity.value
  currentResult.value = resultIdentity === identity.value ? props.result : undefined
  const result = currentResult.value
  delivery.value = result?.delivery
  error.value = ''
  busy.value = false
  try {
    if (result) {
      actionId.value = result.mode === 'DIRECT' ? undefined : result.idempotencyRecordId
      if (actionId.value) sessionStorage.setItem(storageKey.value, String(actionId.value))
      else sessionStorage.removeItem(storageKey.value)
    } else {
      const saved = Number(sessionStorage.getItem(storageKey.value))
      actionId.value = Number.isSafeInteger(saved) && saved > 0 ? saved : undefined
    }
  } catch { actionId.value = result?.mode !== 'DIRECT' ? result?.idempotencyRecordId : undefined }
  if (actionId.value && !delivery.value) void refresh()
}, { immediate: true, flush: 'sync' })

function requestContext() {
  return { revision, token: systemTokens.value?.accessToken, identity: identity.value,
    moduleCode: props.moduleCode, actionId: actionId.value }
}

function requestCurrent(context: ReturnType<typeof requestContext>) {
  return context.revision === revision && context.token === systemTokens.value?.accessToken
    && context.identity === identity.value && context.moduleCode === props.moduleCode && context.actionId === actionId.value
}

async function refresh(retry = false) {
  if (!actionId.value || busy.value || !systemTokens.value?.accessToken) return
  revision++
  const context = requestContext()
  busy.value = true
  error.value = ''
  const previous = delivery.value?.businessStatus
  try {
    const path = `/api/runtime/modules/${encodeURIComponent(context.moduleCode)}/records/action-results/${context.actionId}${retry ? '/retry' : ''}`
    const result = await api<ActionDelivery>(path, retry ? { method: 'POST' } : {}, context.token)
    if (!requestCurrent(context)) return
    delivery.value = result
    if (result.businessStatus === 'EXECUTED' && previous !== 'EXECUTED') emit('executed')
  } catch (reason) {
    if (requestCurrent(context)) {
      if (reason instanceof ApiError && [401, 403, 404].includes(reason.httpStatus || 0)) {
        delivery.value = undefined
        currentResult.value = undefined
        progressOpen.value = false
      }
      error.value = reason instanceof Error ? reason.message : '暂时无法读取处理结果，请稍后刷新'
    }
  } finally { if (requestCurrent(context)) busy.value = false }
}

watch(canViewProgress, value => { if (!value) progressOpen.value = false }, { flush: 'sync' })
onBeforeUnmount(() => { revision++; progressOpen.value = false; delivery.value = undefined; currentResult.value = undefined })
</script>

<template>
  <a-alert v-if="visible" class="section-alert" show-icon
    :type="failed ? 'error' : completed ? 'success' : 'info'"
    :message="title" :description="description">
    <template v-if="actionId" #action>
      <a-space>
        <a-button size="small" :loading="busy" @click="refresh()">刷新结果</a-button>
        <a-button v-if="canViewProgress" size="small" @click="progressOpen = true">查看审批进度</a-button>
        <a-button v-if="failed" size="small" :disabled="busy" @click="refresh(true)">重试原操作</a-button>
      </a-space>
    </template>
  </a-alert>
  <BusinessActionApprovalDrawer :open="progressOpen && canViewProgress" :module-code="moduleCode" :action-id="actionId" @close="progressOpen = false" />
</template>
