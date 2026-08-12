<script setup lang="ts">
import type { TableColumnsType } from 'ant-design-vue'
import { Copy, Eye, Pencil, Plus, Power, RefreshCw, RotateCw } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import AdminMobileNotice from '@/components/admin/AdminMobileNotice.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { ApiRequestError } from '@/services/api'
import { openApiApplicationApi } from '@/services/openapi'
import type {
  ChangeOpenApiApplicationStatusInput,
  CreateOpenApiApplicationInput,
  OpenApiApplication,
  OpenApiCallLog,
  OpenApiCallLogMethodFilter,
  OpenApiCallLogResultFilter,
  RotateOpenApiSecretRefInput,
  UpdateOpenApiApplicationPolicyInput,
} from '@/types/openapi'
import { useAdminViewport } from '@/composables/useAdminViewport'

const { isMobile } = useAdminViewport()

interface MutationAttempt {
  fingerprint: string
  idempotencyKey: string
}

const route = useRoute()
const systemId = computed(() => String(route.params.systemId))
const applications = ref<OpenApiApplication[]>([])
const total = ref(0)
const loading = ref(false)
const detailLoading = ref(false)
const mutation = ref('')
const errorMessage = ref('')
const mutationError = ref('')
const detailOpen = ref(false)
const selected = ref<OpenApiApplication | null>(null)
const createOpen = ref(false)
const policyOpen = ref(false)
const rotateOpen = ref(false)
const statusOpen = ref(false)
const statusCommand = ref<'enable' | 'disable'>('disable')
const copiedExample = ref('')
const copyError = ref('')
const callLogs = ref<OpenApiCallLog[]>([])
const callLogTotal = ref(0)
const callLogPage = ref(1)
const callLogPageSize = ref(20)
const callLogResult = ref<OpenApiCallLogResultFilter>('ALL')
const callLogMethod = ref<OpenApiCallLogMethodFilter>('ALL')
const callLogLoading = ref(false)
const callLogError = ref('')
let callLogRequestSequence = 0
const mutationAttempts = new Map<string, MutationAttempt>()

const callLogPageCount = computed(() =>
  Math.max(1, Math.ceil(callLogTotal.value / callLogPageSize.value)))

