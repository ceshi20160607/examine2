<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Empty, message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { ApiError, api } from '../api'
import { allowsPermission } from '../permissions'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import { messageStatusLabel } from '../message'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import { productDateTime } from '../presentation'
import type { MessageInboxView, MessageItemView, MessageOpenResult } from '../types'

const props = withDefaults(defineProps<{ context?: 'platform' | 'system' }>(), { context: 'platform' })
const emit = defineEmits<{ openTarget: [target: MessageOpenResult] }>()
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const current = computed(() => props.context === 'platform' ? platformContext.value : systemContext.value)
const contextCode = computed(() => props.context === 'platform' ? 'PLATFORM' : 'SYSTEM')
const inbox = ref<MessageInboxView>({ messages: [], unreadCount: 0 })
const status = ref('ACTIVE')
const loading = ref(false)
const busy = ref(false)

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
  loading.value = true
  try {
    inbox.value = await api<MessageInboxView>(`/api/messages?status=${encodeURIComponent(status.value)}&sourceType=ALL`, {}, token.value)
  } catch (error) { message.error(describeError(error)) }
  finally { loading.value = false }
}

async function readAll() {
  if (!token.value) return
  busy.value = true
  try {
    await api('/api/messages/read-all', { method: 'POST' }, token.value)
    await load()
    message.success('当前工作范围内的消息已全部标记为已读')
  } catch (error) { message.error(describeError(error)) }
  finally { busy.value = false }
}

async function markRead(row: MessageItemView) {
  if (!token.value || row.recipientStatus !== 'UNREAD') return
  try { await api(`/api/messages/${row.id}/read`, { method: 'POST' }, token.value); await load() }
  catch (error) { message.error(describeError(error)) }
}

async function archive(row: MessageItemView) {
  if (!token.value) return
  try {
    await api(`/api/messages/${row.id}/archive`, { method: 'POST' }, token.value)
    await load()
    message.success('消息已归档')
  } catch (error) { message.error(describeError(error)) }
}

async function openMessage(row: MessageItemView) {
  if (!token.value || !row.targetType || !row.targetCurrentlyAccessible) return
  try {
    const result = await api<MessageOpenResult>(`/api/messages/${row.id}/open`, { method: 'POST' }, token.value)
    await load()
    emit('openTarget', result)
  } catch (error) { message.error(describeError(error)) }
}

watch(status, () => void load())
onMounted(load)
</script>

<template>
  <div class="message-workspace-page">
    <ProductPageHeader :kicker="context === 'platform' ? '全部工作' : '当前系统'" title="消息" description="与你有关的业务变化、处理结果和系统提醒。">
      <template #actions><a-badge :count="inbox.unreadCount"><a-button :loading="loading" @click="load"><ReloadOutlined />刷新</a-button></a-badge><a-button :loading="busy" @click="readAll">全部已读</a-button></template>
    </ProductPageHeader>
    <a-alert v-if="!can('VIEW')" type="warning" show-icon message="当前工作范围没有消息查看权限" description="平台消息和系统消息不会混合展示。" />
    <template v-else>
      <section class="panel-card message-filter-bar">
        <a-segmented v-model:value="status" :options="[{ value: 'ACTIVE', label: '收件箱' }, { value: 'UNREAD', label: '未读' }, { value: 'READ', label: '已读' }, { value: 'ARCHIVED', label: '归档' }]" />
        <span>未读 {{ inbox.unreadCount }} · 共 {{ inbox.messages.length }} 条</span>
      </section>
      <a-spin :spinning="loading">
        <div v-if="inbox.messages.length" class="message-stream">
          <article v-for="row in inbox.messages" :key="row.id" :class="['panel-card', 'message-card', { openable: row.targetCurrentlyAccessible }]" @click="openMessage(row)">
            <span :class="['message-unread-dot', { visible: row.recipientStatus === 'UNREAD' }]" />
            <div class="message-card__content">
              <header><span><strong>{{ row.subject }}</strong><a-tag :color="statusColor(row.recipientStatus)">{{ messageStatusLabel(row.recipientStatus) }}</a-tag><a-tag v-if="row.sensitivity !== 'NORMAL'" color="red">{{ sensitivityLabel(row.sensitivity) }}</a-tag></span><time>{{ productDateTime(row.createdAt) }}</time></header>
              <p>{{ row.content }}</p>
              <small>{{ sourceLabel(row.sourceType) }} · 发起人 {{ row.actorName }}<template v-if="row.targetCurrentlyAccessible"> · 点击查看关联内容</template></small>
              <a-alert v-if="row.targetType && !row.targetCurrentlyAccessible" type="warning" show-icon message="关联内容当前不可访问" description="权限或对象状态已经变化；消息正文不会代替业务对象泄露内容。" />
            </div>
            <div class="message-card__actions" @click.stop><a-button v-if="row.recipientStatus === 'UNREAD'" size="small" @click="markRead(row)">标为已读</a-button><a-button v-if="row.recipientStatus !== 'ARCHIVED'" size="small" @click="archive(row)">归档</a-button></div>
          </article>
        </div>
        <a-empty v-else-if="!loading" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="当前筛选没有消息" />
      </a-spin>
    </template>
  </div>
</template>
