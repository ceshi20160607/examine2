export interface PlatformAiProvider {
  id: string
  code: string
  name: string
  baseUrl: string
  model: string
  secretRef: string
  timeoutSeconds: number
  enabled: boolean
  version: number
}

export interface CreatePlatformAiProviderInput {
  code: string
  name: string
  baseUrl: string
  model: string
  secretRef: string
  timeoutSeconds: number
  enabled: boolean
}

export interface UpdatePlatformAiProviderInput extends CreatePlatformAiProviderInput {
  expectedVersion: number
}

export type PlatformAiOperation =
  | 'AUTHORIZED_SYSTEMS_QUERY'
  | 'SYSTEM_SWITCH_GUIDANCE'
  | 'PLATFORM_TASK_DRAFT'
  | 'PLATFORM_OPERATIONS_QUERY'
export type PlatformAiDataResidency = 'PLATFORM_METADATA_ONLY'

export interface PlatformAiPolicy {
  draftVersion: number
  status: string
  providerId: string | null
  providerVersion: number
  allowedOperations: PlatformAiOperation[]
  maxSystems: number
  dailyRequestQuota: number
  dailyTokenQuota: number
  maxConcurrency: number
  strictRedaction: boolean
  dataResidency: PlatformAiDataResidency
  promptVersion: string
  enabled: boolean
  activeVersionId: string | null
}

export interface SavePlatformAiPolicyInput {
  expectedVersion: number
  providerId: string
  allowedOperations: PlatformAiOperation[]
  maxSystems: number
  dailyRequestQuota: number
  dailyTokenQuota: number
  maxConcurrency: number
  strictRedaction: true
  dataResidency: PlatformAiDataResidency
  promptVersion: string
  enabled: boolean
}

export interface PlatformAiPolicyCheckIssue {
  code: string
  message: string
  path?: string | null
  severity?: 'BLOCKER' | 'WARNING' | string
}

export interface PlatformAiPolicyCheck {
  status: 'PASSED' | 'FAILED' | string
  issues: PlatformAiPolicyCheckIssue[]
}

export interface PlatformAiCapability {
  available: boolean
  reason: string | null
  policyVersion: string | null
}

export interface PlatformAiSession {
  id: string
  title: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface PlatformAiSessionPage {
  rows: PlatformAiSession[]
  page: number
  size: number
  hasMore: boolean
}

export interface PlatformAiMessage {
  id: string
  role: 'USER' | 'ASSISTANT' | string
  status: string
  content: string
  createdAt: string
}

export interface PlatformAiAuthorizedSystem {
  systemId: string
  systemCode: string
  systemName: string
  status: 'ACTIVE' | 'INITIALIZING'
  membershipState: 'ACTIVE'
  accessState: 'AUTHORIZED'
  switchTarget: string
}

export interface PlatformAiSwitchGuidance {
  requestedSystemCode: string | null
  message: string
  switchTarget: string | null
}

export type PlatformAiTaskPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'
export type PlatformAiTaskProposalState =
  | 'CLARIFICATION_REQUIRED'
  | 'PENDING'
  | 'EXECUTING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'REJECTED'
  | 'EXPIRED'

export interface PlatformAiTaskPreview {
  title: string
  description: string | null
  dueAt: string | null
  priority: PlatformAiTaskPriority
  selfAssigned: true
}

export interface PlatformAiTaskResult {
  taskId: string
  title: string
  description: string | null
  dueAt: string | null
  priority: PlatformAiTaskPriority
  status: 'OPEN'
  source: 'AGENT'
  createdAt: string
}

export interface PlatformAiTaskProposal {
  id: string
  state: PlatformAiTaskProposalState
  revision: number
  preview: PlatformAiTaskPreview | null
  confidence: number
  clarification: string | null
  expiresAt: string
  result: PlatformAiTaskResult | null
  errorCode: string | null
  requestId: string
  traceId: string
}

export type PlatformAiOperationsQueryKind =
  | 'PERSONAL_TASKS'
  | 'AI_QUOTA'
  | 'SERVICE_HEALTH'
  | 'AGENT_ACTIVITY'

export interface PlatformAiPersonalTask {
  taskId: string
  title: string
  dueAt: string | null
  priority: PlatformAiTaskPriority
  status: string
  source: string
  createdAt: string
}

export interface PlatformAiQuota {
  policyVersion: string
  periodStart: string
  periodEnd: string
  requestLimit: number
  requestCount: number
  remainingRequests: number
  tokenLimit: number
  usedTokens: number
  reservedTokens: number
  remainingTokens: number
  concurrencyLimit: number
  runningCount: number
  remainingConcurrency: number
}

export type PlatformAiHealthStatus = 'UP' | 'DEGRADED' | 'UNKNOWN'
export type PlatformAiHealthComponentName =
  | 'DATABASE'
  | 'REDIS'
  | 'PLATFORM_AI_CONFIGURATION'
  | 'PLATFORM_AI_PROVIDER'

export interface PlatformAiHealthComponent {
  component: PlatformAiHealthComponentName
  status: PlatformAiHealthStatus
}

export interface PlatformAiServiceHealth {
  status: PlatformAiHealthStatus
  components: PlatformAiHealthComponent[]
  checkedAt: string
}

export interface PlatformAiAgentActivity {
  event: string
  time: string
  operation: PlatformAiOperation | null
  resultCode: string
  requestId: string
  traceId: string
}

export interface PlatformAiOperations {
  queryKind: PlatformAiOperationsQueryKind | null
  confidence: number
  clarification: string | null
  personalTasks: PlatformAiPersonalTask[]
  quota: PlatformAiQuota | null
  serviceHealth: PlatformAiServiceHealth | null
  agentActivity: PlatformAiAgentActivity[]
}

export interface PlatformAiTurn {
  id: string
  status: 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'RETRYABLE' | string
  operation: PlatformAiOperation | null
  answer: string | null
  errorCode: string | null
  retryable: boolean
  systems: PlatformAiAuthorizedSystem[]
  guidance: PlatformAiSwitchGuidance | null
  proposal: PlatformAiTaskProposal | null
  operations: PlatformAiOperations | null
  requestId: string
  traceId: string
}

export interface PlatformAiEvidence {
  type: string
  systems: PlatformAiAuthorizedSystem[]
  guidance: PlatformAiSwitchGuidance | null
}

export interface PlatformAiStoredTurn {
  id: string
  status: PlatformAiTurn['status']
  operation: PlatformAiOperation | null
  responseSummary: string | null
  errorCode: string | null
  retryable: boolean
  returnedSystems: number
  evidence: PlatformAiEvidence | null
  proposal: PlatformAiTaskProposal | null
  operations: PlatformAiOperations | null
  createdAt: string
  finishedAt: string | null
}

export interface PlatformAiSessionDetail {
  session: PlatformAiSession
  messages: PlatformAiMessage[]
  turns: PlatformAiStoredTurn[]
}

export type PlatformAiDisplayTurn = PlatformAiStoredTurn | PlatformAiTurn

export type PlatformAiConversationDetail = Omit<PlatformAiSessionDetail, 'turns'> & {
  turns: PlatformAiDisplayTurn[]
}

export interface CreatePlatformAiSessionInput {
  title: string
}

export interface SubmitPlatformAiMessageInput {
  content: string
}

export interface ActOnPlatformAiTaskProposalInput {
  expectedRevision: number
}
