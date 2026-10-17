<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Empty, message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { ApiError, api } from '../api'
import { allowsPermission } from '../permissions'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import { deliveryStatusLabel, messageChannelLabel, messageStatusLabel } from '../message'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import { productDateTime } from '../presentation'
import type { MessageInboxView, MessageItemView, MessageOpenResult, MessagePreferenceView } from '../types'

const props = withDefaults(defineProps<{ context?: 'platform' | 'system' }>(), { context: 'platform' })
const emit = defineEmits<{ openTarget: [target: MessageOpenResult] }>()
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const current = computed(() => props.context === 'platform' ? platformContext.value : systemContext.value)
const contextCode = computed(() => props.context === 'platform' ? 'PLATFORM' : 'SYSTEM')
const inbox = ref<MessageInboxView>({ messages: [], unreadCount: 0, total: 0, page: 1, pageSize: 10, hasPrevious: false, hasNext: false })
const status = ref('ACTIVE')
const sourceType = ref('ALL')
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const busy = ref(false)
const preferenceOpen = ref(false)
const preferenceBusy = ref(false)
const preferences = ref<MessagePreferenceView[]>([])
let active = true
let listGeneration = 0
const identity = computed(() => {
  const context = current.value
  return JSON.stringify([props.context, context?.platformId, context?.accountId, context?.systemId,
    context?.tenantId, context?.memberId, context?.tenantMemberId, context?.contextRevision,
    context?.roleIds, context?.permissions, context?.dataScopes])
})

function requestContext() {
  return { token: token.value, identity: identity.value }
}

function requestCurrent(context: ReturnType<typeof requestContext>) {
  return active && context.token === token.value && context.identity === identity.value
}

function can(action: string) {
  return allowsPermission(current.value?.permissions, 'MESSAGE', contextCode.value, action)
    || allowsPermission(current.value?.permissions, 'MESSAGE', '*', action)
}

function describeError(error: unknown) {
  if (error instanceof ApiError) return `${error.message}${error.traceId ? `（追踪号 ${error.traceId}）` : ''}`
  return error instanceof Error ? error.message : '操作失败'
}

function statusColor(value: string) {
  return ({ UNREAD: 'blue', READ: 'default', ARCHIVED: 'orange' } as Record<string, string>)[value] || 'default'
}

function sensitivityLabel(value: string) {
  return ({ NORMAL: '普通', IMPORTANT: '重要', SENSITIVE: '敏感' } as Record<string, string>)[value] || '普通'
}

function sourceLabel(value: string) {
  if (value.includes('FLOW')) return '流程'
  if (value.includes('TODO')) return '待办'
  if (value.includes('WORK')) return '任务'
  if (value.includes('EXPORT')) return '导出结果'
  if (value.includes('KPI')) return '指标提醒'
  if (value.includes('SYSTEM') || value.includes('EXCEPTION')) return '系统'
  return '业务通知'
}

async function load() {
  if (!token.value || !can('VIEW')) return
  const context = requestContext()
  const request = ++listGeneration
  loading.value = true
  try {
    const result = await api<MessageInboxView>(`/api/messages?status=${encodeURIComponent(status.value)}&sourceType=${encodeURIComponent(sourceType.value)}&page=${page.value}&pageSize=${pageSize}`, {}, context.token)
    if (requestCurrent(context) && request === listGeneration) inbox.value = result
  } catch (error) { if (requestCurrent(context) && request === listGeneration) message.error(describeError(error)) }
  finally { if (requestCurrent(context) && request === listGeneration) loading.value = false }
}

async function openPreferences() {
  if (!token.value) return
  const context = requestContext()
  preferenceBusy.value = true
  try {
    const result = await api<MessagePreferenceView[]>('/api/messages/preferences', {}, context.token)
    if (!requestCurrent(context)) return
    preferences.value = result
    preferenceOpen.value = true
  } catch (error) { if (requestCurrent(context)) message.error(describeError(error)) }
  finally { if (requestCurrent(context)) preferenceBusy.value = false }
}

