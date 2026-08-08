<script setup lang="ts">
import { Bot, MessageSquarePlus, RefreshCw, RotateCcw, Send, UserRound } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiRequestError } from '@/services/api'
import { aiRuntimeApi } from '@/services/ai'
import type { AiCapability, AiConfirmation, AiConfigurationArtifactProposal, AiConfigurationFieldProposal, AiConfigurationFieldSettings, AiGeneratedDraftProposal, AiSession, AiSessionDetail, AiTurn, AiWorkProposal } from '@/types/ai'

const route = useRoute()
const router = useRouter()
const systemId = computed(() => String(route.params.systemId))
const capability = ref<AiCapability | null>(null)
const sessions = ref<AiSession[]>([])
const hasMoreSessions = ref(false)
const detail = ref<AiSessionDetail | null>(null)
const selectedSessionId = ref('')
const newTitle = ref('')
const messageText = ref('')
const lastSubmitted = ref('')
const loading = ref(false)
const detailLoading = ref(false)
const creating = ref(false)
const submitting = ref(false)
const loadError = ref('')
const submitError = ref('')
const retryable = ref(false)
const confirmationActions = reactive<Record<string, 'confirm' | 'reject' | undefined>>({})
const confirmationErrors = reactive<Record<string, string | undefined>>({})
const confirmationKeys = new Map<string, string>()
const configurationActions = reactive<Record<string, 'confirm' | 'reject' | undefined>>({})
const configurationErrors = reactive<Record<string, string | undefined>>({})
const configurationKeys = new Map<string, string>()
const artifactActions = reactive<Record<string, 'confirm' | 'reject' | undefined>>({})
const artifactErrors = reactive<Record<string, string | undefined>>({})
const artifactKeys = new Map<string, string>()
const workActions = reactive<Record<string, 'confirm' | 'reject' | undefined>>({})
const workErrors = reactive<Record<string, string | undefined>>({})
const workKeys = new Map<string, string>()
const generatedDraftActions = reactive<Record<string, 'confirm' | 'reject' | undefined>>({})
const generatedDraftErrors = reactive<Record<string, string | undefined>>({})
const generatedDraftKeys = new Map<string, string>()

const unavailableState = computed(() => {
  if (capability.value?.available !== false) return null
  const reason = capability.value.reason ?? 'UNCONFIGURED'
  return reason.includes('DISABLED')
    ? { title: 'Agent 已停用', description: '管理员已停用当前租户的 Agent 策略。', reason }
    : { title: 'Agent 尚未配置', description: '请联系系统管理员配置 Provider 并发布可用策略。', reason }
})

function requestError(error: unknown, fallback: string) {
  return error instanceof ApiRequestError
    ? error.message || error.code
    : error instanceof Error ? error.message : fallback
}

function errorCode(error: unknown) {
  return error instanceof ApiRequestError ? error.code : error instanceof Error ? error.message : ''
}

function isRetryable(code: string) {
  return /(TIMEOUT|UNAVAILABLE|RETRYABLE|PROVIDER_FAILED)/iu.test(code)
}

function formatTime(value: string) {
  const parsed = new Date(value)
  return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleString('zh-CN')
}

function roleLabel(role: string) {
  return role === 'USER' ? '我' : 'Agent'
}

function contextOperationLabel(operation: string) {
  const labels: Record<string, string> = {
    RECORD_CONTEXT_SUMMARY: '记录上下文摘要',
    RECORD_COMMENT_QUERY: '记录评论查询',
    RECORD_HISTORY_QUERY: '记录历史查询',
    RECORD_FILE_QUERY: '记录附件查询',
    WORK_TASK_QUERY: '工作任务查询',
    WORK_DAILY_REPORT_QUERY: '工作日报查询',
    WORK_PROJECT_METRICS_QUERY: '项目进度与指标查询',
    RUNTIME_STATISTICS_QUERY: '运行时统计与趋势查询',
    RUNTIME_REPORT_QUERY: '运行时报表查询',
    FLOW_INSTANCE_HISTORY_QUERY: '审批实例历史查询',
    TODO_QUERY: '当前待办查询',
    MESSAGE_QUERY: '系统消息查询',
  }
  return labels[operation] ?? operation
}

function confirmationState(value: AiConfirmation) {
  if (value.state === 'PENDING' && new Date(value.expiresAt).valueOf() <= Date.now()) return 'EXPIRED'
  return value.state
}

function confirmationStateLabel(value: AiConfirmation) {
  const labels: Record<string, string> = {
    PENDING: '等待确认', EXECUTING: '正在执行', SUCCEEDED: '已执行',
    REJECTED: '已拒绝', EXPIRED: '已过期',
    FAILED: '执行失败', CONFLICTED: '版本冲突', CLARIFICATION_REQUIRED: '需要补充信息',
  }
  return labels[confirmationState(value)] ?? confirmationState(value)
}

function confirmationStateColor(value: AiConfirmation) {
  const state = confirmationState(value)
  if (state === 'SUCCEEDED') return 'green'
  if (state === 'PENDING') return 'orange'
  if (state === 'REJECTED') return 'default'
  return 'red'
}

function canConfirm(value: AiConfirmation) {
  return confirmationState(value) === 'PENDING' && value.clarifications.length === 0
}

function canReject(value: AiConfirmation) {
  return confirmationState(value) === 'PENDING'
}

function confidence(value: number | null) {
  if (value === null || !Number.isFinite(value)) return '未提供'
  const percent = value <= 1 ? value * 100 : value
  return `${Math.round(percent)}%`
}

function progressPercent(completed: number, total: number) {
  if (!Number.isFinite(total) || total <= 0) return '0%'
  return `${Math.round((completed / total) * 1000) / 10}%`
}

