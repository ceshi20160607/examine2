export type FlowTriggerEvent =
  | 'RECORD_ACTIVATED'
  | 'RECORD_CREATED'
  | 'RECORD_UPDATED'
  | 'RECORD_DELETED'
  | 'RECORD_STATUS_CHANGED'
  | 'IMPORT_COMPLETED'
  | 'PERIODIC'
export type FlowTriggerConditionOperator =
  | 'EQ'
  | 'NE'
  | 'GT'
  | 'GTE'
  | 'LT'
  | 'LTE'
  | 'EMPTY'
  | 'NOT_EMPTY'

export interface FlowTriggerCondition {
  fieldCode: string
  operator: FlowTriggerConditionOperator
  value?: unknown
}

export interface FlowTriggerBinding {
  moduleCode: string | null
  event: FlowTriggerEvent
  priority: number
  exclusive: boolean
  conditions: FlowTriggerCondition[]
  startAt?: string | null
  intervalMinutes?: number | null
  requesterMemberId?: string | null
}

export interface FlowPeriodicScheduleState {
  definitionId: string
  definitionVersion: number
  requesterId: string
  intervalMinutes: number
  startAt: string
  nextFireAt: string
  lastScheduledAt: string | null
  lastInstanceId: string | null
  status: 'ACTIVE' | 'PAUSED'
  pauseReason: string | null
  updatedAt: string
}

export interface FlowRecordStatusMapping {
  fieldCode: string
  approvedValue: string
  rejectedValue: string
  withdrawnValue: string
  terminatedValue: string
}

export type FlowApprovalMode = 'SEQUENTIAL' | 'ANY' | 'ALL' | 'QUORUM'
export type FlowApproverSourceKind =
  | 'FIXED'
  | 'ROLE'
  | 'DEPARTMENT'
  | 'DEPARTMENT_LEADER'
  | 'REQUESTER'
  | 'REQUESTER_MANAGER'
  | 'REQUESTER_DEPARTMENT_LEADER'
  | 'RECORD_MEMBER_FIELD'
  | 'PREVIOUS_HANDLER'
export type FlowQuorumRuleType = 'COUNT' | 'PERCENTAGE'
export type FlowDeadlineTimeoutAction = 'NONE' | 'AUTO_APPROVE' | 'AUTO_REJECT'

export interface FlowApproverSource {
  kind: FlowApproverSourceKind
  sourceId?: string | null
  moduleCode?: string | null
}

export interface FlowRecordMemberFieldOption {
  sourceId: string
  moduleCode: string
  fieldCode: string
  fieldName: string
}

export interface FlowRecordMemberFieldCatalog {
  items: FlowRecordMemberFieldOption[]
}

export interface FlowQuorumRule {
  type: FlowQuorumRuleType
  value: number
}

export interface FlowDeadlinePolicy {
  timeoutMinutes: number
  remindBeforeMinutes?: number | null
  timeoutAction: FlowDeadlineTimeoutAction
}

export interface FlowDeadlineState {
  policy: FlowDeadlinePolicy
  remindAt: string | null
  dueAt: string
  remindedAt: string | null
  processedAt: string | null
  overdue: boolean
}

export interface FlowDecisionCommentPolicy {
  approveRequired: boolean
  rejectRequired: boolean
  minimumLength: number
}

export type FlowEvidenceMimeFamily = 'IMAGE' | 'PDF' | 'DOCUMENT' | 'ARCHIVE' | 'OTHER'
export type FlowSignatureMode = 'NONE' | 'OPTIONAL' | 'REQUIRED'

export interface FlowDecisionEvidencePolicy {
  minimumAttachments: number
  maximumAttachments: number
  allowedMimeFamilies: FlowEvidenceMimeFamily[]
  signatureMode: FlowSignatureMode
}

export type FlowCompletionStepType = 'EXTERNAL_TASK' | 'WEBHOOK' | 'SUBFLOW'
export type FlowCompletionFailurePolicy = 'MANUAL_RETRY' | 'COMPENSATE'

export interface FlowExternalTaskCompletionConfig {
  topic: string
  leaseSeconds: number
  maxAttempts: number
  resultJsonLimitBytes: number
}

export interface FlowWebhookCompletionConfig {
  url: string
  secretRef?: string | null
  timeoutSeconds: number
  maxAttempts: number
  baseBackoffSeconds: number
}

