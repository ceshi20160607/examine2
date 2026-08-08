<script setup lang="ts">
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'

import { Handle, Position, VueFlow } from '@vue-flow/core'
import type { NodeMouseEvent } from '@vue-flow/core'
import { ArrowDown, ArrowUp, Plus, Trash2 } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'

import MemberPicker from '@/components/runtime/MemberPicker.vue'
import { systemAdminApi } from '@/services/admin'
import { flowApi } from '@/services/flow'
import type {
  FlowApprovalMode,
  FlowApprovalStage,
  FlowApproverSource,
  FlowApproverSourceKind,
  FlowDecisionCommentPolicy,
  FlowDecisionEvidencePolicy,
  FlowDeadlinePolicy,
  FlowDeadlineTimeoutAction,
  FlowQuorumRule,
  FlowQuorumRuleType,
  FlowRecordMemberFieldOption,
} from '@/types/flow'
import {
  assignFlowApprovalStep,
  createFlowGatewayDraft,
  createFlowInclusiveGatewayDraft,
  createFlowParallelGatewayDraft,
  insertFlowApprovalStep,
  MAX_FLOW_APPROVAL_STEPS,
  moveFlowApprovalStep,
  projectConditionalFlowDraftGraph,
  projectInclusiveFlowDraftGraph,
  projectParallelFlowDraftGraph,
  removeFlowApprovalStep,
} from '@/views/system/flowDraftGraphModel'
import type {
  FlowGatewayBranchDraft,
  FlowGatewayDraft,
  FlowInclusiveGatewayDraft,
  FlowParallelBranchDraft,
  FlowParallelGatewayDraft,
} from '@/views/system/flowDraftGraphModel'

const props = withDefaults(defineProps<{
  systemId: string
  modelValue: string[]
  approvalMode?: FlowApprovalMode
  approvalStages?: FlowApprovalStage[] | null
  approverSource?: FlowApproverSource
  quorumRule?: FlowQuorumRule | null
  deadlinePolicy?: FlowDeadlinePolicy | null
  decisionCommentPolicy?: FlowDecisionCommentPolicy | null
  decisionEvidencePolicy?: FlowDecisionEvidencePolicy | null
  gateway?: FlowGatewayDraft | null
  parallelGateway?: FlowParallelGatewayDraft | null
  inclusiveGateway?: FlowInclusiveGatewayDraft | null
  recordContextModuleCode?: string
  triggerLabel?: string
  approvedLabel?: string
  rejectedLabel?: string
  busy?: boolean
}>(), {
  triggerLabel: '手动发起',
  approvedLabel: '流程通过',
  rejectedLabel: '流程拒绝',
  busy: false,
  gateway: null,
  parallelGateway: null,
  inclusiveGateway: null,
  recordContextModuleCode: '',
  approvalMode: 'SEQUENTIAL',
  approvalStages: null,
  approverSource: () => ({ kind: 'FIXED', sourceId: null }),
  deadlinePolicy: null,
  decisionCommentPolicy: null,
  decisionEvidencePolicy: null,
})

const emit = defineEmits<{
  'update:modelValue': [approverIds: string[]]
  'update:approvalMode': [approvalMode: FlowApprovalMode]
  'update:approvalStages': [stages: FlowApprovalStage[] | null]
  'update:approverSource': [source: FlowApproverSource]
  'update:quorumRule': [rule: FlowQuorumRule | null]
  'update:deadlinePolicy': [policy: FlowDeadlinePolicy | null]
  'update:decisionCommentPolicy': [policy: FlowDecisionCommentPolicy | null]
  'update:decisionEvidencePolicy': [policy: FlowDecisionEvidencePolicy | null]
  'update:gateway': [gateway: FlowGatewayDraft | null]
  'update:parallelGateway': [gateway: FlowParallelGatewayDraft | null]
  'update:inclusiveGateway': [gateway: FlowInclusiveGatewayDraft | null]
  'saveCheck': []
  'saveSimulate': []
}>()

const selectedNodeId = ref('approval-0')
const departmentOptions = ref<Array<{ label: string; value: string }>>([])
const departmentLoading = ref(false)
const departmentLoadError = ref('')
const loadedDepartmentSystemId = ref('')
const recordMemberFieldOptions = ref<FlowRecordMemberFieldOption[]>([])
const recordMemberFieldLoading = ref(false)
const recordMemberFieldLoadError = ref('')
const loadedRecordMemberFieldKey = ref('')
let recordMemberFieldRequestSequence = 0
const projection = computed(() => {
  const labels = {
    trigger: props.triggerLabel,
    approved: props.approvedLabel,
    rejected: props.rejectedLabel,
  }
  return props.inclusiveGateway
    ? projectInclusiveFlowDraftGraph(props.inclusiveGateway, labels)
    : props.parallelGateway
      ? projectParallelFlowDraftGraph(props.parallelGateway, labels)
    : projectConditionalFlowDraftGraph(
        props.modelValue,
        props.gateway,
        labels,
        props.approvalMode,
      )
})
const conditionGateway = computed(() => props.inclusiveGateway ?? props.gateway)
const selectedCoordinates = computed(() => {
  const plain = /^approval-(\d+)$/.exec(selectedNodeId.value)
  if (plain) return { branchIndex: null, stepIndex: Number(plain[1]) }
  const branch = /^branch-(\d+)-approval-(\d+)$/.exec(selectedNodeId.value)
  if (branch) return { branchIndex: Number(branch[1]), stepIndex: Number(branch[2]) }
  const parallel = /^parallel-(\d+)-approval-(\d+)$/.exec(selectedNodeId.value)
  if (!parallel) return null
  return { branchIndex: Number(parallel[1]), stepIndex: Number(parallel[2]) }
})
const selectedStepIndex = computed(() => selectedCoordinates.value?.stepIndex ?? null)
const selectedBranchIndex = computed(() => selectedCoordinates.value?.branchIndex ?? null)
const selectedNode = computed(() =>
  projection.value.nodes.find((node) => node.id === selectedNodeId.value) ?? null)
const selectedBranch = computed(() => {
  const index = selectedBranchIndex.value
  if (index === null) return null
  return props.inclusiveGateway?.branches[index]
    ?? props.parallelGateway?.branches[index]
    ?? props.gateway?.branches[index]
    ?? null
})
const activeBranchIndex = computed(() => {
  if (props.parallelGateway || props.inclusiveGateway) {
    return selectedBranchIndex.value ?? 0
  }
  if (!props.gateway) return null
  return selectedBranchIndex.value ?? props.gateway.branches.length - 1
})
const activeRoute = computed(() => {
  const branchIndex = activeBranchIndex.value
  return branchIndex === null
    ? props.modelValue
    : props.inclusiveGateway?.branches[branchIndex]?.approverIds
      ?? props.parallelGateway?.branches[branchIndex]?.approverIds
      ?? props.gateway?.branches[branchIndex]?.approverIds
      ?? props.modelValue
})
const activeApprovalMode = computed<FlowApprovalMode>(() => {
  const branchIndex = activeBranchIndex.value
  return branchIndex === null
    ? props.approvalMode
    : props.inclusiveGateway?.branches[branchIndex]?.approvalMode
      ?? props.parallelGateway?.branches[branchIndex]?.approvalMode
      ?? props.gateway?.branches[branchIndex]?.approvalMode
      ?? 'SEQUENTIAL'
})
const activeApproverSource = computed<FlowApproverSource>(() => {
  const branchIndex = activeBranchIndex.value
  return branchIndex === null
    ? props.approverSource
    : props.inclusiveGateway?.branches[branchIndex]?.approverSource
      ?? props.parallelGateway?.branches[branchIndex]?.approverSource
      ?? props.gateway?.branches[branchIndex]?.approverSource
      ?? props.approverSource
})
const activeQuorumRule = computed<FlowQuorumRule>(() => {
  const branchIndex = activeBranchIndex.value
  return (branchIndex === null
    ? props.quorumRule
    : props.inclusiveGateway?.branches[branchIndex]?.quorumRule
      ?? props.parallelGateway?.branches[branchIndex]?.quorumRule
      ?? props.gateway?.branches[branchIndex]?.quorumRule
      ?? props.quorumRule)
    ?? { type: 'COUNT', value: 1 }
})
const activeDeadlinePolicy = computed<FlowDeadlinePolicy | null>(() => {
  const branchIndex = activeBranchIndex.value
  if (branchIndex === null) return props.deadlinePolicy
  return props.inclusiveGateway?.branches[branchIndex]?.deadlinePolicy
    ?? props.parallelGateway?.branches[branchIndex]?.deadlinePolicy
    ?? props.gateway?.branches[branchIndex]?.deadlinePolicy
    ?? null
})
const activeDecisionCommentPolicy = computed<FlowDecisionCommentPolicy | null>(() => {
  const branchIndex = activeBranchIndex.value
  if (branchIndex === null) return props.decisionCommentPolicy
  return props.inclusiveGateway?.branches[branchIndex]?.decisionCommentPolicy
    ?? props.parallelGateway?.branches[branchIndex]?.decisionCommentPolicy
    ?? props.gateway?.branches[branchIndex]?.decisionCommentPolicy
    ?? null
})
const activeDecisionEvidencePolicy = computed<FlowDecisionEvidencePolicy | null>(() => {
  const branchIndex = activeBranchIndex.value
  if (branchIndex === null) return props.decisionEvidencePolicy
  return props.inclusiveGateway?.branches[branchIndex]?.decisionEvidencePolicy
    ?? props.parallelGateway?.branches[branchIndex]?.decisionEvidencePolicy
    ?? props.gateway?.branches[branchIndex]?.decisionEvidencePolicy
    ?? null
})
const selectedApproverId = computed(() => {
  const index = selectedStepIndex.value
  return index === null ? '' : activeRoute.value[index] ?? ''
})
const activeBranchApprovalStages = computed(() => {
  const branchIndex = activeBranchIndex.value
  if (branchIndex === null) return null
  return props.inclusiveGateway?.branches[branchIndex]?.approvalStages
    ?? props.parallelGateway?.branches[branchIndex]?.approvalStages
    ?? props.gateway?.branches[branchIndex]?.approvalStages
    ?? null
})
const activeBranchName = computed(() => {
  const branchIndex = activeBranchIndex.value
  if (branchIndex === null) return ''
  return props.inclusiveGateway?.branches[branchIndex]?.name
    ?? props.parallelGateway?.branches[branchIndex]?.name
    ?? props.gateway?.branches[branchIndex]?.name
    ?? ''
})

function stageCopy(stage: FlowApprovalStage): FlowApprovalStage {
  return {
    ...stage,
    approverIds: [...stage.approverIds],
    approverSource: { ...stage.approverSource },
    ...(stage.quorumRule ? { quorumRule: { ...stage.quorumRule } } : {}),
    ...(stage.deadlinePolicy ? { deadlinePolicy: { ...stage.deadlinePolicy } } : {}),
    ...(stage.decisionCommentPolicy
      ? { decisionCommentPolicy: { ...stage.decisionCommentPolicy } }
      : {}),
    ...(stage.decisionEvidencePolicy
      ? {
          decisionEvidencePolicy: {
            ...stage.decisionEvidencePolicy,
            allowedMimeFamilies: [...stage.decisionEvidencePolicy.allowedMimeFamilies],
          },
        }
      : {}),
  }
}

function currentRouteStage(code = 'stage_1', name = '阶段 1'): FlowApprovalStage {
  return {
    code,
    name,
    approverIds: activeApproverSource.value.kind === 'FIXED'
      ? [...activeRoute.value]
      : [],
    approvalMode: activeApprovalMode.value,
    approverSource: { ...activeApproverSource.value },
    ...(activeApprovalMode.value === 'QUORUM'
      ? { quorumRule: { ...activeQuorumRule.value } }
      : {}),
    ...(activeDeadlinePolicy.value
      ? { deadlinePolicy: { ...activeDeadlinePolicy.value } }
      : {}),
    ...(activeDecisionCommentPolicy.value
      ? { decisionCommentPolicy: { ...activeDecisionCommentPolicy.value } }
      : {}),
    ...(activeDecisionEvidencePolicy.value
      ? {
          decisionEvidencePolicy: {
            ...activeDecisionEvidencePolicy.value,
            allowedMimeFamilies: [
              ...activeDecisionEvidencePolicy.value.allowedMimeFamilies,
            ],
          },
        }
      : {}),
  }
}

function enableApprovalStages() {
  if (props.approvalStages) {
    emit('update:approvalStages', null)
    return
  }
  emit('update:approvalStages', [
    currentRouteStage(),
    {
      code: 'stage_2',
      name: '阶段 2',
      approverIds: [''],
      approvalMode: 'SEQUENTIAL',
      approverSource: { kind: 'FIXED', sourceId: null },
    },
  ])
}

function updateApprovalStage(index: number, patch: Partial<FlowApprovalStage>) {
  if (!props.approvalStages?.[index]) return
  const stages = props.approvalStages.map(stage => stageCopy(stage))
  stages[index] = { ...stages[index]!, ...patch }
  emit('update:approvalStages', stages)
}

function updateLaterStageSource(index: number, kind: 'FIXED' | 'PREVIOUS_HANDLER') {
  updateApprovalStage(index, {
    approverSource: kind === 'PREVIOUS_HANDLER'
      ? { kind: 'PREVIOUS_HANDLER' }
      : { kind: 'FIXED', sourceId: null },
    approverIds: kind === 'PREVIOUS_HANDLER'
      ? []
      : (props.approvalStages?.[index]?.approverIds.length
        ? [...props.approvalStages[index]!.approverIds]
        : ['']),
  })
}

function updateStageMode(index: number, approvalMode: FlowApprovalMode) {
  updateApprovalStage(index, {
    approvalMode,
    ...(approvalMode === 'QUORUM'
      ? { quorumRule: props.approvalStages?.[index]?.quorumRule
          ? { ...props.approvalStages[index]!.quorumRule }
          : { type: 'COUNT', value: 1 } }
      : { quorumRule: undefined }),
  })
}

function updateStageMember(index: number, memberIndex: number, memberId: string) {
  const stage = props.approvalStages?.[index]
  if (!stage) return
  const approverIds = [...stage.approverIds]
  approverIds[memberIndex] = memberId
  updateApprovalStage(index, { approverIds })
}

function addStageMember(index: number) {
  const stage = props.approvalStages?.[index]
  if (!stage || stage.approverIds.length >= MAX_FLOW_APPROVAL_STEPS) return
  updateApprovalStage(index, { approverIds: [...stage.approverIds, ''] })
}

