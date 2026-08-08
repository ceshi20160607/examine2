import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { flowApi } from '@/services/flow'
import { fileApi } from '@/services/file'
import { useSessionStore } from '@/stores/session'
import type {
  ApprovalTaskPage,
  FlowCommentPage,
  FlowCopyPage,
  FlowDefinitionDraft,
  FlowDefinitionVersion,
  FlowInstance,
  FlowInstancePage,
  FlowStartableDefinitionPage,
  FlowUrgePage,
} from '@/types/flow'
import FlowView from '@/views/system/FlowView.vue'

const route = vi.hoisted(() => ({
  params: { systemId: '10' },
  query: {} as Record<string, string>,
  fullPath: '/systems/10/flows',
}))

vi.mock('vue-router', () => ({
  useRoute: () => route,
}))

vi.mock('@/services/flow', () => ({
  flowApi: {
    listDefinitions: vi.fn(),
    createDefinition: vi.fn(),
    reviseDefinition: vi.fn(),
    publishDefinition: vi.fn(),
    checkDefinitionDraft: vi.fn(),
    simulateDefinitionDraft: vi.fn(),
    listDefinitionVersions: vi.fn(),
    restoreDefinitionVersion: vi.fn(),
    periodicSchedule: vi.fn(),
    listStartableDefinitions: vi.fn(),
    startInstance: vi.fn(),
    listInstances: vi.fn(),
    listApprovalTasks: vi.fn(),
    listClaimableTasks: vi.fn(),
    listExternalTasks: vi.fn(),
    claimExternalTask: vi.fn(),
    heartbeatExternalTask: vi.fn(),
    completeExternalTask: vi.fn(),
    failExternalTask: vi.fn(),
    listDelegations: vi.fn(),
    createDelegation: vi.fn(),
    revokeDelegation: vi.fn(),
    instance: vi.fn(),
    approve: vi.fn(),
    reject: vi.fn(),
    approveBranch: vi.fn(),
    rejectBranch: vi.fn(),
    withdraw: vi.fn(),
    terminate: vi.fn(),
    listUrges: vi.fn(),
    urge: vi.fn(),
    listComments: vi.fn(),
    createComment: vi.fn(),
    transfer: vi.fn(),
    addSign: vi.fn(),
    reduceSign: vi.fn(),
    listCopies: vi.fn(),
    copy: vi.fn(),
    returnInstance: vi.fn(),
    cancelClaim: vi.fn(),
    claim: vi.fn(),
    history: vi.fn(),
    retryCompletionExecution: vi.fn(),
    retryCompensationExecution: vi.fn(),
    listDecisionCommentTemplates: vi.fn(),
    createDecisionCommentTemplate: vi.fn(),
    updateDecisionCommentTemplate: vi.fn(),
    activateDecisionCommentTemplate: vi.fn(),
    deactivateDecisionCommentTemplate: vi.fn(),
  },
}))

vi.mock('@/services/file', () => ({
  fileApi: {
    get: vi.fn(),
  },
}))

const pendingTask: FlowInstance = {
  instanceId: '101',
  definitionId: '201',
  definitionVersion: 1,
  businessKey: 'contract:301',
  requesterId: '100',
  approverId: '200',
  approverIds: ['150', '200', '300', '400'],
  currentStepIndex: 1,
  claimState: 'CLAIMED',
  recordBinding: null,
  status: 'PENDING',
  startedAt: '2026-07-27T01:00:00Z',
  completedAt: null,
}

const triggerBinding = {
  moduleCode: 'purchase_order',
  event: 'RECORD_ACTIVATED' as const,
  priority: 200,
  exclusive: false,
  conditions: [
    { fieldCode: 'amount', operator: 'GTE' as const, value: 100 },
    { fieldCode: 'urgent', operator: 'EQ' as const, value: true },
    { fieldCode: 'remark', operator: 'NOT_EMPTY' as const },
  ],
}

const recordStatusMapping = {
  fieldCode: 'approval_status',
  approvedValue: '101',
  rejectedValue: '102',
  withdrawnValue: '103',
  terminatedValue: '104',
}

const boundDefinition: FlowDefinitionDraft = {
  definitionId: '201',
  name: 'Purchase approval',
  approverId: '200',
  approverIds: ['200', '300'],
  triggerBinding,
  recordStatusMapping,
  revision: 2,
  updatedAt: '2026-07-27T02:00:00Z',
}

const boundPublishedVersion: FlowDefinitionVersion = {
  definitionId: '201',
  version: 3,
  name: 'Purchase approval',
  approverId: '200',
  approverIds: ['200', '300'],
  triggerBinding,
  recordStatusMapping,
  sourceRevision: 2,
  publishedAt: '2026-07-27T02:01:00Z',
}

function taskPage(
  items: FlowInstance[],
  page = 1,
  total = items.length,
): ApprovalTaskPage {
  return { items, page, size: 20, total }
}

function instancePage(items: FlowInstance[] = []): FlowInstancePage {
  return { items, page: 1, size: 20, total: items.length }
}

function startableDefinitionPage(
  items: FlowStartableDefinitionPage['items'] = [{
    definitionId: '201',
    name: 'Purchase approval',
    latestVersion: 3,
    publishedAt: '2026-07-27T02:01:00Z',
  }],
): FlowStartableDefinitionPage {
  return { items, page: 1, size: 100, total: items.length }
}

function urgePage(items: FlowUrgePage['items'] = [], page = 1, total = items.length): FlowUrgePage {
  return { items, page, size: 20, total }
}

function commentPage(
  items: FlowCommentPage['items'] = [],
  page = 1,
  total = items.length,
): FlowCommentPage {
  return { items, page, size: 20, total }
}

function copyPage(
  items: FlowCopyPage['items'] = [],
  page = 1,
  total = items.length,
): FlowCopyPage {
  return { items, page, size: 20, total }
}

function applySession(
  tenantId: string,
  memberId = '200',
  permissions = ['flow.instance.read', 'flow.instance.decide'],
) {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'approver', displayName: 'Approver' },
    context: {
      type: 'SYSTEM',
      account: { id: '1', username: 'approver', displayName: 'Approver' },
      systemId: '10',
      tenantId,
      memberId,
      permissionVersion: '1',
      permissions,
      shells: ['SYSTEM_RUNTIME'],
    },
    systems: [],
  })
}

