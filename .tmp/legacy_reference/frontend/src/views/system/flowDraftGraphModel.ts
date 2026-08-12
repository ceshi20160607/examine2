import { MarkerType, Position } from '@vue-flow/core'
import type { Edge, Node } from '@vue-flow/core'
import type {
  FlowApprovalMode,
  FlowApprovalStage,
  FlowApproverSource,
  FlowDecisionCommentPolicy,
  FlowDecisionEvidencePolicy,
  FlowDeadlinePolicy,
  FlowQuorumRule,
} from '@/types/flow'

export const MIN_FLOW_APPROVAL_STEPS = 1
export const MAX_FLOW_APPROVAL_STEPS = 10

export type FlowDraftGraphNodeKind = 'trigger' | 'gateway' | 'join' | 'approval' | 'terminal'
export type FlowDraftTerminalOutcome = 'APPROVED' | 'REJECTED'

export interface FlowDraftGraphNodeData {
  kind: FlowDraftGraphNodeKind
  title: string
  subtitle: string
  stepIndex?: number
  branchIndex?: number
  branchCode?: string
  defaultBranch?: boolean
  approverId?: string
  approverIds?: string[]
  approvalMode?: FlowApprovalMode
  outcome?: FlowDraftTerminalOutcome
}

export type FlowDraftGraphNode = Node<FlowDraftGraphNodeData>
export type FlowDraftGraphEdge = Edge<Record<string, never>>

export interface FlowDraftGraphLabels {
  trigger: string
  approved: string
  rejected: string
}

export interface FlowDraftGraphProjection {
  nodes: FlowDraftGraphNode[]
  edges: FlowDraftGraphEdge[]
}

export interface FlowGatewayConditionDraft {
  fieldCode: string
  operator: string
  valueText: string
}

export interface FlowGatewayBranchDraft {
  code: string
  name: string
  defaultBranch: boolean
  conditions: FlowGatewayConditionDraft[]
  approverIds: string[]
  approvalMode: FlowApprovalMode
  approverSource?: FlowApproverSource
  quorumRule?: FlowQuorumRule
  deadlinePolicy?: FlowDeadlinePolicy
  decisionCommentPolicy?: FlowDecisionCommentPolicy
  decisionEvidencePolicy?: FlowDecisionEvidencePolicy
  approvalStages?: FlowApprovalStage[]
}

export interface FlowGatewayDraft {
  branches: FlowGatewayBranchDraft[]
}

export interface FlowParallelBranchDraft {
  code: string
  name: string
  approverIds: string[]
  approvalMode: FlowApprovalMode
  approverSource?: FlowApproverSource
  quorumRule?: FlowQuorumRule
  deadlinePolicy?: FlowDeadlinePolicy
  decisionCommentPolicy?: FlowDecisionCommentPolicy
  decisionEvidencePolicy?: FlowDecisionEvidencePolicy
  approvalStages?: FlowApprovalStage[]
}

export interface FlowParallelGatewayDraft {
  branches: FlowParallelBranchDraft[]
}

export interface FlowInclusiveBranchDraft extends FlowGatewayBranchDraft {}

export interface FlowInclusiveGatewayDraft {
  branches: FlowInclusiveBranchDraft[]
}

const DEFAULT_LABELS: FlowDraftGraphLabels = {
  trigger: '手动发起',
  approved: '流程通过',
  rejected: '流程拒绝',
}

function safeRoute(approverIds: readonly string[]) {
  const route = approverIds.slice(0, MAX_FLOW_APPROVAL_STEPS)
  return route.length ? route : ['']
}

function approvalNode(approverId: string, stepIndex: number): FlowDraftGraphNode {
  return {
    id: `approval-${stepIndex}`,
    type: 'flowDraft',
    position: { x: 300 + stepIndex * 230, y: 180 },
    sourcePosition: Position.Right,
    targetPosition: Position.Left,
    draggable: false,
    connectable: false,
    deletable: false,
    data: {
      kind: 'approval',
      title: `审批 ${stepIndex + 1}`,
      subtitle: approverId || '未选择成员',
      stepIndex,
      approverId,
    },
  }
}

function concurrentApprovalNode(
  approverIds: string[],
  approvalMode: Exclude<FlowApprovalMode, 'SEQUENTIAL'>,
): FlowDraftGraphNode {
  return {
    ...approvalNode('', 0),
    data: {
      kind: 'approval',
      title: approvalMode === 'ANY'
        ? '任一审批'
        : approvalMode === 'QUORUM'
          ? '会签审批'
          : '全员审批',
      subtitle: `${approverIds.filter(Boolean).length} 位审批人并行处理`,
      stepIndex: 0,
      approverIds,
      approvalMode,
    },
  }
}