export interface FlowSubflowCompletionConfig {
  definitionId: string
  version: number
}

export interface FlowExternalTaskCompensation {
  type: 'EXTERNAL_TASK'
  externalTask: FlowExternalTaskCompletionConfig
  webhook?: never
  subflow?: never
}

export interface FlowWebhookCompensation {
  type: 'WEBHOOK'
  externalTask?: never
  webhook: FlowWebhookCompletionConfig
  subflow?: never
}

export interface FlowSubflowCompensation {
  type: 'SUBFLOW'
  externalTask?: never
  webhook?: never
  subflow: FlowSubflowCompletionConfig
}

export type FlowCompletionCompensation =
  | FlowExternalTaskCompensation
  | FlowWebhookCompensation
  | FlowSubflowCompensation

export interface FlowExternalTaskCompletionStep {
  code: string
  name: string
  type: 'EXTERNAL_TASK'
  parallelGroup?: string | null
  compensation?: FlowCompletionCompensation | null
  externalTask: FlowExternalTaskCompletionConfig
  webhook?: never
  subflow?: never
}

export interface FlowWebhookCompletionStep {
  code: string
  name: string
  type: 'WEBHOOK'
  parallelGroup?: string | null
  compensation?: FlowCompletionCompensation | null
  externalTask?: never
  webhook: FlowWebhookCompletionConfig
  subflow?: never
}

export interface FlowSubflowCompletionStep {
  code: string
  name: string
  type: 'SUBFLOW'
  parallelGroup?: string | null
  compensation?: FlowCompletionCompensation | null
  externalTask?: never
  webhook?: never
  subflow: FlowSubflowCompletionConfig
}

export type FlowCompletionStep =
  | FlowExternalTaskCompletionStep
  | FlowWebhookCompletionStep
  | FlowSubflowCompletionStep

export interface FlowApprovalStage {
  code: string
  name: string
  approverIds: string[]
  approvalMode: FlowApprovalMode
  approverSource: FlowApproverSource
  quorumRule?: FlowQuorumRule
  deadlinePolicy?: FlowDeadlinePolicy
  decisionCommentPolicy?: FlowDecisionCommentPolicy
  decisionEvidencePolicy?: FlowDecisionEvidencePolicy
}

export type FlowInstanceStageStatus = 'WAITING' | 'ACTIVE' | 'APPROVED' | 'REJECTED'

export interface FlowInstanceStage {
  stageIndex: number
  code: string
  name: string
  status: FlowInstanceStageStatus
  approverIds: string[]
  approvalMode: FlowApprovalMode
  requiredApprovals: number
  actualHandlerIds: string[]
  startedAt: string | null
  completedAt: string | null
  deadline: FlowDeadlineState | null
  decisionCommentPolicy: FlowDecisionCommentPolicy
  decisionEvidencePolicy?: FlowDecisionEvidencePolicy | null
}

export interface FlowGatewayBranch {
  code: string
  name: string
  defaultBranch: boolean
  conditions: FlowTriggerCondition[]
  approverIds: string[]
  approvalMode?: FlowApprovalMode
  approverSource?: FlowApproverSource
  quorumRule?: FlowQuorumRule
  deadlinePolicy?: FlowDeadlinePolicy
  decisionCommentPolicy?: FlowDecisionCommentPolicy
  decisionEvidencePolicy?: FlowDecisionEvidencePolicy
  approvalStages?: FlowApprovalStage[]
}

export interface FlowGateway {
  branches: FlowGatewayBranch[]
}