function removeStageMember(index: number, memberIndex: number) {
  const stage = props.approvalStages?.[index]
  if (!stage || stage.approverIds.length <= 1) return
  updateApprovalStage(index, {
    approverIds: stage.approverIds.filter((_, current) => current !== memberIndex),
  })
}

function toggleStageDeadline(index: number, enabled: boolean) {
  updateApprovalStage(index, {
    deadlinePolicy: enabled
      ? { timeoutMinutes: 60, remindBeforeMinutes: null, timeoutAction: 'NONE' }
      : undefined,
  })
}

function toggleStageDecisionComment(index: number, enabled: boolean) {
  updateApprovalStage(index, {
    decisionCommentPolicy: enabled
      ? { approveRequired: false, rejectRequired: true, minimumLength: 1 }
      : undefined,
  })
}

function toggleStageDecisionEvidence(index: number, enabled: boolean) {
  updateApprovalStage(index, {
    decisionEvidencePolicy: enabled
      ? {
          minimumAttachments: 0,
          maximumAttachments: 5,
          allowedMimeFamilies: [],
          signatureMode: 'NONE',
        }
      : undefined,
  })
}

function updateStageDecisionEvidence(
  index: number,
  patch: Partial<FlowDecisionEvidencePolicy>,
) {
  const policy = props.approvalStages?.[index]?.decisionEvidencePolicy
  if (!policy) return
  updateApprovalStage(index, {
    decisionEvidencePolicy: { ...policy, ...patch },
  })
}

function toggleStageMimeFamily(
  index: number,
  family: FlowDecisionEvidencePolicy['allowedMimeFamilies'][number],
  enabled: boolean,
) {
  const policy = props.approvalStages?.[index]?.decisionEvidencePolicy
  if (!policy) return
  const allowedMimeFamilies = enabled
    ? [...new Set([...policy.allowedMimeFamilies, family])]
    : policy.allowedMimeFamilies.filter(item => item !== family)
  updateStageDecisionEvidence(index, { allowedMimeFamilies })
}

function addApprovalStage() {
  if (!props.approvalStages || props.approvalStages.length >= 10) return
  const codes = new Set(props.approvalStages.map(stage => stage.code))
  let nextIndex = props.approvalStages.length + 1
  while (codes.has(`stage_${nextIndex}`)) nextIndex += 1
  emit('update:approvalStages', [
    ...props.approvalStages.map(stage => stageCopy(stage)),
    {
      code: `stage_${nextIndex}`,
      name: `阶段 ${nextIndex}`,
      approverIds: [''],
      approvalMode: 'SEQUENTIAL',
      approverSource: { kind: 'FIXED', sourceId: null },
    },
  ])
}

function removeApprovalStage(index: number) {
  if (!props.approvalStages || props.approvalStages.length <= 2) return
  emit(
    'update:approvalStages',
    props.approvalStages
      .filter((_, current) => current !== index)
      .map(stage => stageCopy(stage)),
  )
}

function canMoveApprovalStage(index: number, direction: -1 | 1) {
  if (!props.approvalStages) return false
  const target = index + direction
  if (target < 0 || target >= props.approvalStages.length) return false
  const stages = props.approvalStages.map(stage => stageCopy(stage))
  ;[stages[index], stages[target]] = [stages[target]!, stages[index]!]
  return stages[0]?.approverSource.kind !== 'PREVIOUS_HANDLER'
    && stages.slice(1).every(stage =>
      stage.approverSource.kind === 'FIXED'
      || stage.approverSource.kind === 'PREVIOUS_HANDLER')
}

function moveApprovalStage(index: number, direction: -1 | 1) {
  if (!props.approvalStages || !canMoveApprovalStage(index, direction)) return
  const target = index + direction
  const stages = props.approvalStages.map(stage => stageCopy(stage))
  ;[stages[index], stages[target]] = [stages[target]!, stages[index]!]
  emit('update:approvalStages', stages)
  const first = stages[0]!
  emit('update:modelValue', [...first.approverIds])
  emit('update:approvalMode', first.approvalMode)
  emit('update:approverSource', { ...first.approverSource })
  emit('update:quorumRule', first.quorumRule ? { ...first.quorumRule } : null)
  emit('update:deadlinePolicy', first.deadlinePolicy ? { ...first.deadlinePolicy } : null)
  emit(
    'update:decisionCommentPolicy',
    first.decisionCommentPolicy ? { ...first.decisionCommentPolicy } : null,
  )
}

const contextualStageSourceKinds = new Set<FlowApproverSourceKind>([
  'PREVIOUS_HANDLER',
  'REQUESTER',
  'REQUESTER_MANAGER',
  'REQUESTER_DEPARTMENT_LEADER',
])

function branchStageSource(kind: FlowApproverSourceKind): FlowApproverSource {
  if (contextualStageSourceKinds.has(kind)) return { kind }
  if (kind === 'RECORD_MEMBER_FIELD') {
    return {
      kind,
      sourceId: '',
      moduleCode: props.recordContextModuleCode.trim(),
    }
  }
  return { kind, sourceId: kind === 'FIXED' ? null : '' }
}

function branchStageZeroPatch(stages: FlowApprovalStage[]) {
  const first = stages[0]!
  return {
    approvalStages: stages.map(stage => stageCopy(stage)),
    approverIds: first.approverSource.kind === 'FIXED' ? [...first.approverIds] : [],
    approvalMode: first.approvalMode,
    approverSource: { ...first.approverSource },
    quorumRule: first.quorumRule ? { ...first.quorumRule } : undefined,
    deadlinePolicy: first.deadlinePolicy ? { ...first.deadlinePolicy } : undefined,
    decisionCommentPolicy: first.decisionCommentPolicy
      ? { ...first.decisionCommentPolicy }
      : undefined,
    decisionEvidencePolicy: first.decisionEvidencePolicy
      ? {
          ...first.decisionEvidencePolicy,
          allowedMimeFamilies: [...first.decisionEvidencePolicy.allowedMimeFamilies],
        }
      : undefined,
  }
}

function updateActiveBranchApprovalStages(stages?: FlowApprovalStage[]) {
  const branchIndex = activeBranchIndex.value
  if (branchIndex === null) return
  const patch = stages?.length
    ? branchStageZeroPatch(stages)
    : { approvalStages: undefined }
  if (props.parallelGateway) {
    updateParallelBranch(branchIndex, patch)
  } else {
    updateBranch(branchIndex, patch)
  }
}

function toggleActiveBranchApprovalStages() {
  if (activeBranchApprovalStages.value) {
    updateActiveBranchApprovalStages()
    return
  }
  updateActiveBranchApprovalStages([
    currentRouteStage('branch_stage_1', '分支阶段 1'),
  ])
}

function updateBranchApprovalStage(index: number, patch: Partial<FlowApprovalStage>) {
  const configured = activeBranchApprovalStages.value
  if (!configured?.[index]) return
  const stages = configured.map(stage => stageCopy(stage))
  stages[index] = { ...stages[index]!, ...patch }
  updateActiveBranchApprovalStages(stages)
}

function updateBranchStageSource(index: number, kind: FlowApproverSourceKind) {
  if (index === 0 && kind === 'PREVIOUS_HANDLER') return
  const stage = activeBranchApprovalStages.value?.[index]
  if (!stage) return
  updateBranchApprovalStage(index, {
    approverSource: branchStageSource(kind),
    approverIds: kind === 'FIXED'
      ? (stage.approverIds.length ? [...stage.approverIds] : [''])
      : [],
  })
}

function updateBranchStageSourceId(index: number, sourceId: string) {
  const stage = activeBranchApprovalStages.value?.[index]
  if (!stage) return
  updateBranchApprovalStage(index, {
    approverSource: { ...stage.approverSource, sourceId },
  })
}

function updateBranchStageModuleCode(index: number, moduleCode: string) {
  const stage = activeBranchApprovalStages.value?.[index]
  if (!stage) return
  updateBranchApprovalStage(index, {
    approverSource: {
      kind: 'RECORD_MEMBER_FIELD',
      moduleCode,
      sourceId: stage.approverSource.moduleCode === moduleCode
        ? stage.approverSource.sourceId
        : '',
    },
  })
}

function updateBranchStageMode(index: number, approvalMode: FlowApprovalMode) {
  const stage = activeBranchApprovalStages.value?.[index]
  if (!stage) return
  updateBranchApprovalStage(index, {
    approvalMode,
    quorumRule: approvalMode === 'QUORUM'
      ? (stage.quorumRule ? { ...stage.quorumRule } : { type: 'COUNT', value: 1 })
      : undefined,
  })
}

function updateBranchStageMember(index: number, memberIndex: number, memberId: string) {
  const stage = activeBranchApprovalStages.value?.[index]
  if (!stage) return
  const approverIds = [...stage.approverIds]
  approverIds[memberIndex] = memberId
  updateBranchApprovalStage(index, { approverIds })
}

function addBranchStageMember(index: number) {
  const stage = activeBranchApprovalStages.value?.[index]
  if (!stage || stage.approverIds.length >= MAX_FLOW_APPROVAL_STEPS) return
  updateBranchApprovalStage(index, { approverIds: [...stage.approverIds, ''] })
}

function removeBranchStageMember(index: number, memberIndex: number) {
  const stage = activeBranchApprovalStages.value?.[index]
  if (!stage || stage.approverIds.length <= 1) return
  updateBranchApprovalStage(index, {
    approverIds: stage.approverIds.filter((_, current) => current !== memberIndex),
  })
}

function addBranchApprovalStage() {
  const configured = activeBranchApprovalStages.value
  if (!configured || configured.length >= 10) return
  const codes = new Set(configured.map(stage => stage.code))
  let next = configured.length + 1
  while (codes.has(`branch_stage_${next}`)) next += 1
  updateActiveBranchApprovalStages([
    ...configured.map(stage => stageCopy(stage)),
    {
      code: `branch_stage_${next}`,
      name: `分支阶段 ${next}`,
      approverIds: [''],
      approvalMode: 'SEQUENTIAL',
      approverSource: { kind: 'FIXED', sourceId: null },
    },
  ])
}

function removeBranchApprovalStage(index: number) {
  const configured = activeBranchApprovalStages.value
  if (!configured || configured.length <= 1) return
  const stages = configured
    .filter((_, current) => current !== index)
    .map(stage => stageCopy(stage))
  if (stages[0]?.approverSource.kind === 'PREVIOUS_HANDLER') return
  updateActiveBranchApprovalStages(stages)
}

function canMoveBranchApprovalStage(index: number, direction: -1 | 1) {
  const configured = activeBranchApprovalStages.value
  if (!configured) return false
  const target = index + direction
  if (target < 0 || target >= configured.length) return false
  const firstAfterMove = target === 0
    ? configured[index]
    : index === 0
      ? configured[target]
      : configured[0]
  return firstAfterMove?.approverSource.kind !== 'PREVIOUS_HANDLER'
}

function moveBranchApprovalStage(index: number, direction: -1 | 1) {
  const configured = activeBranchApprovalStages.value
  if (!configured || !canMoveBranchApprovalStage(index, direction)) return
  const target = index + direction
  const stages = configured.map(stage => stageCopy(stage))
  ;[stages[index], stages[target]] = [stages[target]!, stages[index]!]
  updateActiveBranchApprovalStages(stages)
}

function toggleBranchStageDeadline(index: number, enabled: boolean) {
  updateBranchApprovalStage(index, {
    deadlinePolicy: enabled
      ? { timeoutMinutes: 60, remindBeforeMinutes: null, timeoutAction: 'NONE' }
      : undefined,
  })
}

function toggleBranchStageDecisionComment(index: number, enabled: boolean) {
  updateBranchApprovalStage(index, {
    decisionCommentPolicy: enabled
      ? { approveRequired: false, rejectRequired: true, minimumLength: 1 }
      : undefined,
  })
}

function toggleBranchStageDecisionEvidence(index: number, enabled: boolean) {
  updateBranchApprovalStage(index, {
    decisionEvidencePolicy: enabled
      ? {
          minimumAttachments: 0,
          maximumAttachments: 5,
          allowedMimeFamilies: [],
          signatureMode: 'NONE',
        }
      : undefined,
  })
}

function updateBranchStageDecisionEvidence(
  index: number,
  patch: Partial<FlowDecisionEvidencePolicy>,
) {
  const policy = activeBranchApprovalStages.value?.[index]?.decisionEvidencePolicy
  if (!policy) return
  updateBranchApprovalStage(index, {
    decisionEvidencePolicy: { ...policy, ...patch },
  })
}

function toggleBranchStageMimeFamily(
  index: number,
  family: FlowDecisionEvidencePolicy['allowedMimeFamilies'][number],
  enabled: boolean,
) {
  const policy = activeBranchApprovalStages.value?.[index]?.decisionEvidencePolicy
  if (!policy) return
  const allowedMimeFamilies = enabled
    ? [...new Set([...policy.allowedMimeFamilies, family])]
    : policy.allowedMimeFamilies.filter(item => item !== family)
  updateBranchStageDecisionEvidence(index, { allowedMimeFamilies })
}

watch(
  () => activeRoute.value.length,
  (length) => {
    const index = selectedStepIndex.value
    if (index !== null && index >= length) {
      selectedNodeId.value = nodeId(activeBranchIndex.value, Math.max(0, length - 1))
    }
  },
)

watch(
  () => [
    props.systemId,
    activeApproverSource.value.kind,
    activeApproverSource.value.moduleCode ?? '',
  ] as const,
  ([nextSystemId, kind, moduleCode], previous) => {
    if (previous?.[0] !== nextSystemId) {
      departmentOptions.value = []
      loadedDepartmentSystemId.value = ''
      departmentLoadError.value = ''
      recordMemberFieldOptions.value = []
      loadedRecordMemberFieldKey.value = ''
      recordMemberFieldLoadError.value = ''
    }
    if (kind === 'DEPARTMENT_LEADER') void loadDepartmentOptions()
    if (kind === 'RECORD_MEMBER_FIELD' && moduleCode.trim()) {
      void loadRecordMemberFieldOptions(moduleCode)
    }
  },
  { immediate: true },
)

watch(
  () => props.modelValue,
  (route) => {
    const gateway = conditionGateway.value
    if (!gateway) return
    const mirrorIndex = props.inclusiveGateway ? 0 : gateway.branches.length - 1
    const current = gateway.branches[mirrorIndex]
    if (!current || current.approverIds.join('\u0000') === route.join('\u0000')) return
    updateBranch(mirrorIndex, { approverIds: [...route] }, false)
  },
  { deep: true },
)

