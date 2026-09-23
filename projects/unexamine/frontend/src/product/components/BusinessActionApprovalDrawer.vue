<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { api, ApiError } from '../api'
import { runtimeStatusLabel } from '../flow-runtime'
import { productDateTime, productStatus, versionLabel } from '../presentation'
import { systemContext, systemTokens } from '../session'
import type { BusinessActionProgress } from '../types'
import ProductDetailDrawer from './ProductDetailDrawer.vue'
import RuntimeBusinessViewPanel from './RuntimeBusinessViewPanel.vue'

const props = defineProps<{ open: boolean; moduleCode: string; actionId?: number }>()
const emit = defineEmits<{ close: [] }>()
const progress = ref<BusinessActionProgress>()
const loading = ref(false)
const error = ref('')
const waiting = ref(false)
let generation = 0
let active = true
const identity = computed(() => {
  const context = systemContext.value
  return JSON.stringify([context?.platformId, context?.accountId, context?.systemId, context?.tenantId,
    context?.memberId, context?.tenantMemberId, context?.contextRevision, context?.roleIds, context?.permissions,
    context?.dataScopes, context?.tenantSwitchContext])
})

function requestContext() {
  return { generation, token: systemTokens.value?.accessToken, identity: identity.value,
    moduleCode: props.moduleCode, actionId: props.actionId }
}

function requestCurrent(context: ReturnType<typeof requestContext>) {
  return active && props.open && context.generation === generation && context.token === systemTokens.value?.accessToken
    && context.identity === identity.value && context.moduleCode === props.moduleCode && context.actionId === props.actionId
}

function clear() {
  generation++
  progress.value = undefined
  loading.value = false
  error.value = ''
  waiting.value = false
}

function close() {
  clear()
  emit('close')
}

async function refresh() {
  if (!active || !props.open || !props.moduleCode || !props.actionId || !systemTokens.value?.accessToken || loading.value) return
  clear()
  const context = requestContext()
  loading.value = true
  try {
    const result = await api<BusinessActionProgress>(
      `/api/flow-runtime/business-actions/${encodeURIComponent(context.moduleCode)}/${context.actionId}`, {}, context.token)
    if (!requestCurrent(context)) return
    if (result.actionId !== context.actionId || result.moduleCode !== context.moduleCode) {
      error.value = '审批进度与当前业务动作不匹配，请关闭后重新打开'
      return
    }
    progress.value = result
  } catch (reason) {
    if (!requestCurrent(context)) return
    // 失权后清除全部历史内容；不得保留上次获得的业务数据或回退到来源目录。
    progress.value = undefined
    if (reason instanceof ApiError && [401, 403, 404].includes(reason.httpStatus || 0)) {
      error.value = '当前身份已无法查看此审批，可能是权限或共享关系已变化。请关闭后刷新业务列表。'
    } else {
      waiting.value = reason instanceof ApiError && reason.httpStatus === 409 && reason.code === 'ACTION_FLOW_NOT_STARTED'
      error.value = reason instanceof Error ? reason.message : '暂时无法读取审批进度，请稍后刷新'
    }
  } finally { if (requestCurrent(context)) loading.value = false }
}

function statusName(status?: string | null) {
  if (!status) return '—'
  const business = ({ EXECUTED: '业务已生效', CONFLICT: '业务变更冲突', EXECUTION_FAILED: '业务执行失败',
    SKIPPED: '已跳过', ENTERED: '已进入', ACTIVE: '处理中', FLOW_STARTED: '流程已发起', ABORTED: '已中止' } as Record<string, string>)[status]
  if (business) return business
  const runtime = runtimeStatusLabel(status)
  return runtime !== status ? runtime : productStatus(status).label
}

watch([() => props.open, () => props.moduleCode, () => props.actionId,
  () => systemTokens.value?.accessToken, identity], (current, previous) => {
  clear()
  // immediate 首次没有旧身份；历史链接首次挂载 open=true 时也应正常读取本人申请。
  if (previous?.length && current[4] !== previous[4]) { if (props.open) emit('close'); return }
  if (props.open) void refresh()
}, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => { active = false; clear() })
</script>

<template>
  <ProductDetailDrawer :open="open" title="审批进度" width="min(760px, 100vw)" :loading="loading" @close="close">
    <template #extra><a-button :loading="loading" @click="refresh">刷新进度</a-button></template>
    <a-alert v-if="error" show-icon :type="waiting ? 'info' : 'error'" :message="waiting ? '审批尚未启动' : '无法查看审批进度'" :description="error" />
    <div v-else-if="progress" class="approval-progress">
      <a-alert v-if="progress.message && progress.message !== progress.businessView?.message" show-icon type="info" :message="progress.message" />
      <a-descriptions :column="1" bordered size="small">
        <a-descriptions-item label="流程">{{ progress.flowName }}</a-descriptions-item>
        <a-descriptions-item label="流程版本">{{ versionLabel(progress.flowVersionNumber) }}</a-descriptions-item>
        <a-descriptions-item label="审批状态">{{ statusName(progress.status) }}</a-descriptions-item>
        <a-descriptions-item label="业务状态">{{ statusName(progress.businessStatus) }}</a-descriptions-item>
        <a-descriptions-item label="当前节点">{{ progress.currentNodeName || '暂无当前节点' }}</a-descriptions-item>
        <a-descriptions-item label="开始时间">{{ productDateTime(progress.startTime) }}</a-descriptions-item>
        <a-descriptions-item v-if="progress.finishTime" label="结束时间">{{ productDateTime(progress.finishTime) }}</a-descriptions-item>
      </a-descriptions>
      <section>
        <h3>已进入节点</h3>
        <a-timeline v-if="progress.nodes.length">
          <a-timeline-item v-for="(node, index) in progress.nodes" :key="`${node.nodeKey}.${index}`">
            <strong>{{ node.name }}</strong> · {{ statusName(node.status) }}
            <p>进入 {{ productDateTime(node.enterTime) }}<span v-if="node.leaveTime"> · 离开 {{ productDateTime(node.leaveTime) }}</span></p>
          </a-timeline-item>
        </a-timeline>
        <a-empty v-else description="尚无已进入节点" />
      </section>
      <section>
        <h3>审批历史</h3>
        <a-timeline v-if="progress.history.length">
          <a-timeline-item v-for="(event, index) in progress.history" :key="index">
            <strong>{{ event.eventName }}</strong><span v-if="event.nodeName"> · {{ event.nodeName }}</span>
            <p v-if="event.beforeStatus || event.afterStatus">{{ event.beforeStatus ? statusName(event.beforeStatus) : '开始' }} → {{ event.afterStatus ? statusName(event.afterStatus) : '已记录' }}</p>
            <small>{{ productDateTime(event.occurredTime) }}</small>
          </a-timeline-item>
        </a-timeline>
        <a-empty v-else description="尚无审批历史" />
      </section>
      <RuntimeBusinessViewPanel :view="progress.businessView" />
    </div>
  </ProductDetailDrawer>
</template>

<style scoped>
.approval-progress { display: grid; gap: 20px; }
h3 { margin: 0 0 16px; font-size: 15px; }
p { margin: 4px 0; color: #64748b; }
small { color: #64748b; }
</style>
