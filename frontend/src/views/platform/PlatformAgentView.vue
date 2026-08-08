<script setup lang="ts">
import { ArrowRight, Bot, Building2, MessageSquarePlus, RefreshCw, RotateCcw, Send, ShieldAlert, UserRound } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiRequestError } from '@/services/api'
import { platformAiRuntimeApi } from '@/services/platformAi'
import { useSessionStore } from '@/stores/session'
import type {
  PlatformAiAuthorizedSystem,
  PlatformAiCapability,
  PlatformAiConversationDetail,
  PlatformAiDisplayTurn,
  PlatformAiSession,
  PlatformAiStoredTurn,
  PlatformAiTaskProposal,
  PlatformAiTurn,
} from '@/types/platformAi'

const router = useRouter()
const sessionStore = useSessionStore()
const capability = ref<PlatformAiCapability | null>(null)
const sessions = ref<PlatformAiSession[]>([])
const hasMoreSessions = ref(false)
const detail = ref<PlatformAiConversationDetail | null>(null)
const selectedSessionId = ref('')
const newTitle = ref('')
const messageText = ref('')
const lastSubmitted = ref('')
const loading = ref(false)
const detailLoading = ref(false)
const creating = ref(false)
const submitting = ref(false)
const switchingTarget = ref('')
const loadError = ref('')
const loadErrorCode = ref('')
const submitError = ref('')
const retryable = ref(false)
const proposalActions = reactive<Record<string, 'confirm' | 'reject' | undefined>>({})
const proposalErrors = reactive<Record<string, string | undefined>>({})
const proposalKeys = new Map<string, string>()

