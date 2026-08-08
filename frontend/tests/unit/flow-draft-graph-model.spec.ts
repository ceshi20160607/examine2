import { describe, expect, it } from 'vitest'

import {
  assignFlowApprovalStep,
  insertFlowApprovalStep,
  MAX_FLOW_APPROVAL_STEPS,
  moveFlowApprovalStep,
  projectConditionalFlowDraftGraph,
  projectFlowDraftGraph,
  projectInclusiveFlowDraftGraph,
  projectParallelFlowDraftGraph,
  removeFlowApprovalStep,
} from '@/views/system/flowDraftGraphModel'

describe('flow draft graph model', () => {
  it('projects one deterministic executable graph from the ordered route', () => {
    const graph = projectFlowDraftGraph(['200', '300'], {
      trigger: 'RECORD_UPDATED · purchase_order',
      approved: 'approval_status → 101',
      rejected: 'approval_status → 102',
    })

    expect(graph.nodes.map((node) => node.id)).toEqual([
      'trigger',
      'approval-0',
      'approval-1',
      'approved',
      'rejected',
    ])
    expect(graph.nodes[1]?.data).toMatchObject({
      kind: 'approval',
      stepIndex: 0,
      approverId: '200',
    })
    expect(graph.nodes[3]?.data?.subtitle).toBe('approval_status → 101')
    expect(graph.edges.map((edge) => `${edge.source}->${edge.target}`)).toEqual([
      'trigger->approval-0',
      'approval-0->approval-1',
      'approval-1->approved',
      'approval-0->rejected',
      'approval-1->rejected',
    ])
  })

  it('keeps a required empty approval node for a new draft', () => {
    const graph = projectFlowDraftGraph([])

    expect(graph.nodes.find((node) => node.id === 'approval-0')?.data?.subtitle)
      .toBe('未选择成员')
    expect(graph.edges[0]).toMatchObject({
      source: 'trigger',
      target: 'approval-0',
    })
  })

  it('adds, assigns, reorders and removes the exact canonical route', () => {
    const added = insertFlowApprovalStep(['200', '400'], 0)
    const assigned = assignFlowApprovalStep(added, 1, '300')
    const moved = moveFlowApprovalStep(assigned, 2, -1)
    const removed = removeFlowApprovalStep(moved, 0)

    expect(added).toEqual(['200', '', '400'])
    expect(assigned).toEqual(['200', '300', '400'])
    expect(moved).toEqual(['200', '400', '300'])
    expect(removed).toEqual(['400', '300'])
  })

  it('enforces one-to-ten approval steps and ignores invalid mutations', () => {
    const full = Array.from({ length: MAX_FLOW_APPROVAL_STEPS }, (_, index) => String(index + 1))

    expect(insertFlowApprovalStep(full, 4, '11')).toEqual(full)
    expect(removeFlowApprovalStep(['200'], 0)).toEqual(['200'])
    expect(moveFlowApprovalStep(['200', '300'], 0, -1)).toEqual(['200', '300'])
    expect(assignFlowApprovalStep(['200'], 9, '300')).toEqual(['200'])
  })

  it('projects one executable exclusive gateway with ordered branch routes', () => {
    const graph = projectConditionalFlowDraftGraph(['400'], {
      branches: [
        {
          code: 'urgent',
          name: 'Urgent',
          defaultBranch: false,
          conditions: [{ fieldCode: 'urgent', operator: 'EQ', valueText: 'true' }],
          approverIds: ['200', '300'],
          approvalMode: 'SEQUENTIAL',
        },
        {
          code: 'default',
          name: 'Default',
          defaultBranch: true,
          conditions: [],
          approverIds: ['400'],
          approvalMode: 'SEQUENTIAL',
        },
      ],
    })

    expect(graph.nodes.map((node) => node.id)).toEqual([
      'trigger',
      'gateway',
      'approved',
      'rejected',
      'branch-0-approval-0',
      'branch-0-approval-1',
      'branch-1-approval-0',
    ])
    expect(graph.edges.map((edge) => `${edge.source}->${edge.target}`)).toEqual([
      'trigger->gateway',
      'gateway->branch-0-approval-0',
      'branch-0-approval-0->branch-0-approval-1',
      'branch-0-approval-1->approved',
      'branch-0-approval-0->rejected',
      'branch-0-approval-1->rejected',
      'gateway->branch-1-approval-0',
      'branch-1-approval-0->approved',
      'branch-1-approval-0->rejected',
    ])
  })

  it('projects ANY and ALL routes as concurrent approval groups', () => {
    const anyGraph = projectFlowDraftGraph(['200', '300'], {}, 'ANY')
    const gatewayGraph = projectConditionalFlowDraftGraph(['400', '500'], {
      branches: [
        {
          code: 'urgent',
          name: 'Urgent',
          defaultBranch: false,
          conditions: [{ fieldCode: 'urgent', operator: 'EQ', valueText: 'true' }],
          approverIds: ['200', '300'],
          approvalMode: 'ANY',
        },
        {
          code: 'default',
          name: 'Default',
          defaultBranch: true,
          conditions: [],
          approverIds: ['400', '500'],
          approvalMode: 'ALL',
        },
      ],
    })

    expect(anyGraph.nodes.map((node) => node.id)).toEqual([
      'trigger',
      'approval-0',
      'approved',
      'rejected',
    ])
    expect(anyGraph.nodes[1]?.data).toMatchObject({
      approvalMode: 'ANY',
      approverIds: ['200', '300'],
    })
    expect(gatewayGraph.nodes.filter((node) => node.data?.kind === 'approval')).toHaveLength(2)
    expect(gatewayGraph.nodes.find((node) => node.id === 'branch-1-approval-0')?.data)
      .toMatchObject({ approvalMode: 'ALL', approverIds: ['400', '500'] })
  })

  it('projects parallel split, independent routes and one all-branch join', () => {
    const graph = projectParallelFlowDraftGraph({
      branches: [
        {
          code: 'finance',
          name: 'Finance',
          approverIds: ['200', '300'],
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

    expect(graph.nodes.map((node) => node.id)).toEqual([
      'trigger',
      'parallel-split',
      'parallel-join',
      'approved',
      'rejected',
      'parallel-0-approval-0',
      'parallel-0-approval-1',
      'parallel-1-approval-0',
    ])
    expect(graph.edges.map((edge) => `${edge.source}->${edge.target}`)).toEqual([
      'trigger->parallel-split',
      'parallel-join->approved',
      'parallel-split->parallel-0-approval-0',
      'parallel-0-approval-0->parallel-0-approval-1',
      'parallel-0-approval-1->parallel-join',
      'parallel-0-approval-0->rejected',
      'parallel-0-approval-1->rejected',
      'parallel-split->parallel-1-approval-0',
      'parallel-1-approval-0->parallel-join',
      'parallel-1-approval-0->rejected',
    ])
    expect(graph.nodes.find((node) => node.id === 'parallel-1-approval-0')?.data)
      .toMatchObject({ approvalMode: 'ANY', approverIds: ['400', '500'] })
  })

  it('projects inclusive conditions over the shared split and join runtime', () => {
    const graph = projectInclusiveFlowDraftGraph({
      branches: [
        {
          code: 'urgent',
          name: 'Urgent',
          defaultBranch: false,
          conditions: [{ fieldCode: 'urgent', operator: 'EQ', valueText: 'true' }],
          approverIds: ['200'],
          approvalMode: 'SEQUENTIAL',
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
    })

    expect(graph.nodes.find(node => node.id === 'parallel-split')?.data?.title)
      .toBe('包容拆分')
    expect(graph.nodes.find(node => node.id === 'parallel-join')?.data?.title)
      .toBe('包容汇合')
    expect(graph.edges.find(edge => edge.id === 'parallel-split-0')?.label)
      .toContain('urgent EQ')
    expect(graph.edges.find(edge => edge.id === 'parallel-split-1')?.label)
      .toContain('无条件命中')
  })
})