watch(
  () => props.approvalMode,
  (approvalMode) => {
    const gateway = conditionGateway.value
    if (!gateway) return
    const mirrorIndex = props.inclusiveGateway ? 0 : gateway.branches.length - 1
    if (gateway.branches[mirrorIndex]?.approvalMode === approvalMode) return
    updateBranch(mirrorIndex, { approvalMode }, false)
  },
)

watch(
  () => props.approverSource,
  (approverSource) => {
    const gateway = conditionGateway.value
    if (props.parallelGateway) {
      if (props.parallelGateway.branches[0]?.approverSource?.kind === approverSource.kind
        && props.parallelGateway.branches[0]?.approverSource?.sourceId === approverSource.sourceId
        && props.parallelGateway.branches[0]?.approverSource?.moduleCode
          === approverSource.moduleCode) return
      updateParallelBranch(0, { approverSource })
      return
    }
    if (!gateway) return
    const mirrorIndex = props.inclusiveGateway ? 0 : gateway.branches.length - 1
    const current = gateway.branches[mirrorIndex]?.approverSource
    if (
      current?.kind === approverSource.kind
      && current.sourceId === approverSource.sourceId
      && current.moduleCode === approverSource.moduleCode
    ) return
    updateBranch(mirrorIndex, { approverSource }, false)
  },
  { deep: true },
)

watch(
  () => [
    props.modelValue,
    props.approvalMode,
    props.approverSource,
    props.quorumRule,
    props.deadlinePolicy,
    props.decisionCommentPolicy,
    props.decisionEvidencePolicy,
  ] as const,
  () => {
    const current = props.approvalStages?.[0]
    if (!current || props.gateway || props.parallelGateway || props.inclusiveGateway) return
    const next = currentRouteStage(current.code, current.name)
    if (JSON.stringify(current) === JSON.stringify(next)) return
    emit('update:approvalStages', [
      next,
      ...props.approvalStages!.slice(1).map(stage => stageCopy(stage)),
    ])
  },
  { deep: true },
)

watch(
  () => props.quorumRule,
  (quorumRule) => {
    const gateway = conditionGateway.value
    if (props.parallelGateway) {
      if (props.parallelGateway.branches[0]?.quorumRule?.type === quorumRule?.type
        && props.parallelGateway.branches[0]?.quorumRule?.value === quorumRule?.value) return
      updateParallelBranch(0, { quorumRule: quorumRule ?? undefined })
      return
    }
    if (!gateway) return
    const mirrorIndex = props.inclusiveGateway ? 0 : gateway.branches.length - 1
    const current = gateway.branches[mirrorIndex]?.quorumRule
    if (current?.type === quorumRule?.type && current?.value === quorumRule?.value) return
    updateBranch(mirrorIndex, { quorumRule: quorumRule ?? undefined }, false)
  },
  { deep: true },
)

watch(
  () => props.deadlinePolicy,
  (deadlinePolicy) => {
    if (props.parallelGateway) {
      const current = props.parallelGateway.branches[0]?.deadlinePolicy
      if (sameDeadlinePolicy(current, deadlinePolicy)) return
      updateParallelBranch(
        0,
        { deadlinePolicy: deadlinePolicy ?? undefined },
        false,
      )
      return
    }
    const gateway = conditionGateway.value
    if (!gateway) return
    const mirrorIndex = props.inclusiveGateway ? 0 : gateway.branches.length - 1
    const current = gateway.branches[mirrorIndex]?.deadlinePolicy
    if (sameDeadlinePolicy(current, deadlinePolicy)) return
    updateBranch(
      mirrorIndex,
      { deadlinePolicy: deadlinePolicy ?? undefined },
      false,
    )
  },
  { deep: true },
)

watch(
  () => props.decisionCommentPolicy,
  (decisionCommentPolicy) => {
    if (props.parallelGateway) {
      const current = props.parallelGateway.branches[0]?.decisionCommentPolicy
      if (sameDecisionCommentPolicy(current, decisionCommentPolicy)) return
      updateParallelBranch(
        0,
        { decisionCommentPolicy: decisionCommentPolicy ?? undefined },
        false,
      )
      return
    }
    const gateway = conditionGateway.value
    if (!gateway) return
    const mirrorIndex = props.inclusiveGateway ? 0 : gateway.branches.length - 1
    const current = gateway.branches[mirrorIndex]?.decisionCommentPolicy
    if (sameDecisionCommentPolicy(current, decisionCommentPolicy)) return
    updateBranch(
      mirrorIndex,
      { decisionCommentPolicy: decisionCommentPolicy ?? undefined },
      false,
    )
  },
  { deep: true },
)

watch(
  () => props.decisionEvidencePolicy,
  (decisionEvidencePolicy) => {
    if (props.parallelGateway) {
      const current = props.parallelGateway.branches[0]?.decisionEvidencePolicy
      if (sameDecisionEvidencePolicy(current, decisionEvidencePolicy)) return
      updateParallelBranch(
        0,
        { decisionEvidencePolicy: decisionEvidencePolicy ?? undefined },
        false,
      )
      return
    }
    const gateway = conditionGateway.value
    if (!gateway) return
    const mirrorIndex = props.inclusiveGateway ? 0 : gateway.branches.length - 1
    const current = gateway.branches[mirrorIndex]?.decisionEvidencePolicy
    if (sameDecisionEvidencePolicy(current, decisionEvidencePolicy)) return
    updateBranch(
      mirrorIndex,
      { decisionEvidencePolicy: decisionEvidencePolicy ?? undefined },
      false,
    )
  },
  { deep: true },
)

function sameDeadlinePolicy(
  left?: FlowDeadlinePolicy | null,
  right?: FlowDeadlinePolicy | null,
) {
  return left?.timeoutMinutes === right?.timeoutMinutes
    && (left?.remindBeforeMinutes ?? null) === (right?.remindBeforeMinutes ?? null)
    && left?.timeoutAction === right?.timeoutAction
}

function sameDecisionCommentPolicy(
  left?: FlowDecisionCommentPolicy | null,
  right?: FlowDecisionCommentPolicy | null,
) {
  return left?.approveRequired === right?.approveRequired
    && left?.rejectRequired === right?.rejectRequired
    && left?.minimumLength === right?.minimumLength
}

function sameDecisionEvidencePolicy(
  left?: FlowDecisionEvidencePolicy | null,
  right?: FlowDecisionEvidencePolicy | null,
) {
  if (!left || !right) return left === right
  return left.minimumAttachments === right.minimumAttachments
    && left.maximumAttachments === right.maximumAttachments
    && left.signatureMode === right.signatureMode
    && left.allowedMimeFamilies.join('\u0000') === right.allowedMimeFamilies.join('\u0000')
}

function selectNode(event: NodeMouseEvent) {
  selectedNodeId.value = event.node.id
}

function nodeId(branchIndex: number | null, stepIndex: number) {
  const approvalMode = branchIndex === null
    ? props.approvalMode
    : props.inclusiveGateway?.branches[branchIndex]?.approvalMode
      ?? props.parallelGateway?.branches[branchIndex]?.approvalMode
      ?? props.gateway?.branches[branchIndex]?.approvalMode
      ?? 'SEQUENTIAL'
  const visibleIndex = approvalMode === 'SEQUENTIAL' ? stepIndex : 0
  return branchIndex === null
    ? `approval-${visibleIndex}`
    : props.parallelGateway || props.inclusiveGateway
      ? `parallel-${branchIndex}-approval-${visibleIndex}`
      : `branch-${branchIndex}-approval-${visibleIndex}`
}

function mirrorLegacyBranchIntoStageZero<T extends FlowParallelBranchDraft | FlowGatewayBranchDraft>(
  branch: T,
  patch: Partial<T>,
): T {
  if (!branch.approvalStages?.length || 'approvalStages' in patch) return branch
  const approvalStages = branch.approvalStages
  const projectionKeys: Array<keyof FlowParallelBranchDraft> = [
    'approverIds',
    'approvalMode',
    'approverSource',
    'quorumRule',
    'deadlinePolicy',
    'decisionCommentPolicy',
    'decisionEvidencePolicy',
  ]
  if (!projectionKeys.some(key => key in patch)) return branch
  const projectedBranch = branch.approverSource?.kind === 'FIXED'
    ? branch
    : { ...branch, approverIds: [] }
  const first = approvalStages[0]!
  return {
    ...projectedBranch,
    approvalStages: [
      {
        ...stageCopy(first),
        approverIds: projectedBranch.approverSource?.kind === 'FIXED'
          ? [...projectedBranch.approverIds]
          : [],
        approvalMode: projectedBranch.approvalMode,
        approverSource: {
          ...(projectedBranch.approverSource ?? { kind: 'FIXED', sourceId: null }),
        },
        quorumRule: projectedBranch.approvalMode === 'QUORUM' && projectedBranch.quorumRule
          ? { ...projectedBranch.quorumRule }
          : undefined,
        deadlinePolicy: projectedBranch.deadlinePolicy
          ? { ...projectedBranch.deadlinePolicy }
          : undefined,
        decisionCommentPolicy: projectedBranch.decisionCommentPolicy
          ? { ...projectedBranch.decisionCommentPolicy }
          : undefined,
        decisionEvidencePolicy: projectedBranch.decisionEvidencePolicy
          ? {
              ...projectedBranch.decisionEvidencePolicy,
              allowedMimeFamilies: [
                ...projectedBranch.decisionEvidencePolicy.allowedMimeFamilies,
              ],
            }
          : undefined,
      },
      ...approvalStages.slice(1).map(stage => stageCopy(stage)),
    ],
  }
}

function updateParallelBranch(
  branchIndex: number,
  patch: Partial<FlowParallelBranchDraft>,
  syncPrimary = true,
) {
  const gateway = props.parallelGateway
  const branch = gateway?.branches[branchIndex]
  if (!gateway || !branch) return
  const branches = [...gateway.branches]
  branches[branchIndex] = mirrorLegacyBranchIntoStageZero(
    { ...branch, ...patch },
    patch,
  )
  emit('update:parallelGateway', { branches })
  if (syncPrimary && branchIndex === 0 && patch.approverIds) {
    emit('update:modelValue', [...patch.approverIds])
  }
  if (syncPrimary && branchIndex === 0 && patch.approvalMode) {
    emit('update:approvalMode', patch.approvalMode)
  }
  if (syncPrimary && branchIndex === 0 && patch.approverSource) {
    emit('update:approverSource', patch.approverSource)
  }
  if (syncPrimary && branchIndex === 0 && 'quorumRule' in patch) {
    emit('update:quorumRule', patch.quorumRule ?? null)
  }
  if (syncPrimary && branchIndex === 0 && 'deadlinePolicy' in patch) {
    emit('update:deadlinePolicy', patch.deadlinePolicy ?? null)
  }
  if (syncPrimary && branchIndex === 0 && 'decisionCommentPolicy' in patch) {
    emit('update:decisionCommentPolicy', patch.decisionCommentPolicy ?? null)
  }
  if (syncPrimary && branchIndex === 0 && 'decisionEvidencePolicy' in patch) {
    emit('update:decisionEvidencePolicy', patch.decisionEvidencePolicy ?? null)
  }
}

function updateBranch(
  branchIndex: number,
  patch: Partial<FlowGatewayBranchDraft>,
  syncDefault = true,
) {
  const gateway = conditionGateway.value
  const branch = gateway?.branches[branchIndex]
  if (!gateway || !branch) return
  const branches = [...gateway.branches]
  branches[branchIndex] = mirrorLegacyBranchIntoStageZero(
    { ...branch, ...patch },
    patch,
  )
  const next = { branches }
  if (props.inclusiveGateway) {
    emit('update:inclusiveGateway', next)
  } else {
    emit('update:gateway', next)
  }
  const mirrorsDefinition = props.inclusiveGateway
    ? branchIndex === 0
    : Boolean(branches[branchIndex]?.defaultBranch)
  if (syncDefault && mirrorsDefinition && patch.approverIds) {
    emit('update:modelValue', [...patch.approverIds])
  }
  if (syncDefault && mirrorsDefinition && patch.approvalMode) {
    emit('update:approvalMode', patch.approvalMode)
  }
  if (syncDefault && mirrorsDefinition && patch.approverSource) {
    emit('update:approverSource', patch.approverSource)
  }
  if (syncDefault && mirrorsDefinition && 'quorumRule' in patch) {
    emit('update:quorumRule', patch.quorumRule ?? null)
  }
  if (syncDefault && mirrorsDefinition && 'deadlinePolicy' in patch) {
    emit('update:deadlinePolicy', patch.deadlinePolicy ?? null)
  }
  if (syncDefault && mirrorsDefinition && 'decisionCommentPolicy' in patch) {
    emit('update:decisionCommentPolicy', patch.decisionCommentPolicy ?? null)
  }
  if (syncDefault && mirrorsDefinition && 'decisionEvidencePolicy' in patch) {
    emit('update:decisionEvidencePolicy', patch.decisionEvidencePolicy ?? null)
  }
}

function updateApprovalMode(approvalMode: FlowApprovalMode) {
  const branchIndex = activeBranchIndex.value
  const quorumRule = approvalMode === 'QUORUM'
    ? activeQuorumRule.value
    : undefined
  if (branchIndex === null) {
    emit('update:approvalMode', approvalMode)
    emit('update:quorumRule', quorumRule ?? null)
  } else if (props.parallelGateway) {
    updateParallelBranch(branchIndex, { approvalMode, quorumRule })
  } else {
    updateBranch(branchIndex, { approvalMode, quorumRule })
  }
  selectedNodeId.value = nodeId(branchIndex, 0)
}

function updateQuorumRuleType(type: FlowQuorumRuleType) {
  updateQuorumRule({ type, value: type === 'PERCENTAGE' ? 50 : 1 })
}

function updateQuorumRuleValue(value: string) {
  updateQuorumRule({ ...activeQuorumRule.value, value: Number(value) })
}

function updateQuorumRule(rule: FlowQuorumRule) {
  const branchIndex = activeBranchIndex.value
  if (branchIndex === null) {
    emit('update:quorumRule', rule)
  } else if (props.parallelGateway) {
    updateParallelBranch(branchIndex, { quorumRule: rule })
  } else {
    updateBranch(branchIndex, { quorumRule: rule })
  }
}

