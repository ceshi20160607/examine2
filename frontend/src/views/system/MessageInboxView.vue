<script setup lang="ts">
import { Archive, BellRing, CheckCheck, Mail, MailOpen, RefreshCw } from 'lucide-vue-next'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiRequestError } from '@/services/api'
import { eventApi } from '@/services/event'
import { useSessionStore } from '@/stores/session'
import type { DeliveryPreference, InboxMessage, InboxMessageFilter } from '@/types/event'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const systemId = computed(() => String(route.params.systemId))
const tenantId = computed(() => session.context?.tenantId ?? '')
const memberId = computed(() => session.context?.memberId ?? '')
const messages = ref<InboxMessage[]>([])
const unread = ref(0)
const status = ref<InboxMessageFilter>('ALL')
const page = ref(1)
const pageSize = 20
const total = ref(0)
const loading = ref(false)
const mutation = ref('')
const error = ref('')
let loadVersion = 0
const preferenceOpen = ref(false)
const preferences = ref<DeliveryPreference[]>([])
const preferenceLoading = ref(false)
const preferenceLoadError = ref('')
const preferenceBusy = ref<Record<string, boolean>>({})
const preferenceSaveErrors = ref<Record<string, string>>({})
let preferenceContextGeneration = 0
let preferenceLoadVersion = 0

const statusOptions: Array<{ label: string, value: InboxMessageFilter }> = [
  { label: '全部', value: 'ALL' },
  { label: '未读', value: 'UNREAD' },
  { label: '已读', value: 'READ' },
  { label: '已归档', value: 'ARCHIVED' },
]

