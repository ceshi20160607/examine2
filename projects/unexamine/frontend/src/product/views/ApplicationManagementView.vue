<script setup lang="ts">
import { ApiOutlined, CopyOutlined, KeyOutlined, PlusOutlined, SafetyCertificateOutlined } from '@ant-design/icons-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { Empty, message } from 'ant-design-vue'
import { ApiError, api } from '../api'
import { applicationStatusColor, applicationStatusLabel, applicationTypeLabel, newApplicationNonce, parseApplicationFields, parseApplicationObject, signApplicationRequest } from '../application'
import { productDateTime } from '../presentation'
import { allowsPermission } from '../permissions'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import type {
  ApplicationCreateResult,
  ApplicationCallLogView,
  ApplicationCallResult,
  ApplicationCredentialSecret,
  ApplicationPublicationCheck,
  ApplicationPublishResult,
  ApplicationRotateResult,
  ApplicationView,
} from '../types'

interface GrantForm {
  resourceType: string
  resourceId: string
  actionCode: string
  fieldCodes: string
  dataScope: string
  rateLimit: string
}

interface CallbackForm {
  callbackType: 'EVENT' | 'RESULT'
  url: string
  eventCodes: string
  signingSecretRef: string
  timeoutMillis: number
  maxAttempts: number
}

const props = withDefaults(defineProps<{ context?: 'platform' | 'system' }>(), { context: 'platform' })
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const current = computed(() => props.context === 'platform' ? platformContext.value : systemContext.value)
const contextCode = computed(() => props.context === 'platform' ? 'PLATFORM' : 'SYSTEM')
const rows = ref<ApplicationView[]>([])
const selected = ref<ApplicationView>()
const loading = ref(false)
const busy = ref('')
const createOpen = ref(false)
const draftOpen = ref(false)
const secretOpen = ref(false)
const issuedSecret = ref<ApplicationCredentialSecret>()
const callLogs = ref<ApplicationCallLogView[]>([])
const latestCall = ref<ApplicationCallResult>()
const callOpen = ref(false)
const callBusy = ref(false)
const publicationCheck = ref<ApplicationPublicationCheck>()
const createForm = reactive({ code: '', name: '', description: '', applicationType: 'SERVICE' })
const draftForm = reactive({ name: '', description: '', applicationType: 'SERVICE' })
const grantForms = ref<GrantForm[]>([])
const callbackForms = ref<CallbackForm[]>([])
const applicationFilter = ref<'ACTIVE' | 'ALL'>('ACTIVE')
const showTechnical = ref(false)
const callForm = reactive({
  grantId: 0,
  clientId: '',
  clientSecret: '',
  idempotencyKey: '',
  nonce: '',
  requestedDataScope: '{}',
  payload: '{}',
})
const resourceOptions = computed(() => props.context === 'platform'
  ? [{ value: 'FLOW', label: '平台流程' }, { value: 'AI', label: '平台智能能力' }]
  : [{ value: 'MODULE', label: '系统模块' }, { value: 'FLOW', label: '系统流程' }])
const visibleRows = computed(() => applicationFilter.value === 'ACTIVE'
  ? rows.value.filter(item => item.status !== 'DISABLED') : rows.value)

function switchApplicationFilter(value: string) {
  applicationFilter.value = value === 'ALL' ? 'ALL' : 'ACTIVE'
  const first = visibleRows.value[0]
  if (first && !visibleRows.value.some(item => item.id === selected.value?.id)) void selectApplication(first.id)
}

function resourceLabel(value: string) {
  return ({ FLOW: '业务流程', AI: '智能能力', MODULE: '业务模块' } as Record<string, string>)[value] || '业务资源'
}

function actionLabel(value: string) {
  return ({ START: '发起', DETAIL: '查看详情', CREATE: '新建', UPDATE: '编辑', LIST: '查看列表' } as Record<string, string>)[value] || value
}

function can(action: string) {
  return allowsPermission(current.value?.permissions, 'APPLICATION', contextCode.value, action)
    || allowsPermission(current.value?.permissions, 'APPLICATION', '*', action)
}