function updateDeadlinePolicy(policy: FlowDeadlinePolicy | null) {
  const branchIndex = activeBranchIndex.value
  if (branchIndex === null) {
    emit('update:deadlinePolicy', policy)
  } else if (props.parallelGateway) {
    updateParallelBranch(branchIndex, { deadlinePolicy: policy ?? undefined })
  } else {
    updateBranch(branchIndex, { deadlinePolicy: policy ?? undefined })
  }
}

function toggleDeadlinePolicy(enabled: boolean) {
  updateDeadlinePolicy(enabled
    ? {
        timeoutMinutes: 1440,
        remindBeforeMinutes: 60,
        timeoutAction: 'NONE',
      }
    : null)
}

function updateDeadlineTimeout(value: string) {
  const policy = activeDeadlinePolicy.value
  if (!policy) return
  updateDeadlinePolicy({ ...policy, timeoutMinutes: Number(value) })
}

function updateDeadlineReminder(value: string) {
  const policy = activeDeadlinePolicy.value
  if (!policy) return
  const normalized = value.trim()
  updateDeadlinePolicy({
    ...policy,
    remindBeforeMinutes: normalized ? Number(normalized) : null,
  })
}

function updateDeadlineAction(timeoutAction: FlowDeadlineTimeoutAction) {
  const policy = activeDeadlinePolicy.value
  if (!policy) return
  updateDeadlinePolicy({ ...policy, timeoutAction })
}

function updateDecisionCommentPolicy(policy: FlowDecisionCommentPolicy | null) {
  const branchIndex = activeBranchIndex.value
  if (branchIndex === null) {
    emit('update:decisionCommentPolicy', policy)
  } else if (props.parallelGateway) {
    updateParallelBranch(branchIndex, {
      decisionCommentPolicy: policy ?? undefined,
    })
  } else {
    updateBranch(branchIndex, {
      decisionCommentPolicy: policy ?? undefined,
    })
  }
}

function toggleDecisionCommentPolicy(enabled: boolean) {
  updateDecisionCommentPolicy(enabled
    ? {
        approveRequired: false,
        rejectRequired: true,
        minimumLength: 1,
      }
    : null)
}

function updateDecisionCommentRequired(
  action: 'approve' | 'reject',
  required: boolean,
) {
  const policy = activeDecisionCommentPolicy.value
  if (!policy) return
  updateDecisionCommentPolicy({
    ...policy,
    [action === 'approve' ? 'approveRequired' : 'rejectRequired']: required,
  })
}

function updateDecisionCommentMinimumLength(value: string) {
  const policy = activeDecisionCommentPolicy.value
  if (!policy) return
  updateDecisionCommentPolicy({ ...policy, minimumLength: Number(value) })
}

function updateDecisionEvidencePolicy(policy: FlowDecisionEvidencePolicy | null) {
  const branchIndex = activeBranchIndex.value
  if (branchIndex === null) {
    emit('update:decisionEvidencePolicy', policy)
  } else if (props.parallelGateway) {
    updateParallelBranch(branchIndex, {
      decisionEvidencePolicy: policy ?? undefined,
    })
  } else {
    updateBranch(branchIndex, {
      decisionEvidencePolicy: policy ?? undefined,
    })
  }
}

function toggleDecisionEvidencePolicy(enabled: boolean) {
  updateDecisionEvidencePolicy(enabled
    ? {
        minimumAttachments: 0,
        maximumAttachments: 5,
        allowedMimeFamilies: [],
        signatureMode: 'NONE',
      }
    : null)
}

function updateDecisionEvidenceBounds(
  key: 'minimumAttachments' | 'maximumAttachments',
  value: string,
) {
  const policy = activeDecisionEvidencePolicy.value
  if (!policy) return
  updateDecisionEvidencePolicy({ ...policy, [key]: Number(value) })
}

function updateDecisionEvidenceSignatureMode(
  signatureMode: FlowDecisionEvidencePolicy['signatureMode'],
) {
  const policy = activeDecisionEvidencePolicy.value
  if (!policy) return
  updateDecisionEvidencePolicy({ ...policy, signatureMode })
}

function toggleDecisionEvidenceMimeFamily(
  family: FlowDecisionEvidencePolicy['allowedMimeFamilies'][number],
  enabled: boolean,
) {
  const policy = activeDecisionEvidencePolicy.value
  if (!policy) return
  const allowedMimeFamilies = enabled
    ? [...new Set([...policy.allowedMimeFamilies, family])]
    : policy.allowedMimeFamilies.filter(item => item !== family)
  updateDecisionEvidencePolicy({ ...policy, allowedMimeFamilies })
}

function updateApproverSource(source: FlowApproverSource) {
  const branchIndex = activeBranchIndex.value
  if (branchIndex === null) {
    emit('update:approverSource', source)
  } else if (props.parallelGateway) {
    updateParallelBranch(branchIndex, { approverSource: source })
  } else {
    updateBranch(branchIndex, { approverSource: source })
  }
}

function updateApproverSourceKind(kind: FlowApproverSourceKind) {
  if (kind === 'DEPARTMENT_LEADER') void loadDepartmentOptions()
  const moduleCode = kind === 'RECORD_MEMBER_FIELD'
    ? props.recordContextModuleCode.trim()
    : undefined
  updateApproverSource({
    kind,
    sourceId: kind === 'FIXED'
      || kind === 'REQUESTER'
      || kind === 'REQUESTER_MANAGER'
      || kind === 'REQUESTER_DEPARTMENT_LEADER'
      ? null
      : '',
    ...(kind === 'RECORD_MEMBER_FIELD' ? { moduleCode } : {}),
  })
  if (kind === 'RECORD_MEMBER_FIELD' && moduleCode) {
    void loadRecordMemberFieldOptions(moduleCode)
  }
}

function updateApproverSourceId(sourceId: string) {
  updateApproverSource({ ...activeApproverSource.value, sourceId })
}

function updateRecordMemberModuleCode(value: string) {
  const moduleCode = value.trim()
  const currentModuleCode = activeApproverSource.value.moduleCode?.trim() ?? ''
  updateApproverSource({
    kind: 'RECORD_MEMBER_FIELD',
    moduleCode,
    sourceId: moduleCode === currentModuleCode
      ? activeApproverSource.value.sourceId
      : '',
  })
  if (moduleCode) void loadRecordMemberFieldOptions(moduleCode)
}

async function loadRecordMemberFieldOptions(moduleCode: string, force = false) {
  const normalizedModuleCode = moduleCode.trim()
  if (!normalizedModuleCode) {
    recordMemberFieldOptions.value = []
    recordMemberFieldLoadError.value = ''
    loadedRecordMemberFieldKey.value = ''
    return
  }
  const key = `${props.systemId}\u0000${normalizedModuleCode}`
  if (!force && loadedRecordMemberFieldKey.value === key) return
  if (loadedRecordMemberFieldKey.value !== key) {
    recordMemberFieldOptions.value = []
    loadedRecordMemberFieldKey.value = ''
  }
  const sequence = ++recordMemberFieldRequestSequence
  recordMemberFieldLoading.value = true
  recordMemberFieldLoadError.value = ''
  try {
    const result = await flowApi.listRecordMemberFields(props.systemId, normalizedModuleCode)
    if (sequence !== recordMemberFieldRequestSequence) return
    recordMemberFieldOptions.value = result.items
    loadedRecordMemberFieldKey.value = key
  } catch (error) {
    if (sequence !== recordMemberFieldRequestSequence) return
    recordMemberFieldOptions.value = []
    loadedRecordMemberFieldKey.value = ''
    recordMemberFieldLoadError.value = error instanceof Error
      ? error.message
      : '记录成员字段加载失败'
  } finally {
    if (sequence === recordMemberFieldRequestSequence) {
      recordMemberFieldLoading.value = false
    }
  }
}

async function loadDepartmentOptions() {
  if (departmentLoading.value || loadedDepartmentSystemId.value === props.systemId) return
  departmentLoading.value = true
  departmentLoadError.value = ''
  try {
    const result = await systemAdminApi.listDepartments(props.systemId, {
      page: 1,
      size: 200,
      status: 'ACTIVE',
    })
    departmentOptions.value = result.items.map(department => ({
      label: `${department.name}（${department.code}）`,
      value: department.id,
    }))
    loadedDepartmentSystemId.value = props.systemId
  } catch (error) {
    departmentOptions.value = []
    departmentLoadError.value = error instanceof Error ? error.message : '部门列表加载失败'
  } finally {
    departmentLoading.value = false
  }
}

function updateRoute(route: string[]) {
  const branchIndex = activeBranchIndex.value
  if (branchIndex === null) {
    emit('update:modelValue', route)
    return
  }
  if (props.parallelGateway) {
    updateParallelBranch(branchIndex, { approverIds: route })
  } else {
    updateBranch(branchIndex, { approverIds: route })
  }
}

function addStep() {
  const currentIndex = selectedStepIndex.value
  const afterIndex = currentIndex ?? activeRoute.value.length - 1
  const route = insertFlowApprovalStep(activeRoute.value, afterIndex)
  if (route.length === activeRoute.value.length) return
  selectedNodeId.value = nodeId(
    activeBranchIndex.value,
    Math.min(afterIndex + 1, route.length - 1),
  )
  updateRoute(route)
}

function assignStep(approverId: string) {
  const index = selectedStepIndex.value
  if (index === null) return
  updateRoute(assignFlowApprovalStep(activeRoute.value, index, approverId))
}

function assignMemberAt(index: number, approverId: string) {
  updateRoute(assignFlowApprovalStep(activeRoute.value, index, approverId))
}

function moveMember(index: number, offset: -1 | 1) {
  updateRoute(moveFlowApprovalStep(activeRoute.value, index, offset))
}

function removeMember(index: number) {
  updateRoute(removeFlowApprovalStep(activeRoute.value, index))
}

function moveStep(offset: -1 | 1) {
  const index = selectedStepIndex.value
  if (index === null) return
  const route = moveFlowApprovalStep(activeRoute.value, index, offset)
  const nextIndex = index + offset
  if (route[index] === activeRoute.value[index]) return
  selectedNodeId.value = nodeId(activeBranchIndex.value, nextIndex)
  updateRoute(route)
}

function removeStep() {
  const index = selectedStepIndex.value
  if (index === null) return
  const route = removeFlowApprovalStep(activeRoute.value, index)
  if (route.length === activeRoute.value.length) return
  selectedNodeId.value = nodeId(activeBranchIndex.value, Math.min(index, route.length - 1))
  updateRoute(route)
}

function enableGateway() {
  if (props.gateway || props.parallelGateway || props.inclusiveGateway) return
  const gateway = createFlowGatewayDraft(props.modelValue, props.approvalMode)
  const mirrorIndex = gateway.branches.length - 1
  gateway.branches[mirrorIndex] = {
    ...gateway.branches[mirrorIndex]!,
    ...(props.deadlinePolicy ? { deadlinePolicy: { ...props.deadlinePolicy } } : {}),
    ...(props.decisionCommentPolicy
      ? { decisionCommentPolicy: { ...props.decisionCommentPolicy } }
      : {}),
  }
  emit('update:gateway', gateway)
  selectedNodeId.value = 'gateway'
}

function enableParallelGateway() {
  if (props.gateway || props.parallelGateway || props.inclusiveGateway) return
  const gateway = createFlowParallelGatewayDraft(props.modelValue, props.approvalMode)
  gateway.branches[0] = {
    ...gateway.branches[0]!,
    ...(props.deadlinePolicy ? { deadlinePolicy: { ...props.deadlinePolicy } } : {}),
    ...(props.decisionCommentPolicy
      ? { decisionCommentPolicy: { ...props.decisionCommentPolicy } }
      : {}),
  }
  emit(
    'update:parallelGateway',
    gateway,
  )
  selectedNodeId.value = 'parallel-split'
}

function enableInclusiveGateway() {
  if (props.gateway || props.parallelGateway || props.inclusiveGateway) return
  const gateway = createFlowInclusiveGatewayDraft(props.modelValue, props.approvalMode)
  gateway.branches[0] = {
    ...gateway.branches[0]!,
    ...(props.deadlinePolicy ? { deadlinePolicy: { ...props.deadlinePolicy } } : {}),
    ...(props.decisionCommentPolicy
      ? { decisionCommentPolicy: { ...props.decisionCommentPolicy } }
      : {}),
  }
  emit(
    'update:inclusiveGateway',
    gateway,
  )
  selectedNodeId.value = 'parallel-split'
}

function removeParallelGateway() {
  emit('update:parallelGateway', null)
  selectedNodeId.value = 'approval-0'
}

function addParallelBranch() {
  const gateway = props.parallelGateway
  if (!gateway || gateway.branches.length >= 5) return
  const suffix = gateway.branches.length + 1
  emit('update:parallelGateway', {
    branches: [
      ...gateway.branches,
      {
        code: `branch_${suffix}`,
        name: `并行分支 ${suffix}`,
        approverIds: [''],
        approvalMode: 'SEQUENTIAL',
        approverSource: { kind: 'FIXED', sourceId: null },
      },
    ],
  })
}

function removeParallelBranch(branchIndex: number) {
  const gateway = props.parallelGateway
  if (!gateway || gateway.branches.length <= 2) return
  emit('update:parallelGateway', {
    branches: gateway.branches.filter((_, index) => index !== branchIndex),
  })
  selectedNodeId.value = 'parallel-split'
}

function updateParallelBranchName(branchIndex: number, value: string) {
  updateParallelBranch(branchIndex, { name: value })
}

function updateParallelBranchMode(branchIndex: number, value: string) {
  const approvalMode = value as FlowApprovalMode
  updateParallelBranch(branchIndex, {
    approvalMode,
    quorumRule: approvalMode === 'QUORUM'
      ? props.parallelGateway?.branches[branchIndex]?.quorumRule
        ?? { type: 'COUNT', value: 1 }
      : undefined,
  })
}

function removeGateway() {
  if (props.inclusiveGateway) {
    emit('update:inclusiveGateway', null)
  } else {
    emit('update:gateway', null)
  }
  selectedNodeId.value = 'approval-0'
}