async function savePreference(row: MessagePreferenceView) {
  if (!token.value) return
  if (Boolean(row.quietStart) !== Boolean(row.quietEnd)) return message.warning('免打扰开始和结束时间需要同时填写')
  const context = requestContext()
  preferenceBusy.value = true
  try {
    const saved = await api<MessagePreferenceView>('/api/messages/preferences', {
      method: 'POST', body: JSON.stringify({
        channel: row.channel, enabled: row.enabled,
        quietStart: row.quietStart || undefined, quietEnd: row.quietEnd || undefined,
        expectedVersion: row.version,
      }),
    }, context.token)
    if (!requestCurrent(context)) return
    preferences.value = preferences.value.map(item => item.channel === saved.channel ? saved : item)
    message.success(`${messageChannelLabel(saved.channel)}通知偏好已保存`)
  } catch (error) { if (requestCurrent(context)) { message.error(describeError(error)); await openPreferences() } }
  finally { if (requestCurrent(context)) preferenceBusy.value = false }
}

async function changePage(next: number) {
  if (next < 1 || loading.value) return
  page.value = next
  await load()
}

async function readAll() {
  if (!token.value) return
  const context = requestContext()
  busy.value = true
  try {
    await api('/api/messages/read-all', { method: 'POST' }, context.token)
    if (!requestCurrent(context)) return
    await load()
    if (requestCurrent(context)) message.success('当前工作范围内的消息已全部标记为已读')
  } catch (error) { if (requestCurrent(context)) message.error(describeError(error)) }
  finally { if (requestCurrent(context)) busy.value = false }
}

async function markRead(row: MessageItemView) {
  if (!token.value || row.recipientStatus !== 'UNREAD') return
  const context = requestContext()
  try {
    await api(`/api/messages/${row.id}/read`, { method: 'POST' }, context.token)
    if (requestCurrent(context)) await load()
  } catch (error) { if (requestCurrent(context)) message.error(describeError(error)) }
}

async function archive(row: MessageItemView) {
  if (!token.value) return
  const context = requestContext()
  try {
    await api(`/api/messages/${row.id}/archive`, { method: 'POST' }, context.token)
    if (!requestCurrent(context)) return
    await load()
    if (requestCurrent(context)) message.success('消息已归档')
  } catch (error) { if (requestCurrent(context)) message.error(describeError(error)) }
}

async function openMessage(row: MessageItemView) {
  if (!token.value || !row.targetType || !row.targetCurrentlyAccessible) return
  const context = requestContext()
  try {
    const result = await api<MessageOpenResult>(`/api/messages/${row.id}/open`, { method: 'POST' }, context.token)
    if (!requestCurrent(context)) return
    await load()
    if (requestCurrent(context)) emit('openTarget', result)
  } catch (error) { if (requestCurrent(context)) message.error(describeError(error)) }
}

watch([status, sourceType], () => { page.value = 1; void load() })
watch([identity, token], () => {
  listGeneration++
  inbox.value = { messages: [], unreadCount: 0, total: 0, page: 1, pageSize, hasPrevious: false, hasNext: false }
  page.value = 1
  preferences.value = []
  preferenceOpen.value = false
  loading.value = false
  busy.value = false
  preferenceBusy.value = false
  if (token.value) void load()
}, { flush: 'sync' })
onMounted(load)
onBeforeUnmount(() => { active = false; listGeneration++; inbox.value.messages = [] })
</script>