function edge(
  id: string,
  source: string,
  target: string,
  options: Partial<FlowDraftGraphEdge> = {},
): FlowDraftGraphEdge {
  return {
    id,
    source,
    target,
    type: 'smoothstep',
    markerEnd: MarkerType.ArrowClosed,
    ...options,
  }
}

export function projectFlowDraftGraph(
  approverIds: readonly string[],
  labels: Partial<FlowDraftGraphLabels> = {},
  approvalMode: FlowApprovalMode = 'SEQUENTIAL',
): FlowDraftGraphProjection {
  const route = safeRoute(approverIds)
  const resolvedLabels = { ...DEFAULT_LABELS, ...labels }
  const approvalNodes = approvalMode === 'SEQUENTIAL'
    ? route.map(approvalNode)
    : [concurrentApprovalNode(route, approvalMode)]
  const terminalX = 300 + approvalNodes.length * 230
  const nodes: FlowDraftGraphNode[] = [
    {
      id: 'trigger',
      type: 'flowDraft',
      position: { x: 40, y: 180 },
      sourcePosition: Position.Right,
      draggable: false,
      connectable: false,
      deletable: false,
      data: {
        kind: 'trigger',
        title: '开始',
        subtitle: resolvedLabels.trigger,
      },
    },
    ...approvalNodes,
    {
      id: 'approved',
      type: 'flowDraft',
      position: { x: terminalX, y: 90 },
      targetPosition: Position.Left,
      draggable: false,
      connectable: false,
      deletable: false,
      data: {
        kind: 'terminal',
        title: '已通过',
        subtitle: resolvedLabels.approved,
        outcome: 'APPROVED',
      },
    },
    {
      id: 'rejected',
      type: 'flowDraft',
      position: { x: terminalX, y: 285 },
      targetPosition: Position.Left,
      draggable: false,
      connectable: false,
      deletable: false,
      data: {
        kind: 'terminal',
        title: '已拒绝',
        subtitle: resolvedLabels.rejected,
        outcome: 'REJECTED',
      },
    },
  ]
  const edges: FlowDraftGraphEdge[] = [
    edge('trigger-approval-0', 'trigger', 'approval-0'),
  ]
  for (let index = 0; index < approvalNodes.length - 1; index += 1) {
    edges.push(edge(
      `approval-${index}-approval-${index + 1}`,
      `approval-${index}`,
      `approval-${index + 1}`,
    ))
  }
  const lastStep = approvalNodes.length - 1
  edges.push(edge(
    `approval-${lastStep}-approved`,
    `approval-${lastStep}`,
    'approved',
    { label: '通过' },
  ))
  approvalNodes.forEach((_, index) => {
    edges.push(edge(
      `approval-${index}-rejected`,
      `approval-${index}`,
      'rejected',
      {
        label: '拒绝',
        style: { stroke: '#dc2626', strokeDasharray: '5 4' },
        markerEnd: { type: MarkerType.ArrowClosed, color: '#dc2626' },
      },
    ))
  })
  return { nodes, edges }
}