export interface FlowParallelBranch {
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

export interface FlowParallelGateway {
  branches: FlowParallelBranch[]
}

export interface FlowInclusiveBranch extends FlowGatewayBranch {
  approvalMode: FlowApprovalMode
}

export interface FlowInclusiveGateway {
  branches: FlowInclusiveBranch[]
}

export interface FlowDefinitionDraft {
  definitionId: string
  name: string
  approverId: string
  approverIds?: string[]
  triggerBinding: FlowTriggerBinding | null
  recordStatusMapping: FlowRecordStatusMapping | null
  gateway?: FlowGateway | null
  parallelGateway?: FlowParallelGateway | null
  inclusiveGateway?: FlowInclusiveGateway | null
  approvalMode?: FlowApprovalMode
  approverSource?: FlowApproverSource
  quorumRule?: FlowQuorumRule
  deadlinePolicy?: FlowDeadlinePolicy
  decisionCommentPolicy?: FlowDecisionCommentPolicy
  decisionEvidencePolicy?: FlowDecisionEvidencePolicy
  approvalStages?: FlowApprovalStage[]
  completionFailurePolicy?: FlowCompletionFailurePolicy
  completionSteps?: FlowCompletionStep[]
  revision: number
  updatedAt: string
}

export interface FlowDefinitionVersion {
  definitionId: string
  version: number
  name: string
  approverId: string
  approverIds?: string[]
  triggerBinding: FlowTriggerBinding | null
  recordStatusMapping: FlowRecordStatusMapping | null
  gateway?: FlowGateway | null
  parallelGateway?: FlowParallelGateway | null
  inclusiveGateway?: FlowInclusiveGateway | null
  approvalMode?: FlowApprovalMode
  approverSource?: FlowApproverSource
  quorumRule?: FlowQuorumRule
  deadlinePolicy?: FlowDeadlinePolicy
  decisionCommentPolicy?: FlowDecisionCommentPolicy
  decisionEvidencePolicy?: FlowDecisionEvidencePolicy
  approvalStages?: FlowApprovalStage[]
  completionFailurePolicy?: FlowCompletionFailurePolicy
  completionSteps?: FlowCompletionStep[]
  sourceRevision: number
  publishedAt: string
}

export interface FlowDraftIssue {
  severity: 'BLOCKER' | 'WARNING'
  code: string
  path: string
  message: string
}

export interface FlowDraftCheck {
  definitionId: string
  revision: number
  verdict: 'READY' | 'BLOCKED'
  blockerCount: number
  warningCount: number
  issues: FlowDraftIssue[]
}

export interface FlowSimulationStep {
  index: number
  approverId: string
  initial: boolean
}

export interface FlowSimulationTrigger {
  configured: boolean
  event: FlowTriggerEvent | null
  moduleCode: string | null
  matched: boolean
  reason: string
}

export interface FlowDraftSimulation {
  definitionId: string
  revision: number
  check: FlowDraftCheck
  requesterId: string
  businessKey: string
  startable: boolean
  reason: string
  steps: FlowSimulationStep[]
  trigger: FlowSimulationTrigger
  statusEffects: FlowRecordStatusMapping | null
  completionFailurePolicy?: FlowCompletionFailurePolicy
  completionSteps?: FlowCompletionStep[]
  route: {
    configured: boolean
    branchCode: string | null
    branchName: string | null
    defaultBranch: boolean
    approvalMode?: FlowApprovalMode
    requiredApprovals?: number
    activeApproverIds?: string[]
    deadlinePolicy?: FlowDeadlinePolicy | null
    decisionCommentPolicy?: FlowDecisionCommentPolicy | null
    decisionEvidencePolicy?: FlowDecisionEvidencePolicy | null
    approvalStages?: FlowApprovalStage[]
  }
  parallelRoutes?: Array<{
    configured: boolean
    branchCode: string
    branchName: string
    defaultBranch: boolean
    approvalMode: FlowApprovalMode
    requiredApprovals: number
    activeApproverIds: string[]
    deadlinePolicy?: FlowDeadlinePolicy | null
    decisionCommentPolicy?: FlowDecisionCommentPolicy | null
    decisionEvidencePolicy?: FlowDecisionEvidencePolicy | null
    approvalStages?: FlowApprovalStage[]
  }>
}

export interface SimulateFlowDraftInput {
  requesterId?: string
  businessKey?: string
  values?: Record<string, unknown>
  trigger?: {
    moduleCode: string | null
    event: FlowTriggerEvent
    values: Record<string, unknown>
  }
}

export interface FlowStartableDefinition {
  definitionId: string
  name: string
  latestVersion: number
  publishedAt: string
}

export type FlowInstanceStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'WITHDRAWN'
  | 'TERMINATED'
export type ApprovalTaskStatus = 'PENDING' | 'COMPLETED' | 'ALL'
export type FlowAssignmentPosition = 'BEFORE' | 'AFTER'
export type FlowClaimState = 'CLAIMED' | 'OPEN'
export type FlowDelegationStatus = 'SCHEDULED' | 'ACTIVE' | 'EXPIRED' | 'REVOKED'

export interface FlowDelegationRule {
  delegationRuleId: string
  delegatorMemberId: string
  delegateMemberId: string
  startsAt: string
  endsAt: string
  definitionId: string | null
  status: FlowDelegationStatus
  createdAt: string
  createdByMemberId: string
  revokedAt: string | null
  revokedByMemberId: string | null
}

export interface CreateFlowDelegationInput {
  delegatorMemberId?: string
  delegateMemberId: string
  startsAt: string
  endsAt: string
  definitionId?: string | null
}

export type FlowDelegationPage = FlowPage<FlowDelegationRule>

export interface FlowRecordBinding {
  moduleCode: string
  recordId: string
}

export type FlowCompletionPhase =
  | 'HUMAN_APPROVAL'
  | 'EXTERNAL_EXECUTION'
  | 'COMPENSATING'
  | 'COMPLETED'

export type FlowCompletionExecutionStatus =
  | 'WAITING'
  | 'AVAILABLE'
  | 'LEASED'
  | 'RETRYING'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'

export interface FlowCompletionFailure {
  code: string
  message: string
  retryable: boolean
}

export interface FlowCompletionAttempt {
  attemptNumber: number
  status: FlowCompletionExecutionStatus
  httpStatus?: number | null
  durationMs?: number | null
  responseSha256?: string | null
  failureCode?: string | null
  failureMessage?: string | null
  startedAt: string
  completedAt?: string | null
}

export type FlowSubflowRunStatus =
  | 'RUNNING'
  | 'APPROVED_COMPLETED'
  | 'REJECTED'
  | 'WITHDRAWN'
  | 'TERMINATED'

export interface FlowSubflowRun {
  attempt: number
  childInstanceId: string
  targetDefinitionId: string
  targetVersion: number
  childStatus: FlowSubflowRunStatus
  launchedAt: string
  terminalAt: string | null
}

export interface FlowCompletionExecution {
  executionId: string
  instanceId: string
  definitionId: string
  definitionVersion: number
  ordinal: number
  code: string
  name: string
  type: FlowCompletionStepType
  status: FlowCompletionExecutionStatus
  parallelGroup?: string | null
  externalTask?: Omit<FlowExternalTaskCompletionConfig, 'maxAttempts'> | null
  webhook?: Omit<FlowWebhookCompletionConfig, 'maxAttempts'> & {
    secretRef: '********' | null
    secretConfigured: boolean
  } | null
  subflow?: FlowSubflowCompletionConfig | null
  attemptCount: number
  maxAttempts: number
  availableAt: string | null
  leaseExpiresAt?: string | null
  createdAt: string
  startedAt: string | null
  completedAt: string | null
  result?: Record<string, unknown> | null
  failure?: FlowCompletionFailure | null
  attempts?: FlowCompletionAttempt[]
  subflowRuns?: FlowSubflowRun[]
}

export interface FlowExternalTaskClaim {
  execution: FlowCompletionExecution
  leaseToken: string
}

export interface FlowCompletionExecutionHistoryFact {
  executionId: string
  ordinal: number
  code: string
  type: FlowCompletionStepType
  event: string
  status: FlowCompletionExecutionStatus
  attemptNumber?: number | null
  occurredAt: string
  failureCode?: string | null
  parallelGroup?: string | null
  subflowRun?: FlowSubflowRun | null
}

export interface FlowCompensationExecution {
  compensationExecutionId: string
  originalExecutionId: string
  originalOrdinal: number
  instanceId: string
  definitionId: string
  definitionVersion: number
  type: FlowCompletionStepType
  status: FlowCompletionExecutionStatus
  externalTask?: Omit<FlowExternalTaskCompletionConfig, 'maxAttempts'> | null
  webhook?: Omit<FlowWebhookCompletionConfig, 'maxAttempts'> & {
    secretRef: '********' | null
    secretConfigured: boolean
  } | null
  subflow?: FlowSubflowCompletionConfig | null
  attemptCount: number
  maxAttempts: number
  availableAt: string | null
  leaseExpiresAt?: string | null
  createdAt: string
  startedAt: string | null
  completedAt: string | null
  result?: Record<string, unknown> | null
  failure?: FlowCompletionFailure | null
  attempts?: FlowCompletionAttempt[]
  subflowRuns?: FlowSubflowRun[]
}

export interface FlowCompensationExecutionHistoryFact {
  compensationExecutionId: string
  originalExecutionId: string
  originalOrdinal: number
  type: FlowCompletionStepType
  event: string
  status: FlowCompletionExecutionStatus
  attemptNumber?: number | null
  occurredAt: string
  failureCode?: string | null
  subflowRun?: FlowSubflowRun | null
}

export type FlowExternalTaskPage = FlowPage<FlowCompletionExecution>

export interface CompleteFlowExternalTaskInput {
  leaseToken: string
  result: Record<string, unknown>
}

export interface FailFlowExternalTaskInput {
  leaseToken: string
  code: string
  message: string
}

export interface FlowInstance {
  instanceId: string
  definitionId: string
  definitionVersion: number
  businessKey: string
  requesterId: string
  approverId: string
  approverIds?: string[]
  approvalMode?: FlowApprovalMode
  requiredApprovals?: number
  activeApproverIds?: string[]
  approvedApproverIds?: string[]
  rejectedApproverIds?: string[]
  parallelBranches?: FlowBranchExecution[]
  currentStageIndex?: number
  currentStageCode?: string
  stages?: FlowInstanceStage[]
  currentStepIndex: number
  claimState: FlowClaimState
  recordBinding: FlowRecordBinding | null
  status: FlowInstanceStatus
  startedAt: string
  completedAt: string | null
  deadline?: FlowDeadlineState | null
  decisionCommentPolicy?: FlowDecisionCommentPolicy | null
  decisionEvidencePolicy?: FlowDecisionEvidencePolicy | null
  representedAuthorities?: FlowDelegationAuthority[]
  completionPhase?: FlowCompletionPhase | null
  completionFailurePolicy?: FlowCompletionFailurePolicy
  activeCompletionOrdinal?: number | null
  activeCompletionOrdinals?: number[]
  completionExecutions?: FlowCompletionExecution[]
  compensationExecutions?: FlowCompensationExecution[]
}

export interface FlowDelegationAuthority {
  representedMemberId: string
  delegationRuleId: string | null
}

export interface FlowBranchExecution {
  code: string
  name: string
  approverIds: string[]
  approvalMode: FlowApprovalMode
  requiredApprovals: number
  approverId: string
  currentStepIndex: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'WITHDRAWN' | 'TERMINATED'
  activeApproverIds: string[]
  approvedApproverIds: string[]
  rejectedApproverIds: string[]
  startedAt: string
  completedAt: string | null
  deadline?: FlowDeadlineState | null
  decisionCommentPolicy?: FlowDecisionCommentPolicy | null
  decisionEvidencePolicy?: FlowDecisionEvidencePolicy | null
  currentStageIndex?: number
  currentStageCode?: string
  stages?: FlowInstanceStage[]
}

export interface FlowDecisionEvidenceFileSnapshot {
  fileId: string
  fileName: string
  contentType: string
  sizeBytes: number
  sha256: string
}

export interface FlowDecisionEvidenceSignature {
  kind: 'TYPED' | 'FILE'
  typedValue?: string | null
  file?: FlowDecisionEvidenceFileSnapshot | null
}

export interface FlowDecisionEvidenceTemplateSnapshot {
  templateId: string
  version: number
  name: string
}

export interface FlowDecisionEvidence {
  evidenceId: string
  branchCode?: string | null
  stageIndex: number
  attachments: FlowDecisionEvidenceFileSnapshot[]
  signature?: FlowDecisionEvidenceSignature | null
  template?: FlowDecisionEvidenceTemplateSnapshot | null
  actorId: string
  representedMemberId?: string | null
  delegationRuleId?: string | null
  occurredAt: string
}

export interface FlowHistoryEvent {
  sequence: number
  type: string
  actorId: string | null
  fromStatus: FlowInstanceStatus | FlowCompletionExecutionStatus | null
  toStatus: FlowInstanceStatus | FlowCompletionExecutionStatus
  comment: string | null
  targetMemberId?: string | null
  targetStepIndex?: number | null
  position?: FlowAssignmentPosition | null
  representedMemberId?: string | null
  delegationRuleId?: string | null
  evidence?: FlowDecisionEvidence | null
  completionExecution?: FlowCompletionExecutionHistoryFact | null
  compensationExecution?: FlowCompensationExecutionHistoryFact | null
  occurredAt: string
}

export interface FlowHistory {
  instanceId: string
  events: FlowHistoryEvent[]
}

export interface FlowPage<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export type FlowDefinitionPage = FlowPage<FlowDefinitionDraft>
export type FlowDefinitionVersionPage = FlowPage<FlowDefinitionVersion>
export type FlowStartableDefinitionPage = FlowPage<FlowStartableDefinition>
export type FlowInstancePage = FlowPage<FlowInstance>
export type ApprovalTaskPage = FlowPage<FlowInstance>
export type ClaimableTaskPage = FlowPage<FlowInstance>

export type FlowDecisionCommentTemplateStatus = 'ACTIVE' | 'INACTIVE'

export interface FlowDecisionCommentTemplate {
  templateId: string
  name: string
  version: number
  body: string
  status: FlowDecisionCommentTemplateStatus
  createdAt: string
  updatedAt: string
}

export type FlowDecisionCommentTemplatePage = FlowPage<FlowDecisionCommentTemplate>

export interface SaveFlowDecisionCommentTemplateInput {
  name: string
  body: string
}

export interface FlowUrge {
  urgeId: string
  instanceId: string
  actorId: string
  recipientId: string
  message: string
  createdAt: string
}

export interface FlowComment {
  commentId: string
  instanceId: string
  authorId: string
  body: string
  createdAt: string
}

export interface FlowCopy {
  copyId: string
  instanceId: string
  actorId: string
  recipientId: string
  message: string
  createdAt: string
}

export type FlowUrgePage = FlowPage<FlowUrge>
export type FlowCommentPage = FlowPage<FlowComment>
export type FlowCopyPage = FlowPage<FlowCopy>

export interface ApprovalTaskQuery {
  status?: ApprovalTaskStatus
  page?: number
  size?: number
}

export interface FlowInstanceListQuery {
  status?: FlowInstanceStatus
  from?: string
  to?: string
  page?: number
  size?: number
}

export interface CreateFlowDefinitionInput {
  name: string
  approverId?: string
  approverIds?: string[]
  approvalMode?: FlowApprovalMode
  approverSource?: FlowApproverSource
  quorumRule?: FlowQuorumRule
  deadlinePolicy?: FlowDeadlinePolicy
  decisionCommentPolicy?: FlowDecisionCommentPolicy
  decisionEvidencePolicy?: FlowDecisionEvidencePolicy
  approvalStages?: FlowApprovalStage[]
  triggerBinding?: FlowTriggerBinding
  recordStatusMapping?: FlowRecordStatusMapping
  gateway?: FlowGateway
  parallelGateway?: FlowParallelGateway
  inclusiveGateway?: FlowInclusiveGateway
  completionFailurePolicy?: FlowCompletionFailurePolicy
  completionSteps?: FlowCompletionStep[]
}

export interface StartFlowInstanceInput {
  definitionVersion?: number
  businessKey: string
  recordBinding?: FlowRecordBinding
  values?: Record<string, unknown>
}

export interface FlowDecisionEvidenceInput {
  attachmentFileIds?: number[]
  signatureFileId?: number
  typedSignature?: string
  commentTemplateId?: number
}

export interface WithdrawFlowInstanceInput {
  reason: string
}

export interface TerminateFlowInstanceInput {
  reason: string
}

export interface UrgeFlowInstanceInput {
  message?: string
}

export interface CreateFlowCommentInput {
  body: string
}

export interface TransferFlowInstanceInput {
  targetMemberId: string
  reason: string
}

export interface AddSignFlowInstanceInput {
  targetMemberId: string
  position: FlowAssignmentPosition
  reason: string
}

export interface ReduceSignFlowInstanceInput {
  targetStepIndex: number
  reason: string
}

export interface CopyFlowInstanceInput {
  targetMemberId: string
  message?: string
}

export interface ReturnFlowInstanceInput {
  reason: string
}

export interface CancelFlowClaimInput {
  reason: string
}

export interface ClaimFlowInstanceInput {
  comment?: string
}

export type FlowExtensionNodeType =
  | 'START'
  | 'APPROVAL'
  | 'CONDITIONAL_APPROVAL'
  | 'COPY'
  | 'CONDITIONAL_BRANCH'
  | 'PARALLEL_GATEWAY'
  | 'INCLUSIVE_GATEWAY'
  | 'MERGE_GATEWAY'
  | 'SUBFLOW'
  | 'AUTOMATION'
  | 'FORM'
  | 'TASK'
  | 'NOTIFICATION'
  | 'FIELD_UPDATE'
  | 'DATA_CREATE_UPDATE'
  | 'WAIT'
  | 'TIMER'
  | 'MESSAGE'
  | 'WEBHOOK'
  | 'EXTERNAL'
  | 'AI_ASSIST'
  | 'END'

export type FlowExtensionNodeFamily =
  | 'CONTROL'
  | 'HUMAN'
  | 'COLLABORATION'
  | 'RECORD'
  | 'WAIT'
  | 'DELIVERY'
  | 'INTEGRATION'
  | 'AI'

export interface FlowExtensionNodeCatalogEntry {
  type: FlowExtensionNodeType
  name: string
  family: FlowExtensionNodeFamily
  executor: string
  requiresBusinessRecord: boolean
  requiresHumanContinuation: boolean
}

export type FlowExtensionFieldMode = 'VISIBLE' | 'EDITABLE' | 'REQUIRED' | 'HIDDEN'

export interface FlowExtensionFieldPolicy {
  fieldCode: string
  mode: FlowExtensionFieldMode
}

export type FlowExtensionDependencyType =
  | 'FLOW_DEFINITION'
  | 'MODULE_CONFIGURATION'
  | 'OPENAPI_APPLICATION'
  | 'MESSAGE_TEMPLATE'
  | 'AI_POLICY'

export interface FlowExtensionDependency {
  sourceNodeCode: string
  type: FlowExtensionDependencyType
  targetSystemId: number
  targetTenantId: number | null
  targetKey: string
  requiredVersion: number
  versionMode: 'EXACT' | 'MINIMUM'
}

export interface FlowExtensionNode {
  code: string
  name: string
  type: FlowExtensionNodeType
  applicationCode: string
  moduleCode: string | null
  config: Record<string, unknown>
  fieldPolicies: FlowExtensionFieldPolicy[]
  next: string[]
}

export interface FlowExtensionGraph {
  nodes: FlowExtensionNode[]
  dependencies: FlowExtensionDependency[]
}

export interface FlowExtensionStoredGraph {
  definitionId: string
  definitionVersion: number | null
  sourceRevision: number
  graph: FlowExtensionGraph
  checksum: string
  actorId: string
  occurredAt: string
}

export interface FlowExtensionImpactIssue {
  severity: 'BLOCKER' | 'WARNING'
  code: string
  path: string
  message: string
}

export interface FlowExtensionDependencyImpact extends FlowExtensionDependency {
  currentVersion: number | null
  exists: boolean
  active: boolean
  compatible: boolean
  reason: string
}

export interface FlowExtensionInboundConsumer {
  systemId: string
  tenantId: string
  definitionId: string
  definitionVersion: number
  applicationCode: string
  nodeCode: string
}

export interface FlowExtensionPublishImpact {
  definitionId: string
  draftRevision: number
  ready: boolean
  outboundDependencies: FlowExtensionDependencyImpact[]
  inboundConsumers: FlowExtensionInboundConsumer[]
  issues: FlowExtensionImpactIssue[]
}

export interface FlowNodeFormField {
  fieldCode: string
  mode: FlowExtensionFieldMode
  editable: boolean
  required: boolean
  value: unknown
}

export interface FlowNodeForm {
  instanceId: string
  nodeCode: string
  definitionVersion: number
  moduleCode: string
  recordId: string
  recordVersion: number
  snapshotVersion: number
  fields: FlowNodeFormField[]
}

export interface FlowNodeFormHistory {
  sequence: number
  actorId: string
  recordVersionBefore: number
  recordVersionAfter: number
  changes: Record<string, unknown>
  before: Record<string, unknown>
  after: Record<string, unknown>
  occurredAt: string
}

export interface FlowNodeFormWriteResult {
  form: FlowNodeForm
  history: FlowNodeFormHistory
}

export type FlowNodeExecutionStatus =
  | 'CONTINUED'
  | 'WAITING_HUMAN'
  | 'WAITING_EVENT'
  | 'WAITING_TIMER'
  | 'WAITING_EXTERNAL'
  | 'WAITING_CONFIRMATION'
  | 'COMPLETED'

export interface FlowNodeExecution {
  instanceId: string
  nodeCode: string
  nodeType: FlowExtensionNodeType
  status: FlowNodeExecutionStatus
  result: Record<string, unknown>
  version: number
  actorId: string
  updatedAt: string
}

export interface FlowNodeExecutionEvent {
  sequence: number
  fromStatus: FlowNodeExecutionStatus | null
  toStatus: FlowNodeExecutionStatus
  input: Record<string, unknown>
  result: Record<string, unknown>
  actorId: string
  occurredAt: string
}
