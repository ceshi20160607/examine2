<script setup lang="ts">
import { CopyOutlined, LinkOutlined, PlusOutlined, SafetyCertificateOutlined, SendOutlined, UserAddOutlined } from '@ant-design/icons-vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Empty, message } from 'ant-design-vue'
import { ApiError, api } from '../api'
import { productDateTime } from '../presentation'
import type {
  ApplicationGrantView,
  ApplicationProviderCapability,
  ApplicationProviderConnection,
  ApplicationProviderDiagnostic,
  ApplicationProviderDelivery,
  ApplicationProviderEvent,
  ApplicationProviderIdentity,
  ApplicationProviderIdentityOption,
  ApplicationResourceOption,
  MessageTemplateView,
} from '../types'

const props = defineProps<{
  applicationId: number
  token: string
  publishedGrants: ApplicationGrantView[]
  resources: ApplicationResourceOption[]
  canManage: boolean
  applicationActive: boolean
}>()

const loading = ref(false)
const busy = ref('')
const connections = ref<ApplicationProviderConnection[]>([])
const capabilities = ref<ApplicationProviderCapability[]>([])
const events = ref<Record<number, ApplicationProviderEvent[]>>({})
const identities = ref<Record<number, ApplicationProviderIdentity[]>>({})
const identityOptions = ref<Record<number, ApplicationProviderIdentityOption[]>>({})
const deliveries = ref<Record<number, ApplicationProviderDelivery[]>>({})
const messageTemplates = ref<MessageTemplateView[]>([])
const formOpen = ref(false)
const editing = ref<ApplicationProviderConnection>()
const identityOpen = ref(false)
const identityConnection = ref<ApplicationProviderConnection>()
const editingIdentity = ref<ApplicationProviderIdentity>()
const identityForm = reactive({ externalUserId: '', targetKey: '' })
const messageOpen = ref(false)
const messageConnection = ref<ApplicationProviderConnection>()
const messageForm = reactive({ identityId: 0, templateCode: '', variables: {} as Record<string, string> })
const form = reactive({
  providerCode: 'WECOM' as 'WECOM' | 'FEISHU' | 'DINGTALK',
  displayName: '',
  tenantKey: '',
  applicationKey: '',
  apiSecret: '',
  callbackToken: '',
  encodingAesKey: '',
  eventCode: 'MESSAGE_TEXT',
  routeGrantId: 0,
})

const eventOptions = [
  { value: 'MESSAGE_TEXT', label: '成员发送文本消息' },
  { value: 'EVENT_CLICK', label: '成员点击应用菜单' },
  { value: 'EVENT_VIEW', label: '成员点击菜单链接' },
  { value: 'EVENT_CHANGE_CONTACT', label: '通讯录变更事件' },
  { value: 'EVENT_USER_ADD_ORG', label: '成员加入企业事件' },
]
const formEventOptions = computed(() => {
  if (form.providerCode === 'FEISHU') return eventOptions.slice(0, 1)
  if (form.providerCode === 'DINGTALK') return eventOptions.filter(item => item.value === 'EVENT_USER_ADD_ORG')
  return eventOptions.slice(0, 4)
})

const routableGrants = computed(() => props.publishedGrants.filter(grant =>
  grant.resourceType === 'FLOW' && grant.actionCode === 'START'
  && Array.isArray(grant.rateLimit.accessChannels)
  && grant.rateLimit.accessChannels.map(String).includes('EXTERNAL')))

const grantOptions = computed(() => routableGrants.value.map(grant => ({
  value: grant.id,
  label: `发起流程 · ${resourceName(grant.resourceType, grant.resourceId)}`,
})))

const providerTemplates = computed(() => messageTemplates.value.filter(item =>
  item.channel === messageConnection.value?.providerCode && item.status === 'PUBLISHED'))
const selectedMessageTemplate = computed(() => providerTemplates.value.find(item => item.code === messageForm.templateCode))

function describeError(error: unknown) {
  if (error instanceof ApiError) return `${error.message}${error.traceId ? `（追踪号 ${error.traceId}）` : ''}`
  return error instanceof Error ? error.message : '操作失败'
}

function resourceName(type: string, id: string) {
  return props.resources.find(item => item.resourceType === type && item.resourceId === id)?.name || id
}

function statusLabel(value: string) {
  return ({ DRAFT: '待诊断', ACTIVE: '已启用', DISABLED: '已停用' } as Record<string, string>)[value] || value
}

function diagnosticLabel(value: string) {
  return ({ NOT_RUN: '未诊断', LOCAL_PASSED: '本地协议通过', FAILED: '诊断失败', EXTERNAL_PASSED: '外部联通通过' } as Record<string, string>)[value] || value
}