const recordRequestExamples = [
  {
    id: 'create',
    title: '创建记录',
    request: `# <SECRET> 仅在调用端用于生成 HMAC-SHA256 签名，不随请求发送
curl --request POST '<BASE_URL>/openapi/v1/modules/<moduleCode>/records' \\
  --header 'Content-Type: application/json' \\
  --header 'X-App-Key: <APP_KEY>' \\
  --header 'X-Timestamp: <timestamp>' \\
  --header 'X-Nonce: <nonce>' \\
  --header 'X-Signature: <HMAC_SHA256_WITH_SECRET>' \\
  --header 'Idempotency-Key: <Idempotency-Key>' \\
  --data '{
    "lifecycleState": "DRAFT",
    "values": { "fieldCode": "FIELD_VALUE" }
  }'`,
  },
  {
    id: 'list',
    title: '查询记录列表',
    request: `# <SECRET> 仅在调用端用于生成 HMAC-SHA256 签名，不随请求发送
curl --request GET '<BASE_URL>/openapi/v1/modules/<moduleCode>/records?page=1&size=20' \\
  --header 'X-App-Key: <APP_KEY>' \\
  --header 'X-Timestamp: <timestamp>' \\
  --header 'X-Nonce: <nonce>' \\
  --header 'X-Signature: <HMAC_SHA256_WITH_SECRET>' \\
  --header 'Idempotency-Key: <Idempotency-Key>'`,
  },
  {
    id: 'detail',
    title: '查询记录详情',
    request: `# <SECRET> 仅在调用端用于生成 HMAC-SHA256 签名，不随请求发送
curl --request GET '<BASE_URL>/openapi/v1/modules/<moduleCode>/records/<recordId>' \\
  --header 'X-App-Key: <APP_KEY>' \\
  --header 'X-Timestamp: <timestamp>' \\
  --header 'X-Nonce: <nonce>' \\
  --header 'X-Signature: <HMAC_SHA256_WITH_SECRET>' \\
  --header 'Idempotency-Key: <Idempotency-Key>'`,
  },
  {
    id: 'update',
    title: '更新记录',
    request: `# <SECRET> 仅在调用端用于生成 HMAC-SHA256 签名，不随请求发送
curl --request PUT '<BASE_URL>/openapi/v1/modules/<moduleCode>/records/<recordId>' \\
  --header 'Content-Type: application/json' \\
  --header 'X-App-Key: <APP_KEY>' \\
  --header 'X-Timestamp: <timestamp>' \\
  --header 'X-Nonce: <nonce>' \\
  --header 'X-Signature: <HMAC_SHA256_WITH_SECRET>' \\
  --header 'Idempotency-Key: <Idempotency-Key>' \\
  --data '{
    "expectedVersion": <expectedVersion>,
    "values": { "fieldCode": "UPDATED_FIELD_VALUE" }
  }'`,
  },
  ...([
    ['activate', '激活记录'],
    ['archive', '归档记录'],
    ['unarchive', '取消归档'],
    ['trash', '移入回收站'],
    ['restore-from-trash', '从回收站恢复'],
  ] as const).map(([action, title]) => ({
    id: action,
    title,
    request: `# <SECRET> 仅在调用端用于生成 HMAC-SHA256 签名，不随请求发送
curl --request POST '<BASE_URL>/openapi/v1/modules/<moduleCode>/records/<recordId>:${action}' \\
  --header 'Content-Type: application/json' \\
  --header 'X-App-Key: <APP_KEY>' \\
  --header 'X-Timestamp: <timestamp>' \\
  --header 'X-Nonce: <nonce>' \\
  --header 'X-Signature: <HMAC_SHA256_WITH_SECRET>' \\
  --header 'Idempotency-Key: <Idempotency-Key>' \\
  --data '{ "expectedVersion": <expectedVersion> }'`,
  })),
  {
    id: 'file-upload',
    title: '上传记录文件',
    request: `# <SECRET> 仅在调用端用于生成 HMAC-SHA256 签名，不随请求发送
curl --request POST '<BASE_URL>/openapi/v1/modules/<moduleCode>/records/<recordId>/files' \\
  --header 'Content-Type: application/json' \\
  --header 'X-App-Key: <APP_KEY>' \\
  --header 'X-Timestamp: <timestamp>' \\
  --header 'X-Nonce: <nonce>' \\
  --header 'X-Signature: <HMAC_SHA256_WITH_SECRET>' \\
  --header 'Idempotency-Key: <Idempotency-Key>' \\
  --data '{
    "originalName": "<originalName>",
    "mediaType": "<mediaType>",
    "contentBase64": "<contentBase64>"
  }'`,
  },
  {
    id: 'file-list',
    title: '查询记录文件',
    request: `# <SECRET> 仅在调用端用于生成 HMAC-SHA256 签名，不随请求发送
curl --request GET '<BASE_URL>/openapi/v1/modules/<moduleCode>/records/<recordId>/files?page=1&size=20' \\
  --header 'X-App-Key: <APP_KEY>' \\
  --header 'X-Timestamp: <timestamp>' \\
  --header 'X-Nonce: <nonce>' \\
  --header 'X-Signature: <HMAC_SHA256_WITH_SECRET>' \\
  --header 'Idempotency-Key: <Idempotency-Key>'`,
  },
  {
    id: 'file-download',
    title: '下载记录文件',
    request: `# <SECRET> 仅在调用端用于生成 HMAC-SHA256 签名，不随请求发送
curl --request GET '<BASE_URL>/openapi/v1/modules/<moduleCode>/records/<recordId>/files/<fileId>/content' \\
  --header 'X-App-Key: <APP_KEY>' \\
  --header 'X-Timestamp: <timestamp>' \\
  --header 'X-Nonce: <nonce>' \\
  --header 'X-Signature: <HMAC_SHA256_WITH_SECRET>' \\
  --header 'Idempotency-Key: <Idempotency-Key>' \\
  --output '<downloadFileName>'`,
  },
  {
    id: 'flow-status',
    title: '查询流程实例状态 · flow.read',
    request: `# Required application scope: flow.read
# <SECRET> 仅在调用端用于生成 HMAC-SHA256 签名，不随请求发送
curl --request GET '<BASE_URL>/openapi/v1/flow/instances/<instanceId>' \\
  --header 'X-App-Key: <APP_KEY>' \\
  --header 'X-Timestamp: <timestamp>' \\
  --header 'X-Nonce: <nonce>' \\
  --header 'X-Signature: <HMAC_SHA256_WITH_SECRET>' \\
  --header 'Idempotency-Key: <Idempotency-Key>'`,
  },
  {
    id: 'relation-list',
    title: '查询关系字段 · record.read',
    request: `# Required application scope: record.read
# <SECRET> 仅在调用端用于生成 HMAC-SHA256 签名，不随请求发送
curl --request GET '<BASE_URL>/openapi/v1/modules/<moduleCode>/records/<recordId>/relations/<fieldCode>?page=1&size=20' \\
  --header 'X-App-Key: <APP_KEY>' \\
  --header 'X-Timestamp: <timestamp>' \\
  --header 'X-Nonce: <nonce>' \\
  --header 'X-Signature: <HMAC_SHA256_WITH_SECRET>' \\
  --header 'Idempotency-Key: <Idempotency-Key>'`,
  },
  {
    id: 'relation-mutate',
    title: '变更关系字段 · record.write',
    request: `# Required application scope: record.write
# <SECRET> 仅在调用端用于生成 HMAC-SHA256 签名，不随请求发送
curl --request POST '<BASE_URL>/openapi/v1/modules/<moduleCode>/records/<recordId>/relations/<fieldCode>:mutate' \\
  --header 'Content-Type: application/json' \\
  --header 'X-App-Key: <APP_KEY>' \\
  --header 'X-Timestamp: <timestamp>' \\
  --header 'X-Nonce: <nonce>' \\
  --header 'X-Signature: <HMAC_SHA256_WITH_SECRET>' \\
  --header 'Idempotency-Key: <Idempotency-Key>' \\
  --data '{
    "expectedVersion": <expectedVersion>,
    "add": [{
      "targetRecordId": "<targetRecordId>",
      "targetExpectedVersion": <targetExpectedVersion>,
      "ordinal": 0
    }],
    "remove": [],
    "order": []
  }'`,
  },
  {
    id: 'subtable-list',
    title: '查询子表字段 · record.read',
    request: `# Required application scope: record.read
# <SECRET> 仅在调用端用于生成 HMAC-SHA256 签名，不随请求发送
curl --request GET '<BASE_URL>/openapi/v1/modules/<moduleCode>/records/<recordId>/subtables/<fieldCode>?page=1&size=20' \\
  --header 'X-App-Key: <APP_KEY>' \\
  --header 'X-Timestamp: <timestamp>' \\
  --header 'X-Nonce: <nonce>' \\
  --header 'X-Signature: <HMAC_SHA256_WITH_SECRET>' \\
  --header 'Idempotency-Key: <Idempotency-Key>'`,
  },
  {
    id: 'subtable-mutate',
    title: '变更子表字段 · record.write',
    request: `# Required application scope: record.write
# <SECRET> 仅在调用端用于生成 HMAC-SHA256 签名，不随请求发送
curl --request POST '<BASE_URL>/openapi/v1/modules/<moduleCode>/records/<recordId>/subtables/<fieldCode>:mutate' \\
  --header 'Content-Type: application/json' \\
  --header 'X-App-Key: <APP_KEY>' \\
  --header 'X-Timestamp: <timestamp>' \\
  --header 'X-Nonce: <nonce>' \\
  --header 'X-Signature: <HMAC_SHA256_WITH_SECRET>' \\
  --header 'Idempotency-Key: <Idempotency-Key>' \\
  --data '{
    "expectedVersion": <expectedVersion>,
    "add": [{
      "clientRowKey": "<clientRowKey>",
      "ordinal": 0,
      "values": { "<subFieldCode>": "SUB_FIELD_VALUE" }
    }],
    "update": [],
    "remove": [],
    "order": []
  }'`,
  },
] as const

