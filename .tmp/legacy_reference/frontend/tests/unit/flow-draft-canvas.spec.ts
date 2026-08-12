import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { systemAdminApi } from '@/services/admin'
import { flowApi } from '@/services/flow'
import type { FlowApprovalStage, FlowDecisionEvidencePolicy } from '@/types/flow'
import FlowDraftCanvas from '@/views/system/FlowDraftCanvas.vue'
import type {
  FlowGatewayDraft,
  FlowInclusiveGatewayDraft,
  FlowParallelGatewayDraft,
} from '@/views/system/flowDraftGraphModel'

vi.mock('@/services/admin', () => ({
  systemAdminApi: {
    listDepartments: vi.fn(),
  },
}))

vi.mock('@/services/flow', () => ({
  flowApi: {
    listRecordMemberFields: vi.fn(),
  },
}))

function render(
  route = ['200', '300'],
  approvalMode: 'SEQUENTIAL' | 'ANY' | 'ALL' | 'QUORUM' = 'SEQUENTIAL',
  extraProps: Record<string, unknown> = {},
) {
  return mount(FlowDraftCanvas, {
    props: {
      systemId: '10',
      modelValue: route,
      approvalMode,
      triggerLabel: 'RECORD_UPDATED · purchase_order',
      approvedLabel: 'approval_status → 101',
      rejectedLabel: 'approval_status → 102',
      ...extraProps,
    },
    global: {
      stubs: {
        VueFlow: {
          props: ['nodes', 'edges'],
          emits: ['nodeClick'],
          template: `
            <div class="vue-flow-stub">
              <span class="node-count">{{ nodes.length }}</span>
              <span class="edge-count">{{ edges.length }}</span>
              <button class="select-second" @click="$emit('nodeClick', { node: { id: 'approval-1' } })">
                select second
              </button>
              <button class="select-trigger" @click="$emit('nodeClick', { node: { id: 'trigger' } })">
                select trigger
              </button>
              <button class="select-gateway" @click="$emit('nodeClick', { node: { id: 'gateway' } })">
                select gateway
              </button>
              <button class="select-branch" @click="$emit('nodeClick', { node: { id: 'branch-0-approval-0' } })">
                select branch
              </button>
              <button class="select-parallel" @click="$emit('nodeClick', { node: { id: 'parallel-split' } })">
                select parallel
              </button>
              <button class="select-parallel-branch" @click="$emit('nodeClick', { node: { id: 'parallel-1-approval-0' } })">
                select parallel branch
              </button>
            </div>
          `,
        },
        Handle: true,
        MemberPicker: {
          props: ['value'],
          emits: ['update:value'],
          template: `
            <button class="member-picker-stub" @click="$emit('update:value', '900')">
              {{ value }}
            </button>
          `,
        },
      },
    },
  })
}