function addConditionalBranch() {
  const gateway = conditionGateway.value
  const limit = props.inclusiveGateway ? 5 : 6
  if (!gateway || gateway.branches.length >= limit) return
  const conditionalCount = gateway.branches.length - 1
  const suffix = conditionalCount + 1
  const branches = [...gateway.branches]
  branches.splice(branches.length - 1, 0, {
    code: `condition_${suffix}`,
    name: `条件分支 ${suffix}`,
    defaultBranch: false,
    conditions: [{ fieldCode: '', operator: 'EQ', valueText: '' }],
    approverIds: [''],
    approvalMode: 'SEQUENTIAL',
    approverSource: { kind: 'FIXED', sourceId: null },
  })
  if (props.inclusiveGateway) {
    emit('update:inclusiveGateway', { branches })
  } else {
    emit('update:gateway', { branches })
  }
}

function removeConditionalBranch(branchIndex: number) {
  const gateway = conditionGateway.value
  if (!gateway || gateway.branches.length <= 2) return
  const branch = gateway.branches[branchIndex]
  if (!branch || branch.defaultBranch) return
  const branches = gateway.branches.filter((_, index) => index !== branchIndex)
  if (props.inclusiveGateway) {
    emit('update:inclusiveGateway', { branches })
    selectedNodeId.value = 'parallel-split'
  } else {
    emit('update:gateway', { branches })
    selectedNodeId.value = 'gateway'
  }
}

function updateBranchName(branchIndex: number, value: string) {
  updateBranch(branchIndex, { name: value })
}

function updateBranchApprovalMode(branchIndex: number, value: string) {
  const approvalMode = value as FlowApprovalMode
  updateBranch(branchIndex, {
    approvalMode,
    quorumRule: approvalMode === 'QUORUM'
      ? conditionGateway.value?.branches[branchIndex]?.quorumRule
        ?? { type: 'COUNT', value: 1 }
      : undefined,
  })
}

function addBranchCondition(branchIndex: number) {
  const branch = conditionGateway.value?.branches[branchIndex]
  if (!branch || branch.defaultBranch || branch.conditions.length >= 10) return
  updateBranch(branchIndex, {
    conditions: [
      ...branch.conditions,
      { fieldCode: '', operator: 'EQ', valueText: '' },
    ],
  })
}

function updateBranchCondition(
  branchIndex: number,
  conditionIndex: number,
  field: 'fieldCode' | 'operator' | 'valueText',
  value: string,
) {
  const branch = conditionGateway.value?.branches[branchIndex]
  const condition = branch?.conditions[conditionIndex]
  if (!branch || !condition) return
  const conditions = [...branch.conditions]
  conditions[conditionIndex] = { ...condition, [field]: value }
  updateBranch(branchIndex, { conditions })
}

function removeBranchCondition(branchIndex: number, conditionIndex: number) {
  const branch = conditionGateway.value?.branches[branchIndex]
  if (!branch || branch.conditions.length <= 1) return
  updateBranch(branchIndex, {
    conditions: branch.conditions.filter((_, index) => index !== conditionIndex),
  })
}
</script>

