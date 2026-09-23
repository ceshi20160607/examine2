<script setup lang="ts">
import { ApiOutlined, CopyOutlined, DownloadOutlined, KeyOutlined, PlusOutlined, SafetyCertificateOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Empty, message } from 'ant-design-vue'
import { ApiError, api } from '../api'
import { applicationStatusColor, applicationStatusLabel, applicationTypeLabel, newApplicationNonce, signApplicationRequest } from '../application'
import { productDateTime } from '../presentation'
import { allowsPermission } from '../permissions'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import ModuleContextBanner from '../components/ModuleContextBanner.vue'
import ApplicationProviderConnections from '../components/ApplicationProviderConnections.vue'
import { moduleAdminTarget, queryText } from '../module-navigation'
import type {
  ApplicationCreateResult,
  ApplicationCallLogView,
  ApplicationCallResult,
  ApplicationConfigurationExport,
  ApplicationCredentialSecret,
  ApplicationIdentityCatalog,
  ApplicationPublicationCheck,
  ApplicationPublishResult,
  ApplicationResourceField,
  ApplicationResourceOption,
  ApplicationRotateResult,
  ApplicationView,
} from '../types'

interface GrantForm {
  accessChannels: string[]
  resourceType: string
  resourceId: string
  actionCode: string
  readableFields: string[]
  writableFields: string[]
  scopeType: string
  maxRequests: number
  windowSeconds: number
  allowedIps: string[]
  failureDisableThreshold: number
  failureWindowSeconds: number
}

interface CallbackForm {
  callbackType: 'EVENT' | 'RESULT'
  url: string
  eventCodes: string[]
  signingSecretRef: string
  timeoutMillis: number
  maxAttempts: number
}

interface IdentityMappingForm {
  id?: number
  sourceTenantMemberId?: number
  targetTenantMemberId?: number
  enabled: boolean
}

const props = withDefaults(defineProps<{ context?: 'platform' | 'system' }>(), { context: 'platform' })
const route = useRoute()
const router = useRouter()
const requestedModule = computed(() => props.context === 'system' ? queryText(route.query.module) : '')
const moduleOnly = ref(Boolean(requestedModule.value))
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const current = computed(() => props.context === 'platform' ? platformContext.value : systemContext.value)
const contextCode = computed(() => props.context === 'platform' ? 'PLATFORM' : 'SYSTEM')
const rows = ref<ApplicationView[]>([])
const resources = ref<ApplicationResourceOption[]>([])
const selected = ref<ApplicationView>()
const loading = ref(false)
const busy = ref('')
const createOpen = ref(false)
const importOpen = ref(false)
const importInput = ref<HTMLInputElement>()
const importConfiguration = ref<ApplicationConfigurationExport>()
const copyOpen = ref(false)
const draftOpen = ref(false)
const rollbackOpen = ref(false)
const credentialOpen = ref(false)
const credentialAction = ref<'rotate' | 'enable'>('rotate')
const secretOpen = ref(false)
const issuedSecret = ref<ApplicationCredentialSecret>()
const callLogs = ref<ApplicationCallLogView[]>([])
const latestCall = ref<ApplicationCallResult>()
const callOpen = ref(false)
const callBusy = ref(false)
const publicationCheck = ref<ApplicationPublicationCheck>()
const iconOptions = [
  { value: 'api', label: '接口服务', glyph: '⌘' },
  { value: 'link', label: '连接器', glyph: '↗' },
  { value: 'workflow', label: '业务流程', glyph: '◇' },
  { value: 'database', label: '数据服务', glyph: '▤' },
  { value: 'robot', label: '自动化', glyph: '✦' },
  { value: 'message', label: '消息应用', glyph: '●' },
]
const createForm = reactive({ code: '', name: '', description: '', applicationType: 'SERVICE', iconCode: 'api', credentialValidityDays: 365 })
const importForm = reactive({ code: '', name: '', credentialValidityDays: 365 })
const copyForm = reactive({ code: '', name: '', credentialValidityDays: 365 })
const rollbackForm = reactive({ targetVersionId: 0, reason: '' })
const credentialForm = reactive({ validityDays: 365 })
const draftForm = reactive({ name: '', description: '', applicationType: 'SERVICE', iconCode: 'api' })
const grantForms = ref<GrantForm[]>([])
const callbackForms = ref<CallbackForm[]>([])
const identityCatalog = ref<ApplicationIdentityCatalog>({ sourceMembers: [], targetMembers: [] })
const identityMappingForms = ref<IdentityMappingForm[]>([])
const applicationFilter = ref<'ACTIVE' | 'ALL'>('ACTIVE')
const showTechnical = ref(false)
const callForm = reactive({
  accessMode: 'EXTERNAL' as 'EXTERNAL' | 'INTERNAL',
  grantId: 0,
  clientId: '',
  clientSecret: '',
  idempotencyKey: '',
  nonce: '',
  title: '',
  recordId: undefined as number | undefined,
  recordVersion: undefined as number | undefined,
  search: '',
  pageSize: 20,
})
const callFieldValues = ref<Record<string, unknown>>({})
const resourceTypeOptions = computed(() => [...new Set(resources.value.map(item => item.resourceType))]
  .map(value => ({ value, label: resourceLabel(value) })))
const contextModule = computed(() => resources.value.find(resource => resource.resourceType === 'MODULE' && resource.resourceId === requestedModule.value))
const visibleRows = computed(() => rows.value.filter(item => (applicationFilter.value !== 'ACTIVE' || item.status !== 'DISABLED')
  && (!moduleOnly.value || [...item.grants, ...item.publishedGrants].some(grant => grant.resourceType === 'MODULE' && grant.resourceId === requestedModule.value))))
function returnToModule() { if (systemContext.value?.systemId) void router.push(moduleAdminTarget(systemContext.value.systemId, requestedModule.value, 'templates', 'APPLICATION')) }
function switchModuleFilter(checked: boolean) {
  moduleOnly.value = checked
  retainVisibleSelection()
}
function retainVisibleSelection() {
  if (!visibleRows.value.some(item => item.id === selected.value?.id)) { selected.value = undefined; callLogs.value = []; if (visibleRows.value[0]) void selectApplication(visibleRows.value[0].id) }
}
watch(requestedModule, value => switchModuleFilter(Boolean(value)))
const displayGrants = computed(() => selected.value?.publishedGrants.length
  ? selected.value.publishedGrants : selected.value?.grants || [])
const displayIdentityMappings = computed(() => selected.value?.publishedIdentityMappings.length
  ? selected.value.publishedIdentityMappings : selected.value?.identityMappings || [])
const integrationGrant = computed(() => displayGrants.value.find(grant => grantChannels(grant.rateLimit).includes('EXTERNAL')))
const integrationExample = computed(() => {
  const grant = integrationGrant.value
  if (!grant) return ''
  const fields = Object.fromEntries(grant.fields.filter(field => field.writable).map(field => [field.fieldCode, `<${fieldName(grant.resourceType, grant.resourceId, field.fieldCode)}>`]))
  const payload = grant.resourceType === 'FLOW' ? { title: '外部系统发起', variables: {} }
    : grant.actionCode === 'DETAIL' ? { recordId: 123 }
      : grant.actionCode === 'LIST' ? { search: '', pageSize: 20 }
        : grant.actionCode === 'UPDATE' ? { recordId: 123, version: 0, title: '更新后的标题', fields }
          : { title: '新建记录', fields }
  return JSON.stringify({
    resourceType: grant.resourceType,
    resourceId: grant.resourceId,
    actionCode: grant.actionCode,
    targetSystemId: grant.targetSystemId,
    targetTenantId: grant.targetTenantId,
    requestedDataScope: grant.dataScope,
    payload,
  }, null, 2)
})
const availableCallGrants = computed(() => displayGrants.value.filter(grant =>
  grantChannels(grant.rateLimit).includes(callForm.accessMode)))
const selectedCallGrant = computed(() => availableCallGrants.value.find(item => item.id === callForm.grantId))
const selectedCallResource = computed(() => selectedCallGrant.value
  ? findResource(selectedCallGrant.value.resourceType, selectedCallGrant.value.resourceId) : undefined)
