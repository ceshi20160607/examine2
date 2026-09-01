<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Empty, message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { ApiError, api } from '../api'
import { allowsPermission } from '../permissions'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import { deliveryStatusLabel, messageChannelLabel, messageStatusLabel, parseMessageVariables, parseTemplateVariables } from '../message'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import { productDateTime } from '../presentation'
import type { MessageInboxView, MessageItemView, MessageOpenResult, MessageTemplateView } from '../types'

const props = withDefaults(defineProps<{ context?: 'platform' | 'system' }>(), { context: 'platform' })
const emit = defineEmits<{ openTarget: [target: MessageOpenResult] }>()
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const current = computed(() => props.context === 'platform' ? platformContext.value : systemContext.value)
const contextCode = computed(() => props.context === 'platform' ? 'PLATFORM' : 'SYSTEM')
const inbox = ref<MessageInboxView>({ messages: [], unreadCount: 0 })
const templates = ref<MessageTemplateView[]>([])
const status = ref('ACTIVE')
const sourceType = ref('ALL')
const loading = ref(false)
const busy = ref(false)
const templateOpen = ref(false)
const selectedTemplate = ref<MessageTemplateView>()
const templateForm = reactive({
  code: '', name: '', channel: 'IN_APP', subjectTemplate: '', contentTemplate: '', requiredVariables: '',
})
const testForm = reactive({
  recipientIds: '', variables: '{}', targetType: undefined as string | undefined,
  targetId: '', targetRoute: '', sensitivity: 'NORMAL',
})

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

function deliveryColor(value: string) {
  return ({ DELIVERED: 'green', SENT: 'blue', RETRY_PENDING: 'orange', FAILED: 'red' } as Record<string, string>)[value] || 'default'
}

function sensitivityLabel(value: string) {
  return ({ NORMAL: '普通', IMPORTANT: '重要', SENSITIVE: '敏感' } as Record<string, string>)[value] || '普通'
}

function sourceLabel(value: string) {
  if (value.includes('FLOW')) return '流程通知'
  if (value.includes('TODO')) return '待办通知'
  if (value.includes('WORK')) return '任务通知'
  if (value.includes('SYSTEM')) return '系统通知'
  return '业务通知'
}

async function load() {
  if (!token.value || !can('VIEW')) return
  loading.value = true
  try {
    inbox.value = await api<MessageInboxView>(`/api/messages?status=${encodeURIComponent(status.value)}&sourceType=${encodeURIComponent(sourceType.value || 'ALL')}`, {}, token.value)
    if (can('MANAGE')) templates.value = await api<MessageTemplateView[]>('/api/message-templates', {}, token.value)
  } catch (error) {
    message.error(describeError(error))
  } finally { loading.value = false }
}

async function readAll() {
  if (!token.value) return
  busy.value = true
  try {
    await api('/api/messages/read-all', { method: 'POST' }, token.value)
    await load()
    message.success('当前工作范围内的消息已全部标记为已读')
  } catch (error) { message.error(describeError(error)) } finally { busy.value = false }
}

async function markRead(row: MessageItemView) {
  if (!token.value || row.recipientStatus !== 'UNREAD') return
  try {
    await api(`/api/messages/${row.id}/read`, { method: 'POST' }, token.value)
    await load()
  } catch (error) { message.error(describeError(error)) }
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
  if (!token.value || !row.targetType) return
  try {
    const result = await api<MessageOpenResult>(`/api/messages/${row.id}/open`, { method: 'POST' }, token.value)
    await load()
    emit('openTarget', result)
  } catch (error) { message.error(describeError(error)) }
}

function editTemplate(row?: MessageTemplateView) {
  selectedTemplate.value = row
  Object.assign(templateForm, row ? {
    code: row.code, name: row.name, channel: row.channel, subjectTemplate: row.subjectTemplate || '',
    contentTemplate: row.contentTemplate, requiredVariables: row.requiredVariables.join(', '),
  } : { code: '', name: '', channel: 'IN_APP', subjectTemplate: '', contentTemplate: '', requiredVariables: '' })
  Object.assign(testForm, { recipientIds: String(current.value?.accountId || ''), variables: '{}', targetType: undefined, targetId: '', targetRoute: '', sensitivity: 'NORMAL' })
  templateOpen.value = true
}