function capabilityLabel(value: string) {
  return ({
    LOCAL_PROTOCOL_READY: '本地协议已实现',
    PLANNED: '后续接入',
    PRODUCT_SELECTION_REQUIRED: '需确认产品形态',
  } as Record<string, string>)[value] || value
}

function eventStatusLabel(value: string) {
  return ({ RECEIVED: '已接收', PROCESSED: '已执行', IGNORED: '无路由', FAILED: '处理失败' } as Record<string, string>)[value] || value
}

function secretTypeLabel(value: string) {
  return ({ API_SECRET: '应用 Secret', CALLBACK_TOKEN: '回调校验令牌', ENCODING_AES_KEY: '回调加密密钥' } as Record<string, string>)[value] || value
}

function providerName(value?: string) {
  return ({ WECOM: '企业微信', FEISHU: '飞书', DINGTALK: '钉钉' } as Record<string, string>)[value || ''] || '外部平台'
}

function productName(connection: ApplicationProviderConnection) {
  if (connection.providerCode === 'FEISHU') return '企业自建应用'
  if (connection.providerCode === 'DINGTALK') return '企业内部应用'
  return '内部自建应用'
}

function identityName(connection?: ApplicationProviderConnection) {
  if (connection?.providerCode === 'FEISHU') return 'Open ID / User ID'
  if (connection?.providerCode === 'DINGTALK') return 'UserId / StaffId'
  return 'UserID'
}

function providerMark(connection: ApplicationProviderConnection) {
  return ({ WECOM: '企微', FEISHU: '飞书', DINGTALK: '钉钉' } as Record<string, string>)[connection.providerCode] || '平台'
}

function applicationIdentity(connection: ApplicationProviderConnection) {
  if (connection.providerCode === 'FEISHU') return `App ID ${connection.tenantKey}`
  if (connection.providerCode === 'DINGTALK') return `Client ID ${connection.applicationKey} · CorpID ${connection.tenantKey}`
  return `AgentID ${connection.applicationKey}`
}

async function load() {
  loading.value = true
  try {
    const [connectionRows, capabilityRows, templateRows] = await Promise.all([
      api<ApplicationProviderConnection[]>(`/api/applications/${props.applicationId}/provider-connections`, {}, props.token),
      api<ApplicationProviderCapability[]>('/api/applications/provider-capabilities', {}, props.token),
      api<MessageTemplateView[]>('/api/message-templates', {}, props.token).catch(() => []),
    ])
    connections.value = connectionRows
    capabilities.value = capabilityRows
    messageTemplates.value = templateRows
    await Promise.all(connectionRows.map(connection => loadConnectionDetails(connection)))
  } catch (error) {
    message.error(describeError(error))
  } finally { loading.value = false }
}

async function loadConnectionDetails(connection: ApplicationProviderConnection) {
  const [identityRows, deliveryRows] = await Promise.all([
    api<ApplicationProviderIdentity[]>(`/api/applications/${props.applicationId}/provider-connections/${connection.id}/identities`, {}, props.token),
    api<ApplicationProviderDelivery[]>(`/api/applications/${props.applicationId}/provider-connections/${connection.id}/deliveries`, {}, props.token),
  ])
  identities.value[connection.id] = identityRows
  deliveries.value[connection.id] = deliveryRows
}

function resetForm(connection?: ApplicationProviderConnection) {
  editing.value = connection
  const providerCode = connection?.providerCode === 'FEISHU'
    ? 'FEISHU' : connection?.providerCode === 'DINGTALK' ? 'DINGTALK' : 'WECOM'
  const routeGrant = connection?.route
    ? routableGrants.value.find(grant => grant.resourceType === connection.route?.resourceType
      && grant.resourceId === connection.route?.resourceId
      && grant.actionCode === connection.route?.actionCode
      && grant.targetSystemId === connection.route?.targetSystemId
      && grant.targetTenantId === connection.route?.targetTenantId)
    : undefined
  Object.assign(form, {
    providerCode,
    displayName: connection?.displayName || `${providerName(providerCode)}业务接入`,
    tenantKey: connection?.tenantKey || '',
    applicationKey: connection?.applicationKey || '',
    apiSecret: '',
    callbackToken: '',
    encodingAesKey: '',
    eventCode: connection?.route?.eventCode || 'MESSAGE_TEXT',
    routeGrantId: routeGrant?.id || routableGrants.value[0]?.id || 0,
  })
}

function openCreate() {
  resetForm()
  formOpen.value = true
}

function openEdit(connection: ApplicationProviderConnection) {
  resetForm(connection)
  formOpen.value = true
}

function randomBytes(size: number) {
  const data = new Uint8Array(size)
  crypto.getRandomValues(data)
  return data
}