<template>
  <section class="flow-draft-canvas">
    <header class="flow-draft-canvas-toolbar">
      <div>
        <strong>流程画布</strong>
        <small>画布顺序就是发布后的执行顺序</small>
      </div>
      <div class="flow-draft-node-library">
        <button
          class="flow-approval-stages-toggle"
          type="button"
          :disabled="busy || Boolean(gateway) || Boolean(parallelGateway)
            || Boolean(inclusiveGateway)"
          @click="enableApprovalStages"
        >
          {{ approvalStages ? '关闭多阶段' : '开启线性多阶段' }}
        </button>
        <button
          class="flow-graph-add-approval"
          type="button"
          :disabled="busy || activeApproverSource.kind !== 'FIXED' || activeRoute.length >= MAX_FLOW_APPROVAL_STEPS"
          @click="addStep"
        >
          <Plus :size="14" />审批节点
        </button>
        <button
          class="flow-graph-add-gateway"
          type="button"
          :disabled="busy || Boolean(approvalStages) || Boolean(gateway)
            || Boolean(parallelGateway) || Boolean(inclusiveGateway)"
          :title="gateway || parallelGateway || inclusiveGateway ? '当前草稿已配置网关' : '添加一个排他条件网关'"
          @click="enableGateway"
        >
          条件网关
        </button>
        <button
          class="flow-graph-add-parallel"
          type="button"
          :disabled="busy || Boolean(approvalStages) || Boolean(gateway)
            || Boolean(parallelGateway) || Boolean(inclusiveGateway)"
          :title="gateway || parallelGateway || inclusiveGateway ? '当前草稿已配置网关' : '添加并行拆分与汇合'"
          @click="enableParallelGateway"
        >
          并行网关
        </button>
        <button
          class="flow-graph-add-inclusive"
          type="button"
          :disabled="busy || Boolean(approvalStages) || Boolean(gateway)
            || Boolean(parallelGateway) || Boolean(inclusiveGateway)"
          :title="gateway || parallelGateway || inclusiveGateway ? '当前草稿已配置网关' : '添加命中全部条件分支的包容网关'"
          @click="enableInclusiveGateway"
        >
          包容网关
        </button>
        <button type="button" disabled title="后续功能批次">外部任务</button>
        <span>{{ activeRoute.length }}/{{ MAX_FLOW_APPROVAL_STEPS }}</span>
      </div>
    </header>

    <div class="flow-draft-canvas-workspace">
      <div class="flow-draft-canvas-stage">
        <VueFlow
          class="flow-draft-vue-flow"
          :nodes="projection.nodes"
          :edges="projection.edges"
          :nodes-draggable="false"
          :nodes-connectable="false"
          :elements-selectable="true"
          :fit-view-on-init="true"
          :min-zoom="0.35"
          :max-zoom="1.5"
          @node-click="selectNode"
        >
          <template #node-flowDraft="{ data, selected }">
            <div
              class="flow-draft-node"
              :class="[
                `flow-draft-node-${data.kind}`,
                data.outcome ? `flow-draft-node-${data.outcome.toLowerCase()}` : '',
                { selected },
              ]"
            >
              <Handle
                v-if="data.kind !== 'trigger'"
                type="target"
                :position="Position.Left"
                :connectable="false"
              />
              <span v-if="data.kind === 'approval'" class="flow-draft-node-index">
                {{ Number(data.stepIndex) + 1 }}
              </span>
              <div>
                <strong>{{ data.title }}</strong>
                <small>{{ data.subtitle }}</small>
              </div>
              <Handle
                v-if="data.kind !== 'terminal'"
                type="source"
                :position="Position.Right"
                :connectable="false"
              />
            </div>
          </template>
        </VueFlow>
      </div>

      <aside class="flow-draft-inspector">
        <template v-if="selectedStepIndex !== null">
          <header>
            <strong>{{ activeApprovalMode === 'SEQUENTIAL' ? `审批 ${selectedStepIndex + 1}` : '并行审批组' }}</strong>
            <small>
              {{ selectedBranch ? `${selectedBranch.name} · 节点属性` : '节点属性' }}
            </small>
          </header>
          <label>
            审批来源
            <select
              class="flow-approver-source-kind"
              :value="activeApproverSource.kind"
              :disabled="busy"
              @change="updateApproverSourceKind(($event.target as HTMLSelectElement).value as FlowApproverSourceKind)"
            >
              <option value="FIXED">固定成员</option>
              <option value="ROLE">租户角色</option>
              <option value="DEPARTMENT">租户部门</option>
              <option value="DEPARTMENT_LEADER">部门负责人</option>
              <option value="REQUESTER">请求人本人</option>
              <option value="REQUESTER_MANAGER">请求人的直属主管</option>
              <option value="REQUESTER_DEPARTMENT_LEADER">请求人所属部门负责人</option>
              <option value="RECORD_MEMBER_FIELD">记录的成员字段</option>
            </select>
          </label>
          <label
            v-if="activeApproverSource.kind === 'ROLE'
              || activeApproverSource.kind === 'DEPARTMENT'"
          >
            来源 ID
            <input
              class="flow-approver-source-id"
              :value="activeApproverSource.sourceId ?? ''"
              :disabled="busy"
              inputmode="numeric"
              placeholder="输入角色或部门 ID"
              @input="updateApproverSourceId(($event.target as HTMLInputElement).value)"
            />
            <small>
              当前成员预览：{{ activeRoute.filter(Boolean).join('、') || '保存后由服务端解析' }}
            </small>
          </label>
          <label v-else-if="activeApproverSource.kind === 'DEPARTMENT_LEADER'">
            负责人所属部门
            <select
              class="flow-approver-department-leader"
              :value="activeApproverSource.sourceId ?? ''"
              :disabled="busy || departmentLoading"
              @change="updateApproverSourceId(($event.target as HTMLSelectElement).value)"
            >
              <option value="">
                {{ departmentLoading ? '部门加载中…' : '请选择启用部门' }}
              </option>
              <option
                v-for="department in departmentOptions"
                :key="department.value"
                :value="department.value"
              >
                {{ department.label }}
              </option>
            </select>
            <small v-if="departmentLoadError" class="flow-source-load-error">
              {{ departmentLoadError }}
            </small>
            <small v-else>
              启动时解析该部门当前有效负责人；已启动实例不会随负责人变更。
            </small>
          </label>
          <p
            v-else-if="activeApproverSource.kind === 'REQUESTER'
              || activeApproverSource.kind === 'REQUESTER_MANAGER'
              || activeApproverSource.kind === 'REQUESTER_DEPARTMENT_LEADER'"
            class="flow-requester-manager-source"
          >
            启动或模拟时根据请求上下文解析成员，无需填写来源 ID。
          </p>
          <div
            v-else-if="activeApproverSource.kind === 'RECORD_MEMBER_FIELD'"
            class="flow-record-member-source"
          >
            <label>
              记录模块
              <input
                class="flow-record-member-module"
                :value="activeApproverSource.moduleCode ?? ''"
                :disabled="busy"
                maxlength="64"
                placeholder="例如 purchase_order"
                @change="updateRecordMemberModuleCode(
                  ($event.target as HTMLInputElement).value,
                )"
              />
            </label>
            <label>
              单值成员字段
              <select
                class="flow-record-member-field"
                :value="activeApproverSource.sourceId ?? ''"
                :disabled="busy || recordMemberFieldLoading
                  || !activeApproverSource.moduleCode?.trim()"
                @change="updateApproverSourceId(($event.target as HTMLSelectElement).value)"
              >
                <option value="">
                  {{
                    !activeApproverSource.moduleCode?.trim()
                      ? '请先填写模块代码'
                      : recordMemberFieldLoading
                        ? '成员字段加载中…'
                        : '请选择单值 MEMBER 字段'
                  }}
                </option>
                <option
                  v-if="activeApproverSource.sourceId
                    && !recordMemberFieldOptions.some(
                      option => option.sourceId === activeApproverSource.sourceId,
                    )"
                  :value="activeApproverSource.sourceId"
                >
                  已保存字段 #{{ activeApproverSource.sourceId }}
                </option>
                <option
                  v-for="field in recordMemberFieldOptions"
                  :key="field.sourceId"
                  :value="field.sourceId"
                >
                  {{ field.fieldName }}（{{ field.fieldCode }}）
                </option>
              </select>
            </label>
            <p
              v-if="recordMemberFieldLoading"
              class="flow-record-member-loading"
            >
              正在读取当前模块可用的单值成员字段…
            </p>
            <p
              v-else-if="recordMemberFieldLoadError"
              class="flow-source-load-error flow-record-member-error"
            >
              {{ recordMemberFieldLoadError }}
              <button
                type="button"
                :disabled="busy"
                @click="loadRecordMemberFieldOptions(
                  activeApproverSource.moduleCode ?? '',
                  true,
                )"
              >
                重试
              </button>
            </p>
            <p
              v-else-if="activeApproverSource.moduleCode?.trim()
                && loadedRecordMemberFieldKey
                && recordMemberFieldOptions.length === 0"
              class="flow-record-member-empty"
            >
              该模块没有可用的记录级单值 MEMBER 字段。
            </p>
            <small v-else>
              启动时读取权威记录值并冻结审批人；子表、多值及非 MEMBER 字段不会列出。
            </small>
          </div>
          <label>
            审批方式
            <select
              class="flow-approval-mode"
              :value="activeApprovalMode"
              :disabled="busy"
              @change="updateApprovalMode(($event.target as HTMLSelectElement).value as FlowApprovalMode)"
            >
              <option value="SEQUENTIAL">顺序审批</option>
              <option value="ANY">任一同意</option>
              <option value="ALL">全员同意</option>
              <option value="QUORUM">会签阈值</option>
            </select>
          </label>
          <div v-if="activeApprovalMode === 'QUORUM'" class="flow-quorum-rule">
            <label>
              阈值类型
              <select
                class="flow-quorum-rule-type"
                :value="activeQuorumRule.type"
                :disabled="busy"
                @change="updateQuorumRuleType(
                  ($event.target as HTMLSelectElement).value as FlowQuorumRuleType,
                )"
              >
                <option value="COUNT">同意人数</option>
                <option value="PERCENTAGE">同意比例</option>
              </select>
            </label>
            <label>
              {{ activeQuorumRule.type === 'COUNT' ? '需要同意人数' : '需要同意比例（%）' }}
              <input
                class="flow-quorum-rule-value"
                type="number"
                min="1"
                :max="activeQuorumRule.type === 'COUNT' ? 10 : 100"
                :value="activeQuorumRule.value"
                :disabled="busy"
                @input="updateQuorumRuleValue(($event.target as HTMLInputElement).value)"
              >
            </label>
          </div>
          <div class="flow-deadline-policy">
            <label class="flow-deadline-toggle">
              <input
                class="flow-deadline-enabled"
                type="checkbox"
                :checked="Boolean(activeDeadlinePolicy)"
                :disabled="busy"
                @change="toggleDeadlinePolicy(
                  ($event.target as HTMLInputElement).checked,
                )"
              >
              启用审批时限
            </label>
            <template v-if="activeDeadlinePolicy">
              <label>
                超时时间（分钟）
                <input
                  class="flow-deadline-timeout"
                  type="number"
                  min="1"
                  max="525600"
                  :value="activeDeadlinePolicy.timeoutMinutes"
                  :disabled="busy"
                  @input="updateDeadlineTimeout(
                    ($event.target as HTMLInputElement).value,
                  )"
                >
              </label>
              <label>
                提前提醒（分钟，可留空）
                <input
                  class="flow-deadline-reminder"
                  type="number"
                  min="1"
                  :max="Math.max(1, activeDeadlinePolicy.timeoutMinutes - 1)"
                  :value="activeDeadlinePolicy.remindBeforeMinutes ?? ''"
                  :disabled="busy"
                  @input="updateDeadlineReminder(
                    ($event.target as HTMLInputElement).value,
                  )"
                >
              </label>
              <label>
                超时动作
                <select
                  class="flow-deadline-action"
                  :value="activeDeadlinePolicy.timeoutAction"
                  :disabled="busy"
                  @change="updateDeadlineAction(
                    ($event.target as HTMLSelectElement).value as FlowDeadlineTimeoutAction,
                  )"
                >
                  <option value="NONE">仅标记逾期</option>
                  <option value="AUTO_APPROVE">自动通过</option>
                  <option value="AUTO_REJECT">自动拒绝</option>
                </select>
              </label>
            </template>
          </div>
          <div class="flow-decision-comment-policy">
            <label class="flow-decision-comment-toggle">
              <input
                class="flow-decision-comment-enabled"
                type="checkbox"
                :checked="Boolean(activeDecisionCommentPolicy)"
                :disabled="busy"
                @change="toggleDecisionCommentPolicy(
                  ($event.target as HTMLInputElement).checked,
                )"
              >
              配置审批意见规则
            </label>
            <template v-if="activeDecisionCommentPolicy">
              <label>
                <input
                  class="flow-decision-comment-approve-required"
                  type="checkbox"
                  :checked="activeDecisionCommentPolicy.approveRequired"
                  :disabled="busy"
                  @change="updateDecisionCommentRequired(
                    'approve',
                    ($event.target as HTMLInputElement).checked,
                  )"
                >
                通过时必须填写意见
              </label>
              <label>
                <input
                  class="flow-decision-comment-reject-required"
                  type="checkbox"
                  :checked="activeDecisionCommentPolicy.rejectRequired"
                  :disabled="busy"
                  @change="updateDecisionCommentRequired(
                    'reject',
                    ($event.target as HTMLInputElement).checked,
                  )"
                >
                驳回时必须填写原因
              </label>
              <label>
                必填意见最少字符数
                <input
                  class="flow-decision-comment-minimum-length"
                  type="number"
                  min="1"
                  max="500"
                  :value="activeDecisionCommentPolicy.minimumLength"
                  :disabled="busy"
                  @input="updateDecisionCommentMinimumLength(
                    ($event.target as HTMLInputElement).value,
                  )"
                >
              </label>
            </template>
          </div>
          <div class="flow-decision-evidence-policy">
            <label class="flow-decision-evidence-toggle">
              <input
                class="flow-decision-evidence-enabled"
                type="checkbox"
                :checked="Boolean(activeDecisionEvidencePolicy)"
                :disabled="busy"
                @change="toggleDecisionEvidencePolicy(
                  ($event.target as HTMLInputElement).checked,
                )"
              >
              配置审批证据规则
            </label>
            <template v-if="activeDecisionEvidencePolicy">
              <label>
                最少附件
                <input
                  class="flow-decision-evidence-minimum"
                  type="number"
                  min="0"
                  max="5"
                  :value="activeDecisionEvidencePolicy.minimumAttachments"
                  :disabled="busy"
                  @input="updateDecisionEvidenceBounds(
                    'minimumAttachments',
                    ($event.target as HTMLInputElement).value,
                  )"
                >
              </label>
              <label>
                最多附件
                <input
                  class="flow-decision-evidence-maximum"
                  type="number"
                  min="0"
                  max="5"
                  :value="activeDecisionEvidencePolicy.maximumAttachments"
                  :disabled="busy"
                  @input="updateDecisionEvidenceBounds(
                    'maximumAttachments',
                    ($event.target as HTMLInputElement).value,
                  )"
                >
              </label>
              <label>
                签名要求
                <select
                  class="flow-decision-evidence-signature-mode"
                  :value="activeDecisionEvidencePolicy.signatureMode"
                  :disabled="busy"
                  @change="updateDecisionEvidenceSignatureMode(
                    ($event.target as HTMLSelectElement).value as
                      FlowDecisionEvidencePolicy['signatureMode'],
                  )"
                >
                  <option value="NONE">禁止签名</option>
                  <option value="OPTIONAL">签名可选</option>
                  <option value="REQUIRED">必须签名</option>
                </select>
              </label>
              <fieldset class="flow-decision-evidence-mime-families">
                <legend>允许的附件类型（不选表示不限）</legend>
                <label
                  v-for="family in ['IMAGE', 'PDF', 'DOCUMENT', 'ARCHIVE', 'OTHER']"
                  :key="family"
                >
                  <input
                    type="checkbox"
                    :checked="activeDecisionEvidencePolicy.allowedMimeFamilies.includes(
                      family as FlowDecisionEvidencePolicy['allowedMimeFamilies'][number],
                    )"
                    :disabled="busy"
                    @change="toggleDecisionEvidenceMimeFamily(
                      family as FlowDecisionEvidencePolicy['allowedMimeFamilies'][number],
                      ($event.target as HTMLInputElement).checked,
                    )"
                  >
                  {{ family }}
                </label>
              </fieldset>
            </template>
          </div>
          <label v-if="activeApproverSource.kind === 'FIXED' && activeApprovalMode === 'SEQUENTIAL'">
            审批人
            <MemberPicker
              class="flow-graph-member-picker"
              :system-id="systemId"
              :value="selectedApproverId"
              :disabled="busy"
              :placeholder="`选择第 ${selectedStepIndex + 1} 位审批人`"
              @update:value="assignStep"
            />
          </label>
          <div
            v-else-if="activeApproverSource.kind === 'FIXED'"
            class="flow-approval-group-members"
          >
            <label v-for="(approverId, index) in activeRoute" :key="index">
              审批人 {{ index + 1 }}
              <MemberPicker
                class="flow-graph-member-picker"
                :system-id="systemId"
                :value="approverId"
                :disabled="busy"
                :placeholder="`选择第 ${index + 1} 位审批人`"
                @update:value="assignMemberAt(index, $event)"
              />
              <span class="flow-approval-group-actions">
                <button
                  type="button"
                  :disabled="busy || index === 0"
                  @click="moveMember(index, -1)"
                ><ArrowUp :size="13" />上移</button>
                <button
                  type="button"
                  :disabled="busy || index === activeRoute.length - 1"
                  @click="moveMember(index, 1)"
                ><ArrowDown :size="13" />下移</button>
                <button
                  class="danger"
                  type="button"
                  :disabled="busy || activeRoute.length === 1"
                  @click="removeMember(index)"
                ><Trash2 :size="13" />删除</button>
              </span>
            </label>
          </div>
          <div
            v-if="activeApproverSource.kind === 'FIXED' && activeApprovalMode === 'SEQUENTIAL'"
            class="flow-draft-step-actions"
          >
            <button
              class="flow-graph-step-up"
              type="button"
              :disabled="busy || selectedStepIndex === 0"
              @click="moveStep(-1)"
            >
              <ArrowUp :size="14" />上移
            </button>
            <button
              class="flow-graph-step-down"
              type="button"
              :disabled="busy || selectedStepIndex === activeRoute.length - 1"
              @click="moveStep(1)"
            >
              <ArrowDown :size="14" />下移
            </button>
            <button
              class="flow-graph-step-remove danger"
              type="button"
              :disabled="busy || activeRoute.length === 1"
              @click="removeStep"
            >
              <Trash2 :size="14" />删除
            </button>
          </div>
        </template>
        <template
          v-else-if="(selectedNodeId === 'gateway' && gateway)
            || (selectedNodeId === 'parallel-split' && inclusiveGateway)"
        >
          <header>
            <strong>{{ inclusiveGateway ? '包容网关' : '条件网关' }}</strong>
            <small>{{ inclusiveGateway
              ? '执行所有命中的条件分支；零命中时执行默认分支'
              : '从上到下首个命中，否则执行默认分支' }}</small>
          </header>
          <div class="flow-gateway-actions">
            <button
              class="flow-gateway-add-branch"
              type="button"
              :disabled="busy || (conditionGateway?.branches.length ?? 0) >= (inclusiveGateway ? 5 : 6)"
              @click="addConditionalBranch"
            >
              <Plus :size="14" />添加条件分支
            </button>
            <button
              class="flow-gateway-remove danger"
              type="button"
              :disabled="busy"
              @click="removeGateway"
            >
              <Trash2 :size="14" />删除网关
            </button>
          </div>
          <div class="flow-gateway-branches">
            <section
              v-for="(branch, branchIndex) in conditionGateway?.branches ?? []"
              :key="branch.code"
              class="flow-gateway-branch"
              :class="{ default: branch.defaultBranch }"
            >
              <header>
                <strong>{{ branch.defaultBranch ? '默认分支' : `条件 ${branchIndex + 1}` }}</strong>
                <code>{{ branch.code }}</code>
              </header>
              <label>
                分支名称
                <input
                  class="flow-gateway-branch-name"
                  :value="branch.name"
                  :disabled="busy"
                  maxlength="80"
                  @input="updateBranchName(
                    branchIndex,
                    ($event.target as HTMLInputElement).value,
                  )"
                >
              </label>
              <label>
                审批方式
                <select
                  class="flow-gateway-branch-mode"
                  :value="branch.approvalMode"
                  :disabled="busy"
                  @change="updateBranchApprovalMode(
                    branchIndex,
                    ($event.target as HTMLSelectElement).value,
                  )"
                >
                  <option value="SEQUENTIAL">顺序审批</option>
                  <option value="ANY">任一同意</option>
                  <option value="ALL">全员同意</option>
                  <option value="QUORUM">会签阈值</option>
                </select>
              </label>
              <template v-if="!branch.defaultBranch">
                <div
                  v-for="(condition, conditionIndex) in branch.conditions"
                  :key="conditionIndex"
                  class="flow-gateway-condition"
                >
                  <input
                    class="flow-gateway-condition-field"
                    :value="condition.fieldCode"
                    :disabled="busy"
                    maxlength="64"
                    placeholder="字段代码"
                    @input="updateBranchCondition(
                      branchIndex,
                      conditionIndex,
                      'fieldCode',
                      ($event.target as HTMLInputElement).value,
                    )"
                  >
                  <select
                    class="flow-gateway-condition-operator"
                    :value="condition.operator"
                    :disabled="busy"
                    @change="updateBranchCondition(
                      branchIndex,
                      conditionIndex,
                      'operator',
                      ($event.target as HTMLSelectElement).value,
                    )"
                  >
                    <option
                      v-for="operator in ['EQ', 'NE', 'GT', 'GTE', 'LT', 'LTE', 'EMPTY', 'NOT_EMPTY']"
                      :key="operator"
                      :value="operator"
                    >
                      {{ operator }}
                    </option>
                  </select>
                  <input
                    v-if="!['EMPTY', 'NOT_EMPTY'].includes(condition.operator)"
                    class="flow-gateway-condition-value"
                    :value="condition.valueText"
                    :disabled="busy"
                    placeholder="JSON 值"
                    @input="updateBranchCondition(
                      branchIndex,
                      conditionIndex,
                      'valueText',
                      ($event.target as HTMLInputElement).value,
                    )"
                  >
                  <button
                    class="flow-gateway-condition-remove danger"
                    type="button"
                    :disabled="busy || branch.conditions.length === 1"
                    @click="removeBranchCondition(branchIndex, conditionIndex)"
                  >
                    删除
                  </button>
                </div>
                <button
                  class="flow-gateway-condition-add"
                  type="button"
                  :disabled="busy || branch.conditions.length >= 10"
                  @click="addBranchCondition(branchIndex)"
                >
                  <Plus :size="13" />添加 ALL 条件
                </button>
                <button
                  v-if="(conditionGateway?.branches.length ?? 0) > 2"
                  class="flow-gateway-branch-remove danger"
                  type="button"
                  :disabled="busy"
                  @click="removeConditionalBranch(branchIndex)"
                >
                  删除分支
                </button>
              </template>
              <small>
                审批路线：{{ branch.approvalMode === 'SEQUENTIAL'
                  ? `${branch.approverIds.length} 节点`
                  : `1 个并行组 · ${branch.approverIds.length} 人` }}
              </small>
            </section>
          </div>
        </template>
        <template v-else-if="selectedNodeId === 'parallel-split' && parallelGateway">
          <header>
            <strong>并行网关</strong>
            <small>所有分支同时开始，全部通过后汇合</small>
          </header>
          <div class="flow-gateway-actions">
            <button
              class="flow-parallel-add-branch"
              type="button"
              :disabled="busy || parallelGateway.branches.length >= 5"
              @click="addParallelBranch"
            >
              <Plus :size="14" />添加并行分支
            </button>
            <button
              class="flow-parallel-remove danger"
              type="button"
              :disabled="busy"
              @click="removeParallelGateway"
            >
              <Trash2 :size="14" />删除网关
            </button>
          </div>
          <div class="flow-gateway-branches">
            <section
              v-for="(branch, branchIndex) in parallelGateway.branches"
              :key="branch.code"
              class="flow-gateway-branch"
            >
              <header>
                <strong>并行分支 {{ branchIndex + 1 }}</strong>
                <code>{{ branch.code }}</code>
              </header>
              <label>
                分支名称
                <input
                  class="flow-parallel-branch-name"
                  :value="branch.name"
                  :disabled="busy"
                  maxlength="80"
                  @input="updateParallelBranchName(
                    branchIndex,
                    ($event.target as HTMLInputElement).value,
                  )"
                >
              </label>
              <label>
                审批方式
                <select
                  class="flow-parallel-branch-mode"
                  :value="branch.approvalMode"
                  :disabled="busy"
                  @change="updateParallelBranchMode(
                    branchIndex,
                    ($event.target as HTMLSelectElement).value,
                  )"
                >
                  <option value="SEQUENTIAL">顺序审批</option>
                  <option value="ANY">任一同意</option>
                  <option value="ALL">全员同意</option>
                  <option value="QUORUM">会签阈值</option>
                </select>
              </label>
              <small>
                路线：{{ branch.approvalMode === 'SEQUENTIAL'
                  ? `${branch.approverIds.length} 节点`
                  : `1 个并行组 · ${branch.approverIds.length} 人` }}
              </small>
              <button
                v-if="parallelGateway.branches.length > 2"
                class="flow-parallel-branch-remove danger"
                type="button"
                :disabled="busy"
                @click="removeParallelBranch(branchIndex)"
              >
                删除分支
              </button>
            </section>
          </div>
        </template>
        <template v-else-if="selectedNode">
          <header>
            <strong>{{ selectedNode.data?.title }}</strong>
            <small>{{ selectedNode.data?.kind === 'trigger' ? '触发属性' : '终态属性' }}</small>
          </header>
          <p>{{ selectedNode.data?.subtitle }}</p>
        </template>
        <template v-else>
          <p>选择一个节点查看属性。</p>
        </template>

        <dl class="flow-draft-graph-summary">
          <div>
            <dt>触发</dt>
            <dd>{{ triggerLabel }}</dd>
          </div>
          <div>
            <dt>通过</dt>
            <dd>{{ approvedLabel }}</dd>
          </div>
          <div>
            <dt>拒绝</dt>
            <dd>{{ rejectedLabel }}</dd>
          </div>
          <div>
            <dt>路由</dt>
            <dd>{{ inclusiveGateway
              ? `${inclusiveGateway.branches.length - 1} 包容条件 + 默认`
              : parallelGateway
              ? `${parallelGateway.branches.length} 条并行分支`
              : gateway
              ? `${gateway.branches.length - 1} 条件 + 默认`
              : ({
                SEQUENTIAL: '顺序审批',
                ANY: '任一同意',
                ALL: '全员同意',
                QUORUM: '会签阈值',
              }[approvalMode]) }}</dd>
          </div>
        </dl>
      </aside>
    </div>

    <section
      v-if="activeBranchIndex !== null"
      class="flow-approval-stages-editor flow-branch-approval-stages-editor"
    >
      <header>
        <div>
          <strong>分支审批阶段 · {{ activeBranchName }}</strong>
          <small>每个分支独立执行；阶段 1 始终同步兼容分支字段。</small>
        </div>
        <span class="flow-approval-stage-actions">
          <button
            class="flow-branch-approval-stages-toggle"
            type="button"
            :disabled="busy"
            @click="toggleActiveBranchApprovalStages"
          >
            {{ activeBranchApprovalStages ? '使用兼容单阶段' : '开启分支阶段' }}
          </button>
          <button
            v-if="activeBranchApprovalStages"
            class="flow-branch-approval-stage-add"
            type="button"
            :disabled="busy || activeBranchApprovalStages.length >= 10"
            @click="addBranchApprovalStage"
          >
            <Plus :size="14" />新增阶段
          </button>
        </span>
      </header>
      <p v-if="!activeBranchApprovalStages">
        当前分支保持 legacy 单阶段语义；开启后可配置 1 到 10 个有序阶段。
      </p>
      <article
        v-for="(stage, stageIndex) in activeBranchApprovalStages ?? []"
        :key="`${stage.code}:${stageIndex}`"
        class="flow-approval-stage flow-branch-approval-stage"
      >
        <header>
          <strong>分支阶段 {{ stageIndex + 1 }}</strong>
          <span class="flow-approval-stage-actions">
            <button
              class="flow-branch-approval-stage-up"
              type="button"
              :disabled="busy || !canMoveBranchApprovalStage(stageIndex, -1)"
              @click="moveBranchApprovalStage(stageIndex, -1)"
            ><ArrowUp :size="13" />上移</button>
            <button
              class="flow-branch-approval-stage-down"
              type="button"
              :disabled="busy || !canMoveBranchApprovalStage(stageIndex, 1)"
              @click="moveBranchApprovalStage(stageIndex, 1)"
            ><ArrowDown :size="13" />下移</button>
            <button
              class="flow-branch-approval-stage-remove danger"
              type="button"
              :disabled="busy || activeBranchApprovalStages!.length <= 1"
              @click="removeBranchApprovalStage(stageIndex)"
            ><Trash2 :size="13" />删除</button>
          </span>
        </header>
        <div class="flow-approval-stage-fields">
          <label>
            阶段代码
            <input
              class="flow-branch-approval-stage-code"
              :value="stage.code"
              :disabled="busy"
              maxlength="64"
              @input="updateBranchApprovalStage(stageIndex, {
                code: ($event.target as HTMLInputElement).value,
              })"
            >
          </label>
          <label>
            阶段名称
            <input
              class="flow-branch-approval-stage-name"
              :value="stage.name"
              :disabled="busy"
              maxlength="80"
              @input="updateBranchApprovalStage(stageIndex, {
                name: ($event.target as HTMLInputElement).value,
              })"
            >
          </label>
          <label>
            审批来源
            <select
              class="flow-branch-approval-stage-source"
              :value="stage.approverSource.kind"
              :disabled="busy"
              @change="updateBranchStageSource(
                stageIndex,
                ($event.target as HTMLSelectElement).value as FlowApproverSourceKind,
              )"
            >
              <option value="FIXED">固定成员</option>
              <option v-if="stageIndex > 0" value="PREVIOUS_HANDLER">上一阶段实际处理人</option>
              <option value="REQUESTER">请求人本人</option>
              <option value="REQUESTER_MANAGER">请求人的直属主管</option>
              <option value="REQUESTER_DEPARTMENT_LEADER">请求人所属部门负责人</option>
              <option value="ROLE">租户角色</option>
              <option value="DEPARTMENT">租户部门</option>
              <option v-if="stageIndex === 0" value="DEPARTMENT_LEADER">指定部门负责人</option>
              <option value="RECORD_MEMBER_FIELD">记录成员字段</option>
            </select>
          </label>
          <label>
            审批方式
            <select
              class="flow-branch-approval-stage-mode"
              :value="stage.approvalMode"
              :disabled="busy"
              @change="updateBranchStageMode(
                stageIndex,
                ($event.target as HTMLSelectElement).value as FlowApprovalMode,
              )"
            >
              <option value="SEQUENTIAL">顺序审批</option>
              <option value="ANY">任一同意</option>
              <option value="ALL">全员同意</option>
              <option value="QUORUM">会签阈值</option>
            </select>
          </label>
          <label
            v-if="stage.approverSource.kind === 'ROLE'
              || stage.approverSource.kind === 'DEPARTMENT'
              || stage.approverSource.kind === 'DEPARTMENT_LEADER'"
          >
            来源 ID
            <input
              class="flow-branch-approval-stage-source-id"
              :value="stage.approverSource.sourceId ?? ''"
              :disabled="busy"
              @input="updateBranchStageSourceId(
                stageIndex,
                ($event.target as HTMLInputElement).value,
              )"
            >
          </label>
          <template v-if="stage.approverSource.kind === 'RECORD_MEMBER_FIELD'">
            <label>
              记录模块
              <input
                class="flow-branch-approval-stage-module"
                :value="stage.approverSource.moduleCode ?? ''"
                :disabled="busy"
                @input="updateBranchStageModuleCode(
                  stageIndex,
                  ($event.target as HTMLInputElement).value,
                )"
              >
            </label>
            <label>
              成员字段 ID
              <input
                class="flow-branch-approval-stage-source-id"
                :value="stage.approverSource.sourceId ?? ''"
                :disabled="busy"
                @input="updateBranchStageSourceId(
                  stageIndex,
                  ($event.target as HTMLInputElement).value,
                )"
              >
            </label>
          </template>
          <div
            v-if="stage.approverSource.kind === 'FIXED'"
            class="flow-approval-stage-members"
          >
            <label v-for="(memberId, memberIndex) in stage.approverIds" :key="memberIndex">
              固定成员 {{ memberIndex + 1 }}
              <MemberPicker
                :system-id="systemId"
                :value="memberId"
                :disabled="busy"
                @update:value="updateBranchStageMember(stageIndex, memberIndex, $event)"
              />
              <button
                type="button"
                :disabled="busy || stage.approverIds.length <= 1"
                @click="removeBranchStageMember(stageIndex, memberIndex)"
              >删除成员</button>
            </label>
            <button
              class="flow-branch-approval-stage-member-add"
              type="button"
              :disabled="busy || stage.approverIds.length >= MAX_FLOW_APPROVAL_STEPS"
              @click="addBranchStageMember(stageIndex)"
            >
              <Plus :size="13" />添加固定成员
            </button>
          </div>
          <p
            v-else-if="contextualStageSourceKinds.has(stage.approverSource.kind)"
            class="flow-approval-stage-context-source"
          >
            此来源在分支阶段激活时根据不可变启动上下文解析，不保存来源 ID。
          </p>
          <label v-if="stage.approvalMode === 'QUORUM'">
            会签阈值
            <input
              class="flow-branch-approval-stage-quorum"
              type="number"
              min="1"
              :value="stage.quorumRule?.value ?? 1"
              :disabled="busy"
              @input="updateBranchApprovalStage(stageIndex, {
                quorumRule: {
                  type: stage.quorumRule?.type ?? 'COUNT',
                  value: Number(($event.target as HTMLInputElement).value),
                },
              })"
            >
          </label>
          <label>
            <input
              class="flow-branch-approval-stage-deadline-enabled"
              type="checkbox"
              :checked="Boolean(stage.deadlinePolicy)"
              :disabled="busy"
              @change="toggleBranchStageDeadline(
                stageIndex,
                ($event.target as HTMLInputElement).checked,
              )"
            >
            配置审批时限
          </label>
          <input
            v-if="stage.deadlinePolicy"
            class="flow-branch-approval-stage-deadline"
            type="number"
            min="1"
            max="525600"
            :value="stage.deadlinePolicy.timeoutMinutes"
            :disabled="busy"
            @input="updateBranchApprovalStage(stageIndex, {
              deadlinePolicy: {
                ...stage.deadlinePolicy!,
                timeoutMinutes: Number(($event.target as HTMLInputElement).value),
              },
            })"
          >
          <label>
            <input
              class="flow-branch-approval-stage-comment-enabled"
              type="checkbox"
              :checked="Boolean(stage.decisionCommentPolicy)"
              :disabled="busy"
              @change="toggleBranchStageDecisionComment(
                stageIndex,
                ($event.target as HTMLInputElement).checked,
              )"
            >
            配置审批意见规则
          </label>
          <label>
            <input
              class="flow-branch-approval-stage-evidence-enabled"
              type="checkbox"
              :checked="Boolean(stage.decisionEvidencePolicy)"
              :disabled="busy"
              @change="toggleBranchStageDecisionEvidence(
                stageIndex,
                ($event.target as HTMLInputElement).checked,
              )"
            >
            配置审批证据规则
          </label>
          <template v-if="stage.decisionEvidencePolicy">
            <label>
              附件数量
              <span>
                <input
                  class="flow-branch-approval-stage-evidence-minimum"
                  type="number"
                  min="0"
                  max="5"
                  :value="stage.decisionEvidencePolicy.minimumAttachments"
                  :disabled="busy"
                  @input="updateBranchStageDecisionEvidence(stageIndex, {
                    minimumAttachments: Number(($event.target as HTMLInputElement).value),
                  })"
                >
                至
                <input
                  class="flow-branch-approval-stage-evidence-maximum"
                  type="number"
                  min="0"
                  max="5"
                  :value="stage.decisionEvidencePolicy.maximumAttachments"
                  :disabled="busy"
                  @input="updateBranchStageDecisionEvidence(stageIndex, {
                    maximumAttachments: Number(($event.target as HTMLInputElement).value),
                  })"
                >
              </span>
            </label>
            <label>
              签名要求
              <select
                class="flow-branch-approval-stage-evidence-signature-mode"
                :value="stage.decisionEvidencePolicy.signatureMode"
                :disabled="busy"
                @change="updateBranchStageDecisionEvidence(stageIndex, {
                  signatureMode: ($event.target as HTMLSelectElement).value as
                    FlowDecisionEvidencePolicy['signatureMode'],
                })"
              >
                <option value="NONE">禁止签名</option>
                <option value="OPTIONAL">签名可选</option>
                <option value="REQUIRED">必须签名</option>
              </select>
            </label>
            <fieldset class="flow-branch-approval-stage-evidence-mimes">
              <legend>附件类型（空为不限）</legend>
              <label
                v-for="family in ['IMAGE', 'PDF', 'DOCUMENT', 'ARCHIVE', 'OTHER']"
                :key="family"
              >
                <input
                  type="checkbox"
                  :checked="stage.decisionEvidencePolicy.allowedMimeFamilies.includes(
                    family as FlowDecisionEvidencePolicy['allowedMimeFamilies'][number],
                  )"
                  :disabled="busy"
                  @change="toggleBranchStageMimeFamily(
                    stageIndex,
                    family as FlowDecisionEvidencePolicy['allowedMimeFamilies'][number],
                    ($event.target as HTMLInputElement).checked,
                  )"
                >
                {{ family }}
              </label>
            </fieldset>
          </template>
        </div>
      </article>
    </section>

    <section v-if="approvalStages" class="flow-approval-stages-editor">
      <header>
        <div>
          <strong>线性审批阶段</strong>
          <small>阶段按顺序逐个激活；第一阶段使用上方画布的完整审批配置。</small>
        </div>
        <button
          class="flow-approval-stage-add"
          type="button"
          :disabled="busy || approvalStages.length >= 10"
          @click="addApprovalStage"
        >
          <Plus :size="14" />新增阶段
        </button>
      </header>
      <article
        v-for="(stage, stageIndex) in approvalStages"
        :key="`${stage.code}:${stageIndex}`"
        class="flow-approval-stage"
      >
        <header>
          <strong>阶段 {{ stageIndex + 1 }}</strong>
          <span class="flow-approval-stage-actions">
            <button
              class="flow-approval-stage-up"
              type="button"
              :disabled="busy || !canMoveApprovalStage(stageIndex, -1)"
              @click="moveApprovalStage(stageIndex, -1)"
            ><ArrowUp :size="13" />上移</button>
            <button
              class="flow-approval-stage-down"
              type="button"
              :disabled="busy || !canMoveApprovalStage(stageIndex, 1)"
              @click="moveApprovalStage(stageIndex, 1)"
            ><ArrowDown :size="13" />下移</button>
            <button
              class="flow-approval-stage-remove danger"
              type="button"
              :disabled="busy || approvalStages.length <= 2"
              @click="removeApprovalStage(stageIndex)"
            ><Trash2 :size="13" />删除</button>
          </span>
        </header>
        <div class="flow-approval-stage-fields">
          <label>
            阶段代码
            <input
              class="flow-approval-stage-code"
              :value="stage.code"
              :disabled="busy"
              maxlength="64"
              @input="updateApprovalStage(stageIndex, {
                code: ($event.target as HTMLInputElement).value,
              })"
            >
          </label>
          <label>
            阶段名称
            <input
              class="flow-approval-stage-name"
              :value="stage.name"
              :disabled="busy"
              maxlength="80"
              @input="updateApprovalStage(stageIndex, {
                name: ($event.target as HTMLInputElement).value,
              })"
            >
          </label>
          <template v-if="stageIndex === 0">
            <p class="flow-approval-stage-current-source">
              当前画布配置：{{ stage.approverSource.kind }} ·
              {{ stage.approvalMode }} ·
              {{ stage.approverIds.length }} 位固定成员
            </p>
          </template>
          <template v-else>
            <label>
              审批来源
              <select
                class="flow-approval-stage-source"
                :value="stage.approverSource.kind"
                :disabled="busy"
                @change="updateLaterStageSource(
                  stageIndex,
                  ($event.target as HTMLSelectElement).value as
                    'FIXED' | 'PREVIOUS_HANDLER',
                )"
              >
                <option value="FIXED">固定成员</option>
                <option value="PREVIOUS_HANDLER">上一阶段实际处理人</option>
              </select>
            </label>
            <label>
              审批方式
              <select
                class="flow-approval-stage-mode"
                :value="stage.approvalMode"
                :disabled="busy"
                @change="updateStageMode(
                  stageIndex,
                  ($event.target as HTMLSelectElement).value as FlowApprovalMode,
                )"
              >
                <option value="SEQUENTIAL">顺序审批</option>
                <option value="ANY">任一同意</option>
                <option value="ALL">全员同意</option>
                <option value="QUORUM">会签阈值</option>
              </select>
            </label>
            <div
              v-if="stage.approverSource.kind === 'FIXED'"
              class="flow-approval-stage-members"
            >
              <label v-for="(memberId, memberIndex) in stage.approverIds" :key="memberIndex">
                固定成员 {{ memberIndex + 1 }}
                <MemberPicker
                  :system-id="systemId"
                  :value="memberId"
                  :disabled="busy"
                  @update:value="updateStageMember(stageIndex, memberIndex, $event)"
                />
                <button
                  type="button"
                  :disabled="busy || stage.approverIds.length <= 1"
                  @click="removeStageMember(stageIndex, memberIndex)"
                >删除成员</button>
              </label>
              <button
                class="flow-approval-stage-member-add"
                type="button"
                :disabled="busy || stage.approverIds.length >= MAX_FLOW_APPROVAL_STEPS"
                @click="addStageMember(stageIndex)"
              >
                <Plus :size="13" />添加固定成员
              </button>
            </div>
            <p v-else class="flow-approval-stage-previous-handler">
              阶段激活时使用上一阶段完成审批的实际人类处理人；不保存来源 ID。
            </p>
            <label v-if="stage.approvalMode === 'QUORUM'">
              会签阈值
              <input
                class="flow-approval-stage-quorum"
                type="number"
                min="1"
                :value="stage.quorumRule?.value ?? 1"
                :disabled="busy"
                @input="updateApprovalStage(stageIndex, {
                  quorumRule: {
                    type: stage.quorumRule?.type ?? 'COUNT',
                    value: Number(($event.target as HTMLInputElement).value),
                  },
                })"
              >
            </label>
            <label>
              <input
                class="flow-approval-stage-deadline-enabled"
                type="checkbox"
                :checked="Boolean(stage.deadlinePolicy)"
                :disabled="busy"
                @change="toggleStageDeadline(
                  stageIndex,
                  ($event.target as HTMLInputElement).checked,
                )"
              >
              配置审批时限
            </label>
            <input
              v-if="stage.deadlinePolicy"
              class="flow-approval-stage-deadline"
              type="number"
              min="1"
              max="525600"
              :value="stage.deadlinePolicy.timeoutMinutes"
              :disabled="busy"
              @input="updateApprovalStage(stageIndex, {
                deadlinePolicy: {
                  ...stage.deadlinePolicy!,
                  timeoutMinutes: Number(($event.target as HTMLInputElement).value),
                },
              })"
            >
            <label>
              <input
                class="flow-approval-stage-comment-enabled"
                type="checkbox"
                :checked="Boolean(stage.decisionCommentPolicy)"
                :disabled="busy"
                @change="toggleStageDecisionComment(
                  stageIndex,
                  ($event.target as HTMLInputElement).checked,
                )"
              >
              配置审批意见规则
            </label>
            <label>
              <input
                class="flow-approval-stage-evidence-enabled"
                type="checkbox"
                :checked="Boolean(stage.decisionEvidencePolicy)"
                :disabled="busy"
                @change="toggleStageDecisionEvidence(
                  stageIndex,
                  ($event.target as HTMLInputElement).checked,
                )"
              >
              配置审批证据规则
            </label>
            <template v-if="stage.decisionEvidencePolicy">
              <label>
                附件数量
                <span>
                  <input
                    class="flow-approval-stage-evidence-minimum"
                    type="number"
                    min="0"
                    max="5"
                    :value="stage.decisionEvidencePolicy.minimumAttachments"
                    :disabled="busy"
                    @input="updateStageDecisionEvidence(stageIndex, {
                      minimumAttachments: Number(($event.target as HTMLInputElement).value),
                    })"
                  >
                  至
                  <input
                    class="flow-approval-stage-evidence-maximum"
                    type="number"
                    min="0"
                    max="5"
                    :value="stage.decisionEvidencePolicy.maximumAttachments"
                    :disabled="busy"
                    @input="updateStageDecisionEvidence(stageIndex, {
                      maximumAttachments: Number(($event.target as HTMLInputElement).value),
                    })"
                  >
                </span>
              </label>
              <label>
                签名要求
                <select
                  class="flow-approval-stage-evidence-signature-mode"
                  :value="stage.decisionEvidencePolicy.signatureMode"
                  :disabled="busy"
                  @change="updateStageDecisionEvidence(stageIndex, {
                    signatureMode: ($event.target as HTMLSelectElement).value as
                      FlowDecisionEvidencePolicy['signatureMode'],
                  })"
                >
                  <option value="NONE">禁止签名</option>
                  <option value="OPTIONAL">签名可选</option>
                  <option value="REQUIRED">必须签名</option>
                </select>
              </label>
              <fieldset class="flow-approval-stage-evidence-mimes">
                <legend>附件类型（空为不限）</legend>
                <label
                  v-for="family in ['IMAGE', 'PDF', 'DOCUMENT', 'ARCHIVE', 'OTHER']"
                  :key="family"
                >
                  <input
                    type="checkbox"
                    :checked="stage.decisionEvidencePolicy.allowedMimeFamilies.includes(
                      family as FlowDecisionEvidencePolicy['allowedMimeFamilies'][number],
                    )"
                    :disabled="busy"
                    @change="toggleStageMimeFamily(
                      stageIndex,
                      family as FlowDecisionEvidencePolicy['allowedMimeFamilies'][number],
                      ($event.target as HTMLInputElement).checked,
                    )"
                  >
                  {{ family }}
                </label>
              </fieldset>
            </template>
          </template>
        </div>
      </article>
    </section>

    <footer class="flow-draft-canvas-actions">
      <span>保存后使用同一草稿执行发布检查或只读模拟。</span>
      <button
        class="flow-graph-save-check"
        type="button"
        :disabled="busy"
        @click="emit('saveCheck')"
      >
        保存并校验
      </button>
      <button
        class="flow-graph-save-simulate"
        type="button"
        :disabled="busy"
        @click="emit('saveSimulate')"
      >
        保存并模拟
      </button>
    </footer>
  </section>