describe('FlowDraftCanvas', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(systemAdminApi.listDepartments).mockResolvedValue({
      items: [{
        id: '7',
        name: '财务部',
        code: 'FIN',
        status: 'ACTIVE',
        memberCount: 2,
        version: '1',
      }],
      page: 1,
      size: 200,
      total: 1,
    })
    vi.mocked(flowApi.listRecordMemberFields).mockResolvedValue({
      items: [{
        sourceId: '501',
        moduleCode: 'purchase_order',
        fieldCode: 'owner',
        fieldName: '负责人',
      }],
    })
  })

  it('renders the executable graph and assigns the selected approval member', async () => {
    const wrapper = render()

    expect(wrapper.get('.node-count').text()).toBe('5')
    expect(wrapper.get('.edge-count').text()).toBe('5')
    expect(wrapper.text()).toContain('RECORD_UPDATED · purchase_order')
    expect(wrapper.text()).toContain('approval_status → 101')

    await wrapper.get('.select-second').trigger('click')
    expect(wrapper.text()).toContain('审批 2')
    await wrapper.get('.member-picker-stub').trigger('click')

    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toEqual(['200', '900'])
  })

  it('adds, moves and removes nodes through canonical route updates', async () => {
    const wrapper = render()

    await wrapper.get('.select-second').trigger('click')
    await wrapper.get('.flow-graph-add-approval').trigger('click')
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toEqual(['200', '300', ''])

    await wrapper.setProps({ modelValue: ['200', '300', ''] })
    await wrapper.get('.flow-graph-step-up').trigger('click')
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toEqual(['200', '', '300'])

    await wrapper.setProps({ modelValue: ['200', '', '300'] })
    await wrapper.get('.flow-graph-step-remove').trigger('click')
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toEqual(['200', '300'])
  })

  it('exposes save-check and save-simulate actions and guards the route bounds', async () => {
    const wrapper = render(Array.from({ length: 10 }, (_, index) => String(index + 1)))

    expect(wrapper.get('.flow-graph-add-approval').attributes('disabled')).toBeDefined()
    await wrapper.get('.flow-graph-save-check').trigger('click')
    await wrapper.get('.flow-graph-save-simulate').trigger('click')

    expect(wrapper.emitted('saveCheck')).toHaveLength(1)
    expect(wrapper.emitted('saveSimulate')).toHaveLength(1)
  })

  it('enables one gateway, edits a conditional route and keeps the default route canonical', async () => {
    const wrapper = render(['400'])

    await wrapper.get('.flow-graph-add-gateway').trigger('click')
    const gateway = wrapper.emitted('update:gateway')?.at(-1)?.[0] as FlowGatewayDraft
    expect(gateway.branches).toHaveLength(2)
    expect(gateway.branches[1]?.approverIds).toEqual(['400'])

    await wrapper.setProps({ gateway })
    expect(wrapper.get('.node-count').text()).toBe('6')
    expect(wrapper.get('.edge-count').text()).toBe('7')
    await wrapper.get('.select-branch').trigger('click')
    await wrapper.get('.member-picker-stub').trigger('click')

    const edited = wrapper.emitted('update:gateway')?.at(-1)?.[0] as {
      branches: Array<{ approverIds: string[] }>
    }
    expect(edited.branches[0]?.approverIds).toEqual(['900'])
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('edits a concurrent mode and projects the route as one approval group', async () => {
    const wrapper = render(['200', '300'], 'ANY')

    expect(wrapper.get('.node-count').text()).toBe('4')
    expect(wrapper.get('.edge-count').text()).toBe('3')
    expect(wrapper.text()).toContain('并行审批组')
    expect(wrapper.findAll('.flow-approval-group-members .member-picker-stub')).toHaveLength(2)

    await wrapper.get('.flow-approval-mode').setValue('ALL')

    expect(wrapper.emitted('update:approvalMode')?.at(-1)?.[0]).toBe('ALL')
  })

  it('edits count and percentage quorum rules on the active concurrent route', async () => {
    const wrapper = render(['200', '300', '400'])

    await wrapper.get('.flow-approval-mode').setValue('QUORUM')
    expect(wrapper.emitted('update:approvalMode')?.at(-1)?.[0]).toBe('QUORUM')
    expect(wrapper.emitted('update:quorumRule')?.at(-1)?.[0]).toEqual({
      type: 'COUNT',
      value: 1,
    })

    await wrapper.setProps({
      approvalMode: 'QUORUM',
      quorumRule: { type: 'COUNT', value: 1 },
    })
    await wrapper.get('.flow-quorum-rule-value').setValue('2')
    expect(wrapper.emitted('update:quorumRule')?.at(-1)?.[0]).toEqual({
      type: 'COUNT',
      value: 2,
    })

    await wrapper.setProps({ quorumRule: { type: 'COUNT', value: 2 } })
    await wrapper.get('.flow-quorum-rule-type').setValue('PERCENTAGE')
    expect(wrapper.emitted('update:quorumRule')?.at(-1)?.[0]).toEqual({
      type: 'PERCENTAGE',
      value: 50,
    })
  })

  it('configures a bounded reminder and timeout action on the active route', async () => {
    const wrapper = render(['200'])

    await wrapper.get('.flow-deadline-enabled').setValue(true)
    expect(wrapper.emitted('update:deadlinePolicy')?.at(-1)?.[0]).toEqual({
      timeoutMinutes: 1440,
      remindBeforeMinutes: 60,
      timeoutAction: 'NONE',
    })

    await wrapper.setProps({
      deadlinePolicy: {
        timeoutMinutes: 1440,
        remindBeforeMinutes: 60,
        timeoutAction: 'NONE',
      },
    })
    await wrapper.get('.flow-deadline-timeout').setValue('120')
    expect(wrapper.emitted('update:deadlinePolicy')?.at(-1)?.[0]).toEqual({
      timeoutMinutes: 120,
      remindBeforeMinutes: 60,
      timeoutAction: 'NONE',
    })

    await wrapper.setProps({
      deadlinePolicy: {
        timeoutMinutes: 120,
        remindBeforeMinutes: 60,
        timeoutAction: 'NONE',
      },
    })
    await wrapper.get('.flow-deadline-reminder').setValue('30')
    await wrapper.setProps({
      deadlinePolicy: {
        timeoutMinutes: 120,
        remindBeforeMinutes: 30,
        timeoutAction: 'NONE',
      },
    })
    await wrapper.get('.flow-deadline-action').setValue('AUTO_REJECT')
    expect(wrapper.emitted('update:deadlinePolicy')?.at(-1)?.[0]).toEqual({
      timeoutMinutes: 120,
      remindBeforeMinutes: 30,
      timeoutAction: 'AUTO_REJECT',
    })
  })

  it('edits independent approval and rejection comment requirements on the active route', async () => {
    const wrapper = render(['200'])

    await wrapper.get('.flow-decision-comment-enabled').setValue(true)
    expect(wrapper.emitted('update:decisionCommentPolicy')?.at(-1)?.[0]).toEqual({
      approveRequired: false,
      rejectRequired: true,
      minimumLength: 1,
    })

    await wrapper.setProps({
      decisionCommentPolicy: {
        approveRequired: false,
        rejectRequired: true,
        minimumLength: 1,
      },
    })
    await wrapper.get('.flow-decision-comment-approve-required').setValue(true)
    expect(wrapper.emitted('update:decisionCommentPolicy')?.at(-1)?.[0]).toEqual({
      approveRequired: true,
      rejectRequired: true,
      minimumLength: 1,
    })

    await wrapper.setProps({
      decisionCommentPolicy: {
        approveRequired: true,
        rejectRequired: true,
        minimumLength: 1,
      },
    })
    await wrapper.get('.flow-decision-comment-minimum-length').setValue('12')
    expect(wrapper.emitted('update:decisionCommentPolicy')?.at(-1)?.[0]).toEqual({
      approveRequired: true,
      rejectRequired: true,
      minimumLength: 12,
    })
  })

  it('selects a department for its leader and keeps requester-manager source id empty', async () => {
    const wrapper = render(['200'])

    await wrapper.get('.flow-approver-source-kind').setValue('DEPARTMENT_LEADER')
    expect(wrapper.emitted('update:approverSource')?.at(-1)?.[0]).toEqual({
      kind: 'DEPARTMENT_LEADER',
      sourceId: '',
    })
    await wrapper.setProps({
      approverSource: { kind: 'DEPARTMENT_LEADER', sourceId: '' },
    })
    await vi.waitFor(() => {
      expect(systemAdminApi.listDepartments).toHaveBeenCalledWith('10', {
        page: 1,
        size: 200,
        status: 'ACTIVE',
      })
    })
    expect(wrapper.get('.flow-approver-department-leader').text()).toContain('财务部')

    await wrapper.get('.flow-approver-department-leader').setValue('7')
    expect(wrapper.emitted('update:approverSource')?.at(-1)?.[0]).toEqual({
      kind: 'DEPARTMENT_LEADER',
      sourceId: '7',
    })

    await wrapper.get('.flow-approver-source-kind').setValue('REQUESTER_MANAGER')
    expect(wrapper.emitted('update:approverSource')?.at(-1)?.[0]).toEqual({
      kind: 'REQUESTER_MANAGER',
      sourceId: null,
    })
  })

  it('loads and selects a record MEMBER field while preserving moduleCode', async () => {
    const wrapper = render(['200'], 'SEQUENTIAL', {
      recordContextModuleCode: 'purchase_order',
    })

    await wrapper.get('.flow-approver-source-kind').setValue('RECORD_MEMBER_FIELD')
    expect(wrapper.emitted('update:approverSource')?.at(-1)?.[0]).toEqual({
      kind: 'RECORD_MEMBER_FIELD',
      sourceId: '',
      moduleCode: 'purchase_order',
    })
    await wrapper.setProps({
      approverSource: {
        kind: 'RECORD_MEMBER_FIELD',
        sourceId: '',
        moduleCode: 'purchase_order',
      },
    })
    await vi.waitFor(() => {
      expect(flowApi.listRecordMemberFields).toHaveBeenCalledWith(
        '10',
        'purchase_order',
      )
    })
    expect(wrapper.get('.flow-record-member-field').text()).toContain('负责人（owner）')

    await wrapper.get('.flow-record-member-field').setValue('501')
    expect(wrapper.emitted('update:approverSource')?.at(-1)?.[0]).toEqual({
      kind: 'RECORD_MEMBER_FIELD',
      sourceId: '501',
      moduleCode: 'purchase_order',
    })
  })

  it('shows record MEMBER catalog error and empty retry states', async () => {
    vi.mocked(flowApi.listRecordMemberFields)
      .mockRejectedValueOnce(new Error('目录不可用'))
      .mockResolvedValueOnce({ items: [] })
    const wrapper = render(['200'], 'SEQUENTIAL', {
      approverSource: {
        kind: 'RECORD_MEMBER_FIELD',
        sourceId: '',
        moduleCode: 'purchase_order',
      },
    })

    await vi.waitFor(() => expect(wrapper.text()).toContain('目录不可用'))
    await wrapper.get('.flow-record-member-error button').trigger('click')
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('该模块没有可用的记录级单值 MEMBER 字段')
    })
  })

  it('sets record MEMBER field sources independently on every gateway type', async () => {
    const source = {
      kind: 'RECORD_MEMBER_FIELD',
      sourceId: '',
      moduleCode: 'purchase_order',
    }

    const conditional = render(['200'], 'SEQUENTIAL', {
      recordContextModuleCode: 'purchase_order',
    })
    await conditional.get('.flow-graph-add-gateway').trigger('click')
    const conditionalDraft = (
      conditional.emitted('update:gateway')?.at(-1)?.[0]
    ) as FlowGatewayDraft
    await conditional.setProps({ gateway: conditionalDraft })
    await conditional.get('.select-branch').trigger('click')
    await conditional.get('.flow-approver-source-kind').setValue('RECORD_MEMBER_FIELD')
    const conditionalEdited = (
      conditional.emitted('update:gateway')?.at(-1)?.[0]
    ) as FlowGatewayDraft
    expect(conditionalEdited.branches[0]?.approverSource).toEqual(source)
    conditional.unmount()

    const parallel = render(['200'], 'SEQUENTIAL', {
      recordContextModuleCode: 'purchase_order',
    })
    await parallel.get('.flow-graph-add-parallel').trigger('click')
    const parallelDraft = (
      parallel.emitted('update:parallelGateway')?.at(-1)?.[0]
    ) as FlowParallelGatewayDraft
    await parallel.setProps({ parallelGateway: parallelDraft })
    await parallel.get('.select-parallel-branch').trigger('click')
    await parallel.get('.flow-approver-source-kind').setValue('RECORD_MEMBER_FIELD')
    const parallelEdited = (
      parallel.emitted('update:parallelGateway')?.at(-1)?.[0]
    ) as FlowParallelGatewayDraft
    expect(parallelEdited.branches[1]?.approverSource).toEqual(source)
    parallel.unmount()

    const inclusive = render(['200'], 'SEQUENTIAL', {
      recordContextModuleCode: 'purchase_order',
    })
    await inclusive.get('.flow-graph-add-inclusive').trigger('click')
    const inclusiveDraft = (
      inclusive.emitted('update:inclusiveGateway')?.at(-1)?.[0]
    ) as FlowInclusiveGatewayDraft
    await inclusive.setProps({ inclusiveGateway: inclusiveDraft })
    await inclusive.get('.select-parallel-branch').trigger('click')
    await inclusive.get('.flow-approver-source-kind').setValue('RECORD_MEMBER_FIELD')
    const inclusiveEdited = (
      inclusive.emitted('update:inclusiveGateway')?.at(-1)?.[0]
    ) as FlowInclusiveGatewayDraft
    expect(inclusiveEdited.branches[1]?.approverSource).toEqual(source)
  })

  it('adds, edits, reorders and removes explicit linear approval stages', async () => {
    const wrapper = render(['200'])

    await wrapper.get('.flow-approval-stages-toggle').trigger('click')
    const enabled = (
      wrapper.emitted('update:approvalStages')?.at(-1)?.[0]
    ) as FlowApprovalStage[]
    expect(enabled).toHaveLength(2)
    expect(enabled[0]?.approverIds).toEqual(['200'])
    await wrapper.setProps({ approvalStages: enabled })

    await wrapper.get('.flow-approval-stage-source').setValue('PREVIOUS_HANDLER')
    const previousHandler = (
      wrapper.emitted('update:approvalStages')?.at(-1)?.[0]
    ) as typeof enabled
    expect(previousHandler[1]).toEqual(expect.objectContaining({
      approverIds: [],
      approverSource: { kind: 'PREVIOUS_HANDLER' },
    }))
    await wrapper.setProps({ approvalStages: previousHandler })

    await wrapper.get('.flow-approval-stage-add').trigger('click')
    const added = wrapper.emitted('update:approvalStages')?.at(-1)?.[0] as typeof enabled
    expect(added).toHaveLength(3)
    await wrapper.setProps({ approvalStages: added })
    await wrapper.findAll('.flow-approval-stage-up')[2]!.trigger('click')
    const reordered = wrapper.emitted('update:approvalStages')?.at(-1)?.[0] as typeof enabled
    expect(reordered.map(stage => stage.code)).toEqual(['stage_1', 'stage_3', 'stage_2'])
    await wrapper.setProps({ approvalStages: reordered })
    await wrapper.findAll('.flow-approval-stage-remove')[1]!.trigger('click')
    expect(
      (wrapper.emitted('update:approvalStages')?.at(-1)?.[0] as typeof enabled),
    ).toHaveLength(2)
    expect(wrapper.get('.flow-graph-add-gateway').attributes('disabled')).toBeDefined()
  })

  it('edits an independent stage plan and mirrors its first stage onto the branch', async () => {
    const wrapper = render(['200'])
    await wrapper.get('.flow-graph-add-parallel').trigger('click')
    const gateway = wrapper.emitted('update:parallelGateway')?.at(-1)?.[0] as
      FlowParallelGatewayDraft
    await wrapper.setProps({ parallelGateway: gateway })
    await wrapper.get('.select-parallel-branch').trigger('click')
    await wrapper.get('.flow-branch-approval-stages-toggle').trigger('click')
    const enabled = wrapper.emitted('update:parallelGateway')?.at(-1)?.[0] as
      FlowParallelGatewayDraft
    expect(enabled.branches[1]?.approvalStages).toHaveLength(1)
    expect(enabled.branches[0]?.approvalStages).toBeUndefined()

    await wrapper.setProps({ parallelGateway: enabled })
    await wrapper.get('.flow-branch-approval-stage-add').trigger('click')
    const added = wrapper.emitted('update:parallelGateway')?.at(-1)?.[0] as
      FlowParallelGatewayDraft
    await wrapper.setProps({ parallelGateway: added })
    await wrapper.findAll('.flow-branch-approval-stage-source')[1]!
      .setValue('PREVIOUS_HANDLER')
    const previousHandler = wrapper.emitted('update:parallelGateway')?.at(-1)?.[0] as
      FlowParallelGatewayDraft
    await wrapper.setProps({ parallelGateway: previousHandler })
    expect(wrapper.findAll('.flow-branch-approval-stage-down')[0]!
      .attributes('disabled')).toBeDefined()
    await wrapper.findAll('.flow-branch-approval-stage-source')[1]!.setValue('REQUESTER')
    const contextual = wrapper.emitted('update:parallelGateway')?.at(-1)?.[0] as
      FlowParallelGatewayDraft
    expect(contextual.branches[1]?.approvalStages?.[1]).toEqual(expect.objectContaining({
      approverIds: [],
      approverSource: { kind: 'REQUESTER' },
    }))

    await wrapper.setProps({ parallelGateway: contextual })
    await wrapper.findAll('.flow-branch-approval-stage-up')[1]!.trigger('click')
    const reordered = wrapper.emitted('update:parallelGateway')?.at(-1)?.[0] as
      FlowParallelGatewayDraft
    expect(reordered.branches[1]).toEqual(expect.objectContaining({
      approverIds: [],
      approverSource: { kind: 'REQUESTER' },
      approvalMode: 'SEQUENTIAL',
    }))
    expect(reordered.branches[1]?.approvalStages?.[0]?.approverSource).toEqual({
      kind: 'REQUESTER',
    })
  })

  it('enables branch-local stages on exclusive and inclusive gateways', async () => {
    const exclusive = render(['200'])
    await exclusive.get('.flow-graph-add-gateway').trigger('click')
    const exclusiveGateway = exclusive.emitted('update:gateway')?.at(-1)?.[0] as
      FlowGatewayDraft
    await exclusive.setProps({ gateway: exclusiveGateway })
    await exclusive.get('.select-branch').trigger('click')
    await exclusive.get('.flow-branch-approval-stages-toggle').trigger('click')
    const exclusiveEdited = exclusive.emitted('update:gateway')?.at(-1)?.[0] as
      FlowGatewayDraft
    expect(exclusiveEdited.branches[0]?.approvalStages).toHaveLength(1)

    const inclusive = render(['200'])
    await inclusive.get('.flow-graph-add-inclusive').trigger('click')
    const inclusiveGateway = inclusive.emitted('update:inclusiveGateway')?.at(-1)?.[0] as
      FlowInclusiveGatewayDraft
    await inclusive.setProps({ inclusiveGateway })
    await inclusive.get('.select-parallel-branch').trigger('click')
    await inclusive.get('.flow-branch-approval-stages-toggle').trigger('click')
    const inclusiveEdited = inclusive.emitted('update:inclusiveGateway')?.at(-1)?.[0] as
      FlowInclusiveGatewayDraft
    expect(inclusiveEdited.branches[1]?.approvalStages).toHaveLength(1)
  })

  it('keeps decision comment rules independent across parallel branches', async () => {
    const wrapper = render(['200'])
    await wrapper.get('.flow-graph-add-parallel').trigger('click')
    const gateway = (
      wrapper.emitted('update:parallelGateway')?.at(-1)?.[0]
    ) as FlowParallelGatewayDraft
    await wrapper.setProps({ parallelGateway: gateway })
    await wrapper.get('.select-parallel-branch').trigger('click')
    await wrapper.get('.flow-decision-comment-enabled').setValue(true)

    const edited = (
      wrapper.emitted('update:parallelGateway')?.at(-1)?.[0]
    ) as FlowParallelGatewayDraft
    expect(edited.branches[0]?.decisionCommentPolicy).toBeUndefined()
    expect(edited.branches[1]?.decisionCommentPolicy).toEqual({
      approveRequired: false,
      rejectRequired: true,
      minimumLength: 1,
    })
  })

  it('edits bounded attachment, MIME-family and signature evidence policy', async () => {
    const wrapper = render(['200'])
    await wrapper.get('.flow-decision-evidence-enabled').setValue(true)
    const enabled = wrapper.emitted('update:decisionEvidencePolicy')?.at(-1)?.[0] as
      FlowDecisionEvidencePolicy
    expect(enabled).toEqual({
      minimumAttachments: 0,
      maximumAttachments: 5,
      allowedMimeFamilies: [],
      signatureMode: 'NONE',
    })
    await wrapper.setProps({ decisionEvidencePolicy: enabled })
    await wrapper.get('.flow-decision-evidence-minimum').setValue('2')
    await wrapper.setProps({
      decisionEvidencePolicy:
        wrapper.emitted('update:decisionEvidencePolicy')?.at(-1)?.[0] as
          FlowDecisionEvidencePolicy,
    })
    await wrapper.get('.flow-decision-evidence-maximum').setValue('4')
    await wrapper.setProps({
      decisionEvidencePolicy:
        wrapper.emitted('update:decisionEvidencePolicy')?.at(-1)?.[0] as
          FlowDecisionEvidencePolicy,
    })
    await wrapper.get('.flow-decision-evidence-signature-mode').setValue('REQUIRED')
    await wrapper.setProps({
      decisionEvidencePolicy:
        wrapper.emitted('update:decisionEvidencePolicy')?.at(-1)?.[0] as
          FlowDecisionEvidencePolicy,
    })
    await wrapper.findAll('.flow-decision-evidence-mime-families input')[0]!
      .setValue(true)

    expect(wrapper.emitted('update:decisionEvidencePolicy')?.at(-1)?.[0]).toEqual({
      minimumAttachments: 2,
      maximumAttachments: 4,
      allowedMimeFamilies: ['IMAGE'],
      signatureMode: 'REQUIRED',
    })
  })

  it('mirrors a branch evidence policy into explicit stage zero', async () => {
    const wrapper = render(['200'])
    await wrapper.get('.flow-graph-add-parallel').trigger('click')
    const gateway = wrapper.emitted('update:parallelGateway')?.at(-1)?.[0] as
      FlowParallelGatewayDraft
    await wrapper.setProps({ parallelGateway: gateway })
    await wrapper.get('.select-parallel-branch').trigger('click')
    await wrapper.get('.flow-branch-approval-stages-toggle').trigger('click')
    const staged = wrapper.emitted('update:parallelGateway')?.at(-1)?.[0] as
      FlowParallelGatewayDraft
    await wrapper.setProps({ parallelGateway: staged })
    await wrapper.get('.flow-decision-evidence-enabled').setValue(true)
    const edited = wrapper.emitted('update:parallelGateway')?.at(-1)?.[0] as
      FlowParallelGatewayDraft

    expect(edited.branches[1]?.decisionEvidencePolicy).toEqual({
      minimumAttachments: 0,
      maximumAttachments: 5,
      allowedMimeFamilies: [],
      signatureMode: 'NONE',
    })
    expect(edited.branches[1]?.approvalStages?.[0]?.decisionEvidencePolicy)
      .toEqual(edited.branches[1]?.decisionEvidencePolicy)
  })

  it('edits a two-branch parallel split and keeps the first route canonical', async () => {
    const wrapper = render(['200'])

    await wrapper.get('.flow-graph-add-parallel').trigger('click')
    const gateway = (
      wrapper.emitted('update:parallelGateway')?.at(-1)?.[0]
    ) as FlowParallelGatewayDraft
    expect(gateway.branches).toHaveLength(2)
    expect(gateway.branches[0]?.approverIds).toEqual(['200'])

    await wrapper.setProps({ parallelGateway: gateway })
    expect(wrapper.get('.node-count').text()).toBe('7')
    expect(wrapper.get('.edge-count').text()).toBe('8')
    await wrapper.get('.select-parallel').trigger('click')
    expect(wrapper.text()).toContain('所有分支同时开始')

    await wrapper.get('.select-parallel-branch').trigger('click')
    await wrapper.get('.member-picker-stub').trigger('click')
    const edited = (
      wrapper.emitted('update:parallelGateway')?.at(-1)?.[0]
    ) as FlowParallelGatewayDraft
    expect(edited.branches[1]?.approverIds).toEqual(['900'])
  })

  it('edits inclusive branch conditions on the shared split and join canvas', async () => {
    const wrapper = render(['200'])

    await wrapper.get('.flow-graph-add-inclusive').trigger('click')
    const gateway = (
      wrapper.emitted('update:inclusiveGateway')?.at(-1)?.[0]
    ) as FlowInclusiveGatewayDraft
    expect(gateway.branches).toHaveLength(2)
    expect(gateway.branches[0]?.approverIds).toEqual(['200'])

    await wrapper.setProps({ inclusiveGateway: gateway })
    expect(wrapper.get('.node-count').text()).toBe('7')
    expect(wrapper.get('.edge-count').text()).toBe('8')
    await wrapper.get('.select-parallel').trigger('click')
    expect(wrapper.text()).toContain('执行所有命中的条件分支')

    await wrapper.get('.flow-gateway-condition-field').setValue('amount')
    const edited = (
      wrapper.emitted('update:inclusiveGateway')?.at(-1)?.[0]
    ) as FlowInclusiveGatewayDraft
    expect(edited.branches[0]?.conditions[0]?.fieldCode).toBe('amount')
  })
})