<template>
  <div class="message-workspace-page">
    <ProductPageHeader :kicker="context === 'platform' ? '全部工作' : '当前系统'" title="消息" description="与你有关的业务变化、处理结果和系统提醒。">
      <template #actions><a-badge :count="inbox.unreadCount"><a-button :loading="loading" @click="load"><ReloadOutlined />刷新</a-button></a-badge><a-button :loading="preferenceBusy" @click="openPreferences">通知偏好</a-button><a-button :loading="busy" @click="readAll">全部已读</a-button></template>
    </ProductPageHeader>
    <a-alert v-if="!can('VIEW')" type="warning" show-icon message="当前工作范围没有消息查看权限" description="平台消息和系统消息不会混合展示。" />
    <template v-else>
      <section class="panel-card message-filter-bar">
        <a-segmented v-model:value="status" :options="[{ value: 'ACTIVE', label: '收件箱' }, { value: 'UNREAD', label: '未读' }, { value: 'READ', label: '已读' }, { value: 'ARCHIVED', label: '归档' }]" />
        <a-select v-model:value="sourceType" style="width:160px" :options="[
          { value:'ALL', label:'全部来源' }, { value:'FLOW', label:'流程' }, { value:'WORK_TASK', label:'工作任务' },
          { value:'MODULE_EXPORT', label:'导出结果' }, { value:'KPI_ALERT', label:'指标提醒' },
          { value:'CRITICAL_EXCEPTION', label:'系统异常' },
        ]" />
        <span>未读 {{ inbox.unreadCount }} · 共 {{ inbox.total }} 条</span>
      </section>
      <a-spin :spinning="loading">
        <div v-if="inbox.messages.length" class="message-stream">
          <article v-for="row in inbox.messages" :key="row.id" :class="['panel-card', 'message-card', { openable: row.targetCurrentlyAccessible }]" @click="openMessage(row)">
            <span :class="['message-unread-dot', { visible: row.recipientStatus === 'UNREAD' }]" />
            <div class="message-card__content">
              <header><span><strong>{{ row.subject }}</strong><a-tag :color="statusColor(row.recipientStatus)">{{ messageStatusLabel(row.recipientStatus) }}</a-tag><a-tag v-if="row.sensitivity !== 'NORMAL'" color="red">{{ sensitivityLabel(row.sensitivity) }}</a-tag></span><time>{{ productDateTime(row.createdAt) }}</time></header>
              <p>{{ row.content }}</p>
              <small>{{ sourceLabel(row.sourceType) }} · 发起人 {{ row.actorName }}<template v-if="row.targetCurrentlyAccessible"> · 点击查看关联内容</template></small>
              <div v-if="row.deliveries.some(item => item.channel !== 'IN_APP')" class="message-delivery-summary">
                <a-tag v-for="delivery in row.deliveries.filter(item => item.channel !== 'IN_APP')" :key="delivery.channel" :color="delivery.status === 'FAILED' ? 'red' : delivery.status === 'DELIVERED' ? 'green' : 'orange'">
                  {{ messageChannelLabel(delivery.channel) }} · {{ deliveryStatusLabel(delivery.status) }}
                </a-tag>
                <small v-for="delivery in row.deliveries.filter(item => item.failureReason)" :key="`${delivery.channel}-reason`">{{ delivery.failureReason }}</small>
              </div>
              <a-alert v-if="row.targetType && !row.targetCurrentlyAccessible" type="warning" show-icon message="关联内容当前不可访问" description="权限或对象状态已经变化；消息正文不会代替业务对象泄露内容。" />
            </div>
            <div class="message-card__actions" @click.stop><a-button v-if="row.recipientStatus === 'UNREAD'" size="small" @click="markRead(row)">标为已读</a-button><a-button v-if="row.recipientStatus !== 'ARCHIVED'" size="small" @click="archive(row)">归档</a-button></div>
          </article>
        </div>
        <a-empty v-else-if="!loading" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="当前筛选没有消息" />
      </a-spin>
      <div v-if="inbox.total" class="message-pagination"><a-button :disabled="!inbox.hasPrevious" @click="changePage(page - 1)">上一页</a-button><span>第 {{ inbox.page }} 页 · 本页 {{ inbox.messages.length }} 条</span><a-button :disabled="!inbox.hasNext" @click="changePage(page + 1)">下一页</a-button></div>
    </template>
  </div>

  <a-drawer v-model:open="preferenceOpen" title="外部通知偏好" width="620">
    <a-alert type="info" show-icon message="站内消息始终保留" description="你可以关闭外部渠道，或设置每天的免打扰时间。设置只对当前平台或当前系统生效。" class="section-alert" />
    <div class="message-preference-list">
      <section v-for="row in preferences" :key="row.channel" class="panel-card message-preference-row">
        <header><strong>{{ messageChannelLabel(row.channel) }}</strong><a-switch v-model:checked="row.enabled" checked-children="开启" un-checked-children="关闭" /></header>
        <div class="form-grid two">
          <a-form-item label="免打扰开始"><a-input v-model:value="row.quietStart" type="time" allow-clear /></a-form-item>
          <a-form-item label="免打扰结束"><a-input v-model:value="row.quietEnd" type="time" allow-clear /></a-form-item>
        </div>
        <a-button type="primary" :loading="preferenceBusy" @click="savePreference(row)">保存当前渠道</a-button>
      </section>
    </div>
  </a-drawer>
</template>
