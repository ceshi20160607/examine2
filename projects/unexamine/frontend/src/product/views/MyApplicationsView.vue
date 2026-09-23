<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter, type LocationQueryRaw } from 'vue-router'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { api, ApiError } from '../api'
import { runtimeStatusLabel } from '../flow-runtime'
import { productDateTime, productStatus } from '../presentation'
import { systemContext, systemTokens } from '../session'
import type { BusinessActionApplication, BusinessActionApplicationsPage } from '../types'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import ProductListWorkspace from '../components/ProductListWorkspace.vue'
import BusinessActionApprovalDrawer from '../components/BusinessActionApprovalDrawer.vue'

const route = useRoute()
const router = useRouter()
const items = ref<BusinessActionApplication[]>([])
const nextCursor = ref<number | null>(null)
const loading = ref(false)
const error = ref('')
const selected = ref<{ moduleCode: string; actionId: number }>()
let generation = 0
let active = true
const identity = computed(() => {
  const context = systemContext.value
  return JSON.stringify([context?.platformId, context?.accountId, context?.systemId, context?.tenantId,
    context?.memberId, context?.tenantMemberId, context?.contextRevision, context?.roleIds, context?.permissions, context?.dataScopes])
})

function requestContext() {
  return { generation, token: systemTokens.value?.accessToken, identity: identity.value }
}

function requestCurrent(context: ReturnType<typeof requestContext>) {
  return active && context.generation === generation && context.token === systemTokens.value?.accessToken
    && context.identity === identity.value
}

function clear() {
  generation++
  items.value = []
  nextCursor.value = null
  loading.value = false
  error.value = ''
  selected.value = undefined
}

function clearRouteTarget() {
  if (route.query.workspace !== 'applications' || (!route.query.module && !route.query.actionId)) return
  const query = { ...route.query }
  delete query.module
  delete query.actionId
  void router.replace({ path: route.path, query })
}

function closeProgress() {
  selected.value = undefined
  clearRouteTarget()
}

async function loadPage() {
  if (!active || loading.value || !systemTokens.value?.accessToken || !systemContext.value?.tenantMemberId) return
  generation++
  const context = requestContext()
  const cursor = nextCursor.value
  const query = new URLSearchParams({ pageSize: '20' })
  if (cursor !== null) query.set('cursor', String(cursor))
  loading.value = true
  error.value = ''
  try {
    const page = await api<BusinessActionApplicationsPage>(`/api/flow-runtime/business-actions?${query}`, {}, context.token)
    if (!requestCurrent(context)) return
    const existing = new Set(items.value.map(item => `${item.moduleCode}.${item.actionId}`))
    items.value = [...items.value, ...page.items.filter(item => !existing.has(`${item.moduleCode}.${item.actionId}`))]
    // 游标是服务端候选顺序；当前页过滤为空也必须保留继续加载入口。
    nextCursor.value = page.nextCursor
  } catch (reason) {
    if (!requestCurrent(context)) return
    if (reason instanceof ApiError && [401, 403, 404].includes(reason.httpStatus || 0)) {
      items.value = []
      nextCursor.value = null
      closeProgress()
      error.value = '当前身份无法查看这些申请，请重新登录或刷新当前系统权限。'
    } else error.value = reason instanceof Error ? reason.message : '暂时无法读取申请，请稍后刷新'
  } finally { if (requestCurrent(context)) loading.value = false }
}

function refresh() {
  clear()
  clearRouteTarget()
  void loadPage()
}

function openApplication(item: BusinessActionApplication) {
  selected.value = { moduleCode: item.moduleCode, actionId: item.actionId }
  const query: LocationQueryRaw = { ...route.query, workspace: 'applications', module: item.moduleCode, actionId: String(item.actionId) }
  delete query.instanceId
  delete query.taskId
  void router.replace({ path: route.path, query })
}

