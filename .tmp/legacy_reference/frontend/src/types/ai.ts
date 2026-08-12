import type { FieldPermissionMode, SharedFilterScenario } from '@/types/config'

export interface AiProvider {
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

export interface CreateAiProviderInput {
  code: string
  name: string
  baseUrl: string
  model: string
  secretRef: string
  timeoutSeconds: number
  enabled: boolean
}

export interface UpdateAiProviderInput extends CreateAiProviderInput {
  expectedVersion: number
}

export type AiRedactionMode = 'STRICT'
export type AiOperation =
  | 'RECORD_QUERY'
  | 'RECORD_CREATE'
  | 'RECORD_UPDATE'
  | 'AI_FILL'
  | 'CONFIG_FIELD_DRAFT'
  | 'CONFIG_SELECTION_FIELD_DRAFT'
  | 'CONFIG_PAGE_LAYOUT_DRAFT'
  | 'CONFIG_FILTER_SCENARIO_DRAFT'
  | 'CONFIG_FIELD_PERMISSION_STAGE_DRAFT'
  | 'RECORD_CONTEXT_SUMMARY'
  | 'RECORD_COMMENT_QUERY'
  | 'RECORD_HISTORY_QUERY'
  | 'RECORD_FILE_QUERY'
  | 'WORK_TASK_QUERY'
  | 'WORK_DAILY_REPORT_QUERY'
  | 'WORK_PROJECT_METRICS_QUERY'
  | 'RUNTIME_STATISTICS_QUERY'
  | 'RUNTIME_REPORT_QUERY'
  | 'FLOW_INSTANCE_HISTORY_QUERY'
  | 'TODO_QUERY'
  | 'MESSAGE_QUERY'
  | 'WORK_TASK_DRAFT'
  | 'WORK_DAILY_REPORT_DRAFT'
  | 'FLOW_DEFINITION_DRAFT'
  | 'CONFIG_REPORT_DRAFT'
  | 'CONFIG_PRINT_TEMPLATE_DRAFT'
export type AiConfirmationMode = 'REQUIRED'

export interface AiPolicy {
  draftVersion: number
  activeVersionId: string | null
  providerId: string | null
  moduleCodes: string[]
  outboundFields: Record<string, string[]>
  allowedOperations: AiOperation[]
  writableFields: Record<string, string[]>
  fillFields: Record<string, string[]>
  confirmationMode: AiConfirmationMode
  confirmationExpiresSeconds: number
  maxRows: number
  redactionMode: AiRedactionMode
  promptVersion: string
  enabled: boolean
}

export interface SaveAiPolicyInput {
  expectedVersion: number
  providerId: string
  moduleCodes: string[]
  outboundFields: Record<string, string[]>
  allowedOperations: AiOperation[]
  writableFields: Record<string, string[]>
  fillFields: Record<string, string[]>
  confirmationMode: AiConfirmationMode
  confirmationExpiresSeconds: number
  maxRows: number
  redactionMode: AiRedactionMode
  promptVersion: string
  enabled: boolean
}

export interface AiPolicyCheckIssue {
  code: string
  message: string
  path?: string | null
  severity?: 'BLOCKER' | 'WARNING' | string
}

export interface AiPolicyCheck {
  status: 'PASSED' | 'FAILED' | string
  issues: AiPolicyCheckIssue[]
}

export interface AiCapability {
  available: boolean
  reason: string | null
  policyVersion: string | null
}

export interface AiSession {
  id: string
  title: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface AiSessionPage {
  rows: AiSession[]
  page: number
  size: number
  hasMore: boolean
}

export interface AiToolResult {
  moduleCode: string
  total: number
  returnedRows: number
  rows: Array<Record<string, unknown>>
}

export interface AiRecordContextSummary {
  moduleCode?: string | null
  recordId?: string | null
  recordNo?: string | null
  title?: string | null
  status?: string | null
  recordVersion?: number | null
  summary?: string | null
  values?: Record<string, string> | null
  updatedAt?: string | null
}

export interface AiWorkTaskContext {
  taskId?: string | null
  id?: string | null
  title?: string | null
  status?: string | null
  priority?: string | null
  dueAt?: string | null
  projectId?: string | null
  assigneeMemberId?: string | null
  updatedAt?: string | null
}

export interface AiWorkDailyReportContext {
  reportId?: string | null
  id?: string | null
  workDate?: string | null
  status?: string | null
  authorMemberId?: string | null
  completedWork?: string | null
  plannedWork?: string | null
  blockers?: string | null
  updatedAt?: string | null
}

export type AiTodoCategory = 'ALL' | 'TASK' | 'APPROVAL'
export type AiTodoStateFilter = 'ALL' | 'OPEN' | 'CLOSED'
export type AiTodoTimeFilter = 'ALL' | 'TODAY' | 'OVERDUE'

export interface AiTodoContextItem {
  id: string
  category: Exclude<AiTodoCategory, 'ALL'>
  sourceType: string
  sourceId: string
  title: string
  priority: number
  dueAt: string | null
  routeHint: string
  actions: string[]
  state: Exclude<AiTodoStateFilter, 'ALL'>
  version: number
}

export interface AiTodoContext {
  category: AiTodoCategory
  state: AiTodoStateFilter
  time: AiTodoTimeFilter
  total: number
  counts: {
    open: number
    task: number
    approval: number
    today: number
    overdue: number
  }
  items: AiTodoContextItem[]
}

export type AiMessageStatus = 'ALL' | 'UNREAD' | 'READ' | 'ARCHIVED'

export interface AiMessageContextItem {
  id: string
  templateCode: string
  title: string
  body: string
  target: { type: string; id: string } | null
  targetPath: string | null
  status: Exclude<AiMessageStatus, 'ALL'>
  createdAt: string
  readAt: string | null
  archivedAt: string | null
  version: number
}

export interface AiMessageContext {
  status: AiMessageStatus
  unreadCount: number
  total: number
  items: AiMessageContextItem[]
}

export interface AiWorkProjectMetricLink {
  count: number
  route: string
}

export interface AiWorkProjectDailyMetric {
  date: string
  createdCount: number
  completedCount: number
  createdRoute: string
  completedRoute: string
}

export interface AiWorkProjectAssigneeMetric {
  assigneeMemberId: string
  openCount: number
  route: string
}

export interface AiWorkProjectMetricsContext {
  projectId: string
  title: string
  status: string
  updatedAt: string
  fromInclusive: string
  toExclusive: string
  visibility: 'ALL' | 'PARTICIPATING'
  total: number
  open: number
  completed: number
  overdueOpen: AiWorkProjectMetricLink
  dueInRangeOpen: AiWorkProjectMetricLink
  completedInRange: AiWorkProjectMetricLink
  daily: AiWorkProjectDailyMetric[]
  topAssignees: AiWorkProjectAssigneeMetric[]
}

export interface AiRecordCommentContextItem {
  commentId: string
  parentCommentId: string | null
  authorMemberId: string
  body: string | null
  deleted: boolean
  version: number
  createdAt: string
  updatedAt: string
  mentionedMemberIds: string[]
}

export interface AiRecordCommentsContext {
  moduleCode: string
  recordId: string
  total: number
  route: string
  items: AiRecordCommentContextItem[]
}

export interface AiRecordHistoryDiffContext {
  fieldCode: string
  beforeValueJson: string | null
  afterValueJson: string | null
  masked: boolean
}

export interface AiRecordHistoryContextItem {
  historyId: string
  recordVersion: number
  action: string
  actorMemberId: string | null
  occurredAt: string
  diff: AiRecordHistoryDiffContext[]
}

export interface AiRecordHistoryContext {
  moduleCode: string
  recordId: string
  total: number
  route: string
  items: AiRecordHistoryContextItem[]
}

export interface AiRecordFileContextItem {
  fileId: string
  originalName: string
  mediaType: string
  size: number
  uploaderMemberId: string
  createdAt: string
  referencedAt: string
}

export interface AiRecordFilesContext {
  moduleCode: string
  recordId: string
  total: number
  route: string
  items: AiRecordFileContextItem[]
}

export interface AiFlowInstanceHistoryEvent {
  sequence: number
  eventType: string
  fromStatus: string | null
  toStatus: string
  actorMemberId: string | null
  comment: string | null
  occurredAt: string
}

export interface AiFlowInstanceHistoryContext {
  instanceId: string
  status: string
  total: number
  route: string
  events: AiFlowInstanceHistoryEvent[]
}

export type AiRuntimeStatisticsAggregation = 'COUNT' | 'SUM' | 'AVG' | 'MIN' | 'MAX'
export type AiRuntimeStatisticsGrain = 'DAY' | 'WEEK' | 'MONTH'

export interface AiRuntimeStatisticsGroupBucket {
  label: string | null
  nullBucket: boolean
  value: string | null
  recordCount: number
}

export interface AiRuntimeStatisticsGroupingContext {
  fieldCode: string
  buckets: AiRuntimeStatisticsGroupBucket[]
}

export interface AiRuntimeStatisticsTrendBucket {
  startInclusive: string
  endExclusive: string
  value: string | null
  recordCount: number
  empty: boolean
}

export interface AiRuntimeStatisticsTrendContext {
  fieldCode: string
  grain: AiRuntimeStatisticsGrain
  startInclusive: string
  endExclusive: string
  buckets: AiRuntimeStatisticsTrendBucket[]
}

export interface AiRuntimeStatisticsContext {
  dataSourceCode: string
  moduleCode: string
  dataSourceVersionNumber: number
  aggregation: AiRuntimeStatisticsAggregation
  measureFieldCode: string | null
  value: string | null
  matchedRecordCount: number
  bucketCount: number
  totalBucketCount: number
  truncated: boolean
  grouping: AiRuntimeStatisticsGroupingContext | null
  trend: AiRuntimeStatisticsTrendContext | null
}

export interface AiRuntimeReportField {
  fieldCode: string
  fieldName: string
  type: string
}

export interface AiRuntimeReportValue {
  fieldCode: string
  displayValue: string | null
}

export interface AiRuntimeReportRow {
  values: AiRuntimeReportValue[]
}

export interface AiRuntimeReportContext {
  reportCode: string
  reportName: string
  reportVersionNumber: number
  dataSourceCode: string
  dataSourceVersionNumber: number
  moduleCode: string
  page: number
  size: number
  total: number
  returnedRows: number
  hasMore: boolean
  route: string
  fields: AiRuntimeReportField[]
  rows: AiRuntimeReportRow[]
}

interface AiContextResultBase {
  record: AiRecordContextSummary | null
  tasks: AiWorkTaskContext[]
  reports: AiWorkDailyReportContext[]
  todos: AiTodoContext | null
  messages: AiMessageContext | null
}

interface AiRecordActivityContextEmpty {
  recordComments?: null
  recordHistory?: null
  recordFiles?: null
}

interface AiRuntimeStatisticsContextEmpty {
  runtimeStatistics?: null
}

interface AiRuntimeReportContextEmpty {
  runtimeReport?: null
}

interface AiFlowInstanceHistoryContextEmpty {
  flowHistory?: null
}

type AiNonRuntimeStatisticsContextResultBase = AiContextResultBase
  & AiRuntimeStatisticsContextEmpty
  & AiRuntimeReportContextEmpty
  & AiFlowInstanceHistoryContextEmpty

export type AiContextResult =
  | (AiNonRuntimeStatisticsContextResultBase & AiRecordActivityContextEmpty & { operation: 'RECORD_CONTEXT_SUMMARY'; workMetrics?: null })
  | (AiNonRuntimeStatisticsContextResultBase & AiRecordActivityContextEmpty & { operation: 'WORK_TASK_QUERY'; workMetrics?: null })
  | (AiNonRuntimeStatisticsContextResultBase & AiRecordActivityContextEmpty & { operation: 'WORK_DAILY_REPORT_QUERY'; workMetrics?: null })
  | (AiNonRuntimeStatisticsContextResultBase & AiRecordActivityContextEmpty & {
    operation: 'WORK_PROJECT_METRICS_QUERY'
    workMetrics: AiWorkProjectMetricsContext | null
  })
  | (AiNonRuntimeStatisticsContextResultBase & {
    operation: 'RECORD_COMMENT_QUERY'
    workMetrics?: null
    recordComments: AiRecordCommentsContext | null
    recordHistory?: null
    recordFiles?: null
  })
  | (AiNonRuntimeStatisticsContextResultBase & {
    operation: 'RECORD_HISTORY_QUERY'
    workMetrics?: null
    recordComments?: null
    recordHistory: AiRecordHistoryContext | null
    recordFiles?: null
  })
  | (AiNonRuntimeStatisticsContextResultBase & {
    operation: 'RECORD_FILE_QUERY'
    workMetrics?: null
    recordComments?: null
    recordHistory?: null
    recordFiles: AiRecordFilesContext | null
  })
  | (AiNonRuntimeStatisticsContextResultBase & AiRecordActivityContextEmpty & { operation: 'TODO_QUERY'; workMetrics?: null })
  | (AiNonRuntimeStatisticsContextResultBase & AiRecordActivityContextEmpty & { operation: 'MESSAGE_QUERY'; workMetrics?: null })
  | (AiContextResultBase & AiRecordActivityContextEmpty & AiRuntimeReportContextEmpty & AiFlowInstanceHistoryContextEmpty & {
    operation: 'RUNTIME_STATISTICS_QUERY'
    workMetrics?: null
    runtimeStatistics: AiRuntimeStatisticsContext | null
  })
  | (AiContextResultBase & AiRecordActivityContextEmpty & AiRuntimeStatisticsContextEmpty & AiFlowInstanceHistoryContextEmpty & {
    operation: 'RUNTIME_REPORT_QUERY'
    workMetrics?: null
    runtimeReport: AiRuntimeReportContext | null
  })
  | (AiContextResultBase & AiRecordActivityContextEmpty & AiRuntimeStatisticsContextEmpty & AiRuntimeReportContextEmpty & {
    operation: 'FLOW_INSTANCE_HISTORY_QUERY'
    workMetrics?: null
    flowHistory: AiFlowInstanceHistoryContext | null
  })

export interface AiConfirmationFieldPreview {
  fieldCode: string
  fieldName: string
  type: string
  beforeDisplayValue: string | null
  afterDisplayValue: string | null
  masked: boolean
  confidence: number
}

export interface AiConfirmationResult {
  recordId: string
  recordNo: string
  recordVersion: number
  status: string
  title: string | null
  schemaVersionId: string
  values: Record<string, string>
  executedAt: string
}

export interface AiConfirmation {
  id: string
  sessionId: string
  turnId: string
  state: 'PENDING' | 'EXECUTING' | 'SUCCEEDED' | 'REJECTED' | 'EXPIRED' | 'FAILED' | string
  operation: Extract<AiOperation, 'RECORD_CREATE' | 'RECORD_UPDATE'>
  moduleCode: string
  recordId: string | null
  expectedRecordVersion: number | null
  beforeTitle: string | null
  afterTitle: string | null
  fields: AiConfirmationFieldPreview[]
  clarifications: string[]
  expiresAt: string
  version: number
  result: AiConfirmationResult | null
  errorCode: string | null
}

export type AiConfigurationFieldType =
  | 'TEXT'
  | 'LONG_TEXT'
  | 'INTEGER'
  | 'DECIMAL'
  | 'BOOLEAN'
  | 'DATE'
  | 'DATETIME'

export interface AiConfigurationFieldSettings {
  maxLength: number | null
  precision: number | null
  scale: number | null
  minimum: string | null
  maximum: string | null
}

export interface AiConfigurationFieldPreview {
  configRootId: string
  moduleId: string
  expectedDraftRevision: number
  nextDraftRevision: number
  moduleCode: string
  fieldCode: string
  fieldName: string
  fieldType: AiConfigurationFieldType
  required: boolean
  settings: AiConfigurationFieldSettings
}

export interface AiConfigurationFieldResult {
  configRootId: string
  moduleId: string
  draftRevision: number
  moduleCode: string
  fieldId: string
  fieldCode: string
  fieldName: string
  fieldType: AiConfigurationFieldType
  required: boolean
  settings: AiConfigurationFieldSettings
  sortOrder: number
  fieldVersion: number
  draftStatus: 'DRAFT'
}

export interface AiConfigurationFieldProposal {
  id: string
  sessionId: string
  turnId: string
  state: 'CLARIFICATION_REQUIRED' | 'PENDING' | 'EXECUTING' | 'SUCCEEDED' | 'FAILED' | 'REJECTED' | 'EXPIRED'
  revision: number
  moduleCode: string
  preview: AiConfigurationFieldPreview | null
  confidence: number
  clarification: string | null
  expiresAt: string
  result: AiConfigurationFieldResult | null
  errorCode: string | null
  requestId: string
  traceId: string
}

export type AiConfigurationArtifactOperation = Extract<
  AiOperation,
  | 'CONFIG_SELECTION_FIELD_DRAFT'
  | 'CONFIG_PAGE_LAYOUT_DRAFT'
  | 'CONFIG_FILTER_SCENARIO_DRAFT'
  | 'CONFIG_FIELD_PERMISSION_STAGE_DRAFT'
>
export type AiConfigurationArtifactState =
  | 'CLARIFICATION_REQUIRED'
  | 'PENDING'
  | 'EXECUTING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'REJECTED'
  | 'EXPIRED'
export type AiConfigurationArtifactKind =
  | 'SELECTION_FIELD'
  | 'PAGE_LAYOUT'
  | 'FILTER_SCENARIO'
  | 'FIELD_PERMISSION_STAGE'
export type AiSelectionFieldType = 'RADIO' | 'MULTI_SELECT'
export type AiPageType = 'LIST' | 'FORM' | 'DETAIL'
export type AiPageLabelPosition = 'TOP' | 'LEFT'
export type AiPageDensity = 'DEFAULT' | 'COMPACT'

export interface AiConfigurationArtifactOption {
  code: string
  label: string
  semanticKey: string | null
  color: string | null
  defaultOption: boolean
  sortOrder: number
}

export interface AiConfigurationSelectionPreview {
  sortOrder: number
  fieldCode: string
  fieldName: string
  fieldType: AiSelectionFieldType
  required: boolean
  dictionaryCode: string
  dictionaryName: string
  options: AiConfigurationArtifactOption[]
  maxSelections: number | null
}

export interface AiConfigurationPageSection {
  code: string
  title: string
  fieldCodes: string[]
  sortOrder: number
}

export interface AiConfigurationPageLayout {
  columns: number
  gap: number
  labelPosition: AiPageLabelPosition
  density: AiPageDensity
  stickyActions: boolean
  pageSize: number | null
  searchEnabled: boolean | null
  filterEnabled: boolean | null
  sections: AiConfigurationPageSection[]
  sectionCount: number
  fieldCount: number
  redacted: boolean
}

export interface AiConfigurationPagePreview {
  pageId: string
  pageCode: string
  pageType: AiPageType
  pageVersion: number
  layout: AiConfigurationPageLayout
}

export interface AiConfigurationFilterScenarioPreview {
  pageId: string
  pageCode: string
  pageVersion: number
  makeDefault: boolean
  scenario: SharedFilterScenario
  resolvedState: AiConfigurationFilterScenarioState
  resolvedLayout: Record<string, unknown>
}

export interface AiConfigurationFilterScenarioState {
  filterScenarios: SharedFilterScenario[]
  defaultFilterScenarioCode: string | null
}

export interface AiConfigurationFieldPermissionStagePreview {
  fieldId: string
  fieldCode: string
  fieldName: string
  fieldVersion: number
  stageRead: boolean
  stageWrite: boolean
  expectedReadPermissionMode: FieldPermissionMode
  expectedWritePermissionMode: FieldPermissionMode
  readPermissionMode: FieldPermissionMode
  writePermissionMode: FieldPermissionMode
  readPermissionCode: string | null
  writePermissionCode: string | null
}

export interface AiConfigurationArtifactPreview {
  operation: AiConfigurationArtifactOperation
  configRootId: string
  moduleId: string
  moduleCode: string
  expectedDraftRevision: number
  nextDraftRevision: number
  selectionField: AiConfigurationSelectionPreview | null
  pageLayout: AiConfigurationPagePreview | null
  filterScenario: AiConfigurationFilterScenarioPreview | null
  fieldPermissionStage: AiConfigurationFieldPermissionStagePreview | null
}

export interface AiConfigurationArtifactDictionaryResult {
  dictionaryId: string
  dictionaryCode: string
  dictionaryName: string
  version: number
}

export interface AiConfigurationArtifactOptionResult extends AiConfigurationArtifactOption {
  optionId: string
  version: number
}

export interface AiConfigurationSelectionResult {
  dictionary: AiConfigurationArtifactDictionaryResult
  options: AiConfigurationArtifactOptionResult[]
  fieldId: string
  fieldCode: string
  fieldName: string
  fieldType: AiSelectionFieldType
  required: boolean
  dictionaryId: string
  sortOrder: number
  maxSelections: number | null
  version: number
}

export interface AiConfigurationPageResult {
  pageId: string
  pageCode: string
  pageType: AiPageType
  version: number
  layout: AiConfigurationPageLayout
}

export interface AiConfigurationFilterScenarioResult {
  pageId: string
  pageCode: string
  version: number
  state: AiConfigurationFilterScenarioState
}

export interface AiConfigurationFieldPermissionStageResult {
  fieldId: string
  fieldCode: string
  fieldName: string
  version: number
  readPermissionMode: FieldPermissionMode
  writePermissionMode: FieldPermissionMode
  readPermissionCode: string | null
  writePermissionCode: string | null
}

export interface AiConfigurationArtifactResult {
  operation: AiConfigurationArtifactOperation
  configRootId: string
  moduleId: string
  moduleCode: string
  draftRevision: number
  selectionField: AiConfigurationSelectionResult | null
  pageLayout: AiConfigurationPageResult | null
  filterScenario: AiConfigurationFilterScenarioResult | null
  fieldPermissionStage: AiConfigurationFieldPermissionStageResult | null
  draftStatus: 'DRAFT'
}

export interface AiConfigurationArtifactProposal {
  id: string
  sessionId: string
  turnId: string
  state: AiConfigurationArtifactState
  revision: number
  operation: AiConfigurationArtifactOperation
  artifactKind: AiConfigurationArtifactKind
  moduleCode: string | null
  preview: AiConfigurationArtifactPreview | null
  confidence: number
  clarification: string | null
  expiresAt: string
  result: AiConfigurationArtifactResult | null
  errorCode: string | null
  requestId: string
  traceId: string
}

export type AiWorkDraftOperation = Extract<AiOperation, 'WORK_TASK_DRAFT' | 'WORK_DAILY_REPORT_DRAFT'>
export type AiWorkProposalState =
  | 'CLARIFICATION_REQUIRED'
  | 'PENDING'
  | 'EXECUTING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'STALE'
  | 'PERMISSION_DENIED'

export interface AiWorkTaskDraftPreview {
  title: string
  description: string | null
  assigneeMemberId: string
  assigneeDisplayName: string | null
  projectId: string | null
  projectDisplayName: string | null
  dueAt: string | null
}

export interface AiWorkDailyReportDraftPreview {
  workDate: string
  completedWork: string
  plannedWork: string
  blockers: string | null
}

export interface AiWorkProposalPreview {
  operation: AiWorkDraftOperation
  task: AiWorkTaskDraftPreview | null
  dailyReport: AiWorkDailyReportDraftPreview | null
}

export interface AiWorkTaskDraftResult {
  taskId: string
  version: number
  title: string
  description: string | null
  status: 'OPEN'
  assigneeMemberId: string
  projectId: string | null
  dueAt: string | null
  createdAt: string
  updatedAt: string
}

export interface AiWorkDailyReportDraftResult {
  reportId: string
  version: number
  authorMemberId: string
  workDate: string
  completedWork: string
  plannedWork: string
  blockers: string | null
  status: 'DRAFT'
  createdAt: string
  updatedAt: string
}

export interface AiWorkProposalResult {
  operation: AiWorkDraftOperation
  task: AiWorkTaskDraftResult | null
  dailyReport: AiWorkDailyReportDraftResult | null
}

export interface AiWorkProposal {
  id: string
  sessionId: string
  turnId: string
  state: AiWorkProposalState
  revision: number
  operation: AiWorkDraftOperation
  preview: AiWorkProposalPreview | null
  confidence: number
  clarification: string | null
  expiresAt: string
  result: AiWorkProposalResult | null
  errorCode: string | null
  requestId: string
  traceId: string
}

export type AiGeneratedDraftOperation = Extract<
  AiOperation,
  'FLOW_DEFINITION_DRAFT' | 'CONFIG_REPORT_DRAFT' | 'CONFIG_PRINT_TEMPLATE_DRAFT'
>
export type AiGeneratedDraftProposalState =
  | 'CLARIFICATION_REQUIRED'
  | 'PENDING'
  | 'EXECUTING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'STALE'
  | 'PERMISSION_DENIED'

export interface AiFlowDefinitionDraftPreview {
  name: string
  approverMemberIds: string[]
}

export interface AiReportDefinitionDraftPreview {
  code: string
  name: string
  description: string | null
  dataSourceId: string
  outputFieldCodes: string[]
}

export interface AiPrintTemplateDraftPreview {
  moduleCode: string
  code: string
  name: string
  paperSize: 'A4' | 'A5'
  orientation: 'PORTRAIT' | 'LANDSCAPE'
  title: string
  fieldCodes: string[]
  footer: string | null
}

export type AiGeneratedDraftPreview =
  | { operation: 'FLOW_DEFINITION_DRAFT'; flowDefinition: AiFlowDefinitionDraftPreview; reportDefinition: null; printTemplate: null }
  | { operation: 'CONFIG_REPORT_DRAFT'; flowDefinition: null; reportDefinition: AiReportDefinitionDraftPreview; printTemplate: null }
  | { operation: 'CONFIG_PRINT_TEMPLATE_DRAFT'; flowDefinition: null; reportDefinition: null; printTemplate: AiPrintTemplateDraftPreview }

export interface AiFlowDefinitionDraftResult extends AiFlowDefinitionDraftPreview {
  definitionId: string
  revision: number
  updatedAt: string
  published: false
}

export interface AiReportDefinitionDraftResult extends AiReportDefinitionDraftPreview {
  reportId: string
  draftVersion: number
  version: number
  createdAt: string
  updatedAt: string
  published: false
}

export interface AiPrintTemplateDraftResult extends AiPrintTemplateDraftPreview {
  templateId: string
  moduleId: string
  status: 'DISABLED'
  version: number
  updatedAt: string
  published: false
}

export type AiGeneratedDraftResult =
  | { operation: 'FLOW_DEFINITION_DRAFT'; flowDefinition: AiFlowDefinitionDraftResult; reportDefinition: null; printTemplate: null }
  | { operation: 'CONFIG_REPORT_DRAFT'; flowDefinition: null; reportDefinition: AiReportDefinitionDraftResult; printTemplate: null }
  | { operation: 'CONFIG_PRINT_TEMPLATE_DRAFT'; flowDefinition: null; reportDefinition: null; printTemplate: AiPrintTemplateDraftResult }

export interface AiGeneratedDraftProposal {
  id: string
  sessionId: string
  turnId: string
  state: AiGeneratedDraftProposalState
  revision: number
  operation: AiGeneratedDraftOperation
  preview: AiGeneratedDraftPreview | null
  confidence: number
  clarification: string | null
  expiresAt: string
  result: AiGeneratedDraftResult | null
  errorCode: string | null
  requestId: string
  traceId: string
}

export interface AiMessage {
  id: string
  role: 'USER' | 'ASSISTANT' | string
  content: string
  createdAt: string
}

export interface AiTurn {
  id: string
  status: 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'RETRYABLE' | string
  answer?: string | null
  responseSummary?: string | null
  errorCode: string | null
  retryable: boolean
  tool?: AiToolResult | null
  contextResult?: AiContextResult | null
  confirmation?: AiConfirmation | null
  configurationProposal?: AiConfigurationFieldProposal | null
  artifactProposal?: AiConfigurationArtifactProposal | null
  workProposal?: AiWorkProposal | null
  generatedDraftProposal?: AiGeneratedDraftProposal | null
  returnedRows?: number
  requestId?: string
  traceId?: string
  createdAt?: string
  finishedAt?: string | null
}

export interface AiSessionDetail {
  session: AiSession
  messages: AiMessage[]
  turns: AiTurn[]
}

export interface CreateAiSessionInput {
  title: string
}

export interface SubmitAiMessageInput {
  content: string
}