const createForm = reactive({
  name: '',
  tenantId: '',
  serviceMemberId: '',
  secretRef: '',
  scopesText: '',
  ipAllowlistText: '',
  rateLimitPerMinute: 60,
})
const policyForm = reactive({
  scopesText: '',
  ipAllowlistText: '',
  rateLimitPerMinute: 60,
  version: 0,
})
const rotateForm = reactive({ secretRef: '', version: 0 })
const statusForm = reactive({ reason: '', version: 0 })

const columns: TableColumnsType = [
  { title: '应用', key: 'application', width: 260 },
  { title: '租户', dataIndex: 'tenantId', width: 150 },
  { title: '服务成员', dataIndex: 'serviceMemberId', width: 160 },
  { title: '凭据版本', dataIndex: 'credentialVersion', width: 110 },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '更新时间', key: 'updatedAt', width: 180 },
  { title: '操作', key: 'actions', width: 100 },
]

function requestError(error: unknown, fallback: string) {
  return error instanceof ApiRequestError
    ? error.message || error.code
    : error instanceof Error
      ? error.message
      : fallback
}

function lines(value: string) {
  return [...new Set(value
    .split(/[\n,]/u)
    .map((item) => item.trim())
    .filter(Boolean))]
}

function mutationKey(operation: string, payload: unknown) {
  const fingerprint = JSON.stringify(payload)
  const previous = mutationAttempts.get(operation)
  if (previous?.fingerprint === fingerprint) return previous.idempotencyKey
  const idempotencyKey = crypto.randomUUID()
  mutationAttempts.set(operation, { fingerprint, idempotencyKey })
  return idempotencyKey
}

function clearAttempt(operation: string) {
  mutationAttempts.delete(operation)
}

function validRate(value: number) {
  if (Number.isInteger(value) && value >= 1 && value <= 60_000) return true
  mutationError.value = '每分钟限流必须是 1 到 60000 的整数'
  return false
}

function replaceApplication(application: OpenApiApplication) {
  const index = applications.value.findIndex((item) => item.id === application.id)
  if (index < 0) {
    applications.value = [application, ...applications.value]
    total.value += 1
  } else {
    applications.value = applications.value.map((item) =>
      item.id === application.id ? application : item)
  }
  selected.value = application
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await openApiApplicationApi.list(systemId.value, 1, 100)
    applications.value = result.items
    total.value = result.total
  } catch (error) {
    errorMessage.value = requestError(error, '开放应用加载失败')
  } finally {
    loading.value = false
  }
}