function generateToken() {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
  form.callbackToken = Array.from(randomBytes(24), value => alphabet[value % alphabet.length]).join('')
}

function generateEncodingKey() {
  form.encodingAesKey = btoa(String.fromCharCode(...randomBytes(32))).replace(/=$/, '')
}

async function save() {
  if (!form.displayName.trim() || !form.tenantKey.trim()
    || (form.providerCode !== 'FEISHU' && !form.applicationKey.trim())) {
    return message.warning(form.providerCode === 'FEISHU' ? '请填写连接名称和 App ID'
      : form.providerCode === 'DINGTALK' ? '请填写连接名称、CorpID 和 Client ID'
        : '请填写连接名称、CorpID 和 AgentID')
  }
  if (!editing.value && (!form.callbackToken
    || (form.providerCode !== 'FEISHU' && !form.encodingAesKey)
    || (form.providerCode !== 'WECOM' && !form.apiSecret))) {
    return message.warning(form.providerCode === 'FEISHU'
      ? '新飞书连接需要填写 App Secret 和 Verification Token'
      : form.providerCode === 'DINGTALK'
        ? '新钉钉连接需要填写 Client Secret、回调 Token 与 AES Key'
        : '新企业微信连接需要填写回调 Token 与 EncodingAESKey')
  }
  const grant = routableGrants.value.find(item => item.id === form.routeGrantId)
  busy.value = 'save'
  try {
    await api<ApplicationProviderConnection>(`/api/applications/${props.applicationId}/provider-connections`, {
      method: 'POST',
      body: JSON.stringify({
        id: editing.value?.id,
        expectedVersion: editing.value?.version,
        providerCode: form.providerCode,
        productCode: form.providerCode === 'FEISHU' ? 'CUSTOM_APP' : 'INTERNAL_APP',
        displayName: form.displayName.trim(),
        tenantKey: form.tenantKey.trim(),
        applicationKey: form.providerCode === 'FEISHU' ? undefined : form.applicationKey.trim(),
        apiSecret: form.apiSecret || undefined,
        callbackToken: form.callbackToken || undefined,
        encodingAesKey: form.encodingAesKey || undefined,
        route: grant ? {
          eventCode: form.eventCode,
          resourceType: grant.resourceType,
          resourceId: grant.resourceId,
          actionCode: grant.actionCode,
          targetSystemId: grant.targetSystemId,
          targetTenantId: grant.targetTenantId,
        } : undefined,
      }),
    }, props.token)
    formOpen.value = false
    Object.assign(form, { apiSecret: '', callbackToken: '', encodingAesKey: '' })
    await load()
    message.success(`${providerName(form.providerCode)}连接已保存，请执行本地诊断后启用`)
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

async function diagnose(connection: ApplicationProviderConnection) {
  busy.value = `diagnose-${connection.id}`
  try {
    const result = await api<ApplicationProviderDiagnostic>(
      `/api/applications/${props.applicationId}/provider-connections/${connection.id}/diagnose`, {
        method: 'POST', body: JSON.stringify({ activateAfterPass: true }),
      }, props.token)
    await load()
    message.success(result.externallyVerified ? '外部联通验证通过'
      : `本地协议通过并已启用；真实${providerName(connection.providerCode)}联通仍待外部凭据验证`)
  } catch (error) {
    await load().catch(() => undefined)
    message.error(describeError(error))
  } finally { busy.value = '' }
}

async function disable(connection: ApplicationProviderConnection) {
  busy.value = `disable-${connection.id}`
  try {
    await api(`/api/applications/${props.applicationId}/provider-connections/${connection.id}/disable`, {
      method: 'POST',
    }, props.token)
    await load()
    message.success('连接已停用，新回调会立即拒绝')
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

async function loadEvents(connection: ApplicationProviderConnection) {
  busy.value = `events-${connection.id}`
  try {
    events.value[connection.id] = await api<ApplicationProviderEvent[]>(
      `/api/applications/${props.applicationId}/provider-connections/${connection.id}/events`, {}, props.token)
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

function identityRows(connectionId: number) {
  return identities.value[connectionId] || []
}

function deliveryRows(connectionId: number) {
  return deliveries.value[connectionId] || []
}

function identityTargetKey(item: ApplicationProviderIdentity | ApplicationProviderIdentityOption) {
  return `${item.targetType}:${item.targetMemberId}`
}

async function openIdentity(connection: ApplicationProviderConnection, identity?: ApplicationProviderIdentity) {
  busy.value = `identity-options-${connection.id}`
  try {
    identityOptions.value[connection.id] = await api<ApplicationProviderIdentityOption[]>(
      `/api/applications/${props.applicationId}/provider-connections/${connection.id}/identity-options`, {}, props.token)
    identityConnection.value = connection
    editingIdentity.value = identity
    identityForm.externalUserId = identity?.externalUserId || ''
    identityForm.targetKey = identity ? identityTargetKey(identity) : ''
    identityOpen.value = true
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

async function saveIdentity() {
  const connection = identityConnection.value
  const option = connection && identityOptions.value[connection.id]?.find(item => identityTargetKey(item) === identityForm.targetKey)
  if (!connection || !identityForm.externalUserId.trim() || !option) {
    return message.warning(`请填写${providerName(connection?.providerCode)} ${identityName(connection)} 并选择系统成员`)
  }
  busy.value = 'identity-save'
  try {
    await api(`/api/applications/${props.applicationId}/provider-connections/${connection.id}/identities`, {
      method: 'POST',
      body: JSON.stringify({
        id: editingIdentity.value?.id,
        expectedVersion: editingIdentity.value?.version,
        externalUserId: identityForm.externalUserId.trim(),
        targetType: option.targetType,
        targetMemberId: option.targetMemberId,
      }),
    }, props.token)
    identityOpen.value = false
    await loadConnectionDetails(connection)
    message.success('成员映射已保存；后续回调将使用该成员的当前权限')
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

async function disableIdentity(connection: ApplicationProviderConnection, identity: ApplicationProviderIdentity) {
  busy.value = `identity-disable-${identity.id}`
  try {
    await api(`/api/applications/${props.applicationId}/provider-connections/${connection.id}/identities/${identity.id}/disable`, { method: 'POST' }, props.token)
    await loadConnectionDetails(connection)
    message.success('成员映射已停用，新回调将立即拒绝')
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

function openMessage(connection: ApplicationProviderConnection) {
  const active = identityRows(connection.id).filter(item => item.status === 'ACTIVE')
  const [firstIdentity] = active
  messageConnection.value = connection
  const [firstTemplate] = providerTemplates.value
  if (!firstIdentity) return message.warning(`请先配置并启用${providerName(connection.providerCode)}成员映射`)
  if (!firstTemplate) return message.warning(`请先在消息模板中发布“站内 + ${providerName(connection.providerCode)}应用消息”模板`)
  messageForm.identityId = firstIdentity.id
  messageForm.templateCode = firstTemplate.code
  resetMessageVariables()
  messageOpen.value = true
}

function resetMessageVariables() {
  const next: Record<string, string> = {}
  selectedMessageTemplate.value?.requiredVariables.forEach(key => { next[key] = messageForm.variables[key] || '' })
  messageForm.variables = next
}

async function sendMessage() {
  const connection = messageConnection.value
  if (!connection || !messageForm.identityId || !messageForm.templateCode) return
  busy.value = 'message-send'
  try {
    const result = await api<ApplicationProviderDelivery>(
      `/api/applications/${props.applicationId}/provider-connections/${connection.id}/messages`, {
        method: 'POST',
        body: JSON.stringify({
          identityId: messageForm.identityId,
          templateCode: messageForm.templateCode,
          idempotencyKey: crypto.randomUUID(),
          variables: messageForm.variables,
        }),
      }, props.token)
    messageOpen.value = false
    await loadConnectionDetails(connection)
    message.info(result.status === 'BLOCKED_EXTERNAL'
      ? `消息和业务入口已生成；真实${providerName(connection.providerCode)}租户联通尚未验证，外部投递已明确阻塞`
      : `${providerName(connection.providerCode)}消息已提交`)
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

function eventRows(connectionId: number) {
  return events.value[connectionId] || []
}

function deliveryStatusLabel(value: string, connection: ApplicationProviderConnection) {
  return ({ BLOCKED_EXTERNAL: `待真实${providerName(connection.providerCode)}联通`, SENT: '已发送', DELIVERED: '已送达', FAILED: '发送失败', CANCELED: '已撤销', PENDING: '待发送' } as Record<string, string>)[value] || value
}

async function copyCallback(connection: ApplicationProviderConnection) {
  await navigator.clipboard.writeText(`${window.location.origin}${connection.callbackPath}`)
  message.success('回调地址已复制')
}

watch(() => props.applicationId, () => load())
watch(() => form.providerCode, provider => {
  if (!editing.value) {
    form.displayName = `${providerName(provider)}业务接入`
    form.applicationKey = ''
    form.eventCode = provider === 'DINGTALK' ? 'EVENT_USER_ADD_ORG' : 'MESSAGE_TEXT'
  }
})
onMounted(() => load())
</script>

<template>
  <section class="panel-card application-section provider-section">
    <div class="panel-title">
      <div><strong>外部平台连接</strong><small>连接附着在当前应用授权上，不建立第二套接口中心</small></div>
      <a-button v-if="canManage" type="primary" size="small" :disabled="!applicationActive" @click="openCreate"><PlusOutlined />新增外部平台连接</a-button>
    </div>

    <div class="provider-capabilities">
      <a v-for="item in capabilities" :key="`${item.providerCode}-${item.productCode}`" :href="item.officialDocumentation" target="_blank" rel="noreferrer" class="provider-capability">
        <span><strong>{{ item.providerName }} · {{ item.productName }}</strong><a-tag :color="item.implementationStatus === 'LOCAL_PROTOCOL_READY' ? 'green' : item.implementationStatus === 'PRODUCT_SELECTION_REQUIRED' ? 'orange' : 'default'">{{ capabilityLabel(item.implementationStatus) }}</a-tag></span>
        <small>{{ item.callbackBoundary }}</small>
      </a>
    </div>
    <a-alert type="info" show-icon message="微信不是一种通用自建应用" description="公众号和小程序使用不同产品协议；企业微信、飞书、钉钉也各自验签和授权。页面只开放已经实现并验证的产品形态。" />

    <a-spin :spinning="loading">
      <div v-if="connections.length" class="provider-connections">
        <article v-for="connection in connections" :key="connection.id" class="provider-connection-card">
          <div class="provider-connection-card__heading">
            <span><span class="provider-mark" :class="{ 'provider-mark--feishu': connection.providerCode === 'FEISHU', 'provider-mark--dingtalk': connection.providerCode === 'DINGTALK' }">{{ providerMark(connection) }}</span><span><strong>{{ connection.displayName }}</strong><small>{{ providerName(connection.providerCode) }}{{ productName(connection) }} · {{ connection.tenantKey }}</small></span></span>
            <span><a-tag :color="connection.status === 'ACTIVE' ? 'green' : connection.status === 'DISABLED' ? 'red' : 'orange'">{{ statusLabel(connection.status) }}</a-tag><a-tag :color="connection.diagnosticStatus === 'LOCAL_PASSED' || connection.diagnosticStatus === 'EXTERNAL_PASSED' ? 'blue' : connection.diagnosticStatus === 'FAILED' ? 'red' : 'default'">{{ diagnosticLabel(connection.diagnosticStatus) }}</a-tag></span>
          </div>
          <a-alert v-if="connection.diagnosticSummary" :type="connection.diagnosticStatus === 'FAILED' ? 'error' : 'info'" show-icon :message="connection.diagnosticSummary" :description="connection.diagnosticStatus === 'LOCAL_PASSED' ? `当前只证明本地官方协议算法、密钥引用和应用授权有效；尚未证明真实${providerName(connection.providerCode)}租户已配置回调。` : undefined" />
          <div class="provider-connection-grid">
            <div><small>应用标识</small><strong>{{ applicationIdentity(connection) }}</strong></div>
            <div><small>事件路由</small><strong v-if="connection.route">{{ eventOptions.find(item => item.value === connection.route?.eventCode)?.label || connection.route.eventCode }} → {{ resourceName(connection.route.resourceType, connection.route.resourceId) }}</strong><strong v-else>未配置，合法事件安全忽略</strong></div>
            <div><small>回调地址</small><span class="provider-copy"><code>{{ connection.callbackPath }}</code><a-button size="small" @click="copyCallback(connection)"><CopyOutlined /></a-button></span></div>
            <div><small>最近诊断</small><strong>{{ connection.diagnosticTime ? productDateTime(connection.diagnosticTime) : '尚未执行' }}</strong></div>
          </div>
          <div class="provider-secrets">
            <span v-for="secret in connection.secrets.filter(item => item.status === 'ACTIVE')" :key="secret.referencePath"><SafetyCertificateOutlined /><strong>{{ secretTypeLabel(secret.secretType) }} V{{ secret.secretVersion }}</strong><small>{{ secret.referencePath }} · 尾号 {{ secret.secretHint }}</small></span>
          </div>
          <section class="provider-subsection">
            <div class="provider-subsection__title"><span><strong>{{ providerName(connection.providerCode) }}成员映射</strong><small>{{ identityName(connection) }} 对应真实成员，回调始终读取成员当前权限</small></span><a-button v-if="canManage && connection.status !== 'DISABLED'" size="small" :loading="busy === `identity-options-${connection.id}`" @click="openIdentity(connection)"><UserAddOutlined />添加映射</a-button></div>
            <div v-if="identityRows(connection.id).length" class="provider-identity-list">
              <div v-for="identity in identityRows(connection.id)" :key="identity.id"><span><strong>{{ identity.memberName }}</strong><small>{{ identity.contextName }}{{ identity.employeeNumber ? ` · ${identity.employeeNumber}` : '' }}</small></span><span><code>{{ identity.externalUserId }}</code><a-tag :color="identity.status === 'ACTIVE' ? 'green' : 'default'">{{ identity.status === 'ACTIVE' ? '启用' : '停用' }}</a-tag><a-button v-if="canManage && identity.status === 'ACTIVE'" type="link" size="small" @click="openIdentity(connection, identity)">编辑</a-button><a-popconfirm v-if="canManage && identity.status === 'ACTIVE'" :title="`停用后该 ${identityName(connection)} 的新回调会在业务写入前被拒绝。`" @confirm="disableIdentity(connection, identity)"><a-button type="link" danger size="small" :loading="busy === `identity-disable-${identity.id}`">停用</a-button></a-popconfirm></span></div>
            </div>
            <a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="尚未映射成员，连接不能通过启用诊断" />
          </section>
          <div class="provider-actions">
            <a-button v-if="canManage && connection.status !== 'DISABLED'" size="small" @click="openEdit(connection)">编辑并轮换密钥</a-button>
            <a-button v-if="canManage && connection.status !== 'DISABLED'" size="small" type="primary" :loading="busy === `diagnose-${connection.id}`" @click="diagnose(connection)"><LinkOutlined />本地诊断并启用</a-button>
            <a-popconfirm v-if="canManage && connection.status !== 'DISABLED'" title="停用后新回调立即拒绝，历史事件和密钥版本仍保留。" @confirm="disable(connection)"><a-button size="small" danger :loading="busy === `disable-${connection.id}`">停用</a-button></a-popconfirm>
            <a-button v-if="canManage && connection.status === 'ACTIVE'" size="small" @click="openMessage(connection)"><SendOutlined />发送结构化消息</a-button>
            <a-button size="small" :loading="busy === `events-${connection.id}`" @click="loadEvents(connection)">查看回调记录</a-button>
          </div>
          <div v-if="events[connection.id]" class="provider-events">
            <div v-for="event in eventRows(connection.id)" :key="event.id"><span><strong>{{ eventOptions.find(item => item.value === event.eventCode)?.label || event.eventCode }}</strong><small>{{ productDateTime(event.receivedTime) }} · 事件 {{ event.providerEventId }}</small></span><span><a-tag :color="event.status === 'PROCESSED' ? 'green' : event.status === 'FAILED' ? 'red' : 'default'">{{ eventStatusLabel(event.status) }}</a-tag><small v-if="event.applicationCallId">应用调用 {{ event.applicationCallId }}</small></span></div>
            <a-empty v-if="!eventRows(connection.id).length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无回调记录" />
          </div>
          <section v-if="deliveryRows(connection.id).length" class="provider-subsection">
            <div class="provider-subsection__title"><span><strong>消息投递记录</strong><small>统一消息中心保留站内消息，外部状态按真实结果记录</small></span></div>
            <div class="provider-deliveries"><div v-for="delivery in deliveryRows(connection.id)" :key="delivery.id"><span><strong>{{ delivery.memberName }}</strong><small>{{ productDateTime(delivery.createTime) }} · {{ delivery.deepLink }}</small></span><span><a-tag :color="delivery.status === 'DELIVERED' ? 'green' : delivery.status === 'BLOCKED_EXTERNAL' ? 'orange' : 'default'">{{ deliveryStatusLabel(delivery.status, connection) }}</a-tag><small v-if="delivery.errorCode">{{ delivery.errorCode }}</small></span></div></div>
          </section>
        </article>
      </div>
      <a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="当前应用尚未配置外部平台连接" />
    </a-spin>
  </section>

  <a-modal v-model:open="formOpen" :title="`${editing ? '编辑' : '新增'}${providerName(form.providerCode)}连接`" width="760px" :confirm-loading="busy === 'save'" @ok="save">
    <a-form layout="vertical">
      <a-alert type="info" show-icon :message="`当前实现：${providerName(form.providerCode)}${form.providerCode === 'FEISHU' ? '企业自建应用' : form.providerCode === 'DINGTALK' ? '企业内部应用' : '内部自建应用'}`" description="所有配置使用命名字段；密钥只写入加密版本，详情和日志不会返回明文。" style="margin-bottom:16px" />
      <div class="form-grid"><a-form-item label="平台" required><a-select v-model:value="form.providerCode" :disabled="!!editing" :options="[{ value:'WECOM', label:'企业微信 · 内部自建应用' },{ value:'FEISHU', label:'飞书 · 企业自建应用' },{ value:'DINGTALK', label:'钉钉 · 企业内部应用' }]" /></a-form-item><a-form-item label="连接名称" required><a-input v-model:value="form.displayName" :placeholder="`例如：${providerName(form.providerCode)}审批入口`" /></a-form-item></div>
      <div class="form-grid"><a-form-item :label="form.providerCode === 'FEISHU' ? 'App ID' : '企业 CorpID'" required><a-input v-model:value="form.tenantKey" :placeholder="form.providerCode === 'FEISHU' ? '飞书开放平台中的 App ID' : form.providerCode === 'DINGTALK' ? '钉钉企业 CorpID' : '企业微信管理后台中的企业 ID'" /></a-form-item><a-form-item v-if="form.providerCode !== 'FEISHU'" :label="form.providerCode === 'DINGTALK' ? 'Client ID（AppKey）' : '应用 AgentID'" required><a-input v-model:value="form.applicationKey" :placeholder="form.providerCode === 'DINGTALK' ? '钉钉应用 Client ID' : '自建应用的数字 AgentID'" /></a-form-item><a-form-item label="接收事件" required><a-select v-model:value="form.eventCode" :options="formEventOptions" /></a-form-item></div>
      <a-form-item label="事件进入的已发布应用授权"><a-select v-model:value="form.routeGrantId" allow-clear :options="grantOptions" placeholder="不选择时合法事件会被记录并安全忽略" /><small>当前首个切片只允许路由到已发布、允许外部访问的 Flow 发起授权。</small></a-form-item>
      <a-form-item :label="form.providerCode === 'DINGTALK' ? 'Client Secret' : `App Secret${form.providerCode === 'WECOM' ? '（可选）' : ''}`" :required="form.providerCode !== 'WECOM'"><a-input-password v-model:value="form.apiSecret" :placeholder="editing ? '留空保留当前版本；填写后生成新版本' : `填写${providerName(form.providerCode)}应用 Secret`" autocomplete="new-password" /></a-form-item>
      <div class="form-grid">
        <a-form-item :label="form.providerCode === 'FEISHU' ? 'Verification Token' : '回调 Token'" required><a-input-password v-model:value="form.callbackToken" :placeholder="editing ? '留空保留当前版本' : '填写平台事件订阅中的校验令牌'" autocomplete="new-password"><template #addonAfter><a-button type="link" size="small" @click="generateToken">生成</a-button></template></a-input-password></a-form-item>
        <a-form-item :label="form.providerCode === 'FEISHU' ? 'Encrypt Key（可选）' : form.providerCode === 'DINGTALK' ? '回调 AES Key' : 'EncodingAESKey'" :required="form.providerCode !== 'FEISHU'"><a-input-password v-model:value="form.encodingAesKey" :placeholder="editing ? '留空保留当前版本' : form.providerCode === 'FEISHU' ? '填写后启用签名和事件加密' : form.providerCode === 'DINGTALK' ? '43 位钉钉回调 AES Key' : '43 位企业微信回调加密密钥'" autocomplete="new-password"><template #addonAfter><a-button type="link" size="small" @click="generateEncodingKey">生成</a-button></template></a-input-password></a-form-item>
      </div>
    </a-form>
  </a-modal>

  <a-modal v-model:open="identityOpen" :title="editingIdentity ? '编辑成员映射' : '添加成员映射'" width="620px" :confirm-loading="busy === 'identity-save'" @ok="saveIdentity">
    <a-form layout="vertical">
      <a-alert type="info" show-icon message="回调身份按成员当前状态和权限实时解析" :description="`填写${providerName(identityConnection?.providerCode)}中的 ${identityName(identityConnection)}，并从当前应用范围选择成员。页面不接受内部主键或 JSON。`" style="margin-bottom:16px" />
      <a-form-item :label="`${providerName(identityConnection?.providerCode)} ${identityName(identityConnection)}`" required><a-input v-model:value="identityForm.externalUserId" :placeholder="identityConnection?.providerCode === 'FEISHU' ? '例如 ou_xxxxxxxxx' : identityConnection?.providerCode === 'DINGTALK' ? '例如 manager1234' : '例如 zhangsan'" /></a-form-item>
      <a-form-item label="对应系统成员" required><a-select v-model:value="identityForm.targetKey" show-search option-filter-prop="label" :options="(identityConnection ? identityOptions[identityConnection.id] || [] : []).map(item => ({ value: identityTargetKey(item), label: `${item.memberName}${item.employeeNumber ? ` · ${item.employeeNumber}` : ''} · ${item.contextName}` }))" placeholder="按姓名选择有效成员" /></a-form-item>
    </a-form>
  </a-modal>

  <a-modal v-model:open="messageOpen" :title="`发送${providerName(messageConnection?.providerCode)}应用消息`" width="680px" :confirm-loading="busy === 'message-send'" @ok="sendMessage">
    <a-form layout="vertical">
      <a-alert type="info" show-icon message="消息正文来自已发布模板，业务入口来自当前应用授权" description="系统自动生成站内深链和幂等记录；当前无真实租户凭据时会保留站内消息并显示外部阻塞，不会伪报发送成功。" style="margin-bottom:16px" />
      <a-form-item label="接收成员" required><a-select v-model:value="messageForm.identityId" :options="(messageConnection ? identityRows(messageConnection.id) : []).filter(item => item.status === 'ACTIVE').map(item => ({ value: item.id, label: `${item.memberName} · ${item.externalUserId}` }))" /></a-form-item>
      <a-form-item :label="`已发布${providerName(messageConnection?.providerCode)}消息模板`" required><a-select v-model:value="messageForm.templateCode" :options="providerTemplates.map(item => ({ value: item.code, label: `${item.name} · ${item.code}` }))" @change="resetMessageVariables" /></a-form-item>
      <div v-if="selectedMessageTemplate?.requiredVariables.length" class="form-grid"><a-form-item v-for="key in selectedMessageTemplate.requiredVariables" :key="key" :label="key" required><a-input v-model:value="messageForm.variables[key]" :placeholder="`填写 ${key}`" /></a-form-item></div>
      <a-alert v-else type="success" show-icon message="此模板不需要补充变量" />
    </a-form>
  </a-modal>
</template>

<style scoped>
.provider-section{display:grid;gap:16px}.panel-title>div{display:grid;gap:3px}.panel-title small{color:#64748b;font-weight:400}.provider-capabilities{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:10px}.provider-capability{display:grid;min-width:0;gap:8px;padding:12px;border:1px solid #e2e8f0;border-radius:12px;color:inherit;background:#f8fafc}.provider-capability:hover{border-color:#94a3b8}.provider-capability>span{display:flex;flex-wrap:wrap;justify-content:space-between;gap:8px}.provider-capability small{color:#64748b;line-height:1.5}.provider-connections{display:grid;min-width:0;gap:12px}.provider-connection-card{display:grid;min-width:0;gap:12px;padding:16px;border:1px solid #e2e8f0;border-radius:14px}.provider-connection-card__heading,.provider-connection-card__heading>span,.provider-actions,.provider-copy,.provider-subsection__title{display:flex;align-items:center;gap:8px}.provider-connection-card__heading,.provider-subsection__title{justify-content:space-between}.provider-connection-card__heading>span:first-child>span:last-child,.provider-subsection__title>span{display:grid}.provider-connection-card__heading small,.provider-connection-grid small,.provider-secrets small,.provider-events small,.provider-subsection small{color:#64748b}.provider-mark{display:grid;place-items:center;width:42px;height:42px;border-radius:12px;background:#07c160;color:#fff;font-weight:700}.provider-mark--feishu{background:#3370ff}.provider-mark--dingtalk{background:#1677ff}.provider-connection-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.provider-connection-grid>div{display:grid;min-width:0;gap:4px}.provider-connection-grid strong{overflow-wrap:anywhere}.provider-copy{width:100%;min-width:0}.provider-copy code{min-width:0;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.provider-secrets{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:8px}.provider-secrets>span{display:grid;min-width:0;grid-template-columns:auto minmax(0,1fr);gap:2px 7px;padding:10px;background:#f8fafc;border-radius:10px}.provider-secrets>span small{grid-column:2;overflow-wrap:anywhere}.provider-subsection{display:grid;gap:9px;padding:12px;border:1px solid #e2e8f0;border-radius:12px;background:#fbfdff}.provider-identity-list,.provider-deliveries{display:grid}.provider-identity-list>div,.provider-deliveries>div{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:9px 0;border-top:1px solid #eef2f7}.provider-identity-list>div>span,.provider-deliveries>div>span{display:flex;min-width:0;align-items:center;flex-wrap:wrap;gap:6px}.provider-identity-list>div>span:first-child,.provider-deliveries>div>span:first-child{display:grid;gap:2px}.provider-deliveries>div>span:last-child{justify-content:flex-end}.provider-deliveries small{overflow-wrap:anywhere}.provider-actions{flex-wrap:wrap}.provider-events{display:grid;border-top:1px solid #e2e8f0}.provider-events>div{display:flex;justify-content:space-between;gap:12px;padding:10px 0;border-bottom:1px solid #f1f5f9}.provider-events>div>span{display:grid;min-width:0;gap:3px;text-align:right}.provider-events>div>span:first-child{text-align:left}.provider-events small{overflow-wrap:anywhere}@media(max-width:720px){.provider-capabilities,.provider-connection-grid{grid-template-columns:1fr}.provider-connection-card__heading,.provider-subsection__title,.provider-identity-list>div,.provider-deliveries>div{align-items:flex-start;flex-direction:column}}
</style>