function render() {
  return mount(FlowView, {
    global: {
      stubs: {
        FlowDraftCanvas: {
          props: [
            'modelValue',
            'approvalMode',
            'approvalStages',
            'approverSource',
            'quorumRule',
            'deadlinePolicy',
            'decisionCommentPolicy',
            'decisionEvidencePolicy',
            'gateway',
            'parallelGateway',
            'inclusiveGateway',
          ],
          emits: [
            'update:modelValue',
            'update:approvalMode',
            'update:approvalStages',
            'update:approverSource',
            'update:quorumRule',
            'update:deadlinePolicy',
            'update:decisionCommentPolicy',
            'update:decisionEvidencePolicy',
            'update:gateway',
            'update:parallelGateway',
            'update:inclusiveGateway',
            'saveCheck',
            'saveSimulate',
          ],
          template: `
            <div class="flow-draft-canvas-stub">
              <span class="graph-mode-value">{{ approvalMode }}</span>
              <span class="graph-stages-value">
                {{ approvalStages ? approvalStages.map(stage => stage.code).join(',') : '' }}
              </span>
              <span class="graph-branch-stages-value">
                {{ parallelGateway
                  ? parallelGateway.branches
                    .map(branch => (branch.approvalStages || []).map(stage => stage.code).join(','))
                    .join('|')
                  : ''
                }}
              </span>
              <span class="graph-source-value">
                {{ approverSource.kind }}:{{ approverSource.sourceId ?? '' }}{{
                  approverSource.moduleCode ? ':' + approverSource.moduleCode : ''
                }}
              </span>
              <span class="graph-quorum-value">
                {{ quorumRule ? quorumRule.type + ':' + quorumRule.value : '' }}
              </span>
              <span class="graph-deadline-value">
                {{ deadlinePolicy
                  ? deadlinePolicy.timeoutMinutes + ':' + deadlinePolicy.timeoutAction
                  : ''
                }}
              </span>
              <span class="graph-decision-comment-value">
                {{ decisionCommentPolicy
                  ? decisionCommentPolicy.approveRequired + ':' +
                    decisionCommentPolicy.rejectRequired + ':' +
                    decisionCommentPolicy.minimumLength
                  : ''
                }}
              </span>
              <span class="graph-decision-evidence-value">
                {{ decisionEvidencePolicy
                  ? decisionEvidencePolicy.minimumAttachments + ':' +
                    decisionEvidencePolicy.maximumAttachments + ':' +
                    decisionEvidencePolicy.signatureMode + ':' +
                    decisionEvidencePolicy.allowedMimeFamilies.join(',')
                  : ''
                }}
              </span>
              <button
                class="graph-decision-evidence-edit"
                @click="$emit('update:decisionEvidencePolicy', {
                  minimumAttachments: 1,
                  maximumAttachments: 3,
                  allowedMimeFamilies: ['IMAGE', 'PDF'],
                  signatureMode: 'REQUIRED',
                })"
              >
                edit evidence
              </button>
              <button
                class="graph-route-edit"
                @click="$emit('update:modelValue', ['200', '900', '300'])"
              >
                edit graph
              </button>
              <button class="graph-mode-edit" @click="$emit('update:approvalMode', 'ANY')">
                edit mode
              </button>
              <button
                class="graph-quorum-mode-edit"
                @click="
                  $emit('update:approvalMode', 'QUORUM');
                  $emit('update:quorumRule', { type: 'COUNT', value: 2 })
                "
              >
                edit quorum
              </button>
              <button
                class="graph-deadline-edit"
                @click="$emit('update:deadlinePolicy', {
                  timeoutMinutes: 120,
                  remindBeforeMinutes: 30,
                  timeoutAction: 'AUTO_REJECT',
                })"
              >
                edit deadline
              </button>
              <button
                class="graph-decision-comment-edit"
                @click="$emit('update:decisionCommentPolicy', {
                  approveRequired: true,
                  rejectRequired: true,
                  minimumLength: 8,
                })"
              >
                edit decision comment
              </button>
              <button
                class="graph-role-source-edit"
                @click="$emit('update:approverSource', { kind: 'ROLE', sourceId: '77' })"
              >
                edit role source
              </button>
              <button
                class="graph-department-leader-source-edit"
                @click="$emit('update:approverSource', {
                  kind: 'DEPARTMENT_LEADER',
                  sourceId: '77',
                })"
              >
                edit department leader source
              </button>
              <button
                class="graph-requester-manager-source-edit"
                @click="$emit('update:approverSource', {
                  kind: 'REQUESTER_MANAGER',
                  sourceId: null,
                })"
              >
                edit requester manager source
              </button>
              <button
                class="graph-record-member-source-edit"
                @click="$emit('update:approverSource', {
                  kind: 'RECORD_MEMBER_FIELD',
                  sourceId: '501',
                  moduleCode: 'purchase_order',
                })"
              >
                edit record member source
              </button>
              <button
                class="graph-stages-edit"
                @click="
                  $emit('update:modelValue', ['200']);
                  $emit('update:approvalStages', [
                    {
                      code: 'review',
                      name: 'Review',
                      approverIds: ['200'],
                      approvalMode: 'SEQUENTIAL',
                      approverSource: { kind: 'FIXED', sourceId: null },
                    },
                    {
                      code: 'confirm',
                      name: 'Confirm',
                      approverIds: [],
                      approvalMode: 'ANY',
                      approverSource: { kind: 'PREVIOUS_HANDLER' },
                    },
                  ])
                "
              >
                edit stages
              </button>
              <button
                class="graph-gateway-edit"
                @click="$emit('update:gateway', {
                  branches: [
                    {
                      code: 'urgent',
                      name: 'Urgent',
                      defaultBranch: false,
                      conditions: [
                        { fieldCode: 'urgent', operator: 'EQ', valueText: 'true' },
                      ],
                      approverIds: ['900'],
                      approvalMode: 'ANY',
                    },
                    {
                      code: 'default',
                      name: 'Default',
                      defaultBranch: true,
                      conditions: [],
                      approverIds: modelValue,
                      approvalMode,
                    },
                  ],
                })"
              >
                edit gateway
              </button>
              <button
                class="graph-parallel-edit"
                @click="
                  $emit('update:modelValue', ['300']);
                  $emit('update:approvalMode', 'SEQUENTIAL');
                  $emit('update:parallelGateway', {
                    branches: [
                      {
                        code: 'finance',
                        name: 'Finance',
                        approverIds: ['300'],
                        approvalMode: 'SEQUENTIAL',
                      },
                      {
                        code: 'owner',
                        name: 'Owner',
                        approverIds: ['400', '500'],
                        approvalMode: 'ANY',
                      },
                    ],
                  })
                "
              >
                edit parallel
              </button>
              <button
                class="graph-parallel-stages-edit"
                @click="
                  $emit('update:modelValue', ['300']);
                  $emit('update:approvalMode', 'SEQUENTIAL');
                  $emit('update:parallelGateway', {
                    branches: [
                      {
                        code: 'finance',
                        name: 'Finance',
                        approverIds: ['300'],
                        approvalMode: 'SEQUENTIAL',
                        approverSource: { kind: 'FIXED', sourceId: null },
                        approvalStages: [
                          {
                            code: 'finance_review',
                            name: 'Finance review',
                            approverIds: ['300'],
                            approvalMode: 'SEQUENTIAL',
                            approverSource: { kind: 'FIXED', sourceId: null },
                          },
                          {
                            code: 'finance_confirm',
                            name: 'Finance confirm',
                            approverIds: [],
                            approvalMode: 'ANY',
                            approverSource: { kind: 'REQUESTER_MANAGER' },
                          },
                        ],
                      },
                      {
                        code: 'owner',
                        name: 'Owner',
                        approverIds: [],
                        approvalMode: 'SEQUENTIAL',
                        approverSource: { kind: 'REQUESTER' },
                        approvalStages: [
                          {
                            code: 'owner_review',
                            name: 'Owner review',
                            approverIds: [],
                            approvalMode: 'SEQUENTIAL',
                            approverSource: { kind: 'REQUESTER' },
                          },
                        ],
                      },
                    ],
                  })
                "
              >
                edit parallel stages
              </button>
              <button
                class="graph-inclusive-edit"
                @click="
                  $emit('update:modelValue', ['300']);
                  $emit('update:approvalMode', 'SEQUENTIAL');
                  $emit('update:inclusiveGateway', {
                    branches: [
                      {
                        code: 'urgent',
                        name: 'Urgent',
                        defaultBranch: false,
                        conditions: [
                          { fieldCode: 'urgent', operator: 'EQ', valueText: 'true' },
                        ],
                        approverIds: ['300'],
                        approvalMode: 'SEQUENTIAL',
                      },
                      {
                        code: 'large',
                        name: 'Large',
                        defaultBranch: false,
                        conditions: [
                          { fieldCode: 'amount', operator: 'GTE', valueText: '1000' },
                        ],
                        approverIds: ['400', '500'],
                        approvalMode: 'ANY',
                      },
                      {
                        code: 'default',
                        name: 'Default',
                        defaultBranch: true,
                        conditions: [],
                        approverIds: ['600'],
                        approvalMode: 'SEQUENTIAL',
                      },
                    ],
                  })
                "
              >
                edit inclusive
              </button>
              <button class="graph-save-check" @click="$emit('saveCheck')">save check</button>
              <button class="graph-save-simulate" @click="$emit('saveSimulate')">
                save simulate
              </button>
            </div>
          `,
        },
        MemberPicker: {
          props: ['value'],
          emits: ['update:value'],
          template: '<button class="select-assignment-member" @click="$emit(\'update:value\', \'300\')">{{ value || \'select member\' }}</button>',
        },
        'a-alert': {
          props: ['message'],
          template: '<div>{{ message }}<slot /></div>',
        },
        'a-spin': { template: '<div><slot /></div>' },
        'a-empty': {
          props: ['description'],
          template: '<div>{{ description }}</div>',
        },
        'a-tag': { template: '<span><slot /></span>' },
        'a-button': {
          props: ['disabled'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
        },
        'a-segmented': {
          props: ['value', 'options'],
          emits: ['change'],
          template: '<button class="task-status-filter" @click="$emit(\'change\', \'COMPLETED\')">{{ value }}</button>',
        },
        'a-pagination': {
          props: ['current', 'total'],
          emits: ['change'],
          template: '<button class="task-page-two" @click="$emit(\'change\', 2)">{{ current }}/{{ total }}</button>',
        },
        'a-modal': {
          props: ['open'],
          emits: ['ok', 'update:open'],
          template: '<div v-if="open" class="modal"><button class="modal-ok" @click="$emit(\'ok\')">ok</button><slot /></div>',
        },
        'a-drawer': {
          props: ['open'],
          emits: ['update:open'],
          template: '<div v-if="open"><slot /></div>',
        },
        'a-form': { template: '<form><slot /></form>' },
        'a-form-item': { template: '<div><slot /></div>' },
        'a-input': {
          props: ['value'],
          emits: ['update:value'],
          template: '<input :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
        },
        'a-textarea': {
          props: ['value'],
          emits: ['update:value'],
          template: '<textarea class="decision-comment" :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
        },
        'a-timeline': { template: '<div><slot /></div>' },
        'a-timeline-item': { template: '<div><slot /></div>' },
      },
    },
  })
}

describe('FlowView approval tasks', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    route.params.systemId = '10'
    route.query = {}
    route.fullPath = '/systems/10/flows'
    applySession('20')
    vi.mocked(flowApi.listDefinitions).mockResolvedValue({
      items: [], page: 1, size: 20, total: 0,
    })
    vi.mocked(flowApi.createDefinition).mockResolvedValue(boundDefinition)
    vi.mocked(flowApi.reviseDefinition).mockResolvedValue(boundDefinition)
    vi.mocked(flowApi.publishDefinition).mockResolvedValue(boundPublishedVersion)
    vi.mocked(flowApi.checkDefinitionDraft).mockResolvedValue({
      definitionId: '201',
      revision: 2,
      verdict: 'READY',
      blockerCount: 0,
      warningCount: 0,
      issues: [],
    })
    vi.mocked(flowApi.simulateDefinitionDraft).mockResolvedValue({
      definitionId: '201',
      revision: 2,
      check: {
        definitionId: '201',
        revision: 2,
        verdict: 'READY',
        blockerCount: 0,
        warningCount: 0,
        issues: [],
      },
      requesterId: '200',
      businessKey: 'simulation:201:r2',
      startable: true,
      reason: 'TRIGGER_MATCHED',
      steps: [
        { index: 0, approverId: '200', initial: true },
        { index: 1, approverId: '300', initial: false },
      ],
      trigger: {
        configured: true,
        event: 'RECORD_ACTIVATED',
        moduleCode: 'purchase_order',
        matched: true,
        reason: 'TRIGGER_MATCHED',
      },
      statusEffects: recordStatusMapping,
      route: {
        configured: false,
        branchCode: null,
        branchName: null,
        defaultBranch: true,
        approvalMode: 'QUORUM',
        requiredApprovals: 2,
        activeApproverIds: ['200', '300'],
        decisionCommentPolicy: {
          approveRequired: true,
          rejectRequired: true,
          minimumLength: 4,
        },
      },
    })
    vi.mocked(flowApi.listDefinitionVersions).mockResolvedValue({
      items: [], page: 1, size: 10, total: 0,
    })
    vi.mocked(flowApi.restoreDefinitionVersion).mockResolvedValue(boundDefinition)
    vi.mocked(flowApi.listStartableDefinitions).mockResolvedValue(startableDefinitionPage())
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage())
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([pendingTask]))
    vi.mocked(flowApi.listClaimableTasks).mockResolvedValue(instancePage())
    vi.mocked(flowApi.listExternalTasks).mockResolvedValue({
      items: [],
      page: 1,
      size: 20,
      total: 0,
    })
    vi.mocked(flowApi.listDelegations).mockResolvedValue({
      items: [],
      page: 1,
      size: 20,
      total: 0,
    })
    vi.mocked(flowApi.createDelegation).mockResolvedValue({
      delegationRuleId: '701',
      delegatorMemberId: '200',
      delegateMemberId: '300',
      startsAt: '2026-07-30T01:00:00Z',
      endsAt: '2026-07-31T01:00:00Z',
      definitionId: null,
      status: 'ACTIVE',
      createdAt: '2026-07-30T00:00:00Z',
      createdByMemberId: '200',
      revokedAt: null,
      revokedByMemberId: null,
    })
    vi.mocked(flowApi.revokeDelegation).mockResolvedValue({
      delegationRuleId: '701',
      delegatorMemberId: '200',
      delegateMemberId: '300',
      startsAt: '2026-07-30T01:00:00Z',
      endsAt: '2026-07-31T01:00:00Z',
      definitionId: null,
      status: 'REVOKED',
      createdAt: '2026-07-30T00:00:00Z',
      createdByMemberId: '200',
      revokedAt: '2026-07-30T02:00:00Z',
      revokedByMemberId: '200',
    })
    vi.mocked(flowApi.startInstance).mockResolvedValue(pendingTask)
    vi.mocked(flowApi.approve).mockResolvedValue({
      ...pendingTask,
      status: 'APPROVED',
      completedAt: '2026-07-27T01:01:00Z',
    })
    vi.mocked(flowApi.reject).mockResolvedValue({
      ...pendingTask,
      status: 'REJECTED',
      completedAt: '2026-07-27T01:01:00Z',
    })
    vi.mocked(flowApi.approveBranch).mockResolvedValue(pendingTask)
    vi.mocked(flowApi.rejectBranch).mockResolvedValue(pendingTask)
    vi.mocked(flowApi.withdraw).mockResolvedValue({
      ...pendingTask,
      status: 'WITHDRAWN',
      completedAt: '2026-07-27T01:01:00Z',
    })
    vi.mocked(flowApi.terminate).mockResolvedValue({
      ...pendingTask,
      status: 'TERMINATED',
      completedAt: '2026-07-27T01:01:00Z',
    })
    vi.mocked(flowApi.listUrges).mockResolvedValue(urgePage())
    vi.mocked(flowApi.urge).mockResolvedValue({
      urgeId: '501',
      instanceId: '101',
      actorId: '100',
      recipientId: '200',
      message: '',
      createdAt: '2026-07-27T01:02:00Z',
    })
    vi.mocked(flowApi.listComments).mockResolvedValue(commentPage())
    vi.mocked(flowApi.createComment).mockResolvedValue({
      commentId: '601',
      instanceId: '101',
      authorId: '300',
      body: 'Documents checked',
      createdAt: '2026-07-27T01:03:00Z',
    })
    vi.mocked(flowApi.transfer).mockResolvedValue({
      ...pendingTask,
      approverId: '300',
      approverIds: ['300'],
    })
    vi.mocked(flowApi.addSign).mockResolvedValue({
      ...pendingTask,
      approverId: '300',
      approverIds: ['300', '200'],
    })
    vi.mocked(flowApi.reduceSign).mockResolvedValue({
      ...pendingTask,
      approverIds: ['150', '200', '400'],
    })
    vi.mocked(flowApi.listCopies).mockResolvedValue(copyPage())
    vi.mocked(flowApi.copy).mockResolvedValue({
      copyId: '701',
      instanceId: '101',
      actorId: '200',
      recipientId: '300',
      message: '',
      createdAt: '2026-07-27T01:04:00Z',
    })
    vi.mocked(flowApi.returnInstance).mockResolvedValue({
      ...pendingTask,
      approverId: '150',
      currentStepIndex: 0,
    })
    vi.mocked(flowApi.cancelClaim).mockResolvedValue({
      ...pendingTask,
      claimState: 'OPEN',
    })
    vi.mocked(flowApi.claim).mockResolvedValue({
      ...pendingTask,
      claimState: 'CLAIMED',
    })
    vi.mocked(flowApi.instance).mockResolvedValue(pendingTask)
    vi.mocked(flowApi.history).mockResolvedValue({
      instanceId: pendingTask.instanceId,
      events: [],
    })
    vi.mocked(flowApi.retryCompletionExecution).mockResolvedValue({
      executionId: '9901',
      instanceId: '101',
      definitionId: '201',
      definitionVersion: 1,
      ordinal: 0,
      code: 'archive',
      name: 'Archive',
      type: 'EXTERNAL_TASK',
      status: 'AVAILABLE',
      externalTask: {
        topic: 'contract.archive',
        leaseSeconds: 60,
        resultJsonLimitBytes: 8192,
      },
      attemptCount: 1,
      maxAttempts: 3,
      availableAt: '2026-07-31T03:00:00Z',
      createdAt: '2026-07-31T01:00:00Z',
      startedAt: '2026-07-31T01:01:00Z',
      completedAt: null,
      attempts: [],
    })
    vi.mocked(flowApi.retryCompensationExecution).mockResolvedValue({
      compensationExecutionId: '9951',
      originalExecutionId: '9901',
      originalOrdinal: 0,
      instanceId: '101',
      definitionId: '201',
      definitionVersion: 1,
      type: 'EXTERNAL_TASK',
      status: 'AVAILABLE',
      externalTask: {
        topic: 'contract.archive.undo',
        leaseSeconds: 60,
        resultJsonLimitBytes: 8192,
      },
      attemptCount: 1,
      maxAttempts: 3,
      availableAt: '2026-08-03T03:00:00Z',
      createdAt: '2026-08-03T01:00:00Z',
      startedAt: '2026-08-03T01:01:00Z',
      completedAt: null,
      attempts: [],
    })
    vi.mocked(flowApi.listDecisionCommentTemplates).mockResolvedValue({
      items: [],
      page: 1,
      size: 20,
      total: 0,
    })
    vi.mocked(flowApi.createDecisionCommentTemplate).mockResolvedValue({
      templateId: '801',
      name: '同意模板',
      version: 1,
      body: '资料已核验，同意通过',
      status: 'ACTIVE',
      createdAt: '2026-07-31T01:00:00Z',
      updatedAt: '2026-07-31T01:00:00Z',
    })
    vi.mocked(flowApi.updateDecisionCommentTemplate).mockResolvedValue({
      templateId: '801',
      name: '同意模板',
      version: 2,
      body: '资料已复核，同意通过',
      status: 'ACTIVE',
      createdAt: '2026-07-31T01:00:00Z',
      updatedAt: '2026-07-31T02:00:00Z',
    })
    vi.mocked(flowApi.activateDecisionCommentTemplate).mockResolvedValue({
      templateId: '801',
      name: '同意模板',
      version: 2,
      body: '资料已复核，同意通过',
      status: 'ACTIVE',
      createdAt: '2026-07-31T01:00:00Z',
      updatedAt: '2026-07-31T02:00:00Z',
    })
    vi.mocked(flowApi.deactivateDecisionCommentTemplate).mockResolvedValue({
      templateId: '801',
      name: '同意模板',
      version: 2,
      body: '资料已复核，同意通过',
      status: 'INACTIVE',
      createdAt: '2026-07-31T01:00:00Z',
      updatedAt: '2026-07-31T02:00:00Z',
    })
    vi.mocked(fileApi.get).mockResolvedValue({
      id: '901',
      originalName: 'evidence.png',
      mediaType: 'image/png',
      size: 1024,
      sha256: 'abc123',
      uploaderMemberId: '200',
      createdAt: '2026-07-31T01:00:00Z',
      version: 1,
      references: [],
    })
  })

  it('initializes supported task and instance drill filters from the operations route', async () => {
    route.query = {
      taskStatus: 'PENDING',
      status: 'REJECTED',
      from: '2026-07-26',
      to: '2026-08-02',
    }
    route.fullPath = '/systems/10/flows?taskStatus=PENDING&status=REJECTED'
    render()
    await flushPromises()

    expect(vi.mocked(flowApi.listApprovalTasks).mock.calls.at(-1)).toEqual(['10', {
      status: 'PENDING', page: 1, size: 20,
    }])
    expect(vi.mocked(flowApi.listInstances).mock.calls.at(-1)).toEqual(['10', {
      status: 'REJECTED',
      from: '2026-07-26',
      to: '2026-08-02',
      page: 1,
      size: 20,
    }])
  })

  it('validates a trigger binding and preserves every definition draft after save failure', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    vi.mocked(flowApi.createDefinition).mockRejectedValue(new Error('trigger conflict'))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Purchase approval')
    await wrapper.get('.select-assignment-member').trigger('click')
    await wrapper.get('.definition-trigger-toggle input').setValue(true)
    const eventSelector = wrapper.get('.definition-trigger-event')
    expect((eventSelector.element as HTMLSelectElement).value).toBe('RECORD_ACTIVATED')
    expect(eventSelector.findAll('option').map((option) => (
      (option.element as HTMLOptionElement).value
    ))).toEqual([
      'RECORD_ACTIVATED',
      'RECORD_CREATED',
      'RECORD_UPDATED',
      'RECORD_DELETED',
      'RECORD_STATUS_CHANGED',
      'IMPORT_COMPLETED',
      'PERIODIC',
    ])
    await eventSelector.setValue('RECORD_UPDATED')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()
    expect(flowApi.createDefinition).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('自动触发需要合法模块代码')

    await wrapper.get('.definition-trigger-module').setValue('purchase_order')
    await wrapper.get('.definition-trigger-priority').setValue('1001')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()
    expect(flowApi.createDefinition).not.toHaveBeenCalled()

    await wrapper.get('.definition-trigger-priority').setValue('200')
    await wrapper.get('.definition-trigger-exclusive input').setValue(false)
    await wrapper.get('.definition-trigger-add').trigger('click')
    await wrapper.get('.definition-trigger-add').trigger('click')
    await wrapper.get('.definition-trigger-add').trigger('click')
    const conditionFields = wrapper.findAll('.definition-trigger-field')
    const conditionOperators = wrapper.findAll('.definition-trigger-operator')
    await conditionFields[0]!.setValue('amount')
    await conditionOperators[0]!.setValue('GTE')
    await conditionFields[1]!.setValue('urgent')
    await conditionOperators[1]!.setValue('EQ')
    await conditionFields[2]!.setValue('remark')
    await conditionOperators[2]!.setValue('NOT_EMPTY')
    await flushPromises()
    const conditionValues = wrapper.findAll('.definition-trigger-value')
    await conditionValues[0]!.setValue('100')
    await conditionValues[1]!.setValue('true')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDefinition).toHaveBeenCalledWith('10', {
      name: 'Purchase approval',
      approverIds: ['300'],
      approvalMode: 'SEQUENTIAL',
      triggerBinding: {
        ...triggerBinding,
        event: 'RECORD_UPDATED',
      },
    })
    expect((wrapper.get('.definition-name').element as HTMLInputElement).value)
      .toBe('Purchase approval')
    expect(wrapper.get('.select-assignment-member').text()).toBe('300')
    expect((wrapper.get('.definition-trigger-toggle input').element as HTMLInputElement).checked)
      .toBe(true)
    expect((wrapper.get('.definition-trigger-module').element as HTMLInputElement).value)
      .toBe('purchase_order')
    expect((wrapper.get('.definition-trigger-event').element as HTMLSelectElement).value)
      .toBe('RECORD_UPDATED')
    expect((wrapper.get('.definition-trigger-priority').element as HTMLInputElement).value)
      .toBe('200')
    expect((wrapper.get('.definition-trigger-exclusive input').element as HTMLInputElement).checked)
      .toBe(false)
    expect(wrapper.findAll('.definition-trigger-condition')).toHaveLength(3)
    expect(wrapper.text()).toContain('条件组合：ALL')
    expect(wrapper.text()).toContain('trigger conflict')
  })

  it('rejects null condition values and caps the ALL editor at ten rows', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Purchase approval')
    await wrapper.get('.select-assignment-member').trigger('click')
    await wrapper.get('.definition-trigger-toggle input').setValue(true)
    await wrapper.get('.definition-trigger-module').setValue('purchase_order')
    await wrapper.get('.definition-trigger-priority').setValue('200')
    for (let index = 0; index < 10; index += 1) {
      await wrapper.get('.definition-trigger-add').trigger('click')
    }
    expect(wrapper.findAll('.definition-trigger-condition')).toHaveLength(10)
    expect(wrapper.find('.definition-trigger-add').exists()).toBe(false)

    const fields = wrapper.findAll('.definition-trigger-field')
    const values = wrapper.findAll('.definition-trigger-value')
    for (let index = 0; index < 10; index += 1) {
      await fields[index]!.setValue(`field_${index}`)
      await values[index]!.setValue(index === 0 ? 'null' : String(index))
    }
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDefinition).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('非 null JSON 值必须合法')
    expect(wrapper.findAll('.definition-trigger-condition')).toHaveLength(10)
  })

  it('creates a fixed periodic trigger without record fanout fields', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Hourly approval')
    await wrapper.get('.select-assignment-member').trigger('click')
    await wrapper.get('.definition-trigger-toggle input').setValue(true)
    await wrapper.get('.definition-trigger-event').setValue('PERIODIC')
    await flushPromises()

    expect(wrapper.find('.definition-trigger-module').exists()).toBe(false)
    expect(wrapper.find('.definition-trigger-priority').exists()).toBe(false)
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()
    expect(flowApi.createDefinition).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('固定周期需要 ISO-8601 开始时间')

    await wrapper.get('.definition-trigger-start-at').setValue('2026-07-30T01:00:00Z')
    await wrapper.get('.definition-trigger-interval').setValue('60')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDefinition).toHaveBeenCalledWith('10', {
      name: 'Hourly approval',
      approverIds: ['300'],
      approvalMode: 'SEQUENTIAL',
      triggerBinding: {
        moduleCode: null,
        event: 'PERIODIC',
        priority: 0,
        exclusive: true,
        conditions: [],
        startAt: '2026-07-30T01:00:00.000Z',
        intervalMinutes: 60,
      },
    })
  })

  it('renders the durable periodic runtime projection returned by the server', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    vi.mocked(flowApi.listDefinitions).mockResolvedValue({
      items: [{
        ...boundDefinition,
        triggerBinding: {
          moduleCode: null,
          event: 'PERIODIC',
          priority: 0,
          exclusive: true,
          conditions: [],
          startAt: '2026-07-30T01:00:00Z',
          intervalMinutes: 60,
          requesterMemberId: '200',
        },
        recordStatusMapping: null,
      }],
      page: 1,
      size: 20,
      total: 1,
    })
    vi.mocked(flowApi.periodicSchedule).mockResolvedValue({
      definitionId: '201',
      definitionVersion: 3,
      requesterId: '200',
      intervalMinutes: 60,
      startAt: '2026-07-30T01:00:00Z',
      nextFireAt: '2026-07-30T03:00:00Z',
      lastScheduledAt: '2026-07-30T02:00:00Z',
      lastInstanceId: '801',
      status: 'ACTIVE',
      pauseReason: null,
      updatedAt: '2026-07-30T02:00:00Z',
    })

    const wrapper = render()
    await flushPromises()

    expect(flowApi.periodicSchedule).toHaveBeenCalledWith('10', '201')
    expect(wrapper.get('.definition-trigger-summary').text())
      .toContain('固定周期 · 每 60 分钟')
    expect(wrapper.get('.periodic-runtime-summary').text())
      .toContain('周期运行 v3 · ACTIVE')
    expect(wrapper.get('.periodic-runtime-summary').text()).toContain('实例 801')
  })

  it('lists immutable versions and restores the selected snapshot into the draft editor', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const originalVersion: FlowDefinitionVersion = {
      ...boundPublishedVersion,
      version: 1,
      name: 'Original approval',
      approverId: '300',
      approverIds: ['300'],
      triggerBinding: null,
      recordStatusMapping: null,
      sourceRevision: 1,
      publishedAt: '2026-07-26T02:01:00Z',
    }
    const restoredDraft: FlowDefinitionDraft = {
      ...boundDefinition,
      name: originalVersion.name,
      approverId: originalVersion.approverId,
      approverIds: originalVersion.approverIds,
      triggerBinding: originalVersion.triggerBinding,
      recordStatusMapping: originalVersion.recordStatusMapping,
      revision: 4,
    }
    vi.mocked(flowApi.listDefinitions).mockResolvedValue({
      items: [boundDefinition], page: 1, size: 20, total: 1,
    })
    vi.mocked(flowApi.listDefinitionVersions).mockResolvedValue({
      items: [boundPublishedVersion, originalVersion],
      page: 1,
      size: 10,
      total: 2,
    })
    vi.mocked(flowApi.restoreDefinitionVersion).mockResolvedValue(restoredDraft)

    const wrapper = render()
    await flushPromises()
    await wrapper.get('.flow-definition-history').trigger('click')
    await flushPromises()

    expect(flowApi.listDefinitionVersions).toHaveBeenCalledWith('10', '201', 1, 10)
    expect(wrapper.findAll('.version-history-item')).toHaveLength(2)
    expect(wrapper.findAll('.version-history-item')[0]!.text()).toContain('v3')
    expect(wrapper.findAll('.version-history-item')[1]!.text()).toContain('v1')

    await wrapper.findAll('.version-history-item')[1]!
      .get('.flow-version-restore')
      .trigger('click')
    await flushPromises()

    expect(flowApi.restoreDefinitionVersion).toHaveBeenCalledWith('10', '201', 1)
    expect(wrapper.find('.flow-version-history-modal').exists()).toBe(false)
    expect((wrapper.get('.definition-name').element as HTMLInputElement).value)
      .toBe('Original approval')
    expect(wrapper.get('.select-assignment-member').text()).toContain('300')
    expect((wrapper.get('.definition-trigger-toggle input').element as HTMLInputElement).checked)
      .toBe(false)
  })

  it('checks and simulates a draft with route and effect results', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    vi.mocked(flowApi.listDefinitions).mockResolvedValue({
      items: [boundDefinition], page: 1, size: 20, total: 1,
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-check').trigger('click')
    await flushPromises()

    expect(flowApi.checkDefinitionDraft).toHaveBeenCalledWith('10', '201')
    expect(wrapper.get('.draft-check-result').text()).toContain('READY')
    expect(wrapper.get('.draft-check-result').text()).toContain('0')

    await wrapper.get('.flow-definition-simulate').trigger('click')
    await flushPromises()

    expect(flowApi.simulateDefinitionDraft).toHaveBeenCalledWith('10', '201', {
      requesterId: '200',
      businessKey: 'simulation:201:r2',
      trigger: {
        moduleCode: 'purchase_order',
        event: 'RECORD_ACTIVATED',
        values: {
          amount: 100,
          urgent: true,
          remark: 'sample',
        },
      },
    })
    expect(wrapper.get('.draft-simulation-result').text()).toContain('TRIGGER_MATCHED')
    expect(wrapper.get('.simulation-route-result').text()).toContain('需同意 2 人')
    expect(wrapper.get('.simulation-steps').text()).toContain('200')
    expect(wrapper.get('.simulation-steps').text()).toContain('300')
    expect(wrapper.get('.simulation-status-effects').text()).toContain('approval_status')
    expect(wrapper.get('.simulation-decision-comment-result').text())
      .toContain('必填最少 4 字')
  })

  it('renders every selected branch stage while only previewing first-stage participants', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const approvalStages = [
      {
        code: 'review',
        name: 'Review',
        approverIds: ['300'],
        approvalMode: 'SEQUENTIAL' as const,
        approverSource: { kind: 'FIXED' as const, sourceId: null },
      },
      {
        code: 'manager',
        name: 'Manager',
        approverIds: [],
        approvalMode: 'ANY' as const,
        approverSource: { kind: 'REQUESTER_MANAGER' as const, sourceId: null },
      },
    ]
    vi.mocked(flowApi.listDefinitions).mockResolvedValue({
      items: [{
        ...boundDefinition,
        parallelGateway: {
          branches: [{
            code: 'finance',
            name: 'Finance',
            approverIds: ['300'],
            approvalMode: 'SEQUENTIAL',
            approvalStages,
          }, {
            code: 'owner',
            name: 'Owner',
            approverIds: ['400'],
            approvalMode: 'SEQUENTIAL',
          }],
        },
      }],
      page: 1,
      size: 20,
      total: 1,
    })
    vi.mocked(flowApi.simulateDefinitionDraft).mockResolvedValue({
      definitionId: '201',
      revision: 2,
      check: {
        definitionId: '201',
        revision: 2,
        verdict: 'READY',
        blockerCount: 0,
        warningCount: 0,
        issues: [],
      },
      requesterId: '200',
      businessKey: 'simulation:201:r2',
      startable: true,
      reason: 'MANUAL',
      steps: [{ index: 0, approverId: '300', initial: true }],
      trigger: {
        configured: false,
        event: null,
        moduleCode: null,
        matched: true,
        reason: 'MANUAL',
      },
      statusEffects: null,
      route: {
        configured: false,
        branchCode: null,
        branchName: null,
        defaultBranch: false,
      },
      parallelRoutes: [{
        configured: true,
        branchCode: 'finance',
        branchName: 'Finance',
        defaultBranch: false,
        approvalMode: 'SEQUENTIAL',
        requiredApprovals: 1,
        activeApproverIds: ['300'],
        approvalStages,
      }],
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-simulate').trigger('click')
    await flushPromises()

    expect(wrapper.get('.simulation-branch-approval-stages').text())
      .toContain('Review')
    expect(wrapper.get('.simulation-branch-approval-stages').text())
      .toContain('请求人的直属主管')
    expect(wrapper.get('.simulation-parallel-routes').text())
      .toContain('初始处理人 300')
  })

  it('saves the graph route before checking the exact revised draft', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const revised = {
      ...boundDefinition,
      approverIds: ['200', '900', '300'],
      revision: 3,
    }
    vi.mocked(flowApi.listDefinitions).mockResolvedValue({
      items: [boundDefinition], page: 1, size: 20, total: 1,
    })
    vi.mocked(flowApi.reviseDefinition).mockResolvedValue(revised)
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-revise').trigger('click')
    await wrapper.get('.graph-route-edit').trigger('click')
    await wrapper.get('.graph-save-check').trigger('click')
    await flushPromises()

    expect(flowApi.reviseDefinition).toHaveBeenCalledWith('10', '201', {
      name: 'Purchase approval',
      approverIds: ['200', '900', '300'],
      approvalMode: 'SEQUENTIAL',
      triggerBinding,
      recordStatusMapping,
    })
    expect(flowApi.checkDefinitionDraft).toHaveBeenCalledWith('10', '201')
    expect(wrapper.get('.draft-check-result').text()).toContain('READY')
    expect(wrapper.find('.definition-name').exists()).toBe(false)
  })

  it('saves the graph route before simulating the exact revised draft', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const revised = {
      ...boundDefinition,
      approverIds: ['200', '900', '300'],
      revision: 3,
    }
    vi.mocked(flowApi.listDefinitions).mockResolvedValue({
      items: [boundDefinition], page: 1, size: 20, total: 1,
    })
    vi.mocked(flowApi.reviseDefinition).mockResolvedValue(revised)
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-revise').trigger('click')
    await wrapper.get('.graph-route-edit').trigger('click')
    await wrapper.get('.graph-save-simulate').trigger('click')
    await flushPromises()

    expect(flowApi.reviseDefinition).toHaveBeenCalledWith('10', '201', {
      name: 'Purchase approval',
      approverIds: ['200', '900', '300'],
      approvalMode: 'SEQUENTIAL',
      triggerBinding,
      recordStatusMapping,
    })
    expect(flowApi.simulateDefinitionDraft).toHaveBeenCalledWith('10', '201', {
      requesterId: '200',
      businessKey: 'simulation:201:r3',
      trigger: {
        moduleCode: 'purchase_order',
        event: 'RECORD_ACTIVATED',
        values: {
          amount: 100,
          urgent: true,
          remark: 'sample',
        },
      },
    })
    expect(wrapper.get('.draft-simulation-result').text()).toContain('TRIGGER_MATCHED')
    expect(wrapper.find('.definition-name').exists()).toBe(false)
  })

  it('saves a complete executable gateway from the graph editor', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Conditional approval')
    await wrapper.get('.select-assignment-member').trigger('click')
    await wrapper.get('.graph-gateway-edit').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDefinition).toHaveBeenCalledWith('10', {
      name: 'Conditional approval',
      approverIds: ['300'],
      approvalMode: 'SEQUENTIAL',
      gateway: {
        branches: [
          {
            code: 'urgent',
            name: 'Urgent',
            defaultBranch: false,
            conditions: [{ fieldCode: 'urgent', operator: 'EQ', value: true }],
            approverIds: ['900'],
            approvalMode: 'ANY',
          },
          {
            code: 'default',
            name: 'Default',
            defaultBranch: true,
            conditions: [],
            approverIds: ['300'],
            approvalMode: 'SEQUENTIAL',
          },
        ],
      },
    })
  })

  it('saves a complete parallel split and join from the graph editor', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Parallel approval')
    await wrapper.get('.select-assignment-member').trigger('click')
    await wrapper.get('.graph-parallel-edit').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDefinition).toHaveBeenCalledWith('10', {
      name: 'Parallel approval',
      approverIds: ['300'],
      approvalMode: 'SEQUENTIAL',
      parallelGateway: {
        branches: [
          {
            code: 'finance',
            name: 'Finance',
            approverIds: ['300'],
            approvalMode: 'SEQUENTIAL',
          },
          {
            code: 'owner',
            name: 'Owner',
            approverIds: ['400', '500'],
            approvalMode: 'ANY',
          },
        ],
      },
    })
  })

  it('saves complete inclusive conditions and routes from the graph editor', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Inclusive approval')
    await wrapper.get('.select-assignment-member').trigger('click')
    await wrapper.get('.graph-inclusive-edit').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDefinition).toHaveBeenCalledWith('10', {
      name: 'Inclusive approval',
      approverIds: ['300'],
      approvalMode: 'SEQUENTIAL',
      inclusiveGateway: {
        branches: [
          {
            code: 'urgent',
            name: 'Urgent',
            defaultBranch: false,
            conditions: [{ fieldCode: 'urgent', operator: 'EQ', value: true }],
            approverIds: ['300'],
            approvalMode: 'SEQUENTIAL',
          },
          {
            code: 'large',
            name: 'Large',
            defaultBranch: false,
            conditions: [{ fieldCode: 'amount', operator: 'GTE', value: 1000 }],
            approverIds: ['400', '500'],
            approvalMode: 'ANY',
          },
          {
            code: 'default',
            name: 'Default',
            defaultBranch: true,
            conditions: [],
            approverIds: ['600'],
            approvalMode: 'SEQUENTIAL',
          },
        ],
      },
    })
  })

  it('saves the approval mode selected on the function-first graph editor', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Any approval')
    await wrapper.get('.select-assignment-member').trigger('click')
    await wrapper.get('.graph-mode-edit').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDefinition).toHaveBeenCalledWith('10', {
      name: 'Any approval',
      approverIds: ['300'],
      approvalMode: 'ANY',
    })
  })

  it('saves and reopens a count quorum rule from the graph editor', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Two of three approval')
    await wrapper.get('.graph-route-edit').trigger('click')
    await wrapper.get('.graph-quorum-mode-edit').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDefinition).toHaveBeenCalledWith('10', {
      name: 'Two of three approval',
      approverIds: ['200', '900', '300'],
      approvalMode: 'QUORUM',
      quorumRule: {
        type: 'COUNT',
        value: 2,
      },
    })
    wrapper.unmount()

    vi.mocked(flowApi.listDefinitions).mockResolvedValue({
      items: [{
        ...boundDefinition,
        name: 'Two of three approval',
        approverIds: ['200', '900', '300'],
        approvalMode: 'QUORUM',
        quorumRule: { type: 'COUNT', value: 2 },
      }],
      page: 1,
      size: 20,
      total: 1,
    })
    const reopened = render()
    await flushPromises()
    await reopened.get('.flow-definition-revise').trigger('click')
    await flushPromises()

    expect(reopened.get('.graph-mode-value').text()).toBe('QUORUM')
    expect(reopened.get('.graph-quorum-value').text()).toBe('COUNT:2')
  })

  it('saves and reopens an approval deadline policy from the graph editor', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Timed approval')
    await wrapper.get('.select-assignment-member').trigger('click')
    await wrapper.get('.graph-deadline-edit').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDefinition).toHaveBeenCalledWith('10', {
      name: 'Timed approval',
      approverIds: ['300'],
      approvalMode: 'SEQUENTIAL',
      deadlinePolicy: {
        timeoutMinutes: 120,
        remindBeforeMinutes: 30,
        timeoutAction: 'AUTO_REJECT',
      },
    })
    wrapper.unmount()

    vi.mocked(flowApi.listDefinitions).mockResolvedValue({
      items: [{
        ...boundDefinition,
        name: 'Timed approval',
        deadlinePolicy: {
          timeoutMinutes: 120,
          remindBeforeMinutes: 30,
          timeoutAction: 'AUTO_REJECT',
        },
      }],
      page: 1,
      size: 20,
      total: 1,
    })
    const reopened = render()
    await flushPromises()
    await reopened.get('.flow-definition-revise').trigger('click')
    await flushPromises()

    expect(reopened.get('.graph-deadline-value').text()).toBe('120:AUTO_REJECT')
  })

  it('saves and reopens decision comment rules from the graph editor', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Opinion approval')
    await wrapper.get('.select-assignment-member').trigger('click')
    await wrapper.get('.graph-decision-comment-edit').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDefinition).toHaveBeenCalledWith('10', {
      name: 'Opinion approval',
      approverIds: ['300'],
      approvalMode: 'SEQUENTIAL',
      decisionCommentPolicy: {
        approveRequired: true,
        rejectRequired: true,
        minimumLength: 8,
      },
    })
    wrapper.unmount()

    vi.mocked(flowApi.listDefinitions).mockResolvedValue({
      items: [{
        ...boundDefinition,
        name: 'Opinion approval',
        decisionCommentPolicy: {
          approveRequired: true,
          rejectRequired: true,
          minimumLength: 8,
        },
      }],
      page: 1,
      size: 20,
      total: 1,
    })
    const reopened = render()
    await flushPromises()
    await reopened.get('.flow-definition-revise').trigger('click')
    await flushPromises()

    expect(reopened.get('.graph-decision-comment-value').text()).toBe('true:true:8')
  })

  it('saves and reopens bounded decision evidence rules from the graph editor', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Evidence approval')
    await wrapper.get('.select-assignment-member').trigger('click')
    await wrapper.get('.graph-decision-evidence-edit').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDefinition).toHaveBeenCalledWith('10', {
      name: 'Evidence approval',
      approverIds: ['300'],
      approvalMode: 'SEQUENTIAL',
      decisionEvidencePolicy: {
        minimumAttachments: 1,
        maximumAttachments: 3,
        allowedMimeFamilies: ['IMAGE', 'PDF'],
        signatureMode: 'REQUIRED',
      },
    })
    wrapper.unmount()

    vi.mocked(flowApi.listDefinitions).mockResolvedValue({
      items: [{
        ...boundDefinition,
        name: 'Evidence approval',
        decisionEvidencePolicy: {
          minimumAttachments: 1,
          maximumAttachments: 3,
          allowedMimeFamilies: ['IMAGE', 'PDF'],
          signatureMode: 'REQUIRED',
        },
      }],
      page: 1,
      size: 20,
      total: 1,
    })
    const reopened = render()
    await flushPromises()
    expect(reopened.get('.definition-decision-evidence-summary').text())
      .toContain('附件 1–3 个')
    await reopened.get('.flow-definition-revise').trigger('click')
    await flushPromises()

    expect(reopened.get('.graph-decision-evidence-value').text())
      .toBe('1:3:REQUIRED:IMAGE,PDF')
  })

  it('round-trips ordered completion steps and summarizes check and simulation output', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    vi.mocked(flowApi.createDefinition).mockImplementation(async (_systemId, input) => ({
      ...boundDefinition,
      ...input,
      definitionId: '201',
      revision: 3,
      updatedAt: '2026-07-31T03:00:00Z',
      triggerBinding: input.triggerBinding ?? null,
      recordStatusMapping: input.recordStatusMapping ?? null,
      approverId: input.approverIds?.[0] ?? '',
    }))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Completion approval')
    await wrapper.get('.select-assignment-member').trigger('click')
    await wrapper.get('.definition-completion-step-add').trigger('click')
    await wrapper.get('.definition-completion-step-code').setValue('archive')
    await wrapper.get('.definition-completion-step-name').setValue('Archive contract')
    await wrapper.get('.definition-completion-topic').setValue('contract.archive')
    await wrapper.get('.definition-completion-step-add').trigger('click')
    const types = wrapper.findAll('.definition-completion-step-type')
    await types[1]!.setValue('WEBHOOK')
    const codes = wrapper.findAll('.definition-completion-step-code')
    const names = wrapper.findAll('.definition-completion-step-name')
    await codes[1]!.setValue('notify')
    await names[1]!.setValue('Notify downstream')
    await wrapper.get('.definition-completion-webhook-url')
      .setValue('https://hooks.example.com/approved')
    await wrapper.get('.definition-completion-secret-ref').setValue('vault://flow/hook')
    await wrapper.findAll('.definition-completion-step-up')[1]!.trigger('click')
    await wrapper.get('.graph-save-simulate').trigger('click')
    await flushPromises()

    const input = vi.mocked(flowApi.createDefinition).mock.calls[0]![1]
    expect(input.completionSteps).toEqual([
      {
        code: 'notify',
        name: 'Notify downstream',
        type: 'WEBHOOK',
        webhook: {
          url: 'https://hooks.example.com/approved',
          secretRef: 'vault://flow/hook',
          timeoutSeconds: 10,
          maxAttempts: 3,
          baseBackoffSeconds: 5,
        },
      },
      {
        code: 'archive',
        name: 'Archive contract',
        type: 'EXTERNAL_TASK',
        externalTask: {
          topic: 'contract.archive',
          leaseSeconds: 60,
          maxAttempts: 3,
          resultJsonLimitBytes: 8192,
        },
      },
    ])
    expect(wrapper.get('.draft-check-completion-steps').text()).toContain('Notify downstream')
    expect(wrapper.get('.simulation-completion-steps-result').text()).toContain('Archive contract')

    wrapper.unmount()
    vi.mocked(flowApi.listDefinitions).mockResolvedValue({
      items: [{
        ...boundDefinition,
        name: 'Completion approval',
        completionSteps: [
          {
            code: 'notify',
            name: 'Notify downstream',
            type: 'WEBHOOK',
            webhook: {
              url: 'https://hooks.example.com/approved',
              secretRef: 'vault://must-never-read-back',
              timeoutSeconds: 10,
              maxAttempts: 3,
              baseBackoffSeconds: 5,
            },
          },
        ],
      }],
      page: 1,
      size: 20,
      total: 1,
    })
    const reopened = render()
    await flushPromises()
    expect(reopened.get('.definition-completion-steps-summary').text())
      .toContain('Notify downstream')
    await reopened.get('.flow-definition-revise').trigger('click')
    expect(reopened.text()).not.toContain('vault://must-never-read-back')
    expect((reopened.get('.definition-completion-secret-ref').element as HTMLInputElement).value)
      .toBe('********')
    await reopened.get('.modal-ok').trigger('click')
    await flushPromises()
    expect(vi.mocked(flowApi.reviseDefinition).mock.calls[0]![2].completionSteps?.[0])
      .toEqual(expect.objectContaining({
        webhook: expect.objectContaining({ secretRef: '********' }),
      }))
  })

  it('round-trips an exact published SUBFLOW target through draft, version, check and simulation', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    vi.mocked(flowApi.createDefinition).mockImplementation(async (_systemId, input) => ({
      ...boundDefinition,
      ...input,
      definitionId: '201',
      revision: 3,
      updatedAt: '2026-08-01T01:00:00Z',
      triggerBinding: input.triggerBinding ?? null,
      recordStatusMapping: input.recordStatusMapping ?? null,
      approverId: input.approverIds?.[0] ?? '',
    }))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Parent approval')
    await wrapper.get('.select-assignment-member').trigger('click')
    await wrapper.get('.definition-completion-step-add').trigger('click')
    await wrapper.get('.definition-completion-step-type').setValue('SUBFLOW')
    await wrapper.get('.definition-completion-step-code').setValue('child_review')
    await wrapper.get('.definition-completion-step-name').setValue('Child review')
    await wrapper.get('.definition-completion-subflow-definition-id').setValue('501')
    await wrapper.get('.definition-completion-subflow-version').setValue('7')
    await wrapper.get('.graph-save-simulate').trigger('click')
    await flushPromises()

    expect(vi.mocked(flowApi.createDefinition).mock.calls[0]![1].completionSteps).toEqual([{
      code: 'child_review',
      name: 'Child review',
      type: 'SUBFLOW',
      subflow: {
        definitionId: '501',
        version: 7,
      },
    }])
    expect(wrapper.get('.draft-check-completion-steps').text())
      .toContain('定义 501 / v7')
    expect(wrapper.get('.simulation-completion-steps-result').text())
      .toContain('Child review')

    wrapper.unmount()
    const subflowStep = {
      code: 'child_review',
      name: 'Child review',
      type: 'SUBFLOW' as const,
      subflow: { definitionId: '501', version: 7 },
    }
    vi.mocked(flowApi.listDefinitions).mockResolvedValue({
      items: [{ ...boundDefinition, completionSteps: [subflowStep] }],
      page: 1,
      size: 20,
      total: 1,
    })
    vi.mocked(flowApi.listDefinitionVersions).mockResolvedValue({
      items: [{ ...boundPublishedVersion, completionSteps: [subflowStep] }],
      page: 1,
      size: 10,
      total: 1,
    })
    const reopened = render()
    await flushPromises()
    expect(reopened.get('.definition-completion-steps-summary').text())
      .toContain('定义 501 / v7')
    await reopened.get('.flow-definition-revise').trigger('click')
    expect((reopened.get('.definition-completion-subflow-definition-id').element as HTMLInputElement).value)
      .toBe('501')
    expect((reopened.get('.definition-completion-subflow-version').element as HTMLInputElement).value)
      .toBe('7')
    await reopened.get('.flow-definition-history').trigger('click')
    await flushPromises()
    expect(reopened.get('.version-history-completion-steps').text())
      .toContain('Child review')
  })

  it('validates and round-trips an adjacent mixed-type parallel completion group', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    vi.mocked(flowApi.createDefinition).mockImplementation(async (_systemId, input) => ({
      ...boundDefinition,
      ...input,
      definitionId: '201',
      revision: 4,
      updatedAt: '2026-08-02T01:00:00Z',
      triggerBinding: input.triggerBinding ?? null,
      recordStatusMapping: input.recordStatusMapping ?? null,
      approverId: input.approverIds?.[0] ?? '',
    }))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Parallel completion approval')
    await wrapper.get('.select-assignment-member').trigger('click')
    await wrapper.get('.definition-completion-step-add').trigger('click')
    await wrapper.get('.definition-completion-step-code').setValue('archive')
    await wrapper.get('.definition-completion-step-name').setValue('Archive')
    await wrapper.get('.definition-completion-topic').setValue('contract.archive')
    await wrapper.get('.definition-completion-parallel-group').setValue('post_approval')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()
    expect(flowApi.createDefinition).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('精确子流程配置边界')

    await wrapper.get('.definition-completion-step-add').trigger('click')
    await wrapper.findAll('.definition-completion-step-type')[1]!.setValue('SUBFLOW')
    await wrapper.findAll('.definition-completion-step-code')[1]!.setValue('child_review')
    await wrapper.findAll('.definition-completion-step-name')[1]!.setValue('Child review')
    await wrapper.findAll('.definition-completion-parallel-group')[1]!
      .setValue('post_approval')
    await wrapper.get('.definition-completion-subflow-definition-id').setValue('501')
    await wrapper.get('.definition-completion-subflow-version').setValue('7')
    expect(wrapper.get('.definition-completion-stage-summary').text())
      .toContain('并行组 post_approval（2 个成员）')
    await wrapper.get('.graph-save-simulate').trigger('click')
    await flushPromises()

    expect(vi.mocked(flowApi.createDefinition).mock.calls[0]![1].completionSteps)
      .toEqual([
        expect.objectContaining({
          code: 'archive',
          type: 'EXTERNAL_TASK',
          parallelGroup: 'post_approval',
        }),
        expect.objectContaining({
          code: 'child_review',
          type: 'SUBFLOW',
          parallelGroup: 'post_approval',
        }),
      ])
    expect(wrapper.get('.draft-check-completion-steps').text())
      .toContain('并行组 post_approval')
    expect(wrapper.get('.simulation-completion-steps-result').text())
      .toContain('Archive')
  })

  it('requires and round-trips all three compensation action types under COMPENSATE', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    vi.mocked(flowApi.createDefinition).mockImplementation(async (_systemId, input) => ({
      ...boundDefinition,
      ...input,
      definitionId: '201',
      revision: 5,
      updatedAt: '2026-08-03T01:00:00Z',
      triggerBinding: input.triggerBinding ?? null,
      recordStatusMapping: input.recordStatusMapping ?? null,
      approverId: input.approverIds?.[0] ?? '',
    }))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Compensating completion')
    await wrapper.get('.select-assignment-member').trigger('click')
    await wrapper.get('.definition-completion-step-add').trigger('click')
    await wrapper.get('.definition-completion-step-add').trigger('click')
    await wrapper.get('.definition-completion-step-add').trigger('click')
    await wrapper.get('.definition-completion-failure-policy-select').setValue('COMPENSATE')
    const forwardTopics = wrapper.findAll('.definition-completion-topic')
    await forwardTopics[0]!.setValue('contract.create')
    await forwardTopics[1]!.setValue('contract.notify')
    await forwardTopics[2]!.setValue('contract.index')

    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()
    expect(flowApi.createDefinition).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('COMPENSATE 要求每一步都有有效补偿')

    await wrapper.get('.definition-compensation-topic').setValue('contract.create.undo')
    const compensationTypes = wrapper.findAll('.definition-compensation-type')
    await compensationTypes[1]!.setValue('WEBHOOK')
    await compensationTypes[2]!.setValue('SUBFLOW')
    await wrapper.get('.definition-compensation-webhook-url')
      .setValue('https://hooks.example.com/undo-notify')
    await wrapper.get('.definition-compensation-secret-ref').setValue('vault://flow/undo')
    await wrapper.get('.definition-compensation-subflow-definition-id').setValue('601')
    await wrapper.get('.definition-compensation-subflow-version').setValue('9')
    await wrapper.get('.graph-save-simulate').trigger('click')
    await flushPromises()

    const input = vi.mocked(flowApi.createDefinition).mock.calls[0]![1]
    expect(input.completionFailurePolicy).toBe('COMPENSATE')
    expect(input.completionSteps?.map(step => step.compensation)).toEqual([
      {
        type: 'EXTERNAL_TASK',
        externalTask: {
          topic: 'contract.create.undo',
          leaseSeconds: 60,
          maxAttempts: 3,
          resultJsonLimitBytes: 8192,
        },
      },
      {
        type: 'WEBHOOK',
        webhook: {
          url: 'https://hooks.example.com/undo-notify',
          secretRef: 'vault://flow/undo',
          timeoutSeconds: 10,
          maxAttempts: 3,
          baseBackoffSeconds: 5,
        },
      },
      {
        type: 'SUBFLOW',
        subflow: { definitionId: '601', version: 9 },
      },
    ])
    expect(wrapper.get('.draft-check-completion-failure-policy').text())
      .toContain('失败后逆序补偿')
    expect(wrapper.get('.simulation-completion-steps-result').text())
      .toContain('补偿 SUBFLOW · 定义 601 / v9')
  })

  it('creates new template versions and controls template activation', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const template = {
      templateId: '801',
      name: '同意模板',
      version: 1,
      body: '资料已核验，同意通过',
      status: 'ACTIVE' as const,
      createdAt: '2026-07-31T01:00:00Z',
      updatedAt: '2026-07-31T01:00:00Z',
    }
    vi.mocked(flowApi.listDecisionCommentTemplates).mockResolvedValue({
      items: [template],
      page: 1,
      size: 20,
      total: 1,
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-decision-template-manage').trigger('click')
    await flushPromises()
    expect(flowApi.listDecisionCommentTemplates)
      .toHaveBeenCalledWith('10', undefined, 1, 20)
    expect(wrapper.get('.flow-decision-template-item').text()).toContain('同意模板')

    await wrapper.get('.flow-decision-template-edit').trigger('click')
    await wrapper.get('.flow-decision-template-body').setValue('资料已复核，同意通过')
    await wrapper.get('.flow-decision-template-save').trigger('click')
    await flushPromises()
    expect(flowApi.updateDecisionCommentTemplate).toHaveBeenCalledWith('10', '801', {
      name: '同意模板',
      body: '资料已复核，同意通过',
    })

    await wrapper.get('.flow-decision-template-deactivate').trigger('click')
    await flushPromises()
    expect(flowApi.deactivateDecisionCommentTemplate).toHaveBeenCalledWith('10', '801')
  })

  it('saves a role approver source without requiring fixed member selection', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Role approval')
    await wrapper.get('.graph-role-source-edit').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDefinition).toHaveBeenCalledWith('10', {
      name: 'Role approval',
      approverIds: [],
      approvalMode: 'SEQUENTIAL',
      approverSource: {
        kind: 'ROLE',
        sourceId: '77',
      },
    })
  })

  it('saves and reopens a department-leader approver source', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Department leader approval')
    await wrapper.get('.graph-department-leader-source-edit').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDefinition).toHaveBeenCalledWith('10', {
      name: 'Department leader approval',
      approverIds: [],
      approvalMode: 'SEQUENTIAL',
      approverSource: {
        kind: 'DEPARTMENT_LEADER',
        sourceId: '77',
      },
    })
    wrapper.unmount()

    vi.mocked(flowApi.listDefinitions).mockResolvedValue({
      items: [{
        ...boundDefinition,
        name: 'Department leader approval',
        approverIds: [],
        approverSource: {
          kind: 'DEPARTMENT_LEADER',
          sourceId: '77',
        },
      }],
      page: 1,
      size: 20,
      total: 1,
    })
    const reopened = render()
    await flushPromises()
    await reopened.get('.flow-definition-revise').trigger('click')

    expect(reopened.get('.graph-source-value').text())
      .toBe('DEPARTMENT_LEADER:77')
  })

  it('saves requester-manager without a source id and summarizes its resolved simulation', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Requester manager approval')
    await wrapper.get('.graph-requester-manager-source-edit').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDefinition).toHaveBeenCalledWith('10', {
      name: 'Requester manager approval',
      approverIds: [],
      approvalMode: 'SEQUENTIAL',
      approverSource: {
        kind: 'REQUESTER_MANAGER',
        sourceId: null,
      },
    })

    vi.mocked(flowApi.listDefinitions).mockResolvedValue({
      items: [{
        ...boundDefinition,
        approverIds: [],
        approverSource: {
          kind: 'REQUESTER_MANAGER',
          sourceId: null,
        },
      }],
      page: 1,
      size: 20,
      total: 1,
    })
    wrapper.unmount()
    const simulated = render()
    await flushPromises()
    await simulated.get('.flow-definition-simulate').trigger('click')
    await flushPromises()

    expect(simulated.get('.simulation-route-result').text())
      .toContain('请求人的直属主管')
    expect(simulated.get('.simulation-route-result').text()).toContain('200、300')
  })

  it('saves, reopens and simulates a record MEMBER field source', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Record owner approval')
    await wrapper.get('.graph-record-member-source-edit').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDefinition).toHaveBeenCalledWith('10', {
      name: 'Record owner approval',
      approverIds: [],
      approvalMode: 'SEQUENTIAL',
      approverSource: {
        kind: 'RECORD_MEMBER_FIELD',
        sourceId: '501',
        moduleCode: 'purchase_order',
      },
    })
    wrapper.unmount()

    vi.mocked(flowApi.listDefinitions).mockResolvedValue({
      items: [{
        ...boundDefinition,
        approverIds: [],
        approverSource: {
          kind: 'RECORD_MEMBER_FIELD',
          sourceId: '501',
          moduleCode: 'purchase_order',
        },
      }],
      page: 1,
      size: 20,
      total: 1,
    })
    const reopened = render()
    await flushPromises()
    await reopened.get('.flow-definition-revise').trigger('click')
    expect(reopened.get('.graph-source-value').text())
      .toBe('RECORD_MEMBER_FIELD:501:purchase_order')

    await reopened.get('.flow-definition-simulate').trigger('click')
    await flushPromises()
    expect(reopened.get('.simulation-route-result').text())
      .toContain('记录成员字段（purchase_order · #501）')
    expect(flowApi.simulateDefinitionDraft).toHaveBeenCalledWith(
      '10',
      '201',
      expect.objectContaining({
        values: expect.any(Object),
      }),
    )
  })

  it('blocks record MEMBER field sources for periodic drafts', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Invalid periodic record owner')
    await wrapper.get('.graph-record-member-source-edit').trigger('click')
    await wrapper.get('.definition-trigger-toggle input').setValue(true)
    await wrapper.get('.definition-trigger-event').setValue('PERIODIC')
    await wrapper.get('.definition-trigger-start-at').setValue('2026-07-30T01:00:00Z')
    await wrapper.get('.definition-trigger-interval').setValue('60')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDefinition).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('固定周期没有记录上下文')
  })

  it('saves, reopens and checks an authoritative linear approval-stage plan', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const stages = [
      {
        code: 'review',
        name: 'Review',
        approverIds: ['200'],
        approvalMode: 'SEQUENTIAL' as const,
        approverSource: { kind: 'FIXED' as const, sourceId: null },
      },
      {
        code: 'confirm',
        name: 'Confirm',
        approverIds: [],
        approvalMode: 'ANY' as const,
        approverSource: { kind: 'PREVIOUS_HANDLER' as const },
      },
    ]
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Two stage approval')
    await wrapper.get('.graph-stages-edit').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDefinition).toHaveBeenCalledWith('10', {
      name: 'Two stage approval',
      approverIds: ['200'],
      approvalMode: 'SEQUENTIAL',
      approvalStages: stages,
    })
    wrapper.unmount()

    vi.mocked(flowApi.listDefinitions).mockResolvedValue({
      items: [{
        ...boundDefinition,
        approvalStages: stages,
      }],
      page: 1,
      size: 20,
      total: 1,
    })
    const reopened = render()
    await flushPromises()
    await reopened.get('.flow-definition-revise').trigger('click')
    expect(reopened.get('.graph-stages-value').text()).toBe('review,confirm')
    await reopened.get('.flow-definition-check').trigger('click')
    await flushPromises()
    expect(reopened.get('.preflight-approval-stages').text())
      .toContain('上一阶段实际处理人')
  })

  it('saves, reopens and checks independent parallel branch stage plans', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Parallel branch stages')
    await wrapper.get('.graph-parallel-stages-edit').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    const input = vi.mocked(flowApi.createDefinition).mock.calls[0]?.[1]
    expect(input?.parallelGateway?.branches[0]).toEqual(expect.objectContaining({
      approverIds: ['300'],
      approvalMode: 'SEQUENTIAL',
      approvalStages: [
        expect.objectContaining({
          code: 'finance_review',
          approverIds: ['300'],
          approverSource: { kind: 'FIXED', sourceId: null },
        }),
        expect.objectContaining({
          code: 'finance_confirm',
          approverIds: [],
          approverSource: { kind: 'REQUESTER_MANAGER', sourceId: null },
        }),
      ],
    }))
    expect(input?.parallelGateway?.branches[1]).toEqual(expect.objectContaining({
      approverIds: [],
      approverSource: { kind: 'REQUESTER', sourceId: null },
      approvalStages: [
        expect.objectContaining({
          code: 'owner_review',
          approverSource: { kind: 'REQUESTER', sourceId: null },
        }),
      ],
    }))
    wrapper.unmount()

    vi.mocked(flowApi.listDefinitions).mockResolvedValue({
      items: [{
        ...boundDefinition,
        parallelGateway: input!.parallelGateway!,
      }],
      page: 1,
      size: 20,
      total: 1,
    })
    const reopened = render()
    await flushPromises()
    await reopened.get('.flow-definition-revise').trigger('click')
    expect(reopened.get('.graph-branch-stages-value').text())
      .toBe('finance_review,finance_confirm|owner_review')
    await reopened.get('.flow-definition-check').trigger('click')
    await flushPromises()
    expect(flowApi.checkDefinitionDraft).toHaveBeenCalledWith('10', '201')
  })

  it('blocks explicit approval stages combined with a gateway', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Invalid stage gateway')
    await wrapper.get('.graph-stages-edit').trigger('click')
    await wrapper.get('.graph-gateway-edit').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDefinition).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('显式线性审批阶段不能同时配置')
  })

  it('shows the current stage cursor and ordered durable stage snapshots', async () => {
    const stagedInstance: FlowInstance = {
      ...pendingTask,
      currentStageIndex: 1,
      currentStageCode: 'confirm',
      stages: [
        {
          stageIndex: 0,
          code: 'review',
          name: 'Review',
          status: 'APPROVED',
          approverIds: ['150', '200'],
          approvalMode: 'ALL',
          requiredApprovals: 2,
          actualHandlerIds: ['150', '200'],
          startedAt: '2026-07-27T01:00:00Z',
          completedAt: '2026-07-27T01:05:00Z',
          deadline: null,
          decisionCommentPolicy: {
            approveRequired: false,
            rejectRequired: true,
            minimumLength: 1,
          },
        },
        {
          stageIndex: 1,
          code: 'confirm',
          name: 'Confirm',
          status: 'ACTIVE',
          approverIds: ['150', '200'],
          approvalMode: 'ANY',
          requiredApprovals: 1,
          actualHandlerIds: [],
          startedAt: '2026-07-27T01:05:00Z',
          completedAt: null,
          deadline: null,
          decisionCommentPolicy: {
            approveRequired: true,
            rejectRequired: true,
            minimumLength: 2,
          },
        },
      ],
    }
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([stagedInstance]))
    vi.mocked(flowApi.instance).mockResolvedValue(stagedInstance)
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.instance-list .flow-detail').trigger('click')
    await flushPromises()

    expect(wrapper.get('.detail-list').text()).toContain('2 · confirm')
    expect(wrapper.get('.instance-approval-stages').text()).toContain('Review')
    expect(wrapper.get('.instance-approval-stages').text()).toContain('APPROVED')
    expect(wrapper.get('.instance-approval-stages').text()).toContain('ACTIVE')
    expect(wrapper.get('.instance-approval-stages').text()).toContain('150、200')
  })

  it('shows independent branch stage cursors and durable snapshots', async () => {
    applySession('20', '200', ['flow.instance.read'])
    const branchStagedInstance: FlowInstance = {
      ...pendingTask,
      parallelBranches: [{
        code: 'finance',
        name: 'Finance',
        approverIds: ['200'],
        approvalMode: 'SEQUENTIAL',
        requiredApprovals: 1,
        approverId: '200',
        currentStepIndex: 0,
        currentStageIndex: 1,
        currentStageCode: 'confirm',
        status: 'PENDING',
        activeApproverIds: ['200'],
        approvedApproverIds: [],
        rejectedApproverIds: [],
        startedAt: '2026-07-27T01:00:00Z',
        completedAt: null,
        stages: [{
          stageIndex: 0,
          code: 'review',
          name: 'Review',
          status: 'APPROVED',
          approverIds: ['150'],
          approvalMode: 'SEQUENTIAL',
          requiredApprovals: 1,
          actualHandlerIds: ['150'],
          startedAt: '2026-07-27T01:00:00Z',
          completedAt: '2026-07-27T01:05:00Z',
          deadline: null,
          decisionCommentPolicy: {
            approveRequired: false,
            rejectRequired: true,
            minimumLength: 1,
          },
        }, {
          stageIndex: 1,
          code: 'confirm',
          name: 'Confirm',
          status: 'ACTIVE',
          approverIds: ['200'],
          approvalMode: 'ANY',
          requiredApprovals: 1,
          actualHandlerIds: [],
          startedAt: '2026-07-27T01:05:00Z',
          completedAt: null,
          deadline: null,
          decisionCommentPolicy: {
            approveRequired: true,
            rejectRequired: true,
            minimumLength: 2,
          },
        }],
      }],
    }
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([branchStagedInstance]))
    vi.mocked(flowApi.instance).mockResolvedValue(branchStagedInstance)
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.instance-list .flow-detail').trigger('click')
    await flushPromises()

    expect(wrapper.get('.parallel-branch-stage-cursor').text()).toContain('2 · confirm')
    expect(wrapper.findAll('.parallel-branch-approval-stage')).toHaveLength(2)
    expect(wrapper.get('.parallel-branch-approval-stages').text()).toContain('实际处理人 150')
  })

  it('rejects requester-manager sources for periodic drafts before calling the API', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Invalid periodic manager approval')
    await wrapper.get('.graph-requester-manager-source-edit').trigger('click')
    await wrapper.get('.definition-trigger-toggle input').setValue(true)
    await wrapper.get('.definition-trigger-event').setValue('PERIODIC')
    await wrapper.get('.definition-trigger-start-at').setValue('2026-07-30T01:00:00Z')
    await wrapper.get('.definition-trigger-interval').setValue('60')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDefinition).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('固定周期没有人工请求人')
  })

  it('validates every status mapping field and preserves the draft after save failure', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    vi.mocked(flowApi.createDefinition).mockRejectedValue(new Error('mapping conflict'))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Manual purchase approval')
    await wrapper.get('.select-assignment-member').trigger('click')
    await wrapper.get('.definition-status-mapping-toggle input').setValue(true)
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDefinition).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('终态 STATUS 映射需要合法字段代码')

    await wrapper.get('.definition-status-field-code').setValue('_approval_status')
    await wrapper.get('.definition-status-approved-value').setValue('0')
    await wrapper.get('.definition-status-rejected-value').setValue('0102')
    await wrapper.get('.definition-status-withdrawn-value').setValue('')
    await wrapper.get('.definition-status-terminated-value').setValue('12345678901234567890')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()
    expect(flowApi.createDefinition).not.toHaveBeenCalled()

    await wrapper.get('.definition-status-field-code').setValue('approval_status')
    await wrapper.get('.definition-status-approved-value').setValue('101')
    await wrapper.get('.definition-status-rejected-value').setValue('102')
    await wrapper.get('.definition-status-withdrawn-value').setValue('103')
    await wrapper.get('.definition-status-terminated-value').setValue('104')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDefinition).toHaveBeenCalledWith('10', {
      name: 'Manual purchase approval',
      approverIds: ['300'],
      approvalMode: 'SEQUENTIAL',
      recordStatusMapping,
    })
    expect((wrapper.get('.definition-status-mapping-toggle input').element as HTMLInputElement).checked)
      .toBe(true)
    expect((wrapper.get('.definition-status-field-code').element as HTMLInputElement).value)
      .toBe('approval_status')
    expect((wrapper.get('.definition-status-approved-value').element as HTMLInputElement).value)
      .toBe('101')
    expect((wrapper.get('.definition-status-rejected-value').element as HTMLInputElement).value)
      .toBe('102')
    expect((wrapper.get('.definition-status-withdrawn-value').element as HTMLInputElement).value)
      .toBe('103')
    expect((wrapper.get('.definition-status-terminated-value').element as HTMLInputElement).value)
      .toBe('104')
    expect(wrapper.text()).toContain('mapping conflict')
  })

  it('rejects terminal status mapping for non-activation automatic events', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-create').trigger('click')
    await wrapper.get('.definition-name').setValue('Updated purchase approval')
    await wrapper.get('.select-assignment-member').trigger('click')
    await wrapper.get('.definition-trigger-toggle input').setValue(true)
    await wrapper.get('.definition-trigger-module').setValue('purchase_order')
    await wrapper.get('.definition-trigger-event').setValue('RECORD_UPDATED')
    await wrapper.get('.definition-status-mapping-toggle input').setValue(true)
    await wrapper.get('.definition-status-field-code').setValue('approval_status')
    await wrapper.get('.definition-status-approved-value').setValue('101')
    await wrapper.get('.definition-status-rejected-value').setValue('102')
    await wrapper.get('.definition-status-withdrawn-value').setValue('103')
    await wrapper.get('.definition-status-terminated-value').setValue('104')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDefinition).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('终态 STATUS 映射只能与 RECORD_ACTIVATED 自动触发同时配置')
    expect((wrapper.get('.definition-trigger-event').element as HTMLSelectElement).value)
      .toBe('RECORD_UPDATED')
    expect((wrapper.get('.definition-status-mapping-toggle input').element as HTMLInputElement).checked)
      .toBe(true)
  })

  it('preloads draft trigger and status mapping and keeps mapping when revise disables trigger', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    vi.mocked(flowApi.listDefinitions).mockResolvedValue({
      items: [boundDefinition], page: 1, size: 20, total: 1,
    })
    vi.mocked(flowApi.reviseDefinition).mockResolvedValue({
      ...boundDefinition,
      triggerBinding: null,
      revision: 3,
    })
    const wrapper = render()
    await flushPromises()

    expect(wrapper.get('.definition-trigger-summary').text())
      .toContain('purchase_order · RECORD_ACTIVATED · 优先级 200 · 并行')
    expect(wrapper.get('.definition-status-mapping-summary').text())
      .toContain('approval_status · APPROVED→101 · REJECTED→102 · WITHDRAWN→103 · TERMINATED→104')
    await wrapper.get('.flow-definition-revise').trigger('click')
    expect((wrapper.get('.definition-trigger-toggle input').element as HTMLInputElement).checked)
      .toBe(true)
    expect((wrapper.get('.definition-trigger-module').element as HTMLInputElement).value)
      .toBe('purchase_order')
    expect((wrapper.get('.definition-trigger-priority').element as HTMLInputElement).value)
      .toBe('200')
    expect((wrapper.get('.definition-trigger-exclusive input').element as HTMLInputElement).checked)
      .toBe(false)
    expect(wrapper.findAll('.definition-trigger-condition')).toHaveLength(3)
    expect(wrapper.get('.definition-trigger-summary').text())
      .toContain('amount GTE 100 AND urgent EQ true AND remark NOT_EMPTY')
    expect((wrapper.get('.definition-status-mapping-toggle input').element as HTMLInputElement).checked)
      .toBe(true)
    expect((wrapper.get('.definition-status-field-code').element as HTMLInputElement).value)
      .toBe('approval_status')
    expect((wrapper.get('.definition-status-approved-value').element as HTMLInputElement).value)
      .toBe('101')
    expect((wrapper.get('.definition-status-rejected-value').element as HTMLInputElement).value)
      .toBe('102')
    expect((wrapper.get('.definition-status-withdrawn-value').element as HTMLInputElement).value)
      .toBe('103')
    expect((wrapper.get('.definition-status-terminated-value').element as HTMLInputElement).value)
      .toBe('104')

    await wrapper.get('.definition-trigger-toggle input').setValue(false)
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.reviseDefinition).toHaveBeenCalledWith('10', '201', {
      name: 'Purchase approval',
      approverIds: ['200', '300'],
      approvalMode: 'SEQUENTIAL',
      recordStatusMapping,
    })
    expect(wrapper.find('.definition-name').exists()).toBe(false)
  })

  it('restores a non-activation event when revising a definition', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    vi.mocked(flowApi.listDefinitions).mockResolvedValue({
      items: [{
        ...boundDefinition,
        triggerBinding: {
          ...triggerBinding,
          event: 'RECORD_DELETED',
        },
        recordStatusMapping: null,
      }],
      page: 1,
      size: 20,
      total: 1,
    })
    const wrapper = render()
    await flushPromises()

    expect(wrapper.get('.definition-trigger-summary').text())
      .toContain('purchase_order · RECORD_DELETED · 优先级 200 · 并行')
    await wrapper.get('.flow-definition-revise').trigger('click')
    expect((wrapper.get('.definition-trigger-event').element as HTMLSelectElement).value)
      .toBe('RECORD_DELETED')
  })

  it('renders immutable trigger and status mapping summaries returned by publish', async () => {
    applySession('20', '200', ['flow.definition.manage'])
    vi.mocked(flowApi.listDefinitions).mockResolvedValue({
      items: [boundDefinition], page: 1, size: 20, total: 1,
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-definition-publish').trigger('click')
    await flushPromises()

    expect(flowApi.publishDefinition).toHaveBeenCalledWith('10', '201')
    expect(wrapper.get('.published-trigger-summary').text()).toContain('最近发布 v3')
    expect(wrapper.get('.published-trigger-summary').text())
      .toContain('purchase_order · RECORD_ACTIVATED · 优先级 200 · 并行')
    expect(wrapper.get('.published-trigger-summary').text())
      .toContain('amount GTE 100 AND urgent EQ true AND remark NOT_EMPTY')
    expect(wrapper.get('.published-status-mapping-summary').text())
      .toContain('approval_status · APPROVED→101 · REJECTED→102 · WITHDRAWN→103 · TERMINATED→104')
  })

  it('renders loading, empty and retryable error states for the published start catalog', async () => {
    applySession('20', '200', ['flow.instance.start'])
    let resolveCatalog!: (value: FlowStartableDefinitionPage) => void
    vi.mocked(flowApi.listStartableDefinitions).mockImplementationOnce(() =>
      new Promise((resolve) => { resolveCatalog = resolve }))
    const loading = render()

    await loading.get('.flow-heading .flow-start').trigger('click')
    expect(loading.get('.start-catalog-loading').text()).toContain('正在加载')
    resolveCatalog(startableDefinitionPage([]))
    await flushPromises()
    expect(loading.get('.start-catalog-empty').text()).toContain('暂无可发起的已发布流程')
    loading.unmount()

    vi.mocked(flowApi.listStartableDefinitions)
      .mockRejectedValueOnce(new Error('catalog unavailable'))
      .mockResolvedValueOnce(startableDefinitionPage())
    const failed = render()
    await failed.get('.flow-heading .flow-start').trigger('click')
    await flushPromises()
    expect(failed.get('.start-catalog-error').text()).toContain('catalog unavailable')
    await failed.get('.start-catalog-retry').trigger('click')
    await flushPromises()
    expect(failed.find('.start-catalog-error').exists()).toBe(false)
    expect(failed.findAll('.start-definition-id option')).toHaveLength(2)
  })

  it('reuses the start key for an exact retry and rotates it after payload changes', async () => {
    applySession('20', '200', ['flow.instance.start'])
    vi.mocked(flowApi.startInstance).mockRejectedValue(new Error('temporary failure'))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-heading .flow-start').trigger('click')
    await wrapper.get('.start-definition-id').setValue('201')
    await wrapper.get('.start-business-key').setValue('PO-2026-0008')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()
    const firstKey = vi.mocked(flowApi.startInstance).mock.calls[0]![3]

    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()
    expect(vi.mocked(flowApi.startInstance).mock.calls[1]![3]).toBe(firstKey)

    await wrapper.get('.start-business-key').setValue('PO-2026-0009')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()
    expect(vi.mocked(flowApi.startInstance).mock.calls[2]![3]).not.toBe(firstKey)
  })

  it('passes bounded JSON field values when manually starting a conditional route', async () => {
    applySession('20', '200', ['flow.instance.start'])
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-heading .flow-start').trigger('click')
    await wrapper.get('.start-definition-id').setValue('201')
    await wrapper.get('.start-business-key').setValue('PO-GATEWAY-1')
    await wrapper.get('.start-route-values').setValue('{"amount":100,"urgent":true}')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.startInstance).toHaveBeenCalledWith('10', '201', {
      businessKey: 'PO-GATEWAY-1',
      values: { amount: 100, urgent: true },
    }, expect.any(String))
  })

  it('shows a success receipt without requiring instance read permission', async () => {
    applySession('20', '200', ['flow.instance.start'])
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-heading .flow-start').trigger('click')
    await wrapper.get('.start-definition-id').setValue('201')
    await wrapper.get('.start-business-key').setValue('PO-2026-0008')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(wrapper.get('.start-receipt').text()).toContain('审批已发起')
    expect(wrapper.get('.start-receipt').text()).toContain('101')
    expect(wrapper.get('.start-receipt').text()).toContain('contract:301')
    expect(flowApi.instance).not.toHaveBeenCalled()
    expect(flowApi.listInstances).not.toHaveBeenCalled()
  })

  it('prefills the manual start form from a workbench deep link', async () => {
    route.query = {
      flowStart: '1',
      definitionId: '201',
      definitionVersion: '3',
      businessKey: 'R-101',
      moduleCode: 'work_order',
      recordId: '101',
    }
    route.fullPath = '/systems/10/flows?flowStart=1&businessKey=R-101'
    applySession('20', '200', ['flow.instance.start'])

    const wrapper = render()
    await flushPromises()

    expect((wrapper.get('.start-definition-id').element as HTMLSelectElement).value).toBe('201')
    expect((wrapper.get('.start-version').element as HTMLInputElement).value).toBe('3')
    expect((wrapper.get('.start-business-key').element as HTMLInputElement).value).toBe('R-101')
    expect((wrapper.get('.start-binding-module').element as HTMLInputElement).value).toBe('work_order')
    expect((wrapper.get('.start-binding-record').element as HTMLInputElement).value).toBe('101')
  })

  it('starts with an optional record binding, reads the instance back and renders the binding', async () => {
    applySession('20', '200', ['flow.instance.start', 'flow.instance.read'])
    const boundInstance: FlowInstance = {
      ...pendingTask,
      recordBinding: {
        moduleCode: 'purchase_order',
        recordId: '9001',
      },
    }
    vi.mocked(flowApi.startInstance).mockResolvedValue(boundInstance)
    vi.mocked(flowApi.instance).mockResolvedValue(boundInstance)
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage())
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-heading .flow-start').trigger('click')
    await wrapper.get('.start-definition-id').setValue('201')
    await wrapper.get('.start-version').setValue('2')
    await wrapper.get('.start-business-key').setValue('PO-2026-0008')
    await wrapper.get('.start-binding-module').setValue('purchase_order')
    await wrapper.get('.start-binding-record').setValue('9001')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.startInstance).toHaveBeenCalledWith('10', '201', {
      definitionVersion: 2,
      businessKey: 'PO-2026-0008',
      recordBinding: {
        moduleCode: 'purchase_order',
        recordId: '9001',
      },
    }, expect.any(String))
    expect(flowApi.instance).toHaveBeenCalledWith('10', '101')
    expect(wrapper.find('.start-business-key').exists()).toBe(false)

    await wrapper.get('.instance-list .flow-detail').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('绑定模块')
    expect(wrapper.text()).toContain('purchase_order')
    expect(wrapper.text()).toContain('绑定记录 ID')
    expect(wrapper.text()).toContain('9001')
  })

  it('renders route and branch deadline snapshots in the instance detail', async () => {
    applySession('20', '200', ['flow.instance.read'])
    const policy = {
      timeoutMinutes: 120,
      remindBeforeMinutes: 30,
      timeoutAction: 'AUTO_REJECT' as const,
    }
    const timedInstance: FlowInstance = {
      ...pendingTask,
      deadline: {
        policy,
        remindAt: '2026-07-27T02:30:00Z',
        dueAt: '2026-07-27T03:00:00Z',
        remindedAt: '2026-07-27T02:30:00Z',
        processedAt: '2026-07-27T03:00:00Z',
        overdue: true,
      },
      parallelBranches: [{
        code: 'finance',
        name: 'Finance',
        approverIds: ['200'],
        approvalMode: 'SEQUENTIAL',
        requiredApprovals: 1,
        approverId: '200',
        currentStepIndex: 0,
        status: 'PENDING',
        activeApproverIds: ['200'],
        approvedApproverIds: [],
        rejectedApproverIds: [],
        startedAt: '2026-07-27T01:00:00Z',
        completedAt: null,
        deadline: {
          policy,
          remindAt: '2026-07-27T02:30:00Z',
          dueAt: '2026-07-27T03:00:00Z',
          remindedAt: null,
          processedAt: null,
          overdue: false,
        },
      }],
    }
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([timedInstance]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    vi.mocked(flowApi.instance).mockResolvedValue(timedInstance)
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.instance-list .flow-detail').trigger('click')
    await flushPromises()

    expect(wrapper.get('.instance-deadline-runtime').text()).toContain('120')
    expect(wrapper.get('.instance-deadline-runtime').text()).toContain('自动拒绝')
    expect(wrapper.get('.instance-deadline-runtime').text()).toContain('已逾期')
    expect(wrapper.get('.parallel-branch-deadline').text()).toContain('120')
  })

  it('renders immutable route and branch decision comment policies in instance detail', async () => {
    applySession('20', '200', ['flow.instance.read'])
    const routedInstance: FlowInstance = {
      ...pendingTask,
      decisionCommentPolicy: {
        approveRequired: true,
        rejectRequired: false,
        minimumLength: 3,
      },
      parallelBranches: [{
        code: 'finance',
        name: 'Finance',
        approverIds: ['200'],
        approvalMode: 'SEQUENTIAL',
        requiredApprovals: 1,
        approverId: '200',
        currentStepIndex: 0,
        status: 'PENDING',
        activeApproverIds: ['200'],
        approvedApproverIds: [],
        rejectedApproverIds: [],
        startedAt: '2026-07-27T01:00:00Z',
        completedAt: null,
        decisionCommentPolicy: {
          approveRequired: false,
          rejectRequired: true,
          minimumLength: 9,
        },
      }],
    }
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([routedInstance]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    vi.mocked(flowApi.instance).mockResolvedValue(routedInstance)
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.instance-list .flow-detail').trigger('click')
    await flushPromises()

    expect(wrapper.get('.instance-decision-comment-runtime').text())
      .toContain('通过必填')
    expect(wrapper.get('.instance-decision-comment-runtime').text())
      .toContain('最少 3 字')
    expect(wrapper.get('.parallel-branch-decision-comment').text())
      .toContain('驳回必填')
    expect(wrapper.get('.parallel-branch-decision-comment').text())
      .toContain('最少 9 字')
  })

  it('requires an all-or-none binding and preserves all start drafts after failure', async () => {
    applySession('20', '200', ['flow.instance.start'])
    vi.mocked(flowApi.startInstance).mockRejectedValue(new Error('record already pending'))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-heading .flow-start').trigger('click')
    await wrapper.get('.start-definition-id').setValue('201')
    await wrapper.get('.start-business-key').setValue('PO-2026-0008')
    await wrapper.get('.start-binding-module').setValue('purchase_order')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.startInstance).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('模块代码和记录 ID 必须同时填写或同时留空')

    await wrapper.get('.start-binding-record').setValue('9001')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.startInstance).toHaveBeenCalledWith('10', '201', {
      businessKey: 'PO-2026-0008',
      recordBinding: {
        moduleCode: 'purchase_order',
        recordId: '9001',
      },
    }, expect.any(String))
    expect((wrapper.get('.start-definition-id').element as HTMLSelectElement).value).toBe('201')
    expect((wrapper.get('.start-business-key').element as HTMLInputElement).value).toBe('PO-2026-0008')
    expect((wrapper.get('.start-binding-module').element as HTMLInputElement).value).toBe('purchase_order')
    expect((wrapper.get('.start-binding-record').element as HTMLInputElement).value).toBe('9001')
    expect(wrapper.text()).toContain('record already pending')
  })

  it('uses server status/pages and resets them when the tenant changes', async () => {
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([pendingTask], 1, 21))
    const wrapper = render()
    await flushPromises()

    expect(flowApi.listApprovalTasks).toHaveBeenCalledWith('10', {
      status: 'PENDING',
      page: 1,
      size: 20,
    })
    await wrapper.get('.task-status-filter').trigger('click')
    await flushPromises()
    await wrapper.get('.task-page-two').trigger('click')
    await flushPromises()
    expect(flowApi.listApprovalTasks).toHaveBeenCalledWith('10', {
      status: 'COMPLETED',
      page: 2,
      size: 20,
    })

    applySession('21')
    await flushPromises()
    expect(vi.mocked(flowApi.listApprovalTasks).mock.calls.at(-1)).toEqual([
      '10',
      { status: 'PENDING', page: 1, size: 20 },
    ])
  })

  it('reloads both the personal queue and general instances after approval', async () => {
    let approved = false
    vi.mocked(flowApi.listApprovalTasks).mockImplementation(async () =>
      approved ? taskPage([]) : taskPage([pendingTask]))
    vi.mocked(flowApi.approve).mockImplementation(async () => {
      approved = true
      return {
        ...pendingTask,
        status: 'APPROVED',
        completedAt: '2026-07-27T01:01:00Z',
      }
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.task-approve').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.approve).toHaveBeenCalledWith('10', '101', '')
    expect(flowApi.listApprovalTasks).toHaveBeenCalledTimes(2)
    expect(flowApi.listInstances).toHaveBeenCalledTimes(2)
  })

  it('claims, heartbeats, completes and fails external tasks with an in-memory lease token', async () => {
    applySession('20', '200', ['flow.external-task.work'])
    const availableExecution = {
      executionId: '9901',
      instanceId: '101',
      definitionId: '201',
      definitionVersion: 3,
      ordinal: 0,
      code: 'archive',
      name: 'Archive contract',
      type: 'EXTERNAL_TASK' as const,
      status: 'AVAILABLE' as const,
      externalTask: {
        topic: 'contract.archive',
        leaseSeconds: 60,
        resultJsonLimitBytes: 8192,
      },
      attemptCount: 0,
      maxAttempts: 3,
      availableAt: '2026-07-31T03:00:00Z',
      leaseExpiresAt: null,
      createdAt: '2026-07-31T03:00:00Z',
      startedAt: null,
      completedAt: null,
      attempts: [],
    }
    const leasedExecution = {
      ...availableExecution,
      status: 'LEASED' as const,
      attemptCount: 1,
      leaseExpiresAt: '2026-07-31T03:01:00Z',
      startedAt: '2026-07-31T03:00:01Z',
    }
    vi.mocked(flowApi.listExternalTasks).mockResolvedValue({
      items: [availableExecution],
      page: 1,
      size: 20,
      total: 1,
    })
    vi.mocked(flowApi.claimExternalTask).mockResolvedValue({
      execution: leasedExecution,
      leaseToken: 'one-time-lease-secret',
    })
    vi.mocked(flowApi.heartbeatExternalTask).mockResolvedValue(leasedExecution)
    vi.mocked(flowApi.completeExternalTask).mockResolvedValue({
      ...leasedExecution,
      status: 'SUCCEEDED',
      completedAt: '2026-07-31T03:00:30Z',
      result: { archived: true },
    })
    vi.mocked(flowApi.failExternalTask).mockResolvedValue({
      ...leasedExecution,
      status: 'RETRYING',
      availableAt: '2026-07-31T03:02:00Z',
      failure: {
        code: 'ARCHIVE_FAILED',
        message: 'archive rejected',
        retryable: true,
      },
    })
    const wrapper = render()
    await flushPromises()
    const localStorageSet = vi.spyOn(window.localStorage, 'setItem')
    const sessionStorageSet = vi.spyOn(window.sessionStorage, 'setItem')
    const pushState = vi.spyOn(window.history, 'pushState')
    const replaceState = vi.spyOn(window.history, 'replaceState')
    const initialUrl = window.location.href

    expect(flowApi.listExternalTasks).toHaveBeenCalledWith('10', '', 1, 20)
    expect(wrapper.get('.external-task-card').text()).toContain('contract.archive')
    await wrapper.get('.external-task-topic-filter').setValue('contract.archive')
    await wrapper.get('.external-task-filter-submit').trigger('click')
    await flushPromises()
    expect(flowApi.listExternalTasks).toHaveBeenLastCalledWith(
      '10',
      'contract.archive',
      1,
      20,
    )

    await wrapper.get('.external-task-claim').trigger('click')
    await flushPromises()
    expect(flowApi.claimExternalTask).toHaveBeenCalledWith('10', '9901')
    expect(wrapper.text()).not.toContain('one-time-lease-secret')
    expect(localStorageSet).not.toHaveBeenCalled()
    expect(sessionStorageSet).not.toHaveBeenCalled()
    expect(pushState).not.toHaveBeenCalled()
    expect(replaceState).not.toHaveBeenCalled()
    expect(window.location.href).toBe(initialUrl)
    await wrapper.get('.external-task-heartbeat').trigger('click')
    await flushPromises()
    expect(flowApi.heartbeatExternalTask)
      .toHaveBeenCalledWith('10', '9901', 'one-time-lease-secret')

    await wrapper.get('.external-task-result').setValue('{"archived":true}')
    await wrapper.get('.external-task-complete').trigger('click')
    await flushPromises()
    expect(flowApi.completeExternalTask).toHaveBeenCalledWith('10', '9901', {
      leaseToken: 'one-time-lease-secret',
      result: { archived: true },
    })

    await wrapper.get('.external-task-claim').trigger('click')
    await flushPromises()
    await wrapper.get('.external-task-failure-code').setValue('ARCHIVE_FAILED')
    await wrapper.get('.external-task-failure-message').setValue('archive rejected')
    await wrapper.get('.external-task-fail').trigger('click')
    await flushPromises()
    expect(flowApi.failExternalTask).toHaveBeenCalledWith('10', '9901', {
      leaseToken: 'one-time-lease-secret',
      code: 'ARCHIVE_FAILED',
      message: 'archive rejected',
    })
    localStorageSet.mockRestore()
    sessionStorageSet.mockRestore()
    pushState.mockRestore()
    replaceState.mockRestore()
  })

  it('clears an in-memory lease when detail context switches to another instance', async () => {
    applySession('20', '200', ['flow.external-task.work', 'flow.instance.read'])
    const secondInstance: FlowInstance = {
      ...pendingTask,
      instanceId: '102',
      businessKey: 'contract:302',
    }
    const execution = {
      executionId: '9901',
      instanceId: '101',
      definitionId: '201',
      definitionVersion: 3,
      ordinal: 0,
      code: 'archive',
      name: 'Archive contract',
      type: 'EXTERNAL_TASK' as const,
      status: 'AVAILABLE' as const,
      externalTask: {
        topic: 'contract.archive',
        leaseSeconds: 60,
        resultJsonLimitBytes: 8192,
      },
      attemptCount: 0,
      maxAttempts: 3,
      availableAt: '2026-07-31T03:00:00Z',
      createdAt: '2026-07-31T03:00:00Z',
      startedAt: null,
      completedAt: null,
      attempts: [],
    }
    vi.mocked(flowApi.listInstances).mockResolvedValue(
      instancePage([pendingTask, secondInstance]),
    )
    vi.mocked(flowApi.instance).mockImplementation(async (_systemId, instanceId) => (
      instanceId === '102' ? secondInstance : pendingTask
    ))
    vi.mocked(flowApi.listExternalTasks).mockResolvedValue({
      items: [execution],
      page: 1,
      size: 20,
      total: 1,
    })
    vi.mocked(flowApi.claimExternalTask).mockResolvedValue({
      execution: {
        ...execution,
        status: 'LEASED',
        attemptCount: 1,
        leaseExpiresAt: '2026-07-31T03:01:00Z',
        startedAt: '2026-07-31T03:00:01Z',
      },
      leaseToken: 'lease-for-instance-101',
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.external-task-claim').trigger('click')
    await flushPromises()
    expect(wrapper.find('.external-task-heartbeat').exists()).toBe(true)
    await wrapper.findAll('.instance-list .flow-detail')[0]!.trigger('click')
    await flushPromises()
    await wrapper.findAll('.instance-list .flow-detail')[1]!.trigger('click')
    await flushPromises()
    await wrapper.get('.external-task-filter-submit').trigger('click')
    await flushPromises()

    expect(wrapper.find('.external-task-heartbeat').exists()).toBe(false)
    expect(wrapper.find('.external-task-claim').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('lease-for-instance-101')
  })

  it('enforces the runtime approval policy before submission and accepts a Unicode retry', async () => {
    const requiredTask: FlowInstance = {
      ...pendingTask,
      decisionCommentPolicy: {
        approveRequired: true,
        rejectRequired: true,
        minimumLength: 3,
      },
    }
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([requiredTask]))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.task-approve').trigger('click')
    expect(wrapper.get('.decision-comment-requirement').text()).toContain('至少 3 个字符')
    await wrapper.get('.decision-comment').setValue('好🙂')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.approve).not.toHaveBeenCalled()
    expect(wrapper.get('.decision-error').text()).toContain('至少输入 3 个字符')

    await wrapper.get('.decision-comment').setValue('很好🙂')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.approve).toHaveBeenCalledWith('10', '101', '很好🙂')
  })

  it('enforces evidence requirements and submits resolved attachments, typed signature and template', async () => {
    const evidenceTask: FlowInstance = {
      ...pendingTask,
      decisionCommentPolicy: {
        approveRequired: false,
        rejectRequired: true,
        minimumLength: 3,
      },
      decisionEvidencePolicy: {
        minimumAttachments: 1,
        maximumAttachments: 2,
        allowedMimeFamilies: ['IMAGE'],
        signatureMode: 'REQUIRED',
      },
    }
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([evidenceTask]))
    vi.mocked(flowApi.listDecisionCommentTemplates).mockResolvedValue({
      items: [{
        templateId: '801',
        name: '同意模板',
        version: 2,
        body: '资料已复核，同意通过',
        status: 'ACTIVE',
        createdAt: '2026-07-31T01:00:00Z',
        updatedAt: '2026-07-31T02:00:00Z',
      }],
      page: 1,
      size: 100,
      total: 1,
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.task-approve').trigger('click')
    await flushPromises()
    expect(wrapper.get('.decision-evidence-editor').text()).toContain('附件 1–')
    await wrapper.get('.modal-ok').trigger('click')
    expect(wrapper.get('.decision-error').text()).toContain('附件数量必须在 1 到 2 个之间')
    expect(flowApi.approve).not.toHaveBeenCalled()

    await wrapper.get('.decision-evidence-attachment-id').setValue('901')
    await wrapper.get('.modal-ok').trigger('click')
    expect(wrapper.get('.decision-error').text()).toContain('必须提供签名')
    expect(flowApi.approve).not.toHaveBeenCalled()

    await wrapper.get('.decision-comment-template').setValue('801')
    await wrapper.get('.decision-evidence-signature-kind').setValue('TYPED')
    await wrapper.get('.decision-evidence-typed-signature').setValue('审批人')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(fileApi.get).toHaveBeenCalledWith('10', '901')
    expect(flowApi.approve).toHaveBeenCalledWith('10', '101', '', undefined, {
      attachmentFileIds: [901],
      typedSignature: '审批人',
      commentTemplateId: 801,
    })
  })

  it('enforces the selected parallel branch policy without affecting a sibling branch', async () => {
    const branchBase = {
      approverIds: ['200'],
      approvalMode: 'SEQUENTIAL' as const,
      requiredApprovals: 1,
      approverId: '200',
      currentStepIndex: 0,
      status: 'PENDING' as const,
      activeApproverIds: ['200'],
      approvedApproverIds: [],
      rejectedApproverIds: [],
      startedAt: '2026-07-27T01:00:00Z',
      completedAt: null,
    }
    const parallelTask: FlowInstance = {
      ...pendingTask,
      parallelBranches: [
        {
          ...branchBase,
          code: 'finance',
          name: 'Finance',
          decisionCommentPolicy: {
            approveRequired: false,
            rejectRequired: true,
            minimumLength: 1,
          },
        },
        {
          ...branchBase,
          code: 'owner',
          name: 'Owner',
          decisionCommentPolicy: {
            approveRequired: true,
            rejectRequired: true,
            minimumLength: 5,
          },
        },
      ],
    }
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([parallelTask]))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.task-approve').trigger('click')
    await wrapper.get('.decision-branch-code').setValue('owner')
    await wrapper.get('.decision-comment').setValue('四字🙂')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.approveBranch).not.toHaveBeenCalled()
    expect(wrapper.get('.decision-error').text()).toContain('至少输入 5 个字符')

    await wrapper.get('.decision-comment').setValue('足够四字🙂')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.approveBranch).toHaveBeenCalledWith('10', '101', 'owner', '足够四字🙂')
  })

  it('creates, lists and revokes an outgoing approval delegation', async () => {
    const activeRule = {
      delegationRuleId: '701',
      delegatorMemberId: '200',
      delegateMemberId: '300',
      startsAt: '2026-08-01T00:00:00Z',
      endsAt: '2026-08-02T00:00:00Z',
      definitionId: '201',
      status: 'ACTIVE' as const,
      createdAt: '2026-07-30T00:00:00Z',
      createdByMemberId: '200',
      revokedAt: null,
      revokedByMemberId: null,
    }
    vi.mocked(flowApi.listDelegations).mockResolvedValue({
      items: [activeRule],
      page: 1,
      size: 20,
      total: 1,
    })
    const wrapper = render()
    await flushPromises()

    expect(flowApi.listDelegations).toHaveBeenCalledWith('10', undefined, 1, 20)
    expect(wrapper.get('.delegation-card').text()).toContain('200')
    expect(wrapper.get('.delegation-card').text()).toContain('300')

    await wrapper.get('.delegation-create').trigger('click')
    await wrapper.get('.delegation-delegate').trigger('click')
    await wrapper.get('.delegation-starts-at').setValue('2026-08-01T08:00')
    await wrapper.get('.delegation-ends-at').setValue('2026-08-02T08:00')
    await wrapper.get('.delegation-definition-id').setValue('201')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.createDelegation).toHaveBeenCalledWith('10', {
      delegateMemberId: '300',
      startsAt: new Date('2026-08-01T08:00').toISOString(),
      endsAt: new Date('2026-08-02T08:00').toISOString(),
      definitionId: '201',
    })

    await wrapper.get('.delegation-revoke').trigger('click')
    await flushPromises()
    expect(flowApi.revokeDelegation).toHaveBeenCalledWith('10', '701')
  })

  it('marks delegated tasks and submits the represented participant explicitly', async () => {
    const delegatedTask: FlowInstance = {
      ...pendingTask,
      approverId: '300',
      approverIds: ['300'],
      activeApproverIds: ['300'],
      representedAuthorities: [{
        representedMemberId: '300',
        delegationRuleId: '701',
      }],
    }
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([delegatedTask]))
    const wrapper = render()
    await flushPromises()

    expect(wrapper.get('.delegated-task-state').text()).toContain('300')
    await wrapper.get('.task-approve').trigger('click')
    expect((wrapper.get('.decision-represented-member-id').element as HTMLSelectElement).value)
      .toBe('300')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.approve).toHaveBeenCalledWith('10', '101', '', '300')
  })

  it('renders the actual actor, represented participant and delegation rule in history', async () => {
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.instance).mockResolvedValue(pendingTask)
    vi.mocked(flowApi.history).mockResolvedValue({
      instanceId: '101',
      events: [{
        sequence: 1,
        type: 'APPROVED',
        actorId: '200',
        representedMemberId: '300',
        delegationRuleId: '701',
        fromStatus: 'PENDING',
        toStatus: 'APPROVED',
        comment: 'approved for owner',
        occurredAt: '2026-07-30T01:00:00Z',
      }],
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.instance-list .flow-detail').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('实际操作人 200')
    expect(wrapper.text()).toContain('代表成员 300')
    expect(wrapper.text()).toContain('委托规则 701')
  })

  it('renders immutable evidence attachment, signature and template snapshots in history', async () => {
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.instance).mockResolvedValue(pendingTask)
    vi.mocked(flowApi.history).mockResolvedValue({
      instanceId: '101',
      events: [{
        sequence: 1,
        type: 'APPROVED',
        actorId: '200',
        fromStatus: 'PENDING',
        toStatus: 'APPROVED',
        comment: null,
        evidence: {
          evidenceId: '8801',
          branchCode: 'finance',
          stageIndex: 1,
          attachments: [{
            fileId: '901',
            fileName: 'evidence.png',
            contentType: 'image/png',
            sizeBytes: 1024,
            sha256: 'abc123',
          }],
          signature: {
            kind: 'TYPED',
            typedValue: '审批人',
          },
          template: {
            templateId: '801',
            version: 2,
            name: '同意模板',
          },
          actorId: '200',
          representedMemberId: '300',
          delegationRuleId: '701',
          occurredAt: '2026-07-31T02:00:00Z',
        },
        occurredAt: '2026-07-31T02:00:00Z',
      }],
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.instance-list .flow-detail').trigger('click')
    await flushPromises()

    const evidence = wrapper.get('.history-decision-evidence')
    expect(evidence.text()).toContain('证据 8801')
    expect(evidence.text()).toContain('分支 finance')
    expect(evidence.text()).toContain('evidence.png')
    expect(evidence.text()).toContain('SHA-256 abc123')
    expect(evidence.text()).toContain('签名 TYPED： 审批人')
    expect(evidence.text()).toContain('意见模板 #801 · 同意模板 · v2')
    expect(evidence.text()).toContain('代表成员 300')
    expect(evidence.text()).toContain('委托规则 701')
  })

  it('renders completion execution attempts and history, then retries a failed step', async () => {
    applySession('20', '200', ['flow.instance.read', 'flow.definition.manage'])
    const failedExecution = {
      executionId: '9902',
      instanceId: '101',
      definitionId: '201',
      definitionVersion: 3,
      ordinal: 1,
      code: 'notify',
      name: 'Notify downstream',
      type: 'WEBHOOK' as const,
      status: 'FAILED' as const,
      webhook: {
        url: 'https://hooks.example.com/approved',
        secretRef: '********' as const,
        secretConfigured: true,
        timeoutSeconds: 10,
        baseBackoffSeconds: 5,
      },
      attemptCount: 3,
      maxAttempts: 3,
      availableAt: null,
      leaseExpiresAt: null,
      createdAt: '2026-07-31T03:00:00Z',
      startedAt: '2026-07-31T03:00:01Z',
      completedAt: '2026-07-31T03:01:00Z',
      failure: {
        code: 'HTTP_400',
        message: 'downstream rejected request',
        retryable: false,
      },
      attempts: [{
        attemptNumber: 3,
        status: 'FAILED' as const,
        httpStatus: 400,
        durationMs: 120,
        responseSha256: 'response-sha',
        failureCode: 'HTTP_400',
        failureMessage: 'downstream rejected request',
        startedAt: '2026-07-31T03:00:50Z',
        completedAt: '2026-07-31T03:01:00Z',
      }],
    }
    const executingInstance: FlowInstance = {
      ...pendingTask,
      completionPhase: 'EXTERNAL_EXECUTION',
      activeCompletionOrdinal: 1,
      completionExecutions: [failedExecution],
    }
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([executingInstance]))
    vi.mocked(flowApi.instance).mockResolvedValue(executingInstance)
    vi.mocked(flowApi.history).mockResolvedValue({
      instanceId: '101',
      events: [{
        sequence: 1,
        type: 'COMPLETION_FAILED',
        actorId: '200',
        fromStatus: 'PENDING',
        toStatus: 'PENDING',
        comment: null,
        completionExecution: {
          executionId: '9902',
          ordinal: 1,
          code: 'notify',
          type: 'WEBHOOK',
          event: 'FAILED',
          status: 'FAILED',
          attemptNumber: 3,
          occurredAt: '2026-07-31T03:01:00Z',
          failureCode: 'HTTP_400',
        },
        occurredAt: '2026-07-31T03:01:00Z',
      }],
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.instance-list .flow-detail').trigger('click')
    await flushPromises()

    const execution = wrapper.get('.instance-completion-execution')
    expect(wrapper.get('.instance-completion-runtime').text()).toContain('EXTERNAL_EXECUTION')
    expect(execution.text()).toContain('Notify downstream')
    expect(execution.text()).toContain('Secret 已配置')
    expect(execution.text()).not.toContain('********')
    expect(execution.text()).toContain('尝试 3 · FAILED')
    expect(execution.text()).toContain('响应 SHA-256 response-sha')
    expect(wrapper.get('.history-completion-execution').text()).toContain('失败 HTTP_400')

    await wrapper.get('.instance-completion-retry').trigger('click')
    await flushPromises()
    expect(flowApi.retryCompletionExecution).toHaveBeenCalledWith('10', '101', '9902')
  })

  it('hides failed completion retry without definition-management permission', async () => {
    applySession('20', '200', ['flow.instance.read'])
    const readonlyInstance: FlowInstance = {
      ...pendingTask,
      completionPhase: 'EXTERNAL_EXECUTION',
      activeCompletionOrdinal: 0,
      completionExecutions: [{
        executionId: '9902',
        instanceId: '101',
        definitionId: '201',
        definitionVersion: 3,
        ordinal: 0,
        code: 'notify',
        name: 'Notify downstream',
        type: 'WEBHOOK',
        status: 'FAILED',
        webhook: {
          url: 'https://hooks.example.com/approved',
          secretRef: '********',
          secretConfigured: true,
          timeoutSeconds: 10,
          baseBackoffSeconds: 5,
        },
        attemptCount: 3,
        maxAttempts: 3,
        availableAt: null,
        createdAt: '2026-07-31T03:00:00Z',
        startedAt: '2026-07-31T03:00:01Z',
        completedAt: '2026-07-31T03:01:00Z',
        failure: {
          code: 'HTTP_400',
          message: 'downstream rejected request',
          retryable: false,
        },
        attempts: [],
      }],
    }
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([readonlyInstance]))
    vi.mocked(flowApi.instance).mockResolvedValue(readonlyInstance)
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.instance-list .flow-detail').trigger('click')
    await flushPromises()

    expect(wrapper.get('.instance-completion-execution').text()).toContain('FAILED')
    expect(wrapper.find('.instance-completion-retry').exists()).toBe(false)
  })

  it('renders sanitized SUBFLOW child runs, reuses failed retry and navigates to child detail', async () => {
    applySession('20', '200', ['flow.instance.read', 'flow.definition.manage'])
    const childRun = {
      attempt: 2,
      childInstanceId: '102',
      targetDefinitionId: '501',
      targetVersion: 7,
      childStatus: 'REJECTED' as const,
      launchedAt: '2026-08-01T01:00:00Z',
      terminalAt: '2026-08-01T01:05:00Z',
    }
    Object.assign(childRun, {
      outboxId: 'must-not-render-outbox',
      lockVersion: 'must-not-render-lock',
      launchKey: 'must-not-render-launch-key',
    })
    const subflowExecution = {
      executionId: '9903',
      instanceId: '101',
      definitionId: '201',
      definitionVersion: 3,
      ordinal: 0,
      code: 'child_review',
      name: 'Child review',
      type: 'SUBFLOW' as const,
      status: 'FAILED' as const,
      subflow: { definitionId: '501', version: 7 },
      attemptCount: 2,
      maxAttempts: 10,
      availableAt: null,
      createdAt: '2026-08-01T00:59:00Z',
      startedAt: '2026-08-01T01:00:00Z',
      completedAt: '2026-08-01T01:05:00Z',
      failure: {
        code: 'CHILD_REJECTED',
        message: 'child approval was rejected',
        retryable: false,
      },
      attempts: [],
      subflowRuns: [childRun],
    }
    const parentInstance: FlowInstance = {
      ...pendingTask,
      completionPhase: 'EXTERNAL_EXECUTION',
      activeCompletionOrdinal: 0,
      completionExecutions: [subflowExecution],
    }
    const childInstance: FlowInstance = {
      ...pendingTask,
      instanceId: '102',
      definitionId: '501',
      definitionVersion: 7,
      businessKey: 'child:contract:301',
      status: 'REJECTED',
      completedAt: '2026-08-01T01:05:00Z',
    }
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([parentInstance]))
    vi.mocked(flowApi.instance).mockImplementation(async (_systemId, instanceId) => (
      instanceId === '102' ? childInstance : parentInstance
    ))
    vi.mocked(flowApi.history).mockImplementation(async (_systemId, instanceId) => ({
      instanceId,
      events: instanceId === '101'
        ? [{
            sequence: 1,
            type: 'COMPLETION_FAILED',
            actorId: '200',
            fromStatus: 'PENDING',
            toStatus: 'PENDING',
            comment: null,
            completionExecution: {
              executionId: '9903',
              ordinal: 0,
              code: 'child_review',
              type: 'SUBFLOW',
              event: 'FAILED',
              status: 'FAILED',
              attemptNumber: 2,
              occurredAt: '2026-08-01T01:05:00Z',
              failureCode: 'CHILD_REJECTED',
              subflowRun: childRun,
            },
            occurredAt: '2026-08-01T01:05:00Z',
          }]
        : [],
    }))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.instance-list .flow-detail').trigger('click')
    await flushPromises()

    expect(wrapper.get('.instance-completion-subflow-target').text())
      .toContain('定义 501 / v7')
    expect(wrapper.get('.instance-completion-subflow-runs').text())
      .toContain('子实例 102')
    expect(wrapper.get('.history-completion-subflow-run').text())
      .toContain('REJECTED')
    expect(wrapper.get('.history-completion-execution').text())
      .toContain('CHILD_REJECTED')
    expect(wrapper.text()).not.toContain('must-not-render-outbox')
    expect(wrapper.text()).not.toContain('must-not-render-lock')
    expect(wrapper.text()).not.toContain('must-not-render-launch-key')

    await wrapper.get('.instance-completion-retry').trigger('click')
    await flushPromises()
    expect(flowApi.retryCompletionExecution).toHaveBeenCalledWith('10', '101', '9903')

    await wrapper.get('.instance-completion-child-link').trigger('click')
    await flushPromises()
    expect(flowApi.instance).toHaveBeenCalledWith('10', '102')
    expect(wrapper.get('.detail-list').text()).toContain('102')
    expect(wrapper.get('.detail-list').text()).toContain('child:contract:301')
  })

  it('renders parallel completion member states, active ordinals, join history and legacy cursor fallback', async () => {
    applySession('20', '200', ['flow.instance.read', 'flow.definition.manage'])
    const executionBase = {
      instanceId: '101',
      definitionId: '201',
      definitionVersion: 4,
      attemptCount: 1,
      maxAttempts: 3,
      availableAt: '2026-08-02T01:00:00Z',
      createdAt: '2026-08-02T01:00:00Z',
      startedAt: '2026-08-02T01:00:01Z',
      completedAt: null,
      attempts: [],
    }
    const completionExecutions: FlowInstance['completionExecutions'] = [
      {
        ...executionBase,
        executionId: '9910',
        ordinal: 0,
        code: 'archive',
        name: 'Archive',
        type: 'EXTERNAL_TASK',
        status: 'SUCCEEDED',
        parallelGroup: 'prepare',
        externalTask: {
          topic: 'contract.archive',
          leaseSeconds: 60,
          resultJsonLimitBytes: 8192,
        },
        completedAt: '2026-08-02T01:01:00Z',
      },
      {
        ...executionBase,
        executionId: '9911',
        ordinal: 1,
        code: 'audit',
        name: 'Audit',
        type: 'WEBHOOK',
        status: 'SUCCEEDED',
        parallelGroup: 'prepare',
        webhook: {
          url: 'https://hooks.example.com/audit',
          secretRef: null,
          secretConfigured: false,
          timeoutSeconds: 10,
          baseBackoffSeconds: 5,
        },
        completedAt: '2026-08-02T01:01:01Z',
      },
      {
        ...executionBase,
        executionId: '9912',
        ordinal: 2,
        code: 'notify',
        name: 'Notify',
        type: 'EXTERNAL_TASK',
        status: 'AVAILABLE',
        parallelGroup: 'dispatch',
        externalTask: {
          topic: 'contract.notify',
          leaseSeconds: 60,
          resultJsonLimitBytes: 8192,
        },
        startedAt: null,
      },
      {
        ...executionBase,
        executionId: '9913',
        ordinal: 3,
        code: 'child_review',
        name: 'Child review',
        type: 'SUBFLOW',
        status: 'RUNNING',
        parallelGroup: 'dispatch',
        subflow: { definitionId: '501', version: 7 },
        subflowRuns: [{
          attempt: 1,
          childInstanceId: '102',
          targetDefinitionId: '501',
          targetVersion: 7,
          childStatus: 'RUNNING',
          launchedAt: '2026-08-02T01:01:02Z',
          terminalAt: null,
        }],
      },
    ]
    const groupedInstance: FlowInstance = {
      ...pendingTask,
      definitionVersion: 4,
      completionPhase: 'EXTERNAL_EXECUTION',
      activeCompletionOrdinal: 2,
      activeCompletionOrdinals: [2, 3],
      completionExecutions,
    }
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([groupedInstance]))
    vi.mocked(flowApi.instance).mockResolvedValue(groupedInstance)
    vi.mocked(flowApi.history).mockResolvedValue({
      instanceId: '101',
      events: [{
        sequence: 1,
        type: 'COMPLETION_SUCCEEDED',
        actorId: '200',
        fromStatus: 'PENDING',
        toStatus: 'PENDING',
        comment: null,
        completionExecution: {
          executionId: '9910',
          ordinal: 0,
          code: 'archive',
          type: 'EXTERNAL_TASK',
          event: 'SUCCEEDED',
          status: 'SUCCEEDED',
          attemptNumber: 1,
          occurredAt: '2026-08-02T01:01:00Z',
          parallelGroup: 'prepare',
        },
        occurredAt: '2026-08-02T01:01:00Z',
      }, {
        sequence: 2,
        type: 'COMPLETION_STAGE_JOINED',
        actorId: '200',
        fromStatus: 'PENDING',
        toStatus: 'PENDING',
        comment: null,
        completionExecution: {
          executionId: '9910',
          ordinal: 0,
          code: 'archive',
          type: 'EXTERNAL_TASK',
          event: 'STAGE_JOINED',
          status: 'SUCCEEDED',
          attemptNumber: 1,
          parallelGroup: 'prepare',
          occurredAt: '2026-08-02T01:01:01Z',
        },
        occurredAt: '2026-08-02T01:01:01Z',
      }],
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.instance-list .flow-detail').trigger('click')
    await flushPromises()

    expect(wrapper.get('.instance-completion-runtime').text())
      .toContain('活跃顺序 3、4 · 兼容游标 3')
    const stages = wrapper.findAll('.instance-completion-stage')
    expect(stages).toHaveLength(2)
    expect(stages[0]!.text()).toContain('并行组 prepare')
    expect(stages[0]!.text()).toContain('Archive：SUCCEEDED')
    expect(stages[1]!.text()).toContain('Notify：AVAILABLE')
    expect(stages[1]!.text()).toContain('Child review：RUNNING')
    const activeMembers = stages[1]!.findAll('.instance-completion-stage-member')
    expect(activeMembers).toHaveLength(2)
    expect(activeMembers.filter(member => member.text().includes('（活跃）')))
      .toHaveLength(2)
    expect(wrapper.get('.history-completion-execution').text())
      .toContain('并行组 prepare')
    expect(wrapper.findAll('.history-completion-execution')[1]!.text())
      .toContain('STAGE_JOINED → SUCCEEDED')

    wrapper.unmount()
    const legacyInstance: FlowInstance = {
      ...groupedInstance,
      activeCompletionOrdinal: 2,
      activeCompletionOrdinals: undefined,
    }
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([legacyInstance]))
    vi.mocked(flowApi.instance).mockResolvedValue(legacyInstance)
    const legacy = render()
    await flushPromises()
    await legacy.get('.instance-list .flow-detail').trigger('click')
    await flushPromises()
    expect(legacy.get('.instance-completion-runtime').text())
      .toContain('活跃顺序 3 · 兼容游标 3')
  })

  it('renders reverse compensation progress, retries failures and opens compensation children', async () => {
    applySession('20', '200', ['flow.instance.read', 'flow.definition.manage'])
    const compensationBase = {
      originalExecutionId: '9910',
      instanceId: '101',
      definitionId: '201',
      definitionVersion: 1,
      attemptCount: 1,
      maxAttempts: 3,
      availableAt: '2026-08-03T01:00:00Z',
      createdAt: '2026-08-03T01:00:00Z',
      startedAt: '2026-08-03T01:00:01Z',
      completedAt: null,
      attempts: [],
    }
    const compensatingInstance: FlowInstance = {
      ...pendingTask,
      completionPhase: 'COMPENSATING',
      completionFailurePolicy: 'COMPENSATE',
      completionExecutions: [{
        executionId: '9913',
        instanceId: '101',
        definitionId: '201',
        definitionVersion: 1,
        ordinal: 3,
        code: 'failed_forward',
        name: 'Failed forward member',
        type: 'EXTERNAL_TASK',
        status: 'FAILED',
        externalTask: {
          topic: 'contract.failed',
          leaseSeconds: 60,
          resultJsonLimitBytes: 8192,
        },
        attemptCount: 3,
        maxAttempts: 3,
        availableAt: null,
        createdAt: '2026-08-03T00:59:00Z',
        startedAt: '2026-08-03T00:59:01Z',
        completedAt: '2026-08-03T01:00:00Z',
        attempts: [],
      }],
      compensationExecutions: [{
        ...compensationBase,
        compensationExecutionId: '9951',
        originalOrdinal: 0,
        type: 'EXTERNAL_TASK',
        status: 'FAILED',
        externalTask: {
          topic: 'contract.archive.undo',
          leaseSeconds: 60,
          resultJsonLimitBytes: 8192,
        },
        failure: {
          code: 'UNDO_FAILED',
          message: 'archive restore failed',
          retryable: true,
        },
      }, {
        ...compensationBase,
        compensationExecutionId: '9953',
        originalExecutionId: '9912',
        originalOrdinal: 2,
        type: 'WEBHOOK',
        status: 'SUCCEEDED',
        webhook: {
          url: 'https://hooks.example.com/undo',
          secretRef: '********',
          secretConfigured: true,
          timeoutSeconds: 10,
          baseBackoffSeconds: 5,
        },
        completedAt: '2026-08-03T01:01:00Z',
      }, {
        ...compensationBase,
        compensationExecutionId: '9952',
        originalExecutionId: '9911',
        originalOrdinal: 1,
        type: 'SUBFLOW',
        status: 'RUNNING',
        subflow: { definitionId: '601', version: 9 },
        subflowRuns: [{
          attempt: 1,
          childInstanceId: '103',
          targetDefinitionId: '601',
          targetVersion: 9,
          childStatus: 'RUNNING',
          launchedAt: '2026-08-03T01:00:02Z',
          terminalAt: null,
        }],
      }],
    }
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([compensatingInstance]))
    vi.mocked(flowApi.instance).mockResolvedValue(compensatingInstance)
    vi.mocked(flowApi.history).mockResolvedValue({
      instanceId: '101',
      events: [{
        sequence: 1,
        type: 'COMPLETION_COMPENSATION',
        actorId: null,
        fromStatus: null,
        toStatus: 'FAILED',
        comment: null,
        compensationExecution: {
          compensationExecutionId: '9951',
          originalExecutionId: '9910',
          originalOrdinal: 0,
          type: 'EXTERNAL_TASK',
          event: 'FAILED',
          status: 'FAILED',
          attemptNumber: 1,
          failureCode: 'UNDO_FAILED',
          occurredAt: '2026-08-03T01:01:00Z',
        },
        occurredAt: '2026-08-03T01:01:00Z',
      }, {
        sequence: 2,
        type: 'COMPLETION_COMPENSATION',
        actorId: null,
        fromStatus: null,
        toStatus: 'RUNNING',
        comment: null,
        compensationExecution: {
          compensationExecutionId: '9952',
          originalExecutionId: '9911',
          originalOrdinal: 1,
          type: 'SUBFLOW',
          event: 'CHILD_LAUNCHED',
          status: 'RUNNING',
          attemptNumber: 1,
          occurredAt: '2026-08-03T01:00:02Z',
          subflowRun: {
            attempt: 1,
            childInstanceId: '103',
            targetDefinitionId: '601',
            targetVersion: 9,
            childStatus: 'RUNNING',
            launchedAt: '2026-08-03T01:00:02Z',
            terminalAt: null,
          },
        },
        occurredAt: '2026-08-03T01:00:02Z',
      }],
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.instance-list .flow-detail').trigger('click')
    await flushPromises()

    expect(wrapper.get('.instance-completion-runtime').text())
      .toContain('阶段 COMPENSATING')
    const members = wrapper.findAll('.instance-compensation-execution')
    expect(members).toHaveLength(3)
    expect(members.map(member => member.text().match(/原步骤 \d/)?.[0]))
      .toEqual(['原步骤 3', '原步骤 2', '原步骤 1'])
    expect(members.map(member => member.text())).toEqual([
      expect.stringContaining('WEBHOOK · SUCCEEDED'),
      expect.stringContaining('SUBFLOW · RUNNING'),
      expect.stringContaining('EXTERNAL_TASK · FAILED'),
    ])
    expect(wrapper.text()).not.toContain('vault://')
    expect(wrapper.get('.history-compensation-execution').text())
      .toContain('FAILED → FAILED')
    expect(wrapper.get('.history-compensation-subflow-run').text())
      .toContain('子实例 103')
    expect(wrapper.find('.instance-completion-retry').exists()).toBe(false)

    await wrapper.get('.instance-compensation-retry').trigger('click')
    await flushPromises()
    expect(flowApi.retryCompensationExecution).toHaveBeenCalledWith('10', '101', '9951')

    vi.mocked(flowApi.instance).mockResolvedValueOnce({
      ...pendingTask,
      instanceId: '103',
      businessKey: 'compensation:child:103',
    })
    await wrapper.get('.instance-compensation-child-link').trigger('click')
    await flushPromises()
    expect(flowApi.instance).toHaveBeenCalledWith('10', '103')
    expect(wrapper.get('.detail-list').text()).toContain('103')
  })

  it('reloads both the personal queue and general instances after rejection', async () => {
    let rejected = false
    vi.mocked(flowApi.listApprovalTasks).mockImplementation(async () =>
      rejected ? taskPage([]) : taskPage([pendingTask]))
    vi.mocked(flowApi.reject).mockImplementation(async () => {
      rejected = true
      return {
        ...pendingTask,
        status: 'REJECTED',
        completedAt: '2026-07-27T01:01:00Z',
      }
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.task-reject').trigger('click')
    await wrapper.get('.decision-comment').setValue('needs changes')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.reject).toHaveBeenCalledWith('10', '101', 'needs changes')
    expect(flowApi.listApprovalTasks).toHaveBeenCalledTimes(2)
    expect(flowApi.listInstances).toHaveBeenCalledTimes(2)
  })

  it('only exposes requester withdrawal for a pending instance with permission', async () => {
    applySession('20', '100', ['flow.instance.read', 'flow.instance.withdraw'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    const requester = render()
    await flushPromises()
    expect(requester.find('.flow-withdraw').exists()).toBe(true)
    requester.unmount()

    applySession('20', '200', ['flow.instance.read', 'flow.instance.withdraw'])
    const otherMember = render()
    await flushPromises()
    expect(otherMember.find('.flow-withdraw').exists()).toBe(false)
    otherMember.unmount()

    applySession('20', '100', ['flow.instance.read'])
    const noPermission = render()
    await flushPromises()
    expect(noPermission.find('.flow-withdraw').exists()).toBe(false)
    noPermission.unmount()

    applySession('20', '100', ['flow.instance.read', 'flow.instance.withdraw'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([{
      ...pendingTask,
      status: 'APPROVED',
      completedAt: '2026-07-27T01:01:00Z',
    }]))
    const completed = render()
    await flushPromises()
    expect(completed.find('.flow-withdraw').exists()).toBe(false)
  })

  it('preserves the withdrawal reason and dialog when the request fails', async () => {
    applySession('20', '100', ['flow.instance.read', 'flow.instance.withdraw'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    vi.mocked(flowApi.withdraw).mockRejectedValue(new Error('state changed'))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-withdraw').trigger('click')
    await wrapper.get('.withdraw-reason').setValue('request details changed')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.withdraw).toHaveBeenCalledWith('10', '101', {
      reason: 'request details changed',
    })
    expect(wrapper.find('.withdraw-reason').exists()).toBe(true)
    expect((wrapper.get('.withdraw-reason').element as HTMLTextAreaElement).value)
      .toBe('request details changed')
    expect(wrapper.text()).toContain('state changed')
  })

  it('refreshes instance, history and task projections after requester withdrawal', async () => {
    applySession('20', '100', ['flow.instance.read', 'flow.instance.withdraw'])
    let withdrawn = false
    vi.mocked(flowApi.listInstances).mockImplementation(async () =>
      instancePage([{
        ...pendingTask,
        ...(withdrawn
          ? { status: 'WITHDRAWN' as const, completedAt: '2026-07-27T01:01:00Z' }
          : {}),
      }]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    vi.mocked(flowApi.withdraw).mockImplementation(async () => {
      withdrawn = true
      return {
        ...pendingTask,
        status: 'WITHDRAWN',
        completedAt: '2026-07-27T01:01:00Z',
      }
    })
    vi.mocked(flowApi.instance).mockImplementation(async () => ({
      ...pendingTask,
      status: withdrawn ? 'WITHDRAWN' : 'PENDING',
      completedAt: withdrawn ? '2026-07-27T01:01:00Z' : null,
    }))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-withdraw').trigger('click')
    await wrapper.get('.withdraw-reason').setValue('submitted by mistake')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.withdraw).toHaveBeenCalledWith('10', '101', {
      reason: 'submitted by mistake',
    })
    expect(flowApi.listInstances).toHaveBeenCalledTimes(2)
    expect(flowApi.listApprovalTasks).toHaveBeenCalledTimes(2)
    expect(flowApi.instance).toHaveBeenCalledWith('10', '101')
    expect(flowApi.history).toHaveBeenCalledWith('10', '101')
    expect(wrapper.find('.withdraw-reason').exists()).toBe(false)
    expect(wrapper.text()).toContain('已撤回')
  })

  it('only exposes operator termination for a pending instance with permission', async () => {
    applySession('20', '300', ['flow.instance.read', 'flow.instance.terminate'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    const operator = render()
    await flushPromises()
    expect(operator.find('.flow-terminate').exists()).toBe(true)
    operator.unmount()

    applySession('20', '300', ['flow.instance.read'])
    const noPermission = render()
    await flushPromises()
    expect(noPermission.find('.flow-terminate').exists()).toBe(false)
    noPermission.unmount()

    applySession('20', '300', ['flow.instance.read', 'flow.instance.terminate'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([{
      ...pendingTask,
      status: 'WITHDRAWN',
      completedAt: '2026-07-27T01:01:00Z',
    }]))
    const terminal = render()
    await flushPromises()
    expect(terminal.find('.flow-terminate').exists()).toBe(false)
  })

  it('preserves the termination reason and dialog when the request fails', async () => {
    applySession('20', '300', ['flow.instance.read', 'flow.instance.terminate'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    vi.mocked(flowApi.terminate).mockRejectedValue(new Error('state changed'))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-terminate').trigger('click')
    await wrapper.get('.terminate-reason').setValue('duplicate request')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.terminate).toHaveBeenCalledWith('10', '101', {
      reason: 'duplicate request',
    })
    expect(wrapper.find('.terminate-reason').exists()).toBe(true)
    expect((wrapper.get('.terminate-reason').element as HTMLTextAreaElement).value)
      .toBe('duplicate request')
    expect(wrapper.text()).toContain('state changed')
  })

  it('refreshes instance, history and task projections after operator termination', async () => {
    applySession('20', '300', ['flow.instance.read', 'flow.instance.terminate'])
    let terminated = false
    vi.mocked(flowApi.listInstances).mockImplementation(async () =>
      instancePage([{
        ...pendingTask,
        ...(terminated
          ? { status: 'TERMINATED' as const, completedAt: '2026-07-27T01:01:00Z' }
          : {}),
      }]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    vi.mocked(flowApi.terminate).mockImplementation(async () => {
      terminated = true
      return {
        ...pendingTask,
        status: 'TERMINATED',
        completedAt: '2026-07-27T01:01:00Z',
      }
    })
    vi.mocked(flowApi.instance).mockImplementation(async () => ({
      ...pendingTask,
      status: terminated ? 'TERMINATED' : 'PENDING',
      completedAt: terminated ? '2026-07-27T01:01:00Z' : null,
    }))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-terminate').trigger('click')
    await wrapper.get('.terminate-reason').setValue('duplicate request')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.terminate).toHaveBeenCalledWith('10', '101', {
      reason: 'duplicate request',
    })
    expect(flowApi.listInstances).toHaveBeenCalledTimes(2)
    expect(flowApi.listApprovalTasks).toHaveBeenCalledTimes(2)
    expect(flowApi.instance).toHaveBeenCalledWith('10', '101')
    expect(flowApi.history).toHaveBeenCalledWith('10', '101')
    expect(wrapper.find('.terminate-reason').exists()).toBe(false)
    expect(wrapper.text()).toContain('已终止')
  })

  it('only exposes requester urge for a pending instance with permission', async () => {
    applySession('20', '100', ['flow.instance.read', 'flow.instance.urge'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    const requester = render()
    await flushPromises()
    expect(requester.find('.flow-urge').exists()).toBe(true)
    requester.unmount()

    applySession('20', '200', ['flow.instance.read', 'flow.instance.urge'])
    const otherMember = render()
    await flushPromises()
    expect(otherMember.find('.flow-urge').exists()).toBe(false)
    otherMember.unmount()

    applySession('20', '100', ['flow.instance.read'])
    const noPermission = render()
    await flushPromises()
    expect(noPermission.find('.flow-urge').exists()).toBe(false)
    noPermission.unmount()

    applySession('20', '100', ['flow.instance.read', 'flow.instance.urge'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([{
      ...pendingTask,
      status: 'TERMINATED',
      completedAt: '2026-07-27T01:04:00Z',
    }]))
    const terminal = render()
    await flushPromises()
    expect(terminal.find('.flow-urge').exists()).toBe(false)
  })

  it('preserves the optional urge message when the request fails', async () => {
    applySession('20', '100', ['flow.instance.read', 'flow.instance.urge'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    vi.mocked(flowApi.urge).mockRejectedValue(new Error('delivery failed'))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-urge').trigger('click')
    await wrapper.get('.urge-message').setValue('Please review before noon')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.urge).toHaveBeenCalledWith('10', '101', {
      message: 'Please review before noon',
    })
    expect(wrapper.find('.urge-message').exists()).toBe(true)
    expect((wrapper.get('.urge-message').element as HTMLTextAreaElement).value)
      .toBe('Please review before noon')
    expect(wrapper.text()).toContain('delivery failed')
  })

  it('accepts an empty urge message and refreshes the urge timeline after success', async () => {
    applySession('20', '100', ['flow.instance.read', 'flow.instance.urge'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-urge').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.urge).toHaveBeenCalledWith('10', '101', { message: '' })
    expect(flowApi.listUrges).toHaveBeenCalledWith('10', '101', 1, 20)
    expect(wrapper.find('.urge-message').exists()).toBe(false)
  })

  it('loads ordered urge and comment timelines and changes each detail page', async () => {
    applySession('20', '300', ['flow.instance.read', 'flow.instance.comment'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    vi.mocked(flowApi.listUrges)
      .mockResolvedValueOnce(urgePage([
        {
          urgeId: '501',
          instanceId: '101',
          actorId: '100',
          recipientId: '200',
          message: 'first urge',
          createdAt: '2026-07-27T01:02:00Z',
        },
        {
          urgeId: '502',
          instanceId: '101',
          actorId: '100',
          recipientId: '200',
          message: 'second urge',
          createdAt: '2026-07-27T01:03:00Z',
        },
      ], 1, 21))
      .mockResolvedValueOnce(urgePage([], 2, 21))
    vi.mocked(flowApi.listComments)
      .mockResolvedValueOnce(commentPage([
        {
          commentId: '601',
          instanceId: '101',
          authorId: '300',
          body: 'first comment',
          createdAt: '2026-07-27T01:04:00Z',
        },
        {
          commentId: '602',
          instanceId: '101',
          authorId: '301',
          body: 'second comment',
          createdAt: '2026-07-27T01:05:00Z',
        },
      ], 1, 21))
      .mockResolvedValueOnce(commentPage([], 2, 21))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.instance-list .flow-detail').trigger('click')
    await flushPromises()

    expect(flowApi.listUrges).toHaveBeenCalledWith('10', '101', 1, 20)
    expect(flowApi.listComments).toHaveBeenCalledWith('10', '101', 1, 20)
    const urgeText = wrapper.get('.urge-timeline').text()
    const commentText = wrapper.get('.comment-timeline').text()
    expect(urgeText.indexOf('first urge')).toBeLessThan(urgeText.indexOf('second urge'))
    expect(commentText.indexOf('first comment')).toBeLessThan(commentText.indexOf('second comment'))

    await wrapper.get('.urge-pagination').trigger('click')
    await flushPromises()
    expect(flowApi.listUrges).toHaveBeenLastCalledWith('10', '101', 2, 20)
    await wrapper.get('.comment-pagination').trigger('click')
    await flushPromises()
    expect(flowApi.listComments).toHaveBeenLastCalledWith('10', '101', 2, 20)
  })

  it('requires comment text and preserves the draft when append fails', async () => {
    applySession('20', '300', ['flow.instance.read', 'flow.instance.comment'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    vi.mocked(flowApi.createComment).mockRejectedValue(new Error('comment failed'))
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.instance-list .flow-detail').trigger('click')
    await flushPromises()

    await wrapper.get('.flow-comment-submit').trigger('click')
    expect(flowApi.createComment).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('评论内容长度必须为 1–2000 个字符。')

    await wrapper.get('.flow-comment-body').setValue('Supporting documents checked')
    await wrapper.get('.flow-comment-submit').trigger('click')
    await flushPromises()

    expect(flowApi.createComment).toHaveBeenCalledWith('10', '101', {
      body: 'Supporting documents checked',
    })
    expect((wrapper.get('.flow-comment-body').element as HTMLTextAreaElement).value)
      .toBe('Supporting documents checked')
    expect(wrapper.text()).toContain('comment failed')
  })

  it('clears the comment draft and refreshes comments after append succeeds', async () => {
    applySession('20', '300', ['flow.instance.read', 'flow.instance.comment'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    vi.mocked(flowApi.listComments)
      .mockResolvedValueOnce(commentPage())
      .mockResolvedValueOnce(commentPage([{
        commentId: '601',
        instanceId: '101',
        authorId: '300',
        body: 'Documents checked',
        createdAt: '2026-07-27T01:03:00Z',
      }]))
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.instance-list .flow-detail').trigger('click')
    await flushPromises()

    await wrapper.get('.flow-comment-body').setValue('Documents checked')
    await wrapper.get('.flow-comment-submit').trigger('click')
    await flushPromises()

    expect(flowApi.createComment).toHaveBeenCalledWith('10', '101', {
      body: 'Documents checked',
    })
    expect(flowApi.listComments).toHaveBeenCalledTimes(2)
    expect((wrapper.get('.flow-comment-body').element as HTMLTextAreaElement).value).toBe('')
    expect(wrapper.get('.comment-timeline').text()).toContain('Documents checked')
  })

  it('only exposes transfer and add-sign to the current pending approver with permission', async () => {
    applySession('20', '200', [
      'flow.instance.read',
      'flow.instance.transfer',
      'flow.instance.add-sign',
    ])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    const currentApprover = render()
    await flushPromises()
    expect(currentApprover.find('.flow-transfer').exists()).toBe(true)
    expect(currentApprover.find('.flow-add-sign').exists()).toBe(true)
    currentApprover.unmount()

    applySession('20', '300', [
      'flow.instance.read',
      'flow.instance.transfer',
      'flow.instance.add-sign',
    ])
    const otherMember = render()
    await flushPromises()
    expect(otherMember.find('.flow-transfer').exists()).toBe(false)
    expect(otherMember.find('.flow-add-sign').exists()).toBe(false)
    otherMember.unmount()

    applySession('20', '200', ['flow.instance.read'])
    const noPermission = render()
    await flushPromises()
    expect(noPermission.find('.flow-transfer').exists()).toBe(false)
    expect(noPermission.find('.flow-add-sign').exists()).toBe(false)
    noPermission.unmount()

    applySession('20', '200', [
      'flow.instance.read',
      'flow.instance.transfer',
      'flow.instance.add-sign',
    ])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([{
      ...pendingTask,
      status: 'APPROVED',
      completedAt: '2026-07-27T01:06:00Z',
    }]))
    const terminal = render()
    await flushPromises()
    expect(terminal.find('.flow-transfer').exists()).toBe(false)
    expect(terminal.find('.flow-add-sign').exists()).toBe(false)
  })

  it('preserves the transfer member and reason when the request fails', async () => {
    applySession('20', '200', ['flow.instance.read', 'flow.instance.transfer'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    vi.mocked(flowApi.transfer).mockRejectedValue(new Error('assignment changed'))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-transfer').trigger('click')
    await wrapper.get('.select-assignment-member').trigger('click')
    await wrapper.get('.transfer-reason').setValue('Route to owning reviewer')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.transfer).toHaveBeenCalledWith('10', '101', {
      targetMemberId: '300',
      reason: 'Route to owning reviewer',
    })
    expect(wrapper.get('.select-assignment-member').text()).toBe('300')
    expect((wrapper.get('.transfer-reason').element as HTMLTextAreaElement).value)
      .toBe('Route to owning reviewer')
    expect(wrapper.text()).toContain('assignment changed')
  })

  it('closes transfer and refreshes instance, tasks and history after success', async () => {
    applySession('20', '200', ['flow.instance.read', 'flow.instance.transfer'])
    let transferred = false
    vi.mocked(flowApi.listInstances).mockImplementation(async () =>
      instancePage([{ ...pendingTask, ...(transferred ? { approverId: '300' } : {}) }]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    vi.mocked(flowApi.transfer).mockImplementation(async () => {
      transferred = true
      return { ...pendingTask, approverId: '300', approverIds: ['300'] }
    })
    vi.mocked(flowApi.instance).mockImplementation(async () => ({
      ...pendingTask,
      approverId: transferred ? '300' : '200',
    }))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-transfer').trigger('click')
    await wrapper.get('.select-assignment-member').trigger('click')
    await wrapper.get('.transfer-reason').setValue('Route to owning reviewer')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.listInstances).toHaveBeenCalledTimes(2)
    expect(flowApi.listApprovalTasks).toHaveBeenCalledTimes(2)
    expect(flowApi.instance).toHaveBeenCalledWith('10', '101')
    expect(flowApi.history).toHaveBeenCalledWith('10', '101')
    expect(wrapper.find('.transfer-reason').exists()).toBe(false)
    expect(wrapper.text()).toContain('审批人 300')
  })

  it('preserves add-sign member, position and reason when the request fails', async () => {
    applySession('20', '200', ['flow.instance.read', 'flow.instance.add-sign'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    vi.mocked(flowApi.addSign).mockRejectedValue(new Error('sequence changed'))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-add-sign').trigger('click')
    await wrapper.get('.select-assignment-member').trigger('click')
    await wrapper.get('.add-sign-position').setValue('AFTER')
    await wrapper.get('.add-sign-reason').setValue('Security review next')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.addSign).toHaveBeenCalledWith('10', '101', {
      targetMemberId: '300',
      position: 'AFTER',
      reason: 'Security review next',
    })
    expect(wrapper.get('.select-assignment-member').text()).toBe('300')
    expect((wrapper.get('.add-sign-position').element as HTMLSelectElement).value).toBe('AFTER')
    expect((wrapper.get('.add-sign-reason').element as HTMLTextAreaElement).value)
      .toBe('Security review next')
    expect(wrapper.text()).toContain('sequence changed')
  })

  it('closes add-sign and refreshes instance, tasks and history after success', async () => {
    applySession('20', '200', ['flow.instance.read', 'flow.instance.add-sign'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-add-sign').trigger('click')
    await wrapper.get('.select-assignment-member').trigger('click')
    await wrapper.get('.add-sign-position').setValue('BEFORE')
    await wrapper.get('.add-sign-reason').setValue('Security review first')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.addSign).toHaveBeenCalledWith('10', '101', {
      targetMemberId: '300',
      position: 'BEFORE',
      reason: 'Security review first',
    })
    expect(flowApi.listInstances).toHaveBeenCalledTimes(2)
    expect(flowApi.listApprovalTasks).toHaveBeenCalledTimes(2)
    expect(flowApi.instance).toHaveBeenCalledWith('10', '101')
    expect(flowApi.history).toHaveBeenCalledWith('10', '101')
    expect(wrapper.find('.add-sign-reason').exists()).toBe(false)
  })

  it('only exposes reduce-sign to a current claimed approver and lists future runtime steps', async () => {
    applySession('20', '200', ['flow.instance.read', 'flow.instance.reduce-sign'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    const current = render()
    await flushPromises()

    expect(current.find('.flow-reduce-sign').exists()).toBe(true)
    await current.get('.flow-reduce-sign').trigger('click')
    const options = current.findAll('.reduce-sign-target option')
    expect(options.map((option) => option.attributes('value'))).toEqual(['2', '3'])
    expect(options.map((option) => option.text())).toEqual([
      '步骤 3（索引 2）· 成员 300',
      '步骤 4（索引 3）· 成员 400',
    ])
    current.unmount()

    applySession('20', '300', ['flow.instance.read', 'flow.instance.reduce-sign'])
    const otherMember = render()
    await flushPromises()
    expect(otherMember.find('.flow-reduce-sign').exists()).toBe(false)
    otherMember.unmount()

    applySession('20', '200', ['flow.instance.read', 'flow.instance.reduce-sign'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([{
      ...pendingTask,
      claimState: 'OPEN',
    }]))
    const open = render()
    await flushPromises()
    expect(open.find('.flow-reduce-sign').exists()).toBe(false)
    open.unmount()

    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([{
      ...pendingTask,
      status: 'APPROVED',
      completedAt: '2026-07-27T01:06:00Z',
    }]))
    const terminal = render()
    await flushPromises()
    expect(terminal.find('.flow-reduce-sign').exists()).toBe(false)
  })

  it('preserves the reduce-sign target step and reason when the request fails', async () => {
    applySession('20', '200', ['flow.instance.read', 'flow.instance.reduce-sign'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    vi.mocked(flowApi.reduceSign).mockRejectedValue(new Error('sequence changed'))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-reduce-sign').trigger('click')
    await wrapper.get('.reduce-sign-target').setValue('3')
    await wrapper.get('.reduce-sign-reason').setValue('Remove duplicate legal review')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.reduceSign).toHaveBeenCalledWith('10', '101', {
      targetStepIndex: 3,
      reason: 'Remove duplicate legal review',
    })
    expect((wrapper.get('.reduce-sign-target').element as HTMLSelectElement).value).toBe('3')
    expect((wrapper.get('.reduce-sign-reason').element as HTMLTextAreaElement).value)
      .toBe('Remove duplicate legal review')
    expect(wrapper.text()).toContain('sequence changed')
  })

  it('closes reduce-sign and refreshes instance, tasks and history after success', async () => {
    applySession('20', '200', ['flow.instance.read', 'flow.instance.reduce-sign'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-reduce-sign').trigger('click')
    await wrapper.get('.reduce-sign-target').setValue('2')
    await wrapper.get('.reduce-sign-reason').setValue('Finance review is no longer required')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.reduceSign).toHaveBeenCalledWith('10', '101', {
      targetStepIndex: 2,
      reason: 'Finance review is no longer required',
    })
    expect(flowApi.listInstances).toHaveBeenCalledTimes(2)
    expect(flowApi.listApprovalTasks).toHaveBeenCalledTimes(2)
    expect(flowApi.instance).toHaveBeenCalledWith('10', '101')
    expect(flowApi.history).toHaveBeenCalledWith('10', '101')
    expect(wrapper.find('.reduce-sign-reason').exists()).toBe(false)
  })

  it('loads the copy timeline and allows copy from pending and terminal detail', async () => {
    applySession('20', '200', ['flow.instance.read', 'flow.instance.copy'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    vi.mocked(flowApi.listCopies).mockResolvedValue(copyPage([{
      copyId: '701',
      instanceId: '101',
      actorId: '100',
      recipientId: '300',
      message: 'Please follow the outcome',
      createdAt: '2026-07-27T01:04:00Z',
    }]))
    const pending = render()
    await flushPromises()
    await pending.get('.instance-list .flow-detail').trigger('click')
    await flushPromises()

    expect(flowApi.listCopies).toHaveBeenCalledWith('10', '101', 1, 20)
    expect(pending.find('.flow-copy').exists()).toBe(true)
    expect(pending.get('.copy-timeline').text()).toContain('成员 100 抄送成员 300')
    expect(pending.get('.copy-timeline').text()).toContain('Please follow the outcome')
    pending.unmount()

    const terminalInstance: FlowInstance = {
      ...pendingTask,
      status: 'APPROVED',
      completedAt: '2026-07-27T01:06:00Z',
    }
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([terminalInstance]))
    vi.mocked(flowApi.instance).mockResolvedValue(terminalInstance)
    const terminal = render()
    await flushPromises()
    await terminal.get('.instance-list .flow-detail').trigger('click')
    await flushPromises()
    expect(terminal.find('.flow-copy').exists()).toBe(true)
  })

  it('preserves the copy member and optional message when the request fails', async () => {
    applySession('20', '200', ['flow.instance.read', 'flow.instance.copy'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    vi.mocked(flowApi.copy).mockRejectedValue(new Error('recipient already copied'))
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.instance-list .flow-detail').trigger('click')
    await flushPromises()

    await wrapper.get('.flow-copy').trigger('click')
    await wrapper.get('.copy-member-picker').trigger('click')
    await wrapper.get('.copy-message').setValue('Please follow this approval outcome')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.copy).toHaveBeenCalledWith('10', '101', {
      targetMemberId: '300',
      message: 'Please follow this approval outcome',
    })
    expect(wrapper.get('.copy-member-picker').text()).toBe('300')
    expect((wrapper.get('.copy-message').element as HTMLTextAreaElement).value)
      .toBe('Please follow this approval outcome')
    expect(wrapper.text()).toContain('recipient already copied')
  })

  it('copies without a message and refreshes only the copy timeline after success', async () => {
    applySession('20', '200', ['flow.instance.read', 'flow.instance.copy'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    let copied = false
    vi.mocked(flowApi.listCopies).mockImplementation(async () => copyPage(copied ? [{
      copyId: '701',
      instanceId: '101',
      actorId: '200',
      recipientId: '300',
      message: '',
      createdAt: '2026-07-27T01:04:00Z',
    }] : []))
    vi.mocked(flowApi.copy).mockImplementation(async () => {
      copied = true
      return {
        copyId: '701',
        instanceId: '101',
        actorId: '200',
        recipientId: '300',
        message: '',
        createdAt: '2026-07-27T01:04:00Z',
      }
    })
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.instance-list .flow-detail').trigger('click')
    await flushPromises()
    const instanceCalls = vi.mocked(flowApi.instance).mock.calls.length
    const historyCalls = vi.mocked(flowApi.history).mock.calls.length

    await wrapper.get('.flow-copy').trigger('click')
    await wrapper.get('.copy-member-picker').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.copy).toHaveBeenCalledWith('10', '101', {
      targetMemberId: '300',
    })
    expect(flowApi.listCopies).toHaveBeenCalledTimes(2)
    expect(flowApi.instance).toHaveBeenCalledTimes(instanceCalls)
    expect(flowApi.history).toHaveBeenCalledTimes(historyCalls)
    expect(wrapper.find('.copy-message').exists()).toBe(false)
    expect(wrapper.get('.copy-timeline').text()).toContain('成员 200 抄送成员 300')
  })

  it('gates return and cancel-claim and hides decision, assignment and urge while open', async () => {
    applySession('20', '200', [
      'flow.instance.read',
      'flow.instance.decide',
      'flow.instance.transfer',
      'flow.instance.add-sign',
      'flow.instance.return',
      'flow.instance.cancel-claim',
      'flow.instance.urge',
    ])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    const claimed = render()
    await flushPromises()
    expect(claimed.find('.flow-return').exists()).toBe(true)
    expect(claimed.get('.flow-return').attributes('disabled')).toBeUndefined()
    expect(claimed.find('.flow-cancel-claim').exists()).toBe(true)
    claimed.unmount()

    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([{
      ...pendingTask,
      currentStepIndex: 0,
    }]))
    const firstStep = render()
    await flushPromises()
    expect(firstStep.find('.flow-return').exists()).toBe(true)
    expect(firstStep.get('.flow-return').attributes('disabled')).toBeDefined()
    firstStep.unmount()

    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([{
      ...pendingTask,
      requesterId: '200',
      claimState: 'OPEN',
    }]))
    const openTask = render()
    await flushPromises()
    expect(openTask.find('.instance-approve').exists()).toBe(false)
    expect(openTask.find('.instance-reject').exists()).toBe(false)
    expect(openTask.find('.flow-transfer').exists()).toBe(false)
    expect(openTask.find('.flow-add-sign').exists()).toBe(false)
    expect(openTask.find('.flow-return').exists()).toBe(false)
    expect(openTask.find('.flow-urge').exists()).toBe(false)
  })

  it('loads and pages the permission-scoped claimable task section', async () => {
    applySession('20', '300', ['flow.instance.claim'])
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    vi.mocked(flowApi.listClaimableTasks).mockResolvedValue(instancePage([{
      ...pendingTask,
      claimState: 'OPEN',
    }]))
    vi.mocked(flowApi.listClaimableTasks).mockResolvedValue({
      items: [{ ...pendingTask, claimState: 'OPEN' }],
      page: 1,
      size: 20,
      total: 21,
    })
    const wrapper = render()
    await flushPromises()

    expect(flowApi.listClaimableTasks).toHaveBeenCalledWith('10', 1, 20)
    expect(wrapper.find('.claimable-task-section').exists()).toBe(true)
    expect(wrapper.text()).toContain('待认领')
    await wrapper.get('.claimable-pagination').trigger('click')
    await flushPromises()
    expect(flowApi.listClaimableTasks).toHaveBeenLastCalledWith('10', 2, 20)
  })

  it('preserves the optional claim comment when the request fails', async () => {
    applySession('20', '300', ['flow.instance.claim'])
    vi.mocked(flowApi.listClaimableTasks).mockResolvedValue(instancePage([{
      ...pendingTask,
      claimState: 'OPEN',
    }]))
    vi.mocked(flowApi.claim).mockRejectedValue(new Error('claim lost'))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-claim').trigger('click')
    await wrapper.get('.claim-comment').setValue('I will handle this review')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.claim).toHaveBeenCalledWith('10', '101', {
      comment: 'I will handle this review',
    })
    expect((wrapper.get('.claim-comment').element as HTMLTextAreaElement).value)
      .toBe('I will handle this review')
    expect(wrapper.text()).toContain('claim lost')
  })

  it('claims without a comment and refreshes instances, normal tasks, claimable tasks and history', async () => {
    applySession('20', '300', ['flow.instance.read', 'flow.instance.claim'])
    let claimed = false
    vi.mocked(flowApi.listClaimableTasks).mockImplementation(async () =>
      instancePage(claimed ? [] : [{ ...pendingTask, claimState: 'OPEN' }]))
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage())
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    vi.mocked(flowApi.claim).mockImplementation(async () => {
      claimed = true
      return {
        ...pendingTask,
        approverId: '300',
        claimState: 'CLAIMED',
      }
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-claim').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.claim).toHaveBeenCalledWith('10', '101', { comment: '' })
    expect(flowApi.listInstances).toHaveBeenCalledTimes(2)
    expect(flowApi.listApprovalTasks).toHaveBeenCalledTimes(2)
    expect(flowApi.listClaimableTasks).toHaveBeenCalledTimes(2)
    expect(flowApi.instance).toHaveBeenCalledWith('10', '101')
    expect(flowApi.history).toHaveBeenCalledWith('10', '101')
    expect(wrapper.find('.claim-comment').exists()).toBe(false)
  })

  it('preserves the return reason when the request fails', async () => {
    applySession('20', '200', ['flow.instance.read', 'flow.instance.return'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    vi.mocked(flowApi.returnInstance).mockRejectedValue(new Error('cursor changed'))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-return').trigger('click')
    await wrapper.get('.return-reason').setValue('Correct the previous data')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.returnInstance).toHaveBeenCalledWith('10', '101', {
      reason: 'Correct the previous data',
    })
    expect((wrapper.get('.return-reason').element as HTMLTextAreaElement).value)
      .toBe('Correct the previous data')
    expect(wrapper.text()).toContain('cursor changed')
  })

  it('returns and refreshes instances, normal tasks, claimable tasks and history', async () => {
    applySession('20', '200', [
      'flow.instance.read',
      'flow.instance.return',
      'flow.instance.claim',
    ])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.flow-return').trigger('click')
    await wrapper.get('.return-reason').setValue('Correct the previous data')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.listInstances).toHaveBeenCalledTimes(2)
    expect(flowApi.listApprovalTasks).toHaveBeenCalledTimes(2)
    expect(flowApi.listClaimableTasks).toHaveBeenCalledTimes(2)
    expect(flowApi.instance).toHaveBeenCalledWith('10', '101')
    expect(flowApi.history).toHaveBeenCalledWith('10', '101')
    expect(wrapper.find('.return-reason').exists()).toBe(false)
  })

  it('preserves cancel-claim reason on failure and refreshes all projections on retry success', async () => {
    applySession('20', '200', [
      'flow.instance.read',
      'flow.instance.cancel-claim',
      'flow.instance.claim',
    ])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    vi.mocked(flowApi.cancelClaim).mockRejectedValueOnce(new Error('state changed'))
    const failed = render()
    await flushPromises()

    await failed.get('.flow-cancel-claim').trigger('click')
    await failed.get('.cancel-claim-reason').setValue('Release to candidate pool')
    await failed.get('.modal-ok').trigger('click')
    await flushPromises()
    expect((failed.get('.cancel-claim-reason').element as HTMLTextAreaElement).value)
      .toBe('Release to candidate pool')
    expect(failed.text()).toContain('state changed')
    failed.unmount()

    vi.mocked(flowApi.cancelClaim).mockResolvedValue({
      ...pendingTask,
      claimState: 'OPEN',
    })
    const succeeded = render()
    await flushPromises()
    const instancesBefore = vi.mocked(flowApi.listInstances).mock.calls.length
    const tasksBefore = vi.mocked(flowApi.listApprovalTasks).mock.calls.length
    const claimableBefore = vi.mocked(flowApi.listClaimableTasks).mock.calls.length
    await succeeded.get('.flow-cancel-claim').trigger('click')
    await succeeded.get('.cancel-claim-reason').setValue('Release to candidate pool')
    await succeeded.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(flowApi.cancelClaim).toHaveBeenLastCalledWith('10', '101', {
      reason: 'Release to candidate pool',
    })
    expect(vi.mocked(flowApi.listInstances).mock.calls.length).toBe(instancesBefore + 1)
    expect(vi.mocked(flowApi.listApprovalTasks).mock.calls.length).toBe(tasksBefore + 1)
    expect(vi.mocked(flowApi.listClaimableTasks).mock.calls.length).toBe(claimableBefore + 1)
    expect(flowApi.instance).toHaveBeenCalledWith('10', '101')
    expect(flowApi.history).toHaveBeenCalledWith('10', '101')
    expect(succeeded.find('.cancel-claim-reason').exists()).toBe(false)
  })

  it('renders assignment, return, claim and cancel-claim history facts', async () => {
    applySession('20', '200', ['flow.instance.read'])
    vi.mocked(flowApi.listInstances).mockResolvedValue(instancePage([pendingTask]))
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue(taskPage([]))
    vi.mocked(flowApi.history).mockResolvedValue({
      instanceId: '101',
      events: [
        {
          sequence: 1,
          type: 'TRANSFERRED',
          actorId: '200',
          fromStatus: 'PENDING',
          toStatus: 'PENDING',
          comment: 'Route to owner',
          targetMemberId: '300',
          position: null,
          occurredAt: '2026-07-27T01:07:00Z',
        },
        {
          sequence: 2,
          type: 'ADD_SIGNED',
          actorId: '300',
          fromStatus: 'PENDING',
          toStatus: 'PENDING',
          comment: 'Security review first',
          targetMemberId: '301',
          position: 'BEFORE',
          occurredAt: '2026-07-27T01:08:00Z',
        },
        {
          sequence: 3,
          type: 'RETURNED',
          actorId: '301',
          fromStatus: 'PENDING',
          toStatus: 'PENDING',
          comment: 'Correct previous data',
          targetMemberId: '200',
          position: null,
          occurredAt: '2026-07-27T01:09:00Z',
        },
        {
          sequence: 4,
          type: 'CLAIM_CANCELLED',
          actorId: '200',
          fromStatus: 'PENDING',
          toStatus: 'PENDING',
          comment: 'Release to pool',
          targetMemberId: null,
          position: null,
          occurredAt: '2026-07-27T01:10:00Z',
        },
        {
          sequence: 5,
          type: 'CLAIMED',
          actorId: '300',
          fromStatus: 'PENDING',
          toStatus: 'PENDING',
          comment: 'I will review',
          targetMemberId: '300',
          position: null,
          occurredAt: '2026-07-27T01:11:00Z',
        },
        {
          sequence: 6,
          type: 'SIGN_REMOVED',
          actorId: '300',
          fromStatus: 'PENDING',
          toStatus: 'PENDING',
          comment: 'Duplicate finance review',
          targetMemberId: '400',
          targetStepIndex: 3,
          position: null,
          occurredAt: '2026-07-27T01:12:00Z',
        },
      ],
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.instance-list .flow-detail').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('目标成员：300')
    expect(wrapper.text()).toContain('目标成员：301')
    expect(wrapper.text()).toContain('加签位置：当前步骤之前')
    expect(wrapper.text()).toContain('原因：Route to owner')
    expect(wrapper.text()).toContain('原因：Security review first')
    expect(wrapper.text()).toContain('RETURNED')
    expect(wrapper.text()).toContain('目标成员：200')
    expect(wrapper.text()).toContain('原因：Correct previous data')
    expect(wrapper.text()).toContain('CLAIM_CANCELLED')
    expect(wrapper.text()).toContain('原因：Release to pool')
    expect(wrapper.text()).toContain('CLAIMED')
    expect(wrapper.text()).toContain('备注：I will review')
    expect(wrapper.text()).toContain('SIGN_REMOVED')
    expect(wrapper.text()).toContain('目标成员：400')
    expect(wrapper.text()).toContain('目标步骤：4（索引 3）')
    expect(wrapper.text()).toContain('原因：Duplicate finance review')
  })
})