const writableCallFields = computed(() => {
  const writable = new Set(selectedCallGrant.value?.fields.filter(field => field.writable).map(field => field.fieldCode) || [])
  return selectedCallResource.value?.fields.filter(field => writable.has(field.code)) || []
})
const sourceIdentityOptions = computed(() => identityCatalog.value.sourceMembers.map(item => ({
  value: item.tenantMemberId,
  label: `${item.systemName} / ${item.tenantName} / ${item.memberName}${item.employeeNumber ? `（${item.employeeNumber}）` : ''}`,
})))
const targetIdentityOptions = computed(() => identityCatalog.value.targetMembers.map(item => ({
  value: item.tenantMemberId,
  label: `${item.memberName}${item.employeeNumber ? `（${item.employeeNumber}）` : ''} · ${item.tenantName}`,
})))

function switchApplicationFilter(value: string) {
  applicationFilter.value = value === 'ALL' ? 'ALL' : 'ACTIVE'
  retainVisibleSelection()
}

function resourceLabel(value: string) {
  return ({ FLOW: '业务流程', AI: '智能能力', MODULE: '业务模块' } as Record<string, string>)[value] || '业务资源'
}

function actionLabel(value: string) {
  return ({ START: '发起流程', DETAIL: '查看详情', CREATE: '新建记录', UPDATE: '编辑记录', LIST: '查询列表' } as Record<string, string>)[value] || value
}

function credentialStatusLabel(value: string) {
  return ({ ACTIVE: '当前有效', REVOKED: '已失效', EXPIRED: '已过期' } as Record<string, string>)[value] || value
}

function iconGlyph(code?: string) {
  return iconOptions.find(item => item.value === code)?.glyph || '⌘'
}

function callStatusLabel(value: string) {
  return ({ PENDING: '处理中', SUCCESS: '成功', FAILED: '失败' } as Record<string, string>)[value] || value
}

function applicationEventLabel(value: string) {
  return ({
    APPLICATION_DRAFT_CREATED: '创建应用草稿', APPLICATION_DRAFT_SAVED: '保存授权草稿',
    APPLICATION_VERSION_PUBLISHED: '发布应用版本', APPLICATION_CREDENTIAL_ROTATED: '轮换访问凭证',
    APPLICATION_DISABLED: '停用应用', APPLICATION_VERSION_ROLLED_BACK: '回滚应用版本',
    APPLICATION_ENABLED: '重新启用应用', APPLICATION_COPIED: '复制应用配置', APPLICATION_DELETED: '删除应用',
    APPLICATION_CONFIGURATION_IMPORTED: '导入应用配置',
    APPLICATION_FAILURE_POLICY_DISABLED: '触发失败策略并自动停用',
  } as Record<string, string>)[value] || '应用配置变更'
}

function findResource(type: string, id: string) {
  return resources.value.find(item => item.resourceType === type && item.resourceId === id)
}

function resourceName(type: string, id: string) {
  return findResource(type, id)?.name || id
}

function resourceChoices(type: string) {
  return resources.value.filter(item => item.resourceType === type)
    .map(item => ({ value: item.resourceId, label: `${item.name} · V${item.publishedVersionNumber || '—'}` }))
}

function actionChoices(grant: GrantForm) {
  return findResource(grant.resourceType, grant.resourceId)?.actions.map(item => ({ value: item.code, label: item.name })) || []
}

function fieldChoices(grant: GrantForm) {
  return findResource(grant.resourceType, grant.resourceId)?.fields.map(item => ({ value: item.code, label: item.name })) || []
}

function fieldName(type: string, id: string, code: string) {
  return findResource(type, id)?.fields.find(field => field.code === code)?.name || code
}

function selectGrantResource(grant: GrantForm) {
  const resource = findResource(grant.resourceType, grant.resourceId)
  grant.actionCode = resource?.actions[0]?.code || ''
  grant.readableFields = resource?.fields.map(field => field.code) || []
  grant.writableFields = []
}

function selectGrantAction(grant: GrantForm) {
  if (!['CREATE', 'UPDATE'].includes(grant.actionCode)) grant.writableFields = []
}

function selectGrantType(grant: GrantForm) {
  grant.resourceId = resources.value.find(item => item.resourceType === grant.resourceType)?.resourceId || ''
  selectGrantResource(grant)
}

function can(action: string) {
  return allowsPermission(current.value?.permissions, 'APPLICATION', contextCode.value, action)
    || allowsPermission(current.value?.permissions, 'APPLICATION', '*', action)
}

function describeError(error: unknown) {
  if (error instanceof ApiError) return `${error.message}${error.traceId ? `（追踪号 ${error.traceId}）` : ''}`
  return error instanceof Error ? error.message : '操作失败'
}

function settingString(value: unknown, fallback: string) {
  return typeof value === 'string' && value ? value : fallback
}