const unavailableState = computed(() => {
  if (loadErrorCode.value && /(PERMISSION|FORBIDDEN|ACCESS_DENIED)/iu.test(loadErrorCode.value)) {
    return {
      kind: 'denied', title: '无权使用平台 Agent',
      description: '当前平台角色缺少运行访问或 Agent 使用权限，未创建平台 Agent 会话。',
      reason: loadErrorCode.value,
    }
  }
  if (capability.value?.available !== false) return null
  const reason = capability.value.reason ?? 'POLICY_UNCONFIGURED'
  if (/DISABLED/iu.test(reason)) {
    return { kind: 'disabled', title: '平台 Agent 已停用', description: '平台管理员已停用当前发布策略。', reason }
  }
  if (/(PROVIDER|TIMEOUT|UNAVAILABLE|DEGRADED)/iu.test(reason)) {
    return { kind: 'provider', title: '平台 Agent Provider 暂不可用', description: '授权系统本身不受影响，可稍后重试 Agent。', reason }
  }
  return { kind: 'unconfigured', title: '平台 Agent 尚未配置', description: '请联系平台管理员配置 Provider 并发布只读策略。', reason }
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
  return /(TIMEOUT|UNAVAILABLE|RETRYABLE|PROVIDER_FAILED|PROVIDER)/iu.test(code)
}

function formatTime(value: string) {
  const parsed = new Date(value)
  return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleString('zh-CN')
}

function roleLabel(role: string) {
  return role === 'USER' ? '我' : 'Agent'
}

function accessColor(value: string) {
  if (/(AUTHORIZED|ALLOWED|ACCESSIBLE|ACTIVE|GRANTED)/iu.test(value)) return 'green'
  if (/(PENDING|INITIALIZING)/iu.test(value)) return 'orange'
  return 'default'
}

function isStoredTurn(turn: PlatformAiDisplayTurn): turn is PlatformAiStoredTurn {
  return 'responseSummary' in turn
}

function turnAnswer(turn: PlatformAiDisplayTurn) {
  return isStoredTurn(turn)
    ? turn.responseSummary
    : turn.answer
}

function turnSystems(turn: PlatformAiDisplayTurn) {
  return isStoredTurn(turn)
    ? turn.evidence?.systems ?? []
    : turn.systems
}

function turnGuidance(turn: PlatformAiDisplayTurn) {
  return isStoredTurn(turn)
    ? turn.evidence?.guidance ?? null
    : turn.guidance
}

function turnRequestId(turn: PlatformAiDisplayTurn) {
  return isStoredTurn(turn) ? '' : turn.requestId
}

function turnTraceId(turn: PlatformAiDisplayTurn) {
  return isStoredTurn(turn) ? '' : turn.traceId
}

function turnEvidenceType(turn: PlatformAiDisplayTurn) {
  return isStoredTurn(turn)
    ? turn.evidence?.type ?? ''
    : ''
}

function turnProposal(turn: PlatformAiDisplayTurn) {
  return turn.proposal
}

function turnOperations(turn: PlatformAiDisplayTurn) {
  return turn.operations
}

function operationsKindLabel(value: string | null) {
  const labels: Record<string, string> = {
    PERSONAL_TASKS: '个人任务', AI_QUOTA: 'AI 配额', SERVICE_HEALTH: '服务健康', AGENT_ACTIVITY: '个人 Agent 活动',
  }
  return value ? labels[value] ?? value : '需要补充查询条件'
}

function healthColor(value: string) {
  if (value === 'UP') return 'green'
  if (value === 'DEGRADED') return 'orange'
  return 'default'
}

function taskProposalState(value: PlatformAiTaskProposal) {
  if (value.state === 'PENDING' && new Date(value.expiresAt).valueOf() <= Date.now()) return 'EXPIRED'
  return value.state
}

function taskProposalStateLabel(value: PlatformAiTaskProposal) {
  const labels: Record<string, string> = {
    CLARIFICATION_REQUIRED: '需要补充信息', PENDING: '等待确认', EXECUTING: '正在创建',
    SUCCEEDED: '已创建', FAILED: '创建失败', REJECTED: '已拒绝', EXPIRED: '已过期',
  }
  return labels[taskProposalState(value)] ?? taskProposalState(value)
}

function taskProposalStateColor(value: PlatformAiTaskProposal) {
  const state = taskProposalState(value)
  if (state === 'SUCCEEDED') return 'green'
  if (state === 'PENDING') return 'orange'
  if (state === 'REJECTED') return 'default'
  return 'red'
}

function taskConfidence(value: number) {
  if (!Number.isFinite(value)) return '未提供'
  return `${Math.round((value <= 1 ? value * 100 : value))}%`
}

function canActOnTaskProposal(value: PlatformAiTaskProposal) {
  return taskProposalState(value) === 'PENDING' && !value.clarification
}

function replaceTaskProposal(value: PlatformAiTaskProposal) {
  if (!detail.value) return
  detail.value = {
    ...detail.value,
    turns: detail.value.turns.map(turn => turn.proposal?.id === value.id
      ? { ...turn, proposal: value } as PlatformAiDisplayTurn
      : turn),
  }
}

async function refreshTaskProposal(value: PlatformAiTaskProposal) {
  if (!selectedSessionId.value) return value
  const latest = await platformAiRuntimeApi.taskProposal(selectedSessionId.value, value.id)
  replaceTaskProposal(latest)
  return latest
}

function taskProposalFailure(error: unknown) {
  const code = errorCode(error)
  if (/(PERMISSION|FORBIDDEN|TASK_CREATE|AUTHORIZATION|CONTEXT)/iu.test(code)) {
    return '平台任务创建权限或授权上下文已变化，任务未创建。'
  }
  if (/(REVISION|VERSION|REPLAY|CONFLICT|IDEMPOTENCY)/iu.test(code)) {
    return '任务草稿版本或确认请求已变化，未重复创建任务。'
  }
  if (/EXPIRED/iu.test(code)) return '任务草稿已过期，任务未创建。'
  if (/STATE|REJECTED|CLARIFICATION/iu.test(code)) return '当前草稿状态不允许确认，任务未创建。'
  return requestError(error, '平台任务确认失败，任务未创建。')
}

async function confirmTaskProposal(value: PlatformAiTaskProposal) {
  if (!canActOnTaskProposal(value) || proposalActions[value.id] || !selectedSessionId.value) return
  proposalActions[value.id] = 'confirm'
  proposalErrors[value.id] = undefined
  const idempotencyKey = proposalKeys.get(value.id) ?? crypto.randomUUID()
  proposalKeys.set(value.id, idempotencyKey)
  try {
    replaceTaskProposal(await platformAiRuntimeApi.confirmTaskProposal(
      selectedSessionId.value, value.id, { expectedRevision: value.revision }, idempotencyKey,
    ))
  } catch (error) {
    proposalErrors[value.id] = taskProposalFailure(error)
    try { await refreshTaskProposal(value) } catch { /* retain the safe task preview */ }
  } finally {
    proposalActions[value.id] = undefined
  }
}

async function rejectTaskProposal(value: PlatformAiTaskProposal) {
  if (!canActOnTaskProposal(value) || proposalActions[value.id] || !selectedSessionId.value) return
  proposalActions[value.id] = 'reject'
  proposalErrors[value.id] = undefined
  try {
    replaceTaskProposal(await platformAiRuntimeApi.rejectTaskProposal(
      selectedSessionId.value, value.id, { expectedRevision: value.revision },
    ))
  } catch (error) {
    proposalErrors[value.id] = taskProposalFailure(error)
    try { await refreshTaskProposal(value) } catch { /* retain the safe task preview */ }
  } finally {
    proposalActions[value.id] = undefined
  }
}

async function load() {
  loading.value = true
  loadError.value = ''
  loadErrorCode.value = ''
  detail.value = null
  selectedSessionId.value = ''
  try {
    capability.value = await platformAiRuntimeApi.capability()
    if (!capability.value.available) {
      sessions.value = []
      hasMoreSessions.value = false
      return
    }
    const page = await platformAiRuntimeApi.sessions(1, 50)
    sessions.value = page.rows
    hasMoreSessions.value = page.hasMore
    if (page.rows[0]) await selectSession(page.rows[0])
  } catch (error) {
    loadErrorCode.value = errorCode(error)
    loadError.value = requestError(error, '平台 Agent 会话加载失败')
  } finally {
    loading.value = false
  }
}

async function selectSession(value: PlatformAiSession) {
  selectedSessionId.value = value.id
  detailLoading.value = true
  loadError.value = ''
  loadErrorCode.value = ''
  try {
    detail.value = await platformAiRuntimeApi.session(value.id)
  } catch (error) {
    loadErrorCode.value = errorCode(error)
    loadError.value = requestError(error, '平台 Agent 会话详情加载失败')
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
    const created = await platformAiRuntimeApi.createSession({ title })
    sessions.value = [created, ...sessions.value.filter(item => item.id !== created.id)]
    newTitle.value = ''
    await selectSession(created)
  } catch (error) {
    loadErrorCode.value = errorCode(error)
    loadError.value = requestError(error, '平台 Agent 会话创建失败')
  } finally {
    creating.value = false
  }
}

function appendTurn(turn: PlatformAiTurn, submittedContent: string) {
  if (!detail.value) return
  const index = detail.value.turns.findIndex(item => item.id === turn.id)
  const userMessage = {
    id: `local:${turn.id}`,
    role: 'USER' as const,
    status: 'LOCAL',
    content: submittedContent,
    createdAt: new Date().toISOString(),
  }
  detail.value = {
    ...detail.value,
    messages: detail.value.messages.some(item => item.id === userMessage.id)
      ? detail.value.messages
      : [...detail.value.messages, userMessage],
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
    const turn = await platformAiRuntimeApi.submitMessage(selectedSessionId.value, { content: value })
    appendTurn(turn, value)
    messageText.value = ''
    if (turn.status === 'FAILED' || turn.status === 'RETRYABLE') {
      submitError.value = turn.errorCode || '平台 Agent 本轮执行失败。'
      retryable.value = turn.retryable
    }
  } catch (error) {
    const code = errorCode(error)
    submitError.value = requestError(error, '平台 Agent 消息提交失败')
    retryable.value = isRetryable(code)
  } finally {
    submitting.value = false
  }
}

function targetSystemId(target: string | null | undefined) {
  if (!target) return ''
  const match = target.match(/\/systems\/([^/:?]+)/u)
  return match?.[1] ? decodeURIComponent(match[1]) : ''
}

async function enterSystem(systemId: string, switchTarget: string | null) {
  const resolvedSystemId = systemId || targetSystemId(switchTarget)
  if (!resolvedSystemId || switchingTarget.value) return
  switchingTarget.value = switchTarget || resolvedSystemId
  submitError.value = ''
  try {
    await sessionStore.switchSystem(resolvedSystemId)
    const runtimeTarget = switchTarget?.startsWith(`/systems/${resolvedSystemId}/`)
      ? switchTarget
      : `/systems/${resolvedSystemId}/workbench`
    await router.push(runtimeTarget)
  } catch (error) {
    submitError.value = requestError(error, '系统切换失败，当前成员资格或权限可能已变化。')
  } finally {
    switchingTarget.value = ''
  }
}

function enterProjectedSystem(value: PlatformAiAuthorizedSystem) {
  return enterSystem(value.systemId, value.switchTarget)
}

onMounted(load)
</script>

<template>
  <section class="platform-agent-page">
    <header class="platform-agent-header">
      <div><h1><Bot :size="23" />平台 Agent</h1><p>查询当前账号可进入的系统并获得明确切换引导；平台上下文不读取或写入系统业务记录。</p></div>
      <a-button aria-label="刷新平台 Agent" :loading="loading" :disabled="loading" @click="load"><RefreshCw :size="16" />刷新</a-button>
    </header>

    <a-alert class="platform-agent-boundary" type="info" show-icon message="平台元数据范围" description="仅返回系统名称、编码、状态、成员/访问状态和切换目标。业务数据请先切换系统，再由系统 Agent 或业务页面处理。" />
    <a-alert v-if="loadError && !unavailableState" class="platform-agent-load-error" type="error" show-icon role="alert" aria-live="assertive" :message="loadError"><template #action><a-button size="small" @click="load">重试</a-button></template></a-alert>

    <a-spin :spinning="loading">
      <section v-if="unavailableState" class="platform-agent-unavailable" :class="unavailableState.kind">
        <ShieldAlert v-if="unavailableState.kind === 'denied'" :size="34" /><Bot v-else :size="34" />
        <h2>{{ unavailableState.title }}</h2><p>{{ unavailableState.description }}</p><code>{{ unavailableState.reason }}</code>
        <a-button v-if="unavailableState.kind === 'provider'" class="platform-agent-provider-retry" @click="load"><RotateCcw :size="15" />重试 Provider</a-button>
      </section>

      <div v-else-if="capability?.available" class="platform-agent-layout" :aria-busy="loading || detailLoading">
        <aside class="platform-agent-sessions">
          <header><div><strong>会话</strong><small>{{ sessions.length }}{{ hasMoreSessions ? '+' : '' }} 个</small></div><a-tag>策略 {{ capability.policyVersion }}</a-tag></header>
          <div class="platform-agent-session-create"><input v-model="newTitle" class="platform-agent-session-title" aria-label="新会话标题" maxlength="120" placeholder="新会话标题" :disabled="creating" @keyup.enter="createSession" /><a-button class="platform-agent-session-create-button" type="primary" aria-label="创建平台 Agent 会话" :loading="creating" :disabled="creating || !newTitle.trim()" @click="createSession"><MessageSquarePlus :size="15" />创建</a-button></div>
          <nav class="platform-agent-session-list" aria-label="平台 Agent 会话">
            <button v-for="item in sessions" :key="item.id" type="button" :class="{ active: selectedSessionId === item.id }" @click="selectSession(item)"><strong>{{ item.title }}</strong><span>{{ item.status }} · {{ formatTime(item.updatedAt) }}</span></button>
          </nav>
          <a-empty v-if="!sessions.length" description="暂无会话，请先创建" />
        </aside>

        <main class="platform-agent-conversation">
          <a-spin :spinning="detailLoading">
            <template v-if="detail">
              <header class="platform-agent-conversation-header"><div><h2>{{ detail.session.title }}</h2><span>{{ detail.session.status }}</span></div><small>AUTHORIZED_SYSTEMS_QUERY · SYSTEM_SWITCH_GUIDANCE · PLATFORM_OPERATIONS_QUERY（只读） · PLATFORM_TASK_DRAFT（需确认）</small></header>
              <div class="platform-agent-message-list">
                <article v-for="message in detail.messages" :key="message.id" class="platform-agent-message" :class="message.role.toLowerCase()"><header><span><UserRound v-if="message.role === 'USER'" :size="15" /><Bot v-else :size="15" />{{ roleLabel(message.role) }}</span></header><p>{{ message.content }}</p><time>{{ formatTime(message.createdAt) }}</time></article>
                <article v-for="turn in detail.turns" :key="`turn:${turn.id}`" class="platform-agent-turn" :class="turn.status.toLowerCase()">
                  <header><span><Bot :size="15" />{{ turn.operation || '安全边界处理' }}</span><a-tag :color="turn.status === 'FAILED' ? 'red' : turn.status === 'SUCCEEDED' ? 'green' : 'orange'">{{ turn.status }}</a-tag></header>
                  <p>{{ turnAnswer(turn) || (turn.status === 'RUNNING' ? '处理中…' : turn.errorCode || '本轮未生成回答。') }}</p>
                  <small v-if="turnEvidenceType(turn)" class="platform-agent-evidence-type">安全证据 {{ turnEvidenceType(turn) }}</small>
                  <section v-if="turnSystems(turn).length" class="platform-agent-system-results">
                    <header><strong>当前账号可见的授权系统</strong><span>{{ turnSystems(turn).length }} 个权限投影结果</span></header>
                    <div class="platform-agent-system-list">
                      <article v-for="system in turnSystems(turn)" :key="system.systemId" class="platform-agent-system-result">
                        <Building2 :size="20" /><div><strong>{{ system.systemName }}</strong><small>{{ system.systemCode }} · {{ system.status }}</small><span>成员 {{ system.membershipState }} · 访问 {{ system.accessState }}</span></div>
                        <a-tag :color="accessColor(system.accessState)">{{ system.accessState }}</a-tag>
                        <a-button class="platform-agent-system-switch" :disabled="!system.switchTarget" :loading="switchingTarget === system.switchTarget" @click="enterProjectedSystem(system)">进入系统<ArrowRight :size="15" /></a-button>
                      </article>
                    </div>
                  </section>
                  <section v-if="turnGuidance(turn)" class="platform-agent-guidance">
                    <strong>系统切换引导</strong><p>{{ turnGuidance(turn)?.message }}</p><code v-if="turnGuidance(turn)?.requestedSystemCode">目标系统：{{ turnGuidance(turn)?.requestedSystemCode }}</code>
                    <a-button v-if="turnGuidance(turn)?.switchTarget" class="platform-agent-guidance-switch" type="primary" :loading="switchingTarget === turnGuidance(turn)?.switchTarget" @click="enterSystem('', turnGuidance(turn)?.switchTarget ?? null)">按引导切换<ArrowRight :size="15" /></a-button>
                  </section>
                  <section v-if="turn.operation === 'PLATFORM_OPERATIONS_QUERY' && turn.status === 'RUNNING' && !turnOperations(turn)" class="platform-operations-loading">
                    <strong>平台运营数据查询中</strong><span>正在读取当前账号可见的实时只读数据…</span>
                  </section>
                  <section v-if="turnOperations(turn)" class="platform-operations-card">
                    <header><div><strong>{{ operationsKindLabel(turnOperations(turn)!.queryKind) }}</strong><span>PLATFORM_OPERATIONS_QUERY · 只读运营数据回读</span></div><a-tag>置信度 {{ taskConfidence(turnOperations(turn)!.confidence) }}</a-tag></header>
                    <a-alert v-if="turnOperations(turn)!.clarification" class="platform-operations-clarification" type="warning" show-icon message="请补充查询条件" :description="turnOperations(turn)!.clarification!" />

                    <section v-if="turnOperations(turn)!.queryKind === 'PERSONAL_TASKS'" class="platform-operations-personal-tasks">
                      <a-empty v-if="!turnOperations(turn)!.personalTasks.length" class="platform-operations-empty" description="当前账号暂无个人任务" />
                      <article v-for="task in turnOperations(turn)!.personalTasks" :key="task.taskId">
                        <div><strong>{{ task.title }}</strong><span>{{ task.taskId }} · {{ task.status }} · {{ task.source }}</span></div><a-tag>{{ task.priority }}</a-tag>
                        <small>截止 {{ task.dueAt ? formatTime(task.dueAt) : '未设置' }} · 创建于 {{ formatTime(task.createdAt) }}</small>
                      </article>
                    </section>

                    <dl v-if="turnOperations(turn)!.queryKind === 'AI_QUOTA' && turnOperations(turn)!.quota" class="platform-operations-quota">
                      <div><dt>请求</dt><dd>{{ turnOperations(turn)!.quota!.requestCount }} / {{ turnOperations(turn)!.quota!.requestLimit }}（剩余 {{ turnOperations(turn)!.quota!.remainingRequests }}）</dd></div>
                      <div><dt>Token</dt><dd>已用 {{ turnOperations(turn)!.quota!.usedTokens }} · 预留 {{ turnOperations(turn)!.quota!.reservedTokens }} / {{ turnOperations(turn)!.quota!.tokenLimit }}（剩余 {{ turnOperations(turn)!.quota!.remainingTokens }}）</dd></div>
                      <div><dt>并发</dt><dd>{{ turnOperations(turn)!.quota!.runningCount }} / {{ turnOperations(turn)!.quota!.concurrencyLimit }}（剩余 {{ turnOperations(turn)!.quota!.remainingConcurrency }}）</dd></div>
                      <div><dt>周期</dt><dd>{{ formatTime(turnOperations(turn)!.quota!.periodStart) }} — {{ formatTime(turnOperations(turn)!.quota!.periodEnd) }}</dd></div>
                      <div><dt>策略版本</dt><dd>{{ turnOperations(turn)!.quota!.policyVersion }}</dd></div>
                    </dl>

                    <section v-if="turnOperations(turn)!.queryKind === 'SERVICE_HEALTH' && turnOperations(turn)!.serviceHealth" class="platform-operations-health">
                      <header><strong>平台服务</strong><a-tag :color="healthColor(turnOperations(turn)!.serviceHealth!.status)">{{ turnOperations(turn)!.serviceHealth!.status }}</a-tag></header>
                      <div v-for="component in turnOperations(turn)!.serviceHealth!.components" :key="component.component"><span>{{ component.component }}</span><a-tag :color="healthColor(component.status)">{{ component.status }}</a-tag></div>
                      <small>检查于 {{ formatTime(turnOperations(turn)!.serviceHealth!.checkedAt) }}</small>
                    </section>

                    <section v-if="turnOperations(turn)!.queryKind === 'AGENT_ACTIVITY'" class="platform-operations-activity">
                      <a-empty v-if="!turnOperations(turn)!.agentActivity.length" class="platform-operations-empty" description="当前账号暂无 Agent 活动" />
                      <article v-for="activity in turnOperations(turn)!.agentActivity" :key="`${activity.requestId}:${activity.time}`">
                        <div><strong>{{ activity.event }}</strong><a-tag>{{ activity.resultCode }}</a-tag></div>
                        <span>{{ activity.operation || '未识别操作' }} · {{ formatTime(activity.time) }}</span>
                        <small>request {{ activity.requestId }} · trace {{ activity.traceId }}</small>
                      </article>
                    </section>
                  </section>
                  <a-alert v-if="turn.operation === 'PLATFORM_OPERATIONS_QUERY' && !turnOperations(turn) && (turn.status === 'FAILED' || turn.status === 'RETRYABLE')" class="platform-operations-failure" type="error" show-icon message="平台运营数据查询失败" :description="turn.errorCode || '本轮查询未返回数据。'" />
                  <section v-if="turnProposal(turn)" class="platform-task-proposal" :class="taskProposalState(turnProposal(turn)!).toLowerCase()">
                    <header>
                      <div><strong>个人平台任务草稿</strong><span>PLATFORM_TASK_DRAFT · 仅分配给当前账号</span></div>
                      <a-tag :color="taskProposalStateColor(turnProposal(turn)!)">{{ taskProposalStateLabel(turnProposal(turn)!) }}</a-tag>
                    </header>
                    <dl v-if="turnProposal(turn)!.preview" class="platform-task-preview">
                      <div><dt>标题</dt><dd>{{ turnProposal(turn)!.preview!.title }}</dd></div>
                      <div><dt>优先级</dt><dd>{{ turnProposal(turn)!.preview!.priority }}</dd></div>
                      <div><dt>截止时间</dt><dd>{{ turnProposal(turn)!.preview!.dueAt ? formatTime(turnProposal(turn)!.preview!.dueAt!) : '未设置' }}</dd></div>
                      <div><dt>分配方式</dt><dd>{{ turnProposal(turn)!.preview!.selfAssigned ? '当前账号（自分配）' : '不允许' }}</dd></div>
                      <div v-if="turnProposal(turn)!.preview!.description" class="wide"><dt>描述</dt><dd>{{ turnProposal(turn)!.preview!.description }}</dd></div>
                    </dl>
                    <div class="platform-task-proposal-meta"><span>模型置信度 {{ taskConfidence(turnProposal(turn)!.confidence) }}</span><span>有效期至 {{ formatTime(turnProposal(turn)!.expiresAt) }}</span></div>
                    <a-alert v-if="turnProposal(turn)!.clarification" class="platform-task-clarification" type="warning" show-icon message="请补充信息" :description="turnProposal(turn)!.clarification!" />
                    <section v-if="turnProposal(turn)!.result" class="platform-task-result">
                      <strong>平台任务真实回读</strong>
                      <span>任务 {{ turnProposal(turn)!.result!.taskId }} · {{ turnProposal(turn)!.result!.status }} · {{ turnProposal(turn)!.result!.source }}</span>
                      <p>{{ turnProposal(turn)!.result!.title }}</p>
                      <small>创建于 {{ formatTime(turnProposal(turn)!.result!.createdAt) }}</small>
                    </section>
                    <a-alert v-if="turnProposal(turn)!.errorCode" class="platform-task-terminal-error" type="error" show-icon :message="turnProposal(turn)!.errorCode!" />
                    <a-alert v-if="proposalErrors[turnProposal(turn)!.id]" class="platform-task-action-error" type="error" show-icon :message="proposalErrors[turnProposal(turn)!.id]" />
                    <footer v-if="canActOnTaskProposal(turnProposal(turn)!)">
                      <span>确认前不会创建平台任务；确认时会重新检查当前权限。</span>
                      <a-button class="platform-task-reject" :loading="proposalActions[turnProposal(turn)!.id] === 'reject'" :disabled="Boolean(proposalActions[turnProposal(turn)!.id])" @click="rejectTaskProposal(turnProposal(turn)!)">拒绝草稿</a-button>
                      <a-button class="platform-task-confirm" type="primary" :loading="proposalActions[turnProposal(turn)!.id] === 'confirm'" :disabled="Boolean(proposalActions[turnProposal(turn)!.id])" @click="confirmTaskProposal(turnProposal(turn)!)">确认创建任务</a-button>
                    </footer>
                  </section>
                  <small v-if="turnRequestId(turn) || turnTraceId(turn)" class="platform-agent-evidence">request {{ turnRequestId(turn) || '—' }} · trace {{ turnTraceId(turn) || '—' }}</small>
                </article>
              </div>
              <a-alert v-if="submitError" class="platform-agent-submit-error" type="error" show-icon :message="submitError"><template #action><a-button v-if="retryable" class="platform-agent-message-retry" size="small" :loading="submitting" @click="submit(lastSubmitted)"><RotateCcw :size="14" />重试</a-button></template></a-alert>
              <div class="platform-agent-composer"><textarea v-model="messageText" class="platform-agent-message-input" aria-label="发送给平台 Agent 的消息" rows="3" maxlength="2000" placeholder="例如：我可以进入哪些系统？或：如何进入 CRM 系统？" :disabled="submitting" @keydown.ctrl.enter.prevent="submit()" /><a-button class="platform-agent-message-submit" type="primary" aria-label="发送消息" :loading="submitting" :disabled="submitting || !messageText.trim()" @click="submit()"><Send :size="15" />发送</a-button></div>
            </template>
            <a-empty v-else description="选择或创建一个平台 Agent 会话" />
          </a-spin>
        </main>
      </div>
    </a-spin>
  </section>
</template>

<style scoped>
.platform-agent-page{display:grid;gap:14px}.platform-agent-header{display:flex;align-items:center;justify-content:space-between;gap:16px}.platform-agent-header h1{display:flex;align-items:center;gap:8px;margin:0}.platform-agent-header p{margin:4px 0 0;color:#68777e}.platform-agent-boundary{margin:0}.platform-agent-unavailable{display:grid;justify-items:center;gap:9px;padding:54px 20px;border:1px dashed #d3dcdf;border-radius:10px;background:#fff;text-align:center}.platform-agent-unavailable h2,.platform-agent-unavailable p{margin:0}.platform-agent-unavailable p{color:#68777e}.platform-agent-unavailable.denied{border-color:#efc5c5;background:#fff7f7}.platform-agent-unavailable.provider{border-color:#ead6a7;background:#fff9ec}.platform-agent-layout{display:grid;grid-template-columns:280px minmax(0,1fr);min-height:580px;border:1px solid #dfe6e9;border-radius:10px;background:#fff;overflow:hidden}.platform-agent-sessions{padding:14px;border-right:1px solid #e4eaec;background:#f8fafb}.platform-agent-sessions>header,.platform-agent-conversation-header,.platform-agent-turn>header,.platform-agent-system-results>header{display:flex;align-items:center;justify-content:space-between;gap:8px}.platform-agent-sessions>header>div{display:grid}.platform-agent-sessions small{color:#738087;font-size:11px}.platform-agent-session-create{display:grid;grid-template-columns:1fr auto;gap:6px;margin:12px 0}.platform-agent-session-title,.platform-agent-message-input{width:100%;padding:8px;border:1px solid #cbd5d9;border-radius:6px}.platform-agent-session-list{display:grid;gap:6px}.platform-agent-session-list>button{display:grid;gap:3px;padding:10px;border:1px solid transparent;border-radius:7px;background:transparent;text-align:left}.platform-agent-session-list>button.active{border-color:#acd8d1;background:#eaf7f4}.platform-agent-conversation{min-width:0;padding:16px}.platform-agent-conversation-header h2{margin:0}.platform-agent-conversation-header span,.platform-agent-conversation-header small{color:#6b7980}.platform-agent-message-list{display:grid;gap:10px;max-height:455px;margin:15px 0;padding-right:4px;overflow:auto}.platform-agent-message,.platform-agent-turn{padding:11px 13px;border-radius:8px;background:#f5f8f8}.platform-agent-message.user{margin-left:12%;background:#eef7ff}.platform-agent-message header,.platform-agent-message header>span{display:flex;align-items:center;gap:5px}.platform-agent-message p,.platform-agent-turn p{margin:7px 0;white-space:pre-wrap}.platform-agent-message time,.platform-agent-evidence{color:#79878d;font-size:11px}.platform-agent-turn{border:1px solid #dbe6e4;background:#fbfdfd}.platform-agent-turn>header>span{display:flex;align-items:center;gap:5px}.platform-agent-system-results{display:grid;gap:9px;margin-top:10px;padding:11px;border:1px solid #cfe3df;border-radius:7px;background:#f3faf8}.platform-agent-system-results>header span{color:#68777e;font-size:12px}.platform-agent-system-list{display:grid;gap:7px}.platform-agent-system-result{display:grid;grid-template-columns:auto minmax(0,1fr) auto auto;align-items:center;gap:9px;padding:9px;border:1px solid #dce8e5;border-radius:6px;background:#fff}.platform-agent-system-result>div{display:grid}.platform-agent-system-result small,.platform-agent-system-result span{color:#68777e;font-size:12px}.platform-agent-guidance{display:grid;justify-items:start;gap:7px;margin-top:10px;padding:12px;border:1px solid #c9d9ec;border-radius:7px;background:#f1f7fd}.platform-agent-guidance p{margin:0}.platform-agent-evidence{display:block;margin-top:8px}.platform-agent-submit-error{margin-bottom:10px}.platform-agent-composer{display:grid;grid-template-columns:1fr auto;align-items:end;gap:8px}.platform-task-proposal{display:grid;gap:10px;margin-top:10px;padding:12px;border:1px solid #e0c98f;border-radius:8px;background:#fffaf0}.platform-task-proposal>header,.platform-task-proposal>header>div,.platform-task-proposal>footer,.platform-task-result{display:flex;align-items:center;gap:8px}.platform-task-proposal>header{justify-content:space-between}.platform-task-proposal>header>div{display:grid}.platform-task-proposal>header span,.platform-task-proposal>footer>span{color:#68777e;font-size:12px}.platform-task-preview{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;margin:0}.platform-task-preview>div{display:grid;gap:2px}.platform-task-preview>.wide{grid-column:1/-1}.platform-task-preview dt{color:#68777e;font-size:12px}.platform-task-preview dd{margin:0}.platform-task-proposal>footer{justify-content:flex-end}.platform-task-proposal>footer>span{margin-right:auto}.platform-task-result{align-items:flex-start;flex-direction:column;padding:10px;border:1px solid #a9d7bd;border-radius:7px;background:#f1faf5}.platform-task-result p,.platform-task-result small{margin:0}.platform-task-clarification,.platform-task-terminal-error,.platform-task-action-error{margin:0}
.platform-task-proposal-meta{display:flex;gap:14px;color:#68777e;font-size:12px}
.platform-operations-loading,.platform-operations-card{display:grid;gap:10px;margin-top:10px;padding:12px;border:1px solid #c9d9ec;border-radius:8px;background:#f5f9fd}.platform-operations-loading span,.platform-operations-card>header span,.platform-operations-card small{color:#68777e;font-size:12px}.platform-operations-card>header,.platform-operations-card>header>div,.platform-operations-personal-tasks article>div,.platform-operations-activity article>div,.platform-operations-health>header,.platform-operations-health>div{display:flex;align-items:center;justify-content:space-between;gap:8px}.platform-operations-card>header>div{display:grid;justify-content:start}.platform-operations-personal-tasks,.platform-operations-activity,.platform-operations-health{display:grid;gap:8px}.platform-operations-personal-tasks article,.platform-operations-activity article{display:grid;gap:5px;padding:9px;border:1px solid #dce7ef;border-radius:7px;background:#fff}.platform-operations-personal-tasks article>small{grid-column:1/-1}.platform-operations-quota{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px;margin:0}.platform-operations-quota>div{display:grid;gap:2px;padding:8px;border-radius:6px;background:#fff}.platform-operations-quota dt{color:#68777e;font-size:12px}.platform-operations-quota dd{margin:0}.platform-operations-health{padding:9px;border-radius:7px;background:#fff}.platform-operations-clarification,.platform-operations-failure{margin-top:10px}.platform-operations-empty{padding:8px}
@media(max-width:1024px){.platform-agent-system-result{grid-template-columns:auto minmax(0,1fr) auto}.platform-agent-system-result>.ant-btn{grid-column:2/-1;justify-self:start}.platform-agent-conversation-header{align-items:flex-start;flex-direction:column}.platform-agent-conversation-header small{overflow-wrap:anywhere}}
@media(max-width:720px){.platform-agent-page{min-width:0}.platform-agent-header{align-items:flex-start;flex-direction:column}.platform-agent-layout{grid-template-columns:minmax(0,1fr)}.platform-agent-sessions{border-right:0;border-bottom:1px solid #e4eaec}.platform-agent-conversation{padding:12px}.platform-agent-message.user{margin-left:0}.platform-agent-system-results>header,.platform-operations-card>header,.platform-task-proposal>header{align-items:flex-start;flex-direction:column}.platform-agent-system-result{grid-template-columns:auto minmax(0,1fr)}.platform-agent-system-result>.ant-tag,.platform-agent-system-result>.ant-btn{grid-column:2;justify-self:start}.platform-agent-composer{grid-template-columns:minmax(0,1fr)}.platform-agent-composer .ant-btn{justify-self:stretch}.platform-task-preview,.platform-operations-quota{grid-template-columns:minmax(0,1fr)}.platform-task-proposal>footer{align-items:stretch;flex-direction:column}.platform-task-proposal>footer>span{margin-right:0}.platform-task-proposal-meta{flex-wrap:wrap}.platform-agent-message,.platform-agent-turn,.platform-operations-card{min-width:0;overflow-wrap:anywhere}}
</style>
