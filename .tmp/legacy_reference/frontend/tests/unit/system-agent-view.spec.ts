import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { aiRuntimeApi } from '@/services/ai'
import type { AiConfirmation, AiConfigurationArtifactProposal, AiConfigurationFieldProposal, AiGeneratedDraftProposal, AiSession, AiSessionDetail, AiTurn, AiWorkProposal } from '@/types/ai'
import SystemAgentView from '@/views/system/SystemAgentView.vue'

const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
const routerPush = vi.hoisted(() => vi.fn())
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push: routerPush }) }))
vi.mock('@/services/ai', () => ({ aiRuntimeApi: {
  capability: vi.fn(), sessions: vi.fn(), createSession: vi.fn(), session: vi.fn(), submitMessage: vi.fn(),
  confirmation: vi.fn(), confirm: vi.fn(), reject: vi.fn(),
  configurationProposal: vi.fn(), confirmConfigurationProposal: vi.fn(), rejectConfigurationProposal: vi.fn(),
  configurationArtifactProposal: vi.fn(), confirmConfigurationArtifactProposal: vi.fn(), rejectConfigurationArtifactProposal: vi.fn(),
  workProposal: vi.fn(), confirmWorkProposal: vi.fn(), rejectWorkProposal: vi.fn(),
  generatedDraftProposal: vi.fn(), confirmGeneratedDraftProposal: vi.fn(), rejectGeneratedDraftProposal: vi.fn(),
} }))

const session: AiSession = {
  id: 'session-1', title: '订单查询', status: 'ACTIVE',
  createdAt: '2026-08-04T00:00:00Z', updatedAt: '2026-08-04T00:01:00Z',
}
const detail: AiSessionDetail = {
  session,
  messages: [{
    id: 'message-1', role: 'USER', content: '查询订单', createdAt: '2026-08-04T00:01:00Z',
  }],
  turns: [],
}
const answer: AiTurn = {
  id: 'turn-2', status: 'SUCCEEDED', answer: '当前有 1 条订单。', errorCode: null, retryable: false,
  tool: { moduleCode: 'orders', total: 1, returnedRows: 1, rows: [{ orderNo: 'SO-1' }] },
  requestId: 'request-2', traceId: 'trace-2',
}
const proposal: AiConfirmation = {
  id: 'confirmation-1', sessionId: 'session-1', turnId: 'turn-write', state: 'PENDING',
  operation: 'RECORD_UPDATE', moduleCode: 'orders', recordId: 'record-1',
  expectedRecordVersion: 4, beforeTitle: '订单 SO-1', afterTitle: '订单 SO-1（已调整）',
  fields: [{
    fieldCode: 'amount', fieldName: '金额', type: 'DECIMAL', beforeDisplayValue: '100.00',
    afterDisplayValue: '125.00', masked: false, confidence: 0.96,
  }],
  clarifications: [], expiresAt: '2099-08-04T01:00:00Z', version: 7, result: null,
  errorCode: null,
}
const configurationProposal: AiConfigurationFieldProposal = {
  id: 'configuration-proposal-1', sessionId: 'session-1', turnId: 'turn-config', state: 'PENDING', revision: 3,
  moduleCode: 'orders',
  preview: {
    configRootId: 'config-root-1', moduleId: 'module-orders', expectedDraftRevision: 12, nextDraftRevision: 13, moduleCode: 'orders',
    fieldCode: 'externalReference', fieldName: '外部引用', fieldType: 'TEXT', required: true,
    settings: { maxLength: 120, precision: null, scale: null, minimum: null, maximum: null },
  },
  confidence: 0.97, clarification: null, expiresAt: '2099-08-04T02:00:00Z', result: null, errorCode: null,
  requestId: 'request-config-1', traceId: 'trace-config-1',
}
const selectionArtifactProposal: AiConfigurationArtifactProposal = {
  id: 'artifact-proposal-selection', sessionId: 'session-1', turnId: 'turn-artifact-selection',
  state: 'PENDING', revision: 5, operation: 'CONFIG_SELECTION_FIELD_DRAFT', artifactKind: 'SELECTION_FIELD',
  moduleCode: 'orders', confidence: 0.98, clarification: null, expiresAt: '2099-08-04T03:00:00Z',
  preview: {
    operation: 'CONFIG_SELECTION_FIELD_DRAFT', configRootId: 'config-root-1', moduleId: 'module-orders',
    moduleCode: 'orders', expectedDraftRevision: 13, nextDraftRevision: 14, pageLayout: null,
    filterScenario: null, fieldPermissionStage: null,
    selectionField: {
      sortOrder: 5, fieldCode: 'priority', fieldName: '优先级', fieldType: 'RADIO', required: true,
      dictionaryCode: 'order_priority', dictionaryName: '订单优先级', maxSelections: null,
      options: [
        { code: 'HIGH', label: '高', semanticKey: 'priority.high', color: '#E5484D', defaultOption: false, sortOrder: 0 },
        { code: 'MEDIUM', label: '中', semanticKey: 'priority.medium', color: '#F5A623', defaultOption: true, sortOrder: 1 },
        { code: 'LOW', label: '低', semanticKey: 'priority.low', color: '#30A46C', defaultOption: false, sortOrder: 2 },
      ],
    },
  },
  result: null, errorCode: null, requestId: 'request-artifact-selection', traceId: 'trace-artifact-selection',
}
const pageArtifactProposal: AiConfigurationArtifactProposal = {
  id: 'artifact-proposal-page', sessionId: 'session-1', turnId: 'turn-artifact-page', state: 'PENDING',
  revision: 7, operation: 'CONFIG_PAGE_LAYOUT_DRAFT', artifactKind: 'PAGE_LAYOUT', moduleCode: 'orders',
  confidence: 0.95, clarification: null, expiresAt: '2099-08-04T03:00:00Z',
  preview: {
    operation: 'CONFIG_PAGE_LAYOUT_DRAFT', configRootId: 'config-root-1', moduleId: 'module-orders',
    moduleCode: 'orders', expectedDraftRevision: 13, nextDraftRevision: 14, selectionField: null,
    filterScenario: null, fieldPermissionStage: null,
    pageLayout: {
      pageId: 'page-order-form', pageCode: 'order_form', pageType: 'FORM', pageVersion: 4,
      layout: {
        columns: 2, gap: 16, labelPosition: 'TOP', density: 'COMPACT', stickyActions: true,
        pageSize: null, searchEnabled: null, filterEnabled: null, sectionCount: 2, fieldCount: 4,
        redacted: false, sections: [
          { code: 'basic', title: '基础信息', fieldCodes: ['orderNo', 'priority'], sortOrder: 0 },
          { code: 'delivery', title: '交付信息', fieldCodes: ['dueAt', 'address'], sortOrder: 1 },
        ],
      },
    },
  },
  result: null, errorCode: null, requestId: 'request-artifact-page', traceId: 'trace-artifact-page',
}
const pageArtifactSuccess: AiConfigurationArtifactProposal = {
  ...pageArtifactProposal, state: 'SUCCEEDED', revision: 8,
  result: {
    operation: 'CONFIG_PAGE_LAYOUT_DRAFT', configRootId: 'config-root-1', moduleId: 'module-orders',
    moduleCode: 'orders', draftRevision: 14, selectionField: null, filterScenario: null,
    fieldPermissionStage: null, draftStatus: 'DRAFT',
    pageLayout: {
      pageId: 'page-order-form', pageCode: 'order_form', pageType: 'FORM', version: 5,
      layout: pageArtifactProposal.preview!.pageLayout!.layout,
    },
  },
}
const existingFilterScenario = {
  code: 'recent_orders', name: '最近订单', filter: null,
  sort: [{ fieldCode: 'created_at', direction: 'DESC' as const, nulls: 'LAST' as const }],
}
const urgentFilterScenario = {
  code: 'urgent_first', name: '紧急优先',
  filter: { kind: 'PREDICATE' as const, fieldCode: 'priority', operator: 'EQ', value: 'HIGH' },
  sort: [{ fieldCode: 'created_at', direction: 'DESC' as const, nulls: 'LAST' as const }],
}
const filterScenarioArtifactProposal: AiConfigurationArtifactProposal = {
  id: 'artifact-proposal-filter-scenario', sessionId: 'session-1', turnId: 'turn-artifact-filter-scenario',
  state: 'PENDING', revision: 9, operation: 'CONFIG_FILTER_SCENARIO_DRAFT', artifactKind: 'FILTER_SCENARIO',
  moduleCode: 'orders', confidence: 0.94, clarification: null, expiresAt: '2099-08-04T03:00:00Z',
  preview: {
    operation: 'CONFIG_FILTER_SCENARIO_DRAFT', configRootId: 'config-root-1', moduleId: 'module-orders',
    moduleCode: 'orders', expectedDraftRevision: 14, nextDraftRevision: 15,
    selectionField: null, pageLayout: null, fieldPermissionStage: null,
    filterScenario: {
      pageId: 'page-orders', pageCode: 'orders_list', pageVersion: 4,
      scenario: urgentFilterScenario, makeDefault: true,
      resolvedState: {
        filterScenarios: [existingFilterScenario, urgentFilterScenario], defaultFilterScenarioCode: 'urgent_first',
      },
      resolvedLayout: {
        filterScenarios: [existingFilterScenario, urgentFilterScenario], defaultFilterScenarioCode: 'urgent_first',
      },
    },
  },
  result: null, errorCode: null, requestId: 'request-artifact-filter-scenario', traceId: 'trace-artifact-filter-scenario',
}
const filterScenarioArtifactSuccess: AiConfigurationArtifactProposal = {
  ...filterScenarioArtifactProposal, state: 'SUCCEEDED', revision: 10,
  result: {
    operation: 'CONFIG_FILTER_SCENARIO_DRAFT', configRootId: 'config-root-1', moduleId: 'module-orders',
    moduleCode: 'orders', draftRevision: 15, selectionField: null, pageLayout: null,
    fieldPermissionStage: null, draftStatus: 'DRAFT',
    filterScenario: {
      pageId: 'page-orders', pageCode: 'orders_list', version: 5,
      state: {
        filterScenarios: [existingFilterScenario, urgentFilterScenario], defaultFilterScenarioCode: 'urgent_first',
      },
    },
  },
}
const fieldPermissionArtifactProposal: AiConfigurationArtifactProposal = {
  id: 'artifact-proposal-field-permission', sessionId: 'session-1', turnId: 'turn-artifact-field-permission',
  state: 'PENDING', revision: 11, operation: 'CONFIG_FIELD_PERMISSION_STAGE_DRAFT', artifactKind: 'FIELD_PERMISSION_STAGE',
  moduleCode: 'orders', confidence: 0.96, clarification: null, expiresAt: '2099-08-04T03:00:00Z',
  preview: {
    operation: 'CONFIG_FIELD_PERMISSION_STAGE_DRAFT', configRootId: 'config-root-1', moduleId: 'module-orders',
    moduleCode: 'orders', expectedDraftRevision: 15, nextDraftRevision: 16,
    selectionField: null, pageLayout: null, filterScenario: null,
    fieldPermissionStage: {
      fieldId: 'field-secret-note', fieldCode: 'secret_note', fieldName: '保密备注', fieldVersion: 5,
      stageRead: true, stageWrite: false, expectedReadPermissionMode: 'INHERIT', expectedWritePermissionMode: 'INHERIT',
      readPermissionMode: 'STAGED', writePermissionMode: 'INHERIT',
      readPermissionCode: 'module.orders.field.secret_note.read', writePermissionCode: 'module.orders.field.secret_note.write',
    },
  },
  result: null, errorCode: null, requestId: 'request-artifact-field-permission', traceId: 'trace-artifact-field-permission',
}
const fieldPermissionArtifactSuccess: AiConfigurationArtifactProposal = {
  ...fieldPermissionArtifactProposal, state: 'SUCCEEDED', revision: 12,
  result: {
    operation: 'CONFIG_FIELD_PERMISSION_STAGE_DRAFT', configRootId: 'config-root-1', moduleId: 'module-orders',
    moduleCode: 'orders', draftRevision: 16, selectionField: null, pageLayout: null,
    filterScenario: null, draftStatus: 'DRAFT',
    fieldPermissionStage: {
      fieldId: 'field-secret-note', fieldCode: 'secret_note', fieldName: '保密备注', version: 6,
      readPermissionMode: 'STAGED', writePermissionMode: 'INHERIT',
      readPermissionCode: 'module.orders.field.secret_note.read', writePermissionCode: 'module.orders.field.secret_note.write',
    },
  },
}
const taskWorkProposal: AiWorkProposal = {
  id: 'work-proposal-task', sessionId: 'session-1', turnId: 'turn-work-task', state: 'PENDING', revision: 2,
  operation: 'WORK_TASK_DRAFT', confidence: 0.96, clarification: null, expiresAt: '2099-08-04T04:00:00Z',
  preview: {
    operation: 'WORK_TASK_DRAFT', dailyReport: null,
    task: {
      title: '完成订单对账', description: '核对本周异常订单并记录差异。', assigneeMemberId: 'member-7',
      assigneeDisplayName: null, projectId: 'project-3', projectDisplayName: null, dueAt: '2026-08-05T10:00:00Z',
    },
  },
  result: null, errorCode: null, requestId: 'request-work-task', traceId: 'trace-work-task',
}
const reportWorkProposal: AiWorkProposal = {
  id: 'work-proposal-report', sessionId: 'session-1', turnId: 'turn-work-report', state: 'PENDING', revision: 4,
  operation: 'WORK_DAILY_REPORT_DRAFT', confidence: 0.92, clarification: null, expiresAt: '2099-08-04T04:00:00Z',
  preview: {
    operation: 'WORK_DAILY_REPORT_DRAFT', task: null,
    dailyReport: {
      workDate: '2026-08-04', completedWork: '完成订单字段梳理。', plannedWork: '继续补齐工作流测试。', blockers: '等待产品确认状态文案。',
    },
  },
  result: null, errorCode: null, requestId: 'request-work-report', traceId: 'trace-work-report',
}
const taskWorkSuccess: AiWorkProposal = {
  ...taskWorkProposal, state: 'SUCCEEDED', revision: 3,
  result: {
    operation: 'WORK_TASK_DRAFT', dailyReport: null,
    task: {
      ...taskWorkProposal.preview!.task!, taskId: 'task-101', status: 'OPEN',
      createdAt: '2026-08-04T04:05:00Z', updatedAt: '2026-08-04T04:05:00Z', version: 1,
    },
  },
}
const reportWorkSuccess: AiWorkProposal = {
  ...reportWorkProposal, state: 'SUCCEEDED', revision: 5,
  result: {
    operation: 'WORK_DAILY_REPORT_DRAFT', task: null,
    dailyReport: {
      ...reportWorkProposal.preview!.dailyReport!, reportId: 'report-20260804', authorMemberId: 'member-7',
      status: 'DRAFT', createdAt: '2026-08-04T04:06:00Z', updatedAt: '2026-08-04T04:06:00Z', version: 1,
    },
  },
}
const flowGeneratedDraftProposal: AiGeneratedDraftProposal = {
  id: 'generated-flow', sessionId: 'session-1', turnId: 'turn-generated-flow', state: 'PENDING', revision: 2,
  operation: 'FLOW_DEFINITION_DRAFT', confidence: 0.97, clarification: null, expiresAt: '2099-08-04T05:00:00Z',
  preview: {
    operation: 'FLOW_DEFINITION_DRAFT', reportDefinition: null, printTemplate: null,
    flowDefinition: { name: '订单审批流', approverMemberIds: ['member-2', 'member-5'] },
  },
  result: null, errorCode: null, requestId: 'request-generated-flow', traceId: 'trace-generated-flow',
}
const reportGeneratedDraftProposal: AiGeneratedDraftProposal = {
  id: 'generated-report', sessionId: 'session-1', turnId: 'turn-generated-report', state: 'PENDING', revision: 3,
  operation: 'CONFIG_REPORT_DRAFT', confidence: 0.94, clarification: null, expiresAt: '2099-08-04T05:00:00Z',
  preview: {
    operation: 'CONFIG_REPORT_DRAFT', flowDefinition: null, printTemplate: null,
    reportDefinition: {
      code: 'weekly_orders', name: '每周订单', description: '每周订单汇总。', dataSourceId: 'data-source-8',
      outputFieldCodes: ['recordNo', 'amount'],
    },
  },
  result: null, errorCode: null, requestId: 'request-generated-report', traceId: 'trace-generated-report',
}
const printGeneratedDraftProposal: AiGeneratedDraftProposal = {
  id: 'generated-print', sessionId: 'session-1', turnId: 'turn-generated-print', state: 'PENDING', revision: 4,
  operation: 'CONFIG_PRINT_TEMPLATE_DRAFT', confidence: 0.91, clarification: null, expiresAt: '2099-08-04T05:00:00Z',
  preview: {
    operation: 'CONFIG_PRINT_TEMPLATE_DRAFT', flowDefinition: null, reportDefinition: null,
    printTemplate: {
      moduleCode: 'orders', code: 'order_receipt', name: '订单回执', paperSize: 'A4', orientation: 'PORTRAIT',
      title: '订单回执单', fieldCodes: ['recordNo', 'amount'], footer: '内部使用',
    },
  },
  result: null, errorCode: null, requestId: 'request-generated-print', traceId: 'trace-generated-print',
}
const flowGeneratedDraftSuccess: AiGeneratedDraftProposal = {
  ...flowGeneratedDraftProposal, state: 'SUCCEEDED', revision: 3,
  result: {
    operation: 'FLOW_DEFINITION_DRAFT', reportDefinition: null, printTemplate: null,
    flowDefinition: {
      ...flowGeneratedDraftProposal.preview!.flowDefinition!, definitionId: 'flow-definition-11', revision: 1,
      updatedAt: '2026-08-04T05:01:00Z', published: false,
    },
  },
}
const reportGeneratedDraftSuccess: AiGeneratedDraftProposal = {
  ...reportGeneratedDraftProposal, state: 'SUCCEEDED', revision: 4,
  result: {
    operation: 'CONFIG_REPORT_DRAFT', flowDefinition: null, printTemplate: null,
    reportDefinition: {
      ...reportGeneratedDraftProposal.preview!.reportDefinition!, reportId: 'report-11', draftVersion: 1, version: 1,
      createdAt: '2026-08-04T05:02:00Z', updatedAt: '2026-08-04T05:02:00Z', published: false,
    },
  },
}
const printGeneratedDraftSuccess: AiGeneratedDraftProposal = {
  ...printGeneratedDraftProposal, state: 'SUCCEEDED', revision: 5,
  result: {
    operation: 'CONFIG_PRINT_TEMPLATE_DRAFT', flowDefinition: null, reportDefinition: null,
    printTemplate: {
      ...printGeneratedDraftProposal.preview!.printTemplate!, templateId: 'print-template-11', moduleId: 'module-orders',
      status: 'DISABLED', version: 1, updatedAt: '2026-08-04T05:03:00Z', published: false,
    },
  },
}