async function loadInbox() {
  const version = ++loadVersion
  loading.value = true
  error.value = ''
  try {
    let [result, count] = await Promise.all([
      eventApi.list(systemId.value, {
        status: status.value,
        page: page.value,
        size: pageSize,
      }),
      eventApi.unreadCount(systemId.value),
    ])
    if (version !== loadVersion) return

    const lastPage = Math.max(1, Math.ceil(result.total / pageSize))
    if (page.value > lastPage) {
      page.value = lastPage
      result = await eventApi.list(systemId.value, {
        status: status.value,
        page: page.value,
        size: pageSize,
      })
      if (version !== loadVersion) return
    }

    messages.value = result.items
    total.value = result.total
    unread.value = count.unreadCount
  } catch (cause) {
    if (version !== loadVersion) return
    error.value = message(cause)
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

function changeStatus(value: string | number) {
  status.value = value as InboxMessageFilter
  page.value = 1
  void loadInbox()
}

function changePage(value: number) {
  page.value = value
  void loadInbox()
}

function preferenceContext() {
  return `${systemId.value}:${tenantId.value}:${memberId.value}`
}

function preferenceKey(item: DeliveryPreference) {
  return `${item.templateCode}:${item.channel}`
}

function clearPreferences() {
  preferenceContextGeneration += 1
  preferenceLoadVersion += 1
  preferenceOpen.value = false
  preferences.value = []
  preferenceLoading.value = false
  preferenceLoadError.value = ''
  preferenceBusy.value = {}
  preferenceSaveErrors.value = {}
}

function openPreferences() {
  preferenceOpen.value = true
  void loadPreferences()
}

async function loadPreferences(preserveSaveErrors = false) {
  const context = preferenceContext()
  const contextGeneration = preferenceContextGeneration
  const version = ++preferenceLoadVersion
  preferenceLoading.value = true
  preferenceLoadError.value = ''
  if (!preserveSaveErrors) preferenceSaveErrors.value = {}
  try {
    const result = await eventApi.deliveryPreferences(systemId.value)
    if (contextGeneration !== preferenceContextGeneration
      || version !== preferenceLoadVersion
      || context !== preferenceContext()) return
    preferences.value = result
  } catch (cause) {
    if (contextGeneration !== preferenceContextGeneration
      || version !== preferenceLoadVersion
      || context !== preferenceContext()) return
    preferenceLoadError.value = preferenceMessage(cause, '投递偏好加载失败')
  } finally {
    if (contextGeneration === preferenceContextGeneration
      && version === preferenceLoadVersion
      && context === preferenceContext()) {
      preferenceLoading.value = false
    }
  }
}

async function updatePreference(item: DeliveryPreference, enabled: boolean) {
  const key = preferenceKey(item)
  if (enabled === item.enabled || preferenceBusy.value[key]) return
  const context = preferenceContext()
  const contextGeneration = preferenceContextGeneration
  preferenceBusy.value = { ...preferenceBusy.value, [key]: true }
  const nextErrors = { ...preferenceSaveErrors.value }
  delete nextErrors[key]
  preferenceSaveErrors.value = nextErrors
  try {
    const updated = await eventApi.updateDeliveryPreference(systemId.value, item.templateCode, item.channel, {
      enabled,
      expectedVersion: item.version,
    })
    if (contextGeneration !== preferenceContextGeneration || context !== preferenceContext()) return
    preferences.value = preferences.value.map((current) =>
      current.templateCode === updated.templateCode && current.channel === updated.channel ? updated : current)
  } catch (cause) {
    if (contextGeneration !== preferenceContextGeneration || context !== preferenceContext()) return
    preferenceSaveErrors.value = {
      ...preferenceSaveErrors.value,
      [key]: preferenceMessage(cause, '投递偏好保存失败'),
    }
    if (cause instanceof ApiRequestError && cause.status === 409) {
      await loadPreferences(true)
    }
  } finally {
    if (contextGeneration === preferenceContextGeneration && context === preferenceContext()) {
      const nextBusy = { ...preferenceBusy.value }
      delete nextBusy[key]
      preferenceBusy.value = nextBusy
    }
  }
}

async function markRead(item: InboxMessage) {
  mutation.value = `read:${item.id}`
  error.value = ''
  try {
    await eventApi.read(systemId.value, item.id)
    await loadInbox()
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function readAll() {
  mutation.value = 'read-all'
  error.value = ''
  try {
    await eventApi.readAll(systemId.value)
    await loadInbox()
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function archive(item: InboxMessage) {
  mutation.value = `archive:${item.id}`
  error.value = ''
  try {
    await eventApi.archive(systemId.value, item.id)
    await loadInbox()
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function openMessage(item: InboxMessage) {
  if (!item.targetPath) return
  mutation.value = `open:${item.id}`
  error.value = ''
  try {
    if (item.status === 'UNREAD') await eventApi.read(systemId.value, item.id)
    await router.push(item.targetPath)
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

function message(cause: unknown) {
  if (cause instanceof ApiRequestError) return cause.message || cause.code
  return cause instanceof Error ? cause.message : '消息请求失败，请稍后重试'
}

function preferenceMessage(cause: unknown, fallback: string) {
  if (cause instanceof ApiRequestError) {
    return cause.code === cause.message ? cause.code : `${cause.code}：${cause.message}`
  }
  return cause instanceof Error ? cause.message : fallback
}

function time(value: string) {
  const parsed = new Date(value)
  return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleString('zh-CN')
}

onMounted(loadInbox)
watch([systemId, tenantId, memberId], () => {
  status.value = 'ALL'
  page.value = 1
  messages.value = []
  total.value = 0
  unread.value = 0
  clearPreferences()
  void loadInbox()
})
</script>

<template>
  <section class="inbox-page">
    <header class="inbox-heading">
      <div>
        <h1><Mail :size="24" /> 消息中心 <a-badge :count="unread" /></h1>
        <p>查看当前租户发送给你的业务消息。</p>
      </div>
      <div class="inbox-actions">
        <a-button class="delivery-preferences-open" @click="openPreferences"><BellRing :size="16" />投递偏好</a-button>
        <a-button :loading="loading" @click="loadInbox"><RefreshCw :size="16" />刷新</a-button>
        <a-button :disabled="unread === 0 || Boolean(mutation)" :loading="mutation === 'read-all'" @click="readAll">
          <CheckCheck :size="16" />全部已读
        </a-button>
      </div>
    </header>

    <a-alert v-if="error" type="error" show-icon closable :message="error" @close="error = ''" />

    <div class="inbox-filter-bar">
      <a-segmented :value="status" :options="statusOptions" @change="changeStatus" />
      <span>共 {{ total }} 条</span>
    </div>

    <a-spin :spinning="loading">
      <a-empty v-if="!messages.length && !loading" description="当前没有消息" />
      <div v-else class="message-list">
        <article
          v-for="item in messages"
          :key="item.id"
          class="message-card"
          :class="[{ unread: item.status === 'UNREAD' }, { actionable: Boolean(item.targetPath) }]"
          :role="item.targetPath ? 'link' : undefined"
          :tabindex="item.targetPath ? 0 : undefined"
          :aria-label="item.targetPath ? `打开消息：${item.title}` : undefined"
          @click="openMessage(item)"
          @keydown.enter="openMessage(item)"
        >
          <div class="message-icon">
            <Mail v-if="item.status === 'UNREAD'" :size="20" />
            <MailOpen v-else :size="20" />
          </div>
          <div class="message-copy">
            <div class="message-title">
              <strong>{{ item.title }}</strong>
              <a-tag v-if="item.status === 'ARCHIVED'">已归档</a-tag>
              <a-tag v-else-if="item.status === 'UNREAD'" color="blue">未读</a-tag>
            </div>
            <p>{{ item.body }}</p>
            <div class="message-meta">
              <span>{{ time(item.createdAt) }}</span>
              <span v-if="item.target">{{ item.target.type }} · {{ item.target.id }}</span>
            </div>
          </div>
          <div v-if="item.status !== 'ARCHIVED'" class="message-actions" @click.stop @keydown.stop>
            <a-button
              v-if="item.status === 'UNREAD'"
              :loading="mutation === `read:${item.id}`"
              :disabled="Boolean(mutation) && mutation !== `read:${item.id}`"
              @click="markRead(item)"
            >
              标为已读
            </a-button>
            <a-button
              :loading="mutation === `archive:${item.id}`"
              :disabled="Boolean(mutation) && mutation !== `archive:${item.id}`"
              @click="archive(item)"
            >
              <Archive :size="15" />归档
            </a-button>
          </div>
        </article>
      </div>
    </a-spin>

    <a-pagination
      v-if="total > pageSize"
      :current="page"
      :page-size="pageSize"
      :total="total"
      :show-size-changer="false"
      @change="changePage"
    />

    <a-drawer v-model:open="preferenceOpen" title="投递偏好" :width="520">
      <section class="delivery-preference-panel">
        <a-alert
          type="info"
          show-icon
          message="按模板和渠道管理今后的投递"
          description="每个开关仅影响对应模板与渠道的未来投递；直接成员消息、其他渠道和已有消息不受影响。"
        />
        <a-alert
          v-if="preferenceLoadError"
          class="delivery-preference-load-error"
          type="error"
          show-icon
          :message="preferenceLoadError"
        >
          <template #action><a-button class="delivery-preference-retry" size="small" @click="loadPreferences()">重试</a-button></template>
        </a-alert>
        <div v-if="preferenceLoading" class="delivery-preference-loading"><a-spin />正在加载投递偏好…</div>
        <a-empty
          v-else-if="!preferences.length && !preferenceLoadError"
          class="delivery-preference-empty"
          description="暂无可配置的投递类型"
        />
        <div v-else-if="preferences.length" class="delivery-preference-list">
          <article v-for="item in preferences" :key="preferenceKey(item)" class="delivery-preference-row">
            <div class="delivery-preference-copy">
              <strong>{{ item.name }}</strong>
              <span>{{ item.templateCode }}</span>
              <small>{{ item.eventType }}</small>
            </div>
            <a-tag>{{ item.channel }}</a-tag>
            <a-switch
              class="delivery-preference-toggle"
              :checked="item.enabled"
              :loading="Boolean(preferenceBusy[preferenceKey(item)])"
              :disabled="Boolean(preferenceBusy[preferenceKey(item)])"
              checked-children="启用"
              un-checked-children="停用"
              @change="updatePreference(item, Boolean($event))"
            />
            <p v-if="preferenceSaveErrors[preferenceKey(item)]" class="delivery-preference-save-error">
              {{ preferenceSaveErrors[preferenceKey(item)] }}
            </p>
          </article>
        </div>
      </section>
    </a-drawer>
  </section>
</template>

<style scoped>
.inbox-page {
  display: grid;
  gap: 20px;
  max-width: 1120px;
  margin: 0 auto;
  padding: 28px;
  min-width: 0;
  background: #f4f6f8;
}

.inbox-heading,
.inbox-heading h1,
.inbox-actions,
.inbox-filter-bar,
.message-card,
.message-title,
.message-meta,
.message-actions {
  display: flex;
  align-items: center;
}

.message-card.actionable{cursor:pointer}.message-card.actionable:hover{border-color:#0f766e;background:#f7fffd}

.inbox-heading {
  justify-content: space-between;
  gap: 20px;
}

.inbox-heading h1 {
  gap: 10px;
  margin: 0;
}

.inbox-heading p,
.message-copy p {
  margin: 6px 0 0;
  color: #64748b;
}

.inbox-actions,
.message-actions {
  gap: 10px;
  flex-wrap: wrap;
}

.inbox-filter-bar {
  justify-content: space-between;
  gap: 16px;
  color: #64748b;
}

.inbox-page > .ant-pagination {
  justify-self: end;
}

.message-list {
  display: grid;
  gap: 10px;
}

.message-card {
  gap: 16px;
  padding: 18px 20px;
  min-width: 0;
  border: 1px solid #dce3e8;
  border-radius: 12px;
  background: #fff;
}

.message-card.unread {
  border-color: #93c5fd;
  background: #f8fbff;
}

.message-icon {
  color: #2563eb;
}

.message-copy {
  min-width: 0;
  flex: 1;
}

.message-title strong,
.message-copy p {
  overflow-wrap: anywhere;
}

.message-card.actionable:focus-visible {
  outline: 3px solid rgb(15 118 110 / 24%);
  outline-offset: 2px;
}

.message-title,
.message-meta {
  gap: 10px;
}

.message-meta {
  flex-wrap: wrap;
  margin-top: 10px;
  color: #64748b;
  font-size: 13px;
}

.delivery-preference-panel,
.delivery-preference-list {
  display: grid;
  gap: 12px;
}

.delivery-preference-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 120px;
  color: #64748b;
}

.delivery-preference-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
}

.delivery-preference-copy {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.delivery-preference-copy span,
.delivery-preference-copy small {
  overflow-wrap: anywhere;
  color: #64748b;
}

.delivery-preference-save-error {
  grid-column: 1 / -1;
  margin: 0;
  color: #b42318;
  font-size: 13px;
}

@media (max-width: 820px) {
  .inbox-page { gap: 16px; padding: 20px; }
  .inbox-heading { align-items: flex-start; }
  .inbox-actions { justify-content: flex-end; }
  .message-card { align-items: flex-start; }
  .message-actions { justify-content: flex-end; }
}

@media (max-width: 560px) {
  .inbox-page { gap: 14px; padding: 14px; }
  .inbox-heading { align-items: stretch; flex-direction: column; }
  .inbox-actions { justify-content: flex-start; }
  .inbox-filter-bar { align-items: flex-start; flex-direction: column; }
  .message-card { display: grid; grid-template-columns:28px minmax(0,1fr); gap: 12px; padding: 14px; }
  .message-actions { grid-column: 1 / -1; justify-content: flex-start; padding-left: 40px; }
  .delivery-preference-row { grid-template-columns: minmax(0,1fr) auto; }
  .delivery-preference-toggle { grid-column: 1 / -1; justify-self: start; }
  .inbox-page > .ant-pagination { max-width: 100%; justify-self: center; }
}
</style>