</template>

<style scoped>
.flow-draft-canvas {
  display: grid;
  gap: 12px;
  margin-bottom: 18px;
  padding: 14px;
  border: 1px solid #cbd5e1;
  border-radius: 12px;
  background: #f8fafc;
}

.flow-approval-stages-editor,
.flow-approval-stage {
  display: grid;
  gap: 12px;
}

.flow-approval-stages-editor {
  padding: 12px;
  border: 1px solid #bfdbfe;
  border-radius: 10px;
  background: #eff6ff;
}

.flow-approval-stages-editor > header,
.flow-approval-stage > header,
.flow-approval-stage-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.flow-approval-stage {
  padding: 12px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #fff;
}

.flow-approval-stage-fields,
.flow-approval-stage-members {
  display: grid;
  gap: 10px;
}

.flow-approval-stage-fields > label,
.flow-approval-stage-members > label {
  display: grid;
  gap: 5px;
}

.flow-draft-canvas-toolbar,
.flow-draft-node-library,
.flow-draft-step-actions,
.flow-gateway-actions,
.flow-draft-canvas-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.flow-draft-canvas-toolbar,
.flow-draft-canvas-actions {
  justify-content: space-between;
}

.flow-draft-canvas-toolbar > div:first-child,
.flow-draft-inspector header {
  display: grid;
  gap: 2px;
}

