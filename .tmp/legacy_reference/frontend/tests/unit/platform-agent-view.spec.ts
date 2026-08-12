import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiRequestError } from '@/services/api'
import { platformAiRuntimeApi } from '@/services/platformAi'
import { useSessionStore } from '@/stores/session'
import type {
  PlatformAiOperations,
  PlatformAiSession,
  PlatformAiSessionDetail,
  PlatformAiStoredTurn,
  PlatformAiTaskProposal,
  PlatformAiTurn,
} from '@/types/platformAi'
import PlatformAgentView from '@/views/platform/PlatformAgentView.vue'

const router = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => router }))
vi.mock('@/services/platformAi', () => ({ platformAiRuntimeApi: {
  capability: vi.fn(), sessions: vi.fn(), createSession: vi.fn(), session: vi.fn(), submitMessage: vi.fn(),
  taskProposal: vi.fn(), confirmTaskProposal: vi.fn(), rejectTaskProposal: vi.fn(),
} }))

const platformSession: PlatformAiSession = {
  id: 'session-1', title: '系统授权', status: 'ACTIVE',
  createdAt: '2026-08-04T00:00:00Z', updatedAt: '2026-08-04T00:01:00Z',
}
const detail: PlatformAiSessionDetail = {
  session: platformSession,
  messages: [{ id: 'message-1', role: 'USER', status: 'REDACTED', content: '授权系统查询', createdAt: '2026-08-04T00:01:00Z' }],
  turns: [],
}
const systemsTurn: PlatformAiTurn = {
  id: 'turn-1', status: 'SUCCEEDED', operation: 'AUTHORIZED_SYSTEMS_QUERY', answer: '当前可进入 1 个系统。',
  errorCode: null, retryable: false,
  systems: [{
    systemId: 'system-10', systemCode: 'CRM', systemName: '客户系统', status: 'ACTIVE',
    membershipState: 'ACTIVE', accessState: 'AUTHORIZED', switchTarget: '/api/v1/context/systems/system-10:switch',
  }],
  guidance: null, proposal: null, operations: null, requestId: 'request-1', traceId: 'trace-1',
}
const pendingTaskProposal: PlatformAiTaskProposal = {
  id: 'proposal-1', state: 'PENDING', revision: 3,
  preview: {
    title: '跟进平台授权', description: '核对新成员的平台权限', dueAt: '2099-08-05T02:00:00Z',
    priority: 'HIGH', selfAssigned: true,
  },
  confidence: 0.94, clarification: null, expiresAt: '2099-08-04T02:00:00Z',
  result: null, errorCode: null, requestId: 'request-proposal', traceId: 'trace-proposal',
}
const storedTaskTurn: PlatformAiStoredTurn = {
  id: 'turn-task', status: 'SUCCEEDED', operation: 'PLATFORM_TASK_DRAFT',
  responseSummary: '已生成个人平台任务草稿。', errorCode: null, retryable: false,
  returnedSystems: 0, evidence: null, proposal: pendingTaskProposal, operations: null,
  createdAt: '2026-08-04T00:02:00Z', finishedAt: '2026-08-04T00:02:01Z',
}

function storedOperationsTurn(
  id: string,
  operations: PlatformAiOperations | null,
  status: PlatformAiStoredTurn['status'] = 'SUCCEEDED',
  errorCode: string | null = null,
): PlatformAiStoredTurn {
  return {
    id, status, operation: 'PLATFORM_OPERATIONS_QUERY',
    responseSummary: status === 'SUCCEEDED' ? '平台运营数据只读回读。' : null,
    errorCode, retryable: status === 'RETRYABLE', returnedSystems: 0,
    evidence: null, proposal: null, operations,
    createdAt: '2026-08-04T00:10:00Z', finishedAt: status === 'RUNNING' ? null : '2026-08-04T00:10:01Z',
  }
}