async function showDetail(application: OpenApiApplication) {
  detailOpen.value = true
  detailLoading.value = true
  errorMessage.value = ''
  selected.value = application
  resetCallLogState()
  try {
    selected.value = await openApiApplicationApi.detail(systemId.value, application.id)
    await loadCallLogs(selected.value.id)
  } catch (error) {
    errorMessage.value = requestError(error, '开放应用详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

function resetCallLogState() {
  callLogRequestSequence += 1
  callLogs.value = []
  callLogTotal.value = 0
  callLogPage.value = 1
  callLogPageSize.value = 20
  callLogResult.value = 'ALL'
  callLogMethod.value = 'ALL'
  callLogLoading.value = false
  callLogError.value = ''
}

async function loadCallLogs(applicationId = selected.value?.id) {
  if (!applicationId) return
  const requestSequence = ++callLogRequestSequence
  callLogLoading.value = true
  callLogError.value = ''
  try {
    const result = await openApiApplicationApi.callLogs(systemId.value, applicationId, {
      resultCategory: callLogResult.value,
      requestMethod: callLogMethod.value,
      page: callLogPage.value,
      size: callLogPageSize.value,
    })
    if (requestSequence !== callLogRequestSequence || selected.value?.id !== applicationId) return
    callLogs.value = result.items
    callLogPage.value = result.page
    callLogPageSize.value = result.size
    callLogTotal.value = result.total
  } catch (error) {
    if (requestSequence !== callLogRequestSequence || selected.value?.id !== applicationId) return
    callLogError.value = requestError(error, '调用日志加载失败')
  } finally {
    if (requestSequence === callLogRequestSequence) callLogLoading.value = false
  }
}

function changeCallLogResult(event: Event) {
  callLogResult.value = (event.target as HTMLSelectElement).value as OpenApiCallLogResultFilter
  callLogPage.value = 1
  void loadCallLogs()
}

function changeCallLogMethod(event: Event) {
  callLogMethod.value = (event.target as HTMLSelectElement).value as OpenApiCallLogMethodFilter
  callLogPage.value = 1
  void loadCallLogs()
}

function previousCallLogPage() {
  if (callLogPage.value <= 1) return
  callLogPage.value -= 1
  void loadCallLogs()
}

function nextCallLogPage() {
  if (callLogPage.value >= callLogPageCount.value) return
  callLogPage.value += 1
  void loadCallLogs()
}

function openCreate() {
  Object.assign(createForm, {
    name: '',
    tenantId: '',
    serviceMemberId: '',
    secretRef: '',
    scopesText: '',
    ipAllowlistText: '',
    rateLimitPerMinute: 60,
  })
  mutationError.value = ''
  clearAttempt('create')
  createOpen.value = true
}

async function createApplication() {
  const input: CreateOpenApiApplicationInput = {
    name: createForm.name.trim(),
    tenantId: createForm.tenantId.trim(),
    serviceMemberId: createForm.serviceMemberId.trim(),
    secretRef: createForm.secretRef.trim(),
    scopes: lines(createForm.scopesText),
    ipAllowlist: lines(createForm.ipAllowlistText),
    rateLimitPerMinute: Number(createForm.rateLimitPerMinute),
  }
  mutationError.value = ''
  if (!input.name || !input.tenantId || !input.serviceMemberId || !input.secretRef) {
    mutationError.value = '名称、租户、服务成员和 SecretRef 均为必填项'
    return
  }
  if (!validRate(input.rateLimitPerMinute)) return
  mutation.value = 'create'
  try {
    const created = await openApiApplicationApi.create(
      systemId.value,
      input,
      mutationKey('create', input),
    )
    clearAttempt('create')
    replaceApplication(created)
    createOpen.value = false
  } catch (error) {
    mutationError.value = requestError(error, '开放应用创建失败')
  } finally {
    mutation.value = ''
  }
}

function openPolicyEditor() {
  if (!selected.value) return
  Object.assign(policyForm, {
    scopesText: selected.value.scopes.join('\n'),
    ipAllowlistText: selected.value.ipAllowlist.join('\n'),
    rateLimitPerMinute: selected.value.rateLimitPerMinute,
    version: selected.value.version,
  })
  mutationError.value = ''
  clearAttempt(`policy:${selected.value.id}`)
  policyOpen.value = true
}

async function savePolicy() {
  if (!selected.value) return
  const input: UpdateOpenApiApplicationPolicyInput = {
    scopes: lines(policyForm.scopesText),
    ipAllowlist: lines(policyForm.ipAllowlistText),
    rateLimitPerMinute: Number(policyForm.rateLimitPerMinute),
    version: policyForm.version,
  }
  mutationError.value = ''
  if (!validRate(input.rateLimitPerMinute)) return
  const operation = `policy:${selected.value.id}`
  mutation.value = operation
  try {
    const updated = await openApiApplicationApi.updatePolicy(
      systemId.value,
      selected.value.id,
      input,
      mutationKey(operation, input),
    )
    clearAttempt(operation)
    replaceApplication(updated)
    policyOpen.value = false
  } catch (error) {
    mutationError.value = requestError(error, '应用策略保存失败')
  } finally {
    mutation.value = ''
  }
}

function openRotate() {
  if (!selected.value) return
  Object.assign(rotateForm, { secretRef: '', version: selected.value.version })
  mutationError.value = ''
  clearAttempt(`rotate:${selected.value.id}`)
  rotateOpen.value = true
}

async function rotateSecretRef() {
  if (!selected.value) return
  const input: RotateOpenApiSecretRefInput = {
    secretRef: rotateForm.secretRef.trim(),
    version: rotateForm.version,
  }
  mutationError.value = ''
  if (!input.secretRef) {
    mutationError.value = '请输入新的 SecretRef'
    return
  }
  const operation = `rotate:${selected.value.id}`
  mutation.value = operation
  try {
    const updated = await openApiApplicationApi.rotateSecretRef(
      systemId.value,
      selected.value.id,
      input,
      mutationKey(operation, input),
    )
    clearAttempt(operation)
    replaceApplication(updated)
    rotateOpen.value = false
  } catch (error) {
    mutationError.value = requestError(error, 'SecretRef 轮换失败')
  } finally {
    mutation.value = ''
  }
}

function openStatus(command: 'enable' | 'disable') {
  if (!selected.value) return
  statusCommand.value = command
  Object.assign(statusForm, { reason: '', version: selected.value.version })
  mutationError.value = ''
  clearAttempt(`status:${selected.value.id}:${command}`)
  statusOpen.value = true
}

async function changeStatus() {
  if (!selected.value) return
  const input: ChangeOpenApiApplicationStatusInput = {
    version: statusForm.version,
    reason: statusForm.reason.trim(),
  }
  mutationError.value = ''
  if (!input.reason) {
    mutationError.value = '请输入状态变更原因'
    return
  }
  const operation = `status:${selected.value.id}:${statusCommand.value}`
  mutation.value = operation
  try {
    const updated = await openApiApplicationApi.changeStatus(
      systemId.value,
      selected.value.id,
      statusCommand.value,
      input,
      mutationKey(operation, input),
    )
    clearAttempt(operation)
    replaceApplication(updated)
    statusOpen.value = false
  } catch (error) {
    mutationError.value = requestError(error, '应用状态更新失败')
  } finally {
    mutation.value = ''
  }
}

function formatTime(value: string) {
  const parsed = new Date(value)
  return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleString('zh-CN')
}

async function copyRequestExample(id: string, request: string) {
  copyError.value = ''
  try {
    if (!navigator.clipboard?.writeText) throw new Error('clipboard unavailable')
    await navigator.clipboard.writeText(request)
    copiedExample.value = id
  } catch {
    copiedExample.value = ''
    copyError.value = '复制失败，请手动选择示例内容。'
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page openapi-applications-page">
    <AdminPageHeader
      title="开放应用"
      :description="`管理 ${total} 个外部应用的固定租户、服务成员、凭据引用与调用策略。`"
    >
      <template v-if="!isMobile" #actions>
        <a-button aria-label="刷新开放应用" :loading="loading" :disabled="loading" @click="load"><RefreshCw :size="16" />刷新</a-button>
        <a-button class="openapi-create" type="primary" @click="openCreate">
          <Plus :size="16" />新增应用
        </a-button>
      </template>
    </AdminPageHeader>

    <a-alert
      class="secret-boundary-notice"
      type="info"
      show-icon
      message="仅提交 SecretRef"
      description="系统后台不接收、不生成、也不展示任何明文应用密钥；SecretRef 必须指向外部密钥提供方中的引用。"
    />
    <a-alert v-if="errorMessage" class="admin-alert" type="error" show-icon role="alert" aria-live="assertive" :message="errorMessage" />

    <div v-if="isMobile" class="openapi-mobile-gate">
      <AdminMobileNotice />
      <a-empty description="开放应用涉及调用策略、SecretRef 轮换和签名示例，请在宽度大于 720px 的桌面窗口中管理。" />
    </div>
    <div v-else class="admin-table-region" :aria-busy="loading">
      <a-table
        :columns="columns"
        :data-source="applications"
        :loading="loading"
        :pagination="false"
        row-key="id"
        :scroll="{ x: 1080 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'application'">
            <button class="openapi-application-link" type="button" @click="showDetail(record as OpenApiApplication)">
              <strong>{{ record.name }}</strong>
              <span class="cell-secondary">{{ record.appKey }}</span>
            </button>
          </template>
          <template v-else-if="column.dataIndex === 'credentialVersion'">
            v{{ record.credentialVersion }}
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <a-tag :color="record.status === 'ACTIVE' ? 'green' : 'default'">{{ record.status }}</a-tag>
          </template>
          <template v-else-if="column.key === 'updatedAt'">{{ formatTime(record.updatedAt) }}</template>
          <template v-else-if="column.key === 'actions'">
            <a-button class="openapi-detail" type="link" size="small" @click="showDetail(record as OpenApiApplication)">
              <Eye :size="14" />详情
            </a-button>
          </template>
        </template>
      </a-table>
      <a-empty v-if="!loading && !applications.length" description="暂无开放应用" />
    </div>

    <a-drawer
      v-model:open="detailOpen"
      :title="selected?.name || '开放应用详情'"
      :width="620"
    >
      <a-spin :spinning="detailLoading">
        <template v-if="selected">
          <div class="openapi-detail-actions">
            <a-button class="openapi-policy-edit" @click="openPolicyEditor"><Pencil :size="15" />编辑策略</a-button>
            <a-button class="openapi-secret-rotate" @click="openRotate"><RotateCw :size="15" />轮换 SecretRef</a-button>
            <a-button
              v-if="selected.status === 'DISABLED'"
              class="openapi-enable"
              type="primary"
              @click="openStatus('enable')"
            >
              <Power :size="15" />启用
            </a-button>
            <a-button
              v-else
              class="openapi-disable"
              danger
              @click="openStatus('disable')"
            >
              <Power :size="15" />停用
            </a-button>
          </div>
          <dl class="openapi-detail-list">
            <div><dt>应用标识</dt><dd>{{ selected.appKey }}</dd></div>
            <div><dt>状态</dt><dd>{{ selected.status }}</dd></div>
            <div><dt>凭据版本</dt><dd>v{{ selected.credentialVersion }}</dd></div>
            <div><dt>SecretRef</dt><dd>{{ selected.secretRef }}</dd></div>
            <div><dt>服务成员</dt><dd>{{ selected.serviceMemberId }}</dd></div>
            <div><dt>租户</dt><dd>{{ selected.tenantId }}</dd></div>
            <div><dt>Scopes</dt><dd>{{ selected.scopes.join('、') || '无（不授予任何范围）' }}</dd></div>
            <div><dt>IP 白名单</dt><dd>{{ selected.ipAllowlist.join('、') || '空（拒绝所有外部调用）' }}</dd></div>
            <div><dt>每分钟限流</dt><dd>{{ selected.rateLimitPerMinute }}</dd></div>
            <div><dt>更新时间</dt><dd>{{ formatTime(selected.updatedAt) }}</dd></div>
          </dl>
          <section class="openapi-call-logs" aria-label="应用调用日志">
            <header class="openapi-call-log-heading">
              <div><h3>调用日志</h3><p>仅展示当前应用的只读调用结果。</p></div>
              <a-button class="openapi-call-log-refresh" :loading="callLogLoading" @click="loadCallLogs()">
                <RefreshCw :size="15" />刷新日志
              </a-button>
            </header>
            <div class="openapi-call-log-filters">
              <label for="openapi-call-log-result">结果</label>
              <select id="openapi-call-log-result" class="openapi-call-log-result" :value="callLogResult" @change="changeCallLogResult">
                <option value="ALL">全部结果</option>
                <option value="SUCCESS">SUCCESS</option>
                <option value="AUTH_REJECTED">AUTH_REJECTED</option>
                <option value="SIGNATURE_REJECTED">SIGNATURE_REJECTED</option>
                <option value="REPLAY_REJECTED">REPLAY_REJECTED</option>
                <option value="SCOPE_REJECTED">SCOPE_REJECTED</option>
                <option value="PERMISSION_REJECTED">PERMISSION_REJECTED</option>
                <option value="IP_REJECTED">IP_REJECTED</option>
                <option value="RATE_REJECTED">RATE_REJECTED</option>
                <option value="FAILED">FAILED</option>
              </select>
              <label for="openapi-call-log-method">方法</label>
              <select id="openapi-call-log-method" class="openapi-call-log-method" :value="callLogMethod" @change="changeCallLogMethod">
                <option value="ALL">全部方法</option>
                <option value="GET">GET</option>
                <option value="HEAD">HEAD</option>
                <option value="POST">POST</option>
                <option value="PUT">PUT</option>
                <option value="PATCH">PATCH</option>
                <option value="DELETE">DELETE</option>
                <option value="OPTIONS">OPTIONS</option>
              </select>
            </div>
            <a-alert v-if="callLogError" class="openapi-call-log-error" type="error" show-icon :message="callLogError" />
            <div v-if="callLogLoading" class="openapi-call-log-loading"><a-spin />正在加载调用日志…</div>
            <a-empty v-else-if="!callLogs.length" class="openapi-call-log-empty" description="当前筛选条件下暂无调用日志" />
            <div v-else class="openapi-call-log-list">
              <article v-for="log in callLogs" :key="log.id" class="openapi-call-log-card">
                <header>
                  <div><strong>{{ log.requestMethod }} {{ log.routeTemplate }}</strong><small>日志 {{ log.id }}</small></div>
                  <a-tag>{{ log.resultCategory }}</a-tag>
                </header>
                <dl>
                  <div><dt>HTTP 状态</dt><dd>{{ log.httpStatus }}</dd></div>
                  <div><dt>耗时</dt><dd>{{ log.latencyMs }} ms</dd></div>
                  <div><dt>凭据版本</dt><dd>{{ log.credentialVersion === null ? '未识别' : `v${log.credentialVersion}` }}</dd></div>
                  <div><dt>来源 IP</dt><dd>{{ log.observedIp }}</dd></div>
                  <div><dt>发生时间</dt><dd>{{ formatTime(log.createdAt) }}</dd></div>
                </dl>
                <p><span>Request ID</span><code>{{ log.requestId }}</code></p>
                <p><span>Trace ID</span><code>{{ log.traceId }}</code></p>
              </article>
            </div>
            <footer v-if="callLogTotal > callLogPageSize" class="openapi-call-log-pagination">
              <a-button class="openapi-call-log-previous" :disabled="callLogPage <= 1 || callLogLoading" @click="previousCallLogPage">上一页</a-button>
              <span>第 {{ callLogPage }} / {{ callLogPageCount }} 页，共 {{ callLogTotal }} 条</span>
              <a-button class="openapi-call-log-next" :disabled="callLogPage >= callLogPageCount || callLogLoading" @click="nextCallLogPage">下一页</a-button>
            </footer>
          </section>
          <section class="openapi-request-examples" aria-label="记录签名请求示例">
            <header>
              <div>
                <h3>记录签名请求示例</h3>
                <p>示例全部使用占位符。请在调用端解析并使用 <code>&lt;SECRET&gt;</code> 完成签名，不要发送密钥，也不要把 SecretRef 当作密钥。</p>
              </div>
            </header>
            <a-alert v-if="copyError" class="request-copy-error" type="error" show-icon :message="copyError" />
            <article
              v-for="example in recordRequestExamples"
              :key="example.id"
              class="openapi-request-example"
              :data-example="example.id"
            >
              <div class="request-example-heading">
                <strong>{{ example.title }}</strong>
                <a-button
                  class="request-example-copy"
                  size="small"
                  @click="copyRequestExample(example.id, example.request)"
                >
                  <Copy :size="14" />{{ copiedExample === example.id ? '已复制' : '复制' }}
                </a-button>
              </div>
              <pre><code>{{ example.request }}</code></pre>
            </article>
          </section>
        </template>
      </a-spin>
    </a-drawer>

    <a-modal
      v-model:open="createOpen"
      class="openapi-create-modal"
      title="新增开放应用"
      :confirm-loading="mutation === 'create'"
      @ok="createApplication"
    >
      <a-form layout="vertical">
        <a-form-item label="应用名称" required><a-input v-model:value="createForm.name" class="openapi-name" maxlength="120" /></a-form-item>
        <a-form-item label="固定租户 ID" required><a-input v-model:value="createForm.tenantId" class="openapi-tenant" maxlength="64" /></a-form-item>
        <a-form-item label="服务成员 ID" required><a-input v-model:value="createForm.serviceMemberId" class="openapi-service-member" maxlength="64" /></a-form-item>
        <a-form-item label="SecretRef" required>
          <a-input v-model:value="createForm.secretRef" class="openapi-secret-ref" maxlength="500" placeholder="例如 vault://openapi/app-a/v1" />
        </a-form-item>
        <a-form-item label="Scopes（每行一个）"><a-textarea v-model:value="createForm.scopesText" class="openapi-scopes" :rows="4" /></a-form-item>
        <a-form-item label="IP 白名单（每行一个 IP 或 CIDR）">
          <a-textarea v-model:value="createForm.ipAllowlistText" class="openapi-ip-allowlist" :rows="4" />
          <small>留空会拒绝所有外部调用。</small>
        </a-form-item>
        <a-form-item label="每分钟限流" required><a-input-number v-model:value="createForm.rateLimitPerMinute" class="openapi-rate" :min="1" :max="60000" :precision="0" /></a-form-item>
      </a-form>
      <a-alert v-if="mutationError" class="openapi-mutation-error" type="error" show-icon :message="mutationError" />
    </a-modal>

    <a-modal
      v-model:open="policyOpen"
      class="openapi-policy-modal"
      title="编辑调用策略"
      :confirm-loading="mutation.startsWith('policy:')"
      @ok="savePolicy"
    >
      <a-form layout="vertical">
        <a-form-item label="Scopes（每行一个）"><a-textarea v-model:value="policyForm.scopesText" class="policy-scopes" :rows="5" /></a-form-item>
        <a-form-item label="IP 白名单（每行一个 IP 或 CIDR）">
          <a-textarea v-model:value="policyForm.ipAllowlistText" class="policy-ip-allowlist" :rows="5" />
          <small>空白名单会拒绝所有外部调用。</small>
        </a-form-item>
        <a-form-item label="每分钟限流" required><a-input-number v-model:value="policyForm.rateLimitPerMinute" class="policy-rate" :min="1" :max="60000" :precision="0" /></a-form-item>
      </a-form>
      <a-alert v-if="mutationError" class="openapi-mutation-error" type="error" show-icon :message="mutationError" />
    </a-modal>

    <a-modal
      v-model:open="rotateOpen"
      class="openapi-rotate-modal"
      title="轮换 SecretRef"
      :confirm-loading="mutation.startsWith('rotate:')"
      @ok="rotateSecretRef"
    >
      <p>只提交新的密钥引用。系统不会读取或展示该引用解析出的明文密钥。</p>
      <a-form layout="vertical">
        <a-form-item label="新 SecretRef" required>
          <a-input v-model:value="rotateForm.secretRef" class="rotate-secret-ref" maxlength="500" />
        </a-form-item>
      </a-form>
      <a-alert v-if="mutationError" class="openapi-mutation-error" type="error" show-icon :message="mutationError" />
    </a-modal>

    <a-modal
      v-model:open="statusOpen"
      class="openapi-status-modal"
      :title="statusCommand === 'enable' ? '启用开放应用' : '停用开放应用'"
      :confirm-loading="mutation.startsWith('status:')"
      @ok="changeStatus"
    >
      <p>对象：{{ selected?.name }}</p>
      <a-form layout="vertical">
        <a-form-item label="变更原因" required>
          <a-textarea v-model:value="statusForm.reason" class="status-reason" :rows="4" maxlength="500" />
        </a-form-item>
      </a-form>
      <a-alert v-if="mutationError" class="openapi-mutation-error" type="error" show-icon :message="mutationError" />
    </a-modal>
  </section>
</template>

<style scoped>
.secret-boundary-notice {
  margin-bottom: 14px;
}

.openapi-mobile-gate {
  min-width: 0;
  padding: 4px 0 28px;
}

.openapi-mobile-gate .ant-empty {
  padding: 30px 12px;
  border: 1px solid #dce3e7;
  border-radius: 8px;
  background: #fff;
}

.openapi-application-link {
  display: block;
  max-width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: #245b87;
  text-align: left;
  cursor: pointer;
}

.openapi-detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e1e6e9;
}

.openapi-detail-list {
  margin: 0;
}

.openapi-detail-list > div {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px solid #e5eaed;
}

.openapi-detail-list dt {
  color: #697781;
}

.openapi-detail-list dd {
  margin: 0;
  overflow-wrap: anywhere;
}

.openapi-request-examples {
  display: grid;
  gap: 12px;
  margin-top: 20px;
}

.openapi-call-logs {
  display: grid;
  gap: 12px;
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid #e1e6e9;
}

.openapi-call-log-heading,
.openapi-call-log-filters,
.openapi-call-log-card > header,
.openapi-call-log-pagination {
  display: flex;
  align-items: center;
  gap: 9px;
}

.openapi-call-log-heading,
.openapi-call-log-card > header {
  justify-content: space-between;
}

.openapi-call-log-heading h3,
.openapi-call-log-heading p,
.openapi-call-log-card p {
  margin: 0;
}

.openapi-call-log-heading p,
.openapi-call-log-card small,
.openapi-call-log-card p span,
.openapi-call-log-pagination span {
  color: #697781;
  font-size: 12px;
}

.openapi-call-log-filters label {
  color: #697781;
  font-size: 13px;
}

.openapi-call-log-filters select {
  min-width: 130px;
  padding: 7px 9px;
  border: 1px solid #ccd5da;
  border-radius: 6px;
  background: #fff;
}

.openapi-call-log-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 100px;
  color: #697781;
}

.openapi-call-log-list {
  display: grid;
  gap: 9px;
}

.openapi-call-log-card {
  display: grid;
  gap: 9px;
  padding: 12px;
  border: 1px solid #e1e6e9;
  border-radius: 7px;
}

.openapi-call-log-card > header > div {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.openapi-call-log-card > header strong,
.openapi-call-log-card code {
  overflow-wrap: anywhere;
}

.openapi-call-log-card dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
}

.openapi-call-log-card dl > div {
  display: grid;
  gap: 2px;
}

.openapi-call-log-card dt {
  color: #697781;
  font-size: 12px;
}

.openapi-call-log-card dd {
  margin: 0;
}

.openapi-call-log-card p {
  display: grid;
  grid-template-columns: 80px minmax(0, 1fr);
  gap: 8px;
}

.openapi-call-log-pagination {
  justify-content: flex-end;
}

.openapi-request-examples h3,
.openapi-request-examples p {
  margin: 0;
}

.openapi-request-examples p {
  margin-top: 4px;
  color: #697781;
  font-size: 12px;
}

.openapi-request-example {
  min-width: 0;
  border: 1px solid #e1e6e9;
  border-radius: 8px;
  overflow: hidden;
}

.request-example-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 10px;
  background: #f6f8f9;
}

.openapi-request-example pre {
  margin: 0;
  padding: 12px;
  overflow: auto;
  background: #172127;
  color: #eaf3f5;
  font-size: 12px;
  line-height: 1.55;
}

.request-copy-error {
  margin: 0;
}

.openapi-mutation-error {
  margin-top: 12px;
}

.openapi-rate,
.policy-rate {
  width: 100%;
}
</style>
