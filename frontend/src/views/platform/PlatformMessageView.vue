<script setup lang="ts">
import { Archive, CheckCheck, Mail, MailOpen, RefreshCw } from 'lucide-vue-next'
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiRequestError } from '@/services/api'
import { platformMessageApi } from '@/services/platformMessage'
import type { PlatformMessage, PlatformMessageFilter, PlatformMessageType } from '@/types/platformMessage'

const router = useRouter()
const items = ref<PlatformMessage[]>([])
const total = ref(0)
const unread = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const mutation = ref('')
const error = ref('')
const filters = reactive({ status: 'ALL' as PlatformMessageFilter, type: 'ALL' as 'ALL' | PlatformMessageType, templateCode: '', keyword: '', from: '', to: '' })

function message(cause: unknown) {
  if (cause instanceof ApiRequestError) return cause.message || cause.code
  return cause instanceof Error ? cause.message : '平台消息请求失败'
}
async function load() {
  loading.value = true; error.value = ''
  try {
    const [result, count] = await Promise.all([platformMessageApi.list({ ...filters, page: page.value, size }), platformMessageApi.unreadCount()])
    items.value = result.items; total.value = result.total; unread.value = count.unreadCount
  } catch (cause) { error.value = message(cause) } finally { loading.value = false }
}
async function markRead(item: PlatformMessage) {
  mutation.value = `read:${item.id}`
  try { await platformMessageApi.read(item.id); await load() } catch (cause) { error.value = message(cause) } finally { mutation.value = '' }
}
async function readAll() {
  mutation.value = 'read-all'
  try { await platformMessageApi.readAll(); await load() } catch (cause) { error.value = message(cause) } finally { mutation.value = '' }
}
async function archive(item: PlatformMessage) {
  mutation.value = `archive:${item.id}`
  try { await platformMessageApi.archive(item.id); await load() } catch (cause) { error.value = message(cause) } finally { mutation.value = '' }
}
async function open(item: PlatformMessage) {
  if (!item.target) return
  if (!item.target.path.startsWith('/platform/') || item.target.path.startsWith('/systems/')) {
    error.value = '平台消息目标不安全，已阻止跳转。'
    return
  }
  mutation.value = `open:${item.id}`
  try {
    if (item.status === 'UNREAD') await platformMessageApi.read(item.id)
    await router.push(item.target.path)
  } catch (cause) { error.value = message(cause) } finally { mutation.value = '' }
}
function applyFilters() { page.value = 1; void load() }
function time(value: string) { const parsed = new Date(value); return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleString('zh-CN') }
onMounted(load)
</script>

<template>
  <section class="workspace platform-message-page">
    <header class="workspace-heading"><div><h1><Mail :size="23" />平台消息 <a-badge :count="unread" /></h1><p>仅处理平台授权、任务、日志、系统切换和平台 Agent 结果。</p></div><div><a-button :loading="loading" @click="load"><RefreshCw :size="16" />刷新</a-button><a-button class="platform-message-read-all" :disabled="!unread || Boolean(mutation)" :loading="mutation === 'read-all'" @click="readAll"><CheckCheck :size="16" />全部已读</a-button></div></header>
    <a-alert type="info" show-icon message="安全跳转边界" description="平台消息只允许进入平台对象。涉及业务系统时先进入系统切换引导，不直接打开系统业务详情。" />
    <a-alert v-if="error" type="error" show-icon :message="error" />
    <form class="platform-message-filters" @submit.prevent="applyFilters"><select v-model="filters.status"><option value="ALL">全部状态</option><option value="UNREAD">未读</option><option value="READ">已读</option><option value="ARCHIVED">已归档</option></select><select v-model="filters.type"><option value="ALL">全部类型</option><option value="AUTHORIZATION">平台授权</option><option value="TASK">平台任务</option><option value="LOG">平台日志</option><option value="SYSTEM_SWITCH">系统切换</option><option value="AGENT">平台 Agent</option></select><input v-model="filters.templateCode" placeholder="模板编码" /><input v-model="filters.keyword" placeholder="搜索标题和正文" /><input v-model="filters.from" type="date" aria-label="开始日期" /><input v-model="filters.to" type="date" aria-label="结束日期" /><button type="submit">筛选</button></form>
    <a-spin :spinning="loading"><a-empty v-if="!items.length && !loading" description="当前没有平台消息" /><div v-else class="platform-message-list"><article v-for="item in items" :key="item.id" :class="[{ unread: item.status === 'UNREAD' }, { actionable: item.target }]" :role="item.target ? 'link' : undefined" :tabindex="item.target ? 0 : undefined" @click="open(item)" @keydown.enter="open(item)"><Mail v-if="item.status === 'UNREAD'" :size="19" /><MailOpen v-else :size="19" /><div><header><strong>{{ item.title }}</strong><a-tag>{{ item.type }}</a-tag><a-tag v-if="item.status === 'ARCHIVED'">已归档</a-tag><a-tag v-else-if="item.status === 'UNREAD'" color="blue">未读</a-tag></header><p>{{ item.body }}</p><small>{{ item.templateCode }} · {{ time(item.createdAt) }}<template v-if="item.target"> · {{ item.target.type }} #{{ item.target.id }}</template></small></div><footer v-if="item.status !== 'ARCHIVED'" @click.stop @keydown.stop><a-button v-if="item.status === 'UNREAD'" size="small" @click="markRead(item)">标为已读</a-button><a-button size="small" :loading="mutation === `archive:${item.id}`" @click="archive(item)"><Archive :size="14" />归档</a-button></footer></article></div></a-spin>
    <a-pagination v-if="total > size" :current="page" :page-size="size" :total="total" :show-size-changer="false" @change="page = $event; load()" />
  </section>
</template>

<style scoped>
.platform-message-page{display:grid;gap:16px}.workspace-heading h1,.workspace-heading>div:last-child{display:flex;align-items:center;gap:8px}.platform-message-filters{display:grid;grid-template-columns:repeat(2,minmax(120px,160px)) repeat(2,minmax(140px,1fr)) repeat(2,150px) auto;gap:8px}.platform-message-filters input,.platform-message-filters select,.platform-message-filters button{padding:8px;border:1px solid #cbd5d9;border-radius:5px;background:#fff}.platform-message-filters button{background:#0f766e;color:#fff}.platform-message-list{display:grid;gap:9px}.platform-message-list>article{display:grid;grid-template-columns:24px minmax(0,1fr) auto;align-items:start;gap:12px;padding:15px;border:1px solid #dce4e7;border-radius:8px;background:#fff}.platform-message-list>article.unread{border-color:#93c5fd;background:#f8fbff}.platform-message-list>article.actionable{cursor:pointer}.platform-message-list>article.actionable:hover{border-color:#0f766e}.platform-message-list header,.platform-message-list footer{display:flex;align-items:center;gap:8px}.platform-message-list p{margin:7px 0;color:#52616b;white-space:pre-wrap}.platform-message-list small{color:#74818a}.platform-message-list footer{align-self:center}.platform-message-page>.ant-pagination{justify-self:end}@media(max-width:1000px){.platform-message-filters{grid-template-columns:repeat(2,minmax(0,1fr))}.platform-message-list>article{grid-template-columns:24px minmax(0,1fr)}.platform-message-list footer{grid-column:2;justify-content:flex-start}}@media(max-width:560px){.platform-message-filters{grid-template-columns:minmax(0,1fr)}.workspace-heading{align-items:flex-start;flex-direction:column}.platform-message-list>article{padding:12px}.platform-message-list header{align-items:flex-start;flex-wrap:wrap}}
</style>