export function projectConditionalFlowDraftGraph(
  defaultApproverIds: readonly string[],
  gateway: FlowGatewayDraft | null,
  labels: Partial<FlowDraftGraphLabels> = {},
  defaultApprovalMode: FlowApprovalMode = 'SEQUENTIAL',
): FlowDraftGraphProjection {
  if (!gateway) return projectFlowDraftGraph(defaultApproverIds, labels, defaultApprovalMode)
  const resolvedLabels = { ...DEFAULT_LABELS, ...labels }
  const branches = gateway.branches.slice(0, 6)
  const routes = branches.map((branch) => safeRoute(branch.approverIds))
  const visibleStepCounts = branches.map((branch, index) => (
    branch.approvalMode === 'SEQUENTIAL' ? routes[index]!.length : 1
  ))
  const maxSteps = Math.max(...visibleStepCounts)
  const terminalX = 520 + maxSteps * 230
  const centerY = 80 + Math.max(0, branches.length - 1) * 90
  const nodes: FlowDraftGraphNode[] = [
    {
      id: 'trigger',
      type: 'flowDraft',
      position: { x: 30, y: centerY },
      sourcePosition: Position.Right,
      draggable: false,
      connectable: false,
      deletable: false,
      data: {
        kind: 'trigger',
        title: '开始',
        subtitle: resolvedLabels.trigger,
      },
    },
    {
      id: 'gateway',
      type: 'flowDraft',
      position: { x: 265, y: centerY },
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
      draggable: false,
      connectable: false,
      deletable: false,
      data: {
        kind: 'gateway',
        title: '条件网关',
        subtitle: `${Math.max(0, branches.length - 1)} 条件 + 默认`,
      },
    },
    {
      id: 'approved',
      type: 'flowDraft',
      position: { x: terminalX, y: Math.max(20, centerY - 90) },
      targetPosition: Position.Left,
      draggable: false,
      connectable: false,
      deletable: false,
      data: {
        kind: 'terminal',
        title: '已通过',
        subtitle: resolvedLabels.approved,
        outcome: 'APPROVED',
      },
    },
    {
      id: 'rejected',
      type: 'flowDraft',
      position: { x: terminalX, y: centerY + 110 },
      targetPosition: Position.Left,
      draggable: false,
      connectable: false,
      deletable: false,
      data: {
        kind: 'terminal',
        title: '已拒绝',
        subtitle: resolvedLabels.rejected,
        outcome: 'REJECTED',
      },
    },
  ]
  const edges: FlowDraftGraphEdge[] = [
    edge('trigger-gateway', 'trigger', 'gateway'),
  ]
  branches.forEach((branch, branchIndex) => {
    const route = routes[branchIndex]!
    const y = 30 + branchIndex * 180
    const branchNodes = branch.approvalMode === 'SEQUENTIAL'
      ? route.map(approvalNode)
      : [concurrentApprovalNode(route, branch.approvalMode)]
    branchNodes.forEach((approval, stepIndex) => {
      nodes.push({
        ...approval,
        id: `branch-${branchIndex}-approval-${stepIndex}`,
        position: { x: 520 + stepIndex * 230, y },
        data: {
          ...approval.data!,
          branchIndex,
          branchCode: branch.code,
          defaultBranch: branch.defaultBranch,
        },
      })
    })
    const first = `branch-${branchIndex}-approval-0`
    edges.push(edge(`gateway-${branchIndex}`, 'gateway', first, {
      label: branch.defaultBranch ? '默认' : branch.name,
    }))
    for (let stepIndex = 0; stepIndex < branchNodes.length - 1; stepIndex += 1) {
      edges.push(edge(
        `branch-${branchIndex}-${stepIndex}-${stepIndex + 1}`,
        `branch-${branchIndex}-approval-${stepIndex}`,
        `branch-${branchIndex}-approval-${stepIndex + 1}`,
      ))
    }
    const last = `branch-${branchIndex}-approval-${branchNodes.length - 1}`
    edges.push(edge(`branch-${branchIndex}-approved`, last, 'approved', { label: '通过' }))
    branchNodes.forEach((_, stepIndex) => {
      edges.push(edge(
        `branch-${branchIndex}-${stepIndex}-rejected`,
        `branch-${branchIndex}-approval-${stepIndex}`,
        'rejected',
        {
          label: '拒绝',
          style: { stroke: '#dc2626', strokeDasharray: '5 4' },
          markerEnd: { type: MarkerType.ArrowClosed, color: '#dc2626' },
        },
      ))
    })
  })
  return { nodes, edges }
}