function statusName(status: string) {
  const business = ({ EXECUTED: '业务已生效', CONFLICT: '业务变更冲突', EXECUTION_FAILED: '业务执行失败',
    FLOW_STARTED: '流程已发起', ACTIVE: '处理中' } as Record<string, string>)[status]
  if (business) return business
  const runtime = runtimeStatusLabel(status)
  return runtime !== status ? runtime : productStatus(status).label
}

watch([identity, () => systemTokens.value?.accessToken], (_current, previous) => {
  clear()
  if (previous?.length) clearRouteTarget()
  void loadPage()
}, { immediate: true, flush: 'sync' })

watch(() => [route.query.workspace, route.query.module, route.query.actionId], () => {
  selected.value = undefined
  if (route.query.workspace !== 'applications' || typeof route.query.module !== 'string'
      || !route.query.module || typeof route.query.actionId !== 'string' || !/^\d+$/.test(route.query.actionId)) return
  const actionId = Number(route.query.actionId)
  if (Number.isSafeInteger(actionId) && actionId > 0) selected.value = { moduleCode: route.query.module, actionId }
}, { immediate: true, flush: 'sync' })

onBeforeUnmount(() => { active = false; clear() })
</script>

<template>
  <div class="my-applications-page">
    <ProductPageHeader kicker="当前系统" title="我的申请" description="查看你发起且当前仍有权访问的申请及审批进度。">
      <template #actions><a-button :loading="loading" @click="refresh"><ReloadOutlined />刷新申请</a-button></template>
    </ProductPageHeader>
    <ProductListWorkspace :loading="loading" :has-content="items.length > 0" :empty="!loading && !items.length" :error="error"
      empty-title="暂无可查看的申请" :empty-description="nextCursor !== null ? '此页暂无可查看的申请，可以继续加载后续申请。' : '当前没有可查看的申请。'" loading-title="正在读取申请">
      <template #summary>当前已加载 {{ items.length }} 项申请</template>
      <template #state-actions="{ state }">
        <a-button v-if="state === 'empty' && nextCursor !== null" :loading="loading" @click="loadPage">继续加载</a-button>
        <a-button v-else @click="refresh">刷新申请</a-button>
      </template>
      <div class="my-application-list">
        <article v-for="item in items" :key="`${item.moduleCode}.${item.actionId}`" class="my-application-card">
          <div class="my-application-card__copy">
            <strong>{{ item.flowName || '业务审批' }}</strong>
            <p>{{ statusName(item.status) }} · {{ statusName(item.businessStatus) }}</p>
            <small>当前节点：{{ item.currentNodeName || '暂无当前节点' }} · 发起 {{ productDateTime(item.startTime) }}</small>
            <small v-if="item.finishTime">结束 {{ productDateTime(item.finishTime) }}</small>
          </div>
          <a-button @click="openApplication(item)">查看审批进度</a-button>
        </article>
      </div>
      <template #pagination>
        <a-button v-if="nextCursor !== null" :loading="loading" @click="loadPage">继续加载</a-button>
        <span v-else>已加载全部当前可查看的申请</span>
      </template>
    </ProductListWorkspace>
    <BusinessActionApprovalDrawer :open="Boolean(selected)" :module-code="selected?.moduleCode || ''" :action-id="selected?.actionId" @close="closeProgress" />
  </div>
</template>

<style scoped>
.my-application-list { display: grid; gap: 12px; }
.my-application-card { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 16px; border-bottom: 1px solid var(--ux-color-border); }
.my-application-card__copy { min-width: 0; overflow-wrap: anywhere; }
.my-application-card__copy strong { font-size: 15px; }
.my-application-card__copy p { margin: 6px 0; color: var(--ux-color-text-secondary); }
.my-application-card__copy small { display: block; color: var(--ux-color-text-tertiary); }
@media (max-width: 640px) { .my-application-card { align-items: flex-start; flex-direction: column; } }
</style>