function taskDetail(proposal: PlatformAiTaskProposal): PlatformAiSessionDetail {
  return { ...detail, turns: [{ ...storedTaskTurn, proposal }] }
}

function render() {
  return mount(PlatformAgentView, { global: { stubs: {
    ArrowRight: true, Bot: true, Building2: true, MessageSquarePlus: true, RefreshCw: true,
    RotateCcw: true, Send: true, ShieldAlert: true, UserRound: true,
    'a-button': { inheritAttrs: false, props: ['disabled', 'loading'], emits: ['click'], template: '<button v-bind="$attrs" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
    'a-alert': { props: ['message', 'description'], template: '<div class="alert-stub">{{ message }} {{ description }}<slot name="action" /></div>' },
    'a-spin': { template: '<div><slot /></div>' }, 'a-tag': { template: '<span><slot /></span>' },
    'a-empty': { props: ['description'], template: '<div>{{ description }}</div>' },
  } } })
}

function permissionError() {
  return new ApiRequestError(403, {
    code: 'PLATFORM_AI_PERMISSION_DENIED', message: 'permission denied', data: null,
    requestId: 'request-denied', traceId: 'trace-denied', errors: [],
  })
}

describe('PlatformAgentView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    router.push.mockResolvedValue(undefined)
    const store = useSessionStore()
    store.applyAuth({
      account: { id: 'account-1', username: 'member', displayName: 'Member' },
      context: {
        type: 'PLATFORM', account: { id: 'account-1', username: 'member', displayName: 'Member' },
        permissionVersion: '1', permissions: ['platform.runtime.access', 'platform.ai.agent.use'], shells: ['PLATFORM_RUNTIME'],

        roleIds: [],
        dataScope: null,
        restrictedMode: 'NONE',
      },
      systems: [],

      tenants: [],
      firstSystemId: null,
    })
    vi.spyOn(store, 'switchSystem').mockResolvedValue('10')
    vi.mocked(platformAiRuntimeApi.capability).mockResolvedValue({ available: true, reason: null, policyVersion: 'policy-4' })
    vi.mocked(platformAiRuntimeApi.sessions).mockResolvedValue({ rows: [platformSession], page: 1, size: 50, hasMore: false })
    vi.mocked(platformAiRuntimeApi.session).mockResolvedValue(detail)
    vi.mocked(platformAiRuntimeApi.createSession).mockResolvedValue({ ...platformSession, id: 'session-2', title: 'CRM 切换' })
    vi.mocked(platformAiRuntimeApi.submitMessage).mockResolvedValue(systemsTurn)
    vi.mocked(platformAiRuntimeApi.taskProposal).mockResolvedValue(pendingTaskProposal)
    vi.mocked(platformAiRuntimeApi.confirmTaskProposal).mockResolvedValue({
      ...pendingTaskProposal, state: 'SUCCEEDED', revision: 4,
      result: {
        taskId: 'task-1', title: '跟进平台授权', description: '核对新成员的平台权限',
        dueAt: '2099-08-05T02:00:00Z', priority: 'HIGH', status: 'OPEN', source: 'AGENT',
        createdAt: '2026-08-04T00:03:00Z',
      },
    })
    vi.mocked(platformAiRuntimeApi.rejectTaskProposal).mockResolvedValue({
      ...pendingTaskProposal, state: 'REJECTED', revision: 4,
    })
  })

  it('keeps disabled, provider failure and permission denied states explicit', async () => {
    vi.mocked(platformAiRuntimeApi.capability).mockResolvedValueOnce({ available: false, reason: 'POLICY_DISABLED', policyVersion: 'policy-4' })
    const disabled = render()
    await flushPromises()
    expect(disabled.get('.platform-agent-unavailable').text()).toContain('平台 Agent 已停用')
    expect(platformAiRuntimeApi.sessions).not.toHaveBeenCalled()

    vi.mocked(platformAiRuntimeApi.capability).mockResolvedValueOnce({ available: false, reason: 'PROVIDER_UNAVAILABLE', policyVersion: 'policy-4' })
    const providerFailed = render()
    await flushPromises()
    expect(providerFailed.get('.platform-agent-unavailable').text()).toContain('Provider 暂不可用')
    expect(providerFailed.find('.platform-agent-provider-retry').exists()).toBe(true)

    vi.mocked(platformAiRuntimeApi.capability).mockRejectedValueOnce(permissionError())
    const denied = render()
    await flushPromises()
    expect(denied.get('.platform-agent-unavailable').text()).toContain('无权使用平台 Agent')
    expect(denied.text()).toContain('PLATFORM_AI_PERMISSION_DENIED')
  })

  it('lists, creates and submits platform sessions without a confirmation UI', async () => {
    vi.mocked(platformAiRuntimeApi.session).mockImplementation(async sessionId => ({
      ...detail, session: { ...platformSession, id: sessionId, title: sessionId === 'session-2' ? 'CRM 切换' : '系统授权' },
    }))
    const wrapper = render()
    await flushPromises()
    expect(platformAiRuntimeApi.sessions).toHaveBeenCalledWith(1, 50)
    expect(platformAiRuntimeApi.session).toHaveBeenCalledWith('session-1')

    await wrapper.get('.platform-agent-session-title').setValue('CRM 切换')
    await wrapper.get('.platform-agent-session-create-button').trigger('click')
    await flushPromises()
    expect(platformAiRuntimeApi.createSession).toHaveBeenCalledWith({ title: 'CRM 切换' })
    expect(platformAiRuntimeApi.session).toHaveBeenLastCalledWith('session-2')

    await wrapper.get('.platform-agent-message-input').setValue('我可以进入哪些系统？')
    await wrapper.get('.platform-agent-message-submit').trigger('click')
    await flushPromises()
    expect(platformAiRuntimeApi.submitMessage).toHaveBeenCalledWith('session-2', { content: '我可以进入哪些系统？' })
    expect(wrapper.get('.platform-agent-system-results').text()).toContain('客户系统')
    expect(wrapper.get('.platform-agent-system-results').text()).toContain('CRM · ACTIVE')
    expect(wrapper.find('.agent-confirmation').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('确认写入')
  })

  it('switches from a permission-projected result through the normal context switch', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.platform-agent-message-input').setValue('列出授权系统')
    await wrapper.get('.platform-agent-message-submit').trigger('click')
    await flushPromises()
    await wrapper.get('.platform-agent-system-switch').trigger('click')
    await flushPromises()
    expect(useSessionStore().switchSystem).toHaveBeenCalledWith('system-10')
    expect(router.push).toHaveBeenCalledWith('/systems/system-10/workbench')
  })

  it('renders safe guidance for business requests and retries only provider failures', async () => {
    const guidanceTurn: PlatformAiTurn = {
      ...systemsTurn, id: 'turn-guidance', operation: 'SYSTEM_SWITCH_GUIDANCE', systems: [],
      answer: '平台上下文不能查询订单。', guidance: {
        requestedSystemCode: 'CRM', message: '请先切换到 CRM 系统，再从系统业务页面查询。',
        switchTarget: '/systems/system-10/workbench',
      },
    }
    vi.mocked(platformAiRuntimeApi.submitMessage)
      .mockResolvedValueOnce(guidanceTurn)
      .mockRejectedValueOnce(new Error('PROVIDER_TIMEOUT'))
      .mockResolvedValueOnce(systemsTurn)
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.platform-agent-message-input').setValue('查询 CRM 订单')
    await wrapper.get('.platform-agent-message-submit').trigger('click')
    await flushPromises()
    expect(wrapper.get('.platform-agent-guidance').text()).toContain('请先切换到 CRM 系统')
    expect(wrapper.text()).not.toContain('订单记录')

    await wrapper.get('.platform-agent-message-input').setValue('我有哪些系统')
    await wrapper.get('.platform-agent-message-submit').trigger('click')
    await flushPromises()
    expect(wrapper.get('.platform-agent-submit-error').text()).toContain('PROVIDER_TIMEOUT')
    await wrapper.get('.platform-agent-message-retry').trigger('click')
    await flushPromises()
    expect(platformAiRuntimeApi.submitMessage).toHaveBeenCalledTimes(3)
  })

  it('renders four typed read-only platform operations readbacks', async () => {
    vi.mocked(platformAiRuntimeApi.session).mockResolvedValue({
      ...detail,
      turns: [
        storedOperationsTurn('turn-personal-tasks', {
          queryKind: 'PERSONAL_TASKS', confidence: 0.98, clarification: null,
          personalTasks: [{
            taskId: 'task-ops-1', title: '跟进平台授权', dueAt: null, priority: 'HIGH', status: 'OPEN',
            source: 'AGENT', createdAt: '2026-08-04T00:03:00Z',
          }],
          quota: null, serviceHealth: null, agentActivity: [],
        }),
        storedOperationsTurn('turn-quota', {
          queryKind: 'AI_QUOTA', confidence: 0.96, clarification: null, personalTasks: [],
          quota: {
            policyVersion: 'policy-4', periodStart: '2026-08-04T00:00:00Z', periodEnd: '2026-08-05T00:00:00Z',
            requestLimit: 1000, requestCount: 125, remainingRequests: 875,
            tokenLimit: 1000000, usedTokens: 120000, reservedTokens: 5000, remainingTokens: 875000,
            concurrencyLimit: 4, runningCount: 1, remainingConcurrency: 3,
          },
          serviceHealth: null, agentActivity: [],
        }),
        storedOperationsTurn('turn-health', {
          queryKind: 'SERVICE_HEALTH', confidence: 1, clarification: null, personalTasks: [], quota: null,
          serviceHealth: {
            status: 'DEGRADED', checkedAt: '2026-08-04T00:08:00Z', components: [
              { component: 'DATABASE', status: 'UP' }, { component: 'REDIS', status: 'UP' },
              { component: 'PLATFORM_AI_CONFIGURATION', status: 'UP' },
              { component: 'PLATFORM_AI_PROVIDER', status: 'DEGRADED' },
            ],
          },
          agentActivity: [],
        }),
        storedOperationsTurn('turn-activity', {
          queryKind: 'AGENT_ACTIVITY', confidence: 0.92, clarification: null, personalTasks: [], quota: null,
          serviceHealth: null,
          agentActivity: [{
            event: '查询个人 AI 配额', time: '2026-08-04T00:09:00Z', operation: 'PLATFORM_OPERATIONS_QUERY',
            resultCode: 'SUCCEEDED', requestId: 'request-ops-1', traceId: 'trace-ops-1',
          }],
        }),
      ],
    })
    const wrapper = render()
    await flushPromises()

    expect(wrapper.get('.platform-operations-personal-tasks').text()).toContain('跟进平台授权')
    expect(wrapper.get('.platform-operations-personal-tasks').text()).toContain('task-ops-1 · OPEN · AGENT')
    expect(wrapper.get('.platform-operations-quota').text()).toContain('125 / 1000（剩余 875）')
    expect(wrapper.get('.platform-operations-quota').text()).toContain('已用 120000 · 预留 5000 / 1000000')
    expect(wrapper.get('.platform-operations-health').text()).toContain('PLATFORM_AI_PROVIDER')
    expect(wrapper.get('.platform-operations-health').text()).toContain('DEGRADED')
    expect(wrapper.get('.platform-operations-activity').text()).toContain('查询个人 AI 配额')
    expect(wrapper.get('.platform-operations-activity').text()).toContain('request request-ops-1 · trace trace-ops-1')
    expect(wrapper.findAll('.platform-operations-card')).toHaveLength(4)
    expect(wrapper.find('.platform-task-confirm').exists()).toBe(false)
  })

  it('renders operations loading, empty, clarification and failure states without actions', async () => {
    vi.mocked(platformAiRuntimeApi.session).mockResolvedValue({
      ...detail,
      turns: [
        storedOperationsTurn('turn-loading', null, 'RUNNING'),
        storedOperationsTurn('turn-empty', {
          queryKind: 'PERSONAL_TASKS', confidence: 0.99, clarification: null,
          personalTasks: [], quota: null, serviceHealth: null, agentActivity: [],
        }),
        storedOperationsTurn('turn-clarification', {
          queryKind: null, confidence: 0.41, clarification: '请说明要查询任务、配额、服务健康还是 Agent 活动。',
          personalTasks: [], quota: null, serviceHealth: null, agentActivity: [],
        }),
        storedOperationsTurn('turn-failure', null, 'FAILED', 'PLATFORM_OPERATIONS_PERMISSION_DENIED'),
      ],
    })
    const wrapper = render()
    await flushPromises()

    expect(wrapper.get('.platform-operations-loading').text()).toContain('正在读取当前账号可见的实时只读数据')
    expect(wrapper.get('.platform-operations-empty').text()).toContain('当前账号暂无个人任务')
    expect(wrapper.get('.platform-operations-clarification').text()).toContain('请说明要查询任务、配额、服务健康还是 Agent 活动')
    expect(wrapper.get('.platform-operations-failure').text()).toContain('PLATFORM_OPERATIONS_PERMISSION_DENIED')
    expect(wrapper.find('.platform-task-confirm').exists()).toBe(false)
    expect(wrapper.find('.platform-task-reject').exists()).toBe(false)
  })

  it('renders a self-assigned task preview, confidence and clarification without writing', async () => {
    vi.mocked(platformAiRuntimeApi.session).mockResolvedValue(taskDetail(pendingTaskProposal))
    const pending = render()
    await flushPromises()
    const card = pending.get('.platform-task-proposal')
    expect(card.text()).toContain('跟进平台授权')
    expect(card.text()).toContain('核对新成员的平台权限')
    expect(card.text()).toContain('HIGH')
    expect(card.text()).toContain('当前账号（自分配）')
    expect(card.text()).toContain('94%')
    expect(card.text()).toContain('确认前不会创建平台任务')
    expect(platformAiRuntimeApi.confirmTaskProposal).not.toHaveBeenCalled()

    vi.mocked(platformAiRuntimeApi.session).mockResolvedValue(taskDetail({
      ...pendingTaskProposal, state: 'CLARIFICATION_REQUIRED', confidence: 0.42,
      preview: null, clarification: '请补充明确的任务标题。',
    }))
    const clarification = render()
    await flushPromises()
    expect(clarification.get('.platform-task-clarification').text()).toContain('请补充明确的任务标题')
    expect(clarification.find('.platform-task-confirm').exists()).toBe(false)
    expect(clarification.find('.platform-task-reject').exists()).toBe(false)
  })

  it('confirms exactly once and renders the terminal owner task readback', async () => {
    let resolveConfirmation!: (value: PlatformAiTaskProposal) => void
    vi.mocked(platformAiRuntimeApi.session).mockResolvedValue(taskDetail(pendingTaskProposal))
    vi.mocked(platformAiRuntimeApi.confirmTaskProposal).mockReturnValue(new Promise(resolve => {
      resolveConfirmation = resolve
    }))
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.platform-task-confirm').trigger('click')
    await wrapper.get('.platform-task-confirm').trigger('click')
    expect(platformAiRuntimeApi.confirmTaskProposal).toHaveBeenCalledTimes(1)
    expect(platformAiRuntimeApi.confirmTaskProposal).toHaveBeenCalledWith(
      'session-1', 'proposal-1', { expectedRevision: 3 }, expect.any(String),
    )
    expect(wrapper.get('.platform-task-confirm').attributes('disabled')).toBeDefined()

    resolveConfirmation({
      ...pendingTaskProposal, state: 'SUCCEEDED', revision: 4,
      result: {
        taskId: 'task-1', title: '跟进平台授权', description: '核对新成员的平台权限',
        dueAt: '2099-08-05T02:00:00Z', priority: 'HIGH', status: 'OPEN', source: 'AGENT',
        createdAt: '2026-08-04T00:03:00Z',
      },
    })
    await flushPromises()
    expect(wrapper.get('.platform-task-result').text()).toContain('平台任务真实回读')
    expect(wrapper.get('.platform-task-result').text()).toContain('task-1 · OPEN · AGENT')
    expect(wrapper.find('.platform-task-confirm').exists()).toBe(false)
  })

  it('rejects a pending draft and shows terminal permission failure without claiming creation', async () => {
    vi.mocked(platformAiRuntimeApi.session).mockResolvedValue(taskDetail(pendingTaskProposal))
    const rejected = render()
    await flushPromises()
    await rejected.get('.platform-task-reject').trigger('click')
    await flushPromises()
    expect(platformAiRuntimeApi.rejectTaskProposal).toHaveBeenCalledWith(
      'session-1', 'proposal-1', { expectedRevision: 3 },
    )
    expect(rejected.get('.platform-task-proposal').text()).toContain('已拒绝')
    expect(rejected.find('.platform-task-result').exists()).toBe(false)

    vi.clearAllMocks()
    vi.mocked(platformAiRuntimeApi.capability).mockResolvedValue({ available: true, reason: null, policyVersion: 'policy-4' })
    vi.mocked(platformAiRuntimeApi.sessions).mockResolvedValue({ rows: [platformSession], page: 1, size: 50, hasMore: false })
    vi.mocked(platformAiRuntimeApi.session).mockResolvedValue(taskDetail(pendingTaskProposal))
    vi.mocked(platformAiRuntimeApi.confirmTaskProposal).mockRejectedValue(new Error('PLATFORM_TASK_CREATE_FORBIDDEN'))
    vi.mocked(platformAiRuntimeApi.taskProposal).mockResolvedValue({
      ...pendingTaskProposal, state: 'FAILED', revision: 4, errorCode: 'PLATFORM_TASK_CREATE_FORBIDDEN',
    })
    const denied = render()
    await flushPromises()
    await denied.get('.platform-task-confirm').trigger('click')
    await flushPromises()
    expect(denied.get('.platform-task-action-error').text()).toContain('权限或授权上下文已变化')
    expect(denied.get('.platform-task-terminal-error').text()).toContain('PLATFORM_TASK_CREATE_FORBIDDEN')
    expect(denied.find('.platform-task-result').exists()).toBe(false)
  })

  it('reuses the exact confirmation idempotency key after a transient retry', async () => {
    vi.mocked(platformAiRuntimeApi.session).mockResolvedValue(taskDetail(pendingTaskProposal))
    vi.mocked(platformAiRuntimeApi.confirmTaskProposal)
      .mockRejectedValueOnce(new Error('PLATFORM_TASK_OWNER_UNAVAILABLE'))
      .mockResolvedValueOnce({ ...pendingTaskProposal, state: 'REJECTED', revision: 4 })
    vi.mocked(platformAiRuntimeApi.taskProposal).mockResolvedValue(pendingTaskProposal)
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.platform-task-confirm').trigger('click')
    await flushPromises()
    await wrapper.get('.platform-task-confirm').trigger('click')
    await flushPromises()
    expect(platformAiRuntimeApi.confirmTaskProposal).toHaveBeenCalledTimes(2)
    expect(vi.mocked(platformAiRuntimeApi.confirmTaskProposal).mock.calls[0]![3])
      .toBe(vi.mocked(platformAiRuntimeApi.confirmTaskProposal).mock.calls[1]![3])
  })
})