async function saveTemplate() {
  if (!token.value || !templateForm.code.trim() || !templateForm.name.trim() || !templateForm.contentTemplate.trim()) {
    return message.warning('请填写模板编码、名称和正文')
  }
  busy.value = true
  try {
    const saved = await api<MessageTemplateView>('/api/message-templates', {
      method: 'POST', body: JSON.stringify({
        id: selectedTemplate.value?.id,
        expectedVersion: selectedTemplate.value?.version,
        code: templateForm.code.trim().toUpperCase(), name: templateForm.name.trim(), channel: templateForm.channel,
        subjectTemplate: templateForm.subjectTemplate || undefined, contentTemplate: templateForm.contentTemplate,
        requiredVariables: parseTemplateVariables(templateForm.requiredVariables),
      }),
    }, token.value)
    selectedTemplate.value = saved
    await load()
    message.success('消息模板草稿已保存并读回')
  } catch (error) { message.error(describeError(error)) } finally { busy.value = false }
}

async function publishTemplate() {
  if (!token.value || !selectedTemplate.value) return message.warning('请先保存模板草稿')
  busy.value = true
  try {
    selectedTemplate.value = await api<MessageTemplateView>(`/api/message-templates/${selectedTemplate.value.id}/publish`, { method: 'POST' }, token.value)
    await load()
    message.success(`模板 V${selectedTemplate.value.publishedVersion} 已发布`)
  } catch (error) { message.error(describeError(error)) } finally { busy.value = false }
}

async function sendTest() {
  if (!token.value || !selectedTemplate.value?.publishedVersion) return message.warning('请先发布模板')
  let variables: Record<string, unknown>
  try { variables = parseMessageVariables(testForm.variables) }
  catch (error) { return message.warning(describeError(error)) }
  const recipientAccountIds = [...new Set(testForm.recipientIds.split(',').map(Number).filter((id) => id > 0))]
  if (!recipientAccountIds.length) return message.warning('请填写至少一个接收账号 ID')
  busy.value = true
  try {
    await api('/api/messages/events', { method: 'POST', body: JSON.stringify({
      templateCode: selectedTemplate.value.code, sourceType: 'MANUAL_TEST', dedupKey: `message-test-${Date.now()}`,
      recipientAccountIds, variables, targetType: testForm.targetType || undefined,
      targetId: testForm.targetId || undefined, targetRoute: testForm.targetRoute || undefined,
      sensitivity: testForm.sensitivity,
    }) }, token.value)
    templateOpen.value = false
    await load()
    message.success('测试消息已生成，站内记录与渠道回执已读回')
  } catch (error) { message.error(describeError(error)) } finally { busy.value = false }
}

watch([status, sourceType], () => void load())
onMounted(() => load())
</script>

