<script setup lang="ts">
import {
  ArrowDown,
  ArrowRightLeft,
  ArrowUp,
  Ban,
  BellRing,
  Check,
  Copy,
  Eye,
  GitPullRequest,
  Hand,
  MessageSquare,
  Pencil,
  Plus,
  RefreshCw,
  Reply,
  Rocket,
  Send,
  Trash2,
  Undo2,
  UserMinus,
  UserPlus,
  X,
} from 'lucide-vue-next'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import MemberPicker from '@/components/runtime/MemberPicker.vue'
import { ApiRequestError } from '@/services/api'
import { fileApi } from '@/services/file'
import { flowApi } from '@/services/flow'
import { useSessionStore } from '@/stores/session'
import FlowDraftCanvas from '@/views/system/FlowDraftCanvas.vue'
import FlowExtensionEditor from '@/views/system/FlowExtensionEditor.vue'
import FlowNodeRuntimePanel from '@/views/system/FlowNodeRuntimePanel.vue'
import type {
  ApprovalTaskStatus,
  FlowApprovalMode,
  FlowApprovalStage,
  FlowApproverSource,
  FlowAssignmentPosition,
  FlowCompletionCompensation,
  FlowCompletionExecution,
  FlowCompletionFailurePolicy,
  FlowCompensationExecution,
  FlowCompletionStep,
  FlowDefinitionDraft,
  FlowDefinitionVersion,
  FlowDelegationRule,
  FlowDraftCheck,
  FlowDraftSimulation,
  FlowGateway,
  FlowComment,
  FlowCopy,
  FlowDecisionCommentPolicy,
  FlowDecisionCommentTemplate,
  FlowDecisionEvidenceInput,
  FlowDecisionEvidencePolicy,
  FlowDeadlinePolicy,
  FlowHistory,
  FlowInclusiveGateway,
  FlowInstance,
  FlowInstanceStatus,
  FlowParallelGateway,
  FlowPeriodicScheduleState,
  FlowQuorumRule,
  FlowStartableDefinition,
  FlowTriggerCondition,
  FlowTriggerConditionOperator,
  FlowTriggerEvent,
  FlowUrge,
  SaveFlowDecisionCommentTemplateInput,
  StartFlowInstanceInput,
} from '@/types/flow'
import type { FileAsset } from '@/types/file'
import type {
  FlowGatewayDraft,
  FlowInclusiveGatewayDraft,
  FlowParallelGatewayDraft,
} from '@/views/system/flowDraftGraphModel'

interface TriggerConditionDraft {
  fieldCode: string
  operator: FlowTriggerConditionOperator
  valueText: string
}

interface CompletionStepDraft {
  code: string
  name: string
  type: FlowCompletionStep['type']
  parallelGroup: string
  topic: string
  leaseSeconds: string
  externalMaxAttempts: string
  resultJsonLimitBytes: string
  url: string
  secretRef: string
  timeoutSeconds: string
  webhookMaxAttempts: string
  baseBackoffSeconds: string
  subflowDefinitionId: string
  subflowVersion: string
  compensationType: FlowCompletionStep['type']
  compensationTopic: string
  compensationLeaseSeconds: string
  compensationExternalMaxAttempts: string
  compensationResultJsonLimitBytes: string
  compensationUrl: string
  compensationSecretRef: string
  compensationTimeoutSeconds: string
  compensationWebhookMaxAttempts: string
  compensationBaseBackoffSeconds: string
  compensationSubflowDefinitionId: string
  compensationSubflowVersion: string
}

const route = useRoute()
const session = useSessionStore()
const systemId = computed(() => String(route.params.systemId))
const tenantId = computed(() => session.context?.tenantId ?? '')
const canManageDefinitions = computed(() => session.hasPermission('flow.definition.manage'))
const canStart = computed(() => session.hasPermission('flow.instance.start'))
const canRead = computed(() => session.hasPermission('flow.instance.read'))
const canDecide = computed(() => session.hasPermission('flow.instance.decide'))
const canWithdraw = computed(() => session.hasPermission('flow.instance.withdraw'))
const canTerminate = computed(() => session.hasPermission('flow.instance.terminate'))
const canUrge = computed(() => session.hasPermission('flow.instance.urge'))
const canComment = computed(() => session.hasPermission('flow.instance.comment'))
const canTransfer = computed(() => session.hasPermission('flow.instance.transfer'))
const canAddSign = computed(() => session.hasPermission('flow.instance.add-sign'))
const canReduceSign = computed(() => session.hasPermission('flow.instance.reduce-sign'))
const canCopy = computed(() => session.hasPermission('flow.instance.copy'))
const canReturn = computed(() => session.hasPermission('flow.instance.return'))
const canClaim = computed(() => session.hasPermission('flow.instance.claim'))
const canCancelClaim = computed(() => session.hasPermission('flow.instance.cancel-claim'))
const canManageDelegations = computed(() => session.hasPermission('flow.delegation.manage'))
const canWorkExternalTasks = computed(() => session.hasPermission('flow.external-task.work'))
const canUseDelegations = computed(() => Boolean(session.context?.memberId))

const definitions = ref<FlowDefinitionDraft[]>([])
const periodicSchedules = ref(new Map<string, FlowPeriodicScheduleState>())
const definitionPage = ref(1)
const definitionTotal = ref(0)
const instances = ref<FlowInstance[]>([])
const instancePage = ref(1)
const instanceTotal = ref(0)
const instanceStatus = ref<FlowInstanceStatus | ''>('')
const instanceFrom = ref('')
const instanceTo = ref('')
const approvalTasks = ref<FlowInstance[]>([])
const taskStatus = ref<ApprovalTaskStatus>('PENDING')
const taskPage = ref(1)
const taskTotal = ref(0)
const taskPageSize = 20
const claimableTasks = ref<FlowInstance[]>([])
const claimablePage = ref(1)
const claimableTotal = ref(0)
const claimablePageSize = 20
const externalTasks = ref<FlowCompletionExecution[]>([])
const externalTaskTopic = ref('')
const externalTaskPage = ref(1)
const externalTaskTotal = ref(0)
const externalTaskPageSize = 20
const loadingExternalTasks = ref(false)
const externalTaskError = ref('')
const externalTaskLeaseTokens = ref(new Map<string, string>())
const externalTaskResultDrafts = ref(new Map<string, string>())
const externalTaskFailureCodes = ref(new Map<string, string>())
const externalTaskFailureMessages = ref(new Map<string, string>())
const delegations = ref<FlowDelegationRule[]>([])
const delegationPage = ref(1)
const delegationTotal = ref(0)
const delegationPageSize = 20
const loadingDelegations = ref(false)
const delegationError = ref('')
const delegationOpen = ref(false)
const delegationDelegatorMemberId = ref('')
const delegationDelegateMemberId = ref('')
const delegationStartsAt = ref('')
const delegationEndsAt = ref('')
const delegationDefinitionId = ref('')
const loadingDefinitions = ref(false)
const loadingInstances = ref(false)
const loadingTasks = ref(false)
const loadingClaimableTasks = ref(false)
const startableDefinitions = ref<FlowStartableDefinition[]>([])
const loadingStartableDefinitions = ref(false)
const startableDefinitionsError = ref('')
const claimableError = ref('')
const mutation = ref('')
const error = ref('')
let definitionLoadGeneration = 0
let instanceLoadGeneration = 0
let taskLoadGeneration = 0
let claimableLoadGeneration = 0
let externalTaskLoadGeneration = 0
let startableDefinitionLoadGeneration = 0