.flow-draft-canvas-toolbar small,
.flow-draft-inspector small,
.flow-draft-canvas-actions span {
  color: #64748b;
}

.flow-draft-node-library button,
.flow-draft-step-actions button,
.flow-gateway-actions button,
.flow-gateway-branch button,
.flow-draft-canvas-actions button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-height: 32px;
  padding: 5px 10px;
  border: 1px solid #cbd5e1;
  border-radius: 7px;
  background: #fff;
  color: #334155;
  cursor: pointer;
}

.flow-draft-node-library button:disabled,
.flow-draft-step-actions button:disabled,
.flow-gateway-actions button:disabled,
.flow-gateway-branch button:disabled,
.flow-draft-canvas-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.flow-draft-step-actions .danger {
  color: #b91c1c;
}

.flow-gateway-actions .danger,
.flow-gateway-branch .danger {
  color: #b91c1c;
}

.flow-draft-canvas-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 250px;
  min-height: 420px;
  overflow: hidden;
  border: 1px solid #dbe4ee;
  border-radius: 10px;
  background: #fff;
}

.flow-draft-canvas-stage {
  min-width: 0;
  min-height: 420px;
  background:
    linear-gradient(#eef2f7 1px, transparent 1px),
    linear-gradient(90deg, #eef2f7 1px, transparent 1px);
  background-size: 20px 20px;
}

.flow-draft-vue-flow {
  width: 100%;
  height: 420px;
}

.flow-draft-node {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 168px;
  min-height: 58px;
  padding: 10px 12px;
  border: 2px solid #94a3b8;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 5px 15px rgb(15 23 42 / 8%);
}

.flow-draft-node.selected {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgb(37 99 235 / 15%);
}

.flow-draft-node > div {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.flow-draft-node small {
  overflow: hidden;
  color: #64748b;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flow-draft-node-index {
  display: grid;
  flex: 0 0 25px;
  height: 25px;
  place-items: center;
  border-radius: 50%;
  background: #dbeafe;
  color: #1d4ed8;
  font-weight: 700;
}

.flow-draft-node-trigger {
  border-color: #0f766e;
  background: #f0fdfa;
}

.flow-draft-node-gateway {
  border-color: #7c3aed;
  background: #f5f3ff;
}

.flow-draft-node-approved {
  border-color: #15803d;
  background: #f0fdf4;
}

.flow-draft-node-rejected {
  border-color: #dc2626;
  background: #fef2f2;
}

.flow-draft-inspector {
  display: grid;
  align-content: start;
  gap: 14px;
  padding: 16px;
  border-left: 1px solid #dbe4ee;
}

.flow-draft-inspector label {
  display: grid;
  gap: 6px;
  color: #475569;
}

.flow-draft-inspector p {
  margin: 0;
  color: #475569;
}

.flow-draft-step-actions {
  flex-wrap: wrap;
}

.flow-gateway-actions {
  flex-wrap: wrap;
}

.flow-gateway-branches {
  display: grid;
  gap: 10px;
  max-height: 420px;
  overflow: auto;
}

.flow-gateway-branch {
  display: grid;
  gap: 8px;
  padding: 9px;
  border: 1px solid #ddd6fe;
  border-radius: 8px;
  background: #faf5ff;
}

.flow-gateway-branch.default {
  border-color: #cbd5e1;
  background: #f8fafc;
}

.flow-gateway-branch header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.flow-gateway-branch code {
  color: #6d28d9;
  font-size: 11px;
}

.flow-gateway-branch input,
.flow-gateway-branch select {
  min-width: 0;
  min-height: 32px;
  padding: 5px 7px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
}

.flow-gateway-condition {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 82px;
  gap: 5px;
}

.flow-gateway-condition-value,
.flow-gateway-condition-remove {
  grid-column: 1 / -1;
}

.flow-draft-graph-summary {
  display: grid;
  gap: 8px;
  margin: 4px 0 0;
  padding-top: 12px;
  border-top: 1px solid #e2e8f0;
}

.flow-draft-graph-summary > div {
  display: grid;
  gap: 2px;
}

.flow-draft-graph-summary dt {
  color: #64748b;
  font-size: 12px;
}

.flow-draft-graph-summary dd {
  margin: 0;
  overflow-wrap: anywhere;
}

.flow-draft-canvas-actions {
  justify-content: flex-end;
}

.flow-draft-canvas-actions span {
  margin-right: auto;
}

.flow-draft-canvas-actions .flow-graph-save-check {
  border-color: #2563eb;
  color: #1d4ed8;
}

.flow-draft-canvas-actions .flow-graph-save-simulate {
  border-color: #2563eb;
  background: #2563eb;
  color: #fff;
}

@media (max-width: 900px) {
  .flow-draft-canvas-toolbar,
  .flow-draft-canvas-actions { align-items: flex-start; flex-wrap: wrap; }
  .flow-draft-node-library { flex-wrap: wrap; }
  .flow-draft-canvas-workspace { grid-template-columns: minmax(0, 1fr); }
  .flow-draft-inspector { border-top: 1px solid #dbe4ee; border-left: 0; }
}

@media (max-width: 560px) {
  .flow-draft-canvas { padding: 10px; }
  .flow-approval-stages-editor,
  .flow-approval-stage { padding: 10px; }
  .flow-approval-stages-editor>header,
  .flow-approval-stage>header { align-items: flex-start; flex-direction: column; }
  .flow-approval-stage-actions { justify-content: flex-start; flex-wrap: wrap; }
  .flow-draft-node-library { align-items: stretch; display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); width: 100%; }
  .flow-draft-node-library button { width: 100%; }
  .flow-draft-canvas-stage,
  .flow-draft-canvas-workspace { min-height: 340px; }
  .flow-draft-vue-flow { height: 340px; }
  .flow-draft-inspector { padding: 12px; }
  .flow-draft-canvas-actions { justify-content: flex-start; }
  .flow-draft-canvas-actions span { flex-basis: 100%; margin-right: 0; }
}
</style>