function describeError(error: unknown) {
  if (error instanceof ApiError) return `${error.message}${error.traceId ? `（追踪号 ${error.traceId}）` : ''}`
  return error instanceof Error ? error.message : '操作失败'
}

function newGrant(): GrantForm {
  return {
    resourceType: props.context === 'platform' ? 'FLOW' : 'MODULE',
    resourceId: '',
    actionCode: props.context === 'platform' ? 'START' : 'DETAIL',
    fieldCodes: '',
    dataScope: JSON.stringify({ type: props.context === 'platform' ? 'PLATFORM' : 'SELF' }, null, 2),
    rateLimit: JSON.stringify({ maxRequests: 100, windowSeconds: 60, allowedIps: ['127.0.0.1'] }, null, 2),
  }
}

function newCallback(): CallbackForm {
  return {
    callbackType: 'RESULT', url: '', eventCodes: 'APPLICATION.CALL.COMPLETED',
    signingSecretRef: '', timeoutMillis: 5000, maxAttempts: 3,
  }
}

async function load(preferredId?: number) {
  if (!token.value || !can('VIEW')) return
  loading.value = true
  try {
    rows.value = await api<ApplicationView[]>('/api/applications', {}, token.value)
    const id = preferredId && rows.value.some(item => item.id === preferredId)
      ? preferredId : selected.value && rows.value.some(item => item.id === selected.value?.id)
        ? selected.value.id : rows.value[0]?.id
    selected.value = id ? await api<ApplicationView>(`/api/applications/${id}`, {}, token.value) : undefined
    await loadCalls()
  } catch (error) {
    message.error(describeError(error))
  } finally { loading.value = false }
}

async function selectApplication(id: number) {
  if (!token.value) return
  try {
    selected.value = await api<ApplicationView>(`/api/applications/${id}`, {}, token.value)
    await loadCalls()
    publicationCheck.value = undefined
  } catch (error) { message.error(describeError(error)) }
}

async function loadCalls() {
  if (!token.value || !selected.value) {
    callLogs.value = []
    return
  }
  callLogs.value = await api<ApplicationCallLogView[]>(
    `/api/applications/${selected.value.id}/calls`, {}, token.value)
}

function openCallTester(credential?: ApplicationCredentialSecret) {
  if (!selected.value?.grants.length) return message.warning('请先保存并发布至少一项资源动作授权')
  const grant = selected.value.grants[0]!
  const activeCredential = selected.value.credentials.find(item => item.status === 'ACTIVE')
  Object.assign(callForm, {
    grantId: grant.id,
    clientId: credential?.clientId || activeCredential?.clientId || '',
    clientSecret: credential?.clientSecret || '',
    idempotencyKey: `ui-${Date.now()}`,
    nonce: newApplicationNonce(),
    requestedDataScope: JSON.stringify(grant.dataScope, null, 2),
    payload: JSON.stringify(grant.resourceType === 'FLOW'
      ? { title: `应用调用 · ${grant.resourceId}`, variables: {} }
      : { recordId: 1 }, null, 2),
  })
  secretOpen.value = false
  callOpen.value = true
}

async function executeApplicationCall(freshNonce = false) {
  const application = selected.value
  const grant = application?.grants.find(item => item.id === callForm.grantId)
  if (!application || !grant || !callForm.clientId.trim() || !callForm.clientSecret) {
    return message.warning('请选择授权并填写本次持有的 Client ID 与 Client Secret')
  }
  callBusy.value = true
  if (freshNonce || !callForm.nonce.trim()) callForm.nonce = newApplicationNonce()
  try {
    const body = {
      resourceType: grant.resourceType,
      resourceId: grant.resourceId,
      actionCode: grant.actionCode,
      targetSystemId: grant.targetSystemId,
      targetTenantId: grant.targetTenantId,
      requestedDataScope: parseApplicationObject(callForm.requestedDataScope, '请求数据范围'),
      payload: parseApplicationObject(callForm.payload, '调用参数'),
    }
    const rawBody = JSON.stringify(body)
    const timestamp = Date.now().toString()
    const signature = await signApplicationRequest({
      clientId: callForm.clientId.trim(), clientSecret: callForm.clientSecret,
      timestamp, nonce: callForm.nonce.trim(), idempotencyKey: callForm.idempotencyKey.trim(), rawBody,
    })
    latestCall.value = await api<ApplicationCallResult>('/api/application-access/v1/calls', {
      method: 'POST', body: rawBody, headers: {
        'X-App-Client-Id': callForm.clientId.trim(),
        'X-App-Timestamp': timestamp,
        'X-App-Nonce': callForm.nonce.trim(),
        'X-App-Idempotency-Key': callForm.idempotencyKey.trim(),
        'X-App-Signature': signature,
      },
    })
    await loadCalls()
    message.success(latestCall.value.replayed ? '命中幂等结果，目标业务没有重复写入' : '签名调用成功，目标业务结果和来源已保存')
  } catch (error) {
    await loadCalls().catch(() => undefined)
    message.error(describeError(error))
  } finally { callBusy.value = false }
}