function render() {
  return mount(SystemAgentView, { global: { stubs: {
    Bot: true, MessageSquarePlus: true, RefreshCw: true, RotateCcw: true, Send: true, UserRound: true,
    'a-button': { inheritAttrs: false, props: ['disabled', 'loading'], emits: ['click'], template: '<button v-bind="$attrs" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
    'a-alert': { props: ['message', 'description'], template: '<div class="alert-stub">{{ message }} {{ description }}<slot name="action" /></div>' },
    'a-spin': { template: '<div><slot /></div>' }, 'a-tag': { template: '<span><slot /></span>' },
    'a-empty': { props: ['description'], template: '<div>{{ description }}</div>' },
  } } })
}

describe('SystemAgentView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(aiRuntimeApi.capability).mockResolvedValue({ available: true, reason: null, policyVersion: 'policy-4' })
    vi.mocked(aiRuntimeApi.sessions).mockResolvedValue({ rows: [session], page: 1, size: 50, hasMore: false })
    vi.mocked(aiRuntimeApi.session).mockResolvedValue(detail)
    vi.mocked(aiRuntimeApi.createSession).mockResolvedValue({ ...session, id: 'session-2', title: '客户查询' })
    vi.mocked(aiRuntimeApi.submitMessage).mockResolvedValue(answer)
    vi.mocked(aiRuntimeApi.confirmation).mockResolvedValue(proposal)
    vi.mocked(aiRuntimeApi.confirm).mockResolvedValue({
      ...proposal, state: 'SUCCEEDED', version: 8,
      result: {
        recordId: 'record-1', recordNo: 'SO-1', recordVersion: 5, status: 'ACTIVE',
        title: '订单 SO-1', schemaVersionId: '12', values: { amount: '125.00' },
        executedAt: '2026-08-04T00:05:00Z',
      },
    })
    vi.mocked(aiRuntimeApi.reject).mockResolvedValue({ ...proposal, state: 'REJECTED', version: 8 })
    vi.mocked(aiRuntimeApi.configurationProposal).mockResolvedValue(configurationProposal)
    vi.mocked(aiRuntimeApi.confirmConfigurationProposal).mockResolvedValue({
      ...configurationProposal, state: 'SUCCEEDED', revision: 4,
      result: {
        configRootId: 'config-root-1', moduleId: 'module-orders', draftRevision: 13, moduleCode: 'orders', fieldId: 'field-5',
        fieldCode: 'externalReference', fieldName: '外部引用', fieldType: 'TEXT', required: true,
        settings: { maxLength: 120, precision: null, scale: null, minimum: null, maximum: null },
        sortOrder: 5, fieldVersion: 1, draftStatus: 'DRAFT',
      },
    })
    vi.mocked(aiRuntimeApi.rejectConfigurationProposal).mockResolvedValue({
      ...configurationProposal, state: 'REJECTED', revision: 4,
    })
    vi.mocked(aiRuntimeApi.configurationArtifactProposal).mockResolvedValue(selectionArtifactProposal)
    vi.mocked(aiRuntimeApi.confirmConfigurationArtifactProposal).mockResolvedValue({
      ...selectionArtifactProposal, state: 'SUCCEEDED', revision: 6,
      result: {
        operation: 'CONFIG_SELECTION_FIELD_DRAFT', configRootId: 'config-root-1', moduleId: 'module-orders',
        moduleCode: 'orders', draftRevision: 14, pageLayout: null, filterScenario: null,
        fieldPermissionStage: null, draftStatus: 'DRAFT',
        selectionField: {
          dictionary: { dictionaryId: 'dictionary-priority', dictionaryCode: 'order_priority', dictionaryName: '订单优先级', version: 1 },
          options: [
            { optionId: 'option-high', code: 'HIGH', label: '高', semanticKey: 'priority.high', color: '#E5484D', defaultOption: false, sortOrder: 0, version: 1 },
            { optionId: 'option-medium', code: 'MEDIUM', label: '中', semanticKey: 'priority.medium', color: '#F5A623', defaultOption: true, sortOrder: 1, version: 1 },
            { optionId: 'option-low', code: 'LOW', label: '低', semanticKey: 'priority.low', color: '#30A46C', defaultOption: false, sortOrder: 2, version: 1 },
          ],
          fieldId: 'field-priority', fieldCode: 'priority', fieldName: '优先级', fieldType: 'RADIO',
          required: true, dictionaryId: 'dictionary-priority', sortOrder: 5, maxSelections: null, version: 1,
        },
      },
    })
    vi.mocked(aiRuntimeApi.rejectConfigurationArtifactProposal).mockResolvedValue({
      ...selectionArtifactProposal, state: 'REJECTED', revision: 6,
    })
    vi.mocked(aiRuntimeApi.workProposal).mockResolvedValue(taskWorkProposal)
    vi.mocked(aiRuntimeApi.confirmWorkProposal).mockResolvedValue(taskWorkSuccess)
    vi.mocked(aiRuntimeApi.rejectWorkProposal).mockResolvedValue({
      ...taskWorkProposal, state: 'REJECTED', revision: 3,
    })
    vi.mocked(aiRuntimeApi.generatedDraftProposal).mockResolvedValue(flowGeneratedDraftProposal)
    vi.mocked(aiRuntimeApi.confirmGeneratedDraftProposal).mockResolvedValue(flowGeneratedDraftSuccess)
    vi.mocked(aiRuntimeApi.rejectGeneratedDraftProposal).mockResolvedValue({
      ...flowGeneratedDraftProposal, state: 'REJECTED', revision: 3,
    })
  })

  it('keeps unconfigured and disabled capabilities distinct without calling session APIs', async () => {
    vi.mocked(aiRuntimeApi.capability).mockResolvedValueOnce({ available: false, reason: 'POLICY_UNCONFIGURED', policyVersion: null })
    const unconfigured = render()
    await flushPromises()
    expect(aiRuntimeApi.capability).toHaveBeenCalledWith('10')
    expect(aiRuntimeApi.sessions).not.toHaveBeenCalled()
    expect(unconfigured.get('.agent-unavailable-state').text()).toContain('Agent 尚未配置')
    expect(unconfigured.text()).toContain('POLICY_UNCONFIGURED')

    vi.mocked(aiRuntimeApi.capability).mockResolvedValueOnce({ available: false, reason: 'POLICY_DISABLED', policyVersion: 'policy-4' })
    const disabled = render()
    await flushPromises()
    expect(disabled.get('.agent-unavailable-state').text()).toContain('Agent 已停用')
    expect(disabled.text()).toContain('POLICY_DISABLED')
  })

  it('lists, creates and opens sessions, then submits one bounded read-only turn', async () => {
    vi.mocked(aiRuntimeApi.session).mockImplementation(async (_systemId, sessionId) => ({
      ...detail, session: { ...session, id: sessionId, title: sessionId === 'session-2' ? '客户查询' : '订单查询' },
    }))
    const wrapper = render()
    await flushPromises()
    expect(aiRuntimeApi.sessions).toHaveBeenCalledWith('10', 1, 50)
    expect(aiRuntimeApi.session).toHaveBeenCalledWith('10', 'session-1')
    expect(wrapper.get('.agent-message-list').text()).toContain('查询订单')

    await wrapper.get('.agent-session-title').setValue('客户查询')
    await wrapper.get('.agent-session-create-button').trigger('click')
    await flushPromises()
    expect(aiRuntimeApi.createSession).toHaveBeenCalledWith('10', { title: '客户查询' })
    expect(aiRuntimeApi.session).toHaveBeenLastCalledWith('10', 'session-2')

    await wrapper.get('.agent-message-input').setValue('列出客户')
    await wrapper.get('.agent-message-submit').trigger('click')
    await flushPromises()
    expect(aiRuntimeApi.submitMessage).toHaveBeenCalledWith('10', 'session-2', { content: '列出客户' })
    expect(wrapper.get('.agent-message-list').text()).toContain('当前有 1 条订单')
    expect(wrapper.get('.agent-tool-result').text()).toContain('RECORD_QUERY · orders')
    expect(wrapper.get('.agent-tool-result').text()).toContain('SO-1')
  })

  it('renders a typed record context summary without mutation actions', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{
        ...answer, id: 'turn-record-context', tool: null,
        contextResult: {
          operation: 'RECORD_CONTEXT_SUMMARY', tasks: [], reports: [], todos: null, messages: null,
          record: {
            moduleCode: 'orders', recordId: 'record-1', recordNo: 'SO-1', title: '订单 SO-1',
            status: 'ACTIVE', recordVersion: 5, summary: '该订单正在等待发货。',
            values: { amount: '125.00', priority: 'HIGH' }, updatedAt: '2026-08-04T00:05:00Z',
          },
        },
      }],
    })
    const wrapper = render()
    await flushPromises()
    const card = wrapper.get('.agent-record-context-result')
    expect(card.text()).toContain('订单 SO-1')
    expect(card.text()).toContain('orders · ACTIVE')
    expect(card.text()).toContain('该订单正在等待发货')
    expect(card.text()).toContain('amount125.00')
    expect(wrapper.find('.agent-confirmation-confirm').exists()).toBe(false)
    expect(wrapper.find('.config-field-proposal-confirm').exists()).toBe(false)
    expect(wrapper.find('.config-artifact-proposal-confirm').exists()).toBe(false)
  })

  it('renders typed record comments, masked history and safe file metadata with native routes', async () => {
    const route = '/systems/10/workbench?module=orders&mode=view&record=9001'
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [
        { ...answer, id: 'turn-record-comments', tool: null, contextResult: {
          operation: 'RECORD_COMMENT_QUERY', record: null, tasks: [], reports: [],
          todos: null, messages: null, workMetrics: null, recordHistory: null, recordFiles: null,
          recordComments: {
            moduleCode: 'orders', recordId: '9001', total: 2, route,
            items: [{
              commentId: '101', parentCommentId: null, authorMemberId: '200',
              body: '客户已确认交付时间。', deleted: false, version: 2,
              createdAt: '2026-08-05T01:00:00Z', updatedAt: '2026-08-05T01:05:00Z',
              mentionedMemberIds: ['201', '202'],
            }, {
              commentId: '100', parentCommentId: null, authorMemberId: '199', body: null,
              deleted: true, version: 3, createdAt: '2026-08-04T01:00:00Z',
              updatedAt: '2026-08-05T00:00:00Z', mentionedMemberIds: [],
            }],
          },
        } },
        { ...answer, id: 'turn-record-history', tool: null, contextResult: {
          operation: 'RECORD_HISTORY_QUERY', record: null, tasks: [], reports: [],
          todos: null, messages: null, workMetrics: null, recordComments: null, recordFiles: null,
          recordHistory: {
            moduleCode: 'orders', recordId: '9001', total: 1, route,
            items: [{
              historyId: '301', recordVersion: 7, action: 'UPDATED', actorMemberId: '200',
              occurredAt: '2026-08-05T02:00:00Z', diff: [{
                fieldCode: 'amount', beforeValueJson: '100', afterValueJson: '125', masked: false,
              }, {
                fieldCode: 'secretNote', beforeValueJson: '"TOP-SECRET-BEFORE"',
                afterValueJson: '"TOP-SECRET-AFTER"', masked: true,
              }],
            }],
          },
        } },
        { ...answer, id: 'turn-record-files', tool: null, contextResult: {
          operation: 'RECORD_FILE_QUERY', record: null, tasks: [], reports: [],
          todos: null, messages: null, workMetrics: null, recordComments: null, recordHistory: null,
          recordFiles: {
            moduleCode: 'orders', recordId: '9001', total: 1, route,
            items: [{
              fileId: '401', originalName: 'delivery-plan.pdf', mediaType: 'application/pdf',
              size: 2048, uploaderMemberId: '200', createdAt: '2026-08-05T03:00:00Z',
              referencedAt: '2026-08-05T03:05:00Z',
            }],
          },
        } },
      ],
    })
    const wrapper = render()
    await flushPromises()

    const comments = wrapper.get('.agent-record-comment-result')
    expect(comments.text()).toContain('客户已确认交付时间。')
    expect(comments.text()).toContain('评论已删除')
    expect(comments.text()).toContain('提及成员 201、202')
    const history = wrapper.get('.agent-record-history-result')
    expect(history.text()).toContain('amount')
    expect(history.text()).toContain('100→125')
    expect(history.text()).toContain('secretNote已脱敏，不展示变更前后值')
    expect(history.text()).not.toContain('TOP-SECRET')
    const files = wrapper.get('.agent-record-file-result')
    expect(files.text()).toContain('delivery-plan.pdf')
    expect(files.text()).toContain('application/pdf')
    expect(files.text()).toContain('2 KB')
    expect(files.text()).toContain('上传成员200')
    expect(files.text()).not.toContain('引用成员')
    const nativeRoutes = wrapper.findAll('.agent-record-activity-route')
    expect(nativeRoutes).toHaveLength(3)
    expect(nativeRoutes.every(link => link.attributes('href') === route)).toBe(true)
    expect(wrapper.find('[class$="-confirm"]').exists()).toBe(false)
    expect(wrapper.find('[class$="-reject"]').exists()).toBe(false)
  })

  it('keeps all three record-activity loading, empty and failure states explicit', async () => {
    const common = { record: null, tasks: [], reports: [], todos: null, messages: null, workMetrics: null }
    const empty = () => ({
      moduleCode: 'orders', recordId: '9001', total: 0,
      route: '/systems/10/workbench?module=orders&mode=view&record=9001', items: [],
    })
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [
        { ...answer, id: 'comments-running', status: 'RUNNING', answer: null, tool: null,
          contextResult: { ...common, operation: 'RECORD_COMMENT_QUERY', recordComments: null, recordHistory: null, recordFiles: null } },
        { ...answer, id: 'comments-empty', tool: null,
          contextResult: { ...common, operation: 'RECORD_COMMENT_QUERY', recordComments: empty(), recordHistory: null, recordFiles: null } },
        { ...answer, id: 'comments-failed', status: 'FAILED', answer: null, errorCode: 'AI_RECORD_COMMENT_DENIED', tool: null,
          contextResult: { ...common, operation: 'RECORD_COMMENT_QUERY', recordComments: null, recordHistory: null, recordFiles: null } },
        { ...answer, id: 'history-running', status: 'RUNNING', answer: null, tool: null,
          contextResult: { ...common, operation: 'RECORD_HISTORY_QUERY', recordComments: null, recordHistory: null, recordFiles: null } },
        { ...answer, id: 'history-empty', tool: null,
          contextResult: { ...common, operation: 'RECORD_HISTORY_QUERY', recordComments: null, recordHistory: empty(), recordFiles: null } },
        { ...answer, id: 'history-failed', status: 'FAILED', answer: null, errorCode: 'AI_RECORD_HISTORY_DENIED', tool: null,
          contextResult: { ...common, operation: 'RECORD_HISTORY_QUERY', recordComments: null, recordHistory: null, recordFiles: null } },
        { ...answer, id: 'files-running', status: 'RUNNING', answer: null, tool: null,
          contextResult: { ...common, operation: 'RECORD_FILE_QUERY', recordComments: null, recordHistory: null, recordFiles: null } },
        { ...answer, id: 'files-empty', tool: null,
          contextResult: { ...common, operation: 'RECORD_FILE_QUERY', recordComments: null, recordHistory: null, recordFiles: empty() } },
        { ...answer, id: 'files-failed', status: 'FAILED', answer: null, errorCode: 'AI_RECORD_FILE_DENIED', tool: null,
          contextResult: { ...common, operation: 'RECORD_FILE_QUERY', recordComments: null, recordHistory: null, recordFiles: null } },
      ],
    })
    const wrapper = render()
    await flushPromises()

    expect(wrapper.findAll('.agent-context-loading')).toHaveLength(3)
    expect(wrapper.findAll('.agent-context-error')).toHaveLength(3)
    expect(wrapper.text()).toContain('当前记录没有可见评论')
    expect(wrapper.text()).toContain('当前记录没有可见历史')
    expect(wrapper.text()).toContain('当前记录没有可见附件')
    expect(wrapper.text()).toContain('AI_RECORD_COMMENT_DENIED')
    expect(wrapper.text()).toContain('AI_RECORD_HISTORY_DENIED')
    expect(wrapper.text()).toContain('AI_RECORD_FILE_DENIED')
    expect(wrapper.find('[class$="-confirm"]').exists()).toBe(false)
    expect(wrapper.find('[class$="-reject"]').exists()).toBe(false)
  })

  it('renders typed work task and daily report query cards without confirmation', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [
        { ...answer, id: 'turn-work-tasks', tool: null, contextResult: {
          operation: 'WORK_TASK_QUERY', record: null, reports: [], tasks: [{
            taskId: 'task-1', title: '跟进客户', status: 'OPEN', priority: 'HIGH',
            dueAt: '2026-08-05T08:00:00Z', projectId: 'project-1', assigneeMemberId: 'member-2',
          }], todos: null, messages: null,
        } },
        { ...answer, id: 'turn-work-reports', tool: null, contextResult: {
          operation: 'WORK_DAILY_REPORT_QUERY', record: null, tasks: [], reports: [{
            reportId: 'report-1', workDate: '2026-08-04', status: 'SUBMITTED', authorMemberId: 'member-2',
            completedWork: '完成客户回访', plannedWork: '准备交付', blockers: '等待外部确认',
          }], todos: null, messages: null,
        } },
      ],
    })
    const wrapper = render()
    await flushPromises()
    const task = wrapper.get('.agent-work-task-result')
    expect(task.text()).toContain('跟进客户')
    expect(task.text()).toContain('task-1 · 项目 project-1')
    expect(task.text()).toContain('负责人member-2')
    const report = wrapper.get('.agent-work-report-result')
    expect(report.text()).toContain('2026-08-04')
    expect(report.text()).toContain('完成客户回访')
    expect(report.text()).toContain('等待外部确认')
    expect(wrapper.find('[class$="-confirm"]').exists()).toBe(false)
    expect(wrapper.find('[class$="-reject"]').exists()).toBe(false)
  })

  it('renders one read-only Work project progress and metrics card with project-scoped drills', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{
        ...answer, id: 'turn-work-project-metrics', tool: null,
        contextResult: {
          operation: 'WORK_PROJECT_METRICS_QUERY', record: null, tasks: [], reports: [],
          todos: null, messages: null,
          workMetrics: {
            projectId: '501', title: 'Release project', status: 'ACTIVE',
            updatedAt: '2026-08-04T02:00:00Z',
            fromInclusive: '2026-08-01', toExclusive: '2026-08-08',
            visibility: 'PARTICIPATING', total: 2, open: 1, completed: 1,
            overdueOpen: {
              count: 1,
              route: '/systems/10/tasks?role=PARTICIPATING&projectId=501&status=OPEN&time=OVERDUE',
            },
            dueInRangeOpen: {
              count: 1,
              route: '/systems/10/tasks?role=PARTICIPATING&projectId=501&status=OPEN&dueFrom=2026-08-01',
            },
            completedInRange: {
              count: 1,
              route: '/systems/10/tasks?role=PARTICIPATING&projectId=501&status=COMPLETED&updatedFrom=2026-08-01',
            },
            daily: [{
              date: '2026-08-04', createdCount: 1, completedCount: 1,
              createdRoute: '/systems/10/tasks?role=PARTICIPATING&projectId=501&createdFrom=2026-08-04',
              completedRoute: '/systems/10/tasks?role=PARTICIPATING&projectId=501&status=COMPLETED&updatedFrom=2026-08-04',
            }],
            topAssignees: [{
              assigneeMemberId: '200', openCount: 1,
              route: '/systems/10/tasks?role=PARTICIPATING&projectId=501&status=OPEN&assigneeMemberId=200',
            }],
          },
        },
      }],
    })
    const wrapper = render()
    await flushPromises()

    const card = wrapper.get('.agent-work-project-metrics-result')
    expect(card.text()).toContain('Release project')
    expect(card.text()).toContain('项目 501 · PARTICIPATING')
    expect(card.text()).toContain('UTC [2026-08-01, 2026-08-08)')
    expect(card.text()).toContain('总任务2')
    expect(card.text()).toContain('已完成1')
    expect(card.text()).toContain('完成率50%')
    expect(card.text()).toContain('2026-08-04')
    expect(card.text()).toContain('成员 200')
    const drills = card.findAll('a')
    expect(drills).toHaveLength(6)
    expect(drills.every(link => String(link.attributes('href')).includes('projectId=501'))).toBe(true)
    expect(wrapper.find('[class$="-confirm"]').exists()).toBe(false)
    expect(wrapper.find('[class$="-reject"]').exists()).toBe(false)
  })

  it('keeps Work project metrics loading, empty and failure states explicit', async () => {
    const contextResult = {
      operation: 'WORK_PROJECT_METRICS_QUERY' as const,
      record: null, tasks: [], reports: [], todos: null, messages: null, workMetrics: null,
    }
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [
        { ...answer, id: 'turn-work-metrics-loading', status: 'RUNNING', answer: null, tool: null, contextResult },
        { ...answer, id: 'turn-work-metrics-empty', tool: null, contextResult },
        { ...answer, id: 'turn-work-metrics-failed', status: 'FAILED', answer: null, tool: null,
          errorCode: 'AI_WORK_PROJECT_NOT_FOUND', contextResult },
      ],
    })
    const wrapper = render()
    await flushPromises()

    expect(wrapper.get('.agent-context-loading').text()).toContain('正在读取实时上下文')
    expect(wrapper.findAll('.agent-work-project-metrics-result')
      .some(card => card.text().includes('当前权限范围内没有可见项目指标'))).toBe(true)
    expect(wrapper.get('.agent-context-error').text()).toContain('AI_WORK_PROJECT_NOT_FOUND')
    expect(wrapper.find('[class$="-confirm"]').exists()).toBe(false)
    expect(wrapper.find('[class$="-reject"]').exists()).toBe(false)
  })

  it('renders exact scalar values and bounded grouped runtime statistics without controls', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [
        { ...answer, id: 'turn-runtime-statistics-scalar', tool: null, contextResult: {
          operation: 'RUNTIME_STATISTICS_QUERY', record: null, tasks: [], reports: [], todos: null, messages: null,
          runtimeStatistics: {
            dataSourceCode: 'published_orders', moduleCode: 'orders', dataSourceVersionNumber: 7,
            aggregation: 'SUM', measureFieldCode: 'amount', value: '12345678901234567890.123400',
            matchedRecordCount: 42, bucketCount: 0, totalBucketCount: 0, truncated: false,
            grouping: null, trend: null,
          },
        } },
        { ...answer, id: 'turn-runtime-statistics-grouped', tool: null, contextResult: {
          operation: 'RUNTIME_STATISTICS_QUERY', record: null, tasks: [], reports: [], todos: null, messages: null,
          runtimeStatistics: {
            dataSourceCode: 'published_orders', moduleCode: 'orders', dataSourceVersionNumber: 7,
            aggregation: 'AVG', measureFieldCode: 'amount', value: null,
            matchedRecordCount: 11, bucketCount: 2, totalBucketCount: 5, truncated: true,
            grouping: {
              fieldCode: 'region', buckets: [
                { label: '华东', nullBucket: false, value: '88.5000', recordCount: 10 },
                { label: null, nullBucket: true, value: '0.0000', recordCount: 1 },
              ],
            },
            trend: null,
          },
        } },
      ],
    })
    const wrapper = render()
    await flushPromises()

    const cards = wrapper.findAll('.agent-runtime-statistics-result')
    expect(cards).toHaveLength(2)
    expect(cards[0]!.text()).toContain('12345678901234567890.123400')
    expect(cards[0]!.text()).toContain('amount')
    expect(cards[1]!.text()).toContain('按 region 分组')
    expect(cards[1]!.text()).toContain('华东')
    expect(cards[1]!.text()).toContain('空值分组')
    expect(cards[1]!.text()).toContain('0.0000')
    expect(cards[1]!.text()).toContain('2 / 5 · 已截断')
    expect(cards[1]!.find('a').exists()).toBe(false)
    expect(cards[1]!.find('button').exists()).toBe(false)
    expect(cards[1]!.text()).not.toContain('导出')
  })

  it('distinguishes zero values from empty trend state and preserves an empty COUNT value', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [
        { ...answer, id: 'turn-runtime-statistics-trend-sum', tool: null, contextResult: {
          operation: 'RUNTIME_STATISTICS_QUERY', record: null, tasks: [], reports: [], todos: null, messages: null,
          runtimeStatistics: {
            dataSourceCode: 'published_orders', moduleCode: 'orders', dataSourceVersionNumber: 8,
            aggregation: 'SUM', measureFieldCode: 'amount', value: null,
            matchedRecordCount: 1, bucketCount: 2, totalBucketCount: 2, truncated: false,
            grouping: null,
            trend: {
              fieldCode: 'createdAt', grain: 'DAY', startInclusive: '2026-08-01', endExclusive: '2026-08-03',
              buckets: [
                { startInclusive: '2026-08-01', endExclusive: '2026-08-02', value: '0', recordCount: 1, empty: false },
                { startInclusive: '2026-08-02', endExclusive: '2026-08-03', value: null, recordCount: 0, empty: true },
              ],
            },
          },
        } },
        { ...answer, id: 'turn-runtime-statistics-trend-count', tool: null, contextResult: {
          operation: 'RUNTIME_STATISTICS_QUERY', record: null, tasks: [], reports: [], todos: null, messages: null,
          runtimeStatistics: {
            dataSourceCode: 'published_orders', moduleCode: 'orders', dataSourceVersionNumber: 8,
            aggregation: 'COUNT', measureFieldCode: null, value: null,
            matchedRecordCount: 0, bucketCount: 1, totalBucketCount: 1, truncated: false,
            grouping: null,
            trend: {
              fieldCode: 'createdAt', grain: 'DAY', startInclusive: '2026-08-03', endExclusive: '2026-08-04',
              buckets: [
                { startInclusive: '2026-08-03', endExclusive: '2026-08-04', value: '0', recordCount: 0, empty: true },
              ],
            },
          },
        } },
      ],
    })
    const wrapper = render()
    await flushPromises()

    const trends = wrapper.findAll('.agent-runtime-statistics-trend')
    expect(trends).toHaveLength(2)
    expect(trends[0]!.text()).toContain('DAY · [2026-08-01, 2026-08-03)')
    const buckets = trends[0]!.findAll('article')
    expect(buckets).toHaveLength(2)
    expect(buckets[0]!.text()).toContain('0')
    expect(buckets[0]!.text()).toContain('1 条记录')
    expect(buckets[0]!.text()).not.toContain('空桶')
    expect(buckets[1]!.text()).toContain('空桶')
    expect(buckets[1]!.text()).toContain('无聚合值')
    expect(buckets[1]!.text()).not.toContain('0 条记录')
    const emptyCountBucket = trends[1]!.get('article')
    expect(emptyCountBucket.text()).toContain('空桶')
    expect(emptyCountBucket.text()).toContain('0')
    expect(emptyCountBucket.text()).not.toContain('无聚合值')
  })

  it('keeps runtime statistics loading, succeeded-empty and failure states explicit', async () => {
    const contextResult = {
      operation: 'RUNTIME_STATISTICS_QUERY' as const,
      record: null, tasks: [], reports: [], todos: null, messages: null, runtimeStatistics: null,
    }
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [
        { ...answer, id: 'turn-runtime-statistics-loading', status: 'RUNNING', answer: null, tool: null, contextResult },
        { ...answer, id: 'turn-runtime-statistics-empty', tool: null, contextResult },
        { ...answer, id: 'turn-runtime-statistics-failed', status: 'FAILED', answer: null, tool: null,
          errorCode: 'AI_RUNTIME_STATISTICS_SOURCE_NOT_FOUND', contextResult },
      ],
    })
    const wrapper = render()
    await flushPromises()

    expect(wrapper.get('.agent-context-loading').text()).toContain('正在读取实时上下文')
    expect(wrapper.findAll('.agent-runtime-statistics-result')
      .some(card => card.text().includes('当前权限范围内没有可显示的统计结果'))).toBe(true)
    expect(wrapper.get('.agent-context-error').text()).toContain('AI_RUNTIME_STATISTICS_SOURCE_NOT_FOUND')
    expect(wrapper.find('[class$="-confirm"]').exists()).toBe(false)
    expect(wrapper.find('[class$="-reject"]').exists()).toBe(false)
  })

  it('renders an ordered display-value-only runtime report with one native link', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{
        ...answer, id: 'turn-runtime-report', tool: null, contextResult: {
          operation: 'RUNTIME_REPORT_QUERY', record: null, tasks: [], reports: [], todos: null, messages: null,
          runtimeReport: {
            reportCode: 'monthly_orders', reportName: '月度订单', reportVersionNumber: 4,
            dataSourceCode: 'published_orders', dataSourceVersionNumber: 7, moduleCode: 'orders',
            page: 2, size: 3, total: 8, returnedRows: 1, hasMore: true,
            route: '/systems/10/reports/monthly_orders',
            fields: [
              { fieldCode: 'amount', fieldName: '金额', type: 'DECIMAL' },
              { fieldCode: 'identity', fieldName: '证件号', type: 'IDENTITY' },
              { fieldCode: 'note', fieldName: '备注', type: 'TEXT' },
            ],
            rows: [{ values: [
              { fieldCode: 'amount', displayValue: '10000000000000000000.123400' },
              { fieldCode: 'identity', displayValue: '********' },
              { fieldCode: 'note', displayValue: null },
            ] }],
          },
        },
      }],
    })
    const wrapper = render()
    await flushPromises()

    const card = wrapper.get('.agent-runtime-report-result')
    expect(card.text()).toContain('月度订单')
    expect(card.text()).toContain('monthly_orders · 报表 v4')
    expect(card.text()).toContain('数据源 published_orders v7 · 模块 orders')
    expect(card.text()).toContain('当前页2')
    expect(card.text()).toContain('本页返回1')
    expect(card.text()).toContain('总行数8 · 还有更多')
    expect(card.findAll('th').map(item => item.text())).toEqual([
      '金额amount · DECIMAL', '证件号identity · IDENTITY', '备注note · TEXT',
    ])
    expect(card.findAll('td').map(item => item.text())).toEqual([
      '10000000000000000000.123400', '********', '—',
    ])
    expect(card.text()).not.toContain('RAW_IDENTITY_SENTINEL')
    const links = card.findAll('a')
    expect(links).toHaveLength(1)
    expect(links[0]!.text()).toBe('打开报表')
    expect(links[0]!.attributes('href')).toBe('/systems/10/reports/monthly_orders')
    expect(card.find('button').exists()).toBe(false)
    expect(card.text()).not.toContain('导出')
    expect(card.text()).not.toContain('上一页')
    expect(card.text()).not.toContain('下一页')
  })

  it('keeps runtime report executing, both succeeded-empty states and failure explicit', async () => {
    const nullContext = {
      operation: 'RUNTIME_REPORT_QUERY' as const,
      record: null, tasks: [], reports: [], todos: null, messages: null, runtimeReport: null,
    }
    const zeroRowsContext = {
      ...nullContext,
      runtimeReport: {
        reportCode: 'empty_report', reportName: '空报表', reportVersionNumber: 1,
        dataSourceCode: 'published_orders', dataSourceVersionNumber: 7, moduleCode: 'orders',
        page: 1, size: 20, total: 0, returnedRows: 0, hasMore: false,
        route: '/systems/10/reports/empty_report',
        fields: [{ fieldCode: 'amount', fieldName: '金额', type: 'DECIMAL' }],
        rows: [],
      },
    }
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [
        { ...answer, id: 'turn-runtime-report-running', status: 'RUNNING', answer: null, tool: null,
          contextResult: nullContext },
        { ...answer, id: 'turn-runtime-report-null', tool: null, contextResult: nullContext },
        { ...answer, id: 'turn-runtime-report-zero-rows', tool: null, contextResult: zeroRowsContext },
        { ...answer, id: 'turn-runtime-report-failed', status: 'FAILED', answer: null, tool: null,
          errorCode: 'AI_RUNTIME_REPORT_NOT_FOUND', contextResult: nullContext },
      ],
    })
    const wrapper = render()
    await flushPromises()

    expect(wrapper.get('.agent-context-loading').text()).toContain('正在读取实时上下文')
    const reportCards = wrapper.findAll('.agent-runtime-report-result')
    expect(reportCards.some(card => card.text().includes('当前权限范围内没有可显示的已发布报表'))).toBe(true)
    expect(reportCards.some(card => card.text().includes('当前权限和报表固定筛选范围内没有数据'))).toBe(true)
    expect(wrapper.get('.agent-context-error').text()).toContain('AI_RUNTIME_REPORT_NOT_FOUND')
    expect(reportCards.flatMap(card => card.findAll('a'))).toHaveLength(1)
    expect(wrapper.find('[class$="-confirm"]').exists()).toBe(false)
    expect(wrapper.find('[class$="-reject"]').exists()).toBe(false)
  })

  it('renders ordered safe flow-instance history with one owner-derived route and no actions', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{
        ...answer, id: 'turn-flow-instance-history', tool: null, contextResult: {
          operation: 'FLOW_INSTANCE_HISTORY_QUERY', record: null, tasks: [], reports: [], todos: null, messages: null,
          flowHistory: {
            instanceId: '901', status: 'APPROVED', total: 3,
            route: '/systems/10/flows',
            events: [
              { sequence: 1, eventType: 'STARTED', fromStatus: null, toStatus: 'PENDING', actorMemberId: '501', comment: '发起审批', occurredAt: '2026-08-04T08:00:00Z' },
              { sequence: 2, eventType: 'TASK_CREATED', fromStatus: 'PENDING', toStatus: 'PENDING', actorMemberId: null, comment: null, occurredAt: '2026-08-04T08:01:00Z' },
              { sequence: 3, eventType: 'APPROVED', fromStatus: 'PENDING', toStatus: 'APPROVED', actorMemberId: '502', comment: '同意', occurredAt: '2026-08-04T08:02:00Z' },
            ],
          },
        },
      }],
    })
    const wrapper = render()
    await flushPromises()

    const card = wrapper.get('.agent-flow-instance-history-result')
    expect(card.text()).toContain('审批实例 901')
    expect(card.text()).toContain('APPROVED · 共 3 条')
    expect(card.findAll('li').map(item => item.attributes('data-sequence'))).toEqual(['1', '2', '3'])
    expect(card.findAll('li').map(item => item.text())).toEqual(expect.arrayContaining([
      expect.stringContaining('#1 STARTED'),
      expect.stringContaining('#2 TASK_CREATED'),
      expect.stringContaining('#3 APPROVED'),
    ]))
    expect(card.text()).toContain('成员 501')
    expect(card.text()).toContain('系统')
    expect(card.text()).toContain('发起审批')
    expect(card.text()).toContain('同意')
    expect(card.text()).not.toContain('RAW_EVIDENCE_SENTINEL')
    const links = card.findAll('a')
    expect(links).toHaveLength(1)
    expect(links[0]!.text()).toBe('打开审批实例')
    expect(links[0]!.attributes('href')).toBe('/systems/10/flows')
    expect(card.find('button').exists()).toBe(false)
    expect(wrapper.find('[class$="-confirm"]').exists()).toBe(false)
    expect(wrapper.find('[class$="-reject"]').exists()).toBe(false)
  })

  it('keeps flow-instance history succeeded-empty and failure states explicit', async () => {
    const emptyContextResult = {
      operation: 'FLOW_INSTANCE_HISTORY_QUERY' as const,
      record: null, tasks: [], reports: [], todos: null, messages: null,
      flowHistory: {
        instanceId: '902', status: 'PENDING', total: 0,
        route: '/systems/10/flows', events: [],
      },
    }
    const failedContextResult = { ...emptyContextResult, flowHistory: null }
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [
        { ...answer, id: 'turn-flow-history-empty', tool: null, contextResult: emptyContextResult },
        { ...answer, id: 'turn-flow-history-failed', status: 'FAILED', answer: null, tool: null,
          errorCode: 'AI_FLOW_INSTANCE_HISTORY_NOT_FOUND', contextResult: failedContextResult },
      ],
    })
    const wrapper = render()
    await flushPromises()

    const cards = wrapper.findAll('.agent-flow-instance-history-result')
    expect(cards.some(card => card.text().includes('审批实例 902'))).toBe(true)
    expect(cards.some(card => card.text().includes('当前审批实例没有可显示的历史事件'))).toBe(true)
    expect(wrapper.get('.agent-context-error').text()).toContain('AI_FLOW_INSTANCE_HISTORY_NOT_FOUND')
    expect(cards.flatMap(card => card.findAll('a'))).toHaveLength(1)
    expect(wrapper.find('[class$="-confirm"]').exists()).toBe(false)
    expect(wrapper.find('[class$="-reject"]').exists()).toBe(false)
  })

  it('renders bounded Todo and system-message typed cards without mutation actions', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [
        { ...answer, id: 'turn-todos', tool: null, contextResult: {
          operation: 'TODO_QUERY', record: null, tasks: [], reports: [], messages: null,
          todos: {
            category: 'ALL', state: 'OPEN', time: 'TODAY', total: 2,
            counts: { open: 4, task: 3, approval: 1, today: 2, overdue: 1 },
            items: [{
              id: 'todo-1', category: 'TASK', sourceType: 'WORK_TASK', sourceId: 'task-1',
              title: '跟进客户', priority: 100, dueAt: '2026-08-05T08:00:00Z',
              routeHint: '/systems/10/tasks?taskId=task-1', actions: ['COMPLETE'],
              state: 'OPEN', version: 3,
            }],
          },
        } },
        { ...answer, id: 'turn-messages', tool: null, contextResult: {
          operation: 'MESSAGE_QUERY', record: null, tasks: [], reports: [], todos: null,
          messages: {
            status: 'UNREAD', unreadCount: 2, total: 1,
            items: [{
              id: 'message-1', templateCode: 'FLOW_APPROVED', title: '审批已通过',
              body: '申请 SO-1 已通过。', target: { type: 'FLOW_INSTANCE', id: 'flow-1' },
              targetPath: '/systems/10/flows/flow-1', status: 'UNREAD',
              createdAt: '2026-08-04T08:00:00Z', readAt: null, archivedAt: null, version: 1,
            }],
          },
        } },
      ],
    })
    const wrapper = render()
    await flushPromises()

    const todos = wrapper.get('.agent-todo-result')
    expect(todos.text()).toContain('开放 4 · 任务 3 · 审批 1')
    expect(todos.text()).toContain('跟进客户')
    expect(todos.text()).toContain('/systems/10/tasks?taskId=task-1')
    const messages = wrapper.get('.agent-system-message-result')
    expect(messages.text()).toContain('未读2')
    expect(messages.text()).toContain('审批已通过')
    expect(messages.text()).toContain('申请 SO-1 已通过。')
    expect(messages.text()).toContain('FLOW_INSTANCE / flow-1')
    expect(wrapper.find('[class$="-confirm"]').exists()).toBe(false)
    expect(wrapper.find('[class$="-reject"]').exists()).toBe(false)
  })

  it('keeps context loading, empty and error states explicit', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [
        { ...answer, id: 'turn-context-loading', status: 'RUNNING', answer: null, tool: null, contextResult: {
          operation: 'WORK_TASK_QUERY', record: null, tasks: [], reports: [], todos: null, messages: null,
        } },
        { ...answer, id: 'turn-context-empty', tool: null, contextResult: {
          operation: 'WORK_DAILY_REPORT_QUERY', record: null, tasks: [], reports: [], todos: null, messages: null,
        } },
        { ...answer, id: 'turn-context-failed', status: 'FAILED', answer: null, tool: null,
          errorCode: 'WORK_CONTEXT_PERMISSION_DENIED', contextResult: {
            operation: 'RECORD_CONTEXT_SUMMARY', record: null, tasks: [], reports: [], todos: null, messages: null,
          } },
      ],
    })
    const wrapper = render()
    await flushPromises()
    expect(wrapper.get('.agent-context-loading').text()).toContain('按当前成员权限与脱敏规则返回')
    expect(wrapper.findAll('.agent-context-empty').map(item => item.text()).join(' ')).toContain('当前筛选没有可见工作日报')
    expect(wrapper.get('.agent-context-error').text()).toContain('WORK_CONTEXT_PERMISSION_DENIED')
    expect(wrapper.find('[class$="-confirm"]').exists()).toBe(false)
  })

  it('renders audited failed turns and offers retry only for retryable provider failures', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session,
      messages: [],
      turns: [{
        id: 'failed-1', status: 'FAILED', answer: null, errorCode: 'TOOL_PERMISSION_DENIED',
        retryable: false, tool: null, requestId: 'request-failed', traceId: 'trace-failed',
      }],
    })
    vi.mocked(aiRuntimeApi.submitMessage)
      .mockRejectedValueOnce(new Error('PROVIDER_TIMEOUT'))
      .mockResolvedValueOnce(answer)
    const wrapper = render()
    await flushPromises()
    expect(wrapper.get('.agent-message-list').text()).toContain('TOOL_PERMISSION_DENIED')
    expect(wrapper.get('.agent-message-list').text()).toContain('本轮未生成回答')

    await wrapper.get('.agent-message-input').setValue('重试查询')
    await wrapper.get('.agent-message-submit').trigger('click')
    await flushPromises()
    expect(wrapper.get('.agent-submit-error').text()).toContain('PROVIDER_TIMEOUT')
    await wrapper.get('.agent-message-retry').trigger('click')
    await flushPromises()
    expect(aiRuntimeApi.submitMessage).toHaveBeenCalledTimes(2)
    expect(aiRuntimeApi.submitMessage).toHaveBeenLastCalledWith('10', 'session-1', { content: '重试查询' })
  })

  it('renders the scalar configuration field preview and a non-executable clarification', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{
        ...answer, id: 'turn-config', answer: '请确认配置字段草稿。', tool: null, configurationProposal,
      }],
    })
    const pending = render()
    await flushPromises()
    const card = pending.get('.config-field-proposal')
    expect(card.text()).toContain('CONFIG_FIELD_DRAFT · orders')
    expect(card.text()).toContain('config-root-1')
    expect(card.text()).toContain('externalReference')
    expect(card.text()).toContain('外部引用')
    expect(card.text()).toContain('TEXT')
    expect(card.text()).toContain('最大长度 120')
    expect(card.text()).toContain('97%')
    expect(card.text()).toContain('不会发布或激活')
    expect(aiRuntimeApi.confirmConfigurationProposal).not.toHaveBeenCalled()

    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{
        ...answer, id: 'turn-config-clarification', tool: null,
        configurationProposal: {
          ...configurationProposal, id: 'configuration-proposal-clarification', state: 'CLARIFICATION_REQUIRED',
          preview: null, confidence: 0.43, clarification: '请补充字段编码和标量类型。',
        },
      }],
    })
    const clarification = render()
    await flushPromises()
    expect(clarification.get('.config-field-proposal-clarification').text()).toContain('请补充字段编码和标量类型')
    expect(clarification.find('.config-field-proposal-confirm').exists()).toBe(false)
    expect(clarification.find('.config-field-proposal-reject').exists()).toBe(false)
  })

  it('confirms a configuration proposal exactly once and renders the un-published draft readback', async () => {
    let resolveConfiguration!: (value: AiConfigurationFieldProposal) => void
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{ ...answer, id: 'turn-config', tool: null, configurationProposal }],
    })
    vi.mocked(aiRuntimeApi.confirmConfigurationProposal).mockReturnValue(new Promise(resolve => {
      resolveConfiguration = resolve
    }))
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.config-field-proposal-confirm').trigger('click')
    await wrapper.get('.config-field-proposal-confirm').trigger('click')
    expect(aiRuntimeApi.confirmConfigurationProposal).toHaveBeenCalledTimes(1)
    expect(aiRuntimeApi.confirmConfigurationProposal).toHaveBeenCalledWith(
      '10', 'session-1', 'configuration-proposal-1', 3, expect.any(String),
    )
    expect(wrapper.get('.config-field-proposal-confirm').attributes('disabled')).toBeDefined()

    resolveConfiguration({
      ...configurationProposal, state: 'SUCCEEDED', revision: 4,
      result: {
        configRootId: 'config-root-1', moduleId: 'module-orders', draftRevision: 13, moduleCode: 'orders', fieldId: 'field-5',
        fieldCode: 'externalReference', fieldName: '外部引用', fieldType: 'TEXT', required: true,
        settings: { maxLength: 120, precision: null, scale: null, minimum: null, maximum: null },
        sortOrder: 5, fieldVersion: 1, draftStatus: 'DRAFT',
      },
    })
    await flushPromises()
    const result = wrapper.get('.config-field-proposal-result')
    expect(result.text()).toContain('配置草稿真实回读')
    expect(result.text()).toContain('未发布')
    expect(result.text()).toContain('草稿修订13')
    expect(result.text()).toContain('字段顺序5')
    expect(result.text()).toContain('字段版本1')
    expect(result.text()).toContain('field-5')
    expect(wrapper.find('.config-field-proposal-confirm').exists()).toBe(false)
  })

  it('rejects a pending configuration proposal and explains permission, stale and proposal conflicts', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{ ...answer, id: 'turn-config', tool: null, configurationProposal }],
    })
    const rejected = render()
    await flushPromises()
    await rejected.get('.config-field-proposal-reject').trigger('click')
    await flushPromises()
    expect(aiRuntimeApi.rejectConfigurationProposal).toHaveBeenCalledWith(
      '10', 'session-1', 'configuration-proposal-1', 3,
    )
    expect(rejected.get('.config-field-proposal').text()).toContain('已拒绝')
    expect(rejected.find('.config-field-proposal-result').exists()).toBe(false)

    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [
        { ...answer, id: 'turn-permission', tool: null, configurationProposal: { ...configurationProposal, id: 'proposal-permission', state: 'FAILED', errorCode: 'CONFIG_FIELD_PERMISSION_DENIED' } },
        { ...answer, id: 'turn-stale', tool: null, configurationProposal: { ...configurationProposal, id: 'proposal-stale', state: 'FAILED', errorCode: 'CONFIG_DRAFT_REVISION_STALE' } },
        { ...answer, id: 'turn-conflict', tool: null, configurationProposal: { ...configurationProposal, id: 'proposal-conflict', state: 'FAILED', errorCode: 'PROPOSAL_REVISION_CONFLICT' } },
      ],
    })
    const failures = render()
    await flushPromises()
    const messages = failures.findAll('.config-field-proposal-error').map(item => item.text()).join(' ')
    expect(messages).toContain('当前配置权限或成员上下文已变化')
    expect(messages).toContain('配置草稿版本已变化')
    expect(messages).toContain('提案版本或确认请求冲突')
    expect(failures.find('.config-field-proposal-result').exists()).toBe(false)
  })

  it('reuses the same configuration confirmation idempotency key after a transient retry', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{ ...answer, id: 'turn-config', tool: null, configurationProposal }],
    })
    vi.mocked(aiRuntimeApi.confirmConfigurationProposal)
      .mockRejectedValueOnce(new Error('CONFIGURATION_OWNER_UNAVAILABLE'))
      .mockResolvedValueOnce({ ...configurationProposal, state: 'REJECTED', revision: 4 })
    vi.mocked(aiRuntimeApi.configurationProposal).mockResolvedValue(configurationProposal)
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.config-field-proposal-confirm').trigger('click')
    await flushPromises()
    await wrapper.get('.config-field-proposal-confirm').trigger('click')
    await flushPromises()

    expect(aiRuntimeApi.confirmConfigurationProposal).toHaveBeenCalledTimes(2)
    expect(vi.mocked(aiRuntimeApi.confirmConfigurationProposal).mock.calls[0]![4])
      .toBe(vi.mocked(aiRuntimeApi.confirmConfigurationProposal).mock.calls[1]![4])
  })

  it('renders strict selection options and page section/layout previews without writing', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [
        { ...answer, id: 'turn-artifact-selection', tool: null, artifactProposal: selectionArtifactProposal },
        { ...answer, id: 'turn-artifact-page', tool: null, artifactProposal: pageArtifactProposal },
      ],
    })
    const wrapper = render()
    await flushPromises()

    const selection = wrapper.get('.config-artifact-selection-preview')
    expect(selection.text()).toContain('优先级')
    expect(selection.text()).toContain('priority · RADIO')
    expect(selection.text()).toContain('订单优先级 · order_priority')
    expect(selection.text()).toContain('HIGH · priority.high')
    expect(selection.text()).toContain('默认')
    expect(wrapper.get('.config-artifact-proposal').text()).toContain('98%')

    const page = wrapper.get('.config-artifact-page-preview')
    expect(page.text()).toContain('order_form')
    expect(page.text()).toContain('FORM · 页面 page-order-form')
    expect(page.text()).toContain('2 列 · 间距 16')
    expect(page.text()).toContain('2 个分区 · 4 个字段')
    expect(page.text()).toContain('基础信息')
    expect(page.text()).toContain('orderNo · priority')
    expect(aiRuntimeApi.confirmConfigurationArtifactProposal).not.toHaveBeenCalled()
  })

  it('renders typed filter scenario and field permission previews with stable controls and no write', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [
        { ...answer, id: 'turn-artifact-filter-scenario', tool: null, artifactProposal: filterScenarioArtifactProposal },
        { ...answer, id: 'turn-artifact-field-permission', tool: null, artifactProposal: fieldPermissionArtifactProposal },
      ],
    })
    const wrapper = render()
    await flushPromises()

    const filterCard = wrapper.get('.config-filter-scenario-proposal')
    expect(filterCard.attributes('data-artifact-kind')).toBe('FILTER_SCENARIO')
    expect(filterCard.get('.config-filter-scenario-preview').attributes('data-page-code')).toBe('orders_list')
    expect(filterCard.text()).toContain('紧急优先')
    expect(filterCard.text()).toContain('设为默认共享筛选方案')
    expect(filterCard.get('.config-filter-scenario-filter').text()).toContain('"fieldCode": "priority"')
    expect(filterCard.get('.config-filter-scenario-sort').text()).toContain('"direction": "DESC"')
    expect(filterCard.find('.config-filter-scenario-confirm').exists()).toBe(true)
    expect(filterCard.find('.config-filter-scenario-reject').exists()).toBe(true)

    const permissionCard = wrapper.get('.config-field-permission-stage-proposal')
    expect(permissionCard.attributes('data-artifact-kind')).toBe('FIELD_PERMISSION_STAGE')
    expect(permissionCard.get('.config-field-permission-stage-preview').attributes('data-field-code')).toBe('secret_note')
    expect(permissionCard.text()).toContain('READ STAGED · WRITE INHERIT')
    expect(permissionCard.get('.config-field-permission-read-code').text()).toBe('module.orders.field.secret_note.read')
    expect(permissionCard.get('.config-field-permission-stage-notice').text()).toContain('STAGED 不限制运行时，也不会向任何角色授权')
    expect(permissionCard.get('.config-field-permission-stage-notice').text()).toContain('显式选择 ENFORCED')
    expect(permissionCard.find('.config-field-permission-stage-confirm').exists()).toBe(true)
    expect(permissionCard.find('.config-field-permission-stage-reject').exists()).toBe(true)
    expect(aiRuntimeApi.confirmConfigurationArtifactProposal).not.toHaveBeenCalled()
  })

  it('confirms a filter scenario into complete owner readback and opens the fresh page in Configuration Studio', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{ ...answer, id: 'turn-artifact-filter-scenario', tool: null, artifactProposal: filterScenarioArtifactProposal }],
    })
    vi.mocked(aiRuntimeApi.confirmConfigurationArtifactProposal).mockResolvedValueOnce(filterScenarioArtifactSuccess)
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.config-filter-scenario-confirm').trigger('click')
    await flushPromises()

    expect(aiRuntimeApi.confirmConfigurationArtifactProposal).toHaveBeenCalledWith(
      '10', 'session-1', 'artifact-proposal-filter-scenario', 9, expect.any(String),
    )
    const result = wrapper.get('.config-filter-scenario-result')
    expect(result.text()).toContain('orders_list · page-orders · v5')
    expect(result.text()).toContain('urgent_first')
    expect(result.findAll('.config-filter-scenario-readback article')).toHaveLength(2)
    expect(result.text()).toContain('recent_orders')
    await wrapper.get('.config-suggestion-open-studio').trigger('click')
    expect(routerPush).toHaveBeenCalledWith({
      name: 'system-admin-configuration', params: { systemId: '10' },
      query: { moduleCode: 'orders', resourceKind: 'page', resourceCode: 'orders_list' },
    })
  })

  it('confirms field permission staging into owner modes/codes and opens the fresh field in Configuration Studio', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{ ...answer, id: 'turn-artifact-field-permission', tool: null, artifactProposal: fieldPermissionArtifactProposal }],
    })
    vi.mocked(aiRuntimeApi.confirmConfigurationArtifactProposal).mockResolvedValueOnce(fieldPermissionArtifactSuccess)
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.config-field-permission-stage-confirm').trigger('click')
    await flushPromises()

    expect(aiRuntimeApi.confirmConfigurationArtifactProposal).toHaveBeenCalledWith(
      '10', 'session-1', 'artifact-proposal-field-permission', 11, expect.any(String),
    )
    const result = wrapper.get('.config-field-permission-stage-result')
    expect(result.text()).toContain('保密备注 · secret_note · field-secret-note')
    expect(result.get('.config-field-permission-read-mode').text()).toBe('STAGED')
    expect(result.get('.config-field-permission-write-mode').text()).toBe('INHERIT')
    expect(result.get('.config-field-permission-read-code').text()).toBe('module.orders.field.secret_note.read')
    await wrapper.get('.config-suggestion-open-studio').trigger('click')
    expect(routerPush).toHaveBeenCalledWith({
      name: 'system-admin-configuration', params: { systemId: '10' },
      query: { moduleCode: 'orders', resourceKind: 'field', resourceCode: 'secret_note' },
    })
  })

  it('confirms both artifact kinds into draft readbacks and supports rejection', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{ ...answer, id: 'turn-artifact-selection', tool: null, artifactProposal: selectionArtifactProposal }],
    })
    const selection = render()
    await flushPromises()
    await selection.get('.config-artifact-proposal-confirm').trigger('click')
    await flushPromises()
    expect(aiRuntimeApi.confirmConfigurationArtifactProposal).toHaveBeenCalledWith(
      '10', 'session-1', 'artifact-proposal-selection', 5, expect.any(String),
    )
    const selectionResult = selection.get('.config-artifact-proposal-result')
    expect(selectionResult.text()).toContain('DRAFT · 未发布')
    expect(selectionResult.text()).toContain('原子创建的字段、字典与选项')
    expect(selectionResult.text()).toContain('field-priority')
    expect(selectionResult.text()).toContain('dictionary-priority')
    expect(selectionResult.text()).toContain('option-medium')

    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{ ...answer, id: 'turn-artifact-page', tool: null, artifactProposal: pageArtifactProposal }],
    })
    vi.mocked(aiRuntimeApi.confirmConfigurationArtifactProposal).mockResolvedValueOnce(pageArtifactSuccess)
    const page = render()
    await flushPromises()
    await page.get('.config-artifact-proposal-confirm').trigger('click')
    await flushPromises()
    const pageResult = page.get('.config-artifact-page-result')
    expect(pageResult.text()).toContain('页面布局草稿')
    expect(pageResult.text()).toContain('order_form · page-order-form')
    expect(pageResult.text()).toContain('FORM · v5')
    expect(pageResult.text()).toContain('2 个分区 · 4 个字段')

    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{ ...answer, id: 'turn-artifact-reject', tool: null, artifactProposal: pageArtifactProposal }],
    })
    vi.mocked(aiRuntimeApi.rejectConfigurationArtifactProposal).mockResolvedValueOnce({
      ...pageArtifactProposal, state: 'REJECTED', revision: 8,
    })
    const rejected = render()
    await flushPromises()
    await rejected.get('.config-artifact-proposal-reject').trigger('click')
    await flushPromises()
    expect(aiRuntimeApi.rejectConfigurationArtifactProposal).toHaveBeenCalledWith(
      '10', 'session-1', 'artifact-proposal-page', 7,
    )
    expect(rejected.get('.config-artifact-proposal').text()).toContain('已拒绝')
    expect(rejected.find('.config-artifact-proposal-result').exists()).toBe(false)
  })

  it('renders artifact loading, redacted empty, clarification and expiry states', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [
        { ...answer, id: 'turn-artifact-loading', tool: null, artifactProposal: { ...selectionArtifactProposal, id: 'artifact-loading', state: 'EXECUTING' } },
        { ...answer, id: 'turn-artifact-empty', tool: null, artifactProposal: {
          ...pageArtifactProposal, id: 'artifact-empty', preview: {
            ...pageArtifactProposal.preview!, pageLayout: {
              ...pageArtifactProposal.preview!.pageLayout!, layout: {
                ...pageArtifactProposal.preview!.pageLayout!.layout, sections: [], redacted: true,
              },
            },
          },
        } },
        { ...answer, id: 'turn-artifact-clarification', tool: null, artifactProposal: {
          ...selectionArtifactProposal, id: 'artifact-clarification', state: 'CLARIFICATION_REQUIRED',
          moduleCode: null, preview: null, confidence: 0.4, clarification: '请补充字段类型或页面编码。',
        } },
        { ...answer, id: 'turn-artifact-expired', tool: null, artifactProposal: {
          ...selectionArtifactProposal, id: 'artifact-expired', expiresAt: '2000-01-01T00:00:00Z',
        } },
      ],
    })
    const wrapper = render()
    await flushPromises()
    expect(wrapper.get('.config-artifact-proposal-loading').text()).toContain('正在重新检查当前权限与草稿修订')
    expect(wrapper.get('.config-artifact-empty').text()).toContain('持久回读已脱敏分区明细')
    expect(wrapper.get('.config-artifact-proposal-clarification').text()).toContain('请补充字段类型或页面编码')
    const expired = wrapper.findAll('.config-artifact-proposal').find(card => card.classes().includes('expired'))
    expect(expired?.text()).toContain('已过期')
    expect(expired?.find('.config-artifact-proposal-confirm').exists()).toBe(false)
  })

  it('explains artifact permission, stale revision and confirmation conflicts without success', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [
        { ...answer, id: 'turn-artifact-permission', tool: null, artifactProposal: { ...selectionArtifactProposal, id: 'artifact-permission', state: 'FAILED', errorCode: 'CONFIG_ARTIFACT_PERMISSION_DENIED' } },
        { ...answer, id: 'turn-artifact-stale', tool: null, artifactProposal: { ...selectionArtifactProposal, id: 'artifact-stale', state: 'FAILED', errorCode: 'CONFIG_DRAFT_REVISION_STALE' } },
        { ...answer, id: 'turn-artifact-conflict', tool: null, artifactProposal: { ...pageArtifactProposal, id: 'artifact-conflict', state: 'FAILED', errorCode: 'ARTIFACT_PROPOSAL_REVISION_CONFLICT' } },
      ],
    })
    const wrapper = render()
    await flushPromises()
    const errors = wrapper.findAll('.config-artifact-proposal-error').map(item => item.text()).join(' ')
    expect(errors).toContain('当前配置权限或成员上下文已变化')
    expect(errors).toContain('配置草稿版本已变化')
    expect(errors).toContain('提案版本或确认请求冲突')
    expect(wrapper.find('.config-artifact-proposal-result').exists()).toBe(false)
  })

  it('reuses the exact artifact confirmation idempotency key after a transient retry', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{ ...answer, id: 'turn-artifact-selection', tool: null, artifactProposal: selectionArtifactProposal }],
    })
    vi.mocked(aiRuntimeApi.confirmConfigurationArtifactProposal)
      .mockRejectedValueOnce(new Error('CONFIG_ARTIFACT_OWNER_UNAVAILABLE'))
      .mockResolvedValueOnce({ ...selectionArtifactProposal, state: 'REJECTED', revision: 6 })
    vi.mocked(aiRuntimeApi.configurationArtifactProposal).mockResolvedValue(selectionArtifactProposal)
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.config-artifact-proposal-confirm').trigger('click')
    await flushPromises()
    await wrapper.get('.config-artifact-proposal-confirm').trigger('click')
    await flushPromises()
    expect(aiRuntimeApi.confirmConfigurationArtifactProposal).toHaveBeenCalledTimes(2)
    expect(vi.mocked(aiRuntimeApi.confirmConfigurationArtifactProposal).mock.calls[0]![4])
      .toBe(vi.mocked(aiRuntimeApi.confirmConfigurationArtifactProposal).mock.calls[1]![4])
  })

  it('renders typed task and daily report draft previews without creating work', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [
        { ...answer, id: 'turn-work-task', tool: null, workProposal: taskWorkProposal },
        { ...answer, id: 'turn-work-report', tool: null, workProposal: reportWorkProposal },
      ],
    })
    const wrapper = render()
    await flushPromises()

    const cards = wrapper.findAll('.work-proposal')
    expect(cards).toHaveLength(2)
    expect(cards[0]!.text()).toContain('工作任务草稿提案')
    expect(cards[0]!.text()).toContain('完成订单对账')
    expect(cards[0]!.text()).toContain('member-7')
    expect(cards[0]!.text()).toContain('project-3')
    expect(cards[0]!.text()).toContain('96%')
    expect(cards[0]!.text()).toContain('确认前零写入')
    expect(cards[1]!.text()).toContain('个人日报草稿提案')
    expect(cards[1]!.text()).toContain('2026-08-04')
    expect(cards[1]!.text()).toContain('完成订单字段梳理')
    expect(cards[1]!.text()).toContain('DRAFT · 未提交')
    expect(aiRuntimeApi.confirmWorkProposal).not.toHaveBeenCalled()
    expect(aiRuntimeApi.rejectWorkProposal).not.toHaveBeenCalled()
  })

  it('confirms task and report drafts and renders owner readback without completion or submission actions', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{ ...answer, id: 'turn-work-task', tool: null, workProposal: taskWorkProposal }],
    })
    const task = render()
    await flushPromises()
    await task.get('.work-proposal-confirm').trigger('click')
    await flushPromises()
    expect(aiRuntimeApi.confirmWorkProposal).toHaveBeenCalledWith(
      '10', 'session-1', 'work-proposal-task', 2, expect.any(String),
    )
    const taskResult = task.get('.work-proposal-result')
    expect(taskResult.text()).toContain('Work owner 真实回读')
    expect(taskResult.text()).toContain('task-101')
    expect(taskResult.text()).toContain('OPEN · 未完成')
    expect(task.find('.work-proposal-confirm').exists()).toBe(false)
    expect(task.findAll('button').some(button => button.text() === '完成任务')).toBe(false)

    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{ ...answer, id: 'turn-work-report', tool: null, workProposal: reportWorkProposal }],
    })
    vi.mocked(aiRuntimeApi.confirmWorkProposal).mockResolvedValueOnce(reportWorkSuccess)
    const report = render()
    await flushPromises()
    await report.get('.work-proposal-confirm').trigger('click')
    await flushPromises()
    expect(aiRuntimeApi.confirmWorkProposal).toHaveBeenLastCalledWith(
      '10', 'session-1', 'work-proposal-report', 4, expect.any(String),
    )
    const reportResult = report.get('.work-proposal-result')
    expect(reportResult.text()).toContain('report-20260804')
    expect(reportResult.text()).toContain('DRAFT · 未提交')
    expect(reportResult.text()).toContain('member-7')
    expect(report.findAll('button').some(button => button.text() === '提交日报')).toBe(false)
  })

  it('renders clarification, loading, expired, stale and permission-denied work proposal states', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [
        { ...answer, id: 'turn-work-clarification', tool: null, workProposal: { ...taskWorkProposal, id: 'work-clarification', state: 'CLARIFICATION_REQUIRED', preview: null, confidence: 0.4, clarification: '[clarification:redacted]' } },
        { ...answer, id: 'turn-work-loading', tool: null, workProposal: { ...taskWorkProposal, id: 'work-loading', state: 'EXECUTING' } },
        { ...answer, id: 'turn-work-expired', tool: null, workProposal: { ...taskWorkProposal, id: 'work-expired', expiresAt: '2000-01-01T00:00:00Z' } },
        { ...answer, id: 'turn-work-stale', tool: null, workProposal: { ...reportWorkProposal, id: 'work-stale', state: 'STALE', errorCode: 'WORK_REPORT_EXISTING' } },
        { ...answer, id: 'turn-work-permission', tool: null, workProposal: { ...taskWorkProposal, id: 'work-permission', state: 'PERMISSION_DENIED', errorCode: 'WORK_TASK_PERMISSION_DENIED' } },
      ],
    })
    const wrapper = render()
    await flushPromises()
    expect(wrapper.get('.work-proposal-clarification').text()).toContain('[clarification:redacted]')
    expect(wrapper.get('.work-proposal-loading').text()).toContain('正在重新检查权限、业务约束与提案修订')
    expect(wrapper.findAll('.work-proposal').some(card => card.classes().includes('expired') && card.text().includes('已过期'))).toBe(true)
    expect(wrapper.findAll('.work-proposal').some(card => card.classes().includes('stale') && card.text().includes('业务数据已变化'))).toBe(true)
    expect(wrapper.findAll('.work-proposal').some(card => card.classes().includes('permission_denied') && card.text().includes('权限已失效'))).toBe(true)
    const errors = wrapper.findAll('.work-proposal-error').map(item => item.text()).join(' ')
    expect(errors).toContain('同日工作数据已变化')
    expect(errors).toContain('当前工作权限或成员上下文已变化')
    expect(wrapper.findAll('.work-proposal-confirm')).toHaveLength(0)
  })

  it('rejects a work proposal and reuses the exact confirmation idempotency key after a transient retry', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{ ...answer, id: 'turn-work-task', tool: null, workProposal: taskWorkProposal }],
    })
    const rejected = render()
    await flushPromises()
    await rejected.get('.work-proposal-reject').trigger('click')
    await flushPromises()
    expect(aiRuntimeApi.rejectWorkProposal).toHaveBeenCalledWith('10', 'session-1', 'work-proposal-task', 2)
    expect(rejected.get('.work-proposal').text()).toContain('已拒绝')
    expect(rejected.find('.work-proposal-result').exists()).toBe(false)

    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{ ...answer, id: 'turn-work-task', tool: null, workProposal: taskWorkProposal }],
    })
    vi.mocked(aiRuntimeApi.confirmWorkProposal)
      .mockRejectedValueOnce(new Error('WORK_OWNER_UNAVAILABLE'))
      .mockResolvedValueOnce(taskWorkSuccess)
    vi.mocked(aiRuntimeApi.workProposal).mockResolvedValue(taskWorkProposal)
    const retried = render()
    await flushPromises()
    await retried.get('.work-proposal-confirm').trigger('click')
    await flushPromises()
    await retried.get('.work-proposal-confirm').trigger('click')
    await flushPromises()
    expect(aiRuntimeApi.confirmWorkProposal).toHaveBeenCalledTimes(2)
    expect(vi.mocked(aiRuntimeApi.confirmWorkProposal).mock.calls[0]![4])
      .toBe(vi.mocked(aiRuntimeApi.confirmWorkProposal).mock.calls[1]![4])
  })

  it('renders typed Flow, report and disabled print-template previews with zero pre-confirmation writes', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [
        { ...answer, id: 'turn-generated-flow', tool: null, generatedDraftProposal: flowGeneratedDraftProposal },
        { ...answer, id: 'turn-generated-report', tool: null, generatedDraftProposal: reportGeneratedDraftProposal },
        { ...answer, id: 'turn-generated-print', tool: null, generatedDraftProposal: printGeneratedDraftProposal },
      ],
    })
    const wrapper = render()
    await flushPromises()

    expect(wrapper.findAll('.generated-draft-proposal')).toHaveLength(3)
    const flow = wrapper.get('.generated-flow-preview')
    expect(flow.text()).toContain('订单审批流')
    expect(flow.text()).toContain('member-2 → member-5')
    const report = wrapper.get('.generated-report-preview')
    expect(report.text()).toContain('weekly_orders')
    expect(report.text()).toContain('data-source-8')
    expect(report.text()).toContain('recordNo · amount')
    const print = wrapper.get('.generated-print-preview')
    expect(print.text()).toContain('orders')
    expect(print.text()).toContain('A4 · PORTRAIT')
    expect(print.text()).toContain('订单回执单')
    expect(wrapper.text()).toContain('确认前零 Flow 写入')
    expect(wrapper.text()).toContain('确认前零报表写入')
    expect(wrapper.text()).toContain('确认前零打印写入')
    expect(aiRuntimeApi.confirmGeneratedDraftProposal).not.toHaveBeenCalled()
    expect(aiRuntimeApi.rejectGeneratedDraftProposal).not.toHaveBeenCalled()
  })

  it('confirms all generated-draft kinds and renders unpublished owner readbacks', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{ ...answer, id: 'turn-generated-flow', tool: null, generatedDraftProposal: flowGeneratedDraftProposal }],
    })
    const flow = render()
    await flushPromises()
    await flow.get('.generated-draft-confirm').trigger('click')
    await flushPromises()
    expect(aiRuntimeApi.confirmGeneratedDraftProposal).toHaveBeenCalledWith(
      '10', 'session-1', 'generated-flow', 2, expect.any(String),
    )
    expect(flow.get('.generated-draft-result').text()).toContain('flow-definition-11')
    expect(flow.get('.generated-draft-result').text()).toContain('DRAFT · 未发布/未激活')
    expect(flow.findAll('button').some(button => button.text().includes('发布'))).toBe(false)

    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{ ...answer, id: 'turn-generated-report', tool: null, generatedDraftProposal: reportGeneratedDraftProposal }],
    })
    vi.mocked(aiRuntimeApi.confirmGeneratedDraftProposal).mockResolvedValueOnce(reportGeneratedDraftSuccess)
    const report = render()
    await flushPromises()
    await report.get('.generated-draft-confirm').trigger('click')
    await flushPromises()
    expect(report.get('.generated-draft-result').text()).toContain('report-11')
    expect(report.get('.generated-draft-result').text()).toContain('DRAFT · 未发布')
    expect(report.get('.generated-draft-result').text()).toContain('草稿版本1')

    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{ ...answer, id: 'turn-generated-print', tool: null, generatedDraftProposal: printGeneratedDraftProposal }],
    })
    vi.mocked(aiRuntimeApi.confirmGeneratedDraftProposal).mockResolvedValueOnce(printGeneratedDraftSuccess)
    const print = render()
    await flushPromises()
    await print.get('.generated-draft-confirm').trigger('click')
    await flushPromises()
    expect(print.get('.generated-draft-result').text()).toContain('print-template-11')
    expect(print.get('.generated-draft-result').text()).toContain('DISABLED · 未发布')
    expect(print.findAll('button').some(button => /预览|PDF|打印/u.test(button.text()))).toBe(false)
  })

  it('shows clarification, loading, expired, stale and permission-denied generated-draft states', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [
        { ...answer, id: 'turn-generated-clarification', tool: null, generatedDraftProposal: { ...flowGeneratedDraftProposal, id: 'generated-clarification', state: 'CLARIFICATION_REQUIRED', preview: null, confidence: 0.35, clarification: '[clarification:redacted]' } },
        { ...answer, id: 'turn-generated-loading', tool: null, generatedDraftProposal: { ...reportGeneratedDraftProposal, id: 'generated-loading', state: 'EXECUTING' } },
        { ...answer, id: 'turn-generated-expired', tool: null, generatedDraftProposal: { ...printGeneratedDraftProposal, id: 'generated-expired', expiresAt: '2000-01-01T00:00:00Z' } },
        { ...answer, id: 'turn-generated-stale', tool: null, generatedDraftProposal: { ...reportGeneratedDraftProposal, id: 'generated-stale', state: 'STALE', errorCode: 'REPORT_CODE_ALREADY_USED' } },
        { ...answer, id: 'turn-generated-permission', tool: null, generatedDraftProposal: { ...flowGeneratedDraftProposal, id: 'generated-permission', state: 'PERMISSION_DENIED', errorCode: 'FLOW_PERMISSION_DENIED' } },
      ],
    })
    const wrapper = render()
    await flushPromises()
    expect(wrapper.get('.generated-draft-clarification').text()).toContain('[clarification:redacted]')
    expect(wrapper.get('.generated-draft-loading').text()).toContain('正在重新检查当前权限、成员与配置依赖')
    expect(wrapper.findAll('.generated-draft-proposal').some(card => card.classes().includes('expired') && card.text().includes('已过期'))).toBe(true)
    expect(wrapper.findAll('.generated-draft-proposal').some(card => card.classes().includes('stale') && card.text().includes('依赖数据已变化'))).toBe(true)
    expect(wrapper.findAll('.generated-draft-proposal').some(card => card.classes().includes('permission_denied') && card.text().includes('权限已失效'))).toBe(true)
    const errors = wrapper.findAll('.generated-draft-error').map(item => item.text()).join(' ')
    expect(errors).toContain('成员、模块、数据源、字段或编码已变化')
    expect(errors).toContain('当前权限或成员上下文已变化')
    expect(wrapper.findAll('.generated-draft-confirm')).toHaveLength(0)
  })

  it('rejects a generated draft and preserves one idempotency key across an exact retry', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{ ...answer, id: 'turn-generated-flow', tool: null, generatedDraftProposal: flowGeneratedDraftProposal }],
    })
    const rejected = render()
    await flushPromises()
    await rejected.get('.generated-draft-reject').trigger('click')
    await flushPromises()
    expect(aiRuntimeApi.rejectGeneratedDraftProposal).toHaveBeenCalledWith('10', 'session-1', 'generated-flow', 2)
    expect(rejected.get('.generated-draft-proposal').text()).toContain('已拒绝')
    expect(rejected.find('.generated-draft-result').exists()).toBe(false)

    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{ ...answer, id: 'turn-generated-flow', tool: null, generatedDraftProposal: flowGeneratedDraftProposal }],
    })
    vi.mocked(aiRuntimeApi.confirmGeneratedDraftProposal)
      .mockRejectedValueOnce(new Error('FLOW_OWNER_UNAVAILABLE'))
      .mockResolvedValueOnce(flowGeneratedDraftSuccess)
    vi.mocked(aiRuntimeApi.generatedDraftProposal).mockResolvedValue(flowGeneratedDraftProposal)
    const retried = render()
    await flushPromises()
    await retried.get('.generated-draft-confirm').trigger('click')
    await flushPromises()
    await retried.get('.generated-draft-confirm').trigger('click')
    await flushPromises()
    expect(aiRuntimeApi.confirmGeneratedDraftProposal).toHaveBeenCalledTimes(2)
    expect(vi.mocked(aiRuntimeApi.confirmGeneratedDraftProposal).mock.calls[0]![4])
      .toBe(vi.mocked(aiRuntimeApi.confirmGeneratedDraftProposal).mock.calls[1]![4])
  })

  it('renders a safe structured write proposal and confirms it exactly once with owner readback', async () => {
    let resolveConfirmation!: (value: AiConfirmation) => void
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{ ...answer, id: 'turn-write', answer: '请确认更新提案。', confirmation: proposal }],
    })
    vi.mocked(aiRuntimeApi.confirm).mockReturnValue(new Promise(resolve => { resolveConfirmation = resolve }))
    const wrapper = render()
    await flushPromises()

    const card = wrapper.get('.agent-confirmation')
    expect(card.text()).toContain('RECORD_UPDATE · orders')
    expect(card.text()).toContain('record-1')
    expect(card.text()).toContain('100.00')
    expect(card.text()).toContain('125.00')
    expect(card.text()).toContain('96%')
    expect(card.text()).toContain('等待确认')

    await wrapper.get('.agent-confirmation-confirm').trigger('click')
    await wrapper.get('.agent-confirmation-confirm').trigger('click')
    expect(aiRuntimeApi.confirm).toHaveBeenCalledTimes(1)
    expect(aiRuntimeApi.confirm).toHaveBeenCalledWith(
      '10', 'confirmation-1', 7, expect.any(String),
    )
    expect(wrapper.get('.agent-confirmation-confirm').attributes('disabled')).toBeDefined()

    resolveConfirmation({
      ...proposal, state: 'SUCCEEDED', version: 8,
      result: {
        recordId: 'record-1', recordNo: 'SO-1', recordVersion: 5, status: 'ACTIVE',
        title: '订单 SO-1', schemaVersionId: '12', values: { amount: '125.00' },
        executedAt: '2026-08-04T00:05:00Z',
      },
    })
    await flushPromises()
    expect(wrapper.get('.agent-confirmation').text()).toContain('已执行')
    expect(wrapper.get('.agent-confirmation-result').text()).toContain('真实回读')
    expect(wrapper.get('.agent-confirmation-result').text()).toContain('SO-1')
    expect(wrapper.get('.agent-confirmation-result').text()).toContain('记录版本5')
    expect(wrapper.find('.agent-confirmation-confirm').exists()).toBe(false)
  })

  it('rejects a pending proposal and explains permission/version failure without claiming success', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{ ...answer, id: 'turn-write', answer: '请确认更新提案。', confirmation: proposal }],
    })
    const rejected = render()
    await flushPromises()
    await rejected.get('.agent-confirmation-reject').trigger('click')
    await flushPromises()
    expect(aiRuntimeApi.reject).toHaveBeenCalledWith('10', 'confirmation-1', 7)
    expect(rejected.get('.agent-confirmation').text()).toContain('已拒绝')
    expect(rejected.find('.agent-confirmation-result').exists()).toBe(false)

    vi.clearAllMocks()
    vi.mocked(aiRuntimeApi.capability).mockResolvedValue({ available: true, reason: null, policyVersion: 'policy-4' })
    vi.mocked(aiRuntimeApi.sessions).mockResolvedValue({ rows: [session], page: 1, size: 50, hasMore: false })
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{ ...answer, id: 'turn-write', answer: '请确认更新提案。', confirmation: proposal }],
    })
    vi.mocked(aiRuntimeApi.confirmation)
      .mockResolvedValueOnce({ ...proposal, state: 'FAILED', version: 8, errorCode: 'RECORD_VERSION_CONFLICT' })
    vi.mocked(aiRuntimeApi.confirm).mockRejectedValue(new Error('RECORD_VERSION_CONFLICT'))
    const conflicted = render()
    await flushPromises()
    await conflicted.get('.agent-confirmation-confirm').trigger('click')
    await flushPromises()
    expect(conflicted.get('.agent-confirmation-error').text()).toContain('版本已变化')
    expect(conflicted.get('.agent-confirmation').text()).toContain('执行失败')
    expect(conflicted.find('.agent-confirmation-result').exists()).toBe(false)
  })

  it('reuses the same confirmation idempotency key for an exact transient retry', async () => {
    vi.mocked(aiRuntimeApi.session).mockResolvedValue({
      session, messages: [], turns: [{ ...answer, id: 'turn-write', answer: '请确认更新提案。', confirmation: proposal }],
    })
    vi.mocked(aiRuntimeApi.confirm)
      .mockRejectedValueOnce(new Error('OWNER_UNAVAILABLE'))
      .mockResolvedValueOnce({ ...proposal, state: 'REJECTED', version: 8 })
    vi.mocked(aiRuntimeApi.confirmation).mockResolvedValue(proposal)
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.agent-confirmation-confirm').trigger('click')
    await flushPromises()
    await wrapper.get('.agent-confirmation-confirm').trigger('click')
    await flushPromises()

    expect(aiRuntimeApi.confirm).toHaveBeenCalledTimes(2)
    const firstKey = vi.mocked(aiRuntimeApi.confirm).mock.calls[0]![3]
    const secondKey = vi.mocked(aiRuntimeApi.confirm).mock.calls[1]![3]
    expect(firstKey).toBeTruthy()
    expect(secondKey).toBe(firstKey)
  })
})