export function projectParallelFlowDraftGraph(
  gateway: FlowParallelGatewayDraft,
  labels: Partial<FlowDraftGraphLabels> = {},
): FlowDraftGraphProjection {
  const resolvedLabels = { ...DEFAULT_LABELS, ...labels }
  const branches = gateway.branches.slice(0, 5)
  const routes = branches.map((branch) => safeRoute(branch.approverIds))
  const visibleCounts = branches.map((branch, index) => (
    branch.approvalMode === 'SEQUENTIAL' ? routes[index]!.length : 1
  ))
  const maxSteps = Math.max(1, ...visibleCounts)
  const joinX = 520 + maxSteps * 230
  const terminalX = joinX + 230
  const centerY = 80 + Math.max(0, branches.length - 1) * 90
  const nodes: FlowDraftGraphNode[] = [
    {
      id: 'trigger',
      type: 'flowDraft',
      position: { x: 30, y: centerY },
      sourcePosition: Position.Right,
      draggable: false,
      connectable: false,
      deletable: false,
      data: { kind: 'trigger', title: '开始', subtitle: resolvedLabels.trigger },
    },
    {
      id: 'parallel-split',
      type: 'flowDraft',
      position: { x: 265, y: centerY },
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
      draggable: false,
      connectable: false,
      deletable: false,
      data: {
        kind: 'gateway',
        title: '并行拆分',
        subtitle: `${branches.length} 条分支同时执行`,
      },
    },
    {
      id: 'parallel-join',
      type: 'flowDraft',
      position: { x: joinX, y: centerY },
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
      draggable: false,
      connectable: false,
      deletable: false,
      data: {
        kind: 'join',
        title: '并行汇合',
        subtitle: '全部分支通过后继续',
      },
    },
    {
      id: 'approved',
      type: 'flowDraft',
      position: { x: terminalX, y: Math.max(20, centerY - 90) },
      targetPosition: Position.Left,
      draggable: false,
      connectable: false,
      deletable: false,
      data: {
        kind: 'terminal',
        title: '已通过',
        subtitle: resolvedLabels.approved,
        outcome: 'APPROVED',
      },
    },
    {
      id: 'rejected',
      type: 'flowDraft',
      position: { x: terminalX, y: centerY + 110 },
      targetPosition: Position.Left,
      draggable: false,
      connectable: false,
      deletable: false,
      data: {
        kind: 'terminal',
        title: '已拒绝',
        subtitle: resolvedLabels.rejected,
        outcome: 'REJECTED',
      },
    },
  ]
  const edges: FlowDraftGraphEdge[] = [
    edge('trigger-parallel-split', 'trigger', 'parallel-split'),
    edge('parallel-join-approved', 'parallel-join', 'approved', { label: '全部通过' }),
  ]
  branches.forEach((branch, branchIndex) => {
    const route = routes[branchIndex]!
    const y = 30 + branchIndex * 180
    const branchNodes = branch.approvalMode === 'SEQUENTIAL'
      ? route.map(approvalNode)
      : [concurrentApprovalNode(route, branch.approvalMode)]
    branchNodes.forEach((approval, stepIndex) => {
      nodes.push({
        ...approval,
        id: `parallel-${branchIndex}-approval-${stepIndex}`,
        position: { x: 520 + stepIndex * 230, y },
        data: {
          ...approval.data!,
          branchIndex,
          branchCode: branch.code,
        },
      })
    })
    const first = `parallel-${branchIndex}-approval-0`
    edges.push(edge(
      `parallel-split-${branchIndex}`,
      'parallel-split',
      first,
      { label: branch.name },
    ))
    for (let stepIndex = 0; stepIndex < branchNodes.length - 1; stepIndex += 1) {
      edges.push(edge(
        `parallel-${branchIndex}-${stepIndex}-${stepIndex + 1}`,
        `parallel-${branchIndex}-approval-${stepIndex}`,
        `parallel-${branchIndex}-approval-${stepIndex + 1}`,
      ))
    }
    const last = `parallel-${branchIndex}-approval-${branchNodes.length - 1}`
    edges.push(edge(`parallel-${branchIndex}-join`, last, 'parallel-join'))
    branchNodes.forEach((_, stepIndex) => {
      edges.push(edge(
        `parallel-${branchIndex}-${stepIndex}-rejected`,
        `parallel-${branchIndex}-approval-${stepIndex}`,
        'rejected',
        {
          label: '拒绝',
          style: { stroke: '#dc2626', strokeDasharray: '5 4' },
          markerEnd: { type: MarkerType.ArrowClosed, color: '#dc2626' },
        },
      ))
    })
  })
  return { nodes, edges }
}

export function projectInclusiveFlowDraftGraph(
  gateway: FlowInclusiveGatewayDraft,
  labels: Partial<FlowDraftGraphLabels> = {},
): FlowDraftGraphProjection {
  const projected = projectParallelFlowDraftGraph(gateway, labels)
  const branches = gateway.branches.slice(0, 5)
  return {
    nodes: projected.nodes.map((node) => {
      if (node.id === 'parallel-split') {
        return {
          ...node,
          data: {
            ...node.data!,
            title: '包容拆分',
            subtitle: '执行所有命中的条件分支',
          },
        }
      }
      if (node.id === 'parallel-join') {
        return {
          ...node,
          data: {
            ...node.data!,
            title: '包容汇合',
            subtitle: '全部已选分支通过后继续',
          },
        }
      }
      return node
    }),
    edges: projected.edges.map((edgeValue) => {
      if (!edgeValue.id.startsWith('parallel-split-')) return edgeValue
      const branchIndex = Number(edgeValue.id.slice('parallel-split-'.length))
      const branch = branches[branchIndex]
      if (!branch) return edgeValue
      const condition = branch.defaultBranch
        ? '无条件命中'
        : branch.conditions
            .map(item => `${item.fieldCode || '字段'} ${item.operator}`)
            .join(' 且 ')
      return { ...edgeValue, label: `${branch.name} · ${condition}` }
    }),
  }
}