function closeCallTester() {
  callForm.clientSecret = ''
  callOpen.value = false
}

function openCreate() {
  Object.assign(createForm, { code: '', name: '', description: '', applicationType: 'SERVICE' })
  createOpen.value = true
}

async function createApplication() {
  if (!token.value || !createForm.code.trim() || !createForm.name.trim() || !can('MANAGE')) return
  busy.value = 'create'
  try {
    const result = await api<ApplicationCreateResult>('/api/applications', {
      method: 'POST', body: JSON.stringify({
        code: createForm.code.trim(), name: createForm.name.trim(),
        description: createForm.description.trim() || undefined,
        applicationType: createForm.applicationType,
      }),
    }, token.value)
    createOpen.value = false
    issuedSecret.value = result.issuedCredential
    secretOpen.value = true
    await load(result.application.id)
    message.success('应用草稿和首个凭证版本已创建')
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

function openDraft() {
  if (!selected.value) return
  Object.assign(draftForm, {
    name: selected.value.name,
    description: selected.value.description || '',
    applicationType: selected.value.applicationType,
  })
  grantForms.value = selected.value.grants.map(grant => ({
    resourceType: grant.resourceType,
    resourceId: grant.resourceId,
    actionCode: grant.actionCode,
    fieldCodes: grant.fields.map(field => field.fieldCode).join(', '),
    dataScope: JSON.stringify(grant.dataScope, null, 2),
    rateLimit: JSON.stringify(grant.rateLimit, null, 2),
  }))
  if (!grantForms.value.length) grantForms.value = [newGrant()]
  callbackForms.value = selected.value.callbacks.map(callback => ({
    callbackType: callback.callbackType,
    url: callback.url,
    eventCodes: callback.eventCodes.join(', '),
    signingSecretRef: callback.signingSecretRef,
    timeoutMillis: callback.timeoutMillis,
    maxAttempts: callback.maxAttempts,
  }))
  draftOpen.value = true
}

async function saveDraft() {
  if (!token.value || !selected.value || !can('MANAGE')) return
  if (!draftForm.name.trim() || !grantForms.value.length) return message.warning('应用名称和至少一项访问授权不能为空')
  busy.value = 'draft'
  try {
    const grants = grantForms.value.map((grant, index) => {
      if (!grant.resourceId.trim() || !grant.actionCode.trim()) throw new Error(`第 ${index + 1} 项授权缺少资源标识或动作`)
      return {
        targetType: contextCode.value,
        targetSystemId: props.context === 'system' ? current.value?.systemId : undefined,
        targetTenantId: props.context === 'system' ? current.value?.tenantId : undefined,
        resourceType: grant.resourceType,
        resourceId: grant.resourceId.trim(),
        actionCode: grant.actionCode.trim().toUpperCase(),
        dataScope: parseApplicationObject(grant.dataScope, `第 ${index + 1} 项数据范围`),
        rateLimit: parseApplicationObject(grant.rateLimit, `第 ${index + 1} 项调用控制`),
        fields: parseApplicationFields(grant.fieldCodes),
      }
    })
    const callbacks = callbackForms.value.map((callback, index) => {
      if (!callback.url.trim() || !callback.signingSecretRef.trim()) throw new Error(`第 ${index + 1} 个回调缺少地址或签名密钥引用`)
      return {
        callbackType: callback.callbackType,
        url: callback.url.trim(),
        eventCodes: callback.eventCodes.split(',').map(item => item.trim()).filter(Boolean),
        signingSecretRef: callback.signingSecretRef.trim(),
        timeoutMillis: callback.timeoutMillis,
        maxAttempts: callback.maxAttempts,
      }
    })
    selected.value = await api<ApplicationView>(`/api/applications/${selected.value.id}/draft`, {
      method: 'PUT', body: JSON.stringify({
        expectedVersion: selected.value.version,
        name: draftForm.name.trim(), description: draftForm.description.trim() || undefined,
        applicationType: draftForm.applicationType, grants, callbacks,
      }),
    }, token.value)
    draftOpen.value = false
    publicationCheck.value = undefined
    await load(selected.value.id)
    message.success('应用草稿已保存；当前发布版本尚未改变')
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

async function publishApplication() {
  if (!token.value || !selected.value || !can('PUBLISH')) return
  busy.value = 'publish'
  try {
    publicationCheck.value = await api<ApplicationPublicationCheck>(
      `/api/applications/${selected.value.id}/publication-check`, {}, token.value)
    if (!publicationCheck.value.valid) {
      return message.error('发布检查未通过，请按问题提示补全配置')
    }
    const result = await api<ApplicationPublishResult>(`/api/applications/${selected.value.id}/publish`, {
      method: 'POST', body: JSON.stringify({
        expectedDraftRevision: selected.value.draftRevision,
        changeSummary: `发布应用草稿 r${selected.value.draftRevision}`,
      }),
    }, token.value)
    await load(result.application.id)
    message.success(`应用 V${result.versionNumber} 已发布并启用`)
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

async function rotateCredential() {
  if (!token.value || !selected.value || !can('ROTATE')) return
  busy.value = 'rotate'
  try {
    const result = await api<ApplicationRotateResult>(`/api/applications/${selected.value.id}/credentials`, {
      method: 'POST', body: '{}',
    }, token.value)
    issuedSecret.value = result.issuedCredential
    secretOpen.value = true
    await load(result.application.id)
    message.success('新凭证版本已生效，旧凭证已撤销')
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

async function disableApplication() {
  if (!token.value || !selected.value || !can('DISABLE')) return
  busy.value = 'disable'
  try {
    selected.value = await api<ApplicationView>(`/api/applications/${selected.value.id}/disable`, {
      method: 'POST', body: JSON.stringify({ reason: '管理员从应用配置页停用' }),
    }, token.value)
    await load(selected.value.id)
    message.success('应用已停用，新调用和现有凭证立即失效，历史仍保留')
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

async function copySecret(value?: string) {
  if (!value) return
  await navigator.clipboard.writeText(value)
  message.success('已复制；关闭窗口后页面不会再次展示明文')
}

onMounted(() => load())
</script>

<template>
  <div class="application-management-page">
    <div class="page-heading compact-heading">
      <div><p class="eyebrow">{{ context === 'platform' ? '平台应用' : '系统后台 · 应用配置' }}</p><h1>应用接入</h1><p>管理应用可访问的业务资源、接入凭证和调用记录。</p></div>
      <a-button v-if="can('MANAGE')" type="primary" @click="openCreate"><PlusOutlined />新建应用</a-button>
    </div>
    <a-alert v-if="!can('VIEW')" type="warning" show-icon message="当前工作范围没有应用查看权限" description="平台应用与系统应用使用独立权限和数据范围。" />
    <div v-else class="application-layout">
      <section class="panel-card application-list">
        <div class="panel-title"><strong>应用</strong><a-segmented size="small" :value="applicationFilter" :options="[{ value: 'ACTIVE', label: '可用' }, { value: 'ALL', label: '全部' }]" @change="switchApplicationFilter(String($event))" /></div>
        <a-spin :spinning="loading">
          <button v-for="item in visibleRows" :key="item.id" :class="['application-list__item', { active: selected?.id === item.id }]" @click="selectApplication(item.id)">
            <span><strong>{{ item.name }}</strong><small>{{ item.description || applicationTypeLabel(item.applicationType) }}</small></span>
            <a-tag :color="applicationStatusColor(item.status)">{{ applicationStatusLabel(item.status) }}</a-tag>
          </button>
          <a-empty v-if="!loading && !visibleRows.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" :description="applicationFilter === 'ACTIVE' ? '当前没有可用应用' : '当前还没有应用'" />
        </a-spin>
      </section>

      <main v-if="selected" class="application-detail">
        <section class="panel-card application-hero">
          <div><p class="eyebrow">{{ context === 'platform' ? '平台应用' : '系统应用' }}</p><h2>{{ selected.name }}</h2><p>{{ selected.description || '暂无应用说明' }}</p><span><a-tag :color="applicationStatusColor(selected.status)">{{ applicationStatusLabel(selected.status) }}</a-tag><a-tag>{{ applicationTypeLabel(selected.applicationType) }}</a-tag><a-tag v-if="selected.currentVersionId" color="purple">已发布</a-tag></span></div>
          <div class="application-actions">
            <a-button v-if="can('MANAGE') && selected.status !== 'DISABLED'" @click="openDraft">配置草稿</a-button>
            <a-button v-if="can('PUBLISH') && selected.status !== 'DISABLED'" type="primary" :loading="busy === 'publish'" @click="publishApplication">检查并发布</a-button>
            <a-button v-if="selected.status === 'ACTIVE'" @click="openCallTester()"><ApiOutlined />接入调试</a-button>
            <a-popconfirm v-if="can('ROTATE') && selected.status === 'ACTIVE'" title="新凭证生效后旧凭证会立即撤销，确认轮换？" @confirm="rotateCredential"><a-button :loading="busy === 'rotate'"><KeyOutlined />轮换凭证</a-button></a-popconfirm>
            <a-popconfirm v-if="can('DISABLE') && selected.status !== 'DISABLED'" title="停用后新调用立即拒绝，配置和历史不会删除。确认停用？" @confirm="disableApplication"><a-button danger :loading="busy === 'disable'">停用</a-button></a-popconfirm>
          </div>
        </section>

        <a-alert v-if="publicationCheck && !publicationCheck.valid" type="error" show-icon message="发布检查未通过">
          <template #description><ul><li v-for="issue in publicationCheck.issues" :key="issue.code"><code>{{ issue.code }}</code> · {{ issue.message }}</li></ul></template>
        </a-alert>

        <section class="application-summary-grid">
          <article class="panel-card"><small>作用范围</small><strong>{{ selected.contextType === 'SYSTEM' ? '当前系统' : '整个平台' }}</strong><span>{{ selected.contextType === 'SYSTEM' ? '只允许当前系统资源' : '只允许平台资源' }}</span></article>
          <article class="panel-card"><small>访问授权</small><strong>{{ selected.grants.length }} 项</strong><span>资源、动作、字段和数据范围</span></article>
          <article class="panel-card"><small>凭证版本</small><strong>{{ selected.credentials.length }} 个</strong><span>{{ selected.credentials.filter(item => item.status === 'ACTIVE').length }} 个当前有效</span></article>
          <article class="panel-card"><small>发布历史</small><strong>{{ selected.versions.length }} 版</strong><span>不可变授权快照</span></article>
          <article class="panel-card"><small>受控调用</small><strong>{{ callLogs.length }} 次</strong><span>签名、权限快照和业务来源</span></article>
        </section>

        <section class="panel-card application-section">
          <div class="panel-title"><strong>可访问资源</strong><a-tag>按权限开放</a-tag></div>
          <div v-if="selected.grants.length" class="application-grants">
            <article v-for="grant in selected.grants" :key="grant.id">
              <div><a-tag color="blue">{{ grant.targetType === 'SYSTEM' ? '当前系统' : '平台' }}</a-tag><strong>{{ resourceLabel(grant.resourceType) }}</strong><a-tag color="green">{{ actionLabel(grant.actionCode) }}</a-tag></div>
              <p>{{ grant.fields.length ? `限制 ${grant.fields.length} 个字段` : '未限定字段清单' }} · 调用频率和来源地址受控</p>
            </article>
          </div>
          <a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="尚未配置资源动作授权，不能发布" />
        </section>

        <a-button class="application-technical-toggle" @click="showTechnical = !showTechnical">{{ showTechnical ? '收起技术接入详情' : '查看技术接入详情' }}</a-button>
        <template v-if="showTechnical">
        <section class="application-two-columns">
          <article class="panel-card application-section">
            <div class="panel-title"><strong>凭证引用与版本</strong><SafetyCertificateOutlined /></div>
            <div v-for="credential in selected.credentials" :key="credential.id" class="application-row">
              <span><strong>凭证 V{{ credential.credentialVersion }}</strong><small>{{ credential.clientId }} · {{ credential.secretReference }} · secret 尾号 {{ credential.secretHint }}</small></span><a-tag :color="credential.status === 'ACTIVE' ? 'green' : 'default'">{{ credential.status }}</a-tag>
            </div>
          </article>
          <article class="panel-card application-section">
            <div class="panel-title"><strong>回调控制</strong><a-tag>{{ selected.callbacks.length }}</a-tag></div>
            <div v-for="callback in selected.callbacks" :key="callback.id" class="application-row"><span><strong>{{ callback.callbackType }} · {{ callback.url }}</strong><small>{{ callback.eventCodes.join(', ') }} · {{ callback.timeoutMillis }}ms / {{ callback.maxAttempts }} 次</small></span></div>
            <a-empty v-if="!selected.callbacks.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="当前应用未配置回调" />
          </article>
        </section>

        <section class="application-two-columns">
          <article class="panel-card application-section">
            <div class="panel-title"><strong>不可变发布版本</strong><a-tag>{{ selected.versions.length }}</a-tag></div>
            <div v-for="version in selected.versions" :key="version.id" class="application-row"><span><strong>V{{ version.versionNumber }} <a-tag v-if="version.current" color="purple">当前运行</a-tag></strong><small>草稿 r{{ version.draftRevision }} · {{ version.snapshotHash.slice(0, 16) }}…</small></span></div>
          </article>
          <article class="panel-card application-section">
            <div class="panel-title"><strong>状态与安全历史</strong><a-tag>{{ selected.statusHistory.length }}</a-tag></div>
             <a-timeline><a-timeline-item v-for="event in selected.statusHistory" :key="event.id"><strong>{{ event.eventCode }}</strong><p>{{ event.actorAccountId === current?.accountId ? current?.displayName || '当前账号' : '其他管理员' }} · {{ event.resultCode }}</p><small>{{ productDateTime(event.occurredAt) }}</small></a-timeline-item></a-timeline>
          </article>
        </section>

        <section class="panel-card application-section">
          <div class="panel-title"><strong>应用调用日志</strong><a-tag>{{ callLogs.length }}</a-tag></div>
          <a-alert v-if="latestCall" type="success" show-icon :message="latestCall.replayed ? '已返回原幂等结果，未重复写入' : '目标业务调用成功'" :description="`${latestCall.targetReference} · requestId ${latestCall.requestId}`" />
          <div v-if="callLogs.length" class="application-call-logs">
            <article v-for="call in callLogs" :key="call.id" class="application-row application-call-row">
              <span><strong>{{ call.resourceType }} / {{ call.resourceId }} · {{ call.actionCode }}</strong><small>{{ call.requestId }} · {{ call.sourceAddress || '未知来源' }} · {{ call.durationMillis ?? '-' }} ms</small><small v-if="call.targetReference">来源结果 {{ call.targetReference }}</small><small v-if="call.errorMessage" class="danger-text">{{ call.responseCode }} · {{ call.errorMessage }}</small></span>
              <span><a-tag :color="call.status === 'SUCCESS' ? 'green' : call.status === 'FAILED' ? 'red' : 'blue'">{{ call.status }}</a-tag><small v-if="call.replayCount">幂等复用 {{ call.replayCount }} 次</small></span>
            </article>
          </div>
          <a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="尚无调用；发布后可从接入调试执行真实签名请求" />
        </section>
        </template>
      </main>
      <section v-else-if="!loading" class="panel-card application-empty"><a-empty :image="Empty.PRESENTED_IMAGE_SIMPLE" description="新建应用后在这里配置受控访问" /></section>
    </div>
  </div>

  <a-modal v-model:open="createOpen" title="新建受控访问应用" :confirm-loading="busy === 'create'" @ok="createApplication">
    <a-form layout="vertical"><div class="form-grid"><a-form-item label="应用名称" required><a-input v-model:value="createForm.name" /></a-form-item><a-form-item label="应用编码" required><a-input v-model:value="createForm.code" placeholder="customer_bridge" /></a-form-item></div><a-form-item label="应用类型" required><a-select v-model:value="createForm.applicationType" :options="[{ value: 'SERVICE', label: '服务调用' }, { value: 'WEBHOOK', label: '事件回调' }]" /></a-form-item><a-form-item label="说明"><a-textarea v-model:value="createForm.description" :rows="3" /></a-form-item><a-alert type="info" show-icon message="创建时生成首个凭证版本" description="secret 明文只在创建结果中显示一次；详情页之后只保留引用、版本、状态和尾号。" /></a-form>
  </a-modal>

  <a-modal v-model:open="draftOpen" title="配置应用草稿" width="920px" :confirm-loading="busy === 'draft'" @ok="saveDraft">
    <a-form layout="vertical"><div class="form-grid"><a-form-item label="应用名称" required><a-input v-model:value="draftForm.name" /></a-form-item><a-form-item label="应用类型" required><a-select v-model:value="draftForm.applicationType" :options="[{ value: 'SERVICE', label: '服务调用' }, { value: 'WEBHOOK', label: '事件回调' }]" /></a-form-item></div><a-form-item label="说明"><a-textarea v-model:value="draftForm.description" :rows="2" /></a-form-item></a-form>
    <div class="draft-section-heading"><div><strong>资源动作授权</strong><small>{{ context === 'platform' ? '平台应用只能选平台资源' : `范围固定为 system #${current?.systemId} / tenant #${current?.tenantId}` }}</small></div><a-button size="small" @click="grantForms.push(newGrant())">增加授权</a-button></div>
    <section v-for="(grant, index) in grantForms" :key="index" class="application-draft-card">
      <div class="application-draft-card__heading"><strong>授权 {{ index + 1 }}</strong><a-button v-if="grantForms.length > 1" type="link" danger @click="grantForms.splice(index, 1)">移除</a-button></div>
      <div class="form-grid three"><a-form-item label="资源类型" required><a-select v-model:value="grant.resourceType" :options="resourceOptions" /></a-form-item><a-form-item label="资源标识" required><a-input v-model:value="grant.resourceId" placeholder="模块编码或流程编码" /></a-form-item><a-form-item label="允许动作" required><a-input v-model:value="grant.actionCode" placeholder="例如：查看详情、发起" /></a-form-item></div>
      <a-form-item label="允许字段（逗号分隔）"><a-input v-model:value="grant.fieldCodes" placeholder="customer_name, email" /></a-form-item>
      <div class="form-grid"><a-form-item label="数据范围（JSON）" required><a-textarea v-model:value="grant.dataScope" :rows="5" /></a-form-item><a-form-item label="调用控制（JSON）" required><a-textarea v-model:value="grant.rateLimit" :rows="5" /></a-form-item></div>
    </section>
    <div class="draft-section-heading"><div><strong>结果回调</strong><small>可选；保存密钥引用，不保存回调密钥明文</small></div><a-button size="small" @click="callbackForms.push(newCallback())">增加回调</a-button></div>
    <section v-for="(callback, index) in callbackForms" :key="index" class="application-draft-card"><div class="application-draft-card__heading"><strong>回调 {{ index + 1 }}</strong><a-button type="link" danger @click="callbackForms.splice(index, 1)">移除</a-button></div><div class="form-grid"><a-form-item label="回调类型"><a-select v-model:value="callback.callbackType" :options="[{ value: 'RESULT', label: '调用结果' }, { value: 'EVENT', label: '业务事件' }]" /></a-form-item><a-form-item label="HTTPS 地址" required><a-input v-model:value="callback.url" /></a-form-item></div><a-form-item label="事件编码（逗号分隔）" required><a-input v-model:value="callback.eventCodes" /></a-form-item><a-form-item label="签名密钥引用" required><a-input v-model:value="callback.signingSecretRef" placeholder="secret://application/.../v1" /></a-form-item><div class="form-grid"><a-form-item label="超时毫秒"><a-input-number v-model:value="callback.timeoutMillis" :min="100" :max="60000" style="width:100%" /></a-form-item><a-form-item label="最大尝试次数"><a-input-number v-model:value="callback.maxAttempts" :min="1" :max="10" style="width:100%" /></a-form-item></div></section>
  </a-modal>

  <a-modal v-model:open="secretOpen" title="一次性凭证明文" :footer="null" :mask-closable="false" @after-close="issuedSecret = undefined">
    <a-alert type="warning" show-icon message="请立即保存，关闭后无法再次查看 secret 明文" description="详情页只会保留 clientId、密钥尾号、版本和状态。" />
    <a-descriptions v-if="issuedSecret" bordered :column="1" class="application-secret"><a-descriptions-item label="凭证版本">V{{ issuedSecret.credentialVersion }}</a-descriptions-item><a-descriptions-item label="密钥引用"><code>{{ issuedSecret.secretReference }}</code></a-descriptions-item><a-descriptions-item label="Client ID"><code>{{ issuedSecret.clientId }}</code></a-descriptions-item><a-descriptions-item label="Client Secret"><div class="secret-value"><code>{{ issuedSecret.clientSecret }}</code><a-button size="small" @click="copySecret(issuedSecret.clientSecret)"><CopyOutlined />复制</a-button></div></a-descriptions-item></a-descriptions>
    <div class="modal-result-actions"><a-button v-if="issuedSecret && selected?.status === 'ACTIVE'" @click="openCallTester(issuedSecret)"><ApiOutlined />带入本次密钥调试</a-button><a-button type="primary" @click="secretOpen = false">我已安全保存</a-button></div>
  </a-modal>

  <a-modal :open="callOpen" title="受控应用调用调试" width="860px" :footer="null" :mask-closable="false" @cancel="closeCallTester">
    <a-alert type="info" show-icon message="这里执行真实签名调用" description="请求会经过时间戳、nonce、防重放、幂等、限流/IP、固定上下文及应用授权与目标身份权限交集；Secret 只保留在当前输入框，关闭即清空。" />
    <a-form layout="vertical" class="application-call-form">
      <div class="form-grid"><a-form-item label="访问授权" required><a-select v-model:value="callForm.grantId" :options="selected?.grants.map(grant => ({ value: grant.id, label: `${grant.resourceType} / ${grant.resourceId} · ${grant.actionCode}` }))" /></a-form-item><a-form-item label="Client ID" required><a-input v-model:value="callForm.clientId" /></a-form-item></div>
      <a-form-item label="Client Secret（本次持有值）" required><a-input-password v-model:value="callForm.clientSecret" autocomplete="off" /></a-form-item>
      <div class="form-grid"><a-form-item label="幂等键" required><a-input v-model:value="callForm.idempotencyKey" /></a-form-item><a-form-item label="Nonce" required><a-input v-model:value="callForm.nonce" /></a-form-item></div>
      <div class="form-grid"><a-form-item label="请求数据范围（只能缩小）" required><a-textarea v-model:value="callForm.requestedDataScope" :rows="6" /></a-form-item><a-form-item label="调用参数" required><a-textarea v-model:value="callForm.payload" :rows="6" /></a-form-item></div>
      <a-alert type="warning" show-icon message="失败分支可验证" description="原 nonce 再提交会触发重放拒绝；保持幂等键并换新 nonce 会返回原结果；扩大系统、租户或数据范围会在写业务前拒绝并返回 requestId。" />
    </a-form>
    <div class="modal-result-actions"><a-button @click="closeCallTester">关闭并清空 Secret</a-button><a-button :loading="callBusy" @click="executeApplicationCall(true)">换新 Nonce 调用</a-button><a-button type="primary" :loading="callBusy" @click="executeApplicationCall(false)">按当前 Nonce 签名调用</a-button></div>
  </a-modal>
</template>