function settingNumber(value: unknown, fallback: number) {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

function settingStrings(value: unknown, fallback: string[] = []) {
  return Array.isArray(value) ? value.map(String).filter(Boolean) : fallback
}

function grantChannels(callControl: Record<string, unknown>) {
  const values = settingStrings(callControl.accessChannels, ['EXTERNAL'])
  return values.length ? values : ['EXTERNAL']
}

function newGrant(): GrantForm {
  const resource = requestedModule.value ? contextModule.value : resources.value[0]
  return {
    accessChannels: ['EXTERNAL'],
    resourceType: resource?.resourceType || (props.context === 'platform' ? 'FLOW' : 'MODULE'),
    resourceId: resource?.resourceId || '',
    actionCode: resource?.actions[0]?.code || (props.context === 'platform' ? 'START' : 'DETAIL'),
    readableFields: resource?.fields.map(field => field.code) || [], writableFields: [],
    scopeType: props.context === 'platform' ? 'PLATFORM' : 'SELF',
    maxRequests: 100, windowSeconds: 60, allowedIps: ['127.0.0.1'],
    failureDisableThreshold: 5, failureWindowSeconds: 300,
  }
}

function newCallback(): CallbackForm {
  return {
    callbackType: 'RESULT', url: '', eventCodes: ['APPLICATION.CALL.COMPLETED'],
    signingSecretRef: '', timeoutMillis: 5000, maxAttempts: 3,
  }
}

function newIdentityMapping(): IdentityMappingForm {
  return { sourceTenantMemberId: undefined, targetTenantMemberId: undefined, enabled: true }
}

async function load(preferredId?: number) {
  if (!token.value || !can('VIEW')) return
  loading.value = true
  try {
    const [applications, resourceCatalog] = await Promise.all([
      api<ApplicationView[]>('/api/applications', {}, token.value),
      api<ApplicationResourceOption[]>('/api/applications/resources', {}, token.value),
    ])
    rows.value = applications
    resources.value = resourceCatalog
    const id = preferredId && visibleRows.value.some(item => item.id === preferredId)
      ? preferredId : selected.value && visibleRows.value.some(item => item.id === selected.value?.id)
        ? selected.value.id : visibleRows.value[0]?.id
    selected.value = id ? await api<ApplicationView>(`/api/applications/${id}`, {}, token.value) : undefined
    await Promise.all([loadCalls(), loadIdentityCatalog()])
  } catch (error) {
    message.error(describeError(error))
  } finally { loading.value = false }
}

async function selectApplication(id: number) {
  if (!token.value) return
  try {
    selected.value = await api<ApplicationView>(`/api/applications/${id}`, {}, token.value)
    await Promise.all([loadCalls(), loadIdentityCatalog()])
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

async function loadIdentityCatalog() {
  if (!token.value || !selected.value || props.context !== 'system') {
    identityCatalog.value = { sourceMembers: [], targetMembers: [] }
    return
  }
  identityCatalog.value = await api<ApplicationIdentityCatalog>(
    `/api/applications/${selected.value.id}/identity-catalog`, {}, token.value)
}

function openCallTester(credential?: ApplicationCredentialSecret) {
  const application = selected.value
  if (!application || !displayGrants.value.length) return message.warning('请先保存并发布至少一项资源动作授权')
  const externalGrant = displayGrants.value.find(grant => grantChannels(grant.rateLimit).includes('EXTERNAL'))
  const internalGrant = displayGrants.value.find(grant => grantChannels(grant.rateLimit).includes('INTERNAL'))
  const grant = externalGrant || internalGrant
  if (!grant) return message.warning('当前发布版本没有可调用的访问方式')
  const activeCredential = application.credentials.find(item => item.status === 'ACTIVE')
  Object.assign(callForm, {
    accessMode: externalGrant ? 'EXTERNAL' : 'INTERNAL',
    grantId: grant.id,
    clientId: credential?.clientId || activeCredential?.clientId || '',
    clientSecret: credential?.clientSecret || '',
    idempotencyKey: `ui-${Date.now()}`,
    nonce: newApplicationNonce(),
    title: `应用调用 · ${resourceName(grant.resourceType, grant.resourceId)}`,
    recordId: undefined,
    recordVersion: undefined,
    search: '',
    pageSize: 20,
  })
  resetCallFields()
  secretOpen.value = false
  callOpen.value = true
}

function resetCallFields() {
  callFieldValues.value = Object.fromEntries(writableCallFields.value.map(field => [field.code,
    field.fieldType === 'BOOLEAN' ? false : field.fieldType === 'NUMBER' ? 0 : '']))
}

function switchCallMode(mode: string) {
  callForm.accessMode = mode === 'INTERNAL' ? 'INTERNAL' : 'EXTERNAL'
  callForm.grantId = availableCallGrants.value[0]?.id || 0
  resetCallFields()
}

function callPayload() {
  const grant = selectedCallGrant.value
  if (!grant) return {}
  if (grant.resourceType === 'FLOW') return { title: callForm.title, variables: {} }
  if (grant.actionCode === 'LIST') return { search: callForm.search, pageSize: callForm.pageSize }
  if (grant.actionCode === 'DETAIL') return { recordId: callForm.recordId }
  if (grant.actionCode === 'CREATE') return { title: callForm.title, fields: callFieldValues.value }
  if (grant.actionCode === 'UPDATE') return {
    recordId: callForm.recordId, version: callForm.recordVersion,
    title: callForm.title || undefined, fields: callFieldValues.value,
  }
  return {}
}

async function executeApplicationCall(freshNonce = false) {
  const application = selected.value
  const grant = selectedCallGrant.value
  if (!application || !grant) return message.warning('请选择当前访问方式已开放的资源授权')
  if (callForm.accessMode === 'EXTERNAL' && (!callForm.clientId.trim() || !callForm.clientSecret)) {
    return message.warning('外部访问需要填写本次持有的 Client ID 与 Client Secret')
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
      requestedDataScope: grant.dataScope,
      payload: callPayload(),
    }
    if (callForm.accessMode === 'INTERNAL') {
      latestCall.value = await api<ApplicationCallResult>(
        `/api/application-internal-access/v1/applications/${application.id}/calls`, {
          method: 'POST', body: JSON.stringify(body),
          headers: { 'X-Idempotency-Key': callForm.idempotencyKey.trim() },
        }, token.value)
    } else {
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
    }
    await loadCalls()
    message.success(latestCall.value.replayed ? '命中幂等结果，目标业务没有重复写入'
      : `${callForm.accessMode === 'INTERNAL' ? '系统内' : '外部签名'}调用成功，目标业务结果和来源已保存`)
  } catch (error) {
    await loadCalls().catch(() => undefined)
    message.error(describeError(error))
  } finally { callBusy.value = false }
}

function closeCallTester() {
  callForm.clientSecret = ''
  callOpen.value = false
}

function generatedCode(prefix: string, name: string) {
  const latin = name.trim().toLowerCase().replace(/[^a-z0-9_-]+/g, '_').replace(/^_+|_+$/g, '')
  return `${prefix}_${latin || Date.now().toString(36)}`.slice(0, 96)
}

function openCreate() {
  Object.assign(createForm, { code: '', name: '', description: '', applicationType: 'SERVICE', iconCode: 'api', credentialValidityDays: 365 })
  createOpen.value = true
}

async function createApplication() {
  if (!token.value || !createForm.name.trim() || !can('MANAGE')) return
  if (!createForm.code) createForm.code = generatedCode('application', createForm.name)
  busy.value = 'create'
  try {
    const result = await api<ApplicationCreateResult>('/api/applications', {
      method: 'POST', body: JSON.stringify({
        code: createForm.code.trim(), name: createForm.name.trim(),
        description: createForm.description.trim() || undefined,
        applicationType: createForm.applicationType,
        iconCode: createForm.iconCode,
        credentialValidityDays: createForm.credentialValidityDays,
      }),
    }, token.value)
    createOpen.value = false
    issuedSecret.value = result.issuedCredential
    secretOpen.value = true
    await load(result.application.id)
    message.success('应用草稿和首个凭证版本已创建')
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

function chooseImport() {
  importInput.value?.click()
}

async function readImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  try {
    const configuration = JSON.parse(await file.text()) as ApplicationConfigurationExport
    if (configuration.schemaVersion !== 1 || !configuration.applicationType || !configuration.grants?.length) {
      throw new Error('文件不是受支持的应用配置，或缺少资源授权')
    }
    importConfiguration.value = configuration
    Object.assign(importForm, {
      code: `${configuration.sourceCode}_import`,
      name: `${configuration.sourceName}（导入）`,
      credentialValidityDays: 365,
    })
    importOpen.value = true
  } catch (error) { message.error(describeError(error)) }
}

async function importApplication() {
  const configuration = importConfiguration.value
  if (!token.value || !configuration || !importForm.name.trim()) return
  if (!importForm.code) importForm.code = generatedCode('application', importForm.name)
  busy.value = 'import'
  try {
    const result = await api<ApplicationCreateResult>('/api/applications/import', {
      method: 'POST', body: JSON.stringify({
        code: importForm.code.trim(), name: importForm.name.trim(),
        description: configuration.description,
        applicationType: configuration.applicationType,
        iconCode: configuration.iconCode || 'api',
        credentialValidityDays: importForm.credentialValidityDays,
        callbacks: configuration.callbacks,
        grants: configuration.grants,
      }),
    }, token.value)
    importOpen.value = false
    importConfiguration.value = undefined
    issuedSecret.value = result.issuedCredential
    secretOpen.value = true
    await load(result.application.id)
    message.success('配置已导入为独立草稿；请检查资源范围后发布')
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

async function exportApplication() {
  if (!token.value || !selected.value) return
  busy.value = 'export'
  try {
    const configuration = await api<ApplicationConfigurationExport>(
      `/api/applications/${selected.value.id}/export`, {}, token.value)
    const blob = new Blob([JSON.stringify(configuration, null, 2)], { type: 'application/json;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `${selected.value.code}-application-config.json`
    anchor.click()
    URL.revokeObjectURL(url)
    message.success('应用草稿配置已导出；凭证明文和调用历史未包含在文件中')
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

function openCopy() {
  if (!selected.value) return
  Object.assign(copyForm, { code: `${selected.value.code}_copy`, name: `${selected.value.name}（副本）`, credentialValidityDays: 365 })
  copyOpen.value = true
}

async function copyApplication() {
  if (!token.value || !selected.value || !copyForm.name.trim()) return
  if (!copyForm.code) copyForm.code = generatedCode('application', copyForm.name)
  busy.value = 'copy'
  try {
    const result = await api<ApplicationCreateResult>(`/api/applications/${selected.value.id}/copy`, {
      method: 'POST', body: JSON.stringify({
        code: copyForm.code.trim(), name: copyForm.name.trim(),
        credentialValidityDays: copyForm.credentialValidityDays,
      }),
    }, token.value)
    copyOpen.value = false
    issuedSecret.value = result.issuedCredential
    secretOpen.value = true
    await load(result.application.id)
    message.success('应用配置已复制为独立草稿，请检查后再发布')
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

function openDraft() {
  if (!selected.value) return
  Object.assign(draftForm, {
    name: selected.value.name,
    description: selected.value.description || '',
    applicationType: selected.value.applicationType,
    iconCode: selected.value.iconCode,
  })
  grantForms.value = selected.value.grants.map(grant => ({
    accessChannels: grantChannels(grant.rateLimit),
    resourceType: grant.resourceType,
    resourceId: grant.resourceId,
    actionCode: grant.actionCode,
    readableFields: grant.fields.filter(field => field.readable).map(field => field.fieldCode),
    writableFields: grant.fields.filter(field => field.writable).map(field => field.fieldCode),
    scopeType: settingString(grant.dataScope.type, props.context === 'platform' ? 'PLATFORM' : 'SELF'),
    maxRequests: settingNumber(grant.rateLimit.maxRequests, 100),
    windowSeconds: settingNumber(grant.rateLimit.windowSeconds, 60),
    allowedIps: settingStrings(grant.rateLimit.allowedIps, ['127.0.0.1']),
    failureDisableThreshold: settingNumber(grant.rateLimit.failureDisableThreshold, 5),
    failureWindowSeconds: settingNumber(grant.rateLimit.failureWindowSeconds, 300),
  }))
  if (!grantForms.value.length) grantForms.value = [newGrant()]
  callbackForms.value = selected.value.callbacks.map(callback => ({
    callbackType: callback.callbackType,
    url: callback.url,
    eventCodes: callback.eventCodes,
    signingSecretRef: callback.signingSecretRef,
    timeoutMillis: callback.timeoutMillis,
    maxAttempts: callback.maxAttempts,
  }))
  identityMappingForms.value = selected.value.identityMappings.map(identity => ({
    id: identity.id,
    sourceTenantMemberId: identity.sourceTenantMemberId,
    targetTenantMemberId: identity.targetTenantMemberId,
    enabled: identity.status === 'ACTIVE',
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
      if (!grant.accessChannels.length) throw new Error(`第 ${index + 1} 项授权至少选择一种访问方式`)
      return {
        targetType: contextCode.value,
        targetSystemId: props.context === 'system' ? current.value?.systemId : undefined,
        targetTenantId: props.context === 'system' ? current.value?.tenantId : undefined,
        resourceType: grant.resourceType,
        resourceId: grant.resourceId.trim(),
        actionCode: grant.actionCode.trim().toUpperCase(),
        dataScope: { type: grant.scopeType },
        rateLimit: {
          accessChannels: grant.accessChannels,
          maxRequests: grant.maxRequests,
          windowSeconds: grant.windowSeconds,
          allowedIps: grant.allowedIps,
          failureDisableThreshold: grant.failureDisableThreshold,
          failureWindowSeconds: grant.failureWindowSeconds,
        },
        fields: [...new Set([...grant.readableFields, ...grant.writableFields])].map(fieldCode => ({
          fieldCode,
          readable: grant.readableFields.includes(fieldCode) || grant.writableFields.includes(fieldCode),
          writable: grant.writableFields.includes(fieldCode),
          maskStrategy: 'NONE',
        })),
      }
    })
    const callbacks = callbackForms.value.map((callback, index) => {
      if (!callback.url.trim() || !callback.signingSecretRef.trim()) throw new Error(`第 ${index + 1} 个回调缺少地址或签名密钥引用`)
      return {
        callbackType: callback.callbackType,
        url: callback.url.trim(),
        eventCodes: callback.eventCodes,
        signingSecretRef: callback.signingSecretRef.trim(),
        timeoutMillis: callback.timeoutMillis,
        maxAttempts: callback.maxAttempts,
      }
    })
    const identityMappings = identityMappingForms.value.map((identity, index) => {
      if (!identity.sourceTenantMemberId || !identity.targetTenantMemberId) {
        throw new Error(`第 ${index + 1} 项跨系统身份映射没有选择完整的来源成员和目标成员`)
      }
      return {
        id: identity.id,
        sourceTenantMemberId: identity.sourceTenantMemberId,
        targetTenantMemberId: identity.targetTenantMemberId,
        enabled: identity.enabled,
      }
    })
    if (new Set(identityMappings.map(item => item.sourceTenantMemberId)).size !== identityMappings.length) {
      throw new Error('同一来源成员只能配置一个目标执行身份')
    }
    selected.value = await api<ApplicationView>(`/api/applications/${selected.value.id}/draft`, {
      method: 'PUT', body: JSON.stringify({
        expectedVersion: selected.value.version,
        name: draftForm.name.trim(), description: draftForm.description.trim() || undefined,
        applicationType: draftForm.applicationType, iconCode: draftForm.iconCode, grants, callbacks,
        identityMappings,
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

function openCredentialIssue(action: 'rotate' | 'enable') {
  credentialAction.value = action
  credentialForm.validityDays = 365
  credentialOpen.value = true
}

async function rotateCredential() {
  if (!token.value || !selected.value || !can('ROTATE')) return
  busy.value = 'rotate'
  try {
    const result = await api<ApplicationRotateResult>(`/api/applications/${selected.value.id}/credentials`, {
      method: 'POST', body: JSON.stringify({ validityDays: credentialForm.validityDays }),
    }, token.value)
    issuedSecret.value = result.issuedCredential
    credentialOpen.value = false
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

async function enableApplication() {
  if (!token.value || !selected.value || !can('MANAGE')) return
  busy.value = 'enable'
  try {
    const result = await api<ApplicationRotateResult>(`/api/applications/${selected.value.id}/enable`, {
      method: 'POST', body: JSON.stringify({ validityDays: credentialForm.validityDays }),
    }, token.value)
    issuedSecret.value = result.issuedCredential
    credentialOpen.value = false
    secretOpen.value = true
    await load(result.application.id)
    message.success('应用已恢复启用，并签发新的凭证版本')
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

async function submitCredentialIssue() {
  if (credentialAction.value === 'rotate') await rotateCredential()
  else await enableApplication()
}

function openRollback() {
  if (!selected.value) return
  const target = selected.value.versions.find(version => !version.current)
  if (!target) return message.info('当前没有可回滚的历史版本')
  Object.assign(rollbackForm, { targetVersionId: target.id, reason: '' })
  rollbackOpen.value = true
}

async function rollbackApplication() {
  if (!token.value || !selected.value || !rollbackForm.targetVersionId || !rollbackForm.reason.trim()) return
  busy.value = 'rollback'
  try {
    const result = await api<ApplicationPublishResult>(`/api/applications/${selected.value.id}/rollback`, {
      method: 'POST', body: JSON.stringify({
        targetVersionId: rollbackForm.targetVersionId,
        expectedPublicationVersion: selected.value.publicationVersion,
        reason: rollbackForm.reason.trim(),
      }),
    }, token.value)
    rollbackOpen.value = false
    await load(result.application.id)
    message.success(`已从历史快照恢复并发布为 V${result.versionNumber}`)
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

async function deleteApplication() {
  if (!token.value || !selected.value || !can('MANAGE')) return
  busy.value = 'delete'
  try {
    const removedId = selected.value.id
    await api(`/api/applications/${removedId}`, {
      method: 'DELETE', body: JSON.stringify({ reason: '管理员确认删除应用；保留安全审计和历史调用' }),
    }, token.value)
    selected.value = undefined
    await load()
    message.success('应用已删除；凭证已失效，安全审计和历史调用仍保留')
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

async function copySecret(value?: string) {
  if (!value) return
  await navigator.clipboard.writeText(value)
  message.success('已复制；关闭窗口后页面不会再次展示明文')
}

async function copyIntegrationExample() {
  if (!integrationExample.value) return
  await navigator.clipboard.writeText(integrationExample.value)
  message.success('调用示例已复制；签名仍需由接入方使用本地保存的 Secret 计算')
}

onMounted(() => load())
</script>

<template>
  <div class="application-management-page">
    <div class="page-heading compact-heading">
      <div><p class="eyebrow">{{ context === 'platform' ? '平台应用' : '系统后台 · 应用配置' }}</p><h1>应用接入</h1><p>管理应用可访问的业务资源、接入凭证和调用记录。</p></div>
      <div class="page-heading__actions" v-if="can('MANAGE')">
        <input ref="importInput" type="file" accept="application/json,.json" hidden @change="readImportFile" />
        <a-button @click="chooseImport"><UploadOutlined />导入配置</a-button>
        <a-button type="primary" @click="openCreate"><PlusOutlined />新建应用</a-button>
      </div>
    </div>
    <ModuleContextBanner v-if="requestedModule" :module-name="contextModule?.name" :loading="loading" task="应用" @back="returnToModule" />
    <a-checkbox v-if="requestedModule" :checked="!moduleOnly" class="section-alert" @change="switchModuleFilter(!$event.target.checked)">显示所有应用</a-checkbox>
    <a-alert v-if="!can('VIEW')" type="warning" show-icon message="当前工作范围没有应用查看权限" description="平台应用与系统应用使用独立权限和数据范围。" />
    <div v-else class="application-layout">
      <section class="panel-card application-list">
        <div class="panel-title"><strong>应用</strong><a-segmented size="small" :value="applicationFilter" :options="[{ value: 'ACTIVE', label: '可用' }, { value: 'ALL', label: '全部' }]" @change="switchApplicationFilter(String($event))" /></div>
        <a-spin :spinning="loading">
          <button v-for="item in visibleRows" :key="item.id" :class="['application-list__item', { active: selected?.id === item.id }]" @click="selectApplication(item.id)">
            <span class="application-list__identity"><span class="application-icon">{{ iconGlyph(item.iconCode) }}</span><span><strong>{{ item.name }}</strong><small>{{ item.description || applicationTypeLabel(item.applicationType) }}</small></span></span>
            <a-tag :color="applicationStatusColor(item.status)">{{ applicationStatusLabel(item.status) }}</a-tag>
          </button>
          <a-empty v-if="!loading && !visibleRows.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" :description="moduleOnly ? '当前模块尚未关联应用' : applicationFilter === 'ACTIVE' ? '当前没有可用应用' : '当前还没有应用'" />
        </a-spin>
      </section>

      <main v-if="selected" class="application-detail">
        <section class="panel-card application-hero">
          <div class="application-hero__identity"><span class="application-icon application-icon--large">{{ iconGlyph(selected.iconCode) }}</span><div><p class="eyebrow">{{ context === 'platform' ? '平台应用' : '系统应用' }}</p><h2>{{ selected.name }}</h2><p>{{ selected.description || '暂无应用说明' }}</p><span><a-tag :color="applicationStatusColor(selected.status)">{{ applicationStatusLabel(selected.status) }}</a-tag><a-tag>{{ applicationTypeLabel(selected.applicationType) }}</a-tag><a-tag v-if="selected.currentVersionId" color="purple">已发布</a-tag></span></div></div>
          <div class="application-actions">
            <a-button v-if="can('MANAGE') && selected.status !== 'DISABLED'" @click="openDraft">配置草稿</a-button>
            <a-button v-if="can('MANAGE')" @click="openCopy">复制</a-button>
            <a-button v-if="can('VIEW')" :loading="busy === 'export'" @click="exportApplication"><DownloadOutlined />导出配置</a-button>
            <a-button v-if="can('PUBLISH') && selected.status !== 'DISABLED'" type="primary" :loading="busy === 'publish'" @click="publishApplication">检查并发布</a-button>
            <a-button v-if="can('PUBLISH') && selected.status === 'ACTIVE' && selected.versions.some(version => !version.current)" :loading="busy === 'rollback'" @click="openRollback">版本回滚</a-button>
            <a-button v-if="selected.status === 'ACTIVE'" @click="openCallTester()"><ApiOutlined />接入调试</a-button>
            <a-button v-if="can('ROTATE') && selected.status === 'ACTIVE'" :loading="busy === 'rotate'" @click="openCredentialIssue('rotate')"><KeyOutlined />轮换凭证</a-button>
            <a-popconfirm v-if="can('DISABLE') && selected.status !== 'DISABLED'" title="停用后新调用立即拒绝，配置和历史不会删除。确认停用？" @confirm="disableApplication"><a-button danger :loading="busy === 'disable'">停用</a-button></a-popconfirm>
            <a-button v-if="can('MANAGE') && selected.status === 'DISABLED'" type="primary" :loading="busy === 'enable'" @click="openCredentialIssue('enable')">重新启用</a-button>
            <a-popconfirm v-if="can('MANAGE') && selected.status === 'DISABLED'" title="删除后应用不再出现在列表中，凭证失效；安全审计和历史调用仍保留。确认删除？" @confirm="deleteApplication"><a-button danger :loading="busy === 'delete'">删除</a-button></a-popconfirm>
          </div>
        </section>

        <a-alert v-if="publicationCheck && !publicationCheck.valid" type="error" show-icon message="发布检查未通过">
          <template #description><ul><li v-for="issue in publicationCheck.issues" :key="issue.code">{{ issue.message }}</li></ul></template>
        </a-alert>

        <section class="application-summary-grid">
          <article class="panel-card"><small>作用范围</small><strong>{{ selected.contextType === 'SYSTEM' ? '当前系统' : '整个平台' }}</strong><span>{{ selected.contextType === 'SYSTEM' ? '只允许当前系统资源' : '只允许平台资源' }}</span></article>
          <article class="panel-card"><small>当前发布授权</small><strong>{{ displayGrants.length }} 项</strong><span>资源、动作、字段和数据范围</span></article>
          <article v-if="context === 'system'" class="panel-card"><small>跨系统身份</small><strong>{{ displayIdentityMappings.filter(item => item.status === 'ACTIVE').length }} 项</strong><span>来源成员映射到本系统执行成员</span></article>
          <article class="panel-card"><small>凭证版本</small><strong>{{ selected.credentials.length }} 个</strong><span>{{ selected.credentials.filter(item => item.status === 'ACTIVE').length }} 个当前有效</span></article>
          <article class="panel-card"><small>发布历史</small><strong>{{ selected.versions.length }} 版</strong><span>不可变授权快照</span></article>
          <article class="panel-card"><small>受控调用</small><strong>{{ callLogs.length }} 次</strong><span>签名、权限快照和业务来源</span></article>
        </section>

        <section class="panel-card application-section">
          <div class="panel-title"><strong>可访问资源</strong><a-tag>按权限开放</a-tag></div>
          <div v-if="displayGrants.length" class="application-grants">
            <article v-for="grant in displayGrants" :key="grant.id">
              <div><a-tag color="blue">{{ grant.targetType === 'SYSTEM' ? '当前系统' : '平台' }}</a-tag><a-tag v-if="grantChannels(grant.rateLimit).includes('INTERNAL')" color="cyan">系统内访问</a-tag><a-tag v-if="grantChannels(grant.rateLimit).includes('EXTERNAL')" color="purple">外部访问</a-tag><strong>{{ resourceName(grant.resourceType, grant.resourceId) }}</strong><a-tag v-if="grant.resourceVersionNumber" color="gold">固定 {{ grant.resourceType === 'FLOW' ? 'Flow' : '模块' }} V{{ grant.resourceVersionNumber }}</a-tag><a-tag color="green">{{ actionLabel(grant.actionCode) }}</a-tag></div>
              <p>{{ grant.fields.length ? `可返回 ${grant.fields.filter(field => field.readable).map(field => fieldName(grant.resourceType, grant.resourceId, field.fieldCode)).join('、') || '无'}${grant.fields.some(field => field.writable) ? `；可填写 ${grant.fields.filter(field => field.writable).map(field => fieldName(grant.resourceType, grant.resourceId, field.fieldCode)).join('、')}` : ''}` : '流程输入按固定协议校验' }} · {{ settingNumber(grant.rateLimit.maxRequests, 0) }} 次 / {{ settingNumber(grant.rateLimit.windowSeconds, 0) }} 秒 · {{ settingNumber(grant.rateLimit.failureDisableThreshold, 5) }} 次目标执行失败自动停用</p>
            </article>
          </div>
          <a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="尚未配置资源动作授权，不能发布" />
        </section>

        <section v-if="context === 'system'" class="panel-card application-section">
          <div class="panel-title"><strong>跨系统执行身份</strong><a-tag>{{ displayIdentityMappings.length }}</a-tag></div>
          <a-alert type="info" show-icon message="发布版本固定 A 系统来源成员与 B 系统执行成员" description="调用时仍会检查映射开关、双方成员状态，以及目标成员当前角色、字段权限和数据范围；停用后立即拒绝新调用。" />
          <div v-if="displayIdentityMappings.length" class="application-grants application-identity-list">
            <article v-for="identity in displayIdentityMappings" :key="identity.id">
              <div><a-tag color="cyan">{{ identity.sourceSystemName }}</a-tag><strong>{{ identity.sourceMemberName }}</strong><span>（{{ identity.sourceTenantName }}）</span><span>→</span><a-tag color="blue">{{ identity.targetSystemName }}</a-tag><strong>{{ identity.targetMemberName }}</strong><span>（{{ identity.targetTenantName }}）</span><a-tag :color="identity.status === 'ACTIVE' ? 'green' : 'red'">{{ identity.status === 'ACTIVE' ? '调用有效' : '已停用' }}</a-tag></div>
            </article>
          </div>
          <a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="尚未开放其他系统成员调用；当前系统内调用仍使用登录身份" />
        </section>

        <ApplicationProviderConnections
          :key="selected.id"
          :application-id="selected.id"
          :token="token || ''"
          :published-grants="selected.publishedGrants"
          :resources="resources"
          :can-manage="can('MANAGE')"
          :application-active="selected.status === 'ACTIVE'"
        />

        <a-button class="application-technical-toggle" @click="showTechnical = !showTechnical">{{ showTechnical ? '收起技术接入详情' : '查看技术接入详情' }}</a-button>
        <template v-if="showTechnical">
        <section class="application-two-columns">
          <article class="panel-card application-section">
            <div class="panel-title"><strong>凭证引用与版本</strong><SafetyCertificateOutlined /></div>
            <div v-for="credential in selected.credentials" :key="credential.id" class="application-row">
              <span><strong>凭证 V{{ credential.credentialVersion }}</strong><small>{{ credential.clientId }} · {{ credential.secretReference }} · 密钥尾号 {{ credential.secretHint }}</small><small>{{ productDateTime(credential.validFrom) }} 生效 · {{ credential.expiresAt ? `${productDateTime(credential.expiresAt)} 失效` : '未设置失效时间' }}</small></span><a-tag :color="credential.status === 'ACTIVE' ? 'green' : 'default'">{{ credentialStatusLabel(credential.status) }}</a-tag>
            </div>
          </article>
          <article class="panel-card application-section">
            <div class="panel-title"><strong>回调控制</strong><a-tag>{{ selected.callbacks.length }}</a-tag></div>
            <div v-for="callback in selected.callbacks" :key="callback.id" class="application-row"><span><strong>{{ callback.callbackType }} · {{ callback.url }}</strong><small>{{ callback.eventCodes.join(', ') }} · {{ callback.timeoutMillis }}ms / {{ callback.maxAttempts }} 次</small></span></div>
            <a-empty v-if="!selected.callbacks.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="当前应用未配置回调" />
          </article>
        </section>

        <section v-if="integrationGrant" class="panel-card application-section application-integration-guide">
          <div class="panel-title"><strong>外部系统接入说明</strong><a-button size="small" @click="copyIntegrationExample"><CopyOutlined />复制请求体</a-button></div>
          <a-steps size="small" :current="-1" :items="[
            { title: '准备请求', description: '按下方固定资源、动作和范围生成 JSON' },
            { title: '计算签名', description: 'clientId、毫秒时间戳、nonce、幂等键和请求体 SHA-256 依次换行后用 Secret 做 HMAC-SHA256' },
            { title: '发送调用', description: 'POST /api/application-access/v1/calls，并携带五个 X-App-* 请求头' },
          ]" />
          <div class="application-integration-grid">
            <div><strong>必填请求头</strong><ul><li>X-App-Client-Id</li><li>X-App-Timestamp（当前毫秒，五分钟内）</li><li>X-App-Nonce（每次唯一）</li><li>X-App-Idempotency-Key（同一业务请求保持一致）</li><li>X-App-Signature（小写十六进制）</li></ul></div>
            <div><strong>常见拒绝原因</strong><ul><li>时间过期：校准调用服务器时间</li><li>签名错误：保持请求体字节与签名时完全一致</li><li>Nonce 重复：每次重试生成新 Nonce</li><li>来源 IP 拒绝：检查当前授权的出口 IP</li><li>限流或越权：降低频率，检查字段和数据范围</li><li>凭证失效：使用最新一次轮换或重新启用签发的凭证</li></ul></div>
          </div>
          <a-alert type="info" show-icon message="示例由当前发布授权自动生成" description="页面不会再次读取或展示 Client Secret；接入方应在自己的密钥存储中完成签名。" />
          <pre>{{ integrationExample }}</pre>
        </section>

        <section class="application-two-columns">
          <article class="panel-card application-section">
            <div class="panel-title"><strong>不可变发布版本</strong><a-tag>{{ selected.versions.length }}</a-tag></div>
            <div v-for="version in selected.versions" :key="version.id" class="application-row"><span><strong>V{{ version.versionNumber }} <a-tag v-if="version.current" color="purple">当前运行</a-tag></strong><small>草稿 r{{ version.draftRevision }} · {{ version.snapshotHash.slice(0, 16) }}…</small></span></div>
          </article>
          <article class="panel-card application-section">
            <div class="panel-title"><strong>状态与安全历史</strong><a-tag>{{ selected.statusHistory.length }}</a-tag></div>
             <a-timeline><a-timeline-item v-for="event in selected.statusHistory" :key="event.id"><strong>{{ applicationEventLabel(event.eventCode) }}</strong><p>{{ event.actorAccountId === current?.accountId ? current?.displayName || '当前账号' : '其他管理员' }} · {{ event.resultCode === 'SUCCESS' ? '成功' : '未完成' }}</p><small>{{ productDateTime(event.occurredAt) }}</small></a-timeline-item></a-timeline>
          </article>
        </section>

        <section class="panel-card application-section">
          <div class="panel-title"><strong>应用调用日志</strong><a-tag>{{ callLogs.length }}</a-tag></div>
          <a-alert v-if="latestCall" type="success" show-icon :message="latestCall.replayed ? '已返回原幂等结果，未重复写入' : '目标业务调用成功'" :description="`${latestCall.targetReference} · requestId ${latestCall.requestId}`" />
          <div v-if="callLogs.length" class="application-call-logs">
            <article v-for="call in callLogs" :key="call.id" class="application-row application-call-row">
              <span><strong>{{ resourceName(call.resourceType, call.resourceId) }} · {{ actionLabel(call.actionCode) }}</strong><small>请求 {{ call.requestId }} · {{ call.sourceAddress || '未知来源' }} · {{ call.durationMillis ?? '-' }} 毫秒</small><small v-if="call.targetReference">业务结果 {{ call.targetReference }}</small><small v-if="call.errorMessage" class="danger-text">{{ call.errorMessage }}</small></span>
              <span><a-tag :color="call.status === 'SUCCESS' ? 'green' : call.status === 'FAILED' ? 'red' : 'blue'">{{ callStatusLabel(call.status) }}</a-tag><small v-if="call.replayCount">复用原结果 {{ call.replayCount }} 次</small></span>
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
    <a-form layout="vertical"><a-form-item label="应用名称" required><a-input v-model:value="createForm.name" placeholder="例如：客户服务连接器" /></a-form-item><div class="form-grid"><a-form-item label="应用类型" required><a-select v-model:value="createForm.applicationType" :options="[{ value: 'SERVICE', label: '服务调用' }, { value: 'WEBHOOK', label: '事件回调' }]" /></a-form-item><a-form-item label="首个凭证有效期" required><a-input-number v-model:value="createForm.credentialValidityDays" :min="1" :max="3650" style="width:100%" addon-after="天" /></a-form-item></div><a-form-item label="应用图标" required><a-radio-group v-model:value="createForm.iconCode"><a-radio-button v-for="item in iconOptions" :key="item.value" :value="item.value"><span class="application-icon-choice">{{ item.glyph }} {{ item.label }}</span></a-radio-button></a-radio-group></a-form-item><a-form-item label="说明"><a-textarea v-model:value="createForm.description" :rows="3" /></a-form-item><a-alert type="info" show-icon message="创建时生成首个访问凭证" description="密钥明文只在创建结果中显示一次；详情页之后只保留安全引用、状态、有效期和尾号。" /></a-form>
  </a-modal>

  <a-modal v-model:open="copyOpen" title="复制为新应用草稿" :confirm-loading="busy === 'copy'" @ok="copyApplication">
    <a-alert type="info" show-icon message="复制授权配置，不复制调用历史和凭证明文" style="margin-bottom:16px" />
    <a-form layout="vertical"><a-form-item label="新应用名称" required><a-input v-model:value="copyForm.name" /></a-form-item><a-form-item label="新凭证有效期" required><a-input-number v-model:value="copyForm.credentialValidityDays" :min="1" :max="3650" style="width:100%" addon-after="天" /></a-form-item><a-alert type="info" show-icon message="新应用会签发独立凭证，保存后仍需检查并发布。" /></a-form>
  </a-modal>

  <a-modal v-model:open="importOpen" title="导入应用配置" :confirm-loading="busy === 'import'" @ok="importApplication">
    <a-alert type="info" show-icon message="导入为独立草稿，不携带凭证和调用历史" description="资源、动作、字段和数据范围会重新按当前系统校验，导入后需要检查并发布。" style="margin-bottom:16px" />
    <a-form layout="vertical"><a-form-item label="新应用名称" required><a-input v-model:value="importForm.name" /></a-form-item><a-form-item label="新凭证有效期" required><a-input-number v-model:value="importForm.credentialValidityDays" :min="1" :max="3650" style="width:100%" addon-after="天" /></a-form-item><a-descriptions v-if="importConfiguration" size="small" bordered :column="1"><a-descriptions-item label="来源应用">{{ importConfiguration.sourceName }}</a-descriptions-item><a-descriptions-item label="资源授权">{{ importConfiguration.grants.length }} 项</a-descriptions-item><a-descriptions-item label="结果回调">{{ importConfiguration.callbacks.length }} 项</a-descriptions-item></a-descriptions></a-form>
  </a-modal>

  <a-modal v-model:open="rollbackOpen" title="从历史版本恢复" :confirm-loading="busy === 'rollback'" @ok="rollbackApplication">
    <a-alert type="warning" show-icon message="历史版本不会被覆盖；系统会基于所选快照发布一个新版本" style="margin-bottom:16px" />
    <a-form layout="vertical"><a-form-item label="恢复版本" required><a-select v-model:value="rollbackForm.targetVersionId" :options="selected?.versions.filter(version => !version.current).map(version => ({ value: version.id, label: `V${version.versionNumber} · ${productDateTime(version.publishedAt)}` }))" /></a-form-item><a-form-item label="恢复原因" required><a-textarea v-model:value="rollbackForm.reason" :rows="3" /></a-form-item></a-form>
  </a-modal>

  <a-modal v-model:open="draftOpen" title="配置应用草稿" width="920px" :confirm-loading="busy === 'draft'" @ok="saveDraft">
    <a-form layout="vertical"><div class="form-grid"><a-form-item label="应用名称" required><a-input v-model:value="draftForm.name" /></a-form-item><a-form-item label="应用类型" required><a-select v-model:value="draftForm.applicationType" :options="[{ value: 'SERVICE', label: '服务调用' }, { value: 'WEBHOOK', label: '事件回调' }]" /></a-form-item></div><a-form-item label="应用图标" required><a-radio-group v-model:value="draftForm.iconCode"><a-radio-button v-for="item in iconOptions" :key="item.value" :value="item.value">{{ item.glyph }} {{ item.label }}</a-radio-button></a-radio-group></a-form-item><a-form-item label="说明"><a-textarea v-model:value="draftForm.description" :rows="2" /></a-form-item></a-form>
    <div class="draft-section-heading"><div><strong>资源动作授权</strong><small>{{ context === 'platform' ? '只能选择已经发布的平台能力' : '范围固定为当前系统和租户，请从已发布业务资源中选择' }}</small></div><a-button size="small" @click="grantForms.push(newGrant())">增加授权</a-button></div>
    <section v-for="(grant, index) in grantForms" :key="index" class="application-draft-card">
      <div class="application-draft-card__heading"><strong>授权 {{ index + 1 }}</strong><a-button v-if="grantForms.length > 1" type="link" danger @click="grantForms.splice(index, 1)">移除</a-button></div>
      <a-form-item label="访问方式" required><a-checkbox-group v-model:value="grant.accessChannels" :options="[{ value: 'INTERNAL', label: '系统内访问（使用当前登录身份）' }, { value: 'EXTERNAL', label: '外部访问（凭证签名）' }]" /><small>可同时开放，两种方式都必须通过当前发布授权与目标身份权限交集校验。</small></a-form-item>
      <div class="form-grid three"><a-form-item label="资源类型" required><a-select v-model:value="grant.resourceType" :options="resourceTypeOptions" @change="selectGrantType(grant)" /></a-form-item><a-form-item label="业务资源" required><a-select v-model:value="grant.resourceId" show-search option-filter-prop="label" placeholder="选择已发布资源" :options="resourceChoices(grant.resourceType)" @change="selectGrantResource(grant)" /></a-form-item><a-form-item label="允许动作" required><a-select v-model:value="grant.actionCode" :options="actionChoices(grant)" @change="selectGrantAction(grant)" /></a-form-item></div>
      <template v-if="grant.resourceType === 'MODULE'"><a-form-item label="可返回字段" required><a-select v-model:value="grant.readableFields" mode="multiple" show-search option-filter-prop="label" placeholder="选择调用结果允许返回的业务字段" :options="fieldChoices(grant)" /></a-form-item><a-form-item v-if="['CREATE','UPDATE'].includes(grant.actionCode)" label="可填写字段" required><a-select v-model:value="grant.writableFields" mode="multiple" show-search option-filter-prop="label" placeholder="选择调用方可以填写或修改的业务字段" :options="fieldChoices(grant)" /></a-form-item></template>
      <div class="form-grid three"><a-form-item label="数据范围" required><a-select v-model:value="grant.scopeType" :disabled="context === 'platform'" :options="context === 'platform' ? [{value:'PLATFORM',label:'平台范围'}] : [{value:'SELF',label:'调用身份本人数据'},{value:'DEPARTMENT',label:'调用身份所在部门'},{value:'DEPARTMENT_AND_DESCENDANTS',label:'所在部门及下级部门'},{value:'ALL',label:'角色允许的全部数据'}]" /></a-form-item><a-form-item label="调用额度" required><a-input-number v-model:value="grant.maxRequests" :min="1" :max="100000" style="width:100%" addon-after="次" /></a-form-item><a-form-item label="统计窗口" required><a-input-number v-model:value="grant.windowSeconds" :min="1" :max="86400" style="width:100%" addon-after="秒" /></a-form-item></div>
      <a-form-item v-if="grant.accessChannels.includes('EXTERNAL')" label="允许来源 IP" required><a-select v-model:value="grant.allowedIps" mode="tags" :open="false" placeholder="输入 IP 后按回车，可添加多个" /><small>本机调试使用 127.0.0.1；生产环境请填写调用方固定出口 IP。</small></a-form-item>
      <div class="form-grid"><a-form-item label="目标执行失败停用阈值" required><a-input-number v-model:value="grant.failureDisableThreshold" :min="1" :max="1000" style="width:100%" addon-after="次" /><small>签名、重放、IP 和越权拒绝不计入，防止攻击者触发停用。</small></a-form-item><a-form-item label="失败统计窗口" required><a-input-number v-model:value="grant.failureWindowSeconds" :min="10" :max="604800" style="width:100%" addon-after="秒" /><small>窗口内达到阈值会停用应用并撤销有效凭证。</small></a-form-item></div>
    </section>
    <template v-if="context === 'system'">
      <div class="draft-section-heading"><div><strong>跨系统执行身份</strong><small>可选；从同平台其他系统的来源成员，映射到当前系统租户的真实执行成员</small></div><a-button size="small" :disabled="!sourceIdentityOptions.length || !targetIdentityOptions.length" @click="identityMappingForms.push(newIdentityMapping())">增加映射</a-button></div>
      <a-alert type="info" show-icon message="全程使用成员名称选择" description="发布后固定来源和目标关系；每次调用重新读取目标成员的当前角色、字段权限和数据范围。无需填写系统编号、成员编号或 JSON。" style="margin-bottom:12px" />
      <section v-for="(identity, index) in identityMappingForms" :key="identity.id || `new-${index}`" class="application-draft-card">
        <div class="application-draft-card__heading"><strong>身份映射 {{ index + 1 }}</strong><span><a-switch v-model:checked="identity.enabled" checked-children="启用" un-checked-children="停用" /><a-button type="link" danger @click="identityMappingForms.splice(index, 1)">移除并停用</a-button></span></div>
        <div class="form-grid"><a-form-item label="允许调用的来源成员" required><a-select v-model:value="identity.sourceTenantMemberId" show-search option-filter-prop="label" placeholder="选择其他系统中的成员" :options="sourceIdentityOptions" /></a-form-item><a-form-item label="在当前系统中执行为" required><a-select v-model:value="identity.targetTenantMemberId" show-search option-filter-prop="label" placeholder="选择当前租户成员" :options="targetIdentityOptions" /></a-form-item></div>
      </section>
      <a-empty v-if="!identityMappingForms.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="未配置跨系统调用；不影响当前系统内调用和外部凭证调用" />
    </template>
    <div class="draft-section-heading"><div><strong>结果回调</strong><small>可选；保存密钥引用，不保存回调密钥明文</small></div><a-button size="small" @click="callbackForms.push(newCallback())">增加回调</a-button></div>
    <section v-for="(callback, index) in callbackForms" :key="index" class="application-draft-card"><div class="application-draft-card__heading"><strong>回调 {{ index + 1 }}</strong><a-button type="link" danger @click="callbackForms.splice(index, 1)">移除</a-button></div><div class="form-grid"><a-form-item label="回调类型"><a-select v-model:value="callback.callbackType" :options="[{ value: 'RESULT', label: '调用结果' }, { value: 'EVENT', label: '业务事件' }]" /></a-form-item><a-form-item label="HTTPS 地址" required><a-input v-model:value="callback.url" placeholder="https://..." /></a-form-item></div><a-form-item label="触发时机" required><a-select v-model:value="callback.eventCodes" mode="multiple" :options="[{value:'APPLICATION.CALL.COMPLETED',label:'调用成功完成'},{value:'APPLICATION.CALL.FAILED',label:'调用失败'}]" /></a-form-item><a-form-item label="签名密钥引用" required><a-input v-model:value="callback.signingSecretRef" placeholder="从密钥管理中选择或粘贴引用" /></a-form-item><div class="form-grid"><a-form-item label="超时时间"><a-input-number v-model:value="callback.timeoutMillis" :min="100" :max="60000" style="width:100%" addon-after="毫秒" /></a-form-item><a-form-item label="失败重试"><a-input-number v-model:value="callback.maxAttempts" :min="1" :max="10" style="width:100%" addon-after="次" /></a-form-item></div></section>
  </a-modal>

  <a-modal v-model:open="credentialOpen" :title="credentialAction === 'rotate' ? '轮换访问凭证' : '重新启用并签发凭证'" :confirm-loading="busy === credentialAction" @ok="submitCredentialIssue">
    <a-alert :type="credentialAction === 'rotate' ? 'warning' : 'info'" show-icon :message="credentialAction === 'rotate' ? '新凭证生效后，旧凭证立即撤销' : '应用恢复启用后，只接受本次新签发的凭证'" description="Secret 明文只在完成后显示一次，请在关闭结果窗口前保存。" style="margin-bottom:16px" />
    <a-form layout="vertical"><a-form-item label="凭证有效期" required><a-input-number v-model:value="credentialForm.validityDays" :min="1" :max="3650" style="width:100%" addon-after="天" /><small>到期后请求会被拒绝；可提前轮换，新旧凭证不会同时有效。</small></a-form-item></a-form>
  </a-modal>

  <a-modal v-model:open="secretOpen" title="一次性凭证明文" :footer="null" :mask-closable="false" @after-close="issuedSecret = undefined">
    <a-alert type="warning" show-icon message="请立即保存，关闭后无法再次查看 secret 明文" description="详情页只会保留 clientId、密钥尾号、版本和状态。" />
    <a-descriptions v-if="issuedSecret" bordered :column="1" class="application-secret"><a-descriptions-item label="凭证版本">V{{ issuedSecret.credentialVersion }}</a-descriptions-item><a-descriptions-item label="有效时间">{{ productDateTime(issuedSecret.validFrom) }} 至 {{ productDateTime(issuedSecret.expiresAt) }}</a-descriptions-item><a-descriptions-item label="密钥引用"><code>{{ issuedSecret.secretReference }}</code></a-descriptions-item><a-descriptions-item label="Client ID"><code>{{ issuedSecret.clientId }}</code></a-descriptions-item><a-descriptions-item label="Client Secret"><div class="secret-value"><code>{{ issuedSecret.clientSecret }}</code><a-button size="small" @click="copySecret(issuedSecret.clientSecret)"><CopyOutlined />复制</a-button></div></a-descriptions-item></a-descriptions>
    <div class="modal-result-actions"><a-button v-if="issuedSecret && selected?.status === 'ACTIVE'" @click="openCallTester(issuedSecret)"><ApiOutlined />带入本次密钥调试</a-button><a-button type="primary" @click="secretOpen = false">我已安全保存</a-button></div>
  </a-modal>

  <a-modal :open="callOpen" title="受控应用调用调试" width="860px" :footer="null" :mask-closable="false" @cancel="closeCallTester">
    <a-alert type="info" show-icon message="这里执行真实受控调用" description="系统内访问使用当前登录身份；外部访问使用凭证签名。两者都经过幂等、限流、固定上下文及应用授权与目标身份权限交集。" />
    <a-form layout="vertical" class="application-call-form">
      <a-form-item label="访问方式" required><a-segmented :value="callForm.accessMode" :options="[{ value: 'INTERNAL', label: '系统内身份' }, { value: 'EXTERNAL', label: '外部凭证' }]" @change="switchCallMode(String($event))" /></a-form-item>
      <div class="form-grid"><a-form-item label="访问授权" required><a-select v-model:value="callForm.grantId" :options="availableCallGrants.map(grant => ({ value: grant.id, label: `${resourceName(grant.resourceType, grant.resourceId)} · ${actionLabel(grant.actionCode)}` }))" @change="resetCallFields" /></a-form-item><a-form-item v-if="callForm.accessMode === 'EXTERNAL'" label="Client ID" required><a-input v-model:value="callForm.clientId" /></a-form-item></div>
      <a-form-item v-if="callForm.accessMode === 'EXTERNAL'" label="Client Secret（本次持有值）" required><a-input-password v-model:value="callForm.clientSecret" autocomplete="off" /></a-form-item>
      <div class="form-grid"><a-form-item label="幂等键" required><a-input v-model:value="callForm.idempotencyKey" /></a-form-item><a-form-item v-if="callForm.accessMode === 'EXTERNAL'" label="Nonce" required><a-input v-model:value="callForm.nonce" /></a-form-item></div>
      <section v-if="selectedCallGrant" class="application-draft-card"><div class="application-draft-card__heading"><strong>{{ resourceName(selectedCallGrant.resourceType, selectedCallGrant.resourceId) }} · {{ actionLabel(selectedCallGrant.actionCode) }}</strong><a-tag>{{ settingString(selectedCallGrant.dataScope.type, '固定范围') }}</a-tag></div><a-form-item v-if="selectedCallGrant.resourceType === 'FLOW' || ['CREATE','UPDATE'].includes(selectedCallGrant.actionCode)" :label="selectedCallGrant.resourceType === 'FLOW' ? '流程标题' : '记录标题'" required><a-input v-model:value="callForm.title" /></a-form-item><div v-if="['DETAIL','UPDATE'].includes(selectedCallGrant.actionCode)" class="form-grid"><a-form-item label="业务记录编号" required><a-input-number v-model:value="callForm.recordId" :min="1" style="width:100%" /></a-form-item><a-form-item v-if="selectedCallGrant.actionCode === 'UPDATE'" label="当前记录版本" required><a-input-number v-model:value="callForm.recordVersion" :min="0" style="width:100%" /><small>请先查询详情，以当前版本提交，避免覆盖他人的修改。</small></a-form-item></div><div v-if="selectedCallGrant.actionCode === 'LIST'" class="form-grid"><a-form-item label="关键词"><a-input v-model:value="callForm.search" allow-clear /></a-form-item><a-form-item label="返回数量"><a-input-number v-model:value="callForm.pageSize" :min="1" :max="200" style="width:100%" /></a-form-item></div><div v-if="writableCallFields.length" class="form-grid"><a-form-item v-for="field in writableCallFields" :key="field.code" :label="field.name" :required="field.required"><a-switch v-if="field.fieldType === 'BOOLEAN'" v-model:checked="callFieldValues[field.code]" /><a-input-number v-else-if="field.fieldType === 'NUMBER'" v-model:value="callFieldValues[field.code]" style="width:100%" /><a-input v-else v-model:value="callFieldValues[field.code]" /></a-form-item></div></section>
      <a-alert type="warning" show-icon message="失败分支可验证" :description="callForm.accessMode === 'EXTERNAL' ? '原 nonce 再提交会触发重放拒绝；保持幂等键并换新 nonce 会返回原结果；扩大系统、租户或数据范围会在写业务前拒绝。' : '保持幂等键会返回原结果；当前登录身份无目标动作权限，或尝试扩大系统、租户、数据范围时，会在写业务前拒绝。'" />
    </a-form>
    <div class="modal-result-actions"><a-button @click="closeCallTester">关闭并清空 Secret</a-button><a-button v-if="callForm.accessMode === 'EXTERNAL'" :loading="callBusy" @click="executeApplicationCall(true)">换新 Nonce 调用</a-button><a-button type="primary" :loading="callBusy" @click="executeApplicationCall(false)">{{ callForm.accessMode === 'INTERNAL' ? '使用当前身份调用' : '按当前 Nonce 签名调用' }}</a-button></div>
  </a-modal>
</template>