export function createFlowParallelGatewayDraft(
  approverIds: readonly string[],
  approvalMode: FlowApprovalMode = 'SEQUENTIAL',
): FlowParallelGatewayDraft {
  const primary = safeRoute(approverIds)
  return {
    branches: [
      {
        code: 'branch_1',
        name: '并行分支 1',
        approverIds: primary,
        approvalMode,
        approverSource: { kind: 'FIXED', sourceId: null },
      },
      {
        code: 'branch_2',
        name: '并行分支 2',
        approverIds: [''],
        approvalMode: 'SEQUENTIAL',
        approverSource: { kind: 'FIXED', sourceId: null },
      },
    ],
  }
}

export function createFlowInclusiveGatewayDraft(
  approverIds: readonly string[],
  approvalMode: FlowApprovalMode = 'SEQUENTIAL',
): FlowInclusiveGatewayDraft {
  return {
    branches: [
      {
        code: 'condition_1',
        name: '包容条件 1',
        defaultBranch: false,
        conditions: [{ fieldCode: '', operator: 'EQ', valueText: '' }],
        approverIds: safeRoute(approverIds),
        approvalMode,
        approverSource: { kind: 'FIXED', sourceId: null },
      },
      {
        code: 'default',
        name: '默认分支',
        defaultBranch: true,
        conditions: [],
        approverIds: [''],
        approvalMode: 'SEQUENTIAL',
        approverSource: { kind: 'FIXED', sourceId: null },
      },
    ],
  }
}

export function createFlowGatewayDraft(
  defaultApproverIds: readonly string[],
  defaultApprovalMode: FlowApprovalMode = 'SEQUENTIAL',
): FlowGatewayDraft {
  return {
    branches: [
      {
        code: 'condition_1',
        name: '条件分支 1',
        defaultBranch: false,
        conditions: [{ fieldCode: '', operator: 'EQ', valueText: '' }],
        approverIds: [''],
        approvalMode: 'SEQUENTIAL',
        approverSource: { kind: 'FIXED', sourceId: null },
      },
      {
        code: 'default',
        name: '默认分支',
        defaultBranch: true,
        conditions: [],
        approverIds: safeRoute(defaultApproverIds),
        approvalMode: defaultApprovalMode,
        approverSource: { kind: 'FIXED', sourceId: null },
      },
    ],
  }
}

export function insertFlowApprovalStep(
  approverIds: readonly string[],
  afterIndex = approverIds.length - 1,
  approverId = '',
) {
  const route = safeRoute(approverIds)
  if (route.length >= MAX_FLOW_APPROVAL_STEPS) return route
  const insertAt = Math.min(Math.max(afterIndex + 1, 0), route.length)
  const next = [...route]
  next.splice(insertAt, 0, approverId)
  return next
}

export function removeFlowApprovalStep(
  approverIds: readonly string[],
  stepIndex: number,
) {
  const route = safeRoute(approverIds)
  if (
    route.length <= MIN_FLOW_APPROVAL_STEPS
    || stepIndex < 0
    || stepIndex >= route.length
  ) {
    return route
  }
  return route.filter((_, index) => index !== stepIndex)
}

export function moveFlowApprovalStep(
  approverIds: readonly string[],
  stepIndex: number,
  offset: -1 | 1,
) {
  const route = safeRoute(approverIds)
  const targetIndex = stepIndex + offset
  if (
    stepIndex < 0
    || stepIndex >= route.length
    || targetIndex < 0
    || targetIndex >= route.length
  ) {
    return route
  }
  const next = [...route]
  const current = next[stepIndex]!
  next[stepIndex] = next[targetIndex]!
  next[targetIndex] = current
  return next
}

export function assignFlowApprovalStep(
  approverIds: readonly string[],
  stepIndex: number,
  approverId: string,
) {
  const route = safeRoute(approverIds)
  if (stepIndex < 0 || stepIndex >= route.length) return route
  const next = [...route]
  next[stepIndex] = approverId
  return next
}