<template>
  <div class="message-workspace-page">
    <ProductPageHeader :kicker="context === 'platform' ? '全部工作' : '当前系统'" title="消息" description="查看与你当前工作相关的业务通知和处理结果。">
      <template #actions><a-badge :count="inbox.unreadCount"><a-button :loading="loading" @click="load"><ReloadOutlined />刷新</a-button></a-badge><a-button :loading="busy" @click="readAll">全部已读</a-button><a-button v-if="can('MANAGE')" @click="editTemplate()">消息模板</a-button></template>
    </ProductPageHeader>
    <a-alert v-if="!can('VIEW')" type="warning" show-icon message="当前工作范围没有消息查看权限" description="平台消息和系统消息不会混合展示。" />
    <template v-else>
      <section class="panel-card message-filter-bar">
        <a-segmented v-model:value="status" :options="[{ value: 'ACTIVE', label: '收件箱' }, { value: 'UNREAD', label: '未读' }, { value: 'READ', label: '已读' }, { value: 'ARCHIVED', label: '归档' }]" />
        <span>未读 {{ inbox.unreadCount }} · 共 {{ inbox.messages.length }} 条</span>
      </section>
      <a-spin :spinning="loading">
        <div v-if="inbox.messages.length" class="message-stream">
          <article v-for="row in inbox.messages" :key="row.id" :class="['panel-card', 'message-card', { openable: row.targetType }]" @click="openMessage(row)">
            <span :class="['message-unread-dot', { visible: row.recipientStatus === 'UNREAD' }]" />
            <div class="message-card__content">
              <header><span><strong>{{ row.subject }}</strong><a-tag :color="statusColor(row.recipientStatus)">{{ messageStatusLabel(row.recipientStatus) }}</a-tag><a-tag v-if="row.sensitivity !== 'NORMAL'" color="red">{{ sensitivityLabel(row.sensitivity) }}</a-tag></span><time>{{ productDateTime(row.createdAt) }}</time></header>
              <p>{{ row.content }}</p>
              <small>{{ sourceLabel(row.sourceType) }}<template v-if="row.targetType"> · 可打开关联内容</template></small>
              <div class="message-deliveries">
                <span v-for="delivery in row.deliveries" :key="delivery.id"><a-tag :color="deliveryColor(delivery.status)">{{ messageChannelLabel(delivery.channel) }} · {{ deliveryStatusLabel(delivery.status) }}</a-tag><small>{{ delivery.destinationMasked }}</small></span>
              </div>
              <a-alert v-if="row.targetType && !row.targetCurrentlyAccessible" type="warning" show-icon message="关联目标当前不可访问" description="目标权限会在点击时再次校验，消息不会泄露目标业务内容。" />
            </div>
            <div class="message-card__actions" @click.stop><a-button v-if="row.recipientStatus === 'UNREAD'" size="small" @click="markRead(row)">标为已读</a-button><a-button v-if="row.recipientStatus !== 'ARCHIVED'" size="small" @click="archive(row)">归档</a-button></div>
          </article>
        </div>
        <a-empty v-else-if="!loading" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="当前筛选没有消息" />
      </a-spin>
    </template>
  </div>

  <a-drawer v-model:open="templateOpen" width="620" title="消息模板与发送验证">
    <a-form layout="vertical">
      <div class="form-grid three"><a-form-item label="模板编码" required><a-input v-model:value="templateForm.code" :disabled="!!selectedTemplate" /></a-form-item><a-form-item label="名称" required><a-input v-model:value="templateForm.name" /></a-form-item><a-form-item label="外部渠道"><a-select v-model:value="templateForm.channel" :disabled="!!selectedTemplate" :options="[{ value:'IN_APP', label:'仅站内' },{ value:'EMAIL', label:'邮件 + 站内' },{ value:'SMS', label:'短信 + 站内' },{ value:'WEBHOOK', label:'Webhook + 站内' }]" /></a-form-item></div>
      <a-form-item label="标题模板"><a-input v-model:value="templateForm.subjectTemplate" placeholder="例如：流程 {{title}} 已更新" /></a-form-item>
      <a-form-item label="正文模板" required><a-textarea v-model:value="templateForm.contentTemplate" :rows="4" /></a-form-item>
      <a-form-item label="必填变量"><a-input v-model:value="templateForm.requiredVariables" placeholder="title, actor" /></a-form-item>
      <div class="message-template-actions"><a-button :loading="busy" type="primary" @click="saveTemplate">保存草稿</a-button><a-button :disabled="!selectedTemplate" :loading="busy" @click="publishTemplate">发布不可变版本</a-button></div>
      <a-divider>已发布模板测试发送</a-divider>
      <a-select v-if="templates.length" :value="selectedTemplate?.id" style="width:100%;margin-bottom:14px" placeholder="选择已有模板" :options="templates.map((item) => ({ value:item.id, label:`${item.code} · ${item.name} · ${item.status}` }))" @change="(id: number) => editTemplate(templates.find((item) => item.id === id))" />
      <a-form-item label="接收账号编号" required><a-input v-model:value="testForm.recipientIds" placeholder="多个账号编号用逗号分隔" /></a-form-item>
      <a-form-item label="模板变量 JSON" required><a-textarea v-model:value="testForm.variables" :rows="3" /></a-form-item>
      <div class="form-grid three"><a-form-item label="关联内容类型"><a-select v-model:value="testForm.targetType" allow-clear :options="[{value:'PLATFORM_ROUTE',label:'平台页面'},{value:'SYSTEM_ROUTE',label:'系统页面'},{value:'FLOW_INSTANCE',label:'流程实例'},{value:'RUNTIME_RECORD',label:'业务记录'},{value:'WORK_TASK',label:'任务'},{value:'APPLICATION',label:'应用'},{value:'FILE_OBJECT',label:'文件'}]" /></a-form-item><a-form-item label="关联内容编号"><a-input v-model:value="testForm.targetId" /></a-form-item><a-form-item label="信息级别"><a-select v-model:value="testForm.sensitivity" :options="[{value:'NORMAL',label:'普通'},{value:'IMPORTANT',label:'重要'},{value:'SENSITIVE',label:'敏感'}]" /></a-form-item></div>
      <a-form-item label="安全目标路由"><a-input v-model:value="testForm.targetRoute" placeholder="路由仅作引用，打开时仍实时复核目标权限" /></a-form-item>
      <a-button block :loading="busy" :disabled="!selectedTemplate?.publishedVersion" @click="sendTest">发送测试并读回回执</a-button>
    </a-form>
  </a-drawer>
</template>