const taskStatusOptions: Array<{ label: string, value: ApprovalTaskStatus }> = [
  { label: '待审批', value: 'PENDING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '全部', value: 'ALL' },
]
const triggerConditionOperators: FlowTriggerConditionOperator[] = [
  'EQ',
  'NE',
  'GT',
  'GTE',
  'LT',
  'LTE',
  'EMPTY',
  'NOT_EMPTY',
]
const triggerEventOptions: Array<{ label: string, value: FlowTriggerEvent }> = [
  { label: '记录激活', value: 'RECORD_ACTIVATED' },
  { label: '记录创建', value: 'RECORD_CREATED' },
  { label: '记录更新', value: 'RECORD_UPDATED' },
  { label: '记录删除', value: 'RECORD_DELETED' },
  { label: '记录状态变化', value: 'RECORD_STATUS_CHANGED' },
  { label: '导入完成', value: 'IMPORT_COMPLETED' },
  { label: '固定周期', value: 'PERIODIC' },
]
const triggerValueOperators = new Set<FlowTriggerConditionOperator>([
  'EQ',
  'NE',
  'GT',
  'GTE',
  'LT',
  'LTE',
])

const definitionOpen = ref(false)
const editingDefinition = ref<FlowDefinitionDraft | null>(null)
const definitionName = ref('')
const definitionApprovers = ref<string[]>([''])
const definitionApprovalMode = ref<FlowApprovalMode>('SEQUENTIAL')
const definitionApprovalStages = ref<FlowApprovalStage[] | null>(null)
const definitionApproverSource = ref<FlowApproverSource>({
  kind: 'FIXED',
  sourceId: null,
})
const definitionQuorumRule = ref<FlowQuorumRule | null>(null)
const definitionDeadlinePolicy = ref<FlowDeadlinePolicy | null>(null)
const definitionDecisionCommentPolicy = ref<FlowDecisionCommentPolicy | null>(null)
const definitionDecisionEvidencePolicy = ref<FlowDecisionEvidencePolicy | null>(null)
const definitionCompletionFailurePolicy = ref<FlowCompletionFailurePolicy>('MANUAL_RETRY')
const definitionCompletionSteps = ref<CompletionStepDraft[]>([])
const definitionGateway = ref<FlowGatewayDraft | null>(null)
const definitionParallelGateway = ref<FlowParallelGatewayDraft | null>(null)
const definitionInclusiveGateway = ref<FlowInclusiveGatewayDraft | null>(null)
const definitionTriggerEnabled = ref(false)
const definitionTriggerModuleCode = ref('')
const definitionTriggerEvent = ref<FlowTriggerEvent>('RECORD_ACTIVATED')
const definitionTriggerPriority = ref('0')
const definitionTriggerExclusive = ref(true)
const definitionTriggerConditions = ref<TriggerConditionDraft[]>([])
const definitionTriggerStartAt = ref('')
const definitionTriggerIntervalMinutes = ref('60')
const definitionStatusMappingEnabled = ref(false)
const definitionStatusFieldCode = ref('')
const definitionStatusApprovedValue = ref('')
const definitionStatusRejectedValue = ref('')
const definitionStatusWithdrawnValue = ref('')
const definitionStatusTerminatedValue = ref('')
const publishedVersions = ref(new Map<string, FlowDefinitionVersion>())
const versionHistoryOpen = ref(false)
const versionHistoryDefinition = ref<FlowDefinitionDraft | null>(null)
const versionHistoryItems = ref<FlowDefinitionVersion[]>([])
const versionHistoryPage = ref(1)
const versionHistoryTotal = ref(0)
const versionHistoryLoading = ref(false)
const versionHistoryError = ref('')
const versionHistoryPageSize = 10
const preflightOpen = ref(false)
const preflightDefinition = ref<FlowDefinitionDraft | null>(null)
const preflightCheck = ref<FlowDraftCheck | null>(null)
const preflightSimulation = ref<FlowDraftSimulation | null>(null)
const preflightError = ref('')
const simulationRequesterId = ref('')
const simulationBusinessKey = ref('')
const simulationTriggerValues = ref('{}')
const definitionGraphTriggerLabel = computed(() => {
  if (!definitionTriggerEnabled.value) return '手动发起'
  if (definitionTriggerEvent.value === 'PERIODIC') {
    return `PERIODIC · 每 ${definitionTriggerIntervalMinutes.value.trim() || '—'} 分钟`
  }
  const moduleCode = definitionTriggerModuleCode.value.trim() || '未配置模块'
  return `${definitionTriggerEvent.value} · ${moduleCode} · ${
    definitionTriggerConditions.value.length
  } 条件`
})
const definitionGraphApprovedLabel = computed(() => (
  definitionStatusMappingEnabled.value
    ? `${definitionStatusFieldCode.value.trim() || '未配置字段'} → ${
        definitionStatusApprovedValue.value.trim() || '未配置值'
      }`
    : '不回写记录状态'
))
const definitionGraphRejectedLabel = computed(() => (
  definitionStatusMappingEnabled.value
    ? `${definitionStatusFieldCode.value.trim() || '未配置字段'} → ${
        definitionStatusRejectedValue.value.trim() || '未配置值'
      }`
    : '不回写记录状态'
))

const startOpen = ref(false)
const startDefinitionId = ref('')
const startVersion = ref('')
const startBusinessKey = ref('')
const startBindingModuleCode = ref('')
const startBindingRecordId = ref('')
const startRouteValues = ref('{}')
const startError = ref('')
const startRefreshWarning = ref('')
const startReceipt = ref<FlowInstance | null>(null)
const startIdempotencyKey = ref('')
const startSubmittedFingerprint = ref('')
let appliedStartDeepLink = ''

const decisionOpen = ref(false)
const decisionAction = ref<'approve' | 'reject'>('approve')
const decisionInstanceId = ref('')
const decisionBranchCode = ref('')
const decisionComment = ref('')
const decisionError = ref('')
const decisionRepresentedMemberId = ref('')
const decisionAttachmentFileIds = ref<string[]>([])
const decisionAttachmentAssets = ref(new Map<string, FileAsset>())
const decisionSignatureKind = ref<'NONE' | 'TYPED' | 'FILE'>('NONE')
const decisionTypedSignature = ref('')
const decisionSignatureFileId = ref('')
const decisionSignatureAsset = ref<FileAsset | null>(null)
const decisionSelectedTemplateId = ref('')
const decisionTemplates = ref<FlowDecisionCommentTemplate[]>([])
const decisionEvidenceLoading = ref('')

const templateManagerOpen = ref(false)
const templateItems = ref<FlowDecisionCommentTemplate[]>([])
const templatePage = ref(1)
const templateTotal = ref(0)
const templateLoading = ref(false)
const templateError = ref('')
const templateEditingId = ref('')
const templateName = ref('')
const templateBody = ref('')

const withdrawOpen = ref(false)
const withdrawInstanceId = ref('')
const withdrawReason = ref('')
const withdrawError = ref('')

const terminateOpen = ref(false)
const terminateInstanceId = ref('')
const terminateReason = ref('')
const terminateError = ref('')

const urgeOpen = ref(false)
const urgeInstanceId = ref('')
const urgeMessage = ref('')
const urgeError = ref('')

const transferOpen = ref(false)
const transferInstanceId = ref('')
const transferTargetMemberId = ref('')
const transferReason = ref('')
const transferError = ref('')

const addSignOpen = ref(false)
const addSignInstanceId = ref('')
const addSignTargetMemberId = ref('')
const addSignPosition = ref<FlowAssignmentPosition>('BEFORE')
const addSignReason = ref('')
const addSignError = ref('')

const reduceSignOpen = ref(false)
const reduceSignInstanceId = ref('')
const reduceSignCandidates = ref<Array<{ stepIndex: number, memberId: string }>>([])
const reduceSignTargetStepIndex = ref<number | null>(null)
const reduceSignReason = ref('')
const reduceSignError = ref('')

const returnOpen = ref(false)
const returnInstanceId = ref('')
const returnReason = ref('')
const returnError = ref('')

const cancelClaimOpen = ref(false)
const cancelClaimInstanceId = ref('')
const cancelClaimReason = ref('')
const cancelClaimError = ref('')

const claimOpen = ref(false)
const claimInstanceId = ref('')
const claimComment = ref('')
const claimError = ref('')

const copyOpen = ref(false)
const copyInstanceId = ref('')
const copyTargetMemberId = ref('')
const copyMessage = ref('')
const copyError = ref('')

const detailOpen = ref(false)
const detailLoading = ref(false)
const selectedInstance = ref<FlowInstance | null>(null)
const extensionEditorOpen = ref(false)
const extensionDefinition = ref<FlowDefinitionDraft | null>(null)

function openExtensionEditor(definition: FlowDefinitionDraft) {
  extensionDefinition.value = definition
  extensionEditorOpen.value = true
}
const decisionInstance = computed(() => {
  const instanceId = decisionInstanceId.value.trim()
  return approvalTasks.value.find((item) => item.instanceId === instanceId)
    ?? instances.value.find((item) => item.instanceId === instanceId)
    ?? (selectedInstance.value?.instanceId === instanceId ? selectedInstance.value : null)
})
const decisionBranches = computed(() => {
  const memberId = session.context?.memberId
  const instance = decisionInstance.value
  return (instance?.parallelBranches ?? []).filter((branch) => (
    branch.status === 'PENDING'
    && (
      branch.activeApproverIds.includes(memberId ?? '')
      || representedMemberIdsForBranch(instance, branch).length > 0
    )
  ))
})
const decisionRepresentationOptions = computed(() => {
  const branchCode = decisionBranchCode.value.trim()
  const branch = branchCode
    ? decisionBranches.value.find(item => item.code === branchCode)
    : null
  return branch
    ? representedMemberIdsForBranch(decisionInstance.value, branch)
    : representedMemberIds(decisionInstance.value)
})
const decisionDirectlyEligible = computed(() => {
  const instance = decisionInstance.value
  if (!instance) return false
  const branchCode = decisionBranchCode.value.trim()
  const branch = branchCode
    ? decisionBranches.value.find(item => item.code === branchCode)
    : null
  return branch
    ? branch.activeApproverIds.includes(session.context?.memberId ?? '')
    : directDecisionEligible(instance)
})
const defaultDecisionCommentPolicy: FlowDecisionCommentPolicy = {
  approveRequired: false,
  rejectRequired: true,
  minimumLength: 1,
}
const decisionCommentPolicy = computed<FlowDecisionCommentPolicy>(() => {
  const branchCode = decisionBranchCode.value.trim()
  const branch = branchCode
    ? decisionBranches.value.find(item => item.code === branchCode)
    : null
  return branch?.decisionCommentPolicy
    ?? decisionInstance.value?.decisionCommentPolicy
    ?? defaultDecisionCommentPolicy
})
const decisionCommentRequired = computed(() => (
  decisionAction.value === 'approve'
    ? decisionCommentPolicy.value.approveRequired
    : decisionCommentPolicy.value.rejectRequired
))
const decisionEvidencePolicy = computed<FlowDecisionEvidencePolicy | null>(() => {
  const branchCode = decisionBranchCode.value.trim()
  const branch = branchCode
    ? decisionBranches.value.find(item => item.code === branchCode)
    : null
  return branch?.decisionEvidencePolicy
    ?? decisionInstance.value?.decisionEvidencePolicy
    ?? null
})
const selectedHistory = ref<FlowHistory | null>(null)
const selectedUrges = ref<FlowUrge[]>([])
const urgePage = ref(1)
const urgeTotal = ref(0)
const urgeTimelineLoading = ref(false)
const urgeTimelineError = ref('')
const selectedComments = ref<FlowComment[]>([])
const commentPage = ref(1)
const commentTotal = ref(0)
const commentTimelineLoading = ref(false)
const commentTimelineError = ref('')
const commentBody = ref('')
const commentError = ref('')
const selectedCopies = ref<FlowCopy[]>([])
const copyPage = ref(1)
const copyTotal = ref(0)
const copyTimelineLoading = ref(false)
const copyTimelineError = ref('')
const interactionPageSize = 20

async function loadDefinitions() {
  const generation = ++definitionLoadGeneration
  if (!canManageDefinitions.value) {
    definitions.value = []
    periodicSchedules.value = new Map()
    definitionTotal.value = 0
    loadingDefinitions.value = false
    return
  }
  loadingDefinitions.value = true
  try {
    const result = await flowApi.listDefinitions(systemId.value, definitionPage.value)
    if (generation !== definitionLoadGeneration) return
    definitions.value = result.items
    definitionTotal.value = result.total
    const states = await Promise.all(result.items.map(async (definition) => {
      try {
        return await flowApi.periodicSchedule(systemId.value, definition.definitionId)
      } catch {
        return null
      }
    }))
    if (generation !== definitionLoadGeneration) return
    periodicSchedules.value = new Map(
      states
        .filter((state): state is FlowPeriodicScheduleState => Boolean(state))
        .map((state) => [state.definitionId, state]),
    )
  } catch (cause) {
    if (generation === definitionLoadGeneration) error.value = message(cause)
  } finally {
    if (generation === definitionLoadGeneration) loadingDefinitions.value = false
  }
}

async function loadStartableDefinitions() {
  const generation = ++startableDefinitionLoadGeneration
  if (!canStart.value) {
    startableDefinitions.value = []
    startableDefinitionsError.value = ''
    loadingStartableDefinitions.value = false
    return
  }
  loadingStartableDefinitions.value = true
  startableDefinitionsError.value = ''
  try {
    const result = await flowApi.listStartableDefinitions(systemId.value, 1, 100)
    if (generation !== startableDefinitionLoadGeneration) return
    startableDefinitions.value = result.items
    if (
      startOpen.value
      && !startDefinitionId.value
      && result.items.length === 1
    ) {
      startDefinitionId.value = result.items[0]!.definitionId
    }
  } catch (cause) {
    if (generation === startableDefinitionLoadGeneration) {
      startableDefinitions.value = []
      startableDefinitionsError.value = message(cause)
    }
  } finally {
    if (generation === startableDefinitionLoadGeneration) {
      loadingStartableDefinitions.value = false
    }
  }
}

async function loadInstances() {
  const generation = ++instanceLoadGeneration
  if (!canRead.value) {
    instances.value = []
    instanceTotal.value = 0
    loadingInstances.value = false
    return
  }
  loadingInstances.value = true
  try {
    const result = await flowApi.listInstances(systemId.value, {
      ...(instanceStatus.value ? { status: instanceStatus.value } : {}),
      ...(instanceFrom.value ? { from: instanceFrom.value } : {}),
      ...(instanceTo.value ? { to: instanceTo.value } : {}),
      page: instancePage.value,
      size: 20,
    })
    if (generation !== instanceLoadGeneration) return
    instances.value = result.items
    instanceTotal.value = result.total
  } catch (cause) {
    if (generation === instanceLoadGeneration) error.value = message(cause)
  } finally {
    if (generation === instanceLoadGeneration) loadingInstances.value = false
  }
}

async function loadApprovalTasks() {
  const generation = ++taskLoadGeneration
  if (!canRead.value) {
    approvalTasks.value = []
    taskTotal.value = 0
    loadingTasks.value = false
    return
  }
  loadingTasks.value = true
  try {
    let result = await flowApi.listApprovalTasks(systemId.value, {
      status: taskStatus.value,
      page: taskPage.value,
      size: taskPageSize,
    })
    if (generation !== taskLoadGeneration) return

    const lastPage = Math.max(1, Math.ceil(result.total / taskPageSize))
    if (taskPage.value > lastPage) {
      taskPage.value = lastPage
      result = await flowApi.listApprovalTasks(systemId.value, {
        status: taskStatus.value,
        page: taskPage.value,
        size: taskPageSize,
      })
      if (generation !== taskLoadGeneration) return
    }

    approvalTasks.value = result.items
    taskTotal.value = result.total
  } catch (cause) {
    if (generation === taskLoadGeneration) error.value = message(cause)
  } finally {
    if (generation === taskLoadGeneration) loadingTasks.value = false
  }
}

async function loadClaimableTasks() {
  const generation = ++claimableLoadGeneration
  if (!canClaim.value) {
    claimableTasks.value = []
    claimableTotal.value = 0
    loadingClaimableTasks.value = false
    claimableError.value = ''
    return
  }
  loadingClaimableTasks.value = true
  claimableError.value = ''
  try {
    let result = await flowApi.listClaimableTasks(
      systemId.value,
      claimablePage.value,
      claimablePageSize,
    )
    if (generation !== claimableLoadGeneration) return
    const lastPage = Math.max(1, Math.ceil(result.total / claimablePageSize))
    if (claimablePage.value > lastPage) {
      claimablePage.value = lastPage
      result = await flowApi.listClaimableTasks(
        systemId.value,
        claimablePage.value,
        claimablePageSize,
      )
      if (generation !== claimableLoadGeneration) return
    }
    claimableTasks.value = result.items
    claimableTotal.value = result.total
  } catch (cause) {
    if (generation === claimableLoadGeneration) claimableError.value = message(cause)
  } finally {
    if (generation === claimableLoadGeneration) loadingClaimableTasks.value = false
  }
}

async function loadExternalTasks() {
  const generation = ++externalTaskLoadGeneration
  if (!canWorkExternalTasks.value) {
    externalTasks.value = []
    externalTaskTotal.value = 0
    loadingExternalTasks.value = false
    externalTaskError.value = ''
    return
  }
  loadingExternalTasks.value = true
  externalTaskError.value = ''
  try {
    let result = await flowApi.listExternalTasks(
      systemId.value,
      externalTaskTopic.value.trim(),
      externalTaskPage.value,
      externalTaskPageSize,
    )
    if (generation !== externalTaskLoadGeneration) return
    const lastPage = Math.max(1, Math.ceil(result.total / externalTaskPageSize))
    if (externalTaskPage.value > lastPage) {
      externalTaskPage.value = lastPage
      result = await flowApi.listExternalTasks(
        systemId.value,
        externalTaskTopic.value.trim(),
        externalTaskPage.value,
        externalTaskPageSize,
      )
      if (generation !== externalTaskLoadGeneration) return
    }
    const claimedInMemory = externalTasks.value.filter(item => (
      externalTaskLeaseTokens.value.has(item.executionId)
      && !result.items.some(resultItem => resultItem.executionId === item.executionId)
    ))
    externalTasks.value = [...claimedInMemory, ...result.items]
    externalTaskTotal.value = result.total
  } catch (cause) {
    if (generation === externalTaskLoadGeneration) {
      externalTaskError.value = message(cause)
    }
  } finally {
    if (generation === externalTaskLoadGeneration) loadingExternalTasks.value = false
  }
}

async function loadDelegations() {
  if (!canUseDelegations.value) {
    delegations.value = []
    delegationTotal.value = 0
    loadingDelegations.value = false
    delegationError.value = ''
    return
  }
  loadingDelegations.value = true
  delegationError.value = ''
  try {
    const result = await flowApi.listDelegations(
      systemId.value,
      undefined,
      delegationPage.value,
      delegationPageSize,
    )
    delegations.value = result.items
    delegationTotal.value = result.total
  } catch (cause) {
    delegationError.value = message(cause)
  } finally {
    loadingDelegations.value = false
  }
}

async function refresh() {
  error.value = ''
  await Promise.all([
    loadDefinitions(),
    loadStartableDefinitions(),
    loadInstances(),
    loadApprovalTasks(),
    loadClaimableTasks(),
    loadExternalTasks(),
    loadDelegations(),
  ])
}

function changeTaskStatus(value: string | number) {
  taskStatus.value = value as ApprovalTaskStatus
  taskPage.value = 1
  void loadApprovalTasks()
}

function changeTaskPage(value: number) {
  taskPage.value = value
  void loadApprovalTasks()
}

function changeClaimablePage(value: number) {
  claimablePage.value = value
  void loadClaimableTasks()
}

function filterExternalTasks() {
  externalTaskPage.value = 1
  void loadExternalTasks()
}

function changeExternalTaskPage(value: number) {
  externalTaskPage.value = value
  void loadExternalTasks()
}

function changeDelegationPage(value: number) {
  delegationPage.value = value
  void loadDelegations()
}

function replaceExternalTask(execution: FlowCompletionExecution) {
  const index = externalTasks.value.findIndex(
    item => item.executionId === execution.executionId,
  )
  if (index < 0) {
    externalTasks.value = [execution, ...externalTasks.value]
    return
  }
  const next = [...externalTasks.value]
  next[index] = execution
  externalTasks.value = next
}

function setExternalTaskDraft(
  target: 'result' | 'code' | 'message',
  executionId: string,
  value: string,
) {
  const targetRef = target === 'result'
    ? externalTaskResultDrafts
    : target === 'code'
      ? externalTaskFailureCodes
      : externalTaskFailureMessages
  const next = new Map(targetRef.value)
  next.set(executionId, value)
  targetRef.value = next
}

async function claimExternalTask(execution: FlowCompletionExecution) {
  mutation.value = `external-task:claim:${execution.executionId}`
  externalTaskError.value = ''
  try {
    const claimed = await flowApi.claimExternalTask(systemId.value, execution.executionId)
    const tokens = new Map(externalTaskLeaseTokens.value)
    tokens.set(execution.executionId, claimed.leaseToken)
    externalTaskLeaseTokens.value = tokens
    setExternalTaskDraft('result', execution.executionId, '{}')
    setExternalTaskDraft('code', execution.executionId, '')
    setExternalTaskDraft('message', execution.executionId, '')
    replaceExternalTask(claimed.execution)
  } catch (cause) {
    externalTaskError.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function heartbeatExternalTask(execution: FlowCompletionExecution) {
  const leaseToken = externalTaskLeaseTokens.value.get(execution.executionId)
  if (!leaseToken) {
    externalTaskError.value = '当前页面内没有该任务的租约令牌，请重新认领'
    return
  }
  mutation.value = `external-task:heartbeat:${execution.executionId}`
  externalTaskError.value = ''
  try {
    replaceExternalTask(await flowApi.heartbeatExternalTask(
      systemId.value,
      execution.executionId,
      leaseToken,
    ))
  } catch (cause) {
    externalTaskError.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

function clearExternalTaskLease(executionId: string) {
  const tokens = new Map(externalTaskLeaseTokens.value)
  tokens.delete(executionId)
  externalTaskLeaseTokens.value = tokens
}

function clearExternalTaskLeasesOutsideInstance(instanceId: string) {
  const staleExecutionIds = externalTasks.value
    .filter(execution => (
      execution.instanceId !== instanceId
      && externalTaskLeaseTokens.value.has(execution.executionId)
    ))
    .map(execution => execution.executionId)
  if (!staleExecutionIds.length) return
  const stale = new Set(staleExecutionIds)
  externalTaskLeaseTokens.value = new Map(
    [...externalTaskLeaseTokens.value].filter(([executionId]) => !stale.has(executionId)),
  )
  externalTaskResultDrafts.value = new Map(
    [...externalTaskResultDrafts.value].filter(([executionId]) => !stale.has(executionId)),
  )
  externalTaskFailureCodes.value = new Map(
    [...externalTaskFailureCodes.value].filter(([executionId]) => !stale.has(executionId)),
  )
  externalTaskFailureMessages.value = new Map(
    [...externalTaskFailureMessages.value].filter(([executionId]) => !stale.has(executionId)),
  )
  externalTasks.value = externalTasks.value.filter(
    execution => !stale.has(execution.executionId),
  )
}

async function refreshAfterExternalTask(execution: FlowCompletionExecution) {
  await Promise.all([loadExternalTasks(), loadInstances(), loadApprovalTasks()])
  if (
    detailOpen.value
    && selectedInstance.value?.instanceId === execution.instanceId
  ) {
    await showDetail(selectedInstance.value)
  }
}

async function completeExternalTask(execution: FlowCompletionExecution) {
  const leaseToken = externalTaskLeaseTokens.value.get(execution.executionId)
  if (!leaseToken) {
    externalTaskError.value = '当前页面内没有该任务的租约令牌，请重新认领'
    return
  }
  let result: unknown
  try {
    result = JSON.parse(externalTaskResultDrafts.value.get(execution.executionId) ?? '{}')
  } catch {
    externalTaskError.value = '完成结果必须是 JSON 对象'
    return
  }
  const resultLimit = execution.externalTask?.resultJsonLimitBytes ?? 8192
  if (
    !result
    || typeof result !== 'object'
    || Array.isArray(result)
    || new TextEncoder().encode(JSON.stringify(result)).length > resultLimit
  ) {
    externalTaskError.value = `完成结果必须是不超过 ${resultLimit} bytes 的 JSON 对象`
    return
  }
  mutation.value = `external-task:complete:${execution.executionId}`
  externalTaskError.value = ''
  try {
    const completed = await flowApi.completeExternalTask(
      systemId.value,
      execution.executionId,
      { leaseToken, result: result as Record<string, unknown> },
    )
    clearExternalTaskLease(execution.executionId)
    await refreshAfterExternalTask(completed)
  } catch (cause) {
    externalTaskError.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function failExternalTask(execution: FlowCompletionExecution) {
  const leaseToken = externalTaskLeaseTokens.value.get(execution.executionId)
  const code = (externalTaskFailureCodes.value.get(execution.executionId) ?? '').trim()
  const failureMessage = (
    externalTaskFailureMessages.value.get(execution.executionId) ?? ''
  ).trim()
  if (!leaseToken) {
    externalTaskError.value = '当前页面内没有该任务的租约令牌，请重新认领'
    return
  }
  if (!code || code.length > 64 || !failureMessage || failureMessage.length > 1000) {
    externalTaskError.value = '失败代码和消息必须完整，且分别不超过 64/1000 个字符'
    return
  }
  mutation.value = `external-task:fail:${execution.executionId}`
  externalTaskError.value = ''
  try {
    const failed = await flowApi.failExternalTask(
      systemId.value,
      execution.executionId,
      { leaseToken, code, message: failureMessage },
    )
    clearExternalTaskLease(execution.executionId)
    await refreshAfterExternalTask(failed)
  } catch (cause) {
    externalTaskError.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

function openCreateDefinition() {
  editingDefinition.value = null
  definitionName.value = ''
  definitionApprovers.value = ['']
  definitionApprovalMode.value = 'SEQUENTIAL'
  definitionApprovalStages.value = null
  definitionApproverSource.value = { kind: 'FIXED', sourceId: null }
  definitionQuorumRule.value = null
  definitionDeadlinePolicy.value = null
  definitionDecisionCommentPolicy.value = null
  definitionDecisionEvidencePolicy.value = null
  definitionCompletionFailurePolicy.value = 'MANUAL_RETRY'
  definitionCompletionSteps.value = []
  definitionGateway.value = null
  definitionParallelGateway.value = null
  definitionInclusiveGateway.value = null
  definitionTriggerEnabled.value = false
  definitionTriggerModuleCode.value = ''
  definitionTriggerEvent.value = 'RECORD_ACTIVATED'
  definitionTriggerPriority.value = '0'
  definitionTriggerExclusive.value = true
  definitionTriggerConditions.value = []
  definitionTriggerStartAt.value = ''
  definitionTriggerIntervalMinutes.value = '60'
  definitionStatusMappingEnabled.value = false
  definitionStatusFieldCode.value = ''
  definitionStatusApprovedValue.value = ''
  definitionStatusRejectedValue.value = ''
  definitionStatusWithdrawnValue.value = ''
  definitionStatusTerminatedValue.value = ''
  definitionOpen.value = true
}

function newCompletionStep(type: FlowCompletionStep['type'] = 'EXTERNAL_TASK'): CompletionStepDraft {
  const suffix = definitionCompletionSteps.value.length + 1
  return {
    code: `completion_${suffix}`,
    name: `完成步骤 ${suffix}`,
    type,
    parallelGroup: '',
    topic: '',
    leaseSeconds: '60',
    externalMaxAttempts: '3',
    resultJsonLimitBytes: '8192',
    url: '',
    secretRef: '',
    timeoutSeconds: '10',
    webhookMaxAttempts: '3',
    baseBackoffSeconds: '5',
    subflowDefinitionId: '',
    subflowVersion: '',
    compensationType: 'EXTERNAL_TASK',
    compensationTopic: '',
    compensationLeaseSeconds: '60',
    compensationExternalMaxAttempts: '3',
    compensationResultJsonLimitBytes: '8192',
    compensationUrl: '',
    compensationSecretRef: '',
    compensationTimeoutSeconds: '10',
    compensationWebhookMaxAttempts: '3',
    compensationBaseBackoffSeconds: '5',
    compensationSubflowDefinitionId: '',
    compensationSubflowVersion: '',
  }
}

function copyCompletionSteps(steps?: FlowCompletionStep[]): CompletionStepDraft[] {
  return (steps ?? []).map(step => ({
    code: step.code,
    name: step.name,
    type: step.type,
    parallelGroup: step.parallelGroup ?? '',
    topic: step.externalTask?.topic ?? '',
    leaseSeconds: String(step.externalTask?.leaseSeconds ?? 60),
    externalMaxAttempts: String(step.externalTask?.maxAttempts ?? 3),
    resultJsonLimitBytes: String(step.externalTask?.resultJsonLimitBytes ?? 8192),
    url: step.webhook?.url ?? '',
    secretRef: step.webhook?.secretRef ? '********' : '',
    timeoutSeconds: String(step.webhook?.timeoutSeconds ?? 10),
    webhookMaxAttempts: String(step.webhook?.maxAttempts ?? 3),
    baseBackoffSeconds: String(step.webhook?.baseBackoffSeconds ?? 5),
    subflowDefinitionId: step.subflow?.definitionId ?? '',
    subflowVersion: String(step.subflow?.version ?? ''),
    compensationType: step.compensation?.type ?? 'EXTERNAL_TASK',
    compensationTopic: step.compensation?.externalTask?.topic ?? '',
    compensationLeaseSeconds: String(step.compensation?.externalTask?.leaseSeconds ?? 60),
    compensationExternalMaxAttempts: String(step.compensation?.externalTask?.maxAttempts ?? 3),
    compensationResultJsonLimitBytes: String(
      step.compensation?.externalTask?.resultJsonLimitBytes ?? 8192,
    ),
    compensationUrl: step.compensation?.webhook?.url ?? '',
    compensationSecretRef: step.compensation?.webhook?.secretRef ? '********' : '',
    compensationTimeoutSeconds: String(step.compensation?.webhook?.timeoutSeconds ?? 10),
    compensationWebhookMaxAttempts: String(step.compensation?.webhook?.maxAttempts ?? 3),
    compensationBaseBackoffSeconds: String(
      step.compensation?.webhook?.baseBackoffSeconds ?? 5,
    ),
    compensationSubflowDefinitionId: step.compensation?.subflow?.definitionId ?? '',
    compensationSubflowVersion: String(step.compensation?.subflow?.version ?? ''),
  }))
}

function addCompletionStep(type: FlowCompletionStep['type'] = 'EXTERNAL_TASK') {
  if (definitionCompletionSteps.value.length >= 8) return
  definitionCompletionSteps.value = [
    ...definitionCompletionSteps.value,
    newCompletionStep(type),
  ]
}

function removeCompletionStep(index: number) {
  definitionCompletionSteps.value = definitionCompletionSteps.value.filter(
    (_, current) => current !== index,
  )
}

function moveCompletionStep(index: number, offset: -1 | 1) {
  const target = index + offset
  if (target < 0 || target >= definitionCompletionSteps.value.length) return
  const next = [...definitionCompletionSteps.value]
  const [step] = next.splice(index, 1)
  next.splice(target, 0, step!)
  definitionCompletionSteps.value = next
}

function setCompletionStepType(index: number, type: FlowCompletionStep['type']) {
  const step = definitionCompletionSteps.value[index]
  if (step) step.type = type
}

function setCompletionCompensationType(index: number, type: FlowCompletionStep['type']) {
  const step = definitionCompletionSteps.value[index]
  if (step) step.compensationType = type
}

function buildCompletionCompensation(
  draft: CompletionStepDraft,
): FlowCompletionCompensation | false {
  if (draft.compensationType === 'EXTERNAL_TASK') {
    const leaseSeconds = Number(draft.compensationLeaseSeconds)
    const maxAttempts = Number(draft.compensationExternalMaxAttempts)
    const resultJsonLimitBytes = Number(draft.compensationResultJsonLimitBytes)
    if (
      !/^[a-z][a-z0-9._-]{0,63}$/.test(draft.compensationTopic.trim())
      || !Number.isInteger(leaseSeconds)
      || leaseSeconds < 30
      || leaseSeconds > 900
      || !Number.isInteger(maxAttempts)
      || maxAttempts < 1
      || maxAttempts > 10
      || !Number.isInteger(resultJsonLimitBytes)
      || resultJsonLimitBytes < 1
      || resultJsonLimitBytes > 8192
    ) return false
    return {
      type: 'EXTERNAL_TASK',
      externalTask: {
        topic: draft.compensationTopic.trim(),
        leaseSeconds,
        maxAttempts,
        resultJsonLimitBytes,
      },
    }
  }
  if (draft.compensationType === 'SUBFLOW') {
    const definitionId = draft.compensationSubflowDefinitionId.trim()
    const version = Number(draft.compensationSubflowVersion)
    if (
      !/^[1-9][0-9]{0,18}$/.test(definitionId)
      || !Number.isInteger(version)
      || version < 1
    ) return false
    return {
      type: 'SUBFLOW',
      subflow: { definitionId, version },
    }
  }
  const urlText = draft.compensationUrl.trim()
  const timeoutSeconds = Number(draft.compensationTimeoutSeconds)
  const maxAttempts = Number(draft.compensationWebhookMaxAttempts)
  const baseBackoffSeconds = Number(draft.compensationBaseBackoffSeconds)
  let url: URL
  try {
    url = new URL(urlText)
  } catch {
    return false
  }
  if (
    url.protocol !== 'https:'
    || Boolean(url.username || url.password || url.hash)
    || urlText.length > 1024
    || draft.compensationSecretRef.length > 512
    || (!editingDefinition.value && draft.compensationSecretRef === '********')
    || !Number.isInteger(timeoutSeconds)
    || timeoutSeconds < 1
    || timeoutSeconds > 30
    || !Number.isInteger(maxAttempts)
    || maxAttempts < 1
    || maxAttempts > 10
    || !Number.isInteger(baseBackoffSeconds)
    || baseBackoffSeconds < 1
    || baseBackoffSeconds > 300
  ) return false
  return {
    type: 'WEBHOOK',
    webhook: {
      url: urlText,
      secretRef: draft.compensationSecretRef,
      timeoutSeconds,
      maxAttempts,
      baseBackoffSeconds,
    },
  }
}

function buildCompletionSteps(): FlowCompletionStep[] | false {
  const drafts = definitionCompletionSteps.value
  if (drafts.length > 8) return false
  const codes = new Set<string>()
  const completedGroups = new Set<string>()
  const steps: FlowCompletionStep[] = []
  for (let index = 0; index < drafts.length; index += 1) {
    const draft = drafts[index]!
    const code = draft.code.trim()
    const name = draft.name.trim()
    const parallelGroup = draft.parallelGroup.trim()
    if (
      !/^[a-z][a-z0-9_]{0,63}$/.test(code)
      || codes.has(code)
      || !name
      || name.length > 80
      || (parallelGroup && !/^[a-z][a-z0-9_]{0,63}$/.test(parallelGroup))
    ) return false
    if (parallelGroup) {
      if (completedGroups.has(parallelGroup)) return false
      const previousGroup = drafts[index - 1]?.parallelGroup.trim()
      const nextGroup = drafts[index + 1]?.parallelGroup.trim()
      if (previousGroup !== parallelGroup && nextGroup !== parallelGroup) return false
      if (previousGroup === parallelGroup && nextGroup !== parallelGroup) {
        completedGroups.add(parallelGroup)
      }
    }
    codes.add(code)
    const compensation = definitionCompletionFailurePolicy.value === 'COMPENSATE'
      ? buildCompletionCompensation(draft)
      : null
    if (compensation === false) return false
    if (draft.type === 'EXTERNAL_TASK') {
      const leaseSeconds = Number(draft.leaseSeconds)
      const maxAttempts = Number(draft.externalMaxAttempts)
      const resultJsonLimitBytes = Number(draft.resultJsonLimitBytes)
      if (
        !/^[a-z][a-z0-9._-]{0,63}$/.test(draft.topic.trim())
        || !Number.isInteger(leaseSeconds)
        || leaseSeconds < 30
        || leaseSeconds > 900
        || !Number.isInteger(maxAttempts)
        || maxAttempts < 1
        || maxAttempts > 10
        || !Number.isInteger(resultJsonLimitBytes)
        || resultJsonLimitBytes < 1
        || resultJsonLimitBytes > 8192
      ) return false
      steps.push({
        code,
        name,
        type: 'EXTERNAL_TASK',
        ...(parallelGroup ? { parallelGroup } : {}),
        ...(compensation ? { compensation } : {}),
        externalTask: {
          topic: draft.topic.trim(),
          leaseSeconds,
          maxAttempts,
          resultJsonLimitBytes,
        },
      })
      continue
    }
    if (draft.type === 'SUBFLOW') {
      const definitionId = draft.subflowDefinitionId.trim()
      const version = Number(draft.subflowVersion)
      if (
        !/^[1-9][0-9]{0,18}$/.test(definitionId)
        || !Number.isInteger(version)
        || version < 1
      ) return false
      steps.push({
        code,
        name,
        type: 'SUBFLOW',
        ...(parallelGroup ? { parallelGroup } : {}),
        ...(compensation ? { compensation } : {}),
        subflow: { definitionId, version },
      })
      continue
    }
    const urlText = draft.url.trim()
    const timeoutSeconds = Number(draft.timeoutSeconds)
    const maxAttempts = Number(draft.webhookMaxAttempts)
    const baseBackoffSeconds = Number(draft.baseBackoffSeconds)
    let url: URL
    try {
      url = new URL(urlText)
    } catch {
      return false
    }
    if (
      url.protocol !== 'https:'
      || Boolean(url.username || url.password || url.hash)
      || urlText.length > 1024
      || draft.secretRef.length > 512
      || (!editingDefinition.value && draft.secretRef === '********')
      || !Number.isInteger(timeoutSeconds)
      || timeoutSeconds < 1
      || timeoutSeconds > 30
      || !Number.isInteger(maxAttempts)
      || maxAttempts < 1
      || maxAttempts > 10
      || !Number.isInteger(baseBackoffSeconds)
      || baseBackoffSeconds < 1
      || baseBackoffSeconds > 300
    ) return false
    steps.push({
      code,
      name,
      type: 'WEBHOOK',
      ...(parallelGroup ? { parallelGroup } : {}),
      ...(compensation ? { compensation } : {}),
      webhook: {
        url: urlText,
        secretRef: draft.secretRef,
        timeoutSeconds,
        maxAttempts,
        baseBackoffSeconds,
      },
    })
  }
  return steps
}

function copyApprovalStages(stages?: FlowApprovalStage[]) {
  return stages?.map(stage => ({
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
  }))
}

function openReviseDefinition(definition: FlowDefinitionDraft) {
  editingDefinition.value = definition
  definitionName.value = definition.name
  definitionApprovers.value = definitionSequence(definition)
  definitionApprovalMode.value = definition.approvalMode ?? 'SEQUENTIAL'
  definitionApprovalStages.value = copyApprovalStages(definition.approvalStages) ?? null
  definitionApproverSource.value = definition.approverSource
    ? { ...definition.approverSource }
    : { kind: 'FIXED', sourceId: null }
  definitionQuorumRule.value = definition.quorumRule
    ? { ...definition.quorumRule }
    : null
  definitionDeadlinePolicy.value = definition.deadlinePolicy
    ? { ...definition.deadlinePolicy }
    : null
  definitionDecisionCommentPolicy.value = definition.decisionCommentPolicy
    ? { ...definition.decisionCommentPolicy }
    : null
  definitionDecisionEvidencePolicy.value = definition.decisionEvidencePolicy
    ? {
        ...definition.decisionEvidencePolicy,
        allowedMimeFamilies: [...definition.decisionEvidencePolicy.allowedMimeFamilies],
      }
    : null
  definitionCompletionFailurePolicy.value = definition.completionFailurePolicy ?? 'MANUAL_RETRY'
  definitionCompletionSteps.value = copyCompletionSteps(definition.completionSteps)
  const authoritativeStage = definition.approvalStages?.[0]
  if (authoritativeStage) {
    definitionApprovers.value = [...authoritativeStage.approverIds]
    definitionApprovalMode.value = authoritativeStage.approvalMode
    definitionApproverSource.value = { ...authoritativeStage.approverSource }
    definitionQuorumRule.value = authoritativeStage.quorumRule
      ? { ...authoritativeStage.quorumRule }
      : null
    definitionDeadlinePolicy.value = authoritativeStage.deadlinePolicy
      ? { ...authoritativeStage.deadlinePolicy }
      : null
    definitionDecisionCommentPolicy.value = authoritativeStage.decisionCommentPolicy
      ? { ...authoritativeStage.decisionCommentPolicy }
      : null
    definitionDecisionEvidencePolicy.value = authoritativeStage.decisionEvidencePolicy
      ? {
          ...authoritativeStage.decisionEvidencePolicy,
          allowedMimeFamilies: [
            ...authoritativeStage.decisionEvidencePolicy.allowedMimeFamilies,
          ],
        }
      : null
  }
  definitionGateway.value = definition.gateway
    ? {
        branches: definition.gateway.branches.map((branch) => ({
          code: branch.code,
          name: branch.name,
          defaultBranch: branch.defaultBranch,
          conditions: branch.conditions.map((condition) => ({
            fieldCode: condition.fieldCode,
            operator: condition.operator,
            valueText: triggerValueOperators.has(condition.operator)
              ? JSON.stringify(condition.value)
              : '',
          })),
          approverIds: [...branch.approverIds],
          approvalMode: branch.approvalMode ?? 'SEQUENTIAL',
          approverSource: branch.approverSource
            ? { ...branch.approverSource }
            : { kind: 'FIXED', sourceId: null },
          quorumRule: branch.quorumRule ? { ...branch.quorumRule } : undefined,
          deadlinePolicy: branch.deadlinePolicy ? { ...branch.deadlinePolicy } : undefined,
          decisionCommentPolicy: branch.decisionCommentPolicy
            ? { ...branch.decisionCommentPolicy }
            : undefined,
          decisionEvidencePolicy: branch.decisionEvidencePolicy
            ? {
                ...branch.decisionEvidencePolicy,
                allowedMimeFamilies: [...branch.decisionEvidencePolicy.allowedMimeFamilies],
              }
            : undefined,
          approvalStages: copyApprovalStages(branch.approvalStages),
        })),
      }
    : null
  definitionParallelGateway.value = definition.parallelGateway
    ? {
        branches: definition.parallelGateway.branches.map((branch) => ({
          code: branch.code,
          name: branch.name,
          approverIds: [...branch.approverIds],
          approvalMode: branch.approvalMode,
          approverSource: branch.approverSource
            ? { ...branch.approverSource }
            : { kind: 'FIXED', sourceId: null },
          quorumRule: branch.quorumRule ? { ...branch.quorumRule } : undefined,
          deadlinePolicy: branch.deadlinePolicy ? { ...branch.deadlinePolicy } : undefined,
          decisionCommentPolicy: branch.decisionCommentPolicy
            ? { ...branch.decisionCommentPolicy }
            : undefined,
          decisionEvidencePolicy: branch.decisionEvidencePolicy
            ? {
                ...branch.decisionEvidencePolicy,
                allowedMimeFamilies: [...branch.decisionEvidencePolicy.allowedMimeFamilies],
              }
            : undefined,
          approvalStages: copyApprovalStages(branch.approvalStages),
        })),
      }
    : null
  definitionInclusiveGateway.value = definition.inclusiveGateway
    ? {
        branches: definition.inclusiveGateway.branches.map((branch) => ({
          code: branch.code,
          name: branch.name,
          defaultBranch: branch.defaultBranch,
          conditions: branch.conditions.map((condition) => ({
            fieldCode: condition.fieldCode,
            operator: condition.operator,
            valueText: condition.value === undefined ? '' : JSON.stringify(condition.value),
          })),
          approverIds: [...branch.approverIds],
          approvalMode: branch.approvalMode,
          approverSource: branch.approverSource
            ? { ...branch.approverSource }
            : { kind: 'FIXED', sourceId: null },
          quorumRule: branch.quorumRule ? { ...branch.quorumRule } : undefined,
          deadlinePolicy: branch.deadlinePolicy ? { ...branch.deadlinePolicy } : undefined,
          decisionCommentPolicy: branch.decisionCommentPolicy
            ? { ...branch.decisionCommentPolicy }
            : undefined,
          decisionEvidencePolicy: branch.decisionEvidencePolicy
            ? {
                ...branch.decisionEvidencePolicy,
                allowedMimeFamilies: [...branch.decisionEvidencePolicy.allowedMimeFamilies],
              }
            : undefined,
          approvalStages: copyApprovalStages(branch.approvalStages),
        })),
      }
    : null
  definitionTriggerEnabled.value = Boolean(definition.triggerBinding)
  definitionTriggerModuleCode.value = definition.triggerBinding?.moduleCode ?? ''
  definitionTriggerEvent.value = definition.triggerBinding?.event ?? 'RECORD_ACTIVATED'
  definitionTriggerPriority.value = String(definition.triggerBinding?.priority ?? 0)
  definitionTriggerExclusive.value = definition.triggerBinding?.exclusive ?? true
  definitionTriggerStartAt.value = definition.triggerBinding?.startAt ?? ''
  definitionTriggerIntervalMinutes.value = String(
    definition.triggerBinding?.intervalMinutes ?? 60,
  )
  definitionTriggerConditions.value = (definition.triggerBinding?.conditions ?? []).map((condition) => ({
    fieldCode: condition.fieldCode,
    operator: condition.operator,
    valueText: triggerValueOperators.has(condition.operator)
      ? JSON.stringify(condition.value)
      : '',
  }))
  definitionStatusMappingEnabled.value = Boolean(definition.recordStatusMapping)
  definitionStatusFieldCode.value = definition.recordStatusMapping?.fieldCode ?? ''
  definitionStatusApprovedValue.value = definition.recordStatusMapping?.approvedValue ?? ''
  definitionStatusRejectedValue.value = definition.recordStatusMapping?.rejectedValue ?? ''
  definitionStatusWithdrawnValue.value = definition.recordStatusMapping?.withdrawnValue ?? ''
  definitionStatusTerminatedValue.value = definition.recordStatusMapping?.terminatedValue ?? ''
  definitionOpen.value = true
}

function definitionSequence(definition: FlowDefinitionDraft) {
  return definition.approverIds?.length ? [...definition.approverIds] : [definition.approverId]
}

function approvalModeLabel(approvalMode: FlowApprovalMode | undefined) {
  return {
    SEQUENTIAL: '顺序审批',
    ANY: '任一同意',
    ALL: '全员同意',
    QUORUM: '会签阈值',
  }[approvalMode ?? 'SEQUENTIAL']
}

function approverSourceLabel(source?: FlowApproverSource) {
  const resolved = source ?? { kind: 'FIXED' as const, sourceId: null }
  return {
    FIXED: '固定成员',
    ROLE: `租户角色 ${resolved.sourceId ?? '—'}`,
    DEPARTMENT: `租户部门 ${resolved.sourceId ?? '—'}`,
    DEPARTMENT_LEADER: `部门负责人（部门 ${resolved.sourceId ?? '—'}）`,
    REQUESTER: '请求人本人',
    REQUESTER_MANAGER: '请求人的直属主管',
    REQUESTER_DEPARTMENT_LEADER: '请求人所属部门负责人',
    RECORD_MEMBER_FIELD: `记录成员字段（${resolved.moduleCode ?? '—'} · #${
      resolved.sourceId ?? '—'
    }）`,
    PREVIOUS_HANDLER: '上一阶段实际处理人',
  }[resolved.kind]
}

function configuredApproverSources(
  source?: FlowApproverSource,
  gateway?: FlowGateway | null,
  parallelGateway?: FlowParallelGateway | null,
  inclusiveGateway?: FlowInclusiveGateway | null,
) {
  return [
    source,
    ...(gateway?.branches.map(branch => branch.approverSource) ?? []),
    ...(gateway?.branches.flatMap(branch =>
      branch.approvalStages?.map(stage => stage.approverSource) ?? []) ?? []),
    ...(parallelGateway?.branches.map(branch => branch.approverSource) ?? []),
    ...(parallelGateway?.branches.flatMap(branch =>
      branch.approvalStages?.map(stage => stage.approverSource) ?? []) ?? []),
    ...(inclusiveGateway?.branches.map(branch => branch.approverSource) ?? []),
    ...(inclusiveGateway?.branches.flatMap(branch =>
      branch.approvalStages?.map(stage => stage.approverSource) ?? []) ?? []),
  ].filter((item): item is FlowApproverSource => Boolean(item))
}

function definitionApproverSources(definition: FlowDefinitionDraft) {
  return configuredApproverSources(
    definition.approverSource,
    definition.gateway,
    definition.parallelGateway,
    definition.inclusiveGateway,
  )
}

function recordMemberApproverSources(definition: FlowDefinitionDraft) {
  return definitionApproverSources(definition).filter(
    source => source.kind === 'RECORD_MEMBER_FIELD',
  )
}

const simulationUsesRecordMemberField = computed(() => (
  Boolean(preflightDefinition.value
    && recordMemberApproverSources(preflightDefinition.value).length)
))

function simulationApproverSource(branchCode?: string | null) {
  const definition = preflightDefinition.value
  if (!definition) return undefined
  if (branchCode) {
    const branch = definition.inclusiveGateway?.branches.find(item => item.code === branchCode)
      ?? definition.parallelGateway?.branches.find(item => item.code === branchCode)
      ?? definition.gateway?.branches.find(item => item.code === branchCode)
    if (branch) return branch.approvalStages?.[0]?.approverSource ?? branch.approverSource
  }
  return definition.approverSource
}

function deadlinePolicyLabel(policy?: FlowDeadlinePolicy | null) {
  if (!policy) return '未配置审批时限'
  const action = {
    NONE: '仅标记逾期',
    AUTO_APPROVE: '自动通过',
    AUTO_REJECT: '自动拒绝',
  }[policy.timeoutAction]
  const reminder = policy.remindBeforeMinutes
    ? `，提前 ${policy.remindBeforeMinutes} 分钟提醒`
    : ''
  return `${policy.timeoutMinutes} 分钟${reminder}，超时${action}`
}

function decisionCommentPolicyLabel(policy?: FlowDecisionCommentPolicy | null) {
  const resolved = policy ?? defaultDecisionCommentPolicy
  const approve = resolved.approveRequired ? '通过必填' : '通过选填'
  const reject = resolved.rejectRequired ? '驳回必填' : '驳回选填'
  return `${approve}，${reject}，必填最少 ${resolved.minimumLength} 字`
}

function decisionEvidencePolicyLabel(policy?: FlowDecisionEvidencePolicy | null) {
  if (!policy) return '未配置审批证据'
  const mime = policy.allowedMimeFamilies.length
    ? policy.allowedMimeFamilies.join('、')
    : '不限类型'
  return `附件 ${policy.minimumAttachments}–${policy.maximumAttachments} 个 · ${mime} · 签名 ${
    policy.signatureMode
  }`
}

function groupedCompletionStages<T extends { parallelGroup?: string | null }>(items: T[]) {
  const stages: T[][] = []
  for (const item of items) {
    const previous = stages.at(-1)
    if (
      item.parallelGroup
      && previous?.[0]?.parallelGroup === item.parallelGroup
    ) {
      previous.push(item)
    } else {
      stages.push([item])
    }
  }
  return stages
}

function completionFailurePolicyLabel(policy?: FlowCompletionFailurePolicy | null) {
  return (policy ?? 'MANUAL_RETRY') === 'COMPENSATE'
    ? '失败后逆序补偿'
    : '失败后管理员手动重试'
}

function completionCompensationSummary(compensation?: FlowCompletionCompensation | null) {
  if (!compensation) return '无补偿'
  const target = compensation.type === 'EXTERNAL_TASK'
    ? compensation.externalTask.topic
    : compensation.type === 'WEBHOOK'
      ? compensation.webhook.url
      : `定义 ${compensation.subflow.definitionId} / v${compensation.subflow.version}`
  return `${compensation.type} · ${target}`
}

function completionStepsSummary(steps?: FlowCompletionStep[] | null) {
  if (!steps?.length) return '人工审批完成后直接结束'
  return groupedCompletionStages(steps).map((stage, index) => {
    const members = stage.map((step) => {
      const target = step.type === 'EXTERNAL_TASK'
        ? step.externalTask.topic
        : step.type === 'WEBHOOK'
          ? step.webhook.url
          : `定义 ${step.subflow.definitionId} / v${step.subflow.version}`
      const compensation = step.compensation
        ? `；补偿 ${completionCompensationSummary(step.compensation)}`
        : ''
      return `${step.name}（${step.type} · ${target}${compensation}）`
    }).join(' + ')
    return stage[0]!.parallelGroup
      ? `${index + 1}. 并行组 ${stage[0]!.parallelGroup}[${members}]`
      : `${index + 1}. ${members}`
  }).join(' → ')
}

function completionDraftStagesSummary() {
  if (!definitionCompletionSteps.value.length) return '未配置完成步骤'
  return groupedCompletionStages(definitionCompletionSteps.value)
    .map((stage, index) => (
      stage[0]!.parallelGroup
        ? `阶段 ${index + 1}：并行组 ${stage[0]!.parallelGroup}（${stage.length} 个成员）`
        : `阶段 ${index + 1}：${stage[0]!.name || stage[0]!.code || '未命名步骤'}`
    ))
    .join(' → ')
}

function completionParallelGroupSize(group: string) {
  if (!group.trim()) return 1
  return definitionCompletionSteps.value.filter(
    step => step.parallelGroup.trim() === group.trim(),
  ).length
}

function triggerBindingSummary(binding: FlowDefinitionDraft['triggerBinding']) {
  if (!binding) return '未配置记录事件自动触发'
  if (binding.event === 'PERIODIC') {
    return `固定周期 · 每 ${binding.intervalMinutes} 分钟 · 从 ${time(binding.startAt ?? '')} · 请求人 ${
      binding.requesterMemberId ?? '—'
    }`
  }
  const conditions = binding.conditions ?? []
  const conditionSummary = conditions.length
    ? conditions
        .map((condition) => `${condition.fieldCode} ${condition.operator}${
          triggerValueOperators.has(condition.operator) ? ` ${JSON.stringify(condition.value)}` : ''
        }`)
        .join(' AND ')
    : '无条件（匹配全部）'
  return `${binding.moduleCode} · ${binding.event} · 优先级 ${binding.priority} · ${
    binding.exclusive ? '独占' : '并行'
  } · ALL 条件：${conditionSummary}`
}

function statusMappingSummary(mapping: FlowDefinitionDraft['recordStatusMapping']) {
  if (!mapping) return '未配置终态 STATUS 映射'
  return `${mapping.fieldCode} · APPROVED→${mapping.approvedValue} · REJECTED→${
    mapping.rejectedValue
  } · WITHDRAWN→${mapping.withdrawnValue} · TERMINATED→${mapping.terminatedValue}`
}

function gatewaySummary(gateway: FlowGateway | null | undefined) {
  if (!gateway) return '未配置条件网关'
  return gateway.branches.map((branch) => (
    `${branch.defaultBranch ? '默认' : branch.name} · ${approvalModeLabel(branch.approvalMode)} · ${
      approverSourceLabel(branch.approverSource)
    } → ${branch.approverIds.join(' → ')} · ${approvalStagesSummary(branch.approvalStages)}`
  )).join('；')
}

function parallelGatewaySummary(gateway: FlowParallelGateway | null | undefined) {
  if (!gateway) return ''
  return gateway.branches.map((branch) => (
    `${branch.name} · ${approvalModeLabel(branch.approvalMode)} · ${
      approverSourceLabel(branch.approverSource)
    } → ${branch.approverIds.join(' → ')} · ${approvalStagesSummary(branch.approvalStages)}`
  )).join('；')
}

function inclusiveGatewaySummary(gateway: FlowInclusiveGateway | null | undefined) {
  if (!gateway) return ''
  return gateway.branches.map((branch) => (
    `${branch.defaultBranch ? '默认' : branch.name} · ${approvalModeLabel(branch.approvalMode)} · ${
      approverSourceLabel(branch.approverSource)
    } → ${branch.approverIds.join(' → ')} · ${approvalStagesSummary(branch.approvalStages)}`
  )).join('；')
}

function approvalStagesSummary(stages?: FlowApprovalStage[]) {
  if (!stages?.length) return '兼容单阶段'
  return stages.map((stage, index) => (
    `${index + 1}. ${stage.name}（${stage.code} · ${
      approverSourceLabel(stage.approverSource)
    } · ${approvalModeLabel(stage.approvalMode)}）`
  )).join(' → ')
}

function stageStatusColor(status: 'WAITING' | 'ACTIVE' | 'APPROVED' | 'REJECTED') {
  return {
    WAITING: 'default',
    ACTIVE: 'blue',
    APPROVED: 'green',
    REJECTED: 'red',
  }[status]
}

function publishedDefinition(definitionId: string) {
  return publishedVersions.value.get(definitionId)
}

function periodicSchedule(definitionId: string) {
  return periodicSchedules.value.get(definitionId)
}

async function loadDefinitionVersions() {
  const definition = versionHistoryDefinition.value
  if (!definition) return
  versionHistoryLoading.value = true
  versionHistoryError.value = ''
  try {
    const result = await flowApi.listDefinitionVersions(
      systemId.value,
      definition.definitionId,
      versionHistoryPage.value,
      versionHistoryPageSize,
    )
    versionHistoryItems.value = result.items
    versionHistoryTotal.value = result.total
  } catch (cause) {
    versionHistoryItems.value = []
    versionHistoryTotal.value = 0
    versionHistoryError.value = message(cause)
  } finally {
    versionHistoryLoading.value = false
  }
}

function openVersionHistory(definition: FlowDefinitionDraft) {
  versionHistoryDefinition.value = definition
  versionHistoryItems.value = []
  versionHistoryPage.value = 1
  versionHistoryTotal.value = 0
  versionHistoryError.value = ''
  preflightOpen.value = false
  preflightDefinition.value = null
  preflightCheck.value = null
  preflightSimulation.value = null
  preflightError.value = ''
  simulationRequesterId.value = ''
  simulationBusinessKey.value = ''
  simulationTriggerValues.value = '{}'
  versionHistoryOpen.value = true
  void loadDefinitionVersions()
}

function changeVersionHistoryPage(page: number) {
  versionHistoryPage.value = page
  void loadDefinitionVersions()
}

async function restoreDefinitionVersion(version: FlowDefinitionVersion) {
  const definition = versionHistoryDefinition.value
  if (!definition) return
  mutation.value = `restore:${definition.definitionId}:${version.version}`
  versionHistoryError.value = ''
  try {
    const restored = await flowApi.restoreDefinitionVersion(
      systemId.value,
      definition.definitionId,
      version.version,
    )
    definitions.value = definitions.value.map((item) => (
      item.definitionId === restored.definitionId ? restored : item
    ))
    versionHistoryOpen.value = false
    openReviseDefinition(restored)
  } catch (cause) {
    versionHistoryError.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

function defaultSimulationValues(definition: FlowDefinitionDraft) {
  const conditions = [
    ...(definition.triggerBinding?.conditions ?? []),
    ...(definition.gateway?.branches.find((branch) => !branch.defaultBranch)?.conditions ?? []),
  ]
  return Object.fromEntries(conditions.map((condition) => [
    condition.fieldCode,
    condition.operator === 'EMPTY'
      ? null
      : condition.operator === 'NOT_EMPTY'
        ? 'sample'
        : condition.value,
  ]))
}

function preparePreflight(definition: FlowDefinitionDraft) {
  preflightDefinition.value = definition
  preflightCheck.value = null
  preflightSimulation.value = null
  preflightError.value = ''
  simulationRequesterId.value = session.context?.memberId ?? ''
  simulationBusinessKey.value = `simulation:${definition.definitionId}:r${definition.revision}`
  simulationTriggerValues.value = JSON.stringify(defaultSimulationValues(definition), null, 2)
  preflightOpen.value = true
}

function openDraftCheck(definition: FlowDefinitionDraft) {
  preparePreflight(definition)
  void runDraftCheck()
}

function openDraftSimulation(definition: FlowDefinitionDraft) {
  preparePreflight(definition)
  void runDraftSimulation()
}

async function runDraftCheck() {
  const definition = preflightDefinition.value
  if (!definition) return
  mutation.value = `check:${definition.definitionId}`
  preflightError.value = ''
  try {
    preflightCheck.value = await flowApi.checkDefinitionDraft(
      systemId.value,
      definition.definitionId,
    )
  } catch (cause) {
    preflightCheck.value = null
    preflightError.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function runDraftSimulation() {
  const definition = preflightDefinition.value
  if (!definition) return
  const recordMemberSources = recordMemberApproverSources(definition)
  if (
    recordMemberSources.length
    && definition.triggerBinding?.event === 'PERIODIC'
  ) {
    preflightError.value = '固定周期没有记录上下文，不能模拟记录成员字段审批来源'
    return
  }
  const requesterId = simulationRequesterId.value.trim()
  const businessKey = simulationBusinessKey.value.trim()
  if (!requesterId || !businessKey) {
    preflightError.value = '模拟请求人和业务标识不能为空'
    return
  }
  let values: Record<string, unknown> = {}
  const binding = definition.triggerBinding
  if (
    (binding && binding.event !== 'PERIODIC')
    || recordMemberSources.length
    || definition.gateway
    || definition.inclusiveGateway
  ) {
    try {
      const parsed = JSON.parse(simulationTriggerValues.value) as unknown
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
        throw new Error('invalid object')
      }
      values = parsed as Record<string, unknown>
    } catch {
      preflightError.value = '触发字段样本必须是 JSON 对象'
      return
    }
  }
  mutation.value = `simulate:${definition.definitionId}`
  preflightError.value = ''
  try {
    const result = await flowApi.simulateDefinitionDraft(
      systemId.value,
      definition.definitionId,
      {
        requesterId,
        businessKey,
        ...(definition.gateway
          || definition.inclusiveGateway
          || recordMemberSources.length
          ? { values }
          : {}),
        ...(binding && binding.event !== 'PERIODIC'
          ? {
              trigger: {
                moduleCode: binding.moduleCode,
                event: binding.event,
                values,
              },
            }
          : {}),
      },
    )
    preflightSimulation.value = result
    preflightCheck.value = result.check
  } catch (cause) {
    preflightSimulation.value = null
    preflightError.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

function addDefinitionApprover() {
  if (definitionApprovers.value.length < 10) {
    definitionApprovers.value = [...definitionApprovers.value, '']
  }
}

function removeDefinitionApprover(index: number) {
  if (definitionApprovers.value.length <= 1) return
  definitionApprovers.value = definitionApprovers.value.filter((_, itemIndex) => itemIndex !== index)
}

function moveDefinitionApprover(index: number, offset: -1 | 1) {
  const target = index + offset
  if (target < 0 || target >= definitionApprovers.value.length) return
  const next = [...definitionApprovers.value]
  const current = next[index]!
  next[index] = next[target]!
  next[target] = current
  definitionApprovers.value = next
}

function addTriggerCondition() {
  if (definitionTriggerConditions.value.length >= 10) return
  definitionTriggerConditions.value = [
    ...definitionTriggerConditions.value,
    { fieldCode: '', operator: 'EQ', valueText: '' },
  ]
}

function removeTriggerCondition(index: number) {
  definitionTriggerConditions.value = definitionTriggerConditions.value.filter(
    (_, itemIndex) => itemIndex !== index,
  )
}

function selectTriggerConditionOperator(index: number, value: string) {
  if (!triggerConditionOperators.includes(value as FlowTriggerConditionOperator)) return
  const next = [...definitionTriggerConditions.value]
  const current = next[index]
  if (!current) return
  const operator = value as FlowTriggerConditionOperator
  next[index] = {
    ...current,
    operator,
    valueText: triggerValueOperators.has(operator) ? current.valueText : '',
  }
  definitionTriggerConditions.value = next
}

function buildTriggerConditions(): FlowTriggerCondition[] | null {
  if (definitionTriggerConditions.value.length > 10) return null
  const result: FlowTriggerCondition[] = []
  for (const condition of definitionTriggerConditions.value) {
    const fieldCode = condition.fieldCode.trim()
    if (
      !/^[A-Za-z][A-Za-z0-9_]{0,63}$/.test(fieldCode)
      || !triggerConditionOperators.includes(condition.operator)
    ) {
      return null
    }
    if (!triggerValueOperators.has(condition.operator)) {
      result.push({ fieldCode, operator: condition.operator })
      continue
    }
    const valueText = condition.valueText.trim()
    if (!valueText) return null
    try {
      const value: unknown = JSON.parse(valueText)
      if (value === null) return null
      result.push({ fieldCode, operator: condition.operator, value })
    } catch {
      return null
    }
  }
  return result
}

function normalizedApproverSource(source?: FlowApproverSource): FlowApproverSource {
  const kind = source?.kind ?? 'FIXED'
  if (kind === 'PREVIOUS_HANDLER') return { kind }
  return {
    kind,
    sourceId: kind === 'FIXED'
      || kind === 'REQUESTER'
      || kind === 'REQUESTER_MANAGER'
      || kind === 'REQUESTER_DEPARTMENT_LEADER'
      ? null
      : (source?.sourceId ?? '').trim(),
    ...(kind === 'RECORD_MEMBER_FIELD'
      ? { moduleCode: source?.moduleCode?.trim() ?? '' }
      : {}),
  }
}

function validDeadlinePolicy(policy?: FlowDeadlinePolicy) {
  if (!policy) return true
  const reminder = policy.remindBeforeMinutes
  return Number.isInteger(policy.timeoutMinutes)
    && policy.timeoutMinutes >= 1
    && policy.timeoutMinutes <= 525600
    && (
      reminder === null
      || reminder === undefined
      || (
        Number.isInteger(reminder)
        && reminder >= 1
        && reminder < policy.timeoutMinutes
      )
    )
    && ['NONE', 'AUTO_APPROVE', 'AUTO_REJECT'].includes(policy.timeoutAction)
}

function validDecisionCommentPolicy(policy?: FlowDecisionCommentPolicy) {
  return !policy || (
    typeof policy.approveRequired === 'boolean'
    && typeof policy.rejectRequired === 'boolean'
    && Number.isInteger(policy.minimumLength)
    && policy.minimumLength >= 1
    && policy.minimumLength <= 500
  )
}

function validDecisionEvidencePolicy(policy?: FlowDecisionEvidencePolicy) {
  if (!policy) return true
  const allowed = new Set(['IMAGE', 'PDF', 'DOCUMENT', 'ARCHIVE', 'OTHER'])
  return Number.isInteger(policy.minimumAttachments)
    && Number.isInteger(policy.maximumAttachments)
    && policy.minimumAttachments >= 0
    && policy.minimumAttachments <= 5
    && policy.maximumAttachments >= policy.minimumAttachments
    && policy.maximumAttachments <= 5
    && policy.allowedMimeFamilies.length <= 5
    && new Set(policy.allowedMimeFamilies).size === policy.allowedMimeFamilies.length
    && policy.allowedMimeFamilies.every(family => allowed.has(family))
    && ['NONE', 'OPTIONAL', 'REQUIRED'].includes(policy.signatureMode)
}

function copyDecisionEvidencePolicy(policy?: FlowDecisionEvidencePolicy) {
  return policy
    ? { ...policy, allowedMimeFamilies: [...policy.allowedMimeFamilies] }
    : undefined
}

function validApproverRoute(
  approverIds: string[],
  approvalMode: FlowApprovalMode,
  source: FlowApproverSource,
  quorumRule?: FlowQuorumRule,
) {
  if (approvalMode === 'QUORUM') {
    if (!quorumRule
      || !Number.isInteger(quorumRule.value)
      || quorumRule.value < 1
      || (quorumRule.type === 'PERCENTAGE' && quorumRule.value > 100)
      || (quorumRule.type === 'COUNT' && quorumRule.value > 10)
      || (source.kind === 'FIXED' && quorumRule.type === 'COUNT'
        && quorumRule.value > approverIds.length)) {
      return false
    }
  } else if (quorumRule) {
    return false
  }
  if (
    source.kind === 'REQUESTER'
    || source.kind === 'REQUESTER_MANAGER'
    || source.kind === 'REQUESTER_DEPARTMENT_LEADER'
  ) {
    return source.sourceId === null
  }
  if (source.kind === 'PREVIOUS_HANDLER') return false
  if (source.kind === 'RECORD_MEMBER_FIELD') {
    return /^[1-9][0-9]{0,18}$/.test(source.sourceId ?? '')
      && /^[A-Za-z][A-Za-z0-9_]{0,63}$/.test(source.moduleCode ?? '')
  }
  if (source.kind !== 'FIXED') {
    return /^[1-9][0-9]{0,18}$/.test(source.sourceId ?? '')
  }
  return approverIds.length >= 1
    && approverIds.length <= 10
    && approverIds.every(value => /^[1-9][0-9]{0,18}$/.test(value))
    && (approvalMode === 'SEQUENTIAL' || new Set(approverIds).size === approverIds.length)
}

function buildBranchApprovalStages(
  configured?: FlowApprovalStage[],
): FlowApprovalStage[] | null | false {
  if (!configured) return null
  if (configured.length < 1 || configured.length > 10) return false
  const codes = new Set<string>()
  const stages: FlowApprovalStage[] = []
  for (let index = 0; index < configured.length; index += 1) {
    const stage = configured[index]!
    const code = stage.code.trim()
    const name = stage.name.trim()
    const approvalMode = stage.approvalMode ?? 'SEQUENTIAL'
    const source = normalizedApproverSource(stage.approverSource)
    const approverIds = stage.approverIds.map(value => value.trim())
    const contextual = source.kind === 'PREVIOUS_HANDLER'
      || source.kind === 'REQUESTER'
      || source.kind === 'REQUESTER_MANAGER'
      || source.kind === 'REQUESTER_DEPARTMENT_LEADER'
    if (
      !/^[a-z][a-z0-9_]{0,63}$/.test(code)
      || codes.has(code)
      || !name
      || name.length > 80
      || (index === 0 && source.kind === 'PREVIOUS_HANDLER')
      || (index > 0 && source.kind === 'DEPARTMENT_LEADER')
      || !validDeadlinePolicy(stage.deadlinePolicy)
      || !validDecisionCommentPolicy(stage.decisionCommentPolicy)
      || !validDecisionEvidencePolicy(stage.decisionEvidencePolicy)
    ) return false
    if (contextual) {
      if (
        approverIds.length
        || (source.kind === 'PREVIOUS_HANDLER' && source.sourceId !== undefined)
        || source.moduleCode !== undefined
      ) return false
      if (
        approvalMode === 'QUORUM'
        && (
          !stage.quorumRule
          || !Number.isInteger(stage.quorumRule.value)
          || stage.quorumRule.value < 1
          || (stage.quorumRule.type === 'PERCENTAGE' && stage.quorumRule.value > 100)
          || (stage.quorumRule.type === 'COUNT' && stage.quorumRule.value > 10)
        )
      ) return false
    } else if (!validApproverRoute(
      approverIds,
      approvalMode,
      source,
      stage.quorumRule,
    )) {
      return false
    }
    if (approvalMode !== 'QUORUM' && stage.quorumRule) return false
    const previous = stages[index - 1]
    if (
      source.kind === 'PREVIOUS_HANDLER'
      && previous?.deadlinePolicy?.timeoutAction === 'AUTO_APPROVE'
    ) return false
    codes.add(code)
    stages.push({
      code,
      name,
      approverIds: source.kind === 'FIXED' ? approverIds : [],
      approvalMode,
      approverSource: source.kind === 'PREVIOUS_HANDLER'
        ? { kind: 'PREVIOUS_HANDLER' }
        : source,
      ...(approvalMode === 'QUORUM' ? { quorumRule: { ...stage.quorumRule! } } : {}),
      ...(stage.deadlinePolicy ? { deadlinePolicy: { ...stage.deadlinePolicy } } : {}),
      ...(stage.decisionCommentPolicy
        ? { decisionCommentPolicy: { ...stage.decisionCommentPolicy } }
        : {}),
      ...(stage.decisionEvidencePolicy
        ? { decisionEvidencePolicy: copyDecisionEvidencePolicy(stage.decisionEvidencePolicy) }
        : {}),
    })
  }
  return stages
}

function buildGateway(): FlowGateway | null | false {
  const gateway = definitionGateway.value
  if (!gateway) return null
  if (gateway.branches.length < 2 || gateway.branches.length > 6) return false
  const codes = new Set<string>()
  const names = new Set<string>()
  const branches: FlowGateway['branches'] = []
  for (let branchIndex = 0; branchIndex < gateway.branches.length; branchIndex += 1) {
    const branch = gateway.branches[branchIndex]!
    const code = branch.code.trim()
    const name = branch.name.trim()
    const defaultBranch = branchIndex === gateway.branches.length - 1
    const approvalStages = buildBranchApprovalStages(branch.approvalStages)
    if (approvalStages === false) return false
    const primaryStage = approvalStages?.[0]
    const approverIds = primaryStage?.approverIds
      ?? branch.approverIds.map((value) => value.trim())
    const approvalMode = primaryStage?.approvalMode ?? branch.approvalMode ?? 'SEQUENTIAL'
    const approverSource = primaryStage?.approverSource
      ?? normalizedApproverSource(branch.approverSource)
    const quorumRule = approvalStages ? primaryStage!.quorumRule : branch.quorumRule
    const deadlinePolicy = approvalStages ? primaryStage!.deadlinePolicy : branch.deadlinePolicy
    const decisionCommentPolicy = approvalStages
      ? primaryStage!.decisionCommentPolicy
      : branch.decisionCommentPolicy
    const decisionEvidencePolicy = approvalStages
      ? primaryStage!.decisionEvidencePolicy
      : branch.decisionEvidencePolicy
    const requestApproverIds = approverSource.kind === 'FIXED' ? approverIds : []
    if (
      branch.defaultBranch !== defaultBranch
      || !/^[a-z][a-z0-9_]{0,63}$/.test(code)
      || !name
      || name.length > 80
      || codes.has(code)
      || names.has(name)
      || !validApproverRoute(approverIds, approvalMode, approverSource, quorumRule)
      || !validDeadlinePolicy(deadlinePolicy)
      || !validDecisionCommentPolicy(decisionCommentPolicy)
      || !validDecisionEvidencePolicy(decisionEvidencePolicy)
    ) {
      return false
    }
    codes.add(code)
    names.add(name)
    if (defaultBranch) {
      if (branch.conditions.length) return false
      branches.push({
        code, name, defaultBranch, conditions: [], approverIds: requestApproverIds, approvalMode,
        ...(approverSource.kind === 'FIXED' ? {} : { approverSource }),
        ...(approvalMode === 'QUORUM' ? { quorumRule } : {}),
        ...(deadlinePolicy ? { deadlinePolicy: { ...deadlinePolicy } } : {}),
        ...(decisionCommentPolicy
          ? { decisionCommentPolicy: { ...decisionCommentPolicy } }
          : {}),
        ...(decisionEvidencePolicy
          ? { decisionEvidencePolicy: copyDecisionEvidencePolicy(decisionEvidencePolicy) }
          : {}),
        ...(approvalStages ? { approvalStages } : {}),
      })
      continue
    }
    if (!branch.conditions.length || branch.conditions.length > 10) return false
    const conditions: FlowTriggerCondition[] = []
    for (const condition of branch.conditions) {
      const fieldCode = condition.fieldCode.trim()
      const operator = condition.operator as FlowTriggerConditionOperator
      if (
        !/^[A-Za-z][A-Za-z0-9_]{0,63}$/.test(fieldCode)
        || !triggerConditionOperators.includes(operator)
      ) {
        return false
      }
      if (!triggerValueOperators.has(operator)) {
        conditions.push({ fieldCode, operator })
        continue
      }
      try {
        const value: unknown = JSON.parse(condition.valueText.trim())
        if (value === null) return false
        conditions.push({ fieldCode, operator, value })
      } catch {
        return false
      }
    }
    branches.push({
      code, name, defaultBranch, conditions, approverIds: requestApproverIds, approvalMode,
      ...(approverSource.kind === 'FIXED' ? {} : { approverSource }),
      ...(approvalMode === 'QUORUM' ? { quorumRule } : {}),
      ...(deadlinePolicy ? { deadlinePolicy: { ...deadlinePolicy } } : {}),
      ...(decisionCommentPolicy
        ? { decisionCommentPolicy: { ...decisionCommentPolicy } }
        : {}),
      ...(decisionEvidencePolicy
        ? { decisionEvidencePolicy: copyDecisionEvidencePolicy(decisionEvidencePolicy) }
        : {}),
      ...(approvalStages ? { approvalStages } : {}),
    })
  }
  return { branches }
}

function buildInclusiveGateway(): FlowInclusiveGateway | null | false {
  const gateway = definitionInclusiveGateway.value
  if (!gateway) return null
  if (gateway.branches.length < 2 || gateway.branches.length > 5) return false
  const codes = new Set<string>()
  const names = new Set<string>()
  const branches: FlowInclusiveGateway['branches'] = []
  for (let branchIndex = 0; branchIndex < gateway.branches.length; branchIndex += 1) {
    const branch = gateway.branches[branchIndex]!
    const code = branch.code.trim()
    const name = branch.name.trim()
    const defaultBranch = branch.defaultBranch
    const approvalStages = buildBranchApprovalStages(branch.approvalStages)
    if (approvalStages === false) return false
    const primaryStage = approvalStages?.[0]
    const approverIds = primaryStage?.approverIds
      ?? branch.approverIds.map((value) => value.trim())
    const approvalMode = primaryStage?.approvalMode ?? branch.approvalMode ?? 'SEQUENTIAL'
    const approverSource = primaryStage?.approverSource
      ?? normalizedApproverSource(branch.approverSource)
    const quorumRule = approvalStages ? primaryStage!.quorumRule : branch.quorumRule
    const deadlinePolicy = approvalStages ? primaryStage!.deadlinePolicy : branch.deadlinePolicy
    const decisionCommentPolicy = approvalStages
      ? primaryStage!.decisionCommentPolicy
      : branch.decisionCommentPolicy
    const decisionEvidencePolicy = approvalStages
      ? primaryStage!.decisionEvidencePolicy
      : branch.decisionEvidencePolicy
    const requestApproverIds = approverSource.kind === 'FIXED' ? approverIds : []
    if (
      (defaultBranch && branchIndex !== gateway.branches.length - 1)
      || !/^[a-z][a-z0-9_]{0,63}$/.test(code)
      || !name
      || name.length > 80
      || codes.has(code)
      || names.has(name)
      || !validApproverRoute(approverIds, approvalMode, approverSource, quorumRule)
      || !validDeadlinePolicy(deadlinePolicy)
      || !validDecisionCommentPolicy(decisionCommentPolicy)
      || !validDecisionEvidencePolicy(decisionEvidencePolicy)
    ) {
      return false
    }
    codes.add(code)
    names.add(name)
    if (defaultBranch) {
      if (branch.conditions.length) return false
      branches.push({
        code, name, defaultBranch, conditions: [], approverIds: requestApproverIds, approvalMode,
        ...(approverSource.kind === 'FIXED' ? {} : { approverSource }),
        ...(approvalMode === 'QUORUM' ? { quorumRule } : {}),
        ...(deadlinePolicy ? { deadlinePolicy: { ...deadlinePolicy } } : {}),
        ...(decisionCommentPolicy
          ? { decisionCommentPolicy: { ...decisionCommentPolicy } }
          : {}),
        ...(decisionEvidencePolicy
          ? { decisionEvidencePolicy: copyDecisionEvidencePolicy(decisionEvidencePolicy) }
          : {}),
        ...(approvalStages ? { approvalStages } : {}),
      })
      continue
    }
    if (!branch.conditions.length || branch.conditions.length > 10) return false
    const conditions: FlowTriggerCondition[] = []
    for (const condition of branch.conditions) {
      const fieldCode = condition.fieldCode.trim()
      const operator = condition.operator as FlowTriggerConditionOperator
      if (
        !/^[A-Za-z][A-Za-z0-9_]{0,63}$/.test(fieldCode)
        || !triggerConditionOperators.includes(operator)
      ) {
        return false
      }
      if (!triggerValueOperators.has(operator)) {
        conditions.push({ fieldCode, operator })
        continue
      }
      try {
        const value: unknown = JSON.parse(condition.valueText.trim())
        if (value === null) return false
        conditions.push({ fieldCode, operator, value })
      } catch {
        return false
      }
    }
    branches.push({
      code, name, defaultBranch, conditions, approverIds: requestApproverIds, approvalMode,
      ...(approverSource.kind === 'FIXED' ? {} : { approverSource }),
      ...(approvalMode === 'QUORUM' ? { quorumRule } : {}),
      ...(deadlinePolicy ? { deadlinePolicy: { ...deadlinePolicy } } : {}),
      ...(decisionCommentPolicy
        ? { decisionCommentPolicy: { ...decisionCommentPolicy } }
        : {}),
      ...(decisionEvidencePolicy
        ? { decisionEvidencePolicy: copyDecisionEvidencePolicy(decisionEvidencePolicy) }
        : {}),
      ...(approvalStages ? { approvalStages } : {}),
    })
  }
  return { branches }
}

function buildParallelGateway(): FlowParallelGateway | null | false {
  const gateway = definitionParallelGateway.value
  if (!gateway) return null
  if (gateway.branches.length < 2 || gateway.branches.length > 5) return false
  const codes = new Set<string>()
  const names = new Set<string>()
  const branches: FlowParallelGateway['branches'] = []
  for (const branch of gateway.branches) {
    const code = branch.code.trim()
    const name = branch.name.trim()
    const approvalStages = buildBranchApprovalStages(branch.approvalStages)
    if (approvalStages === false) return false
    const primaryStage = approvalStages?.[0]
    const approverIds = primaryStage?.approverIds
      ?? branch.approverIds.map((value) => value.trim())
    const approvalMode = primaryStage?.approvalMode ?? branch.approvalMode ?? 'SEQUENTIAL'
    const approverSource = primaryStage?.approverSource
      ?? normalizedApproverSource(branch.approverSource)
    const quorumRule = approvalStages ? primaryStage!.quorumRule : branch.quorumRule
    const deadlinePolicy = approvalStages ? primaryStage!.deadlinePolicy : branch.deadlinePolicy
    const decisionCommentPolicy = approvalStages
      ? primaryStage!.decisionCommentPolicy
      : branch.decisionCommentPolicy
    const decisionEvidencePolicy = approvalStages
      ? primaryStage!.decisionEvidencePolicy
      : branch.decisionEvidencePolicy
    const requestApproverIds = approverSource.kind === 'FIXED' ? approverIds : []
    if (
      !/^[a-z][a-z0-9_]{0,63}$/.test(code)
      || !name
      || name.length > 80
      || codes.has(code)
      || names.has(name)
      || !validApproverRoute(approverIds, approvalMode, approverSource, quorumRule)
      || !validDeadlinePolicy(deadlinePolicy)
      || !validDecisionCommentPolicy(decisionCommentPolicy)
      || !validDecisionEvidencePolicy(decisionEvidencePolicy)
    ) {
      return false
    }
    codes.add(code)
    names.add(name)
    branches.push({
      code, name, approverIds: requestApproverIds, approvalMode,
      ...(approverSource.kind === 'FIXED' ? {} : { approverSource }),
      ...(approvalMode === 'QUORUM' ? { quorumRule } : {}),
      ...(deadlinePolicy ? { deadlinePolicy: { ...deadlinePolicy } } : {}),
      ...(decisionCommentPolicy
        ? { decisionCommentPolicy: { ...decisionCommentPolicy } }
        : {}),
      ...(decisionEvidencePolicy
        ? { decisionEvidencePolicy: copyDecisionEvidencePolicy(decisionEvidencePolicy) }
        : {}),
      ...(approvalStages ? { approvalStages } : {}),
    })
  }
  return { branches }
}

function buildApprovalStages(): FlowApprovalStage[] | null | false {
  const configured = definitionApprovalStages.value
  if (!configured) return null
  if (configured.length < 2 || configured.length > 10) return false
  const codes = new Set<string>()
  const stages: FlowApprovalStage[] = []
  for (let index = 0; index < configured.length; index += 1) {
    const stage = configured[index]!
    const code = stage.code.trim()
    const name = stage.name.trim()
    const approvalMode = stage.approvalMode ?? 'SEQUENTIAL'
    const source = normalizedApproverSource(stage.approverSource)
    const approverIds = stage.approverIds.map(value => value.trim())
    if (
      !/^[a-z][a-z0-9_]{0,63}$/.test(code)
      || codes.has(code)
      || !name
      || name.length > 80
      || (index === 0 && source.kind === 'PREVIOUS_HANDLER')
      || (index > 0 && source.kind !== 'FIXED' && source.kind !== 'PREVIOUS_HANDLER')
      || !validDeadlinePolicy(stage.deadlinePolicy)
      || !validDecisionCommentPolicy(stage.decisionCommentPolicy)
      || !validDecisionEvidencePolicy(stage.decisionEvidencePolicy)
    ) return false
    if (source.kind === 'PREVIOUS_HANDLER') {
      if (approverIds.length || source.sourceId !== undefined || source.moduleCode !== undefined) {
        return false
      }
    } else if (!validApproverRoute(
      approverIds,
      approvalMode,
      source,
      stage.quorumRule,
    )) {
      return false
    }
    if (
      approvalMode === 'QUORUM'
      && source.kind === 'PREVIOUS_HANDLER'
      && (
        !stage.quorumRule
        || !Number.isInteger(stage.quorumRule.value)
        || stage.quorumRule.value < 1
        || (stage.quorumRule.type === 'PERCENTAGE' && stage.quorumRule.value > 100)
        || (stage.quorumRule.type === 'COUNT' && stage.quorumRule.value > 10)
      )
    ) return false
    if (approvalMode !== 'QUORUM' && stage.quorumRule) return false
    const previous = stages[index - 1]
    if (
      source.kind === 'PREVIOUS_HANDLER'
      && previous?.deadlinePolicy?.timeoutAction === 'AUTO_APPROVE'
    ) return false
    codes.add(code)
    stages.push({
      code,
      name,
      approverIds: source.kind === 'FIXED' ? approverIds : [],
      approvalMode,
      approverSource: source.kind === 'PREVIOUS_HANDLER'
        ? { kind: 'PREVIOUS_HANDLER' }
        : source,
      ...(approvalMode === 'QUORUM' ? { quorumRule: { ...stage.quorumRule! } } : {}),
      ...(stage.deadlinePolicy ? { deadlinePolicy: { ...stage.deadlinePolicy } } : {}),
      ...(stage.decisionCommentPolicy
        ? { decisionCommentPolicy: { ...stage.decisionCommentPolicy } }
        : {}),
      ...(stage.decisionEvidencePolicy
        ? { decisionEvidencePolicy: copyDecisionEvidencePolicy(stage.decisionEvidencePolicy) }
        : {}),
    })
  }
  return stages
}

async function saveDefinition(afterSave?: 'check' | 'simulate') {
  const name = definitionName.value.trim()
  const completionSteps = buildCompletionSteps()
  if (completionSteps === false) {
    error.value = '完成步骤及补偿需要满足唯一小写代码、并行组和外部任务、HTTPS Webhook 或精确子流程配置边界；COMPENSATE 要求每一步都有有效补偿'
    return
  }
  const approvalStages = buildApprovalStages()
  if (approvalStages === false) {
    error.value = '线性审批阶段需要 2 到 10 个唯一阶段；第一阶段可用当前来源，后续阶段仅支持固定成员或上一阶段实际处理人'
    return
  }
  const primaryStage = approvalStages?.[0]
  const approverIds = primaryStage?.approverIds
    ?? definitionApprovers.value.map((item) => item.trim())
  const approvalMode = primaryStage?.approvalMode ?? definitionApprovalMode.value
  const approverSource = primaryStage?.approverSource
    ?? normalizedApproverSource(definitionApproverSource.value)
  const quorumRule = primaryStage?.quorumRule ?? definitionQuorumRule.value ?? undefined
  const deadlinePolicy = primaryStage?.deadlinePolicy ?? definitionDeadlinePolicy.value ?? undefined
  const decisionCommentPolicy = primaryStage?.decisionCommentPolicy
    ?? definitionDecisionCommentPolicy.value
    ?? undefined
  const decisionEvidencePolicy = primaryStage?.decisionEvidencePolicy
    ?? definitionDecisionEvidencePolicy.value
    ?? undefined
  if (
    !name
    || !validApproverRoute(
      approverIds,
      approvalMode,
      approverSource,
      quorumRule,
    )
    || !validDeadlinePolicy(deadlinePolicy)
    || !validDecisionCommentPolicy(decisionCommentPolicy)
    || !validDecisionEvidencePolicy(decisionEvidencePolicy)
  ) {
    error.value = '请配置有效的固定成员、角色/部门 ID、部门负责人、请求人直属主管，或记录成员字段来源'
    return
  }
  const gateway = buildGateway()
  if (gateway === false) {
    error.value = '条件网关需要 1 到 5 个有序条件分支和最后一个默认分支；条件、名称与每条审批路线必须完整'
    return
  }
  const parallelGateway = buildParallelGateway()
  if (parallelGateway === false) {
    error.value = '并行网关需要 2 到 5 条唯一命名分支；每条审批路线与审批方式必须完整'
    return
  }
  const inclusiveGateway = buildInclusiveGateway()
  if (inclusiveGateway === false) {
    error.value = '包容网关需要 2 到 5 条唯一命名分支；条件、审批路线与审批方式必须完整'
    return
  }
  if ([gateway, parallelGateway, inclusiveGateway].filter(Boolean).length > 1) {
    error.value = '条件、并行与包容网关不能同时配置'
    return
  }
  if (approvalStages && (gateway || parallelGateway || inclusiveGateway)) {
    error.value = '显式线性审批阶段不能同时配置条件、并行或包容网关'
    return
  }
  const moduleCode = definitionTriggerModuleCode.value.trim()
  const priorityText = definitionTriggerPriority.value.trim()
  const priority = Number(priorityText)
  const periodicTrigger = definitionTriggerEnabled.value
    && definitionTriggerEvent.value === 'PERIODIC'
  const approverSources = configuredApproverSources(
    approverSource,
    gateway,
    parallelGateway,
    inclusiveGateway,
  )
  const requesterManagerSourceConfigured = approverSources.some(
    source => source.kind === 'REQUESTER_MANAGER',
  )
  const recordMemberSources = approverSources.filter(
    source => source.kind === 'RECORD_MEMBER_FIELD',
  )
  if (periodicTrigger && requesterManagerSourceConfigured) {
    error.value = '固定周期没有人工请求人，不能使用“请求人的直属主管”审批来源'
    return
  }
  if (periodicTrigger && recordMemberSources.length) {
    error.value = '固定周期没有记录上下文，不能使用“记录成员字段”审批来源'
    return
  }
  if (
    definitionTriggerEnabled.value
    && !periodicTrigger
    && recordMemberSources.some(source => source.moduleCode !== moduleCode)
  ) {
    error.value = '记录成员字段来源的模块必须与自动触发记录模块一致'
    return
  }
  const intervalMinutes = Number(definitionTriggerIntervalMinutes.value.trim())
  const startAtValue = definitionTriggerStartAt.value.trim()
  const startAtMillis = Date.parse(startAtValue)
  if (periodicTrigger && (
    !startAtValue
    || !Number.isFinite(startAtMillis)
    || !Number.isInteger(intervalMinutes)
    || intervalMinutes < 1
    || intervalMinutes > 525600
  )) {
    error.value = '固定周期需要 ISO-8601 开始时间，间隔必须是 1 到 525600 分钟的整数'
    return
  }
  if (definitionTriggerEnabled.value && !periodicTrigger && (
    !moduleCode
    || !/^[A-Za-z][A-Za-z0-9_]{0,63}$/.test(moduleCode)
    || !priorityText
    || !Number.isInteger(priority)
    || priority < -1000
    || priority > 1000
  )) {
    error.value = '自动触发需要合法模块代码，优先级必须是 -1000 到 1000 的整数'
    return
  }
  const conditions = definitionTriggerEnabled.value && !periodicTrigger
    ? buildTriggerConditions()
    : []
  if (conditions === null) {
    error.value = '自动触发条件最多 10 条；字段代码、操作符和非 null JSON 值必须合法'
    return
  }
  const statusFieldCode = definitionStatusFieldCode.value.trim()
  const statusApprovedValue = definitionStatusApprovedValue.value.trim()
  const statusRejectedValue = definitionStatusRejectedValue.value.trim()
  const statusWithdrawnValue = definitionStatusWithdrawnValue.value.trim()
  const statusTerminatedValue = definitionStatusTerminatedValue.value.trim()
  const canonicalOptionId = /^[1-9][0-9]{0,18}$/
  if (definitionStatusMappingEnabled.value && (
    !/^[A-Za-z][A-Za-z0-9_]{0,63}$/.test(statusFieldCode)
    || !canonicalOptionId.test(statusApprovedValue)
    || !canonicalOptionId.test(statusRejectedValue)
    || !canonicalOptionId.test(statusWithdrawnValue)
    || !canonicalOptionId.test(statusTerminatedValue)
  )) {
    error.value = '终态 STATUS 映射需要合法字段代码，四个选项 ID 均须为最多 19 位的规范正整数'
    return
  }
  if (
    definitionTriggerEnabled.value
    && definitionTriggerEvent.value !== 'RECORD_ACTIVATED'
    && definitionStatusMappingEnabled.value
  ) {
    error.value = '终态 STATUS 映射只能与 RECORD_ACTIVATED 自动触发同时配置'
    return
  }
  const input = {
    name,
    approverIds: approverSource.kind === 'FIXED' ? approverIds : [],
    approvalMode,
    ...(approverSource.kind === 'FIXED' ? {} : { approverSource }),
    ...(approvalMode === 'QUORUM'
      ? { quorumRule: quorumRule! }
      : {}),
    ...(deadlinePolicy
      ? { deadlinePolicy: { ...deadlinePolicy } }
      : {}),
    ...(decisionCommentPolicy
      ? { decisionCommentPolicy: { ...decisionCommentPolicy } }
      : {}),
    ...(decisionEvidencePolicy
      ? { decisionEvidencePolicy: copyDecisionEvidencePolicy(decisionEvidencePolicy) }
      : {}),
    ...(approvalStages ? { approvalStages } : {}),
    ...(definitionTriggerEnabled.value
      ? {
          triggerBinding: periodicTrigger
            ? {
                moduleCode: null,
                event: 'PERIODIC' as const,
                priority: 0,
                exclusive: true,
                conditions: [],
                startAt: new Date(startAtMillis).toISOString(),
                intervalMinutes,
              }
            : {
                moduleCode,
                event: definitionTriggerEvent.value,
                priority,
                exclusive: definitionTriggerExclusive.value,
                conditions,
              },
        }
      : {}),
    ...(definitionStatusMappingEnabled.value
      ? {
          recordStatusMapping: {
            fieldCode: statusFieldCode,
            approvedValue: statusApprovedValue,
            rejectedValue: statusRejectedValue,
            withdrawnValue: statusWithdrawnValue,
            terminatedValue: statusTerminatedValue,
          },
        }
      : {}),
    ...(gateway ? { gateway } : {}),
    ...(parallelGateway ? { parallelGateway } : {}),
    ...(inclusiveGateway ? { inclusiveGateway } : {}),
    ...(definitionCompletionFailurePolicy.value === 'COMPENSATE'
      ? { completionFailurePolicy: 'COMPENSATE' as const }
      : {}),
    ...(completionSteps.length ? { completionSteps } : {}),
  }
  mutation.value = editingDefinition.value ? `revise:${editingDefinition.value.definitionId}` : 'create'
  error.value = ''
  let savedDefinition: FlowDefinitionDraft | null = null
  try {
    if (editingDefinition.value) {
      savedDefinition = await flowApi.reviseDefinition(
        systemId.value,
        editingDefinition.value.definitionId,
        input,
      )
    } else {
      savedDefinition = await flowApi.createDefinition(systemId.value, input)
    }
    definitionCompletionSteps.value = copyCompletionSteps(savedDefinition.completionSteps)
    definitionOpen.value = false
    await loadDefinitions()
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
  if (!savedDefinition || !afterSave) return
  preparePreflight(savedDefinition)
  if (afterSave === 'check') {
    await runDraftCheck()
  } else {
    await runDraftSimulation()
  }
}

function saveDefinitionAndCheck() {
  void saveDefinition('check')
}

function saveDefinitionAndSimulate() {
  void saveDefinition('simulate')
}

async function publishDefinition(definition: FlowDefinitionDraft) {
  mutation.value = `publish:${definition.definitionId}`
  error.value = ''
  try {
    const published = await flowApi.publishDefinition(systemId.value, definition.definitionId)
    const next = new Map(publishedVersions.value)
    next.set(definition.definitionId, published)
    publishedVersions.value = next
    await loadDefinitions()
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

function openStart(definition?: FlowDefinitionDraft) {
  startDefinitionId.value = definition?.definitionId ?? ''
  startVersion.value = ''
  startBusinessKey.value = ''
  const recordMemberModules = new Set(
    definition
      ? recordMemberApproverSources(definition)
          .map(source => source.moduleCode?.trim() ?? '')
          .filter(Boolean)
      : [],
  )
  startBindingModuleCode.value = recordMemberModules.size === 1
    ? [...recordMemberModules][0]!
    : ''
  startBindingRecordId.value = ''
  startRouteValues.value = '{}'
  startError.value = ''
  startRefreshWarning.value = ''
  startReceipt.value = null
  startIdempotencyKey.value = ''
  startSubmittedFingerprint.value = ''
  startOpen.value = true
}

function startPayload(): { definitionId: string, input: StartFlowInstanceInput } | null {
  const definitionId = startDefinitionId.value.trim()
  const businessKey = startBusinessKey.value.trim()
  if (!definitionId || !businessKey) {
    startError.value = '请选择流程并填写业务标识'
    return null
  }
  const moduleCode = startBindingModuleCode.value.trim()
  const recordId = startBindingRecordId.value.trim()
  if (Boolean(moduleCode) !== Boolean(recordId)) {
    startError.value = '模块代码和记录 ID 必须同时填写或同时留空'
    return null
  }
  const selectedDefinition = definitions.value.find(
    definition => definition.definitionId === definitionId,
  )
  const recordMemberSources = selectedDefinition
    ? recordMemberApproverSources(selectedDefinition)
    : []
  if (recordMemberSources.length && (!moduleCode || !recordId)) {
    startError.value = '记录成员字段审批来源需要绑定记录模块和记录 ID'
    return null
  }
  if (
    recordMemberSources.length
    && !recordMemberSources.some(source => source.moduleCode === moduleCode)
  ) {
    startError.value = '绑定记录模块必须与记录成员字段审批来源的模块一致'
    return null
  }
  const versionText = startVersion.value.trim()
  const definitionVersion = versionText ? Number(versionText) : undefined
  if (definitionVersion !== undefined && (!Number.isInteger(definitionVersion) || definitionVersion < 1)) {
    startError.value = '流程版本必须是大于 0 的整数'
    return null
  }
  let values: Record<string, unknown>
  try {
    const parsed = JSON.parse(startRouteValues.value) as unknown
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error('invalid object')
    }
    values = parsed as Record<string, unknown>
    if (Object.keys(values).length > 100) throw new Error('too many fields')
  } catch {
    startError.value = '路由字段值必须是最多 100 个字段的 JSON 对象'
    return null
  }
  return {
    definitionId,
    input: {
      businessKey,
      ...(definitionVersion === undefined ? {} : { definitionVersion }),
      ...(moduleCode && recordId ? { recordBinding: { moduleCode, recordId } } : {}),
      ...(Object.keys(values).length ? { values } : {}),
    },
  }
}

async function startInstance() {
  const payload = startPayload()
  if (!payload) return
  const fingerprint = JSON.stringify(payload)
  if (
    !startIdempotencyKey.value
    || (
      startSubmittedFingerprint.value
      && startSubmittedFingerprint.value !== fingerprint
    )
  ) {
    startIdempotencyKey.value = crypto.randomUUID()
  }
  startSubmittedFingerprint.value = fingerprint
  mutation.value = 'start'
  startError.value = ''
  startRefreshWarning.value = ''
  try {
    const started = await flowApi.startInstance(
      systemId.value,
      payload.definitionId,
      payload.input,
      startIdempotencyKey.value,
    )
    startReceipt.value = started
    if (canRead.value) {
      instancePage.value = 1
      taskPage.value = 1
      try {
        const [, , current] = await Promise.all([
          loadInstances(),
          loadApprovalTasks(),
          flowApi.instance(systemId.value, started.instanceId),
        ])
        instances.value = instances.value.some((instance) => instance.instanceId === current.instanceId)
          ? instances.value.map((instance) => instance.instanceId === current.instanceId ? current : instance)
          : [current, ...instances.value]
        instanceTotal.value = Math.max(instanceTotal.value, instances.value.length)
      } catch (cause) {
        startRefreshWarning.value = `审批已发起，但列表刷新失败：${message(cause)}`
      }
    }
  } catch (cause) {
    startError.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

function confirmStartModal() {
  if (startReceipt.value) {
    startOpen.value = false
    return
  }
  void startInstance()
}

function startAnother() {
  startVersion.value = ''
  startBusinessKey.value = ''
  startBindingModuleCode.value = ''
  startBindingRecordId.value = ''
  startError.value = ''
  startRefreshWarning.value = ''
  startReceipt.value = null
  startIdempotencyKey.value = ''
  startSubmittedFingerprint.value = ''
}

function queryText(value: unknown) {
  return Array.isArray(value) ? String(value[0] ?? '') : String(value ?? '')
}

function applyStartDeepLink() {
  if (!canStart.value || queryText(route.query.flowStart) !== '1') return
  const signature = JSON.stringify({
    systemId: systemId.value,
    tenantId: tenantId.value,
    flowStart: route.query.flowStart,
    definitionId: route.query.definitionId,
    definitionVersion: route.query.definitionVersion,
    businessKey: route.query.businessKey,
    moduleCode: route.query.moduleCode,
    recordId: route.query.recordId,
  })
  if (signature === appliedStartDeepLink) return
  appliedStartDeepLink = signature
  openStart()
  startDefinitionId.value = queryText(route.query.definitionId)
  startVersion.value = queryText(route.query.definitionVersion)
  startBusinessKey.value = queryText(route.query.businessKey)
  startBindingModuleCode.value = queryText(route.query.moduleCode)
  startBindingRecordId.value = queryText(route.query.recordId)
}

function applyAnalyticsDrillQuery() {
  const taskStatusQuery = queryText(route.query.taskStatus)
  if (taskStatusQuery === 'PENDING' || taskStatusQuery === 'COMPLETED'
    || taskStatusQuery === 'ALL') {
    taskStatus.value = taskStatusQuery
  }
  const instanceStatusQuery = queryText(route.query.status)
  instanceStatus.value = ['PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'TERMINATED']
    .includes(instanceStatusQuery)
    ? instanceStatusQuery as FlowInstanceStatus
    : ''
  const fromQuery = queryText(route.query.from)
  const toQuery = queryText(route.query.to)
  instanceFrom.value = /^\d{4}-\d{2}-\d{2}$/.test(fromQuery) ? fromQuery : ''
  instanceTo.value = /^\d{4}-\d{2}-\d{2}$/.test(toQuery) ? toQuery : ''
}

function openCreateDelegation() {
  const now = new Date()
  const tomorrow = new Date(now.getTime() + 24 * 60 * 60 * 1000)
  delegationDelegatorMemberId.value = session.context?.memberId ?? ''
  delegationDelegateMemberId.value = ''
  delegationStartsAt.value = localDateTime(now)
  delegationEndsAt.value = localDateTime(tomorrow)
  delegationDefinitionId.value = ''
  delegationError.value = ''
  delegationOpen.value = true
}

async function createDelegation() {
  const delegatorMemberId = delegationDelegatorMemberId.value.trim()
  const delegateMemberId = delegationDelegateMemberId.value.trim()
  const startsAtMillis = Date.parse(delegationStartsAt.value)
  const endsAtMillis = Date.parse(delegationEndsAt.value)
  const definitionId = delegationDefinitionId.value.trim()
  if (
    !/^[1-9][0-9]{0,18}$/.test(delegateMemberId)
    || !Number.isFinite(startsAtMillis)
    || !Number.isFinite(endsAtMillis)
    || startsAtMillis >= endsAtMillis
    || endsAtMillis - startsAtMillis > 180 * 24 * 60 * 60 * 1000
    || (definitionId && !/^[1-9][0-9]{0,18}$/.test(definitionId))
    || (
      canManageDelegations.value
      && !/^[1-9][0-9]{0,18}$/.test(delegatorMemberId)
    )
    || delegateMemberId === (
      canManageDelegations.value
        ? delegatorMemberId
        : session.context?.memberId
    )
  ) {
    delegationError.value = '请填写不同的有效成员，并配置不超过 180 天的有效时间'
    return
  }
  mutation.value = 'delegation:create'
  delegationError.value = ''
  try {
    await flowApi.createDelegation(systemId.value, {
      ...(canManageDelegations.value ? { delegatorMemberId } : {}),
      delegateMemberId,
      startsAt: new Date(startsAtMillis).toISOString(),
      endsAt: new Date(endsAtMillis).toISOString(),
      ...(definitionId ? { definitionId } : {}),
    })
    delegationOpen.value = false
    delegationPage.value = 1
    await Promise.all([loadDelegations(), loadApprovalTasks()])
  } catch (cause) {
    delegationError.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function revokeDelegation(rule: FlowDelegationRule) {
  mutation.value = `delegation:revoke:${rule.delegationRuleId}`
  delegationError.value = ''
  try {
    await flowApi.revokeDelegation(systemId.value, rule.delegationRuleId)
    await Promise.all([loadDelegations(), loadApprovalTasks()])
  } catch (cause) {
    delegationError.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function loadActiveDecisionTemplates() {
  try {
    const result = await flowApi.listDecisionCommentTemplates(
      systemId.value,
      'ACTIVE',
      1,
      100,
    )
    decisionTemplates.value = result.items
  } catch (cause) {
    decisionTemplates.value = []
    decisionError.value = message(cause)
  }
}

async function loadTemplateManager() {
  templateLoading.value = true
  templateError.value = ''
  try {
    const result = await flowApi.listDecisionCommentTemplates(
      systemId.value,
      undefined,
      templatePage.value,
      20,
    )
    templateItems.value = result.items
    templateTotal.value = result.total
  } catch (cause) {
    templateItems.value = []
    templateTotal.value = 0
    templateError.value = message(cause)
  } finally {
    templateLoading.value = false
  }
}

function openTemplateManager() {
  templatePage.value = 1
  templateEditingId.value = ''
  templateName.value = ''
  templateBody.value = ''
  templateManagerOpen.value = true
  void loadTemplateManager()
}

function editDecisionTemplate(template: FlowDecisionCommentTemplate) {
  templateEditingId.value = template.templateId
  templateName.value = template.name
  templateBody.value = template.body
}

function newDecisionTemplate() {
  templateEditingId.value = ''
  templateName.value = ''
  templateBody.value = ''
}

async function saveDecisionTemplate() {
  const input: SaveFlowDecisionCommentTemplateInput = {
    name: templateName.value.trim(),
    body: templateBody.value.trim(),
  }
  if (!input.name || input.name.length > 80 || !input.body || input.body.length > 1000) {
    templateError.value = '模板名称和 1 到 1000 字的正文必须完整'
    return
  }
  mutation.value = 'decision-template:save'
  templateError.value = ''
  try {
    if (templateEditingId.value) {
      await flowApi.updateDecisionCommentTemplate(
        systemId.value,
        templateEditingId.value,
        input,
      )
    } else {
      await flowApi.createDecisionCommentTemplate(systemId.value, input)
    }
    newDecisionTemplate()
    await loadTemplateManager()
  } catch (cause) {
    templateError.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function setDecisionTemplateActive(
  template: FlowDecisionCommentTemplate,
  active: boolean,
) {
  mutation.value = `decision-template:${template.templateId}`
  templateError.value = ''
  try {
    if (active) {
      await flowApi.activateDecisionCommentTemplate(systemId.value, template.templateId)
    } else {
      await flowApi.deactivateDecisionCommentTemplate(systemId.value, template.templateId)
    }
    await loadTemplateManager()
  } catch (cause) {
    templateError.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

function addDecisionAttachment() {
  if (decisionAttachmentFileIds.value.length >= 5) return
  decisionAttachmentFileIds.value = [...decisionAttachmentFileIds.value, '']
}

function removeDecisionAttachment(index: number) {
  decisionAttachmentFileIds.value = decisionAttachmentFileIds.value.filter(
    (_, current) => current !== index,
  )
}

function updateDecisionAttachment(index: number, value: string) {
  const fileIds = [...decisionAttachmentFileIds.value]
  const previous = fileIds[index]
  fileIds[index] = value
  decisionAttachmentFileIds.value = fileIds
  if (previous) decisionAttachmentAssets.value.delete(previous)
}

async function resolveDecisionAsset(fileId: string, signature = false) {
  const canonical = fileId.trim()
  if (!/^[1-9][0-9]{0,18}$/.test(canonical)) {
    decisionError.value = '文件 ID 必须是规范正整数'
    return null
  }
  decisionEvidenceLoading.value = signature ? 'signature' : canonical
  decisionError.value = ''
  try {
    const asset = await fileApi.get(systemId.value, canonical)
    if (signature) {
      decisionSignatureAsset.value = asset
    } else {
      decisionAttachmentAssets.value.set(canonical, asset)
    }
    return asset
  } catch (cause) {
    decisionError.value = message(cause)
    return null
  } finally {
    decisionEvidenceLoading.value = ''
  }
}

function fileMimeFamily(
  mediaType: string,
): FlowDecisionEvidencePolicy['allowedMimeFamilies'][number] {
  const normalized = mediaType.toLowerCase()
  if (normalized.startsWith('image/')) return 'IMAGE'
  if (normalized === 'application/pdf') return 'PDF'
  if (
    normalized.startsWith('text/')
    || normalized.includes('word')
    || normalized.includes('document')
    || normalized.includes('spreadsheet')
    || normalized.includes('presentation')
  ) return 'DOCUMENT'
  if (
    normalized.includes('zip')
    || normalized.includes('rar')
    || normalized.includes('tar')
    || normalized.includes('7z')
  ) return 'ARCHIVE'
  return 'OTHER'
}

function openDecision(instance: FlowInstance | null, action: 'approve' | 'reject') {
  decisionAction.value = action
  decisionInstanceId.value = instance?.instanceId ?? ''
  const memberId = session.context?.memberId
  decisionBranchCode.value = instance?.parallelBranches?.find((branch) => (
    branch.status === 'PENDING'
    && (
      branch.activeApproverIds.includes(memberId ?? '')
      || representedMemberIdsForBranch(instance, branch).length > 0
    )
  ))?.code ?? ''
  decisionComment.value = ''
  decisionError.value = ''
  decisionAttachmentFileIds.value = []
  decisionAttachmentAssets.value = new Map()
  decisionSignatureKind.value = 'NONE'
  decisionTypedSignature.value = ''
  decisionSignatureFileId.value = ''
  decisionSignatureAsset.value = null
  decisionSelectedTemplateId.value = ''
  decisionTemplates.value = []
  const branch = decisionBranchCode.value
    ? instance?.parallelBranches?.find(item => item.code === decisionBranchCode.value)
    : null
  const direct = branch
    ? branch.activeApproverIds.includes(memberId ?? '')
    : Boolean(instance && directDecisionEligible(instance))
  decisionRepresentedMemberId.value = direct
    ? ''
    : (
        branch
          ? representedMemberIdsForBranch(instance, branch)
          : representedMemberIds(instance)
      )[0] ?? ''
  decisionAttachmentFileIds.value = Array.from(
    { length: decisionEvidencePolicy.value?.minimumAttachments ?? 0 },
    () => '',
  )
  decisionOpen.value = true
  void loadActiveDecisionTemplates()
}

async function decide() {
  const instanceId = decisionInstanceId.value.trim()
  const branchCode = decisionBranchCode.value.trim()
  const comment = decisionComment.value.trim()
  const selectedTemplate = decisionTemplates.value.find(
    item => item.templateId === decisionSelectedTemplateId.value,
  )
  const effectiveComment = selectedTemplate?.body.trim() ?? comment
  const representedMemberId = decisionRepresentedMemberId.value.trim()
  const commentLength = Array.from(effectiveComment).length
  const policy = decisionCommentPolicy.value
  const evidencePolicy = decisionEvidencePolicy.value
  const required = decisionAction.value === 'approve'
    ? policy.approveRequired
    : policy.rejectRequired
  decisionComment.value = comment
  decisionError.value = ''
  if (
    !instanceId
    || (decisionBranches.value.length > 0 && !branchCode)
    || (!decisionDirectlyEligible.value && !representedMemberId)
    || (
      representedMemberId
      && !decisionRepresentationOptions.value.includes(representedMemberId)
    )
  ) {
    decisionError.value = '请选择要处理的审批实例和分支'
    return
  }
  if (selectedTemplate && comment) {
    decisionError.value = '审批意见和意见模板不能同时提交'
    return
  }
  if (commentLength > 500) {
    decisionError.value = '审批意见最多 500 个字符'
    return
  }
  if (required && commentLength < policy.minimumLength) {
    decisionError.value = `审批意见为必填项，至少输入 ${policy.minimumLength} 个字符`
    return
  }
  const attachmentIds = decisionAttachmentFileIds.value
    .map(value => value.trim())
    .filter(Boolean)
  if (
    attachmentIds.some(value => !/^[1-9][0-9]{0,18}$/.test(value))
    || new Set(attachmentIds).size !== attachmentIds.length
  ) {
    decisionError.value = '附件文件 ID 必须是互不重复的规范正整数'
    return
  }
  const minimumAttachments = evidencePolicy?.minimumAttachments ?? 0
  const maximumAttachments = evidencePolicy?.maximumAttachments ?? 0
  if (
    attachmentIds.length < minimumAttachments
    || attachmentIds.length > maximumAttachments
  ) {
    decisionError.value = `附件数量必须在 ${minimumAttachments} 到 ${maximumAttachments} 个之间`
    return
  }
  const signatureRequired = evidencePolicy?.signatureMode === 'REQUIRED'
  if (signatureRequired && decisionSignatureKind.value === 'NONE') {
    decisionError.value = '当前审批阶段必须提供签名'
    return
  }
  if (
    (!evidencePolicy || evidencePolicy.signatureMode === 'NONE')
    && decisionSignatureKind.value !== 'NONE'
  ) {
    decisionError.value = '当前审批阶段不允许提供签名'
    return
  }
  const typedSignature = decisionTypedSignature.value.trim()
  const signatureFileId = decisionSignatureFileId.value.trim()
  if (
    decisionSignatureKind.value === 'TYPED'
    && (Array.from(typedSignature).length < 1 || Array.from(typedSignature).length > 120)
  ) {
    decisionError.value = '手写签名文本长度必须为 1 到 120 个字符'
    return
  }
  if (
    decisionSignatureKind.value === 'FILE'
    && !/^[1-9][0-9]{0,18}$/.test(signatureFileId)
  ) {
    decisionError.value = '签名文件 ID 必须是规范正整数'
    return
  }
  const resolvedAttachments = await Promise.all(
    attachmentIds.map(fileId => resolveDecisionAsset(fileId)),
  )
  if (resolvedAttachments.some(asset => !asset)) return
  const allowedMimeFamilies = evidencePolicy?.allowedMimeFamilies ?? []
  if (
    allowedMimeFamilies.length
    && resolvedAttachments.some(asset =>
      !allowedMimeFamilies.includes(fileMimeFamily(asset!.mediaType)))
  ) {
    decisionError.value = '存在不符合当前 MIME 家族规则的附件'
    return
  }
  if (
    decisionSignatureKind.value === 'FILE'
    && !await resolveDecisionAsset(signatureFileId, true)
  ) return
  const evidence: FlowDecisionEvidenceInput = {
    ...(attachmentIds.length
      ? { attachmentFileIds: attachmentIds.map(Number) }
      : {}),
    ...(decisionSignatureKind.value === 'FILE'
      ? { signatureFileId: Number(signatureFileId) }
      : {}),
    ...(decisionSignatureKind.value === 'TYPED' ? { typedSignature } : {}),
    ...(selectedTemplate
      ? { commentTemplateId: Number(selectedTemplate.templateId) }
      : {}),
  }
  const hasEvidence = Object.keys(evidence).length > 0
  mutation.value = `decide:${instanceId}`
  error.value = ''
  try {
    if (decisionAction.value === 'approve') {
      if (branchCode) {
        if (representedMemberId) {
          if (hasEvidence) {
            await flowApi.approveBranch(
              systemId.value, instanceId, branchCode, comment, representedMemberId, evidence,
            )
          } else {
            await flowApi.approveBranch(
              systemId.value, instanceId, branchCode, comment, representedMemberId,
            )
          }
        } else if (hasEvidence) {
          await flowApi.approveBranch(
            systemId.value,
            instanceId,
            branchCode,
            comment,
            undefined,
            evidence,
          )
        } else {
          await flowApi.approveBranch(systemId.value, instanceId, branchCode, comment)
        }
      } else {
        if (representedMemberId) {
          if (hasEvidence) {
            await flowApi.approve(
              systemId.value, instanceId, comment, representedMemberId, evidence,
            )
          } else {
            await flowApi.approve(systemId.value, instanceId, comment, representedMemberId)
          }
        } else if (hasEvidence) {
          await flowApi.approve(
            systemId.value,
            instanceId,
            comment,
            undefined,
            evidence,
          )
        } else {
          await flowApi.approve(systemId.value, instanceId, comment)
        }
      }
    } else {
      if (branchCode) {
        if (representedMemberId) {
          if (hasEvidence) {
            await flowApi.rejectBranch(
              systemId.value, instanceId, branchCode, comment, representedMemberId, evidence,
            )
          } else {
            await flowApi.rejectBranch(
              systemId.value, instanceId, branchCode, comment, representedMemberId,
            )
          }
        } else if (hasEvidence) {
          await flowApi.rejectBranch(
            systemId.value,
            instanceId,
            branchCode,
            comment,
            undefined,
            evidence,
          )
        } else {
          await flowApi.rejectBranch(systemId.value, instanceId, branchCode, comment)
        }
      } else {
        if (representedMemberId) {
          if (hasEvidence) {
            await flowApi.reject(
              systemId.value, instanceId, comment, representedMemberId, evidence,
            )
          } else {
            await flowApi.reject(systemId.value, instanceId, comment, representedMemberId)
          }
        } else if (hasEvidence) {
          await flowApi.reject(
            systemId.value,
            instanceId,
            comment,
            undefined,
            evidence,
          )
        } else {
          await flowApi.reject(systemId.value, instanceId, comment)
        }
      }
    }
    decisionOpen.value = false
    if (canRead.value) {
      await Promise.all([loadApprovalTasks(), loadInstances()])
    }
    if (detailOpen.value && selectedInstance.value?.instanceId === instanceId) {
      await showDetail({ ...selectedInstance.value })
    }
  } catch (cause) {
    decisionError.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

function openWithdraw(instance: FlowInstance) {
  withdrawInstanceId.value = instance.instanceId
  withdrawReason.value = ''
  withdrawError.value = ''
  withdrawOpen.value = true
}

async function withdraw() {
  const instanceId = withdrawInstanceId.value
  const reason = withdrawReason.value.trim()
  withdrawReason.value = reason
  if (!instanceId || !reason || reason.length > 500) {
    withdrawError.value = '撤回原因长度必须为 1–500 个字符。'
    return
  }
  mutation.value = `withdraw:${instanceId}`
  withdrawError.value = ''
  try {
    await flowApi.withdraw(systemId.value, instanceId, { reason })
  } catch (cause) {
    withdrawError.value = message(cause)
    mutation.value = ''
    return
  }

  mutation.value = ''
  withdrawOpen.value = false
  withdrawReason.value = ''
  try {
    const detailProjection = Promise.all([
      flowApi.instance(systemId.value, instanceId),
      flowApi.history(systemId.value, instanceId),
    ])
    const [, , [current, history]] = await Promise.all([
      loadInstances(),
      loadApprovalTasks(),
      detailProjection,
    ])
    if (selectedInstance.value?.instanceId === instanceId) {
      selectedInstance.value = current
      selectedHistory.value = history
    }
  } catch (cause) {
    error.value = message(cause)
  }
}

function openTerminate(instance: FlowInstance) {
  terminateInstanceId.value = instance.instanceId
  terminateReason.value = ''
  terminateError.value = ''
  terminateOpen.value = true
}

async function terminate() {
  const instanceId = terminateInstanceId.value
  const reason = terminateReason.value.trim()
  terminateReason.value = reason
  if (!instanceId || !reason || reason.length > 500) {
    terminateError.value = '终止原因长度必须为 1–500 个字符。'
    return
  }
  mutation.value = `terminate:${instanceId}`
  terminateError.value = ''
  try {
    await flowApi.terminate(systemId.value, instanceId, { reason })
  } catch (cause) {
    terminateError.value = message(cause)
    mutation.value = ''
    return
  }

  mutation.value = ''
  terminateOpen.value = false
  terminateReason.value = ''
  try {
    const detailProjection = Promise.all([
      flowApi.instance(systemId.value, instanceId),
      flowApi.history(systemId.value, instanceId),
    ])
    const [, , [current, history]] = await Promise.all([
      loadInstances(),
      loadApprovalTasks(),
      detailProjection,
    ])
    if (selectedInstance.value?.instanceId === instanceId) {
      selectedInstance.value = current
      selectedHistory.value = history
    }
  } catch (cause) {
    error.value = message(cause)
  }
}

function openUrge(instance: FlowInstance) {
  urgeInstanceId.value = instance.instanceId
  urgeMessage.value = ''
  urgeError.value = ''
  urgeOpen.value = true
}

async function loadUrges(instanceId: string, page = urgePage.value) {
  const ownsDetail = selectedInstance.value?.instanceId === instanceId
  if (ownsDetail) {
    urgeTimelineLoading.value = true
    urgeTimelineError.value = ''
  }
  try {
    const result = await flowApi.listUrges(systemId.value, instanceId, page, interactionPageSize)
    if (selectedInstance.value?.instanceId !== instanceId) return
    selectedUrges.value = result.items
    urgePage.value = result.page
    urgeTotal.value = result.total
  } catch (cause) {
    if (selectedInstance.value?.instanceId === instanceId) {
      urgeTimelineError.value = message(cause)
    }
  } finally {
    if (selectedInstance.value?.instanceId === instanceId) {
      urgeTimelineLoading.value = false
    }
  }
}

async function urge() {
  const instanceId = urgeInstanceId.value
  const optionalMessage = urgeMessage.value.trim()
  if (!instanceId || [...optionalMessage].length > 500) {
    urgeError.value = '催办消息最多为 500 个字符。'
    return
  }
  mutation.value = `urge:${instanceId}`
  urgeError.value = ''
  try {
    await flowApi.urge(systemId.value, instanceId, { message: optionalMessage })
  } catch (cause) {
    urgeError.value = message(cause)
    mutation.value = ''
    return
  }

  mutation.value = ''
  urgeOpen.value = false
  urgeMessage.value = ''
  await loadUrges(
    instanceId,
    selectedInstance.value?.instanceId === instanceId ? urgePage.value : 1,
  )
}

function openTransfer(instance: FlowInstance) {
  transferInstanceId.value = instance.instanceId
  transferTargetMemberId.value = ''
  transferReason.value = ''
  transferError.value = ''
  transferOpen.value = true
}

async function refreshAssignmentProjections(instanceId: string) {
  const detailProjection = Promise.all([
    flowApi.instance(systemId.value, instanceId),
    flowApi.history(systemId.value, instanceId),
  ])
  const [, , [current, history]] = await Promise.all([
    loadInstances(),
    loadApprovalTasks(),
    detailProjection,
  ])
  if (selectedInstance.value?.instanceId === instanceId) {
    selectedInstance.value = current
    selectedHistory.value = history
  }
}

async function transfer() {
  const instanceId = transferInstanceId.value
  const targetMemberId = transferTargetMemberId.value.trim()
  const reason = transferReason.value.trim()
  if (!instanceId || !targetMemberId || !reason || [...reason].length > 500) {
    transferError.value = '请选择目标成员，并填写 1–500 个字符的转交原因。'
    return
  }
  mutation.value = `transfer:${instanceId}`
  transferError.value = ''
  try {
    await flowApi.transfer(systemId.value, instanceId, { targetMemberId, reason })
  } catch (cause) {
    transferError.value = message(cause)
    mutation.value = ''
    return
  }

  mutation.value = ''
  transferOpen.value = false
  transferTargetMemberId.value = ''
  transferReason.value = ''
  try {
    await refreshAssignmentProjections(instanceId)
  } catch (cause) {
    error.value = message(cause)
  }
}

function openAddSign(instance: FlowInstance) {
  addSignInstanceId.value = instance.instanceId
  addSignTargetMemberId.value = ''
  addSignPosition.value = 'BEFORE'
  addSignReason.value = ''
  addSignError.value = ''
  addSignOpen.value = true
}

function selectAddSignPosition(value: string) {
  if (value === 'BEFORE' || value === 'AFTER') addSignPosition.value = value
}

async function addSign() {
  const instanceId = addSignInstanceId.value
  const targetMemberId = addSignTargetMemberId.value.trim()
  const reason = addSignReason.value.trim()
  if (!instanceId || !targetMemberId || !reason || [...reason].length > 500) {
    addSignError.value = '请选择目标成员，并填写 1–500 个字符的加签原因。'
    return
  }
  mutation.value = `add-sign:${instanceId}`
  addSignError.value = ''
  try {
    await flowApi.addSign(systemId.value, instanceId, {
      targetMemberId,
      position: addSignPosition.value,
      reason,
    })
  } catch (cause) {
    addSignError.value = message(cause)
    mutation.value = ''
    return
  }

  mutation.value = ''
  addSignOpen.value = false
  addSignTargetMemberId.value = ''
  addSignPosition.value = 'BEFORE'
  addSignReason.value = ''
  try {
    await refreshAssignmentProjections(instanceId)
  } catch (cause) {
    error.value = message(cause)
  }
}

function futureApproverSteps(instance: FlowInstance) {
  return (instance.approverIds ?? [])
    .map((memberId, stepIndex) => ({ memberId, stepIndex }))
    .filter(({ stepIndex }) => stepIndex > instance.currentStepIndex)
}

function openReduceSign(instance: FlowInstance) {
  reduceSignInstanceId.value = instance.instanceId
  reduceSignCandidates.value = futureApproverSteps(instance)
  reduceSignTargetStepIndex.value = reduceSignCandidates.value[0]?.stepIndex ?? null
  reduceSignReason.value = ''
  reduceSignError.value = ''
  reduceSignOpen.value = true
}

function selectReduceSignTarget(value: string) {
  const stepIndex = Number(value)
  if (Number.isInteger(stepIndex)
    && reduceSignCandidates.value.some((candidate) => candidate.stepIndex === stepIndex)) {
    reduceSignTargetStepIndex.value = stepIndex
  }
}

async function reduceSign() {
  const instanceId = reduceSignInstanceId.value
  const targetStepIndex = reduceSignTargetStepIndex.value
  const reason = reduceSignReason.value.trim()
  const validTarget = targetStepIndex !== null
    && reduceSignCandidates.value.some((candidate) => candidate.stepIndex === targetStepIndex)
  if (!instanceId || !validTarget || !reason || [...reason].length > 500) {
    reduceSignError.value = '请选择一个未来审批步骤，并填写 1–500 个字符的减签原因。'
    return
  }
  mutation.value = `reduce-sign:${instanceId}`
  reduceSignError.value = ''
  try {
    await flowApi.reduceSign(systemId.value, instanceId, { targetStepIndex, reason })
  } catch (cause) {
    reduceSignError.value = message(cause)
    mutation.value = ''
    return
  }

  mutation.value = ''
  reduceSignOpen.value = false
  reduceSignCandidates.value = []
  reduceSignTargetStepIndex.value = null
  reduceSignReason.value = ''
  try {
    await refreshAssignmentProjections(instanceId)
  } catch (cause) {
    error.value = message(cause)
  }
}

function openReturn(instance: FlowInstance) {
  returnInstanceId.value = instance.instanceId
  returnReason.value = ''
  returnError.value = ''
  returnOpen.value = true
}

function openCancelClaim(instance: FlowInstance) {
  cancelClaimInstanceId.value = instance.instanceId
  cancelClaimReason.value = ''
  cancelClaimError.value = ''
  cancelClaimOpen.value = true
}

function openClaim(instance: FlowInstance) {
  claimInstanceId.value = instance.instanceId
  claimComment.value = ''
  claimError.value = ''
  claimOpen.value = true
}

async function refreshClaimProjections(instanceId: string) {
  const detailProjection = canRead.value
    ? Promise.all([
        flowApi.instance(systemId.value, instanceId),
        flowApi.history(systemId.value, instanceId),
      ])
    : Promise.resolve(null)
  const [, , , currentDetail] = await Promise.all([
    loadInstances(),
    loadApprovalTasks(),
    loadClaimableTasks(),
    detailProjection,
  ])
  if (currentDetail && selectedInstance.value?.instanceId === instanceId) {
    selectedInstance.value = currentDetail[0]
    selectedHistory.value = currentDetail[1]
  }
}

async function returnInstance() {
  const instanceId = returnInstanceId.value
  const reason = returnReason.value.trim()
  if (!instanceId || !reason || [...reason].length > 500) {
    returnError.value = '退回原因长度必须为 1–500 个字符。'
    return
  }
  mutation.value = `return:${instanceId}`
  returnError.value = ''
  try {
    await flowApi.returnInstance(systemId.value, instanceId, { reason })
  } catch (cause) {
    returnError.value = message(cause)
    mutation.value = ''
    return
  }
  mutation.value = ''
  returnOpen.value = false
  returnReason.value = ''
  try {
    await refreshClaimProjections(instanceId)
  } catch (cause) {
    error.value = message(cause)
  }
}

async function cancelClaim() {
  const instanceId = cancelClaimInstanceId.value
  const reason = cancelClaimReason.value.trim()
  if (!instanceId || !reason || [...reason].length > 500) {
    cancelClaimError.value = '取消认领原因长度必须为 1–500 个字符。'
    return
  }
  mutation.value = `cancel-claim:${instanceId}`
  cancelClaimError.value = ''
  try {
    await flowApi.cancelClaim(systemId.value, instanceId, { reason })
  } catch (cause) {
    cancelClaimError.value = message(cause)
    mutation.value = ''
    return
  }
  mutation.value = ''
  cancelClaimOpen.value = false
  cancelClaimReason.value = ''
  try {
    await refreshClaimProjections(instanceId)
  } catch (cause) {
    error.value = message(cause)
  }
}

async function claim() {
  const instanceId = claimInstanceId.value
  const comment = claimComment.value.trim()
  if (!instanceId || [...comment].length > 500) {
    claimError.value = '认领备注最多为 500 个字符。'
    return
  }
  mutation.value = `claim:${instanceId}`
  claimError.value = ''
  try {
    await flowApi.claim(systemId.value, instanceId, { comment })
  } catch (cause) {
    claimError.value = message(cause)
    mutation.value = ''
    return
  }
  mutation.value = ''
  claimOpen.value = false
  claimComment.value = ''
  try {
    await refreshClaimProjections(instanceId)
  } catch (cause) {
    error.value = message(cause)
  }
}

async function loadComments(instanceId: string, page = commentPage.value) {
  const ownsDetail = selectedInstance.value?.instanceId === instanceId
  if (ownsDetail) {
    commentTimelineLoading.value = true
    commentTimelineError.value = ''
  }
  try {
    const result = await flowApi.listComments(systemId.value, instanceId, page, interactionPageSize)
    if (selectedInstance.value?.instanceId !== instanceId) return
    selectedComments.value = result.items
    commentPage.value = result.page
    commentTotal.value = result.total
  } catch (cause) {
    if (selectedInstance.value?.instanceId === instanceId) {
      commentTimelineError.value = message(cause)
    }
  } finally {
    if (selectedInstance.value?.instanceId === instanceId) {
      commentTimelineLoading.value = false
    }
  }
}

async function appendComment() {
  const instanceId = selectedInstance.value?.instanceId ?? ''
  const body = commentBody.value.trim()
  if (!instanceId || !body || [...body].length > 2000) {
    commentError.value = '评论内容长度必须为 1–2000 个字符。'
    return
  }
  mutation.value = `comment:${instanceId}`
  commentError.value = ''
  try {
    await flowApi.createComment(systemId.value, instanceId, { body })
  } catch (cause) {
    commentError.value = message(cause)
    mutation.value = ''
    return
  }

  mutation.value = ''
  commentBody.value = ''
  await loadComments(instanceId, commentPage.value)
}

async function loadCopies(instanceId: string, page = copyPage.value) {
  const ownsDetail = selectedInstance.value?.instanceId === instanceId
  if (ownsDetail) {
    copyTimelineLoading.value = true
    copyTimelineError.value = ''
  }
  try {
    const result = await flowApi.listCopies(systemId.value, instanceId, page, interactionPageSize)
    if (selectedInstance.value?.instanceId !== instanceId) return
    selectedCopies.value = result.items
    copyPage.value = result.page
    copyTotal.value = result.total
  } catch (cause) {
    if (selectedInstance.value?.instanceId === instanceId) {
      copyTimelineError.value = message(cause)
    }
  } finally {
    if (selectedInstance.value?.instanceId === instanceId) {
      copyTimelineLoading.value = false
    }
  }
}

function openCopy(instance: FlowInstance) {
  copyInstanceId.value = instance.instanceId
  copyTargetMemberId.value = ''
  copyMessage.value = ''
  copyError.value = ''
  copyOpen.value = true
}

async function copyInstance() {
  const instanceId = copyInstanceId.value
  const targetMemberId = copyTargetMemberId.value.trim()
  const optionalMessage = copyMessage.value.trim()
  if (!instanceId || !targetMemberId || [...optionalMessage].length > 500) {
    copyError.value = '请选择抄送成员；抄送消息最多为 500 个字符。'
    return
  }
  mutation.value = `copy:${instanceId}`
  copyError.value = ''
  try {
    await flowApi.copy(systemId.value, instanceId, {
      targetMemberId,
      ...(optionalMessage ? { message: optionalMessage } : {}),
    })
  } catch (cause) {
    copyError.value = message(cause)
    mutation.value = ''
    return
  }

  mutation.value = ''
  copyOpen.value = false
  copyTargetMemberId.value = ''
  copyMessage.value = ''
  await loadCopies(
    instanceId,
    selectedInstance.value?.instanceId === instanceId ? copyPage.value : 1,
  )
}

function changeUrgePage(page: number) {
  if (selectedInstance.value) void loadUrges(selectedInstance.value.instanceId, page)
}

function changeCommentPage(page: number) {
  if (selectedInstance.value) void loadComments(selectedInstance.value.instanceId, page)
}

function changeCopyPage(page: number) {
  if (selectedInstance.value) void loadCopies(selectedInstance.value.instanceId, page)
}

async function showDetail(instance: FlowInstance, useProvidedInstance = false) {
  if (
    selectedInstance.value
    && selectedInstance.value.instanceId !== instance.instanceId
  ) {
    clearExternalTaskLeasesOutsideInstance(instance.instanceId)
  }
  detailOpen.value = true
  detailLoading.value = true
  selectedInstance.value = instance
  selectedHistory.value = null
  selectedUrges.value = []
  urgePage.value = 1
  urgeTotal.value = 0
  urgeTimelineError.value = ''
  selectedComments.value = []
  commentPage.value = 1
  commentTotal.value = 0
  commentTimelineError.value = ''
  commentBody.value = ''
  commentError.value = ''
  selectedCopies.value = []
  copyPage.value = 1
  copyTotal.value = 0
  copyTimelineError.value = ''
  error.value = ''
  try {
    const [current, history, urges, comments, copies] = await Promise.all([
      useProvidedInstance
        ? Promise.resolve(instance)
        : flowApi.instance(systemId.value, instance.instanceId),
      flowApi.history(systemId.value, instance.instanceId),
      flowApi.listUrges(systemId.value, instance.instanceId, 1, interactionPageSize),
      flowApi.listComments(systemId.value, instance.instanceId, 1, interactionPageSize),
      flowApi.listCopies(systemId.value, instance.instanceId, 1, interactionPageSize),
    ])
    selectedInstance.value = current
    selectedHistory.value = history
    selectedUrges.value = urges.items
    urgePage.value = urges.page
    urgeTotal.value = urges.total
    selectedComments.value = comments.items
    commentPage.value = comments.page
    commentTotal.value = comments.total
    selectedCopies.value = copies.items
    copyPage.value = copies.page
    copyTotal.value = copies.total
  } catch (cause) {
    error.value = message(cause)
  } finally {
    detailLoading.value = false
  }
}

async function openChildInstance(childInstanceId: string) {
  mutation.value = `child-detail:${childInstanceId}`
  error.value = ''
  try {
    const child = await flowApi.instance(systemId.value, childInstanceId)
    await showDetail(child, true)
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

function canDecideInstance(instance: FlowInstance) {
  const currentMemberId = session.context?.memberId
  const assignedDirectly = instance.parallelBranches?.length
    ? instance.parallelBranches.some((branch) => (
        branch.status === 'PENDING'
        && branch.activeApproverIds.includes(currentMemberId ?? '')
      ))
    : directDecisionEligible(instance)
  const assignedByDelegation = instance.parallelBranches?.length
    ? instance.parallelBranches.some((branch) => (
        branch.status === 'PENDING'
        && representedMemberIdsForBranch(instance, branch).length > 0
      ))
    : representedMemberIds(instance).length > 0
  return canDecide.value
    && instance.status === 'PENDING'
    && instance.claimState === 'CLAIMED'
    && Boolean(assignedDirectly || assignedByDelegation)
}

function directDecisionEligible(instance: FlowInstance) {
  const currentMemberId = session.context?.memberId
  return instance.approvalMode === 'ANY'
    || instance.approvalMode === 'ALL'
    || instance.approvalMode === 'QUORUM'
    ? instance.activeApproverIds?.includes(currentMemberId ?? '')
    : instance.approverId === currentMemberId
}

function representedMemberIds(
  target: {
    representedAuthorities?: Array<{
      representedMemberId: string
      delegationRuleId: string | null
    }>
  } | null | undefined,
) {
  const values = target?.representedAuthorities
    ?.filter(item => Boolean(item.delegationRuleId))
    .map(item => item.representedMemberId)
    ?? []
  return [...new Set(values.filter(Boolean))]
}

function representedMemberIdsForBranch(
  instance: FlowInstance | null | undefined,
  branch: { activeApproverIds: string[] },
) {
  return representedMemberIds(instance).filter(memberId =>
    branch.activeApproverIds.includes(memberId))
}

function canWithdrawInstance(instance: FlowInstance) {
  return canWithdraw.value
    && instance.status === 'PENDING'
    && instance.requesterId === session.context?.memberId
}

function canTerminateInstance(instance: FlowInstance) {
  return canTerminate.value && instance.status === 'PENDING'
}

function canUrgeInstance(instance: FlowInstance) {
  return canUrge.value
    && instance.status === 'PENDING'
    && instance.claimState === 'CLAIMED'
    && instance.requesterId === session.context?.memberId
}

function canTransferInstance(instance: FlowInstance) {
  return canTransfer.value
    && !instance.parallelBranches?.length
    && (instance.approvalMode ?? 'SEQUENTIAL') === 'SEQUENTIAL'
    && instance.status === 'PENDING'
    && instance.claimState === 'CLAIMED'
    && instance.approverId === session.context?.memberId
}

function canAddSignInstance(instance: FlowInstance) {
  return canAddSign.value
    && !instance.parallelBranches?.length
    && (instance.approvalMode ?? 'SEQUENTIAL') === 'SEQUENTIAL'
    && instance.status === 'PENDING'
    && instance.claimState === 'CLAIMED'
    && instance.approverId === session.context?.memberId
}

function canReduceSignInstance(instance: FlowInstance) {
  return canReduceSign.value
    && !instance.parallelBranches?.length
    && (instance.approvalMode ?? 'SEQUENTIAL') === 'SEQUENTIAL'
    && instance.status === 'PENDING'
    && instance.claimState === 'CLAIMED'
    && instance.approverId === session.context?.memberId
    && futureApproverSteps(instance).length > 0
}

function canReturnInstance(instance: FlowInstance) {
  return canReturn.value
    && !instance.parallelBranches?.length
    && (instance.approvalMode ?? 'SEQUENTIAL') === 'SEQUENTIAL'
    && instance.status === 'PENDING'
    && instance.claimState === 'CLAIMED'
    && instance.approverId === session.context?.memberId
}

function canCancelClaimInstance(instance: FlowInstance) {
  return canCancelClaim.value
    && !instance.parallelBranches?.length
    && (instance.approvalMode ?? 'SEQUENTIAL') === 'SEQUENTIAL'
    && instance.status === 'PENDING'
    && instance.claimState === 'CLAIMED'
    && instance.approverId === session.context?.memberId
}

function claimStateLabel(claimState: FlowInstance['claimState']) {
  return claimState === 'OPEN' ? '待认领' : '已认领'
}

function resolvedActiveCompletionOrdinals(instance: FlowInstance) {
  if (instance.activeCompletionOrdinals?.length) {
    return [...new Set(instance.activeCompletionOrdinals)].sort((left, right) => left - right)
  }
  return instance.activeCompletionOrdinal === null
    || instance.activeCompletionOrdinal === undefined
    ? []
    : [instance.activeCompletionOrdinal]
}

function orderedCompensationExecutions(instance: FlowInstance) {
  return [...(instance.compensationExecutions ?? [])]
    .sort((left, right) => right.originalOrdinal - left.originalOrdinal)
}

function historyCommentPrefix(type: string) {
  if (['TRANSFERRED', 'ADD_SIGNED', 'SIGN_REMOVED', 'RETURNED', 'CLAIM_CANCELLED'].includes(type)) {
    return '原因：'
  }
  return type === 'CLAIMED' ? '备注：' : ''
}

async function retryCompletionExecution(
  instance: FlowInstance,
  execution: FlowCompletionExecution,
) {
  mutation.value = `completion-retry:${execution.executionId}`
  error.value = ''
  try {
    await flowApi.retryCompletionExecution(
      systemId.value,
      instance.instanceId,
      execution.executionId,
    )
    await Promise.all([loadInstances(), loadApprovalTasks()])
    await showDetail(instance)
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function retryCompensationExecution(
  instance: FlowInstance,
  execution: FlowCompensationExecution,
) {
  mutation.value = `compensation-retry:${execution.compensationExecutionId}`
  error.value = ''
  try {
    await flowApi.retryCompensationExecution(
      systemId.value,
      instance.instanceId,
      execution.compensationExecutionId,
    )
    await Promise.all([loadInstances(), loadApprovalTasks()])
    await showDetail(instance)
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

function positionLabel(position: FlowAssignmentPosition) {
  return position === 'BEFORE' ? '当前步骤之前' : '当前步骤之后'
}

function statusLabel(status: FlowInstance['status']) {
  return {
    PENDING: '待审批',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    WITHDRAWN: '已撤回',
    TERMINATED: '已终止',
  }[status]
}

function statusColor(status: FlowInstance['status']) {
  return {
    PENDING: 'blue',
    APPROVED: 'green',
    REJECTED: 'red',
    WITHDRAWN: 'default',
    TERMINATED: 'orange',
  }[status]
}

function time(value: string | null | undefined) {
  if (!value) return '—'
  const parsed = new Date(value)
  return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleString('zh-CN')
}

function localDateTime(value: Date) {
  const offset = value.getTimezoneOffset() * 60_000
  return new Date(value.getTime() - offset).toISOString().slice(0, 16)
}

function message(cause: unknown) {
  if (cause instanceof ApiRequestError) return cause.message || cause.code
  return cause instanceof Error ? cause.message : '流程请求失败，请稍后重试'
}

function resetScope() {
  definitionPage.value = 1
  instancePage.value = 1
  instanceStatus.value = ''
  instanceFrom.value = ''
  instanceTo.value = ''
  taskStatus.value = 'PENDING'
  taskPage.value = 1
  claimablePage.value = 1
  definitions.value = []
  definitionTotal.value = 0
  publishedVersions.value = new Map()
  periodicSchedules.value = new Map()
  versionHistoryOpen.value = false
  versionHistoryDefinition.value = null
  versionHistoryItems.value = []
  versionHistoryPage.value = 1
  versionHistoryTotal.value = 0
  versionHistoryError.value = ''
  preflightOpen.value = false
  preflightDefinition.value = null
  preflightCheck.value = null
  preflightSimulation.value = null
  preflightError.value = ''
  simulationRequesterId.value = ''
  simulationBusinessKey.value = ''
  simulationTriggerValues.value = '{}'
  startableDefinitions.value = []
  startableDefinitionsError.value = ''
  instances.value = []
  instanceTotal.value = 0
  approvalTasks.value = []
  taskTotal.value = 0
  claimableTasks.value = []
  claimableTotal.value = 0
  claimableError.value = ''
  externalTasks.value = []
  externalTaskTopic.value = ''
  externalTaskPage.value = 1
  externalTaskTotal.value = 0
  externalTaskError.value = ''
  externalTaskLeaseTokens.value = new Map()
  externalTaskResultDrafts.value = new Map()
  externalTaskFailureCodes.value = new Map()
  externalTaskFailureMessages.value = new Map()
  delegations.value = []
  delegationPage.value = 1
  delegationTotal.value = 0
  delegationError.value = ''
  delegationOpen.value = false
  delegationDelegatorMemberId.value = ''
  delegationDelegateMemberId.value = ''
  delegationStartsAt.value = ''
  delegationEndsAt.value = ''
  delegationDefinitionId.value = ''
  detailOpen.value = false
  selectedInstance.value = null
  extensionEditorOpen.value = false
  extensionDefinition.value = null
  selectedHistory.value = null
  selectedUrges.value = []
  urgePage.value = 1
  urgeTotal.value = 0
  urgeTimelineError.value = ''
  selectedComments.value = []
  commentPage.value = 1
  commentTotal.value = 0
  commentTimelineError.value = ''
  commentBody.value = ''
  commentError.value = ''
  selectedCopies.value = []
  copyPage.value = 1
  copyTotal.value = 0
  copyTimelineError.value = ''
  withdrawOpen.value = false
  withdrawInstanceId.value = ''
  withdrawReason.value = ''
  withdrawError.value = ''
  terminateOpen.value = false
  terminateInstanceId.value = ''
  terminateReason.value = ''
  terminateError.value = ''
  urgeOpen.value = false
  urgeInstanceId.value = ''
  urgeMessage.value = ''
  urgeError.value = ''
  transferOpen.value = false
  transferInstanceId.value = ''
  transferTargetMemberId.value = ''
  transferReason.value = ''
  transferError.value = ''
  addSignOpen.value = false
  addSignInstanceId.value = ''
  addSignTargetMemberId.value = ''
  addSignPosition.value = 'BEFORE'
  addSignReason.value = ''
  addSignError.value = ''
  reduceSignOpen.value = false
  reduceSignInstanceId.value = ''
  reduceSignCandidates.value = []
  reduceSignTargetStepIndex.value = null
  reduceSignReason.value = ''
  reduceSignError.value = ''
  returnOpen.value = false
  returnInstanceId.value = ''
  returnReason.value = ''
  returnError.value = ''
  cancelClaimOpen.value = false
  cancelClaimInstanceId.value = ''
  cancelClaimReason.value = ''
  cancelClaimError.value = ''
  claimOpen.value = false
  claimInstanceId.value = ''
  claimComment.value = ''
  claimError.value = ''
  copyOpen.value = false
  copyInstanceId.value = ''
  copyTargetMemberId.value = ''
  copyMessage.value = ''
  copyError.value = ''
  decisionRepresentedMemberId.value = ''
  definitionOpen.value = false
  editingDefinition.value = null
  definitionName.value = ''
  definitionApprovers.value = ['']
  definitionApprovalMode.value = 'SEQUENTIAL'
  definitionApprovalStages.value = null
  definitionApproverSource.value = { kind: 'FIXED', sourceId: null }
  definitionQuorumRule.value = null
  definitionDeadlinePolicy.value = null
  definitionDecisionCommentPolicy.value = null
  definitionDecisionEvidencePolicy.value = null
  definitionCompletionSteps.value = []
  definitionGateway.value = null
  definitionParallelGateway.value = null
  definitionInclusiveGateway.value = null
  definitionTriggerEnabled.value = false
  definitionTriggerModuleCode.value = ''
  definitionTriggerEvent.value = 'RECORD_ACTIVATED'
  definitionTriggerPriority.value = '0'
  definitionTriggerExclusive.value = true
  definitionTriggerConditions.value = []
  definitionTriggerStartAt.value = ''
  definitionTriggerIntervalMinutes.value = '60'
  definitionStatusMappingEnabled.value = false
  definitionStatusFieldCode.value = ''
  definitionStatusApprovedValue.value = ''
  definitionStatusRejectedValue.value = ''
  definitionStatusWithdrawnValue.value = ''
  definitionStatusTerminatedValue.value = ''
  startOpen.value = false
  startDefinitionId.value = ''
  startVersion.value = ''
  startBusinessKey.value = ''
  startBindingModuleCode.value = ''
  startBindingRecordId.value = ''
  startRouteValues.value = '{}'
  startError.value = ''
  startRefreshWarning.value = ''
  startReceipt.value = null
  startIdempotencyKey.value = ''
  startSubmittedFingerprint.value = ''
  appliedStartDeepLink = ''
  error.value = ''
  applyStartDeepLink()
  applyAnalyticsDrillQuery()
  void refresh()
}

onMounted(() => {
  applyStartDeepLink()
  applyAnalyticsDrillQuery()
  void refresh()
})
watch([systemId, tenantId], resetScope)
watch(() => route.fullPath, () => {
  applyStartDeepLink()
  applyAnalyticsDrillQuery()
  instancePage.value = 1
  taskPage.value = 1
  void Promise.all([loadInstances(), loadApprovalTasks()])
})
watch(definitionTriggerEvent, (value) => {
  if (value === 'PERIODIC') definitionStatusMappingEnabled.value = false
})
watch(definitionPage, loadDefinitions)
watch(instancePage, loadInstances)
watch(decisionBranchCode, () => {
  if (decisionRepresentationOptions.value.includes(decisionRepresentedMemberId.value)) return
  decisionRepresentedMemberId.value = decisionDirectlyEligible.value
    ? ''
    : decisionRepresentationOptions.value[0] ?? ''
})
</script>

<template>
  <section class="flow-page">
    <header class="flow-heading">
      <div>
        <h1><GitPullRequest :size="24" /> 审批流程</h1>
        <p>配置单级审批流程，发起业务审批并处理待办实例。</p>
      </div>
      <div class="flow-actions">
        <a-button
          :loading="loadingDefinitions || loadingStartableDefinitions || loadingInstances || loadingTasks || loadingClaimableTasks || loadingExternalTasks || loadingDelegations"
          @click="refresh"
        >
          <RefreshCw :size="16" />刷新
        </a-button>
        <a-button v-if="canStart" class="flow-start" @click="openStart()"><Send :size="16" />发起审批</a-button>
        <a-button v-if="canDecide" @click="openDecision(null, 'approve')">
          <Check :size="16" />处理实例
        </a-button>
        <a-button v-if="canManageDefinitions" class="flow-definition-create" type="primary" @click="openCreateDefinition">
          <Plus :size="16" />新建定义
        </a-button>
        <a-button
          v-if="canManageDefinitions"
          class="flow-decision-template-manage"
          @click="openTemplateManager"
        >
          <MessageSquare :size="16" />意见模板
        </a-button>
      </div>
    </header>

    <a-alert v-if="error" type="error" show-icon closable :message="error" @close="error = ''" />

    <section v-if="canManageDefinitions" class="flow-section">
      <header class="section-heading">
        <div>
          <h2>流程定义</h2>
          <p>草稿可以反复修订；发布后，发起审批默认使用最新版本。</p>
        </div>
        <span>共 {{ definitionTotal }} 个</span>
      </header>
      <a-spin :spinning="loadingDefinitions">
        <a-empty v-if="!definitions.length && !loadingDefinitions" description="当前没有流程定义" />
        <div v-else class="definition-list">
          <article v-for="definition in definitions" :key="definition.definitionId" class="flow-card">
            <div>
              <strong>{{ definition.name }}</strong>
              <p>
                定义 {{ definition.definitionId }} · 草稿修订 {{ definition.revision }}
                · {{ approvalModeLabel(definition.approvalMode) }}
                · {{ approverSourceLabel(definition.approverSource) }}
                {{ definition.approverSource ? '' : definitionSequence(definition).join(' → ') }}
              </p>
              <p class="definition-trigger-summary">
                草稿触发：{{ triggerBindingSummary(definition.triggerBinding) }}
              </p>
              <p class="definition-approval-stages-summary">
                审批阶段：{{ approvalStagesSummary(definition.approvalStages) }}
              </p>
              <p class="definition-gateway-summary">
                草稿路由：{{ definition.inclusiveGateway
                  ? inclusiveGatewaySummary(definition.inclusiveGateway)
                  : definition.parallelGateway
                  ? parallelGatewaySummary(definition.parallelGateway)
                  : gatewaySummary(definition.gateway) }}
              </p>
              <p class="definition-deadline-summary">
                审批时限：{{ deadlinePolicyLabel(definition.deadlinePolicy) }}
              </p>
              <p class="definition-decision-comment-summary">
                审批意见：{{ decisionCommentPolicyLabel(definition.decisionCommentPolicy) }}
              </p>
              <p class="definition-decision-evidence-summary">
                审批证据：{{ decisionEvidencePolicyLabel(definition.decisionEvidencePolicy) }}
              </p>
              <p class="definition-completion-steps-summary">
                完成步骤：{{ completionStepsSummary(definition.completionSteps) }}
              </p>
              <p class="definition-completion-failure-policy">
                完成失败策略：{{ completionFailurePolicyLabel(
                  definition.completionFailurePolicy,
                ) }}
              </p>
              <p class="definition-status-mapping-summary">
                草稿状态映射：{{ statusMappingSummary(definition.recordStatusMapping) }}
              </p>
              <p
                v-if="publishedDefinition(definition.definitionId)"
                class="published-trigger-summary"
              >
                最近发布 v{{ publishedDefinition(definition.definitionId)?.version }}：
                {{ triggerBindingSummary(publishedDefinition(definition.definitionId)?.triggerBinding ?? null) }}
              </p>
              <p
                v-if="publishedDefinition(definition.definitionId)"
                class="published-status-mapping-summary"
              >
                最近发布状态映射：
                {{ statusMappingSummary(publishedDefinition(definition.definitionId)?.recordStatusMapping ?? null) }}
              </p>
              <p
                v-if="periodicSchedule(definition.definitionId)"
                class="published-trigger-summary periodic-runtime-summary"
              >
                周期运行 v{{ periodicSchedule(definition.definitionId)?.definitionVersion }} ·
                {{ periodicSchedule(definition.definitionId)?.status }} ·
                下次 {{ time(periodicSchedule(definition.definitionId)?.nextFireAt ?? null) }} ·
                上次 {{ time(periodicSchedule(definition.definitionId)?.lastScheduledAt ?? null) }}
                <template v-if="periodicSchedule(definition.definitionId)?.lastInstanceId">
                  · 实例 {{ periodicSchedule(definition.definitionId)?.lastInstanceId }}
                </template>
                <template v-if="periodicSchedule(definition.definitionId)?.pauseReason">
                  · {{ periodicSchedule(definition.definitionId)?.pauseReason }}
                </template>
              </p>
              <small>更新于 {{ time(definition.updatedAt) }}</small>
            </div>
            <div class="card-actions">
              <a-button class="flow-definition-revise" :disabled="Boolean(mutation)" @click="openReviseDefinition(definition)">
                <Pencil :size="15" />修订
              </a-button>
              <a-button
                class="flow-definition-check"
                :loading="mutation === `check:${definition.definitionId}`"
                :disabled="Boolean(mutation) && mutation !== `check:${definition.definitionId}`"
                @click="openDraftCheck(definition)"
              >
                <Check :size="15" />检查
              </a-button>
              <a-button
                class="flow-definition-simulate"
                :loading="mutation === `simulate:${definition.definitionId}`"
                :disabled="Boolean(mutation) && mutation !== `simulate:${definition.definitionId}`"
                @click="openDraftSimulation(definition)"
              >
                <GitPullRequest :size="15" />模拟
              </a-button>
              <a-button
                class="flow-definition-history"
                :disabled="Boolean(mutation)"
                @click="openVersionHistory(definition)"
              >
                <Eye :size="15" />版本
              </a-button>
              <a-button
                class="flow-definition-extension"
                :disabled="Boolean(mutation)"
                @click="openExtensionEditor(definition)"
              >
                <GitPullRequest :size="15" />扩展节点
              </a-button>
              <a-button
                class="flow-definition-publish"
                :loading="mutation === `publish:${definition.definitionId}`"
                :disabled="Boolean(mutation) && mutation !== `publish:${definition.definitionId}`"
                @click="publishDefinition(definition)"
              >
                <Rocket :size="15" />发布
              </a-button>
              <a-button v-if="canStart" class="flow-start" type="primary" :disabled="Boolean(mutation)" @click="openStart(definition)">
                <Send :size="15" />发起
              </a-button>
            </div>
          </article>
        </div>
      </a-spin>
      <a-pagination
        v-if="definitionTotal > 20"
        v-model:current="definitionPage"
        :page-size="20"
        :total="definitionTotal"
        :show-size-changer="false"
      />
    </section>

    <section v-if="canUseDelegations" class="flow-section delegation-section">
      <header class="section-heading">
        <div>
          <h2>审批委托</h2>
          <p>委托只增加代理审批权限，不改变已发布定义和运行中实例的原始参与人。</p>
        </div>
        <div class="card-actions">
          <span>共 {{ delegationTotal }} 条</span>
          <a-button
            class="delegation-create"
            type="primary"
            :disabled="Boolean(mutation)"
            @click="openCreateDelegation"
          >
            <UserPlus :size="15" />新建委托
          </a-button>
        </div>
      </header>
      <a-alert
        v-if="delegationError && !delegationOpen"
        class="delegation-error"
        type="error"
        show-icon
        :message="delegationError"
      />
      <a-spin :spinning="loadingDelegations">
        <a-empty
          v-if="!delegations.length && !loadingDelegations"
          description="当前没有对外审批委托"
        />
        <div v-else class="delegation-list">
          <article
            v-for="rule in delegations"
            :key="rule.delegationRuleId"
            class="flow-card delegation-card"
          >
            <div>
              <div class="instance-title">
                <a-tag :color="rule.status === 'ACTIVE'
                  ? 'green'
                  : rule.status === 'SCHEDULED'
                    ? 'blue'
                    : 'default'"
                >
                  {{ rule.status }}
                </a-tag>
                <strong>
                  成员 {{ rule.delegatorMemberId }} → 成员 {{ rule.delegateMemberId }}
                </strong>
              </div>
              <p>
                {{ time(rule.startsAt) }} 至 {{ time(rule.endsAt) }}
                · {{ rule.definitionId ? `仅定义 ${rule.definitionId}` : '全部流程定义' }}
              </p>
              <small>
                规则 {{ rule.delegationRuleId }} · 创建人 {{ rule.createdByMemberId }}
                <template v-if="rule.revokedAt">
                  · 撤销人 {{ rule.revokedByMemberId }} · {{ time(rule.revokedAt) }}
                </template>
              </small>
            </div>
            <div class="card-actions">
              <a-button
                v-if="['ACTIVE', 'SCHEDULED'].includes(rule.status)"
                class="delegation-revoke"
                danger
                :loading="mutation === `delegation:revoke:${rule.delegationRuleId}`"
                :disabled="Boolean(mutation)
                  && mutation !== `delegation:revoke:${rule.delegationRuleId}`"
                @click="revokeDelegation(rule)"
              >
                撤销
              </a-button>
            </div>
          </article>
        </div>
      </a-spin>
      <a-pagination
        v-if="delegationTotal > delegationPageSize"
        class="delegation-pagination"
        :current="delegationPage"
        :page-size="delegationPageSize"
        :total="delegationTotal"
        :show-size-changer="false"
        @change="changeDelegationPage"
      />
    </section>

    <section v-if="canClaim" class="flow-section claimable-task-section">
      <header class="section-heading">
        <div>
          <h2>可认领任务</h2>
          <p>显示当前候选池中处于 OPEN 状态的审批任务。</p>
        </div>
        <span>共 {{ claimableTotal }} 个</span>
      </header>
      <a-alert
        v-if="claimableError"
        type="error"
        show-icon
        :message="claimableError"
      />
      <a-spin :spinning="loadingClaimableTasks">
        <a-empty
          v-if="!claimableTasks.length && !loadingClaimableTasks"
          description="当前没有可认领的审批任务"
        />
        <div v-else class="claimable-task-list">
          <article
            v-for="task in claimableTasks"
            :key="task.instanceId"
            class="flow-card claimable-task-card"
          >
            <div>
              <div class="instance-title">
                <a-tag :color="statusColor(task.status)">{{ statusLabel(task.status) }}</a-tag>
                <a-tag color="orange">{{ claimStateLabel(task.claimState) }}</a-tag>
                <strong>{{ task.businessKey }}</strong>
              </div>
              <p>
                实例 {{ task.instanceId }} · 定义 {{ task.definitionId }}
                / v{{ task.definitionVersion }}
                <template v-if="task.currentStageCode">
                  · 阶段 {{ (task.currentStageIndex ?? 0) + 1 }}（{{ task.currentStageCode }}）
                </template>
                · 步骤 {{ task.currentStepIndex + 1 }}
              </p>
              <small>发起于 {{ time(task.startedAt) }}</small>
            </div>
            <div class="card-actions">
              <a-button
                class="flow-claim"
                type="primary"
                :disabled="Boolean(mutation)"
                @click="openClaim(task)"
              >
                <Hand :size="15" />认领
              </a-button>
            </div>
          </article>
        </div>
      </a-spin>
      <a-pagination
        v-if="claimableTotal > claimablePageSize"
        class="claimable-pagination"
        :current="claimablePage"
        :page-size="claimablePageSize"
        :total="claimableTotal"
        :show-size-changer="false"
        @change="changeClaimablePage"
      />
    </section>

    <section v-if="canWorkExternalTasks" class="flow-section external-task-section">
      <header class="section-heading">
        <div>
          <h2>外部任务工作队列</h2>
          <p>按 Topic 拉取可用任务；租约令牌只保存在当前页面内存中。</p>
        </div>
        <span>可用 {{ externalTaskTotal }} 个</span>
      </header>
      <div class="external-task-filter">
        <a-input
          v-model:value="externalTaskTopic"
          class="external-task-topic-filter"
          maxlength="64"
          placeholder="精确 Topic；留空显示全部"
        />
        <a-button class="external-task-filter-submit" @click="filterExternalTasks">
          筛选
        </a-button>
      </div>
      <a-alert
        v-if="externalTaskError"
        class="external-task-error"
        type="error"
        show-icon
        :message="externalTaskError"
      />
      <a-spin :spinning="loadingExternalTasks">
        <a-empty
          v-if="!externalTasks.length && !loadingExternalTasks"
          description="当前没有可处理的外部任务"
        />
        <div v-else class="external-task-list">
          <article
            v-for="execution in externalTasks"
            :key="execution.executionId"
            class="flow-card external-task-card"
          >
            <div>
              <header>
                <strong>{{ execution.name }}</strong>
                <code>{{ execution.code }}</code>
                <a-tag>{{ execution.status }}</a-tag>
              </header>
              <p>
                执行 {{ execution.executionId }} · 实例 {{ execution.instanceId }}
                · 顺序 {{ execution.ordinal + 1 }}
              </p>
              <p>
                Topic {{ execution.externalTask?.topic }}
                · 尝试 {{ execution.attemptCount }}/{{ execution.maxAttempts }}
                · 可用 {{ time(execution.availableAt) }}
              </p>
              <small v-if="execution.leaseExpiresAt">
                租约截止 {{ time(execution.leaseExpiresAt) }}
              </small>
            </div>
            <div
              v-if="externalTaskLeaseTokens.has(execution.executionId)"
              class="external-task-lease-actions"
            >
              <a-button
                class="external-task-heartbeat"
                :loading="mutation === `external-task:heartbeat:${execution.executionId}`"
                @click="heartbeatExternalTask(execution)"
              >
                Heartbeat
              </a-button>
              <label>
                完成结果 JSON
                <textarea
                  class="external-task-result"
                  rows="3"
                  :value="externalTaskResultDrafts.get(execution.executionId) ?? '{}'"
                  @input="setExternalTaskDraft(
                    'result',
                    execution.executionId,
                    ($event.target as HTMLTextAreaElement).value,
                  )"
                />
              </label>
              <a-button
                class="external-task-complete"
                type="primary"
                :loading="mutation === `external-task:complete:${execution.executionId}`"
                @click="completeExternalTask(execution)"
              >
                完成任务
              </a-button>
              <label>
                失败代码
                <a-input
                  class="external-task-failure-code"
                  :value="externalTaskFailureCodes.get(execution.executionId) ?? ''"
                  maxlength="64"
                  @update:value="setExternalTaskDraft(
                    'code',
                    execution.executionId,
                    $event,
                  )"
                />
              </label>
              <label>
                失败消息
                <a-input
                  class="external-task-failure-message"
                  :value="externalTaskFailureMessages.get(execution.executionId) ?? ''"
                  maxlength="1000"
                  @update:value="setExternalTaskDraft(
                    'message',
                    execution.executionId,
                    $event,
                  )"
                />
              </label>
              <a-button
                class="external-task-fail"
                danger
                :loading="mutation === `external-task:fail:${execution.executionId}`"
                @click="failExternalTask(execution)"
              >
                标记失败
              </a-button>
            </div>
            <a-button
              v-else
              class="external-task-claim"
              type="primary"
              :loading="mutation === `external-task:claim:${execution.executionId}`"
              @click="claimExternalTask(execution)"
            >
              认领外部任务
            </a-button>
          </article>
        </div>
      </a-spin>
      <a-pagination
        v-if="externalTaskTotal > externalTaskPageSize"
        class="external-task-pagination"
        :current="externalTaskPage"
        :page-size="externalTaskPageSize"
        :total="externalTaskTotal"
        :show-size-changer="false"
        @change="changeExternalTaskPage"
      />
    </section>

    <section v-if="canRead" class="flow-section approval-task-section">
      <header class="section-heading">
        <div>
          <h2>我的审批任务</h2>
          <p>仅显示分配给当前成员的审批实例，可按处理状态筛选。</p>
        </div>
        <span>共 {{ taskTotal }} 个</span>
      </header>
      <div class="task-filter">
        <a-segmented
          :value="taskStatus"
          :options="taskStatusOptions"
          @change="changeTaskStatus"
        />
      </div>
      <a-spin :spinning="loadingTasks">
        <a-empty
          v-if="!approvalTasks.length && !loadingTasks"
          description="当前没有我的审批任务"
        />
        <div v-else class="approval-task-list">
          <article
            v-for="task in approvalTasks"
            :key="task.instanceId"
            class="flow-card approval-task-card"
          >
            <div>
              <div class="instance-title">
                <a-tag :color="statusColor(task.status)">{{ statusLabel(task.status) }}</a-tag>
                <a-tag :color="task.claimState === 'OPEN' ? 'orange' : 'blue'">
                  {{ claimStateLabel(task.claimState) }}
                </a-tag>
                <a-tag
                  v-if="representedMemberIds(task).length"
                  class="delegated-task-state"
                  color="purple"
                >
                  代理审批：代表 {{ representedMemberIds(task).join('、') }}
                </a-tag>
                <strong>{{ task.businessKey }}</strong>
              </div>
              <p>
                实例 {{ task.instanceId }} · 定义 {{ task.definitionId }}
                / v{{ task.definitionVersion }} · 发起人 {{ task.requesterId }}
              </p>
              <small>发起于 {{ time(task.startedAt) }}</small>
            </div>
            <div class="card-actions">
              <a-button class="flow-detail" @click="showDetail(task)"><Eye :size="15" />详情</a-button>
              <template v-if="canDecideInstance(task)">
                <a-button
                  class="task-approve"
                  type="primary"
                  :disabled="Boolean(mutation)"
                  @click="openDecision(task, 'approve')"
                >
                  <Check :size="15" />通过
                </a-button>
                <a-button
                  class="task-reject"
                  danger
                  :disabled="Boolean(mutation)"
                  @click="openDecision(task, 'reject')"
                >
                  <X :size="15" />驳回
                </a-button>
              </template>
              <a-button
                v-if="canWithdrawInstance(task)"
                class="flow-withdraw"
                :disabled="Boolean(mutation)"
                @click="openWithdraw(task)"
              >
                <Undo2 :size="15" />撤回
              </a-button>
              <a-button
                v-if="canTerminateInstance(task)"
                class="flow-terminate"
                danger
                :disabled="Boolean(mutation)"
                @click="openTerminate(task)"
              >
                <Ban :size="15" />终止
              </a-button>
              <a-button
                v-if="canUrgeInstance(task)"
                class="flow-urge"
                :disabled="Boolean(mutation)"
                @click="openUrge(task)"
              >
                <BellRing :size="15" />催办
              </a-button>
              <a-button
                v-if="canTransferInstance(task)"
                class="flow-transfer"
                :disabled="Boolean(mutation)"
                @click="openTransfer(task)"
              >
                <ArrowRightLeft :size="15" />转交
              </a-button>
              <a-button
                v-if="canAddSignInstance(task)"
                class="flow-add-sign"
                :disabled="Boolean(mutation)"
                @click="openAddSign(task)"
              >
                <UserPlus :size="15" />加签
              </a-button>
              <a-button
                v-if="canReduceSignInstance(task)"
                class="flow-reduce-sign"
                :disabled="Boolean(mutation)"
                @click="openReduceSign(task)"
              >
                <UserMinus :size="15" />减签
              </a-button>
              <a-button
                v-if="canReturnInstance(task)"
                class="flow-return"
                :disabled="Boolean(mutation) || task.currentStepIndex === 0"
                @click="openReturn(task)"
              >
                <Reply :size="15" />退回
              </a-button>
              <a-button
                v-if="canCancelClaimInstance(task)"
                class="flow-cancel-claim"
                :disabled="Boolean(mutation)"
                @click="openCancelClaim(task)"
              >
                <X :size="15" />取消认领
              </a-button>
            </div>
          </article>
        </div>
      </a-spin>
      <a-pagination
        v-if="taskTotal > taskPageSize"
        :current="taskPage"
        :page-size="taskPageSize"
        :total="taskTotal"
        :show-size-changer="false"
        @change="changeTaskPage"
      />
    </section>

    <section v-if="canRead" class="flow-section">
      <header class="section-heading">
        <div>
          <h2>审批实例</h2>
          <p>实例和历史从服务端重新读取，刷新页面后仍可继续处理。</p>
        </div>
        <span>共 {{ instanceTotal }} 个</span>
      </header>
      <a-spin :spinning="loadingInstances">
        <a-empty v-if="!instances.length && !loadingInstances" description="当前没有审批实例" />
        <div v-else class="instance-list">
          <article v-for="instance in instances" :key="instance.instanceId" class="flow-card">
            <div>
              <div class="instance-title">
                <a-tag :color="statusColor(instance.status)">{{ statusLabel(instance.status) }}</a-tag>
                <a-tag :color="instance.claimState === 'OPEN' ? 'orange' : 'blue'">
                  {{ claimStateLabel(instance.claimState) }}
                </a-tag>
                <strong>{{ instance.businessKey }}</strong>
              </div>
              <p>
                实例 {{ instance.instanceId }} · 定义 {{ instance.definitionId }}
                / v{{ instance.definitionVersion }} · 审批人 {{ instance.approverId }}
              </p>
              <small>发起于 {{ time(instance.startedAt) }}</small>
            </div>
            <div class="card-actions">
              <a-button class="flow-detail" @click="showDetail(instance)"><Eye :size="15" />详情</a-button>
              <template v-if="canDecideInstance(instance)">
                <a-button
                  class="instance-approve"
                  type="primary"
                  :disabled="Boolean(mutation)"
                  @click="openDecision(instance, 'approve')"
                >
                  <Check :size="15" />通过
                </a-button>
                <a-button
                  class="instance-reject"
                  danger
                  :disabled="Boolean(mutation)"
                  @click="openDecision(instance, 'reject')"
                >
                  <X :size="15" />驳回
                </a-button>
              </template>
              <a-button
                v-if="canWithdrawInstance(instance)"
                class="flow-withdraw"
                :disabled="Boolean(mutation)"
                @click="openWithdraw(instance)"
              >
                <Undo2 :size="15" />撤回
              </a-button>
              <a-button
                v-if="canTerminateInstance(instance)"
                class="flow-terminate"
                danger
                :disabled="Boolean(mutation)"
                @click="openTerminate(instance)"
              >
                <Ban :size="15" />终止
              </a-button>
              <a-button
                v-if="canUrgeInstance(instance)"
                class="flow-urge"
                :disabled="Boolean(mutation)"
                @click="openUrge(instance)"
              >
                <BellRing :size="15" />催办
              </a-button>
              <a-button
                v-if="canTransferInstance(instance)"
                class="flow-transfer"
                :disabled="Boolean(mutation)"
                @click="openTransfer(instance)"
              >
                <ArrowRightLeft :size="15" />转交
              </a-button>
              <a-button
                v-if="canAddSignInstance(instance)"
                class="flow-add-sign"
                :disabled="Boolean(mutation)"
                @click="openAddSign(instance)"
              >
                <UserPlus :size="15" />加签
              </a-button>
              <a-button
                v-if="canReduceSignInstance(instance)"
                class="flow-reduce-sign"
                :disabled="Boolean(mutation)"
                @click="openReduceSign(instance)"
              >
                <UserMinus :size="15" />减签
              </a-button>
              <a-button
                v-if="canReturnInstance(instance)"
                class="flow-return"
                :disabled="Boolean(mutation) || instance.currentStepIndex === 0"
                @click="openReturn(instance)"
              >
                <Reply :size="15" />退回
              </a-button>
              <a-button
                v-if="canCancelClaimInstance(instance)"
                class="flow-cancel-claim"
                :disabled="Boolean(mutation)"
                @click="openCancelClaim(instance)"
              >
                <X :size="15" />取消认领
              </a-button>
            </div>
          </article>
        </div>
      </a-spin>
      <a-pagination
        v-if="instanceTotal > 20"
        v-model:current="instancePage"
        :page-size="20"
        :total="instanceTotal"
        :show-size-changer="false"
      />
    </section>

    <a-alert
      v-if="!canManageDefinitions && !canRead && !canWorkExternalTasks"
      type="info"
      show-icon
      message="当前角色没有流程列表读取权限"
      description="仍可使用上方按钮按定义 ID 发起审批，或按实例 ID 处理审批。"
    />

    <a-modal
      v-model:open="preflightOpen"
      class="flow-preflight-modal"
      :title="`${preflightDefinition?.name ?? '流程'} · 发布前检查与模拟`"
      :footer="null"
      width="760px"
    >
      <a-alert
        v-if="preflightError"
        class="preflight-error"
        type="error"
        show-icon
        :message="preflightError"
      />
      <div class="preflight-actions">
        <a-button
          class="flow-run-check"
          :loading="mutation.startsWith('check:')"
          :disabled="Boolean(mutation) && !mutation.startsWith('check:')"
          @click="runDraftCheck"
        >
          <Check :size="15" />重新检查
        </a-button>
        <a-button
          class="flow-run-simulation"
          type="primary"
          :loading="mutation.startsWith('simulate:')"
          :disabled="Boolean(mutation) && !mutation.startsWith('simulate:')"
          @click="runDraftSimulation"
        >
          <GitPullRequest :size="15" />运行模拟
        </a-button>
      </div>
      <div class="preflight-form">
        <label>
          <span>模拟请求人</span>
          <input v-model="simulationRequesterId" class="simulation-requester-id">
        </label>
        <label>
          <span>模拟业务标识</span>
          <input v-model="simulationBusinessKey" class="simulation-business-key">
        </label>
        <label
          v-if="(preflightDefinition?.triggerBinding
            && preflightDefinition.triggerBinding.event !== 'PERIODIC')
            || preflightDefinition?.gateway
            || preflightDefinition?.inclusiveGateway
            || simulationUsesRecordMemberField"
        >
          <span>
            路由/触发/记录字段样本
            <template
              v-if="preflightDefinition?.triggerBinding
                && preflightDefinition.triggerBinding.event !== 'PERIODIC'"
            >
              （{{ preflightDefinition.triggerBinding.moduleCode }} ·
              {{ preflightDefinition.triggerBinding.event }}）
            </template>
          </span>
          <textarea
            v-model="simulationTriggerValues"
            class="simulation-trigger-values"
            rows="6"
          />
        </label>
      </div>
      <section
        v-if="preflightDefinition?.approvalStages?.length"
        class="preflight-approval-stages"
      >
        <strong>线性阶段计划</strong>
        <ol>
          <li
            v-for="stage in preflightDefinition.approvalStages"
            :key="stage.code"
          >
            {{ stage.name }}（{{ stage.code }} ·
            {{ approverSourceLabel(stage.approverSource) }} ·
            {{ approvalModeLabel(stage.approvalMode) }}）
          </li>
        </ol>
      </section>
      <section v-if="preflightCheck" class="draft-check-result">
        <header>
          <strong>草稿 r{{ preflightCheck.revision }}</strong>
          <a-tag :color="preflightCheck.verdict === 'READY' ? 'green' : 'red'">
            {{ preflightCheck.verdict }}
          </a-tag>
          <span>
            阻断 {{ preflightCheck.blockerCount }} · 警告 {{ preflightCheck.warningCount }}
          </span>
        </header>
        <p class="draft-check-completion-steps">
          完成步骤：{{ completionStepsSummary(preflightDefinition?.completionSteps) }}
        </p>
        <p class="draft-check-completion-failure-policy">
          完成失败策略：{{ completionFailurePolicyLabel(
            preflightDefinition?.completionFailurePolicy,
          ) }}
        </p>
        <a-empty
          v-if="!preflightCheck.issues.length"
          description="没有发布阻断或警告"
        />
        <ul v-else class="draft-check-issues">
          <li
            v-for="issue in preflightCheck.issues"
            :key="`${issue.code}:${issue.path}`"
            :class="issue.severity.toLowerCase()"
          >
            <strong>{{ issue.code }}</strong>
            <code>{{ issue.path }}</code>
            <span>{{ issue.message }}</span>
          </li>
        </ul>
      </section>
      <section v-if="preflightSimulation" class="draft-simulation-result">
        <header>
          <strong>模拟结果</strong>
          <a-tag :color="preflightSimulation.startable ? 'green' : 'orange'">
            {{ preflightSimulation.startable ? '可启动' : '不会启动' }}
          </a-tag>
          <span>{{ preflightSimulation.reason }}</span>
        </header>
        <p class="simulation-trigger-result">
          触发：
          {{ preflightSimulation.trigger.event ?? 'MANUAL' }} ·
          {{ preflightSimulation.trigger.matched ? 'MATCHED' : 'NOT_MATCHED' }} ·
          {{ preflightSimulation.trigger.reason }}
        </p>
        <p class="simulation-route-result">
          路由：
          <template v-if="preflightSimulation.route?.configured">
            {{ preflightSimulation.route.branchName }}
            （{{ preflightSimulation.route.branchCode }} ·
            {{ preflightSimulation.route.defaultBranch ? '默认' : '条件命中' }} ·
            {{ approvalModeLabel(preflightSimulation.route.approvalMode) }}）
          </template>
          <template v-else>
            {{ approvalModeLabel(preflightSimulation.route?.approvalMode) }}
          </template>
          <template v-if="preflightSimulation.route?.requiredApprovals">
            · 需同意 {{ preflightSimulation.route.requiredApprovals }} 人
          </template>
          · 来源
          {{ approverSourceLabel(
            simulationApproverSource(preflightSimulation.route?.branchCode),
          ) }}
          · 初始处理人
          {{ preflightSimulation.route?.activeApproverIds?.join('、') || '—' }}
        </p>
        <p
          v-if="preflightSimulation.route?.deadlinePolicy"
          class="simulation-deadline-result"
        >
          审批时限：{{ deadlinePolicyLabel(preflightSimulation.route.deadlinePolicy) }}
        </p>
        <p
          v-if="preflightSimulation.route?.approvalStages?.length"
          class="simulation-route-approval-stages"
        >
          分支阶段：{{ approvalStagesSummary(preflightSimulation.route.approvalStages) }}
        </p>
        <p class="simulation-decision-comment-result">
          审批意见：{{
            decisionCommentPolicyLabel(preflightSimulation.route?.decisionCommentPolicy)
          }}
        </p>
        <p class="simulation-decision-evidence-result">
          审批证据：{{
            decisionEvidencePolicyLabel(preflightSimulation.route?.decisionEvidencePolicy)
          }}
        </p>
        <p class="simulation-completion-steps-result">
          完成步骤：{{
            completionStepsSummary(
              preflightSimulation.completionSteps
                ?? preflightDefinition?.completionSteps,
            )
          }}
        </p>
        <p class="simulation-completion-failure-policy">
          完成失败策略：{{ completionFailurePolicyLabel(
            preflightSimulation.completionFailurePolicy
              ?? preflightDefinition?.completionFailurePolicy,
          ) }}
        </p>
        <div
          v-if="preflightSimulation.parallelRoutes?.length"
          class="simulation-parallel-routes"
        >
          <strong>并行拆分与汇合</strong>
          <p
            v-for="branch in preflightSimulation.parallelRoutes"
            :key="branch.branchCode"
          >
            {{ branch.branchName }}（{{ branch.branchCode }} ·
            {{ approvalModeLabel(branch.approvalMode) }} ·
            {{ approverSourceLabel(simulationApproverSource(branch.branchCode)) }}）
            · 需同意 {{ branch.requiredApprovals }} 人
            · 初始处理人 {{ branch.activeApproverIds.join('、') }}
            <small
              v-if="branch.approvalStages?.length"
              class="simulation-branch-approval-stages"
            >
              · 阶段 {{ approvalStagesSummary(branch.approvalStages) }}
            </small>
          </p>
          <p
            v-for="branch in preflightSimulation.parallelRoutes.filter(
              item => Boolean(item.deadlinePolicy),
            )"
            :key="`${branch.branchCode}-deadline`"
            class="simulation-branch-deadline"
          >
            {{ branch.branchName }}审批时限：{{ deadlinePolicyLabel(branch.deadlinePolicy) }}
          </p>
          <p
            v-for="branch in preflightSimulation.parallelRoutes"
            :key="`${branch.branchCode}-decision-comment`"
            class="simulation-branch-decision-comment"
          >
            {{ branch.branchName }}审批意见：{{
              decisionCommentPolicyLabel(branch.decisionCommentPolicy)
            }}
          </p>
          <p
            v-for="branch in preflightSimulation.parallelRoutes"
            :key="`${branch.branchCode}-decision-evidence`"
            class="simulation-branch-decision-evidence"
          >
            {{ branch.branchName }}审批证据：{{
              decisionEvidencePolicyLabel(branch.decisionEvidencePolicy)
            }}
          </p>
        </div>
        <ol class="simulation-steps">
          <li v-for="step in preflightSimulation.steps" :key="step.index">
            步骤 {{ step.index + 1 }} · 成员 {{ step.approverId }}
            <span v-if="step.initial">（初始处理人）</span>
          </li>
        </ol>
        <p v-if="preflightSimulation.statusEffects" class="simulation-status-effects">
          终态回写 {{ preflightSimulation.statusEffects.fieldCode }}：
          APPROVED→{{ preflightSimulation.statusEffects.approvedValue }} ·
          REJECTED→{{ preflightSimulation.statusEffects.rejectedValue }} ·
          WITHDRAWN→{{ preflightSimulation.statusEffects.withdrawnValue }} ·
          TERMINATED→{{ preflightSimulation.statusEffects.terminatedValue }}
        </p>
      </section>
    </a-modal>

    <a-modal
      v-model:open="versionHistoryOpen"
      class="flow-version-history-modal"
      :title="`${versionHistoryDefinition?.name ?? '流程'} · 版本历史`"
      :footer="null"
      width="760px"
    >
      <a-alert
        v-if="versionHistoryError"
        class="version-history-error"
        type="error"
        show-icon
        :message="versionHistoryError"
      />
      <a-spin :spinning="versionHistoryLoading">
        <a-empty
          v-if="!versionHistoryItems.length && !versionHistoryLoading"
          description="尚无已发布版本"
        />
        <div v-else class="version-history-list">
          <article
            v-for="version in versionHistoryItems"
            :key="version.version"
            class="version-history-item"
          >
            <div>
              <strong>v{{ version.version }} · {{ version.name }}</strong>
              <p>
                来源草稿修订 {{ version.sourceRevision }} ·
                {{ approvalModeLabel(version.approvalMode) }}
                {{ (version.approverIds?.length ? version.approverIds : [version.approverId]).join(' → ') }}
              </p>
              <p class="version-history-trigger">
                触发：{{ triggerBindingSummary(version.triggerBinding) }}
              </p>
              <p class="version-history-gateway">
                路由：{{ version.inclusiveGateway
                  ? inclusiveGatewaySummary(version.inclusiveGateway)
                  : version.parallelGateway
                  ? parallelGatewaySummary(version.parallelGateway)
                  : gatewaySummary(version.gateway) }}
              </p>
              <p class="version-history-deadline">
                审批时限：{{ deadlinePolicyLabel(version.deadlinePolicy) }}
              </p>
              <p class="version-history-decision-comment">
                审批意见：{{ decisionCommentPolicyLabel(version.decisionCommentPolicy) }}
              </p>
              <p class="version-history-decision-evidence">
                审批证据：{{ decisionEvidencePolicyLabel(version.decisionEvidencePolicy) }}
              </p>
              <p class="version-history-completion-steps">
                完成步骤：{{ completionStepsSummary(version.completionSteps) }}
              </p>
              <p class="version-history-completion-failure-policy">
                完成失败策略：{{ completionFailurePolicyLabel(
                  version.completionFailurePolicy,
                ) }}
              </p>
              <p class="version-history-mapping">
                状态映射：{{ statusMappingSummary(version.recordStatusMapping) }}
              </p>
              <small>发布于 {{ time(version.publishedAt) }}</small>
            </div>
            <a-button
              class="flow-version-restore"
              :loading="mutation === `restore:${version.definitionId}:${version.version}`"
              :disabled="Boolean(mutation) && mutation !== `restore:${version.definitionId}:${version.version}`"
              @click="restoreDefinitionVersion(version)"
            >
              <Undo2 :size="15" />恢复到草稿
            </a-button>
          </article>
        </div>
      </a-spin>
      <a-pagination
        v-if="versionHistoryTotal > versionHistoryPageSize"
        class="version-history-pagination"
        :current="versionHistoryPage"
        :page-size="versionHistoryPageSize"
        :total="versionHistoryTotal"
        :show-size-changer="false"
        @change="changeVersionHistoryPage"
      />
    </a-modal>

    <a-modal
      v-model:open="definitionOpen"
      :title="editingDefinition ? '修订流程定义' : '新建流程定义'"
      :width="1120"
      :confirm-loading="mutation === 'create' || mutation.startsWith('revise:')"
      @ok="saveDefinition()"
    >
      <a-form layout="vertical">
        <a-form-item label="流程名称" required>
          <a-input v-model:value="definitionName" class="definition-name" maxlength="160" placeholder="例如：合同审批" />
        </a-form-item>
        <FlowDraftCanvas
          v-model="definitionApprovers"
          v-model:approval-mode="definitionApprovalMode"
          v-model:approval-stages="definitionApprovalStages"
          v-model:approver-source="definitionApproverSource"
          v-model:quorum-rule="definitionQuorumRule"
          v-model:deadline-policy="definitionDeadlinePolicy"
          v-model:decision-comment-policy="definitionDecisionCommentPolicy"
          v-model:decision-evidence-policy="definitionDecisionEvidencePolicy"
          v-model:gateway="definitionGateway"
          v-model:parallel-gateway="definitionParallelGateway"
          v-model:inclusive-gateway="definitionInclusiveGateway"
          :system-id="systemId"
          :record-context-module-code="definitionTriggerEnabled
            && definitionTriggerEvent !== 'PERIODIC'
            ? definitionTriggerModuleCode
            : ''"
          :trigger-label="definitionGraphTriggerLabel"
          :approved-label="definitionGraphApprovedLabel"
          :rejected-label="definitionGraphRejectedLabel"
          :busy="mutation === 'create' || mutation.startsWith('revise:')"
          @save-check="saveDefinitionAndCheck"
          @save-simulate="saveDefinitionAndSimulate"
        />
        <section class="definition-completion-editor">
          <header>
            <div>
              <strong>审批完成后的有序步骤</strong>
              <p>人工审批全部通过后依次执行；未配置时保持直接完成。</p>
            </div>
            <span>{{ definitionCompletionSteps.length }}/8</span>
          </header>
          <label class="definition-completion-failure-policy-editor">
            完成失败策略
            <select
              v-model="definitionCompletionFailurePolicy"
              class="definition-completion-failure-policy-select"
            >
              <option value="MANUAL_RETRY">MANUAL_RETRY · 管理员手动重试</option>
              <option value="COMPENSATE">COMPENSATE · 失败后逆序补偿</option>
            </select>
          </label>
          <p class="definition-completion-stage-summary">
            {{ completionDraftStagesSummary() }}
          </p>
          <article
            v-for="(step, index) in definitionCompletionSteps"
            :key="index"
            class="definition-completion-step"
            :class="{ 'definition-completion-step-parallel': Boolean(step.parallelGroup.trim()) }"
          >
            <header>
              <strong>{{ index + 1 }}. {{ step.name || '未命名步骤' }}</strong>
              <a-tag v-if="step.parallelGroup.trim()" color="purple">
                并行组 {{ step.parallelGroup.trim() }} ·
                {{ completionParallelGroupSize(step.parallelGroup) }} 成员
              </a-tag>
              <div>
                <a-button
                  class="definition-completion-step-up"
                  :disabled="index === 0"
                  @click="moveCompletionStep(index, -1)"
                >
                  <ArrowUp :size="14" />
                </a-button>
                <a-button
                  class="definition-completion-step-down"
                  :disabled="index === definitionCompletionSteps.length - 1"
                  @click="moveCompletionStep(index, 1)"
                >
                  <ArrowDown :size="14" />
                </a-button>
                <a-button
                  class="definition-completion-step-remove"
                  danger
                  @click="removeCompletionStep(index)"
                >
                  <Trash2 :size="14" />
                </a-button>
              </div>
            </header>
            <div class="definition-completion-step-core">
              <label>
                稳定代码
                <a-input
                  v-model:value="step.code"
                  class="definition-completion-step-code"
                  maxlength="64"
                  placeholder="例如 archive_contract"
                />
              </label>
              <label>
                名称
                <a-input
                  v-model:value="step.name"
                  class="definition-completion-step-name"
                  maxlength="80"
                />
              </label>
              <label>
                类型
                <select
                  class="definition-completion-step-type"
                  :value="step.type"
                  @change="setCompletionStepType(
                    index,
                    ($event.target as HTMLSelectElement).value as FlowCompletionStep['type'],
                  )"
                >
                  <option value="EXTERNAL_TASK">EXTERNAL_TASK</option>
                  <option value="WEBHOOK">WEBHOOK</option>
                  <option value="SUBFLOW">SUBFLOW</option>
                </select>
              </label>
              <label>
                并行组（可选）
                <a-input
                  v-model:value="step.parallelGroup"
                  class="definition-completion-parallel-group"
                  maxlength="64"
                  placeholder="相邻 2–8 步填写同一小写组名"
                />
              </label>
            </div>
            <div
              v-if="step.type === 'EXTERNAL_TASK'"
              class="definition-completion-external-task"
            >
              <label>
                Topic
                <a-input
                  v-model:value="step.topic"
                  class="definition-completion-topic"
                  maxlength="64"
                  placeholder="例如 contract.archive"
                />
              </label>
              <label>
                租约秒数
                <a-input
                  v-model:value="step.leaseSeconds"
                  class="definition-completion-lease-seconds"
                  inputmode="numeric"
                />
              </label>
              <label>
                最大尝试
                <a-input
                  v-model:value="step.externalMaxAttempts"
                  class="definition-completion-external-max-attempts"
                  inputmode="numeric"
                />
              </label>
              <label>
                结果 JSON 上限（bytes）
                <a-input
                  v-model:value="step.resultJsonLimitBytes"
                  class="definition-completion-result-limit"
                  inputmode="numeric"
                />
              </label>
            </div>
            <div v-else-if="step.type === 'WEBHOOK'" class="definition-completion-webhook">
              <label>
                HTTPS URL
                <a-input
                  v-model:value="step.url"
                  class="definition-completion-webhook-url"
                  maxlength="1024"
                />
              </label>
              <label>
                Secret 引用
                <a-input
                  v-model:value="step.secretRef"
                  class="definition-completion-secret-ref"
                  maxlength="512"
                  placeholder="写入后仅显示 ********；留空清除"
                />
              </label>
              <label>
                超时秒数
                <a-input
                  v-model:value="step.timeoutSeconds"
                  class="definition-completion-timeout-seconds"
                  inputmode="numeric"
                />
              </label>
              <label>
                最大尝试
                <a-input
                  v-model:value="step.webhookMaxAttempts"
                  class="definition-completion-webhook-max-attempts"
                  inputmode="numeric"
                />
              </label>
              <label>
                基础退避秒数
                <a-input
                  v-model:value="step.baseBackoffSeconds"
                  class="definition-completion-backoff-seconds"
                  inputmode="numeric"
                />
              </label>
            </div>
            <div v-else class="definition-completion-subflow">
              <label>
                目标流程定义 ID
                <a-input
                  v-model:value="step.subflowDefinitionId"
                  class="definition-completion-subflow-definition-id"
                  inputmode="numeric"
                  maxlength="19"
                  placeholder="精确目标定义 ID"
                />
              </label>
              <label>
                已发布版本
                <a-input
                  v-model:value="step.subflowVersion"
                  class="definition-completion-subflow-version"
                  inputmode="numeric"
                  placeholder="精确已发布版本"
                />
              </label>
              <small>发布/检查会验证同系统、同租户、无循环且目标版本可用。</small>
            </div>
            <section
              v-if="definitionCompletionFailurePolicy === 'COMPENSATE'"
              class="definition-completion-compensation"
            >
              <header>
                <strong>逆序补偿动作</strong>
                <small>该完成步骤成功后若计划失败，将按原步骤顺序逆序执行。</small>
              </header>
              <label>
                补偿类型
                <select
                  class="definition-compensation-type"
                  :value="step.compensationType"
                  @change="setCompletionCompensationType(
                    index,
                    ($event.target as HTMLSelectElement).value as FlowCompletionStep['type'],
                  )"
                >
                  <option value="EXTERNAL_TASK">EXTERNAL_TASK</option>
                  <option value="WEBHOOK">WEBHOOK</option>
                  <option value="SUBFLOW">SUBFLOW</option>
                </select>
              </label>
              <div
                v-if="step.compensationType === 'EXTERNAL_TASK'"
                class="definition-compensation-external-task"
              >
                <label>
                  Topic
                  <a-input
                    v-model:value="step.compensationTopic"
                    class="definition-compensation-topic"
                    maxlength="64"
                  />
                </label>
                <label>
                  租约秒数
                  <a-input
                    v-model:value="step.compensationLeaseSeconds"
                    class="definition-compensation-lease-seconds"
                    inputmode="numeric"
                  />
                </label>
                <label>
                  最大尝试
                  <a-input
                    v-model:value="step.compensationExternalMaxAttempts"
                    class="definition-compensation-external-max-attempts"
                    inputmode="numeric"
                  />
                </label>
                <label>
                  结果 JSON 上限（bytes）
                  <a-input
                    v-model:value="step.compensationResultJsonLimitBytes"
                    class="definition-compensation-result-limit"
                    inputmode="numeric"
                  />
                </label>
              </div>
              <div
                v-else-if="step.compensationType === 'WEBHOOK'"
                class="definition-compensation-webhook"
              >
                <label>
                  HTTPS URL
                  <a-input
                    v-model:value="step.compensationUrl"
                    class="definition-compensation-webhook-url"
                    maxlength="1024"
                  />
                </label>
                <label>
                  Secret 引用
                  <a-input
                    v-model:value="step.compensationSecretRef"
                    class="definition-compensation-secret-ref"
                    maxlength="512"
                    placeholder="写入后仅显示 ********；留空清除"
                  />
                </label>
                <label>
                  超时秒数
                  <a-input
                    v-model:value="step.compensationTimeoutSeconds"
                    class="definition-compensation-timeout-seconds"
                    inputmode="numeric"
                  />
                </label>
                <label>
                  最大尝试
                  <a-input
                    v-model:value="step.compensationWebhookMaxAttempts"
                    class="definition-compensation-webhook-max-attempts"
                    inputmode="numeric"
                  />
                </label>
                <label>
                  基础退避秒数
                  <a-input
                    v-model:value="step.compensationBaseBackoffSeconds"
                    class="definition-compensation-backoff-seconds"
                    inputmode="numeric"
                  />
                </label>
              </div>
              <div v-else class="definition-compensation-subflow">
                <label>
                  目标流程定义 ID
                  <a-input
                    v-model:value="step.compensationSubflowDefinitionId"
                    class="definition-compensation-subflow-definition-id"
                    inputmode="numeric"
                    maxlength="19"
                  />
                </label>
                <label>
                  已发布版本
                  <a-input
                    v-model:value="step.compensationSubflowVersion"
                    class="definition-compensation-subflow-version"
                    inputmode="numeric"
                  />
                </label>
                <small>补偿子流程固定定义与版本，并复用深度、循环和租户边界检查。</small>
              </div>
            </section>
          </article>
          <a-button
            v-if="definitionCompletionSteps.length < 8"
            class="definition-completion-step-add"
            @click="addCompletionStep()"
          >
            <Plus :size="14" />添加完成步骤
          </a-button>
        </section>
        <details
          v-if="definitionApproverSource.kind === 'FIXED'"
          class="definition-compact-fallback"
        >
          <summary>紧凑审批顺序备用编辑</summary>
          <a-form-item label="顺序审批人" required>
            <div class="approver-sequence">
              <div v-for="(_, index) in definitionApprovers" :key="index" class="approver-step">
                <span>{{ index + 1 }}</span>
                <MemberPicker
                  v-model:value="definitionApprovers[index]"
                  :system-id="systemId"
                  :placeholder="`选择第 ${index + 1} 位审批人`"
                />
                <a-button :disabled="index === 0" aria-label="上移审批人" @click="moveDefinitionApprover(index, -1)">
                  <ArrowUp :size="14" />
                </a-button>
                <a-button :disabled="index === definitionApprovers.length - 1" aria-label="下移审批人" @click="moveDefinitionApprover(index, 1)">
                  <ArrowDown :size="14" />
                </a-button>
                <a-button danger :disabled="definitionApprovers.length === 1" aria-label="删除审批人" @click="removeDefinitionApprover(index)">
                  <Trash2 :size="14" />
                </a-button>
              </div>
              <a-button v-if="definitionApprovers.length < 10" @click="addDefinitionApprover">
                <Plus :size="14" />添加审批人
              </a-button>
            </div>
          </a-form-item>
        </details>
        <a-form-item label="自动触发">
          <label class="definition-trigger-toggle">
            <input v-model="definitionTriggerEnabled" type="checkbox" role="switch">
            发布后按事件或固定周期自动触发
          </label>
        </a-form-item>
        <template v-if="definitionTriggerEnabled">
          <a-form-item v-if="definitionTriggerEvent !== 'PERIODIC'" label="模块代码" required>
            <a-input
              v-model:value="definitionTriggerModuleCode"
              class="definition-trigger-module"
              maxlength="64"
              placeholder="例如 purchase_order"
            />
          </a-form-item>
          <a-form-item label="触发类型" required>
            <select v-model="definitionTriggerEvent" class="definition-trigger-event">
              <option
                v-for="option in triggerEventOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}（{{ option.value }}）
              </option>
            </select>
          </a-form-item>
          <template v-if="definitionTriggerEvent === 'PERIODIC'">
            <a-form-item label="UTC 开始时间" required>
              <a-input
                v-model:value="definitionTriggerStartAt"
                class="definition-trigger-start-at"
                placeholder="例如 2026-07-30T01:00:00Z"
              />
            </a-form-item>
            <a-form-item label="间隔分钟" required>
              <a-input
                v-model:value="definitionTriggerIntervalMinutes"
                class="definition-trigger-interval"
                inputmode="numeric"
                placeholder="1 到 525600"
              />
            </a-form-item>
            <div class="definition-trigger-fixed">
              请求人将在保存草稿时冻结为当前成员；误点火只执行一次并跳到下一个未来周期。
            </div>
          </template>
          <a-form-item v-if="definitionTriggerEvent !== 'PERIODIC'" label="优先级" required>
            <a-input
              v-model:value="definitionTriggerPriority"
              class="definition-trigger-priority"
              inputmode="numeric"
              placeholder="-1000 到 1000"
            />
          </a-form-item>
          <div v-if="definitionTriggerEvent !== 'PERIODIC'" class="definition-trigger-fixed">
            条件组合：ALL
          </div>
          <label v-if="definitionTriggerEvent !== 'PERIODIC'" class="definition-trigger-exclusive">
            <input v-model="definitionTriggerExclusive" type="checkbox" role="switch">
            独占匹配（关闭时允许并行触发）
          </label>
          <div v-if="definitionTriggerEvent !== 'PERIODIC'" class="definition-trigger-conditions">
            <header>
              <strong>ALL 条件</strong>
              <span>{{ definitionTriggerConditions.length }}/10</span>
            </header>
            <div
              v-for="(condition, index) in definitionTriggerConditions"
              :key="index"
              class="definition-trigger-condition"
            >
              <a-input
                v-model:value="condition.fieldCode"
                class="definition-trigger-field"
                maxlength="64"
                placeholder="字段代码"
              />
              <select
                class="definition-trigger-operator"
                :value="condition.operator"
                @change="selectTriggerConditionOperator(index, ($event.target as HTMLSelectElement).value)"
              >
                <option
                  v-for="operator in triggerConditionOperators"
                  :key="operator"
                  :value="operator"
                >
                  {{ operator }}
                </option>
              </select>
              <a-input
                v-if="triggerValueOperators.has(condition.operator)"
                v-model:value="condition.valueText"
                class="definition-trigger-value"
                placeholder="JSON，例如 100、true、&quot;urgent&quot;"
              />
              <span v-else class="definition-trigger-value-empty">无需值</span>
              <a-button class="definition-trigger-remove" danger @click="removeTriggerCondition(index)">
                删除
              </a-button>
            </div>
            <a-button
              v-if="definitionTriggerConditions.length < 10"
              class="definition-trigger-add"
              @click="addTriggerCondition"
            >
              <Plus :size="14" />添加条件
            </a-button>
          </div>
        </template>
        <a-form-item label="终态 STATUS 映射">
          <label class="definition-status-mapping-toggle">
            <input v-model="definitionStatusMappingEnabled" type="checkbox" role="switch">
            审批终态写入记录 STATUS 字段
          </label>
        </a-form-item>
        <template v-if="definitionStatusMappingEnabled">
          <a-form-item label="STATUS 字段代码" required>
            <a-input
              v-model:value="definitionStatusFieldCode"
              class="definition-status-field-code"
              maxlength="64"
              placeholder="例如 approval_status"
            />
          </a-form-item>
          <a-form-item label="APPROVED 选项 ID" required>
            <a-input
              v-model:value="definitionStatusApprovedValue"
              class="definition-status-approved-value"
              inputmode="numeric"
              maxlength="19"
              placeholder="例如 101"
            />
          </a-form-item>
          <a-form-item label="REJECTED 选项 ID" required>
            <a-input
              v-model:value="definitionStatusRejectedValue"
              class="definition-status-rejected-value"
              inputmode="numeric"
              maxlength="19"
              placeholder="例如 102"
            />
          </a-form-item>
          <a-form-item label="WITHDRAWN 选项 ID" required>
            <a-input
              v-model:value="definitionStatusWithdrawnValue"
              class="definition-status-withdrawn-value"
              inputmode="numeric"
              maxlength="19"
              placeholder="例如 103"
            />
          </a-form-item>
          <a-form-item label="TERMINATED 选项 ID" required>
            <a-input
              v-model:value="definitionStatusTerminatedValue"
              class="definition-status-terminated-value"
              inputmode="numeric"
              maxlength="19"
              placeholder="例如 104"
            />
          </a-form-item>
        </template>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="templateManagerOpen"
      class="flow-decision-template-manager"
      title="审批意见模板"
      :footer="null"
      width="760px"
    >
      <a-alert
        v-if="templateError"
        class="flow-decision-template-error"
        type="error"
        show-icon
        :message="templateError"
      />
      <section class="flow-decision-template-editor">
        <header>
          <strong>{{ templateEditingId ? '创建模板新版本' : '新建模板' }}</strong>
          <a-button v-if="templateEditingId" @click="newDecisionTemplate">取消编辑</a-button>
        </header>
        <label>
          模板名称
          <a-input
            v-model:value="templateName"
            class="flow-decision-template-name"
            maxlength="80"
          />
        </label>
        <label>
          意见正文
          <a-textarea
            v-model:value="templateBody"
            class="flow-decision-template-body"
            :rows="4"
            maxlength="1000"
          />
        </label>
        <a-button
          class="flow-decision-template-save"
          type="primary"
          :loading="mutation === 'decision-template:save'"
          @click="saveDecisionTemplate"
        >
          {{ templateEditingId ? '保存新版本' : '创建模板' }}
        </a-button>
      </section>
      <a-spin :spinning="templateLoading">
        <div class="flow-decision-template-list">
          <article
            v-for="template in templateItems"
            :key="`${template.templateId}:${template.version}`"
            class="flow-decision-template-item"
          >
            <header>
              <strong>{{ template.name }}</strong>
              <code>#{{ template.templateId }} · v{{ template.version }}</code>
              <a-tag :color="template.status === 'ACTIVE' ? 'green' : 'default'">
                {{ template.status }}
              </a-tag>
            </header>
            <p>{{ template.body }}</p>
            <small>更新于 {{ time(template.updatedAt) }}</small>
            <div>
              <a-button
                class="flow-decision-template-edit"
                @click="editDecisionTemplate(template)"
              >
                编辑新版本
              </a-button>
              <a-button
                v-if="template.status === 'ACTIVE'"
                class="flow-decision-template-deactivate"
                danger
                :loading="mutation === `decision-template:${template.templateId}`"
                @click="setDecisionTemplateActive(template, false)"
              >
                停用
              </a-button>
              <a-button
                v-else
                class="flow-decision-template-activate"
                :loading="mutation === `decision-template:${template.templateId}`"
                @click="setDecisionTemplateActive(template, true)"
              >
                启用
              </a-button>
            </div>
          </article>
          <a-empty v-if="!templateLoading && !templateItems.length" description="暂无意见模板" />
        </div>
      </a-spin>
      <a-pagination
        v-if="templateTotal > 20"
        :current="templatePage"
        :page-size="20"
        :total="templateTotal"
        @change="templatePage = $event; loadTemplateManager()"
      />
    </a-modal>

    <a-modal
      v-model:open="startOpen"
      title="发起审批"
      :confirm-loading="mutation === 'start'"
      :ok-text="startReceipt ? '关闭' : '发起'"
      @ok="confirmStartModal"
    >
      <a-spin v-if="!startReceipt" :spinning="loadingStartableDefinitions">
        <p v-if="loadingStartableDefinitions" class="start-catalog-loading">
          正在加载可发起流程…
        </p>
        <a-alert
          v-else-if="startableDefinitionsError"
          class="start-catalog-error"
          type="error"
          show-icon
          :message="startableDefinitionsError"
        >
          <a-button class="start-catalog-retry" size="small" @click="loadStartableDefinitions">
            重试
          </a-button>
        </a-alert>
        <a-empty
          v-else-if="!loadingStartableDefinitions && !startableDefinitions.length"
          class="start-catalog-empty"
          description="暂无可发起的已发布流程"
        />
        <a-form v-else layout="vertical">
          <a-form-item label="流程" required>
            <select v-model="startDefinitionId" class="start-definition-id">
              <option value="" disabled>请选择已发布流程</option>
              <option
                v-for="definition in startableDefinitions"
                :key="definition.definitionId"
                :value="definition.definitionId"
              >
                {{ definition.name }} · v{{ definition.latestVersion }} · {{ definition.definitionId }}
              </option>
            </select>
          </a-form-item>
          <a-form-item label="流程版本">
            <a-input v-model:value="startVersion" class="start-version" inputmode="numeric" placeholder="留空则使用最新发布版本" />
          </a-form-item>
          <a-form-item label="业务标识" required>
            <a-input v-model:value="startBusinessKey" class="start-business-key" maxlength="160" placeholder="例如：contract:10001" />
          </a-form-item>
          <a-form-item label="绑定记录模块代码">
            <a-input
              v-model:value="startBindingModuleCode"
              class="start-binding-module"
              maxlength="64"
              placeholder="可选；例如 purchase_order，需与记录 ID 同时填写"
            />
          </a-form-item>
          <a-form-item label="绑定记录 ID">
            <a-input
              v-model:value="startBindingRecordId"
              class="start-binding-record"
              inputmode="numeric"
              placeholder="可选；需与模块代码同时填写"
            />
          </a-form-item>
          <a-form-item label="条件网关字段值">
            <textarea
              v-model="startRouteValues"
              class="start-route-values"
              rows="4"
              placeholder="可选 JSON 对象，例如 {&quot;amount&quot;: 100}"
            />
          </a-form-item>
          <a-alert
            v-if="startError"
            class="start-error"
            type="error"
            show-icon
            :message="startError"
          />
        </a-form>
      </a-spin>
      <div v-else class="start-receipt">
        <a-alert type="success" show-icon message="审批已发起" />
        <dl class="detail-list">
          <div><dt>实例 ID</dt><dd>{{ startReceipt.instanceId }}</dd></div>
          <div><dt>状态</dt><dd>{{ statusLabel(startReceipt.status) }}</dd></div>
          <div><dt>流程版本</dt><dd>v{{ startReceipt.definitionVersion }}</dd></div>
          <div><dt>业务标识</dt><dd>{{ startReceipt.businessKey }}</dd></div>
          <template v-if="startReceipt.recordBinding">
            <div><dt>绑定模块</dt><dd>{{ startReceipt.recordBinding.moduleCode }}</dd></div>
            <div><dt>绑定记录 ID</dt><dd>{{ startReceipt.recordBinding.recordId }}</dd></div>
          </template>
        </dl>
        <a-alert
          v-if="startRefreshWarning"
          class="start-refresh-warning"
          type="warning"
          show-icon
          :message="startRefreshWarning"
        />
        <a-button class="start-another" @click="startAnother">发起另一条</a-button>
      </div>
    </a-modal>

    <a-modal
      v-model:open="delegationOpen"
      title="新建审批委托"
      :confirm-loading="mutation === 'delegation:create'"
      @ok="createDelegation"
    >
      <a-form layout="vertical">
        <a-form-item v-if="canManageDelegations" label="委托人" required>
          <MemberPicker
            v-model:value="delegationDelegatorMemberId"
            class="delegation-delegator"
            :system-id="systemId"
            placeholder="选择委托人"
          />
        </a-form-item>
        <a-form-item label="代理人" required>
          <MemberPicker
            v-model:value="delegationDelegateMemberId"
            class="delegation-delegate"
            :system-id="systemId"
            placeholder="选择代理审批成员"
          />
        </a-form-item>
        <a-form-item label="开始时间" required>
          <input
            v-model="delegationStartsAt"
            class="delegation-starts-at"
            type="datetime-local"
          >
        </a-form-item>
        <a-form-item label="结束时间" required>
          <input
            v-model="delegationEndsAt"
            class="delegation-ends-at"
            type="datetime-local"
          >
        </a-form-item>
        <a-form-item label="限定流程定义 ID">
          <a-input
            v-model:value="delegationDefinitionId"
            class="delegation-definition-id"
            inputmode="numeric"
            placeholder="留空表示全部流程定义"
          />
        </a-form-item>
        <a-alert
          v-if="delegationError"
          class="delegation-modal-error"
          type="error"
          show-icon
          :message="delegationError"
        />
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="decisionOpen"
      :title="decisionAction === 'approve' ? '通过审批' : '驳回审批'"
      :confirm-loading="mutation.startsWith('decide:')"
      :ok-button-props="{ danger: decisionAction === 'reject' }"
      @ok="decide"
    >
      <a-form layout="vertical">
        <a-form-item label="审批实例 ID" required>
          <a-input v-model:value="decisionInstanceId" inputmode="numeric" />
        </a-form-item>
        <a-form-item v-if="decisionBranches.length" label="并行分支" required>
          <select v-model="decisionBranchCode" class="decision-branch-code">
            <option
              v-for="branch in decisionBranches"
              :key="branch.code"
              :value="branch.code"
            >
              {{ branch.name }} · {{ approvalModeLabel(branch.approvalMode) }}
            </option>
          </select>
        </a-form-item>
        <a-form-item
          v-if="decisionRepresentationOptions.length"
          label="审批身份"
          :required="!decisionDirectlyEligible"
        >
          <select
            v-model="decisionRepresentedMemberId"
            class="decision-represented-member-id"
          >
            <option v-if="decisionDirectlyEligible" value="">以本人身份审批</option>
            <option
              v-for="memberId in decisionRepresentationOptions"
              :key="memberId"
              :value="memberId"
            >
              代表成员 {{ memberId }} 审批
            </option>
          </select>
          <small>代理审批会占用被代表成员的原始参与人席位。</small>
        </a-form-item>
        <a-form-item label="审批意见模板">
          <select
            v-model="decisionSelectedTemplateId"
            class="decision-comment-template"
            @change="decisionComment = ''"
          >
            <option value="">不使用模板</option>
            <option
              v-for="template in decisionTemplates"
              :key="template.templateId"
              :value="template.templateId"
            >
              {{ template.name }} · v{{ template.version }}
            </option>
          </select>
          <small v-if="decisionSelectedTemplateId">
            {{
              decisionTemplates.find(item => item.templateId === decisionSelectedTemplateId)?.body
            }}
          </small>
        </a-form-item>
        <a-form-item
          :label="decisionAction === 'approve' ? '审批意见' : '驳回原因'"
          :required="decisionCommentRequired"
        >
          <a-textarea
            v-model:value="decisionComment"
            :rows="4"
            maxlength="1000"
            :disabled="Boolean(decisionSelectedTemplateId)"
          />
          <small class="decision-comment-requirement">
            {{ decisionCommentRequired
              ? `必填，至少 ${decisionCommentPolicy.minimumLength} 个字符`
              : '选填，最多 500 个字符' }}
          </small>
        </a-form-item>
        <section
          v-if="decisionEvidencePolicy"
          class="decision-evidence-editor"
        >
          <header>
            <strong>审批证据</strong>
            <small>
              附件 {{ decisionEvidencePolicy.minimumAttachments }}–
              {{ decisionEvidencePolicy.maximumAttachments }} 个 ·
              签名 {{ decisionEvidencePolicy.signatureMode }}
            </small>
          </header>
          <div
            v-for="(fileId, index) in decisionAttachmentFileIds"
            :key="index"
            class="decision-evidence-attachment"
          >
            <a-input
              :value="fileId"
              class="decision-evidence-attachment-id"
              inputmode="numeric"
              placeholder="已上传文件资产 ID"
              @update:value="updateDecisionAttachment(index, $event)"
            />
            <a-button
              class="decision-evidence-attachment-resolve"
              :loading="decisionEvidenceLoading === fileId"
              @click="resolveDecisionAsset(fileId)"
            >
              解析文件
            </a-button>
            <a-button
              class="decision-evidence-attachment-remove"
              danger
              @click="removeDecisionAttachment(index)"
            >
              删除
            </a-button>
            <small v-if="decisionAttachmentAssets.get(fileId.trim())">
              {{ decisionAttachmentAssets.get(fileId.trim())!.originalName }} ·
              {{ decisionAttachmentAssets.get(fileId.trim())!.mediaType }} ·
              {{ decisionAttachmentAssets.get(fileId.trim())!.size }} bytes
            </small>
          </div>
          <a-button
            class="decision-evidence-attachment-add"
            :disabled="decisionAttachmentFileIds.length >= decisionEvidencePolicy.maximumAttachments"
            @click="addDecisionAttachment"
          >
            添加现有文件
          </a-button>
          <label>
            签名方式
            <select
              v-model="decisionSignatureKind"
              class="decision-evidence-signature-kind"
              :disabled="decisionEvidencePolicy.signatureMode === 'NONE'"
            >
              <option value="NONE">不签名</option>
              <option value="TYPED">输入签名</option>
              <option value="FILE">签名文件</option>
            </select>
          </label>
          <a-input
            v-if="decisionSignatureKind === 'TYPED'"
            v-model:value="decisionTypedSignature"
            class="decision-evidence-typed-signature"
            maxlength="120"
            placeholder="输入签名显示值"
          />
          <div
            v-if="decisionSignatureKind === 'FILE'"
            class="decision-evidence-signature-file"
          >
            <a-input
              v-model:value="decisionSignatureFileId"
              class="decision-evidence-signature-file-id"
              inputmode="numeric"
              placeholder="签名文件资产 ID"
            />
            <a-button
              class="decision-evidence-signature-resolve"
              :loading="decisionEvidenceLoading === 'signature'"
              @click="resolveDecisionAsset(decisionSignatureFileId, true)"
            >
              解析签名文件
            </a-button>
            <small v-if="decisionSignatureAsset">
              {{ decisionSignatureAsset.originalName }} · {{ decisionSignatureAsset.mediaType }}
            </small>
          </div>
        </section>
        <a-alert
          v-if="decisionError"
          class="decision-error"
          type="error"
          show-icon
          :message="decisionError"
        />
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="withdrawOpen"
      title="撤回审批"
      :confirm-loading="mutation.startsWith('withdraw:')"
      :ok-button-props="{ danger: true }"
      ok-text="确认撤回"
      @ok="withdraw"
    >
      <a-form layout="vertical">
        <a-form-item label="撤回原因" required>
          <a-textarea
            v-model:value="withdrawReason"
            class="withdraw-reason"
            :rows="4"
            maxlength="500"
            placeholder="请输入撤回原因"
          />
        </a-form-item>
        <a-alert v-if="withdrawError" type="error" show-icon :message="withdrawError" />
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="terminateOpen"
      title="终止审批"
      :confirm-loading="mutation.startsWith('terminate:')"
      :ok-button-props="{ danger: true }"
      ok-text="确认终止"
      @ok="terminate"
    >
      <a-form layout="vertical">
        <a-form-item label="终止原因" required>
          <a-textarea
            v-model:value="terminateReason"
            class="terminate-reason"
            :rows="4"
            maxlength="500"
            placeholder="请输入终止原因"
          />
        </a-form-item>
        <a-alert v-if="terminateError" type="error" show-icon :message="terminateError" />
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="urgeOpen"
      title="催办审批"
      :confirm-loading="mutation.startsWith('urge:')"
      ok-text="发送催办"
      @ok="urge"
    >
      <a-form layout="vertical">
        <a-form-item label="催办消息">
          <a-textarea
            v-model:value="urgeMessage"
            class="urge-message"
            :rows="4"
            maxlength="500"
            placeholder="可选；将随催办消息发送给当前审批人"
          />
        </a-form-item>
        <a-alert v-if="urgeError" type="error" show-icon :message="urgeError" />
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="transferOpen"
      title="转交当前审批"
      :confirm-loading="mutation.startsWith('transfer:')"
      ok-text="确认转交"
      @ok="transfer"
    >
      <a-form class="assignment-form" layout="vertical">
        <a-form-item label="目标成员" required>
          <MemberPicker
            class="assignment-member-picker"
            :system-id="systemId"
            :value="transferTargetMemberId"
            placeholder="选择新的当前审批人"
            @update:value="transferTargetMemberId = $event"
          />
        </a-form-item>
        <a-form-item label="转交原因" required>
          <a-textarea
            v-model:value="transferReason"
            class="transfer-reason"
            :rows="4"
            maxlength="500"
            placeholder="请输入转交原因"
          />
        </a-form-item>
        <a-alert v-if="transferError" type="error" show-icon :message="transferError" />
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="addSignOpen"
      title="添加审批人"
      :confirm-loading="mutation.startsWith('add-sign:')"
      ok-text="确认加签"
      @ok="addSign"
    >
      <a-form class="assignment-form" layout="vertical">
        <a-form-item label="目标成员" required>
          <MemberPicker
            class="assignment-member-picker"
            :system-id="systemId"
            :value="addSignTargetMemberId"
            placeholder="选择要加入审批序列的成员"
            @update:value="addSignTargetMemberId = $event"
          />
        </a-form-item>
        <a-form-item label="加签位置" required>
          <select
            class="add-sign-position"
            :value="addSignPosition"
            @change="selectAddSignPosition(($event.target as HTMLSelectElement).value)"
          >
            <option value="BEFORE">当前步骤之前</option>
            <option value="AFTER">当前步骤之后</option>
          </select>
        </a-form-item>
        <a-form-item label="加签原因" required>
          <a-textarea
            v-model:value="addSignReason"
            class="add-sign-reason"
            :rows="4"
            maxlength="500"
            placeholder="请输入加签原因"
          />
        </a-form-item>
        <a-alert v-if="addSignError" type="error" show-icon :message="addSignError" />
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="reduceSignOpen"
      title="移除未来审批步骤"
      :confirm-loading="mutation.startsWith('reduce-sign:')"
      ok-text="确认减签"
      @ok="reduceSign"
    >
      <a-form class="assignment-form" layout="vertical">
        <a-form-item label="未来审批步骤" required>
          <select
            class="reduce-sign-target"
            :value="reduceSignTargetStepIndex ?? ''"
            @change="selectReduceSignTarget(($event.target as HTMLSelectElement).value)"
          >
            <option
              v-for="candidate in reduceSignCandidates"
              :key="candidate.stepIndex"
              :value="candidate.stepIndex"
            >
              步骤 {{ candidate.stepIndex + 1 }}（索引 {{ candidate.stepIndex }}）· 成员 {{ candidate.memberId }}
            </option>
          </select>
        </a-form-item>
        <a-form-item label="减签原因" required>
          <a-textarea
            v-model:value="reduceSignReason"
            class="reduce-sign-reason"
            :rows="4"
            maxlength="500"
            placeholder="请输入减签原因"
          />
        </a-form-item>
        <a-alert v-if="reduceSignError" type="error" show-icon :message="reduceSignError" />
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="returnOpen"
      title="退回上一审批步骤"
      :confirm-loading="mutation.startsWith('return:')"
      ok-text="确认退回"
      @ok="returnInstance"
    >
      <a-form layout="vertical">
        <a-form-item label="退回原因" required>
          <a-textarea
            v-model:value="returnReason"
            class="return-reason"
            :rows="4"
            maxlength="500"
            placeholder="请输入退回原因"
          />
        </a-form-item>
        <a-alert v-if="returnError" type="error" show-icon :message="returnError" />
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="cancelClaimOpen"
      title="取消认领"
      :confirm-loading="mutation.startsWith('cancel-claim:')"
      ok-text="确认释放任务"
      @ok="cancelClaim"
    >
      <a-form layout="vertical">
        <a-form-item label="取消原因" required>
          <a-textarea
            v-model:value="cancelClaimReason"
            class="cancel-claim-reason"
            :rows="4"
            maxlength="500"
            placeholder="请输入取消认领原因"
          />
        </a-form-item>
        <a-alert v-if="cancelClaimError" type="error" show-icon :message="cancelClaimError" />
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="claimOpen"
      title="认领审批任务"
      :confirm-loading="mutation.startsWith('claim:')"
      ok-text="确认认领"
      @ok="claim"
    >
      <a-form layout="vertical">
        <a-form-item label="认领备注">
          <a-textarea
            v-model:value="claimComment"
            class="claim-comment"
            :rows="4"
            maxlength="500"
            placeholder="可选；说明本次认领"
          />
        </a-form-item>
        <a-alert v-if="claimError" type="error" show-icon :message="claimError" />
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="copyOpen"
      title="抄送审批实例"
      :confirm-loading="mutation.startsWith('copy:')"
      ok-text="确认抄送"
      @ok="copyInstance"
    >
      <a-form layout="vertical">
        <a-form-item label="抄送成员" required>
          <MemberPicker
            class="copy-member-picker"
            :system-id="systemId"
            :value="copyTargetMemberId"
            placeholder="选择要抄送的活跃成员"
            @update:value="copyTargetMemberId = $event"
          />
        </a-form-item>
        <a-form-item label="抄送消息">
          <a-textarea
            v-model:value="copyMessage"
            class="copy-message"
            :rows="4"
            maxlength="500"
            placeholder="可选；随抄送通知发送"
          />
        </a-form-item>
        <a-alert v-if="copyError" type="error" show-icon :message="copyError" />
      </a-form>
    </a-modal>

    <a-drawer
      v-model:open="extensionEditorOpen"
      :title="`${extensionDefinition?.name ?? '流程'} · 扩展节点草稿`"
      width="min(1200px, 100vw)"
      destroy-on-close
    >
      <FlowExtensionEditor
        v-if="extensionDefinition"
        :system-id="systemId"
        :definition-id="extensionDefinition.definitionId"
        :revision="extensionDefinition.revision"
        @saved="loadDefinitions"
      />
    </a-drawer>

    <a-drawer
      v-model:open="detailOpen"
      title="审批实例详情"
      width="min(560px, 100vw)"
    >
      <a-spin :spinning="detailLoading">
        <template v-if="selectedInstance">
          <div v-if="canWithdrawInstance(selectedInstance)" class="detail-actions">
            <a-button
              class="flow-withdraw"
              :disabled="Boolean(mutation)"
              @click="openWithdraw(selectedInstance)"
            >
              <Undo2 :size="15" />撤回审批
            </a-button>
          </div>
          <div v-if="canTerminateInstance(selectedInstance)" class="detail-actions">
            <a-button
              class="flow-terminate"
              danger
              :disabled="Boolean(mutation)"
              @click="openTerminate(selectedInstance)"
            >
              <Ban :size="15" />终止审批
            </a-button>
          </div>
          <div v-if="canUrgeInstance(selectedInstance)" class="detail-actions">
            <a-button
              class="flow-urge"
              :disabled="Boolean(mutation)"
              @click="openUrge(selectedInstance)"
            >
              <BellRing :size="15" />催办审批
            </a-button>
          </div>
          <div v-if="canTransferInstance(selectedInstance)" class="detail-actions">
            <a-button
              class="flow-transfer"
              :disabled="Boolean(mutation)"
              @click="openTransfer(selectedInstance)"
            >
              <ArrowRightLeft :size="15" />转交审批
            </a-button>
          </div>
          <div v-if="canAddSignInstance(selectedInstance)" class="detail-actions">
            <a-button
              class="flow-add-sign"
              :disabled="Boolean(mutation)"
              @click="openAddSign(selectedInstance)"
            >
              <UserPlus :size="15" />添加审批人
            </a-button>
          </div>
          <div v-if="canReduceSignInstance(selectedInstance)" class="detail-actions">
            <a-button
              class="flow-reduce-sign"
              :disabled="Boolean(mutation)"
              @click="openReduceSign(selectedInstance)"
            >
              <UserMinus :size="15" />移除未来审批步骤
            </a-button>
          </div>
          <div v-if="canReturnInstance(selectedInstance)" class="detail-actions">
            <a-button
              class="flow-return"
              :disabled="Boolean(mutation) || selectedInstance.currentStepIndex === 0"
              @click="openReturn(selectedInstance)"
            >
              <Reply :size="15" />退回上一审批步骤
            </a-button>
          </div>
          <div v-if="canCancelClaimInstance(selectedInstance)" class="detail-actions">
            <a-button
              class="flow-cancel-claim"
              :disabled="Boolean(mutation)"
              @click="openCancelClaim(selectedInstance)"
            >
              <X :size="15" />取消认领
            </a-button>
          </div>
          <div v-if="canCopy" class="detail-actions">
            <a-button
              class="flow-copy"
              :disabled="Boolean(mutation)"
              @click="openCopy(selectedInstance)"
            >
              <Copy :size="15" />抄送成员
            </a-button>
          </div>
          <dl class="detail-list">
            <div><dt>实例 ID</dt><dd>{{ selectedInstance.instanceId }}</dd></div>
            <div><dt>业务标识</dt><dd>{{ selectedInstance.businessKey }}</dd></div>
            <div><dt>状态</dt><dd><a-tag :color="statusColor(selectedInstance.status)">{{ statusLabel(selectedInstance.status) }}</a-tag></dd></div>
            <div><dt>流程定义</dt><dd>{{ selectedInstance.definitionId }} / v{{ selectedInstance.definitionVersion }}</dd></div>
            <div><dt>发起人</dt><dd>{{ selectedInstance.requesterId }}</dd></div>
            <div><dt>审批方式</dt><dd>{{ approvalModeLabel(selectedInstance.approvalMode) }}</dd></div>
            <div v-if="selectedInstance.requiredApprovals">
              <dt>需同意人数</dt><dd>{{ selectedInstance.requiredApprovals }}</dd>
            </div>
            <div><dt>当前处理人</dt><dd>{{ selectedInstance.activeApproverIds?.join('、') || selectedInstance.approverId }}</dd></div>
            <div><dt>已同意</dt><dd>{{ selectedInstance.approvedApproverIds?.join('、') || '—' }}</dd></div>
            <div><dt>已拒绝</dt><dd>{{ selectedInstance.rejectedApproverIds?.join('、') || '—' }}</dd></div>
            <div v-if="selectedInstance.currentStageCode">
              <dt>当前阶段</dt>
              <dd>{{ (selectedInstance.currentStageIndex ?? 0) + 1 }} · {{ selectedInstance.currentStageCode }}</dd>
            </div>
            <div><dt>当前步骤</dt><dd>{{ selectedInstance.currentStepIndex + 1 }}</dd></div>
            <div><dt>认领状态</dt><dd>{{ claimStateLabel(selectedInstance.claimState) }}</dd></div>
            <div v-if="selectedInstance.recordBinding">
              <dt>绑定模块</dt><dd>{{ selectedInstance.recordBinding.moduleCode }}</dd>
            </div>
            <div v-if="selectedInstance.recordBinding">
              <dt>绑定记录 ID</dt><dd>{{ selectedInstance.recordBinding.recordId }}</dd>
            </div>
            <div><dt>发起时间</dt><dd>{{ time(selectedInstance.startedAt) }}</dd></div>
            <div><dt>完成时间</dt><dd>{{ time(selectedInstance.completedAt) }}</dd></div>
          </dl>
          <FlowNodeRuntimePanel
            v-if="canRead || canDecide"
            :system-id="systemId"
            :instance-id="selectedInstance.instanceId"
            :can-decide="canDecide"
          />
          <section
            v-if="selectedInstance.stages?.length"
            class="instance-approval-stages"
          >
            <h3>审批阶段执行</h3>
            <article
              v-for="stage in selectedInstance.stages"
              :key="stage.code"
              class="instance-approval-stage"
            >
              <header>
                <strong>{{ stage.stageIndex + 1 }}. {{ stage.name }}</strong>
                <code>{{ stage.code }}</code>
                <a-tag :color="stageStatusColor(stage.status)">
                  {{ stage.status }}
                </a-tag>
              </header>
              <p>
                {{ approvalModeLabel(stage.approvalMode) }}
                · 需同意 {{ stage.requiredApprovals }} 人
                · 参与人 {{ stage.approverIds.join('、') || '待激活解析' }}
              </p>
              <p>
                实际处理人 {{ stage.actualHandlerIds.join('、') || '—' }}
                · 开始 {{ time(stage.startedAt) }}
                · 完成 {{ time(stage.completedAt) }}
              </p>
              <p v-if="stage.deadline">
                {{ deadlinePolicyLabel(stage.deadline.policy) }}
                · 截止 {{ time(stage.deadline.dueAt) }}
              </p>
              <p class="instance-stage-decision-evidence">
                审批证据：{{ decisionEvidencePolicyLabel(stage.decisionEvidencePolicy) }}
              </p>
            </article>
          </section>
          <section
            v-if="selectedInstance.deadline"
            class="instance-deadline-runtime"
          >
            <h3>审批时限</h3>
            <p>{{ deadlinePolicyLabel(selectedInstance.deadline.policy) }}</p>
            <p>
              截止：{{ time(selectedInstance.deadline.dueAt) }}
              <template v-if="selectedInstance.deadline.remindAt">
                · 提醒：{{ time(selectedInstance.deadline.remindAt) }}
              </template>
              <template v-if="selectedInstance.deadline.overdue">· 已逾期</template>
            </p>
          </section>
          <section class="instance-decision-comment-runtime">
            <h3>审批意见规则</h3>
            <p>{{ decisionCommentPolicyLabel(selectedInstance.decisionCommentPolicy) }}</p>
          </section>
          <section class="instance-decision-evidence-runtime">
            <h3>审批证据规则</h3>
            <p>{{ decisionEvidencePolicyLabel(selectedInstance.decisionEvidencePolicy) }}</p>
          </section>
          <section
            v-if="selectedInstance.completionPhase
              || selectedInstance.completionExecutions?.length
              || selectedInstance.compensationExecutions?.length"
            class="instance-completion-runtime"
          >
            <h3>审批完成后执行</h3>
            <p>
              阶段 {{ selectedInstance.completionPhase ?? '—' }}
              · 失败策略 {{ completionFailurePolicyLabel(
                selectedInstance.completionFailurePolicy,
              ) }}
              <template v-if="resolvedActiveCompletionOrdinals(selectedInstance).length">
                · 活跃顺序 {{ resolvedActiveCompletionOrdinals(selectedInstance)
                  .map(ordinal => ordinal + 1).join('、') }}
                · 兼容游标 {{ (selectedInstance.activeCompletionOrdinal
                  ?? resolvedActiveCompletionOrdinals(selectedInstance)[0]!) + 1 }}
              </template>
            </p>
            <div
              v-if="selectedInstance.completionExecutions?.length"
              class="instance-completion-stage-list"
            >
              <article
                v-for="(stage, stageIndex) in groupedCompletionStages(
                  selectedInstance.completionExecutions,
                )"
                :key="stage[0]!.parallelGroup ?? stage[0]!.executionId"
                class="instance-completion-stage"
              >
                <strong>
                  阶段 {{ stageIndex + 1 }} ·
                  {{ stage[0]!.parallelGroup
                    ? `并行组 ${stage[0]!.parallelGroup}`
                    : '顺序步骤' }}
                </strong>
                <span
                  v-for="member in stage"
                  :key="member.executionId"
                  class="instance-completion-stage-member"
                >
                  {{ member.ordinal + 1 }}. {{ member.name }}：{{ member.status }}
                  <template v-if="resolvedActiveCompletionOrdinals(selectedInstance)
                    .includes(member.ordinal)">
                    （活跃）
                  </template>
                </span>
              </article>
            </div>
            <article
              v-for="execution in selectedInstance.completionExecutions ?? []"
              :key="execution.executionId"
              class="instance-completion-execution"
            >
              <header>
                <strong>{{ execution.ordinal + 1 }}. {{ execution.name }}</strong>
                <code>{{ execution.code }}</code>
                <a-tag>{{ execution.type }} · {{ execution.status }}</a-tag>
                <a-tag v-if="execution.parallelGroup" color="purple">
                  并行组 {{ execution.parallelGroup }}
                </a-tag>
              </header>
              <p>
                执行 {{ execution.executionId }}
                · 尝试 {{ execution.attemptCount }}
                <template v-if="execution.type !== 'SUBFLOW'">
                  /{{ execution.maxAttempts }}
                </template>
                · 可用 {{ time(execution.availableAt) }}
              </p>
              <p v-if="execution.externalTask">
                Topic {{ execution.externalTask.topic }}
                · 租约 {{ execution.externalTask.leaseSeconds }} 秒
                · 结果上限 {{ execution.externalTask.resultJsonLimitBytes }} bytes
              </p>
              <p v-if="execution.webhook">
                Webhook {{ execution.webhook.url }}
                · Secret {{ execution.webhook.secretConfigured ? '已配置' : '未配置' }}
                · 超时 {{ execution.webhook.timeoutSeconds }} 秒
                · 退避 {{ execution.webhook.baseBackoffSeconds }} 秒
              </p>
              <p v-if="execution.subflow" class="instance-completion-subflow-target">
                子流程目标：定义 {{ execution.subflow.definitionId }}
                / v{{ execution.subflow.version }}
              </p>
              <ol
                v-if="execution.subflowRuns?.length"
                class="instance-completion-subflow-runs"
              >
                <li
                  v-for="run in execution.subflowRuns"
                  :key="`${run.childInstanceId}:${run.attempt}`"
                >
                  尝试 {{ run.attempt }} · 子实例 {{ run.childInstanceId }}
                  · 定义 {{ run.targetDefinitionId }} / v{{ run.targetVersion }}
                  · {{ run.childStatus }}
                  · 启动 {{ time(run.launchedAt) }}
                  <template v-if="run.terminalAt">
                    · 终态 {{ time(run.terminalAt) }}
                  </template>
                  <a-button
                    class="instance-completion-child-link"
                    :loading="mutation === `child-detail:${run.childInstanceId}`"
                    @click="openChildInstance(run.childInstanceId)"
                  >
                    查看子实例
                  </a-button>
                </li>
              </ol>
              <p v-if="execution.failure" class="instance-completion-failure">
                失败 {{ execution.failure.code }}：{{ execution.failure.message }}
                · {{ execution.failure.retryable ? '可重试' : '不可重试' }}
              </p>
              <pre v-if="execution.result" class="instance-completion-result">{{
                JSON.stringify(execution.result, null, 2)
              }}</pre>
              <ol
                v-if="execution.attempts?.length"
                class="instance-completion-attempts"
              >
                <li
                  v-for="attempt in execution.attempts"
                  :key="attempt.attemptNumber"
                >
                  尝试 {{ attempt.attemptNumber }} · {{ attempt.status }}
                  <template v-if="attempt.httpStatus">
                    · HTTP {{ attempt.httpStatus }}
                  </template>
                  <template v-if="attempt.durationMs !== null
                    && attempt.durationMs !== undefined">
                    · {{ attempt.durationMs }} ms
                  </template>
                  <template v-if="attempt.responseSha256">
                    · 响应 SHA-256 {{ attempt.responseSha256 }}
                  </template>
                  <template v-if="attempt.failureCode">
                    · {{ attempt.failureCode }}：{{ attempt.failureMessage }}
                  </template>
                  · {{ time(attempt.startedAt) }} → {{ time(attempt.completedAt) }}
                </li>
              </ol>
              <a-button
                v-if="canManageDefinitions
                  && execution.status === 'FAILED'
                  && selectedInstance.completionPhase !== 'COMPENSATING'"
                class="instance-completion-retry"
                :loading="mutation === `completion-retry:${execution.executionId}`"
                @click="retryCompletionExecution(selectedInstance, execution)"
              >
                重试此步骤
              </a-button>
            </article>
          </section>
          <section
            v-if="selectedInstance.compensationExecutions?.length"
            class="instance-compensation-runtime"
          >
            <h3>逆序补偿执行</h3>
            <p>
              阶段 {{ selectedInstance.completionPhase ?? '—' }}
              · 共 {{ selectedInstance.compensationExecutions.length }} 个补偿成员
            </p>
            <article
              v-for="execution in orderedCompensationExecutions(selectedInstance)"
              :key="execution.compensationExecutionId"
              class="instance-compensation-execution"
            >
              <header>
                <strong>原步骤 {{ execution.originalOrdinal + 1 }}</strong>
                <code>{{ execution.originalExecutionId }}</code>
                <a-tag>{{ execution.type }} · {{ execution.status }}</a-tag>
              </header>
              <p>
                补偿执行 {{ execution.compensationExecutionId }}
                · 尝试 {{ execution.attemptCount }}
                <template v-if="execution.type !== 'SUBFLOW'">
                  /{{ execution.maxAttempts }}
                </template>
                · 可用 {{ time(execution.availableAt) }}
              </p>
              <p v-if="execution.externalTask">
                Topic {{ execution.externalTask.topic }}
                · 租约 {{ execution.externalTask.leaseSeconds }} 秒
                · 结果上限 {{ execution.externalTask.resultJsonLimitBytes }} bytes
              </p>
              <p v-if="execution.webhook">
                Webhook {{ execution.webhook.url }}
                · Secret {{ execution.webhook.secretConfigured ? '已配置' : '未配置' }}
                · 超时 {{ execution.webhook.timeoutSeconds }} 秒
                · 退避 {{ execution.webhook.baseBackoffSeconds }} 秒
              </p>
              <p v-if="execution.subflow" class="instance-compensation-subflow-target">
                补偿子流程：定义 {{ execution.subflow.definitionId }}
                / v{{ execution.subflow.version }}
              </p>
              <ol
                v-if="execution.subflowRuns?.length"
                class="instance-compensation-subflow-runs"
              >
                <li
                  v-for="run in execution.subflowRuns"
                  :key="`${run.childInstanceId}:${run.attempt}`"
                >
                  尝试 {{ run.attempt }} · 子实例 {{ run.childInstanceId }}
                  · 定义 {{ run.targetDefinitionId }} / v{{ run.targetVersion }}
                  · {{ run.childStatus }}
                  · 启动 {{ time(run.launchedAt) }}
                  <template v-if="run.terminalAt">
                    · 终态 {{ time(run.terminalAt) }}
                  </template>
                  <a-button
                    class="instance-compensation-child-link"
                    :loading="mutation === `child-detail:${run.childInstanceId}`"
                    @click="openChildInstance(run.childInstanceId)"
                  >
                    查看补偿子实例
                  </a-button>
                </li>
              </ol>
              <p v-if="execution.failure" class="instance-compensation-failure">
                失败 {{ execution.failure.code }}：{{ execution.failure.message }}
                · {{ execution.failure.retryable ? '可重试' : '不可重试' }}
              </p>
              <pre v-if="execution.result" class="instance-compensation-result">{{
                JSON.stringify(execution.result, null, 2)
              }}</pre>
              <ol
                v-if="execution.attempts?.length"
                class="instance-compensation-attempts"
              >
                <li
                  v-for="attempt in execution.attempts"
                  :key="attempt.attemptNumber"
                >
                  尝试 {{ attempt.attemptNumber }} · {{ attempt.status }}
                  <template v-if="attempt.httpStatus">
                    · HTTP {{ attempt.httpStatus }}
                  </template>
                  <template v-if="attempt.durationMs !== null
                    && attempt.durationMs !== undefined">
                    · {{ attempt.durationMs }} ms
                  </template>
                  <template v-if="attempt.responseSha256">
                    · 响应 SHA-256 {{ attempt.responseSha256 }}
                  </template>
                  <template v-if="attempt.failureCode">
                    · {{ attempt.failureCode }}：{{ attempt.failureMessage }}
                  </template>
                  · {{ time(attempt.startedAt) }} → {{ time(attempt.completedAt) }}
                </li>
              </ol>
              <a-button
                v-if="canManageDefinitions && execution.status === 'FAILED'"
                class="instance-compensation-retry"
                :loading="mutation === `compensation-retry:${execution.compensationExecutionId}`"
                @click="retryCompensationExecution(selectedInstance, execution)"
              >
                重试此补偿
              </a-button>
            </article>
          </section>
          <section
            v-if="selectedInstance.parallelBranches?.length"
            class="parallel-branch-runtime"
          >
            <h3>并行分支执行</h3>
            <article
              v-for="branch in selectedInstance.parallelBranches"
              :key="branch.code"
              class="parallel-branch-runtime-item"
            >
              <header>
                <strong>{{ branch.name }}</strong>
                <code>{{ branch.code }}</code>
                <a-tag :color="branch.status === 'APPROVED'
                  ? 'green'
                  : branch.status === 'REJECTED'
                    ? 'red'
                    : branch.status === 'PENDING' ? 'blue' : 'default'"
                >
                  {{ branch.status }}
                </a-tag>
              </header>
              <p>
                {{ approvalModeLabel(branch.approvalMode) }}
                · 需同意 {{ branch.requiredApprovals }} 人
                · 当前 {{ branch.activeApproverIds.join('、') || '—' }}
              </p>
              <p v-if="branch.currentStageCode" class="parallel-branch-stage-cursor">
                当前阶段 {{ (branch.currentStageIndex ?? 0) + 1 }}
                · {{ branch.currentStageCode }}
              </p>
              <section
                v-if="branch.stages?.length"
                class="parallel-branch-approval-stages"
              >
                <article
                  v-for="stage in branch.stages"
                  :key="stage.code"
                  class="parallel-branch-approval-stage"
                >
                  <header>
                    <strong>{{ stage.stageIndex + 1 }}. {{ stage.name }}</strong>
                    <code>{{ stage.code }}</code>
                    <a-tag :color="stageStatusColor(stage.status)">{{ stage.status }}</a-tag>
                  </header>
                  <p>
                    {{ approvalModeLabel(stage.approvalMode) }}
                    · 需同意 {{ stage.requiredApprovals }} 人
                    · 参与人 {{ stage.approverIds.join('、') || '待激活解析' }}
                  </p>
                  <small>
                    实际处理人 {{ stage.actualHandlerIds.join('、') || '—' }}
                    · 开始 {{ time(stage.startedAt) }}
                    · 完成 {{ time(stage.completedAt) }}
                  </small>
                  <p class="parallel-branch-stage-decision-evidence">
                    审批证据：{{ decisionEvidencePolicyLabel(stage.decisionEvidencePolicy) }}
                  </p>
                </article>
              </section>
              <p v-if="branch.deadline" class="parallel-branch-deadline">
                {{ deadlinePolicyLabel(branch.deadline.policy) }}
                · 截止 {{ time(branch.deadline.dueAt) }}
                <template v-if="branch.deadline.overdue">· 已逾期</template>
              </p>
              <p class="parallel-branch-decision-comment">
                {{ decisionCommentPolicyLabel(branch.decisionCommentPolicy) }}
              </p>
              <p class="parallel-branch-decision-evidence">
                {{ decisionEvidencePolicyLabel(branch.decisionEvidencePolicy) }}
              </p>
              <small>
                已同意 {{ branch.approvedApproverIds.join('、') || '—' }}
                · 已拒绝 {{ branch.rejectedApproverIds.join('、') || '—' }}
              </small>
            </article>
          </section>
          <h3 class="history-title">处理历史</h3>
          <a-timeline v-if="selectedHistory?.events.length">
            <a-timeline-item v-for="event in selectedHistory.events" :key="event.sequence">
              <strong>{{ event.type }}</strong>
              <p>{{ event.fromStatus || '开始' }} → {{ event.toStatus }}</p>
              <small>
                实际操作人 {{ event.actorId }}
                <template v-if="event.representedMemberId">
                  · 代表成员 {{ event.representedMemberId }}
                  · 委托规则 {{ event.delegationRuleId }}
                </template>
                · {{ time(event.occurredAt) }}
              </small>
              <p v-if="event.targetMemberId">目标成员：{{ event.targetMemberId }}</p>
              <p v-if="event.targetStepIndex !== null && event.targetStepIndex !== undefined">
                目标步骤：{{ event.targetStepIndex + 1 }}（索引 {{ event.targetStepIndex }}）
              </p>
              <p v-if="event.position">加签位置：{{ positionLabel(event.position) }}</p>
              <p v-if="event.comment">
                {{ historyCommentPrefix(event.type) }}{{ event.comment }}
              </p>
              <section v-if="event.evidence" class="history-decision-evidence">
                <p>
                  证据 {{ event.evidence.evidenceId }}
                  · 阶段 {{ event.evidence.stageIndex + 1 }}
                  <template v-if="event.evidence.branchCode">
                    · 分支 {{ event.evidence.branchCode }}
                  </template>
                </p>
                <ul v-if="event.evidence.attachments.length">
                  <li
                    v-for="file in event.evidence.attachments"
                    :key="file.fileId"
                  >
                    附件 #{{ file.fileId }} {{ file.fileName }}
                    · {{ file.contentType }} · {{ file.sizeBytes }} bytes
                    · SHA-256 {{ file.sha256 }}
                  </li>
                </ul>
                <p v-if="event.evidence.signature">
                  签名 {{ event.evidence.signature.kind }}：
                  <template v-if="event.evidence.signature.kind === 'TYPED'">
                    {{ event.evidence.signature.typedValue }}
                  </template>
                  <template v-else-if="event.evidence.signature.file">
                    #{{ event.evidence.signature.file.fileId }}
                    {{ event.evidence.signature.file.fileName }}
                  </template>
                </p>
                <p v-if="event.evidence.template">
                  意见模板 #{{ event.evidence.template.templateId }}
                  · {{ event.evidence.template.name }}
                  · v{{ event.evidence.template.version }}
                </p>
                <small>
                  证据提交人 {{ event.evidence.actorId }}
                  <template v-if="event.evidence.representedMemberId">
                    · 代表成员 {{ event.evidence.representedMemberId }}
                  </template>
                  <template v-if="event.evidence.delegationRuleId">
                    · 委托规则 {{ event.evidence.delegationRuleId }}
                  </template>
                  · {{ time(event.evidence.occurredAt) }}
                </small>
              </section>
              <section
                v-if="event.completionExecution"
                class="history-completion-execution"
              >
                <p>
                  完成执行 {{ event.completionExecution.executionId }}
                  · 顺序 {{ event.completionExecution.ordinal + 1 }}
                  · {{ event.completionExecution.code }}
                  · {{ event.completionExecution.type }}
                  <template v-if="event.completionExecution.parallelGroup">
                    · 并行组 {{ event.completionExecution.parallelGroup }}
                  </template>
                </p>
                <p>
                  {{ event.completionExecution.event }}
                  → {{ event.completionExecution.status }}
                  <template v-if="event.completionExecution.attemptNumber">
                    · 尝试 {{ event.completionExecution.attemptNumber }}
                  </template>
                  <template v-if="event.completionExecution.failureCode">
                    · 失败 {{ event.completionExecution.failureCode }}
                  </template>
                </p>
                <small>{{ time(event.completionExecution.occurredAt) }}</small>
                <p
                  v-if="event.completionExecution.subflowRun"
                  class="history-completion-subflow-run"
                >
                  子实例 {{ event.completionExecution.subflowRun.childInstanceId }}
                  · 定义 {{ event.completionExecution.subflowRun.targetDefinitionId }}
                  / v{{ event.completionExecution.subflowRun.targetVersion }}
                  · {{ event.completionExecution.subflowRun.childStatus }}
                  · 尝试 {{ event.completionExecution.subflowRun.attempt }}
                  <a-button
                    class="history-completion-child-link"
                    @click="openChildInstance(
                      event.completionExecution.subflowRun.childInstanceId,
                    )"
                  >
                    查看子实例
                  </a-button>
                </p>
              </section>
              <section
                v-if="event.compensationExecution"
                class="history-compensation-execution"
              >
                <p>
                  补偿执行 {{ event.compensationExecution.compensationExecutionId }}
                  · 原执行 {{ event.compensationExecution.originalExecutionId }}
                  · 原顺序 {{ event.compensationExecution.originalOrdinal + 1 }}
                  · {{ event.compensationExecution.type }}
                </p>
                <p>
                  {{ event.compensationExecution.event }}
                  → {{ event.compensationExecution.status }}
                  <template v-if="event.compensationExecution.attemptNumber">
                    · 尝试 {{ event.compensationExecution.attemptNumber }}
                  </template>
                  <template v-if="event.compensationExecution.failureCode">
                    · 失败 {{ event.compensationExecution.failureCode }}
                  </template>
                </p>
                <small>{{ time(event.compensationExecution.occurredAt) }}</small>
                <p
                  v-if="event.compensationExecution.subflowRun"
                  class="history-compensation-subflow-run"
                >
                  子实例 {{ event.compensationExecution.subflowRun.childInstanceId }}
                  · 定义 {{ event.compensationExecution.subflowRun.targetDefinitionId }}
                  / v{{ event.compensationExecution.subflowRun.targetVersion }}
                  · {{ event.compensationExecution.subflowRun.childStatus }}
                  · 尝试 {{ event.compensationExecution.subflowRun.attempt }}
                  <a-button
                    class="history-compensation-child-link"
                    @click="openChildInstance(
                      event.compensationExecution.subflowRun.childInstanceId,
                    )"
                  >
                    查看补偿子实例
                  </a-button>
                </p>
              </section>
            </a-timeline-item>
          </a-timeline>
          <a-empty v-else description="暂无处理历史" />

          <h3 class="history-title"><Copy :size="17" />抄送记录</h3>
          <a-alert
            v-if="copyTimelineError"
            type="error"
            show-icon
            :message="copyTimelineError"
          />
          <a-spin :spinning="copyTimelineLoading">
            <a-timeline v-if="selectedCopies.length" class="copy-timeline">
              <a-timeline-item v-for="item in selectedCopies" :key="item.copyId">
                <strong>成员 {{ item.actorId }} 抄送成员 {{ item.recipientId }}</strong>
                <p v-if="item.message">{{ item.message }}</p>
                <small>{{ time(item.createdAt) }}</small>
              </a-timeline-item>
            </a-timeline>
            <a-empty v-else description="暂无抄送记录" />
          </a-spin>
          <a-pagination
            v-if="copyTotal > interactionPageSize"
            class="copy-pagination"
            :current="copyPage"
            :page-size="interactionPageSize"
            :total="copyTotal"
            :show-size-changer="false"
            @change="changeCopyPage"
          />

          <h3 class="history-title">催办记录</h3>
          <a-alert
            v-if="urgeTimelineError"
            type="error"
            show-icon
            :message="urgeTimelineError"
          />
          <a-spin :spinning="urgeTimelineLoading">
            <a-timeline v-if="selectedUrges.length" class="urge-timeline">
              <a-timeline-item v-for="item in selectedUrges" :key="item.urgeId">
                <strong>成员 {{ item.actorId }} 催办成员 {{ item.recipientId }}</strong>
                <p v-if="item.message">{{ item.message }}</p>
                <small>{{ time(item.createdAt) }}</small>
              </a-timeline-item>
            </a-timeline>
            <a-empty v-else description="暂无催办记录" />
          </a-spin>
          <a-pagination
            v-if="urgeTotal > interactionPageSize"
            class="urge-pagination"
            :current="urgePage"
            :page-size="interactionPageSize"
            :total="urgeTotal"
            :show-size-changer="false"
            @change="changeUrgePage"
          />

          <h3 class="history-title"><MessageSquare :size="17" />实例评论</h3>
          <a-form v-if="canComment" class="flow-comment-form" layout="vertical">
            <a-form-item label="添加评论" required>
              <a-textarea
                v-model:value="commentBody"
                class="flow-comment-body"
                :rows="4"
                maxlength="2000"
                placeholder="填写对该流程实例的评论"
              />
            </a-form-item>
            <a-alert v-if="commentError" type="error" show-icon :message="commentError" />
            <a-button
              class="flow-comment-submit"
              type="primary"
              :loading="mutation.startsWith('comment:')"
              :disabled="Boolean(mutation)"
              @click="appendComment"
            >
              <MessageSquare :size="15" />提交评论
            </a-button>
          </a-form>
          <a-alert
            v-if="commentTimelineError"
            type="error"
            show-icon
            :message="commentTimelineError"
          />
          <a-spin :spinning="commentTimelineLoading">
            <a-timeline v-if="selectedComments.length" class="comment-timeline">
              <a-timeline-item v-for="item in selectedComments" :key="item.commentId">
                <strong>成员 {{ item.authorId }}</strong>
                <p>{{ item.body }}</p>
                <small>{{ time(item.createdAt) }}</small>
              </a-timeline-item>
            </a-timeline>
            <a-empty v-else description="暂无实例评论" />
          </a-spin>
          <a-pagination
            v-if="commentTotal > interactionPageSize"
            class="comment-pagination"
            :current="commentPage"
            :page-size="interactionPageSize"
            :total="commentTotal"
            :show-size-changer="false"
            @change="changeCommentPage"
          />
        </template>
      </a-spin>
    </a-drawer>
  </section>
</template>

<style scoped>
.flow-page {
  display: grid;
  gap: 22px;
  max-width: 1180px;
  margin: 0 auto;
  padding: 28px;
  min-width: 0;
  background: #f4f6f8;
}

.flow-heading,
.flow-heading h1,
.flow-actions,
.section-heading,
.flow-card,
.card-actions,
.instance-title {
  display: flex;
  align-items: center;
}

.flow-heading,
.section-heading,
.flow-card {
  justify-content: space-between;
  gap: 20px;
}

.flow-heading h1 {
  gap: 10px;
  margin: 0;
}

.flow-heading p,
.section-heading p,
.flow-card p {
  margin: 6px 0 0;
  color: #64748b;
}

.flow-actions,
.card-actions,
.instance-title,
.task-filter {
  gap: 9px;
}

.flow-actions,
.card-actions,
.detail-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.flow-section {
  display: grid;
  gap: 14px;
  min-width: 0;
  padding: 20px;
  border: 1px solid #dce3e8;
  border-radius: 10px;
  background: #fff;
}

.section-heading h2 {
  margin: 0;
  font-size: 18px;
}

.section-heading > span,
.flow-card small {
  color: #64748b;
  font-size: 13px;
}

.definition-list,
.version-history-list,
.instance-list,
.approval-task-list,
.claimable-task-list {
  display: grid;
  gap: 10px;
}

.version-history-list {
  margin-top: 12px;
}

.version-history-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 15px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}

.version-history-item p {
  margin: 5px 0 0;
  color: #64748b;
}

.version-history-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.preflight-actions,
.draft-check-result header,
.draft-simulation-result header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.preflight-actions {
  margin: 12px 0;
}

.preflight-form {
  display: grid;
  gap: 10px;
}

.preflight-form label {
  display: grid;
  gap: 5px;
  color: #475569;
}

.preflight-form input,
.preflight-form textarea {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font: inherit;
}

.draft-check-result,
.draft-simulation-result {
  display: grid;
  gap: 10px;
  margin-top: 16px;
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}

.draft-check-issues,
.simulation-steps {
  display: grid;
  gap: 7px;
  margin: 0;
  padding-left: 20px;
}

.draft-check-issues li {
  display: grid;
  gap: 3px;
}

.draft-check-issues .blocker {
  color: #b91c1c;
}

.draft-check-issues .warning {
  color: #b45309;
}

.simulation-trigger-result,
.simulation-status-effects {
  margin: 0;
  color: #64748b;
}

.task-filter {
  display: flex;
  align-items: center;
  justify-content: flex-start;
}

.flow-card {
  min-height: 92px;
  padding: 17px 19px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
}

.flow-card > :first-child {
  min-width: 0;
}

.flow-card p,
.flow-card strong {
  overflow-wrap: anywhere;
}

.detail-list {
  margin: 0;
}

.detail-list > div {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px solid #e5e7eb;
}

.detail-list dt {
  color: #64748b;
}

.detail-list dd {
  margin: 0;
  overflow-wrap: anywhere;
}

.instance-approval-stages,
.instance-approval-stage,
.parallel-branch-approval-stages,
.parallel-branch-approval-stage,
.preflight-approval-stages {
  display: grid;
  gap: 10px;
}

.instance-approval-stage,
.parallel-branch-approval-stage {
  padding: 12px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fafc;
}

.instance-approval-stage > header,
.parallel-branch-approval-stage > header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.history-title {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 24px;
}

.flow-comment-form {
  display: grid;
  gap: 10px;
  margin-bottom: 18px;
}

.start-definition-id {
  width: 100%;
  min-height: 32px;
  padding: 4px 11px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  background: #fff;
}

.start-receipt {
  display: grid;
  gap: 14px;
}

.start-error,
.start-refresh-warning,
.start-catalog-error {
  margin-top: 10px;
}

.assignment-form {
  display: grid;
  gap: 8px;
}

.add-sign-position {
  width: 100%;
  min-height: 34px;
  padding: 4px 10px;
  border: 1px solid #d9d9d9;
  background: #fff;
  color: #263640;
}

.flow-comment-submit,
.urge-pagination,
.comment-pagination {
  margin-left: auto;
}

.urge-pagination,
.comment-pagination,
.claimable-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.approver-sequence {
  display: grid;
  gap: 10px;
}

.definition-compact-fallback {
  margin-bottom: 18px;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
}

.definition-compact-fallback summary {
  cursor: pointer;
  color: #475569;
  font-weight: 600;
}

.definition-compact-fallback[open] summary {
  margin-bottom: 12px;
}

.approver-step {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) repeat(3, 34px);
  align-items: center;
  gap: 7px;
}

.approver-step > span {
  color: #64748b;
  text-align: center;
}

@media (max-width: 1024px) {
  .flow-page { gap: 18px; padding: 22px; }
  .flow-heading { align-items: flex-start; }
  .flow-actions { justify-content: flex-end; }
  .flow-section { padding: 17px; }
  .flow-card { align-items: flex-start; }
  .card-actions { justify-content: flex-end; }
}

@media (max-width: 720px) {
  .flow-page { gap: 14px; padding: 14px; }
  .flow-heading,
  .section-heading,
  .flow-card,
  .version-history-item { align-items: stretch; flex-direction: column; gap: 14px; }
  .flow-actions,
  .card-actions { justify-content: flex-start; }
  .flow-actions .ant-btn,
  .card-actions .ant-btn { min-height: 34px; }
  .flow-section { padding: 14px; }
  .section-heading > span { align-self: flex-start; }
  .task-filter { align-items: stretch; flex-direction: column; }
  .detail-list > div { grid-template-columns: 92px minmax(0, 1fr); gap: 10px; }
  .preflight-actions { flex-wrap: wrap; }
  .approver-step { grid-template-columns: 24px minmax(0, 1fr) repeat(3, 34px); }
}
</style>