function formatFileSize(value: number) {
  if (!Number.isFinite(value) || value < 0) return String(value)
  if (value < 1024) return `${value} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let size = value / 1024
  let unit = 0
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024
    unit += 1
  }
  return `${Math.round(size * 10) / 10} ${units[unit]}`
}

function preview(value: string | null) {
  if (value === null) return '—'
  return value === '' ? '（空值）' : value
}

function replaceConfirmation(value: AiConfirmation) {
  if (!detail.value) return
  detail.value = {
    ...detail.value,
    turns: detail.value.turns.map(turn => turn.confirmation?.id === value.id
      ? { ...turn, confirmation: value }
      : turn),
  }
}

async function refreshConfirmation(value: AiConfirmation) {
  const latest = await aiRuntimeApi.confirmation(systemId.value, value.id)
  replaceConfirmation(latest)
  return latest
}

function confirmationCodeLabel(code: string) {
  if (/PERMISSION|MEMBER|TENANT|CONTEXT/iu.test(code)) return '当前权限或成员上下文已变化，本次写入未执行。'
  if (/RECORD_VERSION|VERSION|CONFLICT|REPLAY/iu.test(code)) return '记录或提案版本已变化，本次写入未执行。'
  if (/EXPIRED/iu.test(code)) return '提案已过期，本次写入未执行。'
  if (/REJECTED|CONSUMED|STATE/iu.test(code)) return '提案已结束，不能再次执行。'
  return code ? `执行失败（${code}），业务记录未被写入。` : '确认执行失败，业务记录未被写入。'
}

function confirmationFailure(error: unknown) {
  const code = errorCode(error)
  if (code) return confirmationCodeLabel(code)
  return requestError(error, '确认执行失败，业务记录未被写入。')
}

async function confirmProposal(value: AiConfirmation) {
  if (!canConfirm(value) || confirmationActions[value.id]) return
  confirmationActions[value.id] = 'confirm'
  confirmationErrors[value.id] = undefined
  const idempotencyKey = confirmationKeys.get(value.id) ?? crypto.randomUUID()
  confirmationKeys.set(value.id, idempotencyKey)
  try {
    replaceConfirmation(await aiRuntimeApi.confirm(
      systemId.value, value.id, value.version, idempotencyKey,
    ))
  } catch (error) {
    confirmationErrors[value.id] = confirmationFailure(error)
    try { await refreshConfirmation(value) } catch { /* retain the safe submitted preview */ }
  } finally {
    confirmationActions[value.id] = undefined
  }
}

async function rejectProposal(value: AiConfirmation) {
  if (confirmationState(value) !== 'PENDING' || confirmationActions[value.id]) return
  confirmationActions[value.id] = 'reject'
  confirmationErrors[value.id] = undefined
  try {
    replaceConfirmation(await aiRuntimeApi.reject(systemId.value, value.id, value.version))
  } catch (error) {
    confirmationErrors[value.id] = confirmationFailure(error)
    try { await refreshConfirmation(value) } catch { /* retain the safe submitted preview */ }
  } finally {
    confirmationActions[value.id] = undefined
  }
}

function configurationState(value: AiConfigurationFieldProposal) {
  if (value.state === 'PENDING' && new Date(value.expiresAt).valueOf() <= Date.now()) return 'EXPIRED'
  return value.state
}

function configurationStateLabel(value: AiConfigurationFieldProposal) {
  const labels: Record<string, string> = {
    CLARIFICATION_REQUIRED: '需要补充信息', PENDING: '等待确认', EXECUTING: '正在添加字段',
    SUCCEEDED: '已加入配置草稿', FAILED: '执行失败', REJECTED: '已拒绝', EXPIRED: '已过期',
  }
  return labels[configurationState(value)] ?? configurationState(value)
}

function configurationStateColor(value: AiConfigurationFieldProposal) {
  const state = configurationState(value)
  if (state === 'SUCCEEDED') return 'green'
  if (state === 'PENDING') return 'orange'
  if (state === 'REJECTED') return 'default'
  return 'red'
}

function canConfirmConfiguration(value: AiConfigurationFieldProposal) {
  return configurationState(value) === 'PENDING' && Boolean(value.preview) && !value.clarification
}

function canRejectConfiguration(value: AiConfigurationFieldProposal) {
  return configurationState(value) === 'PENDING'
}

function configurationSettings(value: { settings: AiConfigurationFieldSettings } | null) {
  if (!value) return []
  const settings = value.settings
  const entries: string[] = []
  if (settings.maxLength !== null) entries.push(`最大长度 ${settings.maxLength}`)
  if (settings.precision !== null) entries.push(`精度 ${settings.precision}`)
  if (settings.scale !== null) entries.push(`小数位 ${settings.scale}`)
  if (settings.minimum !== null) entries.push(`最小值 ${settings.minimum}`)
  if (settings.maximum !== null) entries.push(`最大值 ${settings.maximum}`)
  return entries
}

function replaceConfigurationProposal(value: AiConfigurationFieldProposal) {
  if (!detail.value) return
  detail.value = {
    ...detail.value,
    turns: detail.value.turns.map(turn => turn.configurationProposal?.id === value.id
      ? { ...turn, configurationProposal: value }
      : turn),
  }
}

async function refreshConfigurationProposal(value: AiConfigurationFieldProposal) {
  const latest = await aiRuntimeApi.configurationProposal(systemId.value, value.sessionId, value.id)
  replaceConfigurationProposal(latest)
  return latest
}

function configurationCodeLabel(code: string) {
  if (/PERMISSION|FORBIDDEN|MEMBER|TENANT|CONTEXT|AUTHORIZATION/iu.test(code)) {
    return '当前配置权限或成员上下文已变化，字段未写入配置草稿。'
  }
  if (/STALE|DRAFT_REVISION|CONFIG(?:URATION)?_REVISION/iu.test(code)) {
    return '配置草稿版本已变化，字段未写入；请重新生成提案。'
  }
  if (/REVISION|VERSION|CONFLICT|REPLAY|IDEMPOTENCY/iu.test(code)) {
    return '提案版本或确认请求冲突，字段未重复写入配置草稿。'
  }
  if (/EXPIRED/iu.test(code)) return '配置字段提案已过期，字段未写入配置草稿。'
  if (/REJECTED|CONSUMED|STATE/iu.test(code)) return '配置字段提案已结束，不能再次执行。'
  return code ? `配置字段执行失败（${code}），字段未写入配置草稿。` : '确认失败，字段未写入配置草稿。'
}

function configurationFailure(error: unknown) {
  const code = errorCode(error)
  if (code) return configurationCodeLabel(code)
  return requestError(error, '确认失败，字段未写入配置草稿。')
}

async function confirmConfigurationProposal(value: AiConfigurationFieldProposal) {
  if (!canConfirmConfiguration(value) || configurationActions[value.id]) return
  configurationActions[value.id] = 'confirm'
  configurationErrors[value.id] = undefined
  const idempotencyKey = configurationKeys.get(value.id) ?? crypto.randomUUID()
  configurationKeys.set(value.id, idempotencyKey)
  try {
    replaceConfigurationProposal(await aiRuntimeApi.confirmConfigurationProposal(
      systemId.value, value.sessionId, value.id, value.revision, idempotencyKey,
    ))
  } catch (error) {
    configurationErrors[value.id] = configurationFailure(error)
    try { await refreshConfigurationProposal(value) } catch { /* retain the safe configuration preview */ }
  } finally {
    configurationActions[value.id] = undefined
  }
}

async function rejectConfigurationProposal(value: AiConfigurationFieldProposal) {
  if (!canRejectConfiguration(value) || configurationActions[value.id]) return
  configurationActions[value.id] = 'reject'
  configurationErrors[value.id] = undefined
  try {
    replaceConfigurationProposal(await aiRuntimeApi.rejectConfigurationProposal(
      systemId.value, value.sessionId, value.id, value.revision,
    ))
  } catch (error) {
    configurationErrors[value.id] = configurationFailure(error)
    try { await refreshConfigurationProposal(value) } catch { /* retain the safe configuration preview */ }
  } finally {
    configurationActions[value.id] = undefined
  }
}

function artifactState(value: AiConfigurationArtifactProposal) {
  if (value.state === 'PENDING' && new Date(value.expiresAt).valueOf() <= Date.now()) return 'EXPIRED'
  return value.state
}

function artifactStateLabel(value: AiConfigurationArtifactProposal) {
  const labels: Record<string, string> = {
    CLARIFICATION_REQUIRED: '需要补充信息', PENDING: '等待确认', EXECUTING: '正在更新配置草稿',
    SUCCEEDED: '配置草稿已更新', FAILED: '执行失败', REJECTED: '已拒绝', EXPIRED: '已过期',
  }
  return labels[artifactState(value)] ?? artifactState(value)
}

function artifactStateColor(value: AiConfigurationArtifactProposal) {
  const state = artifactState(value)
  if (state === 'SUCCEEDED') return 'green'
  if (state === 'PENDING' || state === 'EXECUTING') return 'orange'
  if (state === 'REJECTED') return 'default'
  return 'red'
}

function artifactKindLabel(value: AiConfigurationArtifactProposal) {
  const labels = {
    SELECTION_FIELD: '选择字段与选项字典',
    PAGE_LAYOUT: '页面布局',
    FILTER_SCENARIO: '共享筛选方案',
    FIELD_PERMISSION_STAGE: '字段权限 STAGED',
  }
  return labels[value.artifactKind]
}

function artifactKindClass(value: AiConfigurationArtifactProposal) {
  const classes = {
    SELECTION_FIELD: 'config-selection-field-proposal',
    PAGE_LAYOUT: 'config-page-layout-proposal',
    FILTER_SCENARIO: 'config-filter-scenario-proposal',
    FIELD_PERMISSION_STAGE: 'config-field-permission-stage-proposal',
  }
  return classes[value.artifactKind]
}

function canonicalJson(value: unknown) {
  return JSON.stringify(value, null, 2)
}

function artifactStudioTarget(value: AiConfigurationArtifactProposal) {
  const filterScenario = value.result?.filterScenario ?? value.preview?.filterScenario
  if (filterScenario) return { resourceKind: 'page' as const, resourceCode: filterScenario.pageCode }
  const fieldPermission = value.result?.fieldPermissionStage ?? value.preview?.fieldPermissionStage
  if (fieldPermission) return { resourceKind: 'field' as const, resourceCode: fieldPermission.fieldCode }
  return null
}

function openArtifactInConfiguration(value: AiConfigurationArtifactProposal) {
  const target = artifactStudioTarget(value)
  const moduleCode = value.result?.moduleCode ?? value.preview?.moduleCode ?? value.moduleCode
  if (!target || !moduleCode) return
  router.push({
    name: 'system-admin-configuration',
    params: { systemId: systemId.value },
    query: { moduleCode, resourceKind: target.resourceKind, resourceCode: target.resourceCode },
  })
}

function canConfirmArtifact(value: AiConfigurationArtifactProposal) {
  return artifactState(value) === 'PENDING' && Boolean(value.preview) && !value.clarification
}

function canRejectArtifact(value: AiConfigurationArtifactProposal) {
  return artifactState(value) === 'PENDING'
}

function replaceArtifactProposal(value: AiConfigurationArtifactProposal) {
  if (!detail.value) return
  detail.value = {
    ...detail.value,
    turns: detail.value.turns.map(turn => turn.artifactProposal?.id === value.id
      ? { ...turn, artifactProposal: value }
      : turn),
  }
}

async function refreshArtifactProposal(value: AiConfigurationArtifactProposal) {
  const latest = await aiRuntimeApi.configurationArtifactProposal(systemId.value, value.sessionId, value.id)
  replaceArtifactProposal(latest)
  return latest
}

function artifactCodeLabel(code: string) {
  if (/PERMISSION|FORBIDDEN|MEMBER|TENANT|CONTEXT|AUTHORIZATION/iu.test(code)) {
    return '当前配置权限或成员上下文已变化，配置草稿未更新。'
  }
  if (/STALE|DRAFT_REVISION|CONFIG(?:URATION)?_REVISION/iu.test(code)) {
    return '配置草稿版本已变化，本提案未执行；请重新生成。'
  }
  if (/REVISION|VERSION|CONFLICT|REPLAY|IDEMPOTENCY/iu.test(code)) {
    return '提案版本或确认请求冲突，配置草稿未被重复更新。'
  }
  if (/EXPIRED/iu.test(code)) return '配置工件提案已过期，配置草稿未更新。'
  if (/REJECTED|CONSUMED|STATE/iu.test(code)) return '配置工件提案已结束，不能再次执行。'
  return code ? `配置工件执行失败（${code}），配置草稿未更新。` : '确认失败，配置草稿未更新。'
}

function artifactFailure(error: unknown) {
  const code = errorCode(error)
  if (code) return artifactCodeLabel(code)
  return requestError(error, '确认失败，配置草稿未更新。')
}

async function confirmArtifactProposal(value: AiConfigurationArtifactProposal) {
  if (!canConfirmArtifact(value) || artifactActions[value.id]) return
  artifactActions[value.id] = 'confirm'
  artifactErrors[value.id] = undefined
  const idempotencyKey = artifactKeys.get(value.id) ?? crypto.randomUUID()
  artifactKeys.set(value.id, idempotencyKey)
  try {
    replaceArtifactProposal(await aiRuntimeApi.confirmConfigurationArtifactProposal(
      systemId.value, value.sessionId, value.id, value.revision, idempotencyKey,
    ))
  } catch (error) {
    artifactErrors[value.id] = artifactFailure(error)
    try { await refreshArtifactProposal(value) } catch { /* retain the safe artifact preview */ }
  } finally {
    artifactActions[value.id] = undefined
  }
}

async function rejectArtifactProposal(value: AiConfigurationArtifactProposal) {
  if (!canRejectArtifact(value) || artifactActions[value.id]) return
  artifactActions[value.id] = 'reject'
  artifactErrors[value.id] = undefined
  try {
    replaceArtifactProposal(await aiRuntimeApi.rejectConfigurationArtifactProposal(
      systemId.value, value.sessionId, value.id, value.revision,
    ))
  } catch (error) {
    artifactErrors[value.id] = artifactFailure(error)
    try { await refreshArtifactProposal(value) } catch { /* retain the safe artifact preview */ }
  } finally {
    artifactActions[value.id] = undefined
  }
}

function workState(value: AiWorkProposal) {
  if (value.state === 'PENDING' && new Date(value.expiresAt).valueOf() <= Date.now()) return 'EXPIRED'
  return value.state
}

function workStateLabel(value: AiWorkProposal) {
  const labels: Record<string, string> = {
    CLARIFICATION_REQUIRED: '需要补充信息', PENDING: '等待确认', EXECUTING: '正在创建',
    SUCCEEDED: '已创建', FAILED: '创建失败', REJECTED: '已拒绝', EXPIRED: '已过期',
    STALE: '业务数据已变化', PERMISSION_DENIED: '权限已失效',
  }
  return labels[workState(value)] ?? workState(value)
}

function workStateColor(value: AiWorkProposal) {
  const state = workState(value)
  if (state === 'SUCCEEDED') return 'green'
  if (state === 'PENDING' || state === 'EXECUTING') return 'orange'
  if (state === 'REJECTED') return 'default'
  return 'red'
}

function canConfirmWork(value: AiWorkProposal) {
  return workState(value) === 'PENDING' && Boolean(value.preview) && !value.clarification
}

function canRejectWork(value: AiWorkProposal) {
  return workState(value) === 'PENDING'
}

function replaceWorkProposal(value: AiWorkProposal) {
  if (!detail.value) return
  detail.value = {
    ...detail.value,
    turns: detail.value.turns.map(turn => turn.workProposal?.id === value.id
      ? { ...turn, workProposal: value }
      : turn),
  }
}

async function refreshWorkProposal(value: AiWorkProposal) {
  const latest = await aiRuntimeApi.workProposal(systemId.value, value.sessionId, value.id)
  replaceWorkProposal(latest)
  return latest
}

function workCodeLabel(code: string) {
  if (/PERMISSION|FORBIDDEN|MEMBER|TENANT|CONTEXT|AUTHORIZATION/iu.test(code)) {
    return '当前工作权限或成员上下文已变化，任务或日报未创建。'
  }
  if (/STALE|EXISTING|DUPLICATE|PROJECT|ASSIGNEE/iu.test(code)) {
    return '负责人、项目或同日工作数据已变化，本提案未创建任何内容。'
  }
  if (/REVISION|VERSION|CONFLICT|REPLAY|IDEMPOTENCY/iu.test(code)) {
    return '提案版本或确认请求冲突，任务或日报未被重复创建。'
  }
  if (/EXPIRED/iu.test(code)) return '工作草稿提案已过期，任务或日报未创建。'
  if (/REJECTED|CONSUMED|STATE/iu.test(code)) return '工作草稿提案已结束，不能再次执行。'
  return code ? `工作草稿创建失败（${code}），未创建任何内容。` : '确认失败，任务或日报未创建。'
}

function workFailure(error: unknown) {
  const code = errorCode(error)
  if (code) return workCodeLabel(code)
  return requestError(error, '确认失败，任务或日报未创建。')
}

async function confirmWorkProposal(value: AiWorkProposal) {
  if (!canConfirmWork(value) || workActions[value.id]) return
  workActions[value.id] = 'confirm'
  workErrors[value.id] = undefined
  const idempotencyKey = workKeys.get(value.id) ?? crypto.randomUUID()
  workKeys.set(value.id, idempotencyKey)
  try {
    replaceWorkProposal(await aiRuntimeApi.confirmWorkProposal(
      systemId.value, value.sessionId, value.id, value.revision, idempotencyKey,
    ))
  } catch (error) {
    workErrors[value.id] = workFailure(error)
    try { await refreshWorkProposal(value) } catch { /* retain the safe Work preview */ }
  } finally {
    workActions[value.id] = undefined
  }
}

async function rejectWorkProposal(value: AiWorkProposal) {
  if (!canRejectWork(value) || workActions[value.id]) return
  workActions[value.id] = 'reject'
  workErrors[value.id] = undefined
  try {
    replaceWorkProposal(await aiRuntimeApi.rejectWorkProposal(
      systemId.value, value.sessionId, value.id, value.revision,
    ))
  } catch (error) {
    workErrors[value.id] = workFailure(error)
    try { await refreshWorkProposal(value) } catch { /* retain the safe Work preview */ }
  } finally {
    workActions[value.id] = undefined
  }
}

function generatedDraftState(value: AiGeneratedDraftProposal) {
  if (value.state === 'PENDING' && new Date(value.expiresAt).valueOf() <= Date.now()) return 'EXPIRED'
  return value.state
}

function generatedDraftStateLabel(value: AiGeneratedDraftProposal) {
  const labels: Record<string, string> = {
    CLARIFICATION_REQUIRED: '需要补充信息', PENDING: '等待确认', EXECUTING: '正在创建',
    SUCCEEDED: '草稿已创建', FAILED: '创建失败', REJECTED: '已拒绝', EXPIRED: '已过期',
    STALE: '依赖数据已变化', PERMISSION_DENIED: '权限已失效',
  }
  return labels[generatedDraftState(value)] ?? generatedDraftState(value)
}

function generatedDraftStateColor(value: AiGeneratedDraftProposal) {
  const state = generatedDraftState(value)
  if (state === 'SUCCEEDED') return 'green'
  if (state === 'PENDING' || state === 'EXECUTING') return 'orange'
  if (state === 'REJECTED') return 'default'
  return 'red'
}

function generatedDraftKindLabel(value: AiGeneratedDraftProposal) {
  if (value.operation === 'FLOW_DEFINITION_DRAFT') return 'Flow 定义'
  if (value.operation === 'CONFIG_REPORT_DRAFT') return '报表定义'
  return '打印模板'
}

function canConfirmGeneratedDraft(value: AiGeneratedDraftProposal) {
  return generatedDraftState(value) === 'PENDING' && Boolean(value.preview) && !value.clarification
}

function canRejectGeneratedDraft(value: AiGeneratedDraftProposal) {
  return generatedDraftState(value) === 'PENDING'
}

function replaceGeneratedDraftProposal(value: AiGeneratedDraftProposal) {
  if (!detail.value) return
  detail.value = {
    ...detail.value,
    turns: detail.value.turns.map(turn => turn.generatedDraftProposal?.id === value.id
      ? { ...turn, generatedDraftProposal: value }
      : turn),
  }
}

async function refreshGeneratedDraftProposal(value: AiGeneratedDraftProposal) {
  const latest = await aiRuntimeApi.generatedDraftProposal(systemId.value, value.sessionId, value.id)
  replaceGeneratedDraftProposal(latest)
  return latest
}

function generatedDraftCodeLabel(code: string) {
  if (/PERMISSION|FORBIDDEN|TENANT|MEMBER|CONTEXT|AUTHORIZATION/iu.test(code)) {
    return '当前权限或成员上下文已变化，Flow、报表或打印模板均未创建。'
  }
  if (/STALE|EXISTING|DUPLICATE|USED|DATA_SOURCE|MODULE|FIELD|APPROVER|CODE/iu.test(code)) {
    return '成员、模块、数据源、字段或编码已变化，本提案未创建任何草稿。'
  }
  if (/REVISION|VERSION|CONFLICT|REPLAY|IDEMPOTENCY/iu.test(code)) {
    return '提案版本或确认请求冲突，未重复创建 Flow、报表或打印模板。'
  }
  if (/EXPIRED/iu.test(code)) return '生成草稿提案已过期，未创建任何内容。'
  if (/REJECTED|CONSUMED|STATE/iu.test(code)) return '生成草稿提案已结束，不能再次执行。'
  return code ? `生成草稿失败（${code}），未创建任何内容。` : '确认失败，未创建 Flow、报表或打印模板。'
}

function generatedDraftFailure(error: unknown) {
  const code = errorCode(error)
  if (code) return generatedDraftCodeLabel(code)
  return requestError(error, '确认失败，未创建 Flow、报表或打印模板。')
}

async function confirmGeneratedDraftProposal(value: AiGeneratedDraftProposal) {
  if (!canConfirmGeneratedDraft(value) || generatedDraftActions[value.id]) return
  generatedDraftActions[value.id] = 'confirm'
  generatedDraftErrors[value.id] = undefined
  const idempotencyKey = generatedDraftKeys.get(value.id) ?? crypto.randomUUID()
  generatedDraftKeys.set(value.id, idempotencyKey)
  try {
    replaceGeneratedDraftProposal(await aiRuntimeApi.confirmGeneratedDraftProposal(
      systemId.value, value.sessionId, value.id, value.revision, idempotencyKey,
    ))
  } catch (error) {
    generatedDraftErrors[value.id] = generatedDraftFailure(error)
    try { await refreshGeneratedDraftProposal(value) } catch { /* retain the safe generated draft preview */ }
  } finally {
    generatedDraftActions[value.id] = undefined
  }
}

async function rejectGeneratedDraftProposal(value: AiGeneratedDraftProposal) {
  if (!canRejectGeneratedDraft(value) || generatedDraftActions[value.id]) return
  generatedDraftActions[value.id] = 'reject'
  generatedDraftErrors[value.id] = undefined
  try {
    replaceGeneratedDraftProposal(await aiRuntimeApi.rejectGeneratedDraftProposal(
      systemId.value, value.sessionId, value.id, value.revision,
    ))
  } catch (error) {
    generatedDraftErrors[value.id] = generatedDraftFailure(error)
    try { await refreshGeneratedDraftProposal(value) } catch { /* retain the safe generated draft preview */ }
  } finally {
    generatedDraftActions[value.id] = undefined
  }
}

async function load() {
  loading.value = true
  loadError.value = ''
  detail.value = null
  selectedSessionId.value = ''
  try {
    capability.value = await aiRuntimeApi.capability(systemId.value)
    if (!capability.value.available) {
      sessions.value = []
      hasMoreSessions.value = false
      return
    }
    const page = await aiRuntimeApi.sessions(systemId.value, 1, 50)
    sessions.value = page.rows
    hasMoreSessions.value = page.hasMore
    if (page.rows[0]) await selectSession(page.rows[0])
  } catch (error) {
    loadError.value = requestError(error, 'Agent 会话加载失败')
  } finally {
    loading.value = false
  }
}

async function selectSession(session: AiSession) {
  selectedSessionId.value = session.id
  detailLoading.value = true
  loadError.value = ''
  try {
    detail.value = await aiRuntimeApi.session(systemId.value, session.id)
  } catch (error) {
    loadError.value = requestError(error, '会话详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

async function createSession() {
  const title = newTitle.value.trim()
  if (!title) {
    loadError.value = '请输入会话标题。'
    return
  }
  creating.value = true
  loadError.value = ''
  try {
    const created = await aiRuntimeApi.createSession(systemId.value, { title })
    sessions.value = [created, ...sessions.value.filter(item => item.id !== created.id)]
    newTitle.value = ''
    await selectSession(created)
  } catch (error) {
    loadError.value = requestError(error, '会话创建失败')
  } finally {
    creating.value = false
  }
}

function appendTurn(turn: AiTurn) {
  if (!detail.value) return
  const index = detail.value.turns.findIndex(item => item.id === turn.id)
  detail.value = {
    ...detail.value,
    turns: index < 0
      ? [...detail.value.turns, turn]
      : detail.value.turns.map(item => item.id === turn.id ? turn : item),
  }
}

async function submit(content = messageText.value) {
  const value = content.trim()
  if (!selectedSessionId.value || !value || submitting.value) return
  lastSubmitted.value = value
  submitError.value = ''
  retryable.value = false
  submitting.value = true
  try {
    const turn = await aiRuntimeApi.submitMessage(
      systemId.value, selectedSessionId.value, { content: value },
    )
    appendTurn(turn)
    messageText.value = ''
    if (turn.status === 'FAILED' || turn.status === 'RETRYABLE') {
      submitError.value = turn.errorCode || 'Agent 本轮执行失败。'
      retryable.value = turn.retryable
    }
  } catch (error) {
    const code = errorCode(error)
    submitError.value = requestError(error, 'Agent 消息提交失败')
    retryable.value = isRetryable(code)
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="system-agent-page">
    <header class="agent-page-header">
      <div><h1><Bot :size="23" />Agent</h1><p>查询业务记录，或在查看精确变更预览后显式确认创建与更新。</p></div>
      <a-button :loading="loading" @click="load"><RefreshCw :size="16" />刷新</a-button>
    </header>

    <a-alert v-if="loadError" class="agent-load-error" type="error" show-icon :message="loadError">
      <template #action><a-button size="small" @click="load">重试</a-button></template>
    </a-alert>

    <a-spin :spinning="loading">
      <section v-if="unavailableState" class="agent-unavailable-state">
        <Bot :size="34" />
        <h2>{{ unavailableState.title }}</h2>
        <p>{{ unavailableState.description }}</p>
        <code>{{ unavailableState.reason }}</code>
      </section>

      <div v-else-if="capability?.available" class="agent-layout">
        <aside class="agent-session-sidebar">
          <header><div><strong>会话</strong><small>{{ sessions.length }}{{ hasMoreSessions ? '+' : '' }} 个</small></div><a-tag>策略 {{ capability.policyVersion }}</a-tag></header>
          <div class="agent-session-create">
            <input v-model="newTitle" class="agent-session-title" maxlength="120" placeholder="新会话标题" @keyup.enter="createSession" />
            <a-button class="agent-session-create-button" type="primary" :loading="creating" @click="createSession"><MessageSquarePlus :size="15" />创建</a-button>
          </div>
          <nav class="agent-session-list" aria-label="Agent 会话">
            <button
              v-for="session in sessions"
              :key="session.id"
              type="button"
              :class="{ active: selectedSessionId === session.id }"
              @click="selectSession(session)"
            >
              <strong>{{ session.title }}</strong><span>{{ session.status }} · {{ formatTime(session.updatedAt) }}</span>
            </button>
          </nav>
          <a-empty v-if="!sessions.length" description="暂无会话，请先创建" />
        </aside>

        <main class="agent-conversation">
          <a-spin :spinning="detailLoading">
            <template v-if="detail">
              <header class="agent-conversation-header"><div><h2>{{ detail.session.title }}</h2><span>{{ detail.session.status }}</span></div><small>记录写入 · 标量字段 · 选择字段 · 页面布局（配置仅更新草稿，均需确认）</small></header>
              <div class="agent-message-list">
                <article v-for="message in detail.messages" :key="message.id" class="agent-message" :class="message.role.toLowerCase()">
                  <header><span><UserRound v-if="message.role === 'USER'" :size="15" /><Bot v-else :size="15" />{{ roleLabel(message.role) }}</span></header>
                  <p>{{ message.content }}</p>
                  <time>{{ formatTime(message.createdAt) }}</time>
                </article>
                <article v-for="turn in detail.turns" :key="`turn:${turn.id}`" class="agent-turn" :class="turn.status.toLowerCase()">
                  <header><span><Bot :size="15" />执行记录</span><a-tag :color="turn.status === 'FAILED' ? 'red' : turn.status === 'SUCCEEDED' ? 'green' : 'orange'">{{ turn.status }}</a-tag></header>
                  <p>{{ turn.answer || turn.responseSummary || (turn.status === 'RUNNING' ? '处理中…' : '本轮未生成回答。') }}</p>
                  <div v-if="turn.tool" class="agent-tool-result">
                    <strong>RECORD_QUERY · {{ turn.tool.moduleCode }}</strong>
                    <span>返回 {{ turn.tool.returnedRows }} / {{ turn.tool.total }} 行</span>
                    <pre>{{ JSON.stringify(turn.tool.rows, null, 2) }}</pre>
                  </div>
                  <section v-if="turn.contextResult" class="agent-context-result" :class="turn.contextResult.operation.toLowerCase()">
                    <header><div><strong>{{ contextOperationLabel(turn.contextResult.operation) }}</strong><span>{{ turn.contextResult.operation }} · 只读安全投影</span></div><a-tag :color="turn.status === 'FAILED' ? 'red' : turn.status === 'RUNNING' ? 'orange' : 'green'">{{ turn.status }}</a-tag></header>
                    <a-alert v-if="turn.status === 'RUNNING'" class="agent-context-loading" type="info" show-icon message="正在读取实时上下文" description="结果会按当前成员权限与脱敏规则返回。" />
                    <a-alert v-if="turn.status === 'FAILED' || turn.status === 'RETRYABLE'" class="agent-context-error" type="error" show-icon message="上下文查询失败" :description="turn.errorCode || '本轮未返回安全上下文。'" />

                    <section v-if="turn.contextResult.operation === 'RECORD_CONTEXT_SUMMARY'" class="agent-record-context-result">
                      <a-empty v-if="!turn.contextResult.record" class="agent-context-empty" description="当前权限范围内没有可显示的记录上下文" />
                      <template v-else>
                        <header><div><strong>{{ turn.contextResult.record.title || turn.contextResult.record.recordNo || turn.contextResult.record.recordId || '记录上下文' }}</strong><span>{{ turn.contextResult.record.moduleCode || '未提供模块' }} · {{ turn.contextResult.record.status || '未提供状态' }}</span></div><a-tag v-if="turn.contextResult.record.recordVersion !== null && turn.contextResult.record.recordVersion !== undefined">v{{ turn.contextResult.record.recordVersion }}</a-tag></header>
                        <p v-if="turn.contextResult.record.summary">{{ turn.contextResult.record.summary }}</p>
                        <dl><div v-if="turn.contextResult.record.recordId"><dt>记录 ID</dt><dd>{{ turn.contextResult.record.recordId }}</dd></div><div v-if="turn.contextResult.record.recordNo"><dt>记录编号</dt><dd>{{ turn.contextResult.record.recordNo }}</dd></div><div v-if="turn.contextResult.record.updatedAt"><dt>更新时间</dt><dd>{{ formatTime(turn.contextResult.record.updatedAt) }}</dd></div></dl>
                        <div v-if="turn.contextResult.record.values && Object.keys(turn.contextResult.record.values).length" class="agent-record-context-values"><article v-for="(value, fieldCode) in turn.contextResult.record.values" :key="fieldCode"><span>{{ fieldCode }}</span><strong>{{ value }}</strong></article></div>
                      </template>
                    </section>

                    <section v-if="turn.contextResult.operation === 'RECORD_COMMENT_QUERY'" class="agent-record-comment-result">
                      <a-empty
                        v-if="turn.status === 'SUCCEEDED' && (!turn.contextResult.recordComments || !turn.contextResult.recordComments.items.length)"
                        class="agent-context-empty"
                        description="当前记录没有可见评论"
                      />
                      <template v-else-if="turn.contextResult.recordComments">
                        <header>
                          <div><strong>最近评论</strong><span>{{ turn.contextResult.recordComments.moduleCode }} · 记录 {{ turn.contextResult.recordComments.recordId }} · 共 {{ turn.contextResult.recordComments.total }} 条</span></div>
                          <a class="agent-record-activity-route" :href="turn.contextResult.recordComments.route">打开记录</a>
                        </header>
                        <article v-for="comment in turn.contextResult.recordComments.items" :key="comment.commentId" :class="{ deleted: comment.deleted }">
                          <header><div><strong>评论 {{ comment.commentId }}</strong><span>成员 {{ comment.authorMemberId }}<template v-if="comment.parentCommentId"> · 回复 {{ comment.parentCommentId }}</template></span></div><a-tag>v{{ comment.version }}</a-tag></header>
                          <p v-if="comment.deleted" class="agent-record-comment-tombstone">评论已删除</p>
                          <p v-else>{{ comment.body }}</p>
                          <small>创建于 {{ formatTime(comment.createdAt) }} · 更新于 {{ formatTime(comment.updatedAt) }}</small>
                          <small v-if="comment.mentionedMemberIds.length">提及成员 {{ comment.mentionedMemberIds.join('、') }}</small>
                        </article>
                      </template>
                    </section>

                    <section v-if="turn.contextResult.operation === 'RECORD_HISTORY_QUERY'" class="agent-record-history-result">
                      <a-empty
                        v-if="turn.status === 'SUCCEEDED' && (!turn.contextResult.recordHistory || !turn.contextResult.recordHistory.items.length)"
                        class="agent-context-empty"
                        description="当前记录没有可见历史"
                      />
                      <template v-else-if="turn.contextResult.recordHistory">
                        <header>
                          <div><strong>记录历史</strong><span>{{ turn.contextResult.recordHistory.moduleCode }} · 记录 {{ turn.contextResult.recordHistory.recordId }} · 共 {{ turn.contextResult.recordHistory.total }} 条</span></div>
                          <a class="agent-record-activity-route" :href="turn.contextResult.recordHistory.route">打开记录</a>
                        </header>
                        <article v-for="history in turn.contextResult.recordHistory.items" :key="history.historyId">
                          <header><div><strong>{{ history.action }}</strong><span>历史 {{ history.historyId }} · 记录 v{{ history.recordVersion }}</span></div><a-tag>{{ history.actorMemberId ? `成员 ${history.actorMemberId}` : '系统' }}</a-tag></header>
                          <small>{{ formatTime(history.occurredAt) }}</small>
                          <div class="agent-record-history-diffs">
                            <div v-for="diff in history.diff" :key="diff.fieldCode">
                              <strong>{{ diff.fieldCode }}</strong>
                              <span v-if="diff.masked" class="agent-record-history-masked">已脱敏，不展示变更前后值</span>
                              <span v-else class="agent-record-history-values"><code>{{ diff.beforeValueJson ?? '—' }}</code><span>→</span><code>{{ diff.afterValueJson ?? '—' }}</code></span>
                            </div>
                          </div>
                        </article>
                      </template>
                    </section>

                    <section v-if="turn.contextResult.operation === 'RECORD_FILE_QUERY'" class="agent-record-file-result">
                      <a-empty
                        v-if="turn.status === 'SUCCEEDED' && (!turn.contextResult.recordFiles || !turn.contextResult.recordFiles.items.length)"
                        class="agent-context-empty"
                        description="当前记录没有可见附件"
                      />
                      <template v-else-if="turn.contextResult.recordFiles">
                        <header>
                          <div><strong>附件元数据</strong><span>{{ turn.contextResult.recordFiles.moduleCode }} · 记录 {{ turn.contextResult.recordFiles.recordId }} · 共 {{ turn.contextResult.recordFiles.total }} 个</span></div>
                          <a class="agent-record-activity-route" :href="turn.contextResult.recordFiles.route">打开记录</a>
                        </header>
                        <article v-for="file in turn.contextResult.recordFiles.items" :key="file.fileId">
                          <header><div><strong>{{ file.originalName }}</strong><span>文件 {{ file.fileId }}</span></div><a-tag>{{ file.mediaType }}</a-tag></header>
                          <dl>
                            <div><dt>大小</dt><dd>{{ formatFileSize(file.size) }}</dd></div>
                            <div><dt>上传成员</dt><dd>{{ file.uploaderMemberId }}</dd></div>
                            <div><dt>创建时间</dt><dd>{{ formatTime(file.createdAt) }}</dd></div>
                            <div><dt>关联时间</dt><dd>{{ formatTime(file.referencedAt) }}</dd></div>
                          </dl>
                        </article>
                      </template>
                    </section>

                    <section v-if="turn.contextResult.operation === 'WORK_TASK_QUERY'" class="agent-work-task-result">
                      <a-empty v-if="!turn.contextResult.tasks.length" class="agent-context-empty" description="当前筛选没有可见工作任务" />
                      <article v-for="(task, index) in turn.contextResult.tasks" :key="task.taskId || task.id || index">
                        <header><div><strong>{{ task.title || '未命名任务' }}</strong><span>{{ task.taskId || task.id || '无任务 ID' }}<template v-if="task.projectId"> · 项目 {{ task.projectId }}</template></span></div><a-tag>{{ task.status || 'UNKNOWN' }}</a-tag></header>
                        <dl><div v-if="task.priority"><dt>优先级</dt><dd>{{ task.priority }}</dd></div><div><dt>截止时间</dt><dd>{{ task.dueAt ? formatTime(task.dueAt) : '未设置' }}</dd></div><div v-if="task.assigneeMemberId"><dt>负责人</dt><dd>{{ task.assigneeMemberId }}</dd></div><div v-if="task.updatedAt"><dt>更新时间</dt><dd>{{ formatTime(task.updatedAt) }}</dd></div></dl>
                      </article>
                    </section>

                    <section v-if="turn.contextResult.operation === 'WORK_DAILY_REPORT_QUERY'" class="agent-work-report-result">
                      <a-empty v-if="!turn.contextResult.reports.length" class="agent-context-empty" description="当前筛选没有可见工作日报" />
                      <article v-for="(report, index) in turn.contextResult.reports" :key="report.reportId || report.id || index">
                        <header><div><strong>{{ report.workDate || '未提供工作日期' }}</strong><span>{{ report.reportId || report.id || '无日报 ID' }}<template v-if="report.authorMemberId"> · 成员 {{ report.authorMemberId }}</template></span></div><a-tag>{{ report.status || 'UNKNOWN' }}</a-tag></header>
                        <dl><div v-if="report.completedWork"><dt>已完成</dt><dd>{{ report.completedWork }}</dd></div><div v-if="report.plannedWork"><dt>计划</dt><dd>{{ report.plannedWork }}</dd></div><div v-if="report.blockers"><dt>阻塞项</dt><dd>{{ report.blockers }}</dd></div><div v-if="report.updatedAt"><dt>更新时间</dt><dd>{{ formatTime(report.updatedAt) }}</dd></div></dl>
                      </article>
                    </section>

                    <section v-if="turn.contextResult.operation === 'WORK_PROJECT_METRICS_QUERY'" class="agent-work-project-metrics-result">
                      <a-empty
                        v-if="turn.status === 'SUCCEEDED' && !turn.contextResult.workMetrics"
                        class="agent-context-empty"
                        description="当前权限范围内没有可见项目指标"
                      />
                      <template v-else-if="turn.contextResult.workMetrics">
                        <header>
                          <div>
                            <strong>{{ turn.contextResult.workMetrics.title }}</strong>
                            <span>项目 {{ turn.contextResult.workMetrics.projectId }} · {{ turn.contextResult.workMetrics.visibility }}</span>
                          </div>
                          <a-tag>{{ turn.contextResult.workMetrics.status }}</a-tag>
                        </header>
                        <small>更新于 {{ formatTime(turn.contextResult.workMetrics.updatedAt) }} · UTC [{{ turn.contextResult.workMetrics.fromInclusive }}, {{ turn.contextResult.workMetrics.toExclusive }})</small>
                        <dl class="agent-work-project-progress">
                          <div><dt>总任务</dt><dd>{{ turn.contextResult.workMetrics.total }}</dd></div>
                          <div><dt>已完成</dt><dd>{{ turn.contextResult.workMetrics.completed }}</dd></div>
                          <div><dt>待处理</dt><dd>{{ turn.contextResult.workMetrics.open }}</dd></div>
                          <div><dt>完成率</dt><dd>{{ progressPercent(turn.contextResult.workMetrics.completed, turn.contextResult.workMetrics.total) }}</dd></div>
                        </dl>
                        <div class="agent-work-project-kpis">
                          <article><span>当前待处理</span><strong>{{ turn.contextResult.workMetrics.open }}</strong></article>
                          <article><span>已逾期待处理</span><strong>{{ turn.contextResult.workMetrics.overdueOpen.count }}</strong><a :href="turn.contextResult.workMetrics.overdueOpen.route">查看任务</a></article>
                          <article><span>区间内到期</span><strong>{{ turn.contextResult.workMetrics.dueInRangeOpen.count }}</strong><a :href="turn.contextResult.workMetrics.dueInRangeOpen.route">查看任务</a></article>
                          <article><span>区间内完成</span><strong>{{ turn.contextResult.workMetrics.completedInRange.count }}</strong><a :href="turn.contextResult.workMetrics.completedInRange.route">查看任务</a></article>
                        </div>
                        <section class="agent-work-project-daily">
                          <h4>UTC 每日任务事实</h4>
                          <a-empty v-if="!turn.contextResult.workMetrics.daily.length" description="所选区间没有任务活动" />
                          <article v-for="day in turn.contextResult.workMetrics.daily" :key="day.date">
                            <strong>{{ day.date }}</strong>
                            <span>创建 {{ day.createdCount }} · 完成 {{ day.completedCount }}</span>
                            <span><a :href="day.createdRoute">查看创建</a><a :href="day.completedRoute">查看完成</a></span>
                          </article>
                        </section>
                        <section class="agent-work-project-assignees">
                          <h4>待处理负责人 Top 20</h4>
                          <a-empty v-if="!turn.contextResult.workMetrics.topAssignees.length" description="当前没有负责人待处理任务" />
                          <article v-for="assignee in turn.contextResult.workMetrics.topAssignees" :key="assignee.assigneeMemberId">
                            <span>成员 {{ assignee.assigneeMemberId }}</span><strong>{{ assignee.openCount }}</strong><a :href="assignee.route">查看任务</a>
                          </article>
                        </section>
                      </template>
                    </section>

                    <section v-if="turn.contextResult.operation === 'RUNTIME_STATISTICS_QUERY'" class="agent-runtime-statistics-result">
                      <a-empty
                        v-if="turn.status === 'SUCCEEDED' && !turn.contextResult.runtimeStatistics"
                        class="agent-context-empty"
                        description="当前权限范围内没有可显示的统计结果"
                      />
                      <template v-else-if="turn.contextResult.runtimeStatistics">
                        <header>
                          <div>
                            <strong>{{ turn.contextResult.runtimeStatistics.dataSourceCode }}</strong>
                            <span>{{ turn.contextResult.runtimeStatistics.moduleCode }} · 数据源 v{{ turn.contextResult.runtimeStatistics.dataSourceVersionNumber }}</span>
                          </div>
                          <a-tag>{{ turn.contextResult.runtimeStatistics.aggregation }}</a-tag>
                        </header>
                        <dl class="agent-runtime-statistics-kpis">
                          <div>
                            <dt>聚合值</dt>
                            <dd>{{ turn.contextResult.runtimeStatistics.value ?? '无聚合值' }}</dd>
                          </div>
                          <div>
                            <dt>度量字段</dt>
                            <dd>{{ turn.contextResult.runtimeStatistics.measureFieldCode ?? '记录数量' }}</dd>
                          </div>
                          <div>
                            <dt>匹配记录</dt>
                            <dd>{{ turn.contextResult.runtimeStatistics.matchedRecordCount }}</dd>
                          </div>
                          <div>
                            <dt>结果桶</dt>
                            <dd>{{ turn.contextResult.runtimeStatistics.bucketCount }} / {{ turn.contextResult.runtimeStatistics.totalBucketCount }}<template v-if="turn.contextResult.runtimeStatistics.truncated"> · 已截断</template></dd>
                          </div>
                        </dl>

                        <section v-if="turn.contextResult.runtimeStatistics.grouping" class="agent-runtime-statistics-buckets agent-runtime-statistics-groups">
                          <header>
                            <h4>按 {{ turn.contextResult.runtimeStatistics.grouping.fieldCode }} 分组</h4>
                            <a-tag v-if="turn.contextResult.runtimeStatistics.truncated" color="orange">结果已截断</a-tag>
                          </header>
                          <a-empty v-if="!turn.contextResult.runtimeStatistics.grouping.buckets.length" description="没有可显示的分组桶" />
                          <article v-for="(bucket, index) in turn.contextResult.runtimeStatistics.grouping.buckets" :key="`${bucket.nullBucket}:${bucket.label}:${index}`">
                            <div>
                              <strong>{{ bucket.nullBucket ? '空值分组' : (bucket.label ?? '未命名分组') }}</strong>
                              <span>{{ bucket.nullBucket ? '字段值为空' : '分组标签' }}</span>
                            </div>
                            <strong>{{ bucket.value ?? '无聚合值' }}</strong>
                            <span>{{ bucket.recordCount }} 条记录</span>
                          </article>
                        </section>

                        <section v-if="turn.contextResult.runtimeStatistics.trend" class="agent-runtime-statistics-buckets agent-runtime-statistics-trend">
                          <header>
                            <div>
                              <h4>按 {{ turn.contextResult.runtimeStatistics.trend.fieldCode }} 查看趋势</h4>
                              <span>{{ turn.contextResult.runtimeStatistics.trend.grain }} · [{{ turn.contextResult.runtimeStatistics.trend.startInclusive }}, {{ turn.contextResult.runtimeStatistics.trend.endExclusive }})</span>
                            </div>
                          </header>
                          <a-empty v-if="!turn.contextResult.runtimeStatistics.trend.buckets.length" description="所选区间没有趋势桶" />
                          <article v-for="bucket in turn.contextResult.runtimeStatistics.trend.buckets" :key="bucket.startInclusive">
                            <div>
                              <strong>[{{ bucket.startInclusive }}, {{ bucket.endExclusive }})</strong>
                              <span>{{ bucket.empty ? '空桶' : `${bucket.recordCount} 条记录` }}</span>
                            </div>
                            <strong>{{ bucket.value ?? '无聚合值' }}</strong>
                          </article>
                        </section>
                      </template>
                    </section>

                    <section v-if="turn.contextResult.operation === 'RUNTIME_REPORT_QUERY'" class="agent-runtime-report-result">
                      <a-empty
                        v-if="turn.status === 'SUCCEEDED' && !turn.contextResult.runtimeReport"
                        class="agent-context-empty"
                        description="当前权限范围内没有可显示的已发布报表"
                      />
                      <template v-else-if="turn.contextResult.runtimeReport">
                        <header>
                          <div>
                            <strong>{{ turn.contextResult.runtimeReport.reportName }}</strong>
                            <span>{{ turn.contextResult.runtimeReport.reportCode }} · 报表 v{{ turn.contextResult.runtimeReport.reportVersionNumber }}</span>
                          </div>
                          <a class="agent-runtime-report-route" :href="turn.contextResult.runtimeReport.route">打开报表</a>
                        </header>
                        <small>数据源 {{ turn.contextResult.runtimeReport.dataSourceCode }} v{{ turn.contextResult.runtimeReport.dataSourceVersionNumber }} · 模块 {{ turn.contextResult.runtimeReport.moduleCode }}</small>
                        <dl class="agent-runtime-report-kpis">
                          <div><dt>当前页</dt><dd>{{ turn.contextResult.runtimeReport.page }}</dd></div>
                          <div><dt>页大小</dt><dd>{{ turn.contextResult.runtimeReport.size }}</dd></div>
                          <div><dt>本页返回</dt><dd>{{ turn.contextResult.runtimeReport.returnedRows }}</dd></div>
                          <div><dt>总行数</dt><dd>{{ turn.contextResult.runtimeReport.total }}<template v-if="turn.contextResult.runtimeReport.hasMore"> · 还有更多</template></dd></div>
                        </dl>
                        <div v-if="turn.contextResult.runtimeReport.rows.length" class="agent-runtime-report-table-wrap">
                          <table class="agent-runtime-report-table">
                            <thead>
                              <tr><th v-for="field in turn.contextResult.runtimeReport.fields" :key="field.fieldCode">{{ field.fieldName }}<small>{{ field.fieldCode }} · {{ field.type }}</small></th></tr>
                            </thead>
                            <tbody>
                              <tr v-for="(row, rowIndex) in turn.contextResult.runtimeReport.rows" :key="rowIndex">
                                <td v-for="value in row.values" :key="value.fieldCode">{{ value.displayValue ?? '—' }}</td>
                              </tr>
                            </tbody>
                          </table>
                        </div>
                        <a-empty
                          v-else-if="turn.status === 'SUCCEEDED'"
                          class="agent-context-empty"
                          description="当前权限和报表固定筛选范围内没有数据"
                        />
                      </template>
                    </section>

                    <section v-if="turn.contextResult.operation === 'FLOW_INSTANCE_HISTORY_QUERY'" class="agent-flow-instance-history-result">
                      <a-empty
                        v-if="turn.status === 'SUCCEEDED' && !turn.contextResult.flowHistory"
                        class="agent-context-empty"
                        description="当前权限范围内没有可显示的审批实例历史"
                      />
                      <template v-else-if="turn.contextResult.flowHistory">
                        <header>
                          <div>
                            <strong>审批实例 {{ turn.contextResult.flowHistory.instanceId }}</strong>
                            <span>{{ turn.contextResult.flowHistory.status }} · 共 {{ turn.contextResult.flowHistory.total }} 条</span>
                          </div>
                          <a class="agent-flow-instance-history-route" :href="turn.contextResult.flowHistory.route">打开审批实例</a>
                        </header>
                        <a-empty
                          v-if="turn.status === 'SUCCEEDED' && !turn.contextResult.flowHistory.events.length"
                          class="agent-context-empty"
                          description="当前审批实例没有可显示的历史事件"
                        />
                        <ol v-else class="agent-flow-instance-history-events">
                          <li v-for="event in turn.contextResult.flowHistory.events" :key="event.sequence" :data-sequence="event.sequence">
                            <header>
                              <div><strong>#{{ event.sequence }} {{ event.eventType }}</strong><span>{{ formatTime(event.occurredAt) }}</span></div>
                              <a-tag>{{ event.fromStatus ?? '—' }} → {{ event.toStatus }}</a-tag>
                            </header>
                            <p v-if="event.comment">{{ event.comment }}</p>
                            <small>{{ event.actorMemberId ? `成员 ${event.actorMemberId}` : '系统' }}</small>
                          </li>
                        </ol>
                      </template>
                    </section>

                    <section v-if="turn.contextResult.operation === 'TODO_QUERY' && turn.contextResult.todos" class="agent-todo-result">
                      <dl class="agent-context-query-summary">
                        <div><dt>分类</dt><dd>{{ turn.contextResult.todos.category }}</dd></div>
                        <div><dt>状态</dt><dd>{{ turn.contextResult.todos.state }}</dd></div>
                        <div><dt>时间</dt><dd>{{ turn.contextResult.todos.time }}</dd></div>
                        <div><dt>符合条件</dt><dd>{{ turn.contextResult.todos.total }}</dd></div>
                        <div><dt>待办总览</dt><dd>开放 {{ turn.contextResult.todos.counts.open }} · 任务 {{ turn.contextResult.todos.counts.task }} · 审批 {{ turn.contextResult.todos.counts.approval }}</dd></div>
                        <div><dt>时间总览</dt><dd>今日 {{ turn.contextResult.todos.counts.today }} · 逾期 {{ turn.contextResult.todos.counts.overdue }}</dd></div>
                      </dl>
                      <a-empty v-if="!turn.contextResult.todos.items.length" class="agent-context-empty" description="当前筛选没有可见待办" />
                      <article v-for="item in turn.contextResult.todos.items" :key="item.id">
                        <header><div><strong>{{ item.title }}</strong><span>{{ item.category }} · {{ item.sourceType }} / {{ item.sourceId }}</span></div><a-tag>{{ item.state }}</a-tag></header>
                        <dl><div><dt>优先级</dt><dd>{{ item.priority }}</dd></div><div><dt>截止时间</dt><dd>{{ item.dueAt ? formatTime(item.dueAt) : '未设置' }}</dd></div><div><dt>可用动作</dt><dd>{{ item.actions.join('、') }}</dd></div><div><dt>版本</dt><dd>v{{ item.version }}</dd></div><div class="wide"><dt>路由提示</dt><dd><code>{{ item.routeHint }}</code></dd></div></dl>
                      </article>
                    </section>

                    <section v-if="turn.contextResult.operation === 'MESSAGE_QUERY' && turn.contextResult.messages" class="agent-system-message-result">
                      <dl class="agent-context-query-summary">
                        <div><dt>状态</dt><dd>{{ turn.contextResult.messages.status }}</dd></div>
                        <div><dt>未读</dt><dd>{{ turn.contextResult.messages.unreadCount }}</dd></div>
                        <div><dt>符合条件</dt><dd>{{ turn.contextResult.messages.total }}</dd></div>
                      </dl>
                      <a-empty v-if="!turn.contextResult.messages.items.length" class="agent-context-empty" description="当前筛选没有可见系统消息" />
                      <article v-for="item in turn.contextResult.messages.items" :key="item.id">
                        <header><div><strong>{{ item.title }}</strong><span>{{ item.templateCode }} · 消息 {{ item.id }}</span></div><a-tag>{{ item.status }}</a-tag></header>
                        <p>{{ item.body }}</p>
                        <dl><div v-if="item.target"><dt>目标</dt><dd>{{ item.target.type }} / {{ item.target.id }}</dd></div><div><dt>创建时间</dt><dd>{{ formatTime(item.createdAt) }}</dd></div><div v-if="item.readAt"><dt>读取时间</dt><dd>{{ formatTime(item.readAt) }}</dd></div><div v-if="item.archivedAt"><dt>归档时间</dt><dd>{{ formatTime(item.archivedAt) }}</dd></div><div><dt>版本</dt><dd>v{{ item.version }}</dd></div><div v-if="item.targetPath" class="wide"><dt>目标路径</dt><dd><code>{{ item.targetPath }}</code></dd></div></dl>
                      </article>
                    </section>
                  </section>
                  <section v-if="turn.confirmation" class="agent-confirmation" :class="confirmationState(turn.confirmation).toLowerCase()">
                    <header>
                      <div><strong>写入提案</strong><span>{{ turn.confirmation.operation }} · {{ turn.confirmation.moduleCode }}</span></div>
                      <a-tag :color="confirmationStateColor(turn.confirmation)">{{ confirmationStateLabel(turn.confirmation) }}</a-tag>
                    </header>
                    <dl class="agent-confirmation-target">
                      <div><dt>操作</dt><dd>{{ turn.confirmation.operation }}</dd></div>
                      <div><dt>模块</dt><dd>{{ turn.confirmation.moduleCode }}</dd></div>
                      <div><dt>目标记录</dt><dd>{{ turn.confirmation.recordId || '新记录' }}</dd></div>
                      <div><dt>预期记录版本</dt><dd>{{ turn.confirmation.expectedRecordVersion ?? '新建' }}</dd></div>
                      <div v-if="turn.confirmation.beforeTitle !== null"><dt>变更前标题</dt><dd>{{ preview(turn.confirmation.beforeTitle) }}</dd></div>
                      <div v-if="turn.confirmation.afterTitle !== null"><dt>变更后标题</dt><dd>{{ preview(turn.confirmation.afterTitle) }}</dd></div>
                      <div><dt>有效期至</dt><dd>{{ formatTime(turn.confirmation.expiresAt) }}</dd></div>
                    </dl>
                    <div v-if="turn.confirmation.fields.length" class="agent-confirmation-fields">
                      <table>
                        <thead><tr><th>字段</th><th>变更前</th><th>变更后</th><th>置信度</th></tr></thead>
                        <tbody><tr v-for="field in turn.confirmation.fields" :key="field.fieldCode">
                          <th>{{ field.fieldName }}<small>{{ field.fieldCode }} · {{ field.type }}<template v-if="field.masked"> · 已脱敏</template></small></th>
                          <td>{{ preview(field.beforeDisplayValue) }}</td><td>{{ preview(field.afterDisplayValue) }}</td><td>{{ confidence(field.confidence) }}</td>
                        </tr></tbody>
                      </table>
                    </div>
                    <div v-if="turn.confirmation.clarifications.length" class="agent-confirmation-clarifications">
                      <strong>需要补充信息，当前提案不可执行</strong>
                      <ul><li v-for="item in turn.confirmation.clarifications" :key="item">{{ item }}</li></ul>
                    </div>
                    <div v-if="canReject(turn.confirmation)" class="agent-confirmation-actions">
                      <span>确认后才会调用业务模块写入；同一提案只执行一次。</span>
                      <a-button
                        class="agent-confirmation-reject"
                        :loading="confirmationActions[turn.confirmation.id] === 'reject'"
                        :disabled="Boolean(confirmationActions[turn.confirmation.id])"
                        @click="rejectProposal(turn.confirmation)"
                      >拒绝</a-button>
                      <a-button
                        class="agent-confirmation-confirm"
                        type="primary"
                        :loading="confirmationActions[turn.confirmation.id] === 'confirm'"
                        :disabled="Boolean(confirmationActions[turn.confirmation.id]) || !canConfirm(turn.confirmation)"
                        @click="confirmProposal(turn.confirmation)"
                      >确认并执行</a-button>
                    </div>
                    <a-alert
                      v-if="confirmationErrors[turn.confirmation.id]"
                      class="agent-confirmation-error"
                      type="error"
                      show-icon
                      :message="confirmationErrors[turn.confirmation.id]"
                    />
                    <a-alert
                      v-else-if="turn.confirmation.errorCode"
                      class="agent-confirmation-error"
                      type="error"
                      show-icon
                      :message="confirmationCodeLabel(turn.confirmation.errorCode)"
                    />
                    <section v-if="turn.confirmation.result" class="agent-confirmation-result">
                      <header><strong>业务模块执行结果</strong><a-tag color="green">真实回读</a-tag></header>
                      <dl>
                        <div><dt>记录 ID</dt><dd>{{ turn.confirmation.result.recordId }}</dd></div>
                        <div><dt>记录编号</dt><dd>{{ turn.confirmation.result.recordNo }}</dd></div>
                        <div><dt>记录版本</dt><dd>{{ turn.confirmation.result.recordVersion }}</dd></div>
                        <div><dt>Schema 版本</dt><dd>{{ turn.confirmation.result.schemaVersionId }}</dd></div>
                        <div><dt>状态</dt><dd>{{ turn.confirmation.result.status }}</dd></div>
                        <div v-if="turn.confirmation.result.title"><dt>标题</dt><dd>{{ turn.confirmation.result.title }}</dd></div>
                        <div v-if="turn.confirmation.result.executedAt"><dt>执行时间</dt><dd>{{ formatTime(turn.confirmation.result.executedAt) }}</dd></div>
                      </dl>
                      <table v-if="Object.keys(turn.confirmation.result.values).length">
                        <tbody><tr v-for="(value, fieldCode) in turn.confirmation.result.values" :key="fieldCode"><th>{{ fieldCode }}</th><td>{{ value }}</td></tr></tbody>
                      </table>
                    </section>
                  </section>
                  <section v-if="turn.configurationProposal" class="config-field-proposal" :class="configurationState(turn.configurationProposal).toLowerCase()">
                    <header>
                      <div><strong>配置字段草稿提案</strong><span>CONFIG_FIELD_DRAFT · {{ turn.configurationProposal.moduleCode }}</span></div>
                      <a-tag :color="configurationStateColor(turn.configurationProposal)">{{ configurationStateLabel(turn.configurationProposal) }}</a-tag>
                    </header>
                    <dl v-if="turn.configurationProposal.preview" class="config-field-proposal-preview">
                      <div><dt>配置根</dt><dd>{{ turn.configurationProposal.preview.configRootId }}</dd></div>
                      <div><dt>模块 ID</dt><dd>{{ turn.configurationProposal.preview.moduleId }}</dd></div>
                      <div><dt>预期草稿修订</dt><dd>{{ turn.configurationProposal.preview.expectedDraftRevision }}</dd></div>
                      <div><dt>确认后草稿修订</dt><dd>{{ turn.configurationProposal.preview.nextDraftRevision }}</dd></div>
                      <div><dt>模块编码</dt><dd>{{ turn.configurationProposal.preview.moduleCode }}</dd></div>
                      <div><dt>字段编码</dt><dd>{{ turn.configurationProposal.preview.fieldCode }}</dd></div>
                      <div><dt>字段名称</dt><dd>{{ turn.configurationProposal.preview.fieldName }}</dd></div>
                      <div><dt>标量类型</dt><dd>{{ turn.configurationProposal.preview.fieldType }}</dd></div>
                      <div><dt>是否必填</dt><dd>{{ turn.configurationProposal.preview.required ? '是' : '否' }}</dd></div>
                      <div><dt>类型设置</dt><dd><template v-if="configurationSettings(turn.configurationProposal.preview).length">{{ configurationSettings(turn.configurationProposal.preview).join(' · ') }}</template><template v-else>无额外设置</template></dd></div>
                    </dl>
                    <div class="config-field-proposal-meta"><span>模型置信度 {{ confidence(turn.configurationProposal.confidence) }}</span><span>有效期至 {{ formatTime(turn.configurationProposal.expiresAt) }}</span></div>
                    <a-alert v-if="turn.configurationProposal.clarification" class="config-field-proposal-clarification" type="warning" show-icon message="请补充字段信息" :description="turn.configurationProposal.clarification" />
                    <div v-if="canRejectConfiguration(turn.configurationProposal)" class="config-field-proposal-actions">
                      <span>确认前不会修改配置；确认只添加一个字段到当前草稿，不会发布或激活。</span>
                      <a-button class="config-field-proposal-reject" :loading="configurationActions[turn.configurationProposal.id] === 'reject'" :disabled="Boolean(configurationActions[turn.configurationProposal.id])" @click="rejectConfigurationProposal(turn.configurationProposal)">拒绝</a-button>
                      <a-button class="config-field-proposal-confirm" type="primary" :loading="configurationActions[turn.configurationProposal.id] === 'confirm'" :disabled="Boolean(configurationActions[turn.configurationProposal.id]) || !canConfirmConfiguration(turn.configurationProposal)" @click="confirmConfigurationProposal(turn.configurationProposal)">确认加入配置草稿</a-button>
                    </div>
                    <a-alert v-if="configurationErrors[turn.configurationProposal.id]" class="config-field-proposal-error" type="error" show-icon :message="configurationErrors[turn.configurationProposal.id]" />
                    <a-alert v-else-if="turn.configurationProposal.errorCode" class="config-field-proposal-error" type="error" show-icon :message="configurationCodeLabel(turn.configurationProposal.errorCode)" />
                    <section v-if="turn.configurationProposal.result" class="config-field-proposal-result">
                      <header><strong>配置草稿真实回读</strong><a-tag color="green">未发布</a-tag></header>
                      <dl>
                        <div><dt>配置根</dt><dd>{{ turn.configurationProposal.result.configRootId }}</dd></div>
                        <div><dt>模块 ID</dt><dd>{{ turn.configurationProposal.result.moduleId }}</dd></div>
                        <div><dt>草稿修订</dt><dd>{{ turn.configurationProposal.result.draftRevision }}</dd></div>
                        <div><dt>草稿状态</dt><dd>{{ turn.configurationProposal.result.draftStatus }}</dd></div>
                        <div><dt>模块</dt><dd>{{ turn.configurationProposal.result.moduleCode }}</dd></div>
                        <div><dt>字段 ID</dt><dd>{{ turn.configurationProposal.result.fieldId }}</dd></div>
                        <div><dt>字段</dt><dd>{{ turn.configurationProposal.result.fieldName }} · {{ turn.configurationProposal.result.fieldCode }}</dd></div>
                        <div><dt>定义</dt><dd>{{ turn.configurationProposal.result.fieldType }} · {{ turn.configurationProposal.result.required ? '必填' : '非必填' }}</dd></div>
                        <div><dt>类型设置</dt><dd><template v-if="configurationSettings(turn.configurationProposal.result).length">{{ configurationSettings(turn.configurationProposal.result).join(' · ') }}</template><template v-else>无额外设置</template></dd></div>
                        <div><dt>字段顺序</dt><dd>{{ turn.configurationProposal.result.sortOrder }}</dd></div>
                        <div><dt>字段版本</dt><dd>{{ turn.configurationProposal.result.fieldVersion }}</dd></div>
                      </dl>
                      <small>request {{ turn.configurationProposal.requestId }} · trace {{ turn.configurationProposal.traceId }}</small>
                    </section>
                  </section>
                  <section
                    v-if="turn.artifactProposal"
                    class="config-artifact-proposal"
                    :class="[artifactState(turn.artifactProposal).toLowerCase(), artifactKindClass(turn.artifactProposal)]"
                    :data-artifact-kind="turn.artifactProposal.artifactKind"
                    :data-proposal-id="turn.artifactProposal.id"
                  >
                    <header>
                      <div><strong>{{ artifactKindLabel(turn.artifactProposal) }}草稿提案</strong><span>{{ turn.artifactProposal.operation }} · {{ turn.artifactProposal.moduleCode || '待补充模块' }}</span></div>
                      <a-tag :color="artifactStateColor(turn.artifactProposal)">{{ artifactStateLabel(turn.artifactProposal) }}</a-tag>
                    </header>
                    <a-alert v-if="artifactState(turn.artifactProposal) === 'EXECUTING'" class="config-artifact-proposal-loading" type="info" show-icon message="正在更新配置草稿" description="正在重新检查当前权限与草稿修订，并执行一次原子 owner mutation。" />
                    <dl v-if="turn.artifactProposal.preview" class="config-artifact-common-preview">
                      <div><dt>配置根</dt><dd>{{ turn.artifactProposal.preview.configRootId }}</dd></div>
                      <div><dt>模块</dt><dd>{{ turn.artifactProposal.preview.moduleCode }} · {{ turn.artifactProposal.preview.moduleId }}</dd></div>
                      <div><dt>预期草稿修订</dt><dd>{{ turn.artifactProposal.preview.expectedDraftRevision }}</dd></div>
                      <div><dt>确认后草稿修订</dt><dd>{{ turn.artifactProposal.preview.nextDraftRevision }}</dd></div>
                    </dl>

                    <section v-if="turn.artifactProposal.preview?.selectionField" class="config-artifact-selection-preview">
                      <header><div><strong>{{ turn.artifactProposal.preview.selectionField.fieldName }}</strong><span>{{ turn.artifactProposal.preview.selectionField.fieldCode }} · {{ turn.artifactProposal.preview.selectionField.fieldType }}</span></div><a-tag>{{ turn.artifactProposal.preview.selectionField.required ? '必填' : '非必填' }}</a-tag></header>
                      <dl>
                        <div><dt>字段顺序</dt><dd>{{ turn.artifactProposal.preview.selectionField.sortOrder }}</dd></div>
                        <div><dt>字典</dt><dd>{{ turn.artifactProposal.preview.selectionField.dictionaryName }} · {{ turn.artifactProposal.preview.selectionField.dictionaryCode }}</dd></div>
                        <div><dt>最大选择数</dt><dd>{{ turn.artifactProposal.preview.selectionField.maxSelections ?? '不适用' }}</dd></div>
                        <div><dt>选项数</dt><dd>{{ turn.artifactProposal.preview.selectionField.options.length }}</dd></div>
                      </dl>
                      <a-empty v-if="!turn.artifactProposal.preview.selectionField.options.length" class="config-artifact-empty" description="暂无可显示的选项摘要" />
                      <div v-else class="config-artifact-options">
                        <article v-for="option in turn.artifactProposal.preview.selectionField.options" :key="`${option.code}:${option.sortOrder}`">
                          <span class="config-artifact-option-order">{{ option.sortOrder + 1 }}</span><div><strong>{{ option.label }}</strong><small>{{ option.code }}<template v-if="option.semanticKey"> · {{ option.semanticKey }}</template></small></div><i v-if="option.color" :style="{ backgroundColor: option.color }" /><a-tag v-if="option.defaultOption" color="green">默认</a-tag>
                        </article>
                      </div>
                    </section>

                    <section v-if="turn.artifactProposal.preview?.pageLayout" class="config-artifact-page-preview">
                      <header><div><strong>{{ turn.artifactProposal.preview.pageLayout.pageCode }}</strong><span>{{ turn.artifactProposal.preview.pageLayout.pageType }} · 页面 {{ turn.artifactProposal.preview.pageLayout.pageId }}</span></div><a-tag>版本 {{ turn.artifactProposal.preview.pageLayout.pageVersion }}</a-tag></header>
                      <dl>
                        <div><dt>网格</dt><dd>{{ turn.artifactProposal.preview.pageLayout.layout.columns }} 列 · 间距 {{ turn.artifactProposal.preview.pageLayout.layout.gap }}</dd></div>
                        <div><dt>标签与密度</dt><dd>{{ turn.artifactProposal.preview.pageLayout.layout.labelPosition }} · {{ turn.artifactProposal.preview.pageLayout.layout.density }}</dd></div>
                        <div><dt>固定操作区</dt><dd>{{ turn.artifactProposal.preview.pageLayout.layout.stickyActions ? '开启' : '关闭' }}</dd></div>
                        <div><dt>列表能力</dt><dd>每页 {{ turn.artifactProposal.preview.pageLayout.layout.pageSize ?? '不适用' }} · 搜索 {{ turn.artifactProposal.preview.pageLayout.layout.searchEnabled ?? '不适用' }} · 筛选 {{ turn.artifactProposal.preview.pageLayout.layout.filterEnabled ?? '不适用' }}</dd></div>
                        <div><dt>摘要</dt><dd>{{ turn.artifactProposal.preview.pageLayout.layout.sectionCount }} 个分区 · {{ turn.artifactProposal.preview.pageLayout.layout.fieldCount }} 个字段</dd></div>
                      </dl>
                      <a-empty v-if="!turn.artifactProposal.preview.pageLayout.layout.sections.length" class="config-artifact-empty" :description="turn.artifactProposal.preview.pageLayout.layout.redacted ? '持久回读已脱敏分区明细，仅保留数量摘要' : '暂无页面分区'" />
                      <div v-else class="config-artifact-sections">
                        <article v-for="section in turn.artifactProposal.preview.pageLayout.layout.sections" :key="section.code"><div><strong>{{ section.title }}</strong><span>{{ section.code }} · 顺序 {{ section.sortOrder + 1 }}</span></div><small>{{ section.fieldCodes.join(' · ') }}</small></article>
                      </div>
                    </section>

                    <section
                      v-if="turn.artifactProposal.preview?.filterScenario"
                      class="config-filter-scenario-preview"
                      :data-page-code="turn.artifactProposal.preview.filterScenario.pageCode"
                    >
                      <header>
                        <div><strong>{{ turn.artifactProposal.preview.filterScenario.scenario.name }}</strong><span>{{ turn.artifactProposal.preview.filterScenario.scenario.code }} · LIST 页面 {{ turn.artifactProposal.preview.filterScenario.pageCode }}</span></div>
                        <a-tag>页面版本 {{ turn.artifactProposal.preview.filterScenario.pageVersion }}</a-tag>
                      </header>
                      <dl>
                        <div><dt>目标页面</dt><dd>{{ turn.artifactProposal.preview.filterScenario.pageCode }} · {{ turn.artifactProposal.preview.filterScenario.pageId }}</dd></div>
                        <div><dt>默认意图</dt><dd>{{ turn.artifactProposal.preview.filterScenario.makeDefault ? '设为默认共享筛选方案' : '保持现有默认方案' }}</dd></div>
                        <div><dt>确认后方案数</dt><dd>{{ turn.artifactProposal.preview.filterScenario.resolvedState.filterScenarios.length }}</dd></div>
                        <div><dt>确认后默认方案</dt><dd>{{ turn.artifactProposal.preview.filterScenario.resolvedState.defaultFilterScenarioCode ?? '未设置' }}</dd></div>
                      </dl>
                      <div class="config-filter-scenario-canonical">
                        <label>canonical filter</label><pre class="config-filter-scenario-filter">{{ canonicalJson(turn.artifactProposal.preview.filterScenario.scenario.filter) }}</pre>
                        <label>canonical sort</label><pre class="config-filter-scenario-sort">{{ canonicalJson(turn.artifactProposal.preview.filterScenario.scenario.sort) }}</pre>
                      </div>
                      <a-alert type="info" show-icon message="仅更新配置草稿；不会自动检查、发布或激活配置。" />
                    </section>

                    <section
                      v-if="turn.artifactProposal.preview?.fieldPermissionStage"
                      class="config-field-permission-stage-preview"
                      :data-field-code="turn.artifactProposal.preview.fieldPermissionStage.fieldCode"
                    >
                      <header>
                        <div><strong>{{ turn.artifactProposal.preview.fieldPermissionStage.fieldName }}</strong><span>{{ turn.artifactProposal.preview.fieldPermissionStage.fieldCode }} · {{ turn.artifactProposal.preview.fieldPermissionStage.fieldId }}</span></div>
                        <a-tag>字段版本 {{ turn.artifactProposal.preview.fieldPermissionStage.fieldVersion }}</a-tag>
                      </header>
                      <dl>
                        <div><dt>本次方向</dt><dd><a-tag v-if="turn.artifactProposal.preview.fieldPermissionStage.stageRead">READ</a-tag><a-tag v-if="turn.artifactProposal.preview.fieldPermissionStage.stageWrite">WRITE</a-tag></dd></div>
                        <div><dt>当前模式</dt><dd>READ {{ turn.artifactProposal.preview.fieldPermissionStage.expectedReadPermissionMode }} · WRITE {{ turn.artifactProposal.preview.fieldPermissionStage.expectedWritePermissionMode }}</dd></div>
                        <div><dt>确认后模式</dt><dd>READ {{ turn.artifactProposal.preview.fieldPermissionStage.readPermissionMode }} · WRITE {{ turn.artifactProposal.preview.fieldPermissionStage.writePermissionMode }}</dd></div>
                        <div><dt>READ owner code</dt><dd class="config-field-permission-read-code">{{ turn.artifactProposal.preview.fieldPermissionStage.readPermissionCode ?? '未注册' }}</dd></div>
                        <div><dt>WRITE owner code</dt><dd class="config-field-permission-write-code">{{ turn.artifactProposal.preview.fieldPermissionStage.writePermissionCode ?? '未注册' }}</dd></div>
                      </dl>
                      <a-alert class="config-field-permission-stage-notice" type="warning" show-icon message="STAGED 不限制运行时，也不会向任何角色授权；配置发布后，管理员必须先授予并发布角色，再在配置工作台中显式选择 ENFORCED。" />
                    </section>

                    <div class="config-artifact-proposal-meta"><span>模型置信度 {{ confidence(turn.artifactProposal.confidence) }}</span><span>有效期至 {{ formatTime(turn.artifactProposal.expiresAt) }}</span></div>
                    <a-alert v-if="turn.artifactProposal.clarification" class="config-artifact-proposal-clarification" type="warning" show-icon message="请补充配置工件信息" :description="turn.artifactProposal.clarification" />
                    <div v-if="canRejectArtifact(turn.artifactProposal)" class="config-artifact-proposal-actions">
                      <span>确认前零写入；确认仅更新当前配置草稿，绝不自动发布或激活。</span>
                      <a-button :class="['config-artifact-proposal-reject', { 'config-filter-scenario-reject': turn.artifactProposal.artifactKind === 'FILTER_SCENARIO', 'config-field-permission-stage-reject': turn.artifactProposal.artifactKind === 'FIELD_PERMISSION_STAGE' }]" :loading="artifactActions[turn.artifactProposal.id] === 'reject'" :disabled="Boolean(artifactActions[turn.artifactProposal.id])" @click="rejectArtifactProposal(turn.artifactProposal)">拒绝</a-button>
                      <a-button :class="['config-artifact-proposal-confirm', { 'config-filter-scenario-confirm': turn.artifactProposal.artifactKind === 'FILTER_SCENARIO', 'config-field-permission-stage-confirm': turn.artifactProposal.artifactKind === 'FIELD_PERMISSION_STAGE' }]" type="primary" :loading="artifactActions[turn.artifactProposal.id] === 'confirm'" :disabled="Boolean(artifactActions[turn.artifactProposal.id]) || !canConfirmArtifact(turn.artifactProposal)" @click="confirmArtifactProposal(turn.artifactProposal)">确认更新配置草稿</a-button>
                    </div>
                    <a-alert v-if="artifactErrors[turn.artifactProposal.id]" class="config-artifact-proposal-error" type="error" show-icon :message="artifactErrors[turn.artifactProposal.id]" />
                    <a-alert v-else-if="turn.artifactProposal.errorCode" class="config-artifact-proposal-error" type="error" show-icon :message="artifactCodeLabel(turn.artifactProposal.errorCode)" />

                    <section v-if="turn.artifactProposal.result" class="config-artifact-proposal-result">
                      <header><strong>配置草稿真实回读</strong><a-tag color="green">{{ turn.artifactProposal.result.draftStatus }} · 未发布</a-tag></header>
                      <dl class="config-artifact-result-summary">
                        <div><dt>配置根</dt><dd>{{ turn.artifactProposal.result.configRootId }}</dd></div><div><dt>模块</dt><dd>{{ turn.artifactProposal.result.moduleCode }} · {{ turn.artifactProposal.result.moduleId }}</dd></div><div><dt>草稿修订</dt><dd>{{ turn.artifactProposal.result.draftRevision }}</dd></div>
                      </dl>
                      <section v-if="turn.artifactProposal.result.selectionField" class="config-artifact-selection-result">
                        <strong>原子创建的字段、字典与选项</strong>
                        <dl><div><dt>字段</dt><dd>{{ turn.artifactProposal.result.selectionField.fieldName }} · {{ turn.artifactProposal.result.selectionField.fieldCode }} · {{ turn.artifactProposal.result.selectionField.fieldId }}</dd></div><div><dt>字段定义</dt><dd>{{ turn.artifactProposal.result.selectionField.fieldType }} · {{ turn.artifactProposal.result.selectionField.required ? '必填' : '非必填' }} · 版本 {{ turn.artifactProposal.result.selectionField.version }}</dd></div><div><dt>字典</dt><dd>{{ turn.artifactProposal.result.selectionField.dictionary.dictionaryName }} · {{ turn.artifactProposal.result.selectionField.dictionary.dictionaryCode }} · {{ turn.artifactProposal.result.selectionField.dictionary.dictionaryId }}</dd></div><div><dt>选项数</dt><dd>{{ turn.artifactProposal.result.selectionField.options.length }}</dd></div></dl>
                        <a-empty v-if="!turn.artifactProposal.result.selectionField.options.length" class="config-artifact-empty" description="暂无可显示的选项回读" />
                        <div v-else class="config-artifact-options"><article v-for="option in turn.artifactProposal.result.selectionField.options" :key="option.optionId"><span class="config-artifact-option-order">{{ option.sortOrder + 1 }}</span><div><strong>{{ option.label }}</strong><small>{{ option.code }} · {{ option.optionId }} · v{{ option.version }}</small></div><i v-if="option.color" :style="{ backgroundColor: option.color }" /><a-tag v-if="option.defaultOption" color="green">默认</a-tag></article></div>
                      </section>
                      <section v-if="turn.artifactProposal.result.pageLayout" class="config-artifact-page-result">
                        <strong>页面布局草稿</strong><dl><div><dt>页面</dt><dd>{{ turn.artifactProposal.result.pageLayout.pageCode }} · {{ turn.artifactProposal.result.pageLayout.pageId }}</dd></div><div><dt>类型与版本</dt><dd>{{ turn.artifactProposal.result.pageLayout.pageType }} · v{{ turn.artifactProposal.result.pageLayout.version }}</dd></div><div><dt>布局</dt><dd>{{ turn.artifactProposal.result.pageLayout.layout.columns }} 列 · 间距 {{ turn.artifactProposal.result.pageLayout.layout.gap }} · {{ turn.artifactProposal.result.pageLayout.layout.density }}</dd></div><div><dt>摘要</dt><dd>{{ turn.artifactProposal.result.pageLayout.layout.sectionCount }} 个分区 · {{ turn.artifactProposal.result.pageLayout.layout.fieldCount }} 个字段</dd></div></dl>
                        <a-empty v-if="!turn.artifactProposal.result.pageLayout.layout.sections.length" class="config-artifact-empty" :description="turn.artifactProposal.result.pageLayout.layout.redacted ? '持久回读已脱敏分区明细，仅保留数量摘要' : '暂无页面分区回读'" />
                        <div v-else class="config-artifact-sections"><article v-for="section in turn.artifactProposal.result.pageLayout.layout.sections" :key="section.code"><div><strong>{{ section.title }}</strong><span>{{ section.code }} · 顺序 {{ section.sortOrder + 1 }}</span></div><small>{{ section.fieldCodes.join(' · ') }}</small></article></div>
                      </section>
                      <section v-if="turn.artifactProposal.result.filterScenario" class="config-filter-scenario-result">
                        <strong>共享筛选方案 owner 回读</strong>
                        <dl>
                          <div><dt>页面</dt><dd>{{ turn.artifactProposal.result.filterScenario.pageCode }} · {{ turn.artifactProposal.result.filterScenario.pageId }} · v{{ turn.artifactProposal.result.filterScenario.version }}</dd></div>
                          <div><dt>默认方案</dt><dd>{{ turn.artifactProposal.result.filterScenario.state.defaultFilterScenarioCode ?? '未设置' }}</dd></div>
                        </dl>
                        <div class="config-filter-scenario-readback">
                          <article v-for="scenario in turn.artifactProposal.result.filterScenario.state.filterScenarios" :key="scenario.code" :data-scenario-code="scenario.code">
                            <header><strong>{{ scenario.name }}</strong><span>{{ scenario.code }}<template v-if="scenario.code === turn.artifactProposal.result.filterScenario.state.defaultFilterScenarioCode"> · 默认</template></span></header>
                            <label>canonical filter</label><pre>{{ canonicalJson(scenario.filter) }}</pre>
                            <label>canonical sort</label><pre>{{ canonicalJson(scenario.sort) }}</pre>
                          </article>
                        </div>
                      </section>
                      <section v-if="turn.artifactProposal.result.fieldPermissionStage" class="config-field-permission-stage-result">
                        <strong>字段权限 owner 回读</strong>
                        <dl>
                          <div><dt>字段</dt><dd>{{ turn.artifactProposal.result.fieldPermissionStage.fieldName }} · {{ turn.artifactProposal.result.fieldPermissionStage.fieldCode }} · {{ turn.artifactProposal.result.fieldPermissionStage.fieldId }}</dd></div>
                          <div><dt>字段版本</dt><dd>{{ turn.artifactProposal.result.fieldPermissionStage.version }}</dd></div>
                          <div><dt>READ</dt><dd><span class="config-field-permission-read-mode">{{ turn.artifactProposal.result.fieldPermissionStage.readPermissionMode }}</span> · <span class="config-field-permission-read-code">{{ turn.artifactProposal.result.fieldPermissionStage.readPermissionCode ?? '未注册' }}</span></dd></div>
                          <div><dt>WRITE</dt><dd><span class="config-field-permission-write-mode">{{ turn.artifactProposal.result.fieldPermissionStage.writePermissionMode }}</span> · <span class="config-field-permission-write-code">{{ turn.artifactProposal.result.fieldPermissionStage.writePermissionCode ?? '未注册' }}</span></dd></div>
                        </dl>
                      </section>
                      <a-button v-if="artifactStudioTarget(turn.artifactProposal)" class="config-suggestion-open-studio" type="primary" @click="openArtifactInConfiguration(turn.artifactProposal)">前往配置工作台</a-button>
                      <small>request {{ turn.artifactProposal.requestId }} · trace {{ turn.artifactProposal.traceId }}</small>
                    </section>
                  </section>
                  <section v-if="turn.workProposal" class="work-proposal" :class="workState(turn.workProposal).toLowerCase()">
                    <header>
                      <div><strong>{{ turn.workProposal.operation === 'WORK_TASK_DRAFT' ? '工作任务草稿提案' : '个人日报草稿提案' }}</strong><span>{{ turn.workProposal.operation }} · 显式确认后才会创建</span></div>
                      <a-tag :color="workStateColor(turn.workProposal)">{{ workStateLabel(turn.workProposal) }}</a-tag>
                    </header>
                    <a-alert v-if="workState(turn.workProposal) === 'EXECUTING'" class="work-proposal-loading" type="info" show-icon message="正在创建工作草稿" description="正在重新检查权限、业务约束与提案修订，并执行一次原子 owner mutation。" />

                    <dl v-if="turn.workProposal.preview?.task" class="work-proposal-preview">
                      <div><dt>任务标题</dt><dd>{{ turn.workProposal.preview.task.title }}</dd></div>
                      <div><dt>负责人成员</dt><dd>{{ turn.workProposal.preview.task.assigneeMemberId }}</dd></div>
                      <div><dt>项目</dt><dd>{{ turn.workProposal.preview.task.projectId || '未关联项目' }}</dd></div>
                      <div><dt>截止时间</dt><dd>{{ turn.workProposal.preview.task.dueAt ? formatTime(turn.workProposal.preview.task.dueAt) : '未设置' }}</dd></div>
                      <div class="wide"><dt>任务描述</dt><dd>{{ turn.workProposal.preview.task.description || '未填写' }}</dd></div>
                    </dl>
                    <dl v-if="turn.workProposal.preview?.dailyReport" class="work-proposal-preview">
                      <div><dt>工作日期</dt><dd>{{ turn.workProposal.preview.dailyReport.workDate }}</dd></div>
                      <div><dt>草稿状态</dt><dd>DRAFT · 未提交</dd></div>
                      <div class="wide"><dt>已完成工作</dt><dd>{{ turn.workProposal.preview.dailyReport.completedWork }}</dd></div>
                      <div class="wide"><dt>计划工作</dt><dd>{{ turn.workProposal.preview.dailyReport.plannedWork }}</dd></div>
                      <div class="wide"><dt>阻塞事项</dt><dd>{{ turn.workProposal.preview.dailyReport.blockers || '无' }}</dd></div>
                    </dl>
                    <a-empty v-if="!turn.workProposal.preview && !turn.workProposal.clarification && workState(turn.workProposal) === 'PENDING'" class="work-proposal-empty" description="工作草稿预览尚未生成" />
                    <div class="work-proposal-meta"><span>模型置信度 {{ confidence(turn.workProposal.confidence) }}</span><span>有效期至 {{ formatTime(turn.workProposal.expiresAt) }}</span></div>
                    <a-alert v-if="turn.workProposal.clarification" class="work-proposal-clarification" type="warning" show-icon message="请补充工作草稿信息" :description="turn.workProposal.clarification" />
                    <div v-if="canRejectWork(turn.workProposal)" class="work-proposal-actions">
                      <span>{{ turn.workProposal.operation === 'WORK_TASK_DRAFT' ? '确认前零写入；确认只创建一个 OPEN 任务，不会自动完成。' : '确认前零写入；确认只创建一份个人 DRAFT 日报，不会自动提交。' }}</span>
                      <a-button class="work-proposal-reject" :loading="workActions[turn.workProposal.id] === 'reject'" :disabled="Boolean(workActions[turn.workProposal.id])" @click="rejectWorkProposal(turn.workProposal)">拒绝</a-button>
                      <a-button class="work-proposal-confirm" type="primary" :loading="workActions[turn.workProposal.id] === 'confirm'" :disabled="Boolean(workActions[turn.workProposal.id]) || !canConfirmWork(turn.workProposal)" @click="confirmWorkProposal(turn.workProposal)">{{ turn.workProposal.operation === 'WORK_TASK_DRAFT' ? '确认创建 OPEN 任务' : '确认创建 DRAFT 日报' }}</a-button>
                    </div>
                    <a-alert v-if="workErrors[turn.workProposal.id]" class="work-proposal-error" type="error" show-icon :message="workErrors[turn.workProposal.id]" />
                    <a-alert v-else-if="turn.workProposal.errorCode" class="work-proposal-error" type="error" show-icon :message="workCodeLabel(turn.workProposal.errorCode)" />

                    <section v-if="turn.workProposal.result" class="work-proposal-result">
                      <header><strong>Work owner 真实回读</strong><a-tag color="green">{{ turn.workProposal.result.task ? 'OPEN · 未完成' : 'DRAFT · 未提交' }}</a-tag></header>
                      <dl v-if="turn.workProposal.result.task">
                        <div><dt>任务 ID</dt><dd>{{ turn.workProposal.result.task.taskId }}</dd></div>
                        <div><dt>任务状态</dt><dd>{{ turn.workProposal.result.task.status }} · 未完成</dd></div>
                        <div><dt>任务标题</dt><dd>{{ turn.workProposal.result.task.title }}</dd></div>
                        <div><dt>负责人成员</dt><dd>{{ turn.workProposal.result.task.assigneeMemberId }}</dd></div>
                        <div><dt>项目</dt><dd>{{ turn.workProposal.result.task.projectId || '未关联项目' }}</dd></div>
                        <div><dt>截止时间</dt><dd>{{ turn.workProposal.result.task.dueAt ? formatTime(turn.workProposal.result.task.dueAt) : '未设置' }}</dd></div>
                        <div><dt>创建时间</dt><dd>{{ formatTime(turn.workProposal.result.task.createdAt) }}</dd></div>
                        <div><dt>版本</dt><dd>{{ turn.workProposal.result.task.version }}</dd></div>
                        <div class="wide"><dt>任务描述</dt><dd>{{ turn.workProposal.result.task.description || '未填写' }}</dd></div>
                      </dl>
                      <dl v-if="turn.workProposal.result.dailyReport">
                        <div><dt>日报 ID</dt><dd>{{ turn.workProposal.result.dailyReport.reportId }}</dd></div>
                        <div><dt>日报状态</dt><dd>{{ turn.workProposal.result.dailyReport.status }} · 未提交</dd></div>
                        <div><dt>工作日期</dt><dd>{{ turn.workProposal.result.dailyReport.workDate }}</dd></div>
                        <div><dt>作者成员</dt><dd>{{ turn.workProposal.result.dailyReport.authorMemberId }}</dd></div>
                        <div><dt>创建时间</dt><dd>{{ formatTime(turn.workProposal.result.dailyReport.createdAt) }}</dd></div>
                        <div><dt>版本</dt><dd>{{ turn.workProposal.result.dailyReport.version }}</dd></div>
                        <div class="wide"><dt>已完成工作</dt><dd>{{ turn.workProposal.result.dailyReport.completedWork }}</dd></div>
                        <div class="wide"><dt>计划工作</dt><dd>{{ turn.workProposal.result.dailyReport.plannedWork }}</dd></div>
                        <div class="wide"><dt>阻塞事项</dt><dd>{{ turn.workProposal.result.dailyReport.blockers || '无' }}</dd></div>
                      </dl>
                      <small>request {{ turn.workProposal.requestId }} · trace {{ turn.workProposal.traceId }}</small>
                    </section>
                  </section>
                  <section v-if="turn.generatedDraftProposal" class="generated-draft-proposal" :class="generatedDraftState(turn.generatedDraftProposal).toLowerCase()">
                    <header>
                      <div><strong>{{ generatedDraftKindLabel(turn.generatedDraftProposal) }}草稿提案</strong><span>{{ turn.generatedDraftProposal.operation }} · 显式确认后才会创建</span></div>
                      <a-tag :color="generatedDraftStateColor(turn.generatedDraftProposal)">{{ generatedDraftStateLabel(turn.generatedDraftProposal) }}</a-tag>
                    </header>
                    <a-alert v-if="generatedDraftState(turn.generatedDraftProposal) === 'EXECUTING'" class="generated-draft-loading" type="info" show-icon message="正在创建生成草稿" description="正在重新检查当前权限、成员与配置依赖，并执行一次原子 owner mutation。" />

                    <section v-if="turn.generatedDraftProposal.preview?.operation === 'FLOW_DEFINITION_DRAFT'" class="generated-draft-preview generated-flow-preview">
                      <dl>
                        <div class="wide"><dt>Flow 名称</dt><dd>{{ turn.generatedDraftProposal.preview.flowDefinition.name }}</dd></div>
                        <div class="wide"><dt>有序审批成员</dt><dd>{{ turn.generatedDraftProposal.preview.flowDefinition.approverMemberIds.join(' → ') }}</dd></div>
                      </dl>
                      <a-empty v-if="!turn.generatedDraftProposal.preview.flowDefinition.approverMemberIds.length" description="没有可创建的审批成员" />
                    </section>
                    <section v-if="turn.generatedDraftProposal.preview?.operation === 'CONFIG_REPORT_DRAFT'" class="generated-draft-preview generated-report-preview">
                      <dl>
                        <div><dt>报表编码</dt><dd>{{ turn.generatedDraftProposal.preview.reportDefinition.code }}</dd></div>
                        <div><dt>报表名称</dt><dd>{{ turn.generatedDraftProposal.preview.reportDefinition.name }}</dd></div>
                        <div><dt>数据源</dt><dd>{{ turn.generatedDraftProposal.preview.reportDefinition.dataSourceId }}</dd></div>
                        <div><dt>输出字段数</dt><dd>{{ turn.generatedDraftProposal.preview.reportDefinition.outputFieldCodes.length }}</dd></div>
                        <div class="wide"><dt>报表描述</dt><dd>{{ turn.generatedDraftProposal.preview.reportDefinition.description || '未填写' }}</dd></div>
                        <div class="wide"><dt>输出字段</dt><dd>{{ turn.generatedDraftProposal.preview.reportDefinition.outputFieldCodes.join(' · ') }}</dd></div>
                      </dl>
                    </section>
                    <section v-if="turn.generatedDraftProposal.preview?.operation === 'CONFIG_PRINT_TEMPLATE_DRAFT'" class="generated-draft-preview generated-print-preview">
                      <dl>
                        <div><dt>模块</dt><dd>{{ turn.generatedDraftProposal.preview.printTemplate.moduleCode }}</dd></div>
                        <div><dt>模板编码</dt><dd>{{ turn.generatedDraftProposal.preview.printTemplate.code }}</dd></div>
                        <div><dt>模板名称</dt><dd>{{ turn.generatedDraftProposal.preview.printTemplate.name }}</dd></div>
                        <div><dt>纸张</dt><dd>{{ turn.generatedDraftProposal.preview.printTemplate.paperSize }} · {{ turn.generatedDraftProposal.preview.printTemplate.orientation }}</dd></div>
                        <div class="wide"><dt>打印标题</dt><dd>{{ turn.generatedDraftProposal.preview.printTemplate.title }}</dd></div>
                        <div class="wide"><dt>打印字段</dt><dd>{{ turn.generatedDraftProposal.preview.printTemplate.fieldCodes.join(' · ') }}</dd></div>
                        <div class="wide"><dt>页脚</dt><dd>{{ turn.generatedDraftProposal.preview.printTemplate.footer || '未填写' }}</dd></div>
                      </dl>
                    </section>
                    <a-empty v-if="!turn.generatedDraftProposal.preview && !turn.generatedDraftProposal.clarification && generatedDraftState(turn.generatedDraftProposal) === 'PENDING'" class="generated-draft-empty" description="生成草稿预览尚未生成" />
                    <div class="generated-draft-meta"><span>模型置信度 {{ confidence(turn.generatedDraftProposal.confidence) }}</span><span>有效期至 {{ formatTime(turn.generatedDraftProposal.expiresAt) }}</span></div>
                    <a-alert v-if="turn.generatedDraftProposal.clarification" class="generated-draft-clarification" type="warning" show-icon message="请补充生成草稿信息" :description="turn.generatedDraftProposal.clarification" />
                    <div v-if="canRejectGeneratedDraft(turn.generatedDraftProposal)" class="generated-draft-actions">
                      <span v-if="turn.generatedDraftProposal.operation === 'FLOW_DEFINITION_DRAFT'">确认前零 Flow 写入；确认只创建一份未发布、未激活的 Flow 定义草稿，不启动流程实例。</span>
                      <span v-else-if="turn.generatedDraftProposal.operation === 'CONFIG_REPORT_DRAFT'">确认前零报表写入；确认只创建一份未发布报表定义，不检查、运行、导出或调度。</span>
                      <span v-else>确认前零打印写入；确认只创建一个 DISABLED、未发布模板，不预览、生成 PDF 或打印。</span>
                      <a-button class="generated-draft-reject" :loading="generatedDraftActions[turn.generatedDraftProposal.id] === 'reject'" :disabled="Boolean(generatedDraftActions[turn.generatedDraftProposal.id])" @click="rejectGeneratedDraftProposal(turn.generatedDraftProposal)">拒绝</a-button>
                      <a-button class="generated-draft-confirm" type="primary" :loading="generatedDraftActions[turn.generatedDraftProposal.id] === 'confirm'" :disabled="Boolean(generatedDraftActions[turn.generatedDraftProposal.id]) || !canConfirmGeneratedDraft(turn.generatedDraftProposal)" @click="confirmGeneratedDraftProposal(turn.generatedDraftProposal)">确认创建{{ generatedDraftKindLabel(turn.generatedDraftProposal) }}草稿</a-button>
                    </div>
                    <a-alert v-if="generatedDraftErrors[turn.generatedDraftProposal.id]" class="generated-draft-error" type="error" show-icon :message="generatedDraftErrors[turn.generatedDraftProposal.id]" />
                    <a-alert v-else-if="turn.generatedDraftProposal.errorCode" class="generated-draft-error" type="error" show-icon :message="generatedDraftCodeLabel(turn.generatedDraftProposal.errorCode)" />

                    <section v-if="turn.generatedDraftProposal.result" class="generated-draft-result">
                      <header><strong>领域 owner 真实回读</strong><a-tag color="green"><template v-if="turn.generatedDraftProposal.result.operation === 'FLOW_DEFINITION_DRAFT'">DRAFT · 未发布/未激活</template><template v-else-if="turn.generatedDraftProposal.result.operation === 'CONFIG_REPORT_DRAFT'">DRAFT · 未发布</template><template v-else>DISABLED · 未发布</template></a-tag></header>
                      <dl v-if="turn.generatedDraftProposal.result.operation === 'FLOW_DEFINITION_DRAFT'">
                        <div><dt>定义 ID</dt><dd>{{ turn.generatedDraftProposal.result.flowDefinition.definitionId }}</dd></div>
                        <div><dt>草稿修订</dt><dd>{{ turn.generatedDraftProposal.result.flowDefinition.revision }}</dd></div>
                        <div><dt>发布状态</dt><dd>{{ turn.generatedDraftProposal.result.flowDefinition.published ? '已发布' : '未发布/未激活' }}</dd></div>
                        <div class="wide"><dt>Flow 名称</dt><dd>{{ turn.generatedDraftProposal.result.flowDefinition.name }}</dd></div>
                        <div class="wide"><dt>有序审批成员</dt><dd>{{ turn.generatedDraftProposal.result.flowDefinition.approverMemberIds.join(' → ') }}</dd></div>
                        <div><dt>更新时间</dt><dd>{{ formatTime(turn.generatedDraftProposal.result.flowDefinition.updatedAt) }}</dd></div>
                      </dl>
                      <dl v-else-if="turn.generatedDraftProposal.result.operation === 'CONFIG_REPORT_DRAFT'">
                        <div><dt>报表 ID</dt><dd>{{ turn.generatedDraftProposal.result.reportDefinition.reportId }}</dd></div>
                        <div><dt>草稿版本</dt><dd>{{ turn.generatedDraftProposal.result.reportDefinition.draftVersion }}</dd></div>
                        <div><dt>报表编码</dt><dd>{{ turn.generatedDraftProposal.result.reportDefinition.code }}</dd></div>
                        <div><dt>报表名称</dt><dd>{{ turn.generatedDraftProposal.result.reportDefinition.name }}</dd></div>
                        <div><dt>数据源</dt><dd>{{ turn.generatedDraftProposal.result.reportDefinition.dataSourceId }}</dd></div>
                        <div><dt>实体版本</dt><dd>{{ turn.generatedDraftProposal.result.reportDefinition.version }}</dd></div>
                        <div><dt>发布状态</dt><dd>{{ turn.generatedDraftProposal.result.reportDefinition.published ? '已发布' : '未发布' }}</dd></div>
                        <div class="wide"><dt>输出字段</dt><dd>{{ turn.generatedDraftProposal.result.reportDefinition.outputFieldCodes.join(' · ') }}</dd></div>
                        <div><dt>创建时间</dt><dd>{{ formatTime(turn.generatedDraftProposal.result.reportDefinition.createdAt) }}</dd></div>
                        <div><dt>更新时间</dt><dd>{{ formatTime(turn.generatedDraftProposal.result.reportDefinition.updatedAt) }}</dd></div>
                      </dl>
                      <dl v-else>
                        <div><dt>模板 ID</dt><dd>{{ turn.generatedDraftProposal.result.printTemplate.templateId }}</dd></div>
                        <div><dt>模板状态</dt><dd>{{ turn.generatedDraftProposal.result.printTemplate.status }} · 未发布</dd></div>
                        <div><dt>模块</dt><dd>{{ turn.generatedDraftProposal.result.printTemplate.moduleCode }} · {{ turn.generatedDraftProposal.result.printTemplate.moduleId }}</dd></div>
                        <div><dt>模板编码</dt><dd>{{ turn.generatedDraftProposal.result.printTemplate.code }}</dd></div>
                        <div><dt>模板名称</dt><dd>{{ turn.generatedDraftProposal.result.printTemplate.name }}</dd></div>
                        <div><dt>纸张</dt><dd>{{ turn.generatedDraftProposal.result.printTemplate.paperSize }} · {{ turn.generatedDraftProposal.result.printTemplate.orientation }}</dd></div>
                        <div class="wide"><dt>打印标题</dt><dd>{{ turn.generatedDraftProposal.result.printTemplate.title }}</dd></div>
                        <div class="wide"><dt>打印字段</dt><dd>{{ turn.generatedDraftProposal.result.printTemplate.fieldCodes.join(' · ') }}</dd></div>
                        <div class="wide"><dt>页脚</dt><dd>{{ turn.generatedDraftProposal.result.printTemplate.footer || '未填写' }}</dd></div>
                        <div><dt>实体版本</dt><dd>{{ turn.generatedDraftProposal.result.printTemplate.version }}</dd></div>
                        <div><dt>发布状态</dt><dd>{{ turn.generatedDraftProposal.result.printTemplate.published ? '已发布' : '未发布' }}</dd></div>
                        <div><dt>更新时间</dt><dd>{{ formatTime(turn.generatedDraftProposal.result.printTemplate.updatedAt) }}</dd></div>
                      </dl>
                      <small>request {{ turn.generatedDraftProposal.requestId }} · trace {{ turn.generatedDraftProposal.traceId }}</small>
                    </section>
                  </section>
                  <a-alert v-if="turn.status === 'FAILED' || turn.status === 'RETRYABLE'" type="error" :message="turn.errorCode || 'Agent 本轮执行失败'" />
                  <small v-if="turn.requestId || turn.traceId">request {{ turn.requestId || '—' }} · trace {{ turn.traceId || '—' }}</small>
                  <small v-else-if="turn.createdAt">{{ formatTime(turn.createdAt) }}<template v-if="turn.returnedRows !== undefined"> · 返回 {{ turn.returnedRows }} 行</template></small>
                </article>
                <a-empty v-if="!detail.messages.length && !detail.turns.length" description="输入问题开始查询，或提出记录创建/更新需求" />
              </div>
              <div class="agent-composer">
                <textarea v-model="messageText" class="agent-message-input" rows="4" maxlength="4000" placeholder="查询记录，或描述希望创建/更新的内容…" />
                <div><span>{{ messageText.length }} / 4000</span><a-button class="agent-message-submit" type="primary" :loading="submitting" :disabled="!messageText.trim()" @click="submit()"><Send :size="15" />发送</a-button></div>
              </div>
              <a-alert v-if="submitError" class="agent-submit-error" type="error" show-icon :message="submitError">
                <template v-if="retryable" #action><a-button class="agent-message-retry" size="small" :loading="submitting" @click="submit(lastSubmitted)"><RotateCcw :size="14" />重试</a-button></template>
              </a-alert>
            </template>
            <section v-else class="agent-no-selection"><MessageSquarePlus :size="30" /><p>选择或创建一个会话。</p></section>
          </a-spin>
        </main>
      </div>
    </a-spin>
  </section>
</template>

<style scoped>
.system-agent-page{display:grid;gap:14px;max-width:1440px;margin:0 auto;padding:22px}.agent-page-header,.agent-page-header>div,.agent-session-sidebar>header,.agent-session-sidebar>header>div,.agent-conversation-header,.agent-message header,.agent-message header>span,.agent-composer>div{display:flex;align-items:center}.agent-page-header,.agent-session-sidebar>header,.agent-conversation-header,.agent-composer>div{justify-content:space-between;gap:12px}.agent-page-header h1,.agent-page-header p,.agent-conversation-header h2{margin:0}.agent-page-header h1{display:flex;align-items:center;gap:8px;font-size:23px}.agent-page-header p{margin-top:5px;color:#66757c}.agent-load-error{margin-bottom:2px}.agent-unavailable-state{display:grid;justify-items:center;gap:9px;padding:70px 24px;border:1px solid #e2e7e9;border-radius:10px;background:#fff;text-align:center}.agent-unavailable-state h2,.agent-unavailable-state p{margin:0}.agent-unavailable-state p{color:#66757c}.agent-layout{display:grid;grid-template-columns:290px minmax(0,1fr);min-height:650px;border:1px solid #dce4e7;border-radius:10px;background:#fff;overflow:hidden}.agent-session-sidebar{padding:14px;border-right:1px solid #dce4e7}.agent-session-sidebar>header>div{gap:7px}.agent-session-sidebar small,.agent-session-list span,.agent-conversation-header small,.agent-message time,.agent-composer span{color:#718087;font-size:12px}.agent-session-create{display:grid;gap:7px;margin:13px 0}.agent-session-title,.agent-message-input{width:100%;padding:8px 10px;border:1px solid #cbd5d9;border-radius:7px}.agent-session-list{display:grid;gap:7px}.agent-session-list button{display:grid;gap:4px;padding:10px;border:1px solid #e0e6e8;border-radius:7px;background:#fff;text-align:left}.agent-session-list button.active{border-color:#278f82;background:#eef8f6}.agent-conversation{min-width:0;padding:16px}.agent-conversation-header{padding-bottom:12px;border-bottom:1px solid #e2e7e9}.agent-conversation-header>div{display:grid;gap:3px}.agent-message-list{display:grid;align-content:start;gap:12px;min-height:440px;max-height:580px;padding:15px 0;overflow:auto}.agent-message{display:grid;gap:8px;max-width:82%;padding:12px;border-radius:9px;background:#f3f6f7}.agent-message.user{justify-self:end;background:#eaf7f4}.agent-message header{justify-content:space-between;gap:12px}.agent-message header>span{gap:6px}.agent-message p{margin:0;white-space:pre-wrap}.agent-tool-result{display:grid;gap:6px;padding:10px;border:1px solid #d9e3e5;border-radius:7px;background:#fff}.agent-tool-result pre{max-height:220px;margin:0;padding:9px;overflow:auto;background:#182228;color:#edf4f5;font-size:12px}.agent-composer{display:grid;gap:7px;padding-top:12px;border-top:1px solid #e2e7e9}.agent-message-input{resize:vertical}.agent-submit-error{margin-top:10px}.agent-no-selection{display:grid;justify-items:center;gap:8px;padding:160px 20px;color:#718087}
.agent-turn{display:grid;gap:8px;max-width:82%;padding:12px;border:1px solid #dce4e7;border-radius:9px;background:#fff}.agent-turn header,.agent-turn header>span{display:flex;align-items:center;gap:6px}.agent-turn header{justify-content:space-between}.agent-turn p{margin:0;white-space:pre-wrap}.agent-turn small{color:#718087;font-size:12px}
.agent-context-result{display:grid;gap:9px;padding:11px;border:1px solid #bcd8d2;border-radius:8px;background:#f3faf8}.agent-context-result>header,.agent-context-result>header>div,.agent-record-context-result>header,.agent-record-context-result>header>div,.agent-work-task-result article>header,.agent-work-task-result article>header>div,.agent-work-report-result article>header,.agent-work-report-result article>header>div,.agent-todo-result article>header,.agent-todo-result article>header>div,.agent-system-message-result article>header,.agent-system-message-result article>header>div{display:flex;align-items:center;justify-content:space-between;gap:8px}.agent-context-result>header>div,.agent-record-context-result>header>div,.agent-work-task-result article>header>div,.agent-work-report-result article>header>div,.agent-todo-result article>header>div,.agent-system-message-result article>header>div{display:grid;justify-content:start}.agent-context-result>header span,.agent-record-context-result>header span,.agent-work-task-result article>header span,.agent-work-report-result article>header span,.agent-todo-result article>header span,.agent-system-message-result article>header span{color:#68777e;font-size:12px}.agent-record-context-result,.agent-work-task-result,.agent-work-report-result,.agent-todo-result,.agent-system-message-result{display:grid;gap:8px}.agent-record-context-result,.agent-work-task-result article,.agent-work-report-result article,.agent-todo-result article,.agent-system-message-result article{padding:9px;border:1px solid #dce8e5;border-radius:7px;background:#fff}.agent-record-context-result dl,.agent-work-task-result dl,.agent-work-report-result dl,.agent-todo-result dl,.agent-system-message-result dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px;margin:8px 0 0}.agent-context-query-summary{padding:9px;border:1px solid #dce8e5;border-radius:7px;background:#fff}.agent-context-result dl .wide{grid-column:1/-1}.agent-context-result dt{color:#68777e;font-size:11px}.agent-context-result dd{margin:0;overflow-wrap:anywhere}.agent-system-message-result p{margin:8px 0 0;white-space:pre-wrap}.agent-record-context-values{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:6px;margin-top:8px}.agent-record-context-values article{display:grid;gap:2px;padding:7px;border-radius:5px;background:#f5f8f8}.agent-record-context-values span{color:#68777e;font-size:11px}.agent-context-loading,.agent-context-error{margin:0}.agent-context-empty{padding:7px}
.agent-record-comment-result,.agent-record-history-result,.agent-record-file-result{display:grid;gap:8px}.agent-record-comment-result>header,.agent-record-comment-result>header>div,.agent-record-comment-result article>header,.agent-record-comment-result article>header>div,.agent-record-history-result>header,.agent-record-history-result>header>div,.agent-record-history-result article>header,.agent-record-history-result article>header>div,.agent-record-file-result>header,.agent-record-file-result>header>div,.agent-record-file-result article>header,.agent-record-file-result article>header>div{display:flex;align-items:center;justify-content:space-between;gap:8px}.agent-record-comment-result>header>div,.agent-record-comment-result article>header>div,.agent-record-history-result>header>div,.agent-record-history-result article>header>div,.agent-record-file-result>header>div,.agent-record-file-result article>header>div{display:grid;justify-content:start}.agent-record-comment-result>header span,.agent-record-comment-result article>header span,.agent-record-comment-result small,.agent-record-history-result>header span,.agent-record-history-result article>header span,.agent-record-history-result small,.agent-record-file-result>header span,.agent-record-file-result article>header span{color:#68777e;font-size:12px}.agent-record-comment-result>article,.agent-record-history-result>article,.agent-record-file-result>article{display:grid;gap:7px;padding:9px;border:1px solid #dce8e5;border-radius:7px;background:#fff}.agent-record-comment-result p{margin:0;white-space:pre-wrap}.agent-record-comment-tombstone{color:#68777e;font-style:italic}.agent-record-activity-route{color:#167a6c;text-decoration:none}.agent-record-history-diffs{display:grid;gap:6px}.agent-record-history-diffs>div{display:grid;grid-template-columns:minmax(100px,.5fr) minmax(0,1.5fr);gap:8px;padding:7px;border-radius:5px;background:#f5f8f8}.agent-record-history-values{display:grid;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr);gap:6px;align-items:center}.agent-record-history-values code{overflow-wrap:anywhere;white-space:pre-wrap}.agent-record-history-masked{color:#7a6870}.agent-record-file-result dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px;margin:0}.agent-record-file-result dl>div{display:grid;gap:2px;padding:7px;border-radius:5px;background:#f5f8f8}.agent-record-file-result dt{color:#68777e;font-size:11px}.agent-record-file-result dd{margin:0;overflow-wrap:anywhere}
.agent-work-project-metrics-result{display:grid;gap:9px;padding:9px;border:1px solid #dce8e5;border-radius:7px;background:#fff}.agent-work-project-metrics-result>header,.agent-work-project-metrics-result>header>div,.agent-work-project-daily article,.agent-work-project-assignees article{display:flex;align-items:center;justify-content:space-between;gap:8px}.agent-work-project-metrics-result>header>div{display:grid;justify-content:start}.agent-work-project-metrics-result>header span,.agent-work-project-metrics-result>small,.agent-work-project-kpis span,.agent-work-project-daily span,.agent-work-project-assignees span{color:#68777e;font-size:12px}.agent-work-project-progress,.agent-work-project-kpis{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:7px;margin:0}.agent-work-project-progress>div,.agent-work-project-kpis article{display:grid;gap:3px;padding:8px;border-radius:6px;background:#f5f8f8}.agent-work-project-kpis strong{font-size:20px}.agent-work-project-metrics-result a{color:#167a6c;text-decoration:none}.agent-work-project-daily,.agent-work-project-assignees{display:grid;gap:6px}.agent-work-project-daily h4,.agent-work-project-assignees h4{margin:3px 0}.agent-work-project-daily article,.agent-work-project-assignees article{padding:7px;border-top:1px solid #e6ecea}.agent-work-project-daily article>span:last-child{display:flex;gap:8px}
.agent-runtime-statistics-result{display:grid;gap:9px;padding:9px;border:1px solid #dce8e5;border-radius:7px;background:#fff}.agent-runtime-statistics-result>header,.agent-runtime-statistics-result>header>div,.agent-runtime-statistics-buckets>header,.agent-runtime-statistics-buckets>header>div,.agent-runtime-statistics-buckets article,.agent-runtime-statistics-buckets article>div{display:flex;align-items:center;justify-content:space-between;gap:8px}.agent-runtime-statistics-result>header>div,.agent-runtime-statistics-buckets>header>div,.agent-runtime-statistics-buckets article>div{display:grid;justify-content:start}.agent-runtime-statistics-result>header span,.agent-runtime-statistics-buckets header span,.agent-runtime-statistics-buckets article span{color:#68777e;font-size:12px}.agent-runtime-statistics-kpis{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:7px;margin:0}.agent-runtime-statistics-kpis>div{display:grid;gap:3px;padding:8px;border-radius:6px;background:#f5f8f8}.agent-runtime-statistics-kpis dd{font-size:18px}.agent-runtime-statistics-buckets{display:grid;gap:6px}.agent-runtime-statistics-buckets h4{margin:3px 0}.agent-runtime-statistics-buckets article{padding:7px;border-top:1px solid #e6ecea}.agent-runtime-statistics-buckets article>strong{overflow-wrap:anywhere}
.agent-runtime-report-result{display:grid;gap:9px;padding:9px;border:1px solid #dce8e5;border-radius:7px;background:#fff}.agent-runtime-report-result>header,.agent-runtime-report-result>header>div{display:flex;align-items:center;justify-content:space-between;gap:8px}.agent-runtime-report-result>header>div{display:grid;justify-content:start}.agent-runtime-report-result>header span,.agent-runtime-report-result>small,.agent-runtime-report-table th small{color:#68777e;font-size:12px}.agent-runtime-report-route{color:#167a6c;text-decoration:none}.agent-runtime-report-kpis{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:7px;margin:0}.agent-runtime-report-kpis>div{display:grid;gap:3px;padding:8px;border-radius:6px;background:#f5f8f8}.agent-runtime-report-kpis dd{font-size:18px}.agent-runtime-report-table-wrap{overflow:auto;border:1px solid #e0e7e9;border-radius:7px}.agent-runtime-report-table{width:100%;border-collapse:collapse;white-space:nowrap}.agent-runtime-report-table th,.agent-runtime-report-table td{padding:8px 10px;border-bottom:1px solid #e7edef;text-align:left}.agent-runtime-report-table th{background:#f6f9f9}.agent-runtime-report-table th small{display:block;margin-top:2px;font-weight:400}
.agent-flow-instance-history-result{display:grid;gap:9px;padding:9px;border:1px solid #dce8e5;border-radius:7px;background:#fff}.agent-flow-instance-history-result>header,.agent-flow-instance-history-result>header>div,.agent-flow-instance-history-events li>header,.agent-flow-instance-history-events li>header>div{display:flex;align-items:center;justify-content:space-between;gap:8px}.agent-flow-instance-history-result>header>div,.agent-flow-instance-history-events li>header>div{display:grid;justify-content:start}.agent-flow-instance-history-result span,.agent-flow-instance-history-result small{color:#68777e;font-size:12px}.agent-flow-instance-history-route{color:#167a6c;text-decoration:none}.agent-flow-instance-history-events{display:grid;gap:7px;margin:0;padding:0;list-style:none}.agent-flow-instance-history-events li{display:grid;gap:6px;padding:9px;border:1px solid #e0e7e9;border-radius:7px}.agent-flow-instance-history-events p{margin:0;white-space:pre-wrap}
.agent-confirmation{display:grid;gap:11px;padding:12px;border:1px solid #e2c27c;border-radius:8px;background:#fffaf0}.agent-confirmation.confirmed,.agent-confirmation.succeeded{border-color:#9bd0c4;background:#f2fbf9}.agent-confirmation.failed,.agent-confirmation.conflicted,.agent-confirmation.expired{border-color:#e0aaaa;background:#fff6f6}.agent-confirmation>header,.agent-confirmation-result>header{display:flex;align-items:center;justify-content:space-between;gap:10px}.agent-confirmation>header>div{display:grid;gap:3px}.agent-confirmation>header span{color:#64747b;font-size:12px}.agent-confirmation-target,.agent-confirmation-result dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px;margin:0}.agent-confirmation-target>div,.agent-confirmation-result dl>div{display:grid;gap:2px;padding:7px;background:#fff;border-radius:5px}.agent-confirmation dt{color:#6c7a80;font-size:11px}.agent-confirmation dd{margin:0;overflow-wrap:anywhere}.agent-confirmation-fields{overflow:auto}.agent-confirmation table{width:100%;border-collapse:collapse;background:#fff}.agent-confirmation th,.agent-confirmation td{padding:7px;border:1px solid #dfe5e7;text-align:left;vertical-align:top}.agent-confirmation th{font-size:12px}.agent-confirmation th small{display:block;font-weight:400}.agent-confirmation-clarifications{padding:9px;border-radius:6px;background:#fff1d5}.agent-confirmation-clarifications ul{margin:6px 0 0;padding-left:20px}.agent-confirmation-actions{display:flex;align-items:center;justify-content:flex-end;gap:8px}.agent-confirmation-actions>span{margin-right:auto;color:#65747b;font-size:12px}.agent-confirmation-result{display:grid;gap:8px;padding:10px;border:1px solid #9bd0c4;border-radius:7px;background:#edf9f6}.agent-confirmation-error{margin-top:2px}
.config-field-proposal{display:grid;gap:10px;padding:12px;border:1px solid #d5bd80;border-radius:8px;background:#fffaf0}.config-field-proposal.succeeded{border-color:#91cdbf;background:#f2fbf9}.config-field-proposal.failed,.config-field-proposal.expired{border-color:#e0aaaa;background:#fff6f6}.config-field-proposal>header,.config-field-proposal>header>div,.config-field-proposal-result>header,.config-field-proposal-actions{display:flex;align-items:center;justify-content:space-between;gap:8px}.config-field-proposal>header>div{display:grid}.config-field-proposal>header span,.config-field-proposal-meta,.config-field-proposal-actions>span,.config-field-proposal-result>small{color:#65747b;font-size:12px}.config-field-proposal-preview,.config-field-proposal-result dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px;margin:0}.config-field-proposal-preview>div,.config-field-proposal-result dl>div{display:grid;gap:2px;padding:7px;border-radius:5px;background:#fff}.config-field-proposal dt{color:#6c7a80;font-size:11px}.config-field-proposal dd{margin:0;overflow-wrap:anywhere}.config-field-proposal-meta{display:flex;gap:14px}.config-field-proposal-actions{justify-content:flex-end}.config-field-proposal-actions>span{margin-right:auto}.config-field-proposal-clarification,.config-field-proposal-error{margin:0}.config-field-proposal-result{display:grid;gap:8px;padding:10px;border:1px solid #91cdbf;border-radius:7px;background:#edf9f6}
  .config-artifact-proposal{display:grid;gap:10px;padding:12px;border:1px solid #c8b67e;border-radius:8px;background:#fffaf0}.config-artifact-proposal.succeeded{border-color:#88c9ba;background:#f2fbf9}.config-artifact-proposal.failed,.config-artifact-proposal.expired{border-color:#dfa8a8;background:#fff6f6}.config-artifact-proposal>header,.config-artifact-proposal>header>div,.config-artifact-selection-preview>header,.config-artifact-selection-preview>header>div,.config-artifact-page-preview>header,.config-artifact-page-preview>header>div,.config-artifact-proposal-result>header,.config-artifact-proposal-actions,.config-artifact-sections article>div{display:flex;align-items:center;justify-content:space-between;gap:8px}.config-artifact-proposal>header>div,.config-artifact-selection-preview>header>div,.config-artifact-page-preview>header>div{display:grid;justify-content:start}.config-artifact-proposal>header span,.config-artifact-selection-preview>header span,.config-artifact-page-preview>header span,.config-artifact-proposal-meta,.config-artifact-proposal-actions>span,.config-artifact-proposal-result>small,.config-artifact-sections span,.config-artifact-sections small,.config-artifact-options small{color:#65747b;font-size:12px}.config-artifact-common-preview,.config-artifact-selection-preview dl,.config-artifact-page-preview dl,.config-artifact-result-summary,.config-artifact-selection-result dl,.config-artifact-page-result dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px;margin:0}.config-artifact-common-preview>div,.config-artifact-selection-preview dl>div,.config-artifact-page-preview dl>div,.config-artifact-result-summary>div,.config-artifact-selection-result dl>div,.config-artifact-page-result dl>div{display:grid;gap:2px;padding:7px;border-radius:5px;background:#fff}.config-artifact-proposal dt{color:#6c7a80;font-size:11px}.config-artifact-proposal dd{margin:0;overflow-wrap:anywhere}.config-artifact-selection-preview,.config-artifact-page-preview,.config-artifact-selection-result,.config-artifact-page-result{display:grid;gap:8px;padding:9px;border:1px solid #ded5b6;border-radius:7px;background:#fffdf8}.config-artifact-options,.config-artifact-sections{display:grid;gap:6px}.config-artifact-options article{display:grid;grid-template-columns:auto minmax(0,1fr) auto auto;align-items:center;gap:8px;padding:7px;border:1px solid #e0e5e7;border-radius:6px;background:#fff}.config-artifact-options article>div{display:grid}.config-artifact-options i{width:14px;height:14px;border:1px solid #cbd5d9;border-radius:50%}.config-artifact-option-order{display:grid;place-items:center;width:22px;height:22px;border-radius:50%;background:#edf3f4;font-size:11px}.config-artifact-sections article{display:grid;gap:4px;padding:8px;border:1px solid #e0e5e7;border-radius:6px;background:#fff}.config-artifact-proposal-meta{display:flex;gap:14px}.config-artifact-proposal-actions{justify-content:flex-end}.config-artifact-proposal-actions>span{margin-right:auto}.config-artifact-proposal-loading,.config-artifact-proposal-clarification,.config-artifact-proposal-error{margin:0}.config-artifact-proposal-result{display:grid;gap:9px;padding:10px;border:1px solid #88c9ba;border-radius:7px;background:#edf9f6}.config-artifact-empty{padding:6px}
  .work-proposal{display:grid;gap:10px;padding:12px;border:1px solid #c8b67e;border-radius:8px;background:#fffaf0}.work-proposal.succeeded{border-color:#88c9ba;background:#f2fbf9}.work-proposal.failed,.work-proposal.expired,.work-proposal.stale,.work-proposal.permission_denied{border-color:#dfa8a8;background:#fff6f6}.work-proposal>header,.work-proposal>header>div,.work-proposal-result>header,.work-proposal-actions{display:flex;align-items:center;justify-content:space-between;gap:8px}.work-proposal>header>div{display:grid;justify-content:start}.work-proposal>header span,.work-proposal-meta,.work-proposal-actions>span,.work-proposal-result>small{color:#65747b;font-size:12px}.work-proposal-preview,.work-proposal-result dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px;margin:0}.work-proposal-preview>div,.work-proposal-result dl>div{display:grid;gap:2px;padding:7px;border-radius:5px;background:#fff}.work-proposal-preview .wide,.work-proposal-result .wide{grid-column:1/-1}.work-proposal dt{color:#6c7a80;font-size:11px}.work-proposal dd{margin:0;overflow-wrap:anywhere;white-space:pre-wrap}.work-proposal-meta{display:flex;gap:14px}.work-proposal-actions{justify-content:flex-end}.work-proposal-actions>span{margin-right:auto}.work-proposal-loading,.work-proposal-clarification,.work-proposal-error{margin:0}.work-proposal-result{display:grid;gap:9px;padding:10px;border:1px solid #88c9ba;border-radius:7px;background:#edf9f6}.work-proposal-empty{padding:6px}
  .generated-draft-proposal{display:grid;gap:10px;padding:12px;border:1px solid #c8b67e;border-radius:8px;background:#fffaf0}.generated-draft-proposal.succeeded{border-color:#88c9ba;background:#f2fbf9}.generated-draft-proposal.failed,.generated-draft-proposal.expired,.generated-draft-proposal.stale,.generated-draft-proposal.permission_denied{border-color:#dfa8a8;background:#fff6f6}.generated-draft-proposal>header,.generated-draft-proposal>header>div,.generated-draft-result>header,.generated-draft-actions{display:flex;align-items:center;justify-content:space-between;gap:8px}.generated-draft-proposal>header>div{display:grid;justify-content:start}.generated-draft-proposal>header span,.generated-draft-meta,.generated-draft-actions>span,.generated-draft-result>small{color:#65747b;font-size:12px}.generated-draft-preview,.generated-draft-result{display:grid;gap:8px}.generated-draft-preview{padding:9px;border:1px solid #ded5b6;border-radius:7px;background:#fffdf8}.generated-draft-preview dl,.generated-draft-result dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px;margin:0}.generated-draft-preview dl>div,.generated-draft-result dl>div{display:grid;gap:2px;padding:7px;border-radius:5px;background:#fff}.generated-draft-preview .wide,.generated-draft-result .wide{grid-column:1/-1}.generated-draft-proposal dt{color:#6c7a80;font-size:11px}.generated-draft-proposal dd{margin:0;overflow-wrap:anywhere;white-space:pre-wrap}.generated-draft-meta{display:flex;gap:14px}.generated-draft-actions{justify-content:flex-end}.generated-draft-actions>span{margin-right:auto}.generated-draft-loading,.generated-draft-clarification,.generated-draft-error{margin:0}.generated-draft-result{padding:10px;border:1px solid #88c9ba;border-radius:7px;background:#edf9f6}.generated-draft-empty{padding:6px}
</style>
