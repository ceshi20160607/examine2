export interface ConfiguredModuleGroup {
  id: number
  code: string
  name: string
  sortOrder: number
  status: string
}

export interface ConfiguredModule {
  id: number
  groupId: number
  code: string
  name: string
  status: string
  draftRevision: number
  version: number
}

export interface ConfiguredField {
  id: number
  code: string
  name: string
  fieldType: string
  required: boolean
  uniqueValue?: boolean
  searchable?: boolean
  dictionaryId?: number
  referenceModuleId?: number
  sortOrder: number
  status: string
  configJson: string
  version: number
}

export interface ConfiguredPage {
  id: number
  pageType: string
  name: string
  layoutJson: string
  version: number
}

export interface ConfiguredModuleMenu {
  id: number
  moduleId: number
  parentId?: number
  code: string
  name: string
  icon?: string
  routePath: string
  sortOrder: number
  visible: boolean
  status: 'ACTIVE' | 'DISABLED'
  version: number
}

export interface ConfiguredAction {
  id: number
  code: string
  name: string
  location: string
  status: string
  configJson: string
  version: number
}

export interface ModuleOverview {
  groups: ConfiguredModuleGroup[]
  modules: ConfiguredModule[]
}

export interface AiModelGrant {
  id: number
  modelId: number
  systemId: number
  systemCode?: string
  systemName?: string
  usageLimit: { dailyTokenLimit?: number; concurrencyLimit?: number }
  status: string
  version: number
  grantedAt: string
}

export interface AiModel {
  id: number
  activeGrantId?: number
  code: string
  name: string
  provider: string
  modelName: string
  endpointUrl?: string
  credentialReferenceType: string
  credentialAvailable: boolean
  capabilities: string[]
  limitPolicy: {
    dailyTokenLimit?: number
    concurrencyLimit?: number
    logMasking?: boolean
    dataResidency?: string
  }
  status: string
  version: number
  grants: AiModelGrant[]
}

export interface AiSystemOption { id: number; code: string; name: string }

export interface AiPlatformOverview {
  models: AiModel[]
  systems: AiSystemOption[]
}

export interface AiTool {
  id: number
  toolType: 'QUERY' | 'WRITE' | 'FLOW_DRAFT' | 'REPORT' | 'ERROR_EXPLAIN'
  resourceType: 'MODULE'
  resourceId: string
  actionCode: string
  fieldCodes: string[]
  requestedDataScope: 'CURRENT' | 'ALL'
  requiresConfirmation: boolean
  version: number
}

export interface AiAgentVersion {
  id: number
  versionNumber: number
  draftRevision: number
  snapshotHash: string
  publishedByMemberId: number
  publishedAt: string
  current: boolean
}

export interface AiAgent {
  id: number
  code: string
  name: string
  description?: string
  modelGrantId: number
  draftRevision: number
  systemPrompt: string
  contextPolicy: Record<string, unknown>
  confirmationPolicy: Record<string, unknown>
  fallbackPolicy: Record<string, unknown>
  status: 'DRAFT' | 'PUBLISHED'
  version: number
  tools: AiTool[]
  versions: AiAgentVersion[]
}

export interface AiModuleOption {
  code: string
  name: string
  actions: string[]
  fields: string[]
}

export interface AiSystemOverview {
  availableModels: AiModel[]
  modules: AiModuleOption[]
  agents: AiAgent[]
}

export interface AiIssue { code: string; message: string; toolIndex?: number }

export interface AiToolPreview {
  toolId: number
  toolType: string
  resourceId: string
  actionCode: string
  fieldAuthorization: Record<string, {
    readable: boolean
    writable: boolean
    maskStrategy?: string
    contributingRoleIds: number[]
    reason: string
  }>
  dataAuthorization: Record<string, unknown>
  requiresConfirmation: boolean
  valid: boolean
  issues: AiIssue[]
}

export interface AiAgentPreview {
  agentId: number
  draftRevision: number
  valid: boolean
  issues: AiIssue[]
  finalModel?: AiModel
  tools: AiToolPreview[]
  permissionSnapshot: Record<string, unknown>
  confirmationPolicy: Record<string, unknown>
}

export interface AiQueryAgentOption {
  agentId: number
  agentCode: string
  agentName: string
  versionId: number
  versionNumber: number
  moduleCodes: string[]
}

export interface AiQueryConversationSummary {
  id: number
  agentId: number
  agentVersionId: number
  agentName?: string
  title: string
  status: string
  entryContext: Record<string, unknown>
  updatedAt: string
}

export interface AiQueryOverview {
  agents: AiQueryAgentOption[]
  conversations: AiQueryConversationSummary[]
}

export interface AiQueryScope {
  systemId: number
  tenantId: number
  moduleCode: string
  actionCode: string
  lifecycleState: string
  tenantScope: string
  search: string
  filters: Array<{ fieldCode: string; operator: string; value?: string }>
  fieldCodes: string[]
  dataScopes: Record<string, unknown>
  sourcePath: string
}

export interface AiQuerySource {
  recordId: number
  recordNumber?: string
  title: string
  path: string
  fields: Record<string, unknown>
}

export interface AiQueryResult {
  conversationId: number
  executionId: number
  outcome: 'SUCCEEDED' | 'REFUSED' | 'DEGRADED'
  answer: string
  errorCode?: string
  retryable: boolean
  scope: AiQueryScope
  metricDefinition?: string
  sources: AiQuerySource[]
  persistedAt: string
}

export interface AiQueryMessage {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  structuredContent: Record<string, unknown>
  modelUsage: Record<string, unknown>
  errorCode?: string
  createdAt: string
}

export interface AiQueryConversationDetail {
  conversation: AiQueryConversationSummary
  messages: AiQueryMessage[]
}

export interface AiWriteAgentOption {
  agentId: number
  agentCode: string
  agentName: string
  agentVersionId: number
  versionNumber: number
  moduleCodes: string[]
}

export interface AiWriteOverview {
  agents: AiWriteAgentOption[]
}

export interface AiWriteFieldCandidate {
  code: string
  label: string
  fieldType: string
  required: boolean
  writable: boolean
  value?: unknown
  confidence: number
  recognized: boolean
  note: string
}

export interface AiWriteCandidate {
  pendingWriteId: number
  conversationId: number
  executionId: number
  agentId: number
  agentVersionId: number
  moduleCode: string
  outcome: 'READY' | 'DEGRADED' | 'REFUSED' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'
  status: 'PENDING_CONFIRMATION' | 'NEEDS_MANUAL_INPUT' | 'VALIDATION_FAILED' | 'REFUSED' | 'CONFIRMED' | 'CANCELLED'
  errorCode?: string
  message: string
  retryable: boolean
  confirmationRequired: boolean
  businessWritten: boolean
  sourceType: 'TEXT' | 'FILE'
  sourceReference?: string
  inputText: string
  proposedTitle: string
  proposedRecordNumber?: string
  proposedStatus: string
  fields: AiWriteFieldCandidate[]
  unknownSegments: string[]
  recordId?: number
  recordPath?: string
  confirmedAt?: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface AiWriteResult {
  pendingWriteId: number
  status: string
  outcome: string
  errorCode?: string
  message: string
  recordId?: number
  recordPath?: string
  record: Record<string, unknown>
  businessWritten: boolean
  version: number
  confirmedAt?: string
}

export interface ModuleDraft {
  module: ConfiguredModule
  fields: ConfiguredField[]
  pages: ConfiguredPage[]
  menus: ConfiguredModuleMenu[]
  actions: ConfiguredAction[]
  published: boolean
}

export interface PublicationIssue {
  path: string
  code: string
  message: string
}

export interface PublicationCheck {
  valid: boolean
  draftRevision: number
  issues: PublicationIssue[]
  indexProjectionPlans: string[]
}

export interface ConfiguredModuleRule {
  id: number
  moduleId: number
  code: string
  name: string
  ruleType: string
  triggerEvent: string
  expressionText: string
  messageTemplate?: string
  testStatus: 'NOT_TESTED' | 'EXECUTED'
  lastTestInputJson?: string
  lastTestResultJson?: string
  lastTestedByMemberId?: number
  lastTestedAt?: string
  sortOrder: number
  status: 'ACTIVE' | 'DISABLED'
  version: number
}

export interface ConfiguredQueryIndex {
  id: number
  moduleId: number
  code: string
  name: string
  uniqueIndex: boolean
  status: 'ACTIVE' | 'DISABLED'
  version: number
}

export interface ConfiguredQueryIndexField {
  id: number
  queryIndexId: number
  fieldId: number
  sortOrder: number
  sortDirection: 'ASC' | 'DESC'
}

export interface QueryIndexDraft {
  index: ConfiguredQueryIndex
  fields: ConfiguredQueryIndexField[]
  fieldCodes: string[]
  scope: string
  projectionPlan: string
}

export interface RuleIndexDraft {
  rules: ConfiguredModuleRule[]
  indexes: QueryIndexDraft[]
}

export interface RuleTestResult {
  matched: boolean
  effectType: string
  targetField?: string
  message?: string
  conditions: Array<{ field: string; operator: string; matched: boolean; actual: unknown; expected: unknown }>
}

export interface PublishedModuleVersion {
  versionId: number
  versionNumber: number
  draftRevision: number
  changeSummary: string
  publishedAt: string
  current: boolean
  publicationVersion: number
}

export interface TenantExtensionField extends ConfiguredField {
  source: 'MAIN' | 'TENANT'
  sourceTenantId: number
  mandatory: boolean
}

export interface TenantExtensionApplicationGrant {
  applicationId: number
  applicationCode: string
  applicationName: string
  grantId: number
  actionCode: string
  fieldCodes: string[]
}

export interface TenantExtensionVersion {
  versionId: number
  versionNumber: number
  baseModuleVersionId: number
  publishedAt: string
  current: boolean
}

export interface TenantExtensionModule {
  moduleId: number
  moduleCode: string
  moduleName: string
  baseTenantId: number
  baseTenantName: string
  baseVersionId: number
  baseVersionNumber: number
  extension?: {
    id: number
    draftRevision: number
    currentVersionId?: number
    status: 'DRAFT' | 'PUBLISHED' | 'DELETED'
    version: number
  }
  draft: {
    fields: TenantExtensionField[]
    pages: Array<{ pageType: string; fieldCodes: string[]; source: 'TENANT' }>
    applicationBindings: Array<{
      applicationId: number
      grantIds: number[]
      actionCodes: string[]
      fieldCodes: string[]
    }>
  }
  mergedPreview: {
    fields: TenantExtensionField[]
    pages: ConfiguredPage[]
    applicationBindings: unknown[]
    tenantExtensionApplied: boolean
  }
  availableApplicationGrants: TenantExtensionApplicationGrant[]
  versions: TenantExtensionVersion[]
}

export interface TenantExtensionCheck {
  valid: boolean
  draftRevision: number
  baseVersionId: number
  issues: PublicationIssue[]
  mergedPreview: TenantExtensionModule['mergedPreview']
}

export interface ConfiguredDictionary {
  id: number
  code: string
  name: string
  hierarchical: boolean
  status: string
  version: number
}

export interface ConfiguredDictionaryItem {
  id: number
  dictionaryId: number
  parentId?: number
  code: string
  label: string
  pathCode: string
  color?: string
  sortOrder: number
  status: string
  version: number
}

export interface DictionaryDraft {
  dictionary: ConfiguredDictionary
  items: ConfiguredDictionaryItem[]
  currentVersionId?: number
  currentVersionNumber?: number
  publicationVersion?: number
}

export interface DictionaryPublicationCheck {
  valid: boolean
  draftRevision: number
  issues: PublicationIssue[]
}

export interface DictionaryVersion {
  versionId: number
  versionNumber: number
  draftRevision: number
  publishedAt: string
  current: boolean
  publicationVersion: number
}

export interface RuntimeDictionary {
  dictionaryId: number
  code: string
  name: string
  hierarchical: boolean
  versionId: number
  versionNumber: number
  publicationVersion: number
  items: ConfiguredDictionaryItem[]
}

export interface RuntimeModuleCatalogItem {
  groupId: number
  groupCode: string
  groupName: string
  groupSortOrder: number
  moduleId: number
  moduleCode: string
  moduleName: string
  menuId?: number
  menuParentId?: number
  menuCode: string
  menuName: string
  menuIcon?: string
  menuRoutePath: string
  menuSortOrder: number
}

export interface CommandCenterItem {
  id: string
  groupCode: 'MENU' | 'CREATE' | 'MODULE' | 'RECORD'
  groupName: string
  label: string
  description: string
  target: 'HOME' | 'RUNTIME_MODULE' | 'RUNTIME_CREATE' | 'RUNTIME_RECORD' | 'MODULE_CONFIG' | 'AUDIT' | 'SYSTEM_SETTINGS'
    | 'PLATFORM_SYSTEMS' | 'PLATFORM_DASHBOARD' | 'PLATFORM_FLOWS' | 'PLATFORM_APPLICATIONS' | 'PLATFORM_TASKS'
    | 'PLATFORM_AI' | 'PLATFORM_TODOS' | 'PLATFORM_MESSAGES' | 'PLATFORM_PROFILE' | 'PLATFORM_ADMIN'
  moduleCode?: string
  recordId?: number
}

export interface CommandCenterState {
  favoriteIds: string[]
  recentIds: string[]
  version?: number
}

export interface CommandCenterView {
  results: CommandCenterItem[]
  favorites: CommandCenterItem[]
  recent: CommandCenterItem[]
  state: CommandCenterState
  invalidated: Array<{ id: string; reason: string }>
}

export interface CommandCenterExecution {
  command: CommandCenterItem
  state: CommandCenterState
  invalidated: Array<{ id: string; reason: string }>
}

export interface RuntimeField extends ConfiguredField {
  access?: {
    channel: 'PAGE' | 'APPLICATION' | 'FILE'
    readable: boolean
    writable: boolean
    maskStrategy?: string
    reason: string
  }
}
export interface RuntimeAction extends ConfiguredAction {}

export interface RuntimeModuleConfiguration {
  moduleId: number
  versionId: number
  versionNumber: number
  publicationVersion: number
  configuration: {
    module: ConfiguredModule
    fields: RuntimeField[]
    pages: ConfiguredPage[]
    menus: ConfiguredModuleMenu[]
    rules?: ConfiguredModuleRule[]
    queryIndexes?: ConfiguredQueryIndex[]
    queryIndexFields?: ConfiguredQueryIndexField[]
    actions: RuntimeAction[]
    tenantExtensionApplied?: boolean
    tenantExtensionVersionId?: number
    tenantExtensionVersionNumber?: number
    tenantExtensionBaseModuleVersionId?: number
  }
}

export interface RuntimeRecord {
  id: number
  recordNumber?: string
  title: string
  status: string
  ownerMemberId?: number
  departmentId?: number
  participantMemberIds: number[]
  archived: boolean
  archivedAt?: string
  deleted: boolean
  deletedAt?: string
  version: number
  createdAt: string
  updatedAt: string
  dataTenantId: number
  dataTenantName: string
  ownedByCurrentTenant: boolean
  shared: boolean
  shareId?: number
  shareStatus?: 'ACTIVE' | 'EXPIRED' | 'REVOKED'
  sharedActions: string[]
  shareExpiresAt?: string
  fields: Record<string, unknown>
}

export interface RuntimeRecordList {
  records: RuntimeRecord[]
  total: number
  page: number
  pageSize: number
}

export interface RuntimeListFilter {
  fieldCode: string
  operator: 'EQ' | 'NE' | 'CONTAINS' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'EMPTY' | 'NOT_EMPTY'
  value: string
}

export interface RuntimeListView {
  id: number
  code: string
  name: string
  search: string
  filters: RuntimeListFilter[]
  sortField: string
  sortDirection: 'ASC' | 'DESC'
  visibleFieldCodes: string[]
  fixedFieldCodes: string[]
  pageSize: number
  defaultView: boolean
  version: number
  updatedAt: string
}

export interface RuntimeRecordTimelineChange {
  fieldCode: string
  fieldName: string
  valueType: string
  beforeValue?: unknown
  afterValue?: unknown
  masked: boolean
}

export interface RuntimeRecordTimelineEntry {
  eventId: number
  eventCode: 'BUSINESS_RECORD_CREATED' | 'BUSINESS_RECORD_UPDATED' | 'BUSINESS_RECORD_ARCHIVED' | 'BUSINESS_RECORD_DELETED' | 'BUSINESS_RECORD_RESTORED' | 'BUSINESS_RECORD_TRANSFERRED' | 'BUSINESS_RECORD_CONVERTED'
  label: string
  actorAccountId?: number
  actorMemberId?: number
  actorDisplayName: string
  occurredAt: string
  changes: RuntimeRecordTimelineChange[]
}

export interface RuntimeRecordTimeline {
  entries: RuntimeRecordTimelineEntry[]
}

export interface RuntimeRecordLifecycleImpact {
  recordId: number
  action: 'ARCHIVE' | 'DELETE' | 'RESTORE'
  currentState: 'ACTIVE' | 'ARCHIVED' | 'DELETED'
  outgoingRelationCount: number
  incomingRelationCount: number
  participantCount: number
  warning: string
}

export interface RuntimeRecordTransferPreview {
  recordId: number
  version: number
  fromOwnerMemberId?: number
  fromOwnerName?: string
  toOwnerMemberId: number
  toOwnerName: string
  fromDepartmentId?: number
  fromDepartmentName?: string
  toDepartmentId?: number
  toDepartmentName?: string
  fromParticipantMemberIds: number[]
  toParticipantMemberIds: number[]
}

export interface RuntimeRecordConversionMapping {
  targetFieldCode: string
  targetFieldName: string
  required: boolean
  sourceFieldCode?: string
  value?: unknown
  status: 'READY' | 'MISSING' | 'INVALID' | 'DENIED' | 'EMPTY'
  message: string
}

export interface RuntimeRecordConversionPreview {
  sourceRecordId: number
  sourceVersion: number
  targetModuleCode: string
  targetModuleName: string
  targetTitle: string
  mappings: RuntimeRecordConversionMapping[]
  issues: string[]
  alreadyConverted: boolean
  executable: boolean
}

export interface RuntimeRecordConversionExecution {
  conversionId: number
  targetModuleCode: string
  targetRecord: RuntimeRecord
}

export interface RuntimeRecordConversionLink {
  conversionId: number
  direction: 'SOURCE' | 'TARGET'
  moduleCode: string
  moduleName: string
  recordId: number
  recordTitle: string
  convertedAt: string
}

export interface RuntimeRecordConversions {
  links: RuntimeRecordConversionLink[]
}

export interface TenantShareTarget {
  tenantId: number
  tenantCode: string
  tenantName: string
}

export interface RuntimeTenantShare {
  id: number
  sourceTenantId: number
  sourceTenantName: string
  targetTenantId: number
  targetTenantName: string
  recordId: number
  moduleCode: string
  allowedActions: string[]
  status: 'ACTIVE' | 'EXPIRED' | 'REVOKED'
  effectiveAt: string
  expiresAt?: string
  revokedAt?: string
  grantedByMemberId: number
  version: number
}

export interface AuditEvent {
  id: number
  traceId: string
  requestId?: string
  actorAccountId?: number
  memberId?: number
  eventCode: string
  objectType?: string
  objectId?: string
  resultCode: string
  permissionSnapshot?: string
  detailJson?: string
  occurredAt: string
}

export interface AuditFieldChange {
  id: number
  fieldCode: string
  valueType: string
  beforeValueJson: string
  afterValueJson: string
  sensitivity: 'NORMAL' | 'SENSITIVE'
  masked: boolean
}

export interface AuditEventDetail {
  event: AuditEvent
  fieldChanges: AuditFieldChange[]
  sensitiveValuesVisible: boolean
}

export interface AuditRetentionPreflight {
  objectType: string
  objectId: string
  allowed: boolean
  auditEventCount: number
  fieldChangeCount: number
  existingMarker: boolean
  approvalRequired: boolean
  referenceImpactConfirmationRequired: boolean
  blockers: string[]
}

export interface AuditRetentionMarker {
  id: number
  objectType: string
  objectId: string
  businessKey?: string
  snapshotHash: string
  purgeReason: string
  purgedByAccountId: number
  purgedAt: string
}

export interface AuditEventList {
  events: AuditEvent[]
  total: number
  page: number
  pageSize: number
}

export interface SystemSettings {
  systemId: number
  code: string
  name: string
  tenantMode: 'SINGLE' | 'MULTI'
  status: string
  version: number
}

export interface SystemTenant {
  id: number
  code: string
  name: string
  main: boolean
  status: 'ACTIVE' | 'DISABLED'
  current: boolean
  version: number
}

export interface SystemDomain {
  id: number
  domainType: 'SUBDOMAIN' | 'CUSTOM'
  host: string
  basePath: string
  tlsRequired: boolean
  status: 'PENDING_VERIFICATION' | 'VERIFICATION_FAILED' | 'VERIFIED' | 'PUBLISHED'
  verifiedAt?: string
  verificationToken: string
  verificationUrl: string
  version: number
}

export interface TenantModeMigrationPreflight {
  fromMode: 'SINGLE' | 'MULTI'
  toMode: 'SINGLE' | 'MULTI'
  allowed: boolean
  blockers: string[]
  impact: {
    tenantCount: number
    secondaryTenantCount: number
    nonMainRecordCount: number
    activeShareCount: number
  }
  migrationSteps: string[]
}

export interface TenantModeMigration {
  id: number
  fromMode: 'SINGLE' | 'MULTI'
  toMode: 'SINGLE' | 'MULTI'
  status: 'PENDING_APPROVAL' | 'RUNNING' | 'COMPLETED' | 'REJECTED' | 'BLOCKED' | 'FAILED'
  jobId?: number
  requestedByMemberId: number
  impactSnapshotJson: string
  rollbackSnapshotJson?: string
  startedAt?: string
  finishedAt?: string
  version: number
}

export interface AuthorizationDepartment {
  id: number
  parentId?: number
  code: string
  name: string
  pathCode: string
  sortOrder: number
  status: string
  version: number
}

export interface AuthorizationMember {
  tenantMemberId: number
  systemMemberId: number
  accountId: number
  displayName: string
  employeeNumber?: string
  departmentId?: number
  tenantAdmin: boolean
  status: string
  roleIds: number[]
  version: number
}

export interface AuthorizationPermissionConfig {
  resourceType: string
  resourceCode: string
  actionCode: string
  dataScopeType: string
  dataScopeJson?: string
}

export interface AuthorizationFieldPolicy {
  resourceCode: string
  fieldCode: string
  channel: string
  readable: boolean
  writable: boolean
  maskStrategy?: string
}

export interface AuthorizationRole {
  id: number
  code: string
  name: string
  description?: string
  builtIn: boolean
  status: string
  permissions: AuthorizationPermissionConfig[]
  fieldPolicies: AuthorizationFieldPolicy[]
  version: number
}

export interface AuthorizationResource {
  resourceType: string
  resourceCode: string
  name: string
  actions: string[]
  fields: string[]
}

export interface SystemAuthorizationOverview {
  departments: AuthorizationDepartment[]
  members: AuthorizationMember[]
  roles: AuthorizationRole[]
  resources: AuthorizationResource[]
  permissionVersion: number
}

export interface PermissionPreview {
  permissionVersion: number
  targetMember: AuthorizationMember
  roles: Array<{
    roleId: number
    roleCode: string
    roleName: string
    contributedPermissions: AuthorizationPermissionConfig[]
  }>
  actions: Array<{
    resourceType: string
    resourceCode: string
    resourceName: string
    actionCode: string
    allowed: boolean
    contributingRoleIds: number[]
    dataScope?: { mode: string; terms: Array<{ type: string; roleIds: number[] }> }
    reason: string
  }>
  fields: Array<{
    resourceCode: string
    fieldCode: string
    channel: string
    readable: boolean
    writable: boolean
    maskStrategies: string[]
    restrictingRoleIds: number[]
    reason: string
  }>
  mergeRules: Record<string, string>
}

export interface AccountMfaMethod {
  id: number
  methodType: string
  displayLabel: string
  status: string
  verifiedAt?: string
}

export interface AccountSession {
  id: number
  contextType: 'PLATFORM' | 'SYSTEM'
  systemId?: number
  tenantId?: number
  mfaLevel: string
  accessExpiresAt: string
  refreshExpiresAt: string
  createdAt: string
  current: boolean
  revoked: boolean
}

export interface AccountProfile {
  accountId: number
  username: string
  displayName: string
  email?: string
  mobile?: string
  locale: string
  timezone: string
  status: string
  lastLoginAt?: string
  credentialVersion: number
  version: number
  mfaMethods: AccountMfaMethod[]
  sessions: AccountSession[]
}

export interface SystemDirectoryItem {
  systemId: number
  systemCode: string
  systemName: string
  tenantMode: 'SINGLE' | 'MULTI'
  defaultTenantId: number
  defaultTenantName: string
  accessible: boolean
  accessRequestId?: number
  accessRequestStatus?: 'PENDING' | 'APPROVED' | 'REJECTED'
}

export interface SystemAccessRequest {
  id: number
  systemId: number
  systemName: string
  tenantId: number
  tenantName: string
  accountId: number
  accountDisplayName: string
  identityProvider: string
  externalUserId?: string
  requestReason: string
  requestedRole?: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  decidedByMemberId?: number
  decisionComment?: string
  approvedRoleIds: number[]
  approvedDataScope: Record<string, unknown>
  requestTraceId: string
  decisionTraceId?: string
  createdAt: string
  decidedAt?: string
  version: number
}

export interface SystemRoleOption {
  id: number
  code: string
  name: string
}

export interface SsoProviderTestCheck {
  code: string
  status: 'PASSED' | 'FAILED'
  message: string
}

export interface SsoProviderTestReport {
  status: 'PASSED' | 'FAILED'
  requestId: string
  failureCode?: string
  testedAt: string
  checks: SsoProviderTestCheck[]
}

export interface SsoProviderVersion {
  id: number
  versionNumber: number
  protocol: string
  issuer: string
  clientId: string
  clientSecretRef?: string
  protocolConfig: Record<string, unknown>
  allowedDomains: string[]
  attributeMapping: Record<string, unknown>
  jitPolicy: Record<string, unknown>
  mfaPolicy: Record<string, unknown>
  callbackUris: string[]
  testStatus: 'NOT_TESTED' | 'PASSED' | 'FAILED'
  testReport?: SsoProviderTestReport
  status: 'DRAFT' | 'PUBLISHED'
  createdAt: string
  publishedAt?: string
}

export interface SsoProviderAdmin {
  id: number
  code: string
  name: string
  status: 'DRAFT' | 'PUBLISHED'
  publishedVersionId?: number
  publishedVersionNumber?: number
  versions: SsoProviderVersion[]
}

export interface ContextSettingCategory {
  code: string
  name: string
}

export interface ContextSetting {
  id: number
  contextType: 'PLATFORM' | 'SYSTEM'
  platformId: number
  systemId?: number
  category: string
  settingKey: string
  valueType: string
  value: unknown
  sensitive: boolean
  status: string
  version: number
  updatedAt: string
}

export interface SystemIdentityProviderOption {
  providerId: number
  code: string
  name: string
  protocol: string
  publishedVersionId: number
  publishedVersionNumber: number
  allowedDomains: string[]
}

export interface SystemDepartmentOption {
  id: number
  code: string
  name: string
  pathCode: string
}

export interface IdentityMappingPreflightItem {
  rowNumber: number
  externalUserId: string
  externalDepartment: string
  email?: string
  mobile?: string
  employeeNo?: string
  displayName: string
  mfaLevel: string
  device: string
  requestId: string
  accountId?: number
  systemMemberId?: number
  tenantMemberId?: number
  departmentId?: number
  departmentName?: string
  outcome: 'READY' | 'ACCESS_REQUEST' | 'MANUAL_REVIEW' | 'CONFLICT'
  plannedAction: string
  reasons: string[]
}

export interface IdentityMappingPreflightReport {
  preflightId: string
  providerId: number
  providerVersionId: number
  providerCode: string
  tenantDomain: string
  createdAt: string
  summary: Record<string, number>
  items: IdentityMappingPreflightItem[]
}

export interface SystemIdentityMappingConfiguration {
  settingId: number
  version: number
  systemId: number
  tenantId: number
  providerId: number
  providerVersionId: number
  providerCode: string
  providerName: string
  tenantDomain: string
  departmentMappings: Record<string, number>
  matchOrder: string[]
  jitPolicy: 'ACCESS_REQUEST' | 'CREATE_MEMBER' | 'CREATE_ACCOUNT_AND_MEMBER'
  latestPreflight?: IdentityMappingPreflightReport
  confirmedJobId?: number
  confirmedAt?: string
  updatedAt: string
}

export interface IdentityMappingJobItem {
  rowNumber: number
  externalUserId: string
  status: string
  action: string
  accountId?: number
  systemMemberId?: number
  tenantMemberId?: number
  accessRequestId?: number
  errorCode?: string
  message: string
}

export interface IdentityMappingJob {
  id: number
  status: string
  progressCurrent: number
  progressTotal: number
  summary: Record<string, unknown>
  items: IdentityMappingJobItem[]
  createdAt: string
  finishedAt?: string
}

export interface IdentityMappingLog {
  id: number
  occurredAt: string
  identityProvider: string
  externalUserId: string
  mfaLevel: string
  device: string
  requestId: string
  traceId: string
  resultCode: string
  failureReason?: string
  systemMemberId?: number
  jobId?: number
}

export interface RateLimitState {
  current: number
  limit: number
  retryAfterSeconds: number
  allowed: boolean
  source: string
}

export interface FoundationCacheProbe {
  epoch: number
  source: 'DATABASE' | 'REDIS'
  cacheKey: string
  roleIds: number[]
  permissionCount: number
}

export interface FoundationCommandRecord {
  id: number
  contextKey: string
  operationCode: string
  idempotencyKey: string
  status: string
  responseCode: string
  expiresAt: string
  version: number
}

export interface FoundationControlOverview {
  applicationCode: string
  operationCode: string
  rateLimit: RateLimitState
  permissionCache: FoundationCacheProbe
  recentCommands: FoundationCommandRecord[]
}

export interface FoundationCommandResult {
  idempotencyRecordId: number
  outboxId: number
  idempotencyKey: string
  operationCode: string
  resultReference: string
  status: string
  replayed: boolean
  committedAt: string
  extensionStatus: string
}

export interface FoundationFeatureFlag {
  id: number
  flagKey: string
  enabled: boolean
  targetType: 'SYSTEM' | 'APPLICATION' | 'MODULE' | 'ROLE'
  targetCodes: string[]
  effectiveFrom?: string
  effectiveUntil?: string
  fallbackEnabled: boolean
  stableVariant: string
  status: string
  version: number
  updatedAt: string
}

export interface FoundationFeatureFlagResolution {
  flagKey: string
  enabled: boolean
  permissionStillRequired: boolean
  source: string
  reason: string
  version: number
}

export interface FoundationQuota {
  id: number
  quotaCode: string
  periodType: 'TOTAL' | 'DAY' | 'MONTH'
  hardLimit: number
  warningThreshold?: number
  periodKey: string
  usedValue: number
  reservedValue: number
  remaining: number
  status: string
  version: number
}

export interface FoundationSequence {
  id: number
  sequenceCode: string
  pattern: string
  resetPeriod: 'NONE' | 'DAY' | 'MONTH' | 'YEAR'
  periodKey: string
  currentValue: number
  stepValue: number
  status: string
  version: number
}

export interface FoundationSequenceValue {
  sequenceCode: string
  value: string
  periodKey: string
  numericValue: number
  version: number
}

export interface BackgroundJobHandler {
  jobType: string
  name: string
  description: string
}

export interface BackgroundJobRedisState {
  status: string
  progressCurrent: number
  progressTotal: number
  attemptCount: number
  updatedAt?: string
  queued: boolean
  locked: boolean
  source: 'REDIS' | 'DATABASE_FALLBACK'
}

export interface BackgroundJob {
  id: number
  contextType: string
  systemId?: number
  tenantId?: number
  jobType: string
  sourceType: string
  sourceId?: string
  status: 'QUEUED' | 'RUNNING' | 'RETRY_WAIT' | 'SUCCEEDED' | 'FAILED' | 'PERMANENT_FAILED'
  progressCurrent: number
  progressTotal: number
  maxAttempts: number
  attemptCount: number
  nextRunAt?: string
  heartbeatAt?: string
  resultSummary: Record<string, unknown>
  errorCode?: string
  errorMessage?: string
  createdAt: string
  startedAt?: string
  finishedAt?: string
  redisState: BackgroundJobRedisState
}

export interface BackgroundJobAttempt {
  id: number
  attemptNumber: number
  workerId: string
  status: string
  startedAt: string
  finishedAt?: string
  errorCode?: string
  errorMessage?: string
}

export interface BackgroundJobItem {
  id: number
  itemKey: string
  rowNumber?: number
  status: string
  result: Record<string, unknown>
  errorCode?: string
  errorMessage?: string
  updatedAt?: string
}

export interface BackgroundJobDetail {
  job: BackgroundJob
  attempts: BackgroundJobAttempt[]
  items: BackgroundJobItem[]
  authorizationSnapshot: Record<string, unknown>
}

export interface OperationsHealthItem {
  id: number
  code: string
  name: string
  category: string
  status: 'PASSED' | 'FAILED'
  message: string
  metric: Record<string, unknown>
  checkedAt: string
}

export interface OperationsJobOverview {
  queued: number
  running: number
  retryWait: number
  failed: number
  succeeded: number
  total: number
}

export interface OperationsErrorSummary {
  requestId: string
  source: string
  code: string
  message: string
  status: string
  occurredAt: string
  targetType: string
  targetId: string
}

export interface OperationsHealth {
  id: number
  status: 'READY' | 'NOT_READY'
  ready: boolean
  releaseVersion: string
  migrationVersion: string
  requestId: string
  startedAt: string
  finishedAt: string
  items: OperationsHealthItem[]
  jobs: OperationsJobOverview
  recentErrors: OperationsErrorSummary[]
}

export interface OperationsObservationEntry {
  source: string
  kind: string
  status: string
  summary: string
  occurredAt: string
  detail: Record<string, unknown>
}

export interface OperationsObservationStage {
  code: string
  name: string
  observed: boolean
  evidence: string
}

export interface OperationsObservation {
  requestId: string
  traceId: string
  systemId: number
  tenantId: number
  accountId?: number
  observable: boolean
  stages: OperationsObservationStage[]
  logs: OperationsObservationEntry[]
  metrics: Record<string, number>
  jobs: Record<string, unknown>[]
  audits: Record<string, unknown>[]
  applicationCalls: Record<string, unknown>[]
  flowEvents: Record<string, unknown>[]
}

export interface OperationsRuntimeManifest {
  environmentCode: string
  backendVersion: string
  frontendVersion: string
  databaseVersion: string
  configVersion: string
  artifactSha256: string
  frontendSmokeUrl: string
  artifactDigestConfigured: boolean
  ready: boolean
}

export interface OperationsRelease {
  id: number
  versionName: string
  artifactSha256: string
  databaseVersion: string
  configVersion: string
  frontendVersion: string
  databaseRollbackStrategy: 'FORWARD_FIX' | 'REVERSIBLE'
  releaseNotes: string
  status: string
  createdByAccountId: number
  createdAt: string
}

export interface OperationsDeploymentStep {
  code: string
  name: string
  version: string
  durationMillis: number
  status: 'PASSED' | 'FAILED'
  health: string
  realEntrySmoke: string
  evidence: string
}

export interface OperationsDeploymentApproval {
  reference: string
  approvedByAccountId: number
  approvedAt: string
  confirmation: string
}

export interface OperationsDeployment {
  id: number
  releaseId: number
  fromReleaseId?: number
  releaseVersion: string
  environmentCode: string
  deploymentType: 'DEPLOY' | 'ROLLBACK'
  status: 'RUNNING' | 'SUCCESS' | 'FAILED' | 'ROLLBACK_READY'
  failureCode: string
  failureMessage: string
  approval: OperationsDeploymentApproval
  steps: OperationsDeploymentStep[]
  rollbackPoint: Record<string, unknown>
  requestedByAccountId: number
  startedAt: string
  finishedAt: string
  createdAt: string
}

export interface OperationsDeploymentOverview {
  runtime: OperationsRuntimeManifest
  deployConfirmation: string
  releases: OperationsRelease[]
  deployments: OperationsDeployment[]
}

export interface OperationsBackupItem {
  id: number
  itemType: 'DATABASE' | 'FILE_STORAGE' | 'CONFIGURATION' | 'SECRET_REFERENCES'
  storageUri: string
  sizeBytes: number
  sha256: string
  status: string
}

export interface OperationsBackup {
  id: number
  sourceReleaseId: number
  systemId: number
  backupType: string
  status: string
  consistencyPoint: string
  manifestHash: string
  encryptionKeyReference: string
  secretMaterialStrategy: string
  retentionUntil: string
  requestedByAccountId: number
  startedAt: string
  finishedAt: string
  createdAt: string
  items: OperationsBackupItem[]
}

export interface OperationsRestoreDrill {
  id: number
  backupId: number
  environmentCode: string
  status: string
  verification: Record<string, unknown>
  requestedByAccountId: number
  startedAt: string
  finishedAt: string
  createdAt: string
}

export interface OperationsUpgradeGate {
  code: string
  name: string
  status: string
  evidence: string
}

export interface OperationsUpgradePreflight {
  ready: boolean
  sourceDatabaseVersion: string
  targetDatabaseVersion: string
  sourceConfigVersion: string
  targetConfigVersion: string
  checks: OperationsUpgradeGate[]
}

export interface OperationsUpgradeStep {
  stepNumber: number
  stepType: string
  sourceVersion: string
  targetVersion: string
  mapping: Record<string, unknown>
  status: string
  startedAt: string
  finishedAt: string
  errorMessage?: string
}

export interface OperationsUpgrade {
  id: number
  releaseId: number
  backupId: number
  systemId: number
  status: string
  impactReport: Record<string, unknown>
  rollbackPoint: Record<string, unknown>
  requestedByAccountId: number
  startedAt: string
  finishedAt: string
  createdAt: string
  steps: OperationsUpgradeStep[]
}

export interface OperationsContinuityOverview {
  runtimeDatabaseVersion: string
  runtimeConfigVersion: string
  backupConfirmation: string
  restoreConfirmation: string
  upgradeConfirmation: string
  backups: OperationsBackup[]
  restoreDrills: OperationsRestoreDrill[]
  upgrades: OperationsUpgrade[]
}

export type FlowNodeType = 'START' | 'END' | 'APPROVAL' | 'GATEWAY' | 'SUBFLOW' | 'FORM_TASK'
  | 'NOTIFICATION' | 'UPDATE_FIELD' | 'WAIT_TIMER' | 'WEBHOOK' | 'EXTERNAL' | 'AI'

export interface FlowNodeInput {
  nodeKey: string
  nodeType: FlowNodeType
  name: string
  positionX: number
  positionY: number
  assigneePolicy?: Record<string, unknown>
  formPolicy?: Record<string, unknown>
  timeoutPolicy?: Record<string, unknown>
  exceptionPolicy?: Record<string, unknown>
  config: Record<string, unknown>
}

export interface FlowEdgeInput {
  edgeKey: string
  sourceNodeKey: string
  targetNodeKey: string
  conditionExpression?: string
  priorityOrder: number
  config: Record<string, unknown>
}

export interface FlowNodeView extends FlowNodeInput { id: number; version: number }
export interface FlowEdgeView extends FlowEdgeInput { id: number; version: number }
export interface FlowIssue { code: string; location: string; message: string }
export interface FlowSimulationStep { nodeKey: string; nodeType: FlowNodeType; outcome: string }
export interface FlowSimulationResult {
  flowId: number
  draftRevision: number
  successful: boolean
  steps: FlowSimulationStep[]
  issues: FlowIssue[]
  variables: Record<string, unknown>
}
export interface FlowVersionView {
  id: number
  versionNumber: number
  draftRevision: number
  definitionHash: string
  changeSummary: string
  publishedByAccountId: number
  publishedAt: string
  snapshot: Record<string, unknown>
  simulationResult: FlowSimulationResult
}
export interface FlowDefinitionView {
  id: number
  contextType: 'PLATFORM' | 'SYSTEM'
  platformId: number
  systemId?: number
  tenantId?: number
  code: string
  name: string
  description?: string
  draftRevision: number
  status: string
  version: number
  currentVersionId?: number
  nodes: FlowNodeView[]
  edges: FlowEdgeView[]
  versions: FlowVersionView[]
}
export interface FlowPublicationCheck {
  flowId: number
  draftRevision: number
  valid: boolean
  issues: FlowIssue[]
}
export interface FlowPublicationResult {
  flowId: number
  versionId: number
  versionNumber: number
  draftRevision: number
  definitionHash: string
  previousVersionId?: number
  simulation: FlowSimulationResult
}

export interface FlowBindingView {
  id: number
  systemId: number
  ownerTenantId: number
  scopeType: 'DEFAULT' | 'TENANT_OVERRIDE'
  moduleId: number
  actionId?: number
  triggerEvent: string
  flowId: number
  flowVersionId: number
  flowVersionNumber: number
  executionMode: string
  priorityOrder: number
  conditionExpression?: string
  mutuallyExclusive: boolean
  status: string
  version: number
  updatedAt: string
}
export interface FlowBindingList {
  currentTenant: FlowBindingView[]
  inheritedDefaults: FlowBindingView[]
}
export interface FlowBindingResolution {
  resolutionId: number
  bindingId: number
  source: 'DEFAULT' | 'TENANT_OVERRIDE'
  sourceTenantId: number
  requestedTenantId: number
  flowId: number
  flowVersionId: number
  flowVersionNumber: number
  definitionHash: string
  definitionSnapshot: Record<string, unknown>
  executionMode: string
  mutuallyExclusive: boolean
  resolutionReason: string
  resolvedAt: string
}

export interface FlowRuntimeCandidate {
  id: number
  candidateType: string
  candidateId: string
  resolutionReason: string
  createdAt: string
}
export interface FlowRuntimeTask {
  id: number
  nodeKey: string
  taskType: string
  status: string
  assigneeAccountId?: number
  assigneeSnapshot: Record<string, unknown>
  dueAt?: string
  claimedAt?: string
  completedAt?: string
  version: number
  candidates: FlowRuntimeCandidate[]
}
export interface FlowRuntimeAction {
  id: number
  taskId?: number
  nodeKey: string
  actionCode: string
  comment?: string
  input: Record<string, unknown>
  result: Record<string, unknown>
  idempotencyKey: string
  actedByAccountId: number
  actedAt: string
}
export interface FlowRuntimeException {
  id: number
  nodeKey: string
  exceptionType: string
  errorCode: string
  errorMessage: string
  policyAction: string
  status: string
  resolvedByAccountId?: number
  resolutionComment?: string
  occurredAt: string
  resolvedAt?: string
  version: number
}
export interface FlowRuntimeInstance {
  id: number
  contextType: 'PLATFORM' | 'SYSTEM'
  platformId: number
  systemId?: number
  tenantId?: number
  flowId: number
  flowVersionId: number
  flowVersionNumber: number
  definitionHash: string
  definitionSnapshot: Record<string, unknown>
  businessType?: string
  businessId?: string
  businessSnapshot: Record<string, unknown>
  title: string
  currentNodeKey?: string
  currentNodeName?: string
  status: string
  startedByAccountId: number
  startedAt: string
  finishedAt?: string
  errorCode?: string
  errorMessage?: string
  version: number
  variables: Record<string, unknown>
  tasks: FlowRuntimeTask[]
  actions: FlowRuntimeAction[]
  exceptions: FlowRuntimeException[]
  allowedActions: string[]
  nextStep: string
}
export interface FlowRuntimeActionResult {
  actionId: number
  replayed: boolean
  instance: FlowRuntimeInstance
}
export interface FlowManualNodePreview {
  instanceId: number
  instanceStatus: string
  currentNodeKey: string
  suspendedTaskId: number
  assigneeAccountId: number
  position: 'BEFORE_CURRENT'
  reason: string
  statusMappings: Record<string, Record<string, string>>
  allowed: boolean
  checks: string[]
}
export interface FlowManualNodeResult {
  taskId: number
  actionId: number
  instance: FlowRuntimeInstance
}
export interface TodoItemView {
  id: number
  contextType: 'PLATFORM' | 'SYSTEM'
  platformId: number
  systemId?: number
  tenantId?: number
  assigneeAccountId: number
  todoType: string
  sourceType: string
  sourceId: string
  title: string
  summary: string
  targetRoute?: string
  priority: string
  dueAt?: string
  status: string
  completedAt?: string
  createdAt: string
  updatedAt?: string
  version: number
  availableActions: string[]
  freshnessReason?: string
}
export interface TodoHandleResult {
  todo: TodoItemView
  resultReference: string
  targetResult?: FlowRuntimeActionResult
}

export interface WorkProjectMember { id: number; accountId: number; projectRole: string; status: string }
export interface WorkTaskGroup { id: number; name: string; sortOrder: number; status: string; version: number }
export interface WorkTaskHistory {
  id: number
  actionCode: string
  before: Record<string, unknown>
  after: Record<string, unknown>
  comment?: string
  changedByAccountId: number
  changedAt: string
}
export interface WorkTask {
  id: number
  contextType: 'PLATFORM' | 'SYSTEM'
  platformId: number
  systemId?: number
  tenantId?: number
  projectId?: number
  taskGroupId?: number
  parentTaskId?: number
  title: string
  description?: string
  taskType: string
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'
  status: 'BACKLOG' | 'TODO' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED' | 'CANCELLED'
  progressPercent: number
  ownerAccountId: number
  startAt?: string
  dueAt?: string
  completedAt?: string
  customValues: Record<string, unknown>
  version: number
  collaboratorAccountIds: number[]
  history: WorkTaskHistory[]
}
export interface WorkProject {
  id: number
  contextType: 'PLATFORM' | 'SYSTEM'
  platformId: number
  systemId?: number
  tenantId?: number
  code: string
  name: string
  description?: string
  ownerAccountId: number
  startDate?: string
  dueDate?: string
  progressPercent: number
  status: string
  version: number
  members: WorkProjectMember[]
  taskGroups: WorkTaskGroup[]
  tasks: WorkTask[]
}

export interface WorkLogRevision {
  id: number
  revisionNumber: number
  snapshot: Record<string, unknown>
  revisionReason: string
  revisedByAccountId: number
  revisedAt: string
}
export interface WorkLog {
  id: number
  contextType: 'PLATFORM' | 'SYSTEM'
  platformId: number
  systemId?: number
  tenantId?: number
  authorAccountId: number
  workDate: string
  title: string
  content: string
  durationMinutes?: number
  status: 'DRAFT' | 'SUBMITTED' | 'WITHDRAWN'
  customValues: Record<string, unknown>
  version: number
  createdAt: string
  updatedAt: string
  revisions: WorkLogRevision[]
}

export interface WorkPublicationVersion {
  version: number
  basedOnVersion?: number
  changeSummary: string
  publishedAt: string
  publishedByAccountId: number
  snapshot: Record<string, unknown>
}
export interface WorkConfigurationField {
  id: number
  contextType: 'PLATFORM' | 'SYSTEM'
  platformId: number
  systemId?: number
  tenantId?: number
  targetType: 'PROJECT' | 'TASK' | 'LOG' | 'STATISTICS'
  fieldCode: string
  fieldName: string
  fieldType: string
  required: boolean
  sortOrder: number
  settings: Record<string, unknown>
  status: 'DRAFT' | 'PUBLISHED'
  rowVersion: number
  currentPublicationVersion?: number
  publicationVersions: WorkPublicationVersion[]
  source: 'PLATFORM_DEFAULT' | 'SYSTEM_OVERRIDE'
}
export interface WorkConfiguration {
  ownDrafts: WorkConfigurationField[]
  inheritedPlatformDefaults: WorkConfigurationField[]
  effectivePublished: WorkConfigurationField[]
}
export interface WorkCalendarTask {
  id: number
  title: string
  status: string
  priority: string
  projectId?: number
  ownerAccountId: number
  dueAt?: string
  completedAt?: string
}
export interface WorkCalendarLog {
  id: number
  title: string
  status: string
  authorAccountId: number
  durationMinutes: number
  workDate: string
}
export interface WorkCalendarMilestone {
  id: number
  name: string
  status: string
  dueDate: string
}
export interface WorkCalendarDay {
  date: string
  tasksDue: number
  tasksCompleted: number
  overdueTasks: number
  riskTasks: number
  logCount: number
  logMinutes: number
  projectMilestones: number
  detailAvailable: boolean
  tasks: WorkCalendarTask[]
  logs: WorkCalendarLog[]
  milestones: WorkCalendarMilestone[]
}
export interface WorkCalendar {
  contextType: 'PLATFORM' | 'SYSTEM'
  systemId?: number
  tenantId?: number
  from: string
  to: string
  projectId?: number
  accountId?: number
  detailAvailable: boolean
  summary: Omit<WorkCalendarDay, 'date' | 'detailAvailable' | 'tasks' | 'logs' | 'milestones'>
  days: WorkCalendarDay[]
}

export interface ApplicationCallbackView {
  id: number
  callbackType: 'EVENT' | 'RESULT'
  url: string
  eventCodes: string[]
  signingSecretRef: string
  timeoutMillis: number
  maxAttempts: number
  status: string
  version: number
}

export interface ApplicationGrantFieldView {
  id: number
  fieldCode: string
  readable: boolean
  writable: boolean
  maskStrategy?: string
}

export interface ApplicationGrantView {
  id: number
  targetType: 'PLATFORM' | 'SYSTEM'
  targetSystemId?: number
  targetTenantId?: number
  resourceType: string
  resourceId: string
  actionCode: string
  dataScope: Record<string, unknown>
  rateLimit: Record<string, unknown>
  status: string
  version: number
  fields: ApplicationGrantFieldView[]
}

export interface ApplicationCredentialView {
  id: number
  credentialVersion: number
  clientId: string
  secretReference: string
  secretHint: string
  validFrom: string
  expiresAt?: string
  status: string
  revokedAt?: string
  createdAt: string
}

export interface ApplicationVersionView {
  id: number
  versionNumber: number
  draftRevision: number
  snapshotHash: string
  publishedByAccountId: number
  publishedAt: string
  current: boolean
}

export interface ApplicationStatusEvent {
  id: number
  eventCode: string
  resultCode: string
  actorAccountId: number
  occurredAt: string
  detail: Record<string, unknown>
}

export interface ApplicationView {
  id: number
  contextType: 'PLATFORM' | 'SYSTEM'
  platformId: number
  ownerSystemId?: number
  ownerTenantId?: number
  code: string
  name: string
  description?: string
  applicationType: 'SERVICE' | 'WEBHOOK'
  draftRevision: number
  status: 'DRAFT' | 'ACTIVE' | 'DISABLED'
  version: number
  currentVersionId?: number
  callbacks: ApplicationCallbackView[]
  grants: ApplicationGrantView[]
  credentials: ApplicationCredentialView[]
  versions: ApplicationVersionView[]
  statusHistory: ApplicationStatusEvent[]
  createdAt: string
  updatedAt: string
}

export interface ApplicationCredentialSecret {
  credentialId: number
  credentialVersion: number
  clientId: string
  clientSecret: string
  secretReference: string
  secretHint: string
  shownOnce: boolean
}

export interface ApplicationCreateResult {
  application: ApplicationView
  issuedCredential: ApplicationCredentialSecret
}

export interface ApplicationRotateResult {
  application: ApplicationView
  issuedCredential: ApplicationCredentialSecret
}

export interface ApplicationPublicationCheck {
  applicationId: number
  draftRevision: number
  valid: boolean
  issues: { code: string; message: string }[]
}

export interface ApplicationPublishResult {
  application: ApplicationView
  versionId: number
  versionNumber: number
  snapshotHash: string
  previousVersionId?: number
}

export interface ApplicationCallResult {
  requestId: string
  replayed: boolean
  applicationId: number
  grantId: number
  credentialVersion: number
  resourceType: string
  resourceId: string
  actionCode: string
  targetReference: string
  result: unknown
  source: Record<string, unknown>
}

export interface ApplicationCallLogView {
  id: number
  requestId: string
  traceId: string
  credentialVersion: number
  grantId?: number
  sourceAddress?: string
  targetSystemId?: number
  targetTenantId?: number
  resourceType: string
  resourceId: string
  actionCode: string
  status: 'PENDING' | 'SUCCESS' | 'FAILED'
  responseCode?: string
  durationMillis?: number
  errorMessage?: string
  targetReference?: string
  replayCount: number
  permissionSnapshot: Record<string, unknown>
  response?: unknown
  calledAt: string
  finishedAt?: string
}

export interface MessageTemplateView {
  id: number
  contextType: 'PLATFORM' | 'SYSTEM'
  platformId: number
  systemId?: number
  tenantId?: number
  code: string
  name: string
  channel: 'IN_APP' | 'EMAIL' | 'SMS' | 'WEBHOOK'
  draftRevision: number
  subjectTemplate?: string
  contentTemplate: string
  requiredVariables: string[]
  status: 'DRAFT' | 'PUBLISHED'
  publishedVersion?: number
  publishedVersionId?: number
  publishedAt?: string
  version: number
  updatedAt: string
}

export interface MessageDeliveryView {
  id: number
  channel: string
  destinationMasked?: string
  status: string
  attemptCount: number
  nextAttemptAt?: string
  providerMessageId?: string
  providerReceipt?: string
  lastError?: string
  sentAt?: string
  deliveredAt?: string
  version: number
}

export interface MessageItemView {
  id: number
  contextType: 'PLATFORM' | 'SYSTEM'
  platformId: number
  systemId?: number
  tenantId?: number
  templateVersionId?: number
  sourceType: string
  sourceId?: string
  subject: string
  content: string
  targetType?: string
  targetId?: string
  targetRoute?: string
  sensitivity: 'NORMAL' | 'IMPORTANT' | 'SENSITIVE'
  recipientStatus: 'UNREAD' | 'READ' | 'ARCHIVED'
  readAt?: string
  archivedAt?: string
  recipientVersion: number
  createdAt: string
  targetCurrentlyAccessible: boolean
  deliveries: MessageDeliveryView[]
}

export interface MessageInboxView {
  messages: MessageItemView[]
  unreadCount: number
}

export interface MessageOpenResult {
  messageId: number
  route: string
  switchSystemId?: number
  targetType: string
  targetId: string
}

export interface FileReferenceView {
  id: number
  contextType: 'PLATFORM' | 'SYSTEM'
  systemId?: number
  tenantId?: number
  ownerType: 'ACCOUNT' | 'FLOW_INSTANCE' | 'BUSINESS_RECORD' | 'WORK_TASK'
  ownerId: string
  fieldCode?: string
  referenceType: 'ATTACHMENT' | 'IMAGE' | 'DOCUMENT' | 'RESULT'
  createdByAccountId: number
  createdAt: string
}

export interface FileScanView {
  id: number
  scanner: string
  scanVersion: string
  status: 'CLEAN' | 'BLOCKED' | 'FAILED'
  resultCode: string
  resultDetail: string
  startedAt: string
  finishedAt: string
}

export interface ControlledFileView {
  id: number
  contextType: 'PLATFORM' | 'SYSTEM'
  platformId: number
  systemId?: number
  tenantId?: number
  uploadSessionId: number
  originalName: string
  contentType: string
  sizeBytes: number
  sha256: string
  scanStatus: 'SCANNING' | 'CLEAN' | 'BLOCKED' | 'FAILED'
  previewStatus: 'PENDING' | 'AVAILABLE' | 'UNSUPPORTED' | 'BLOCKED'
  status: 'QUARANTINED' | 'ACTIVE' | 'DELETED'
  uploadedByAccountId: number
  createdAt: string
  deletedAt?: string
  version: number
  references: FileReferenceView[]
  scans: FileScanView[]
}

export interface FileUploadSessionView {
  id: number
  originalName: string
  contentType: string
  expectedSize: number
  expectedSha256?: string
  status: string
  uploadToken: string
  shownOnce: boolean
  expiresAt: string
  version: number
}

export interface ImportTemplateColumn {
  code: string
  name: string
  fieldType: string
  required: boolean
  writable: boolean
  builtIn: boolean
}

export interface ImportTemplateView {
  moduleCode: string
  moduleId: number
  versionNumber: number
  columns: ImportTemplateColumn[]
  csvHeader: string
  sampleRow: string
}

export interface ImportRowView {
  id: number
  rowNumber: number
  operation: 'CREATE' | 'UPDATE' | 'SKIP' | string
  status: string
  targetRecordId?: number
  expectedRecordVersion?: number
  appliedRecordVersion?: number
  raw: Record<string, unknown>
  normalized: Record<string, unknown>
  errorCode?: string
  errorMessage?: string
  rollbackStatus?: string
  version: number
}

export interface ImportBatchView {
  id: number
  moduleCode: string
  mappingId: number
  sourceFileId: number
  jobId?: number
  mode: 'DRY_RUN' | 'EXECUTE'
  status: string
  totalRows: number
  validRows: number
  successRows: number
  failedRows: number
  columnMapping: Record<string, string>
  conflictPolicy: 'ERROR' | 'SKIP' | 'UPDATE'
  summary: Record<string, unknown>
  rows: ImportRowView[]
  createdAt: string
  startedAt?: string
  finishedAt?: string
  version: number
}

export interface ExportFieldView {
  code: string
  name: string
  fieldType: string
  builtIn: boolean
  readable: boolean
  masked: boolean
  maskStrategy?: string
}

export interface ExportEstimateView {
  moduleCode: string
  scope: 'CURRENT_FILTER' | 'SELECTED'
  estimatedRows: number
  maximumRows: number
  withinQuota: boolean
  availableFields: ExportFieldView[]
  selectedFields: ExportFieldView[]
  maskedFields: string[]
}

export interface ExportBatchView {
  id: number
  moduleCode: string
  jobId?: number
  status: string
  totalRows: number
  exportedRows: number
  resultFileId?: number
  errorMessage?: string
  filterSnapshot: Record<string, unknown>
  selectedFields: string[]
  createdAt: string
  startedAt?: string
  finishedAt?: string
  version: number
}

export type DashboardSourceType = 'PLATFORM_SYSTEMS' | 'MODULE_RECORDS' | 'MODULE_REPORT' | 'TODO_ITEMS'
  | 'MESSAGE_ITEMS' | 'WORK_PROJECTS'
export type DashboardComponentType = 'METRIC' | 'CHART' | 'LIST' | 'TODO' | 'QUICK_ENTRY'
  | 'KPI' | 'PROGRESS' | 'RANKING'

export interface DashboardDataSourceVersion {
  id: number
  versionNumber: number
  draftRevision: number
  definitionHash: string
  publishedAt: string
}
export interface DashboardDataSource {
  id: number
  contextType: 'PLATFORM' | 'SYSTEM'
  code: string
  name: string
  sourceType: DashboardSourceType
  draftRevision: number
  definition: Record<string, unknown>
  permissionPolicy: Record<string, unknown>
  status: 'DRAFT' | 'PUBLISHED'
  version: number
  updatedAt: string
  versions: DashboardDataSourceVersion[]
}
export interface DashboardComponentInput {
  componentKey: string
  componentType: DashboardComponentType
  title: string
  dataSourceId?: number
  layout: Record<string, unknown>
  queryParameters: Record<string, unknown>
  displayConfig: Record<string, unknown>
  drillTarget?: Record<string, unknown>
  sortOrder: number
}
export interface DashboardComponentView extends DashboardComponentInput {
  id: number
  version: number
}
export interface DashboardVersion {
  id: number
  versionNumber: number
  draftRevision: number
  snapshotHash: string
  publishedAt: string
  current: boolean
}
export interface DashboardDefinition {
  id: number
  contextType: 'PLATFORM' | 'SYSTEM'
  code: string
  name: string
  description?: string
  draftRevision: number
  status: 'DRAFT' | 'PUBLISHED'
  version: number
  updatedAt: string
  components: DashboardComponentView[]
  versions: DashboardVersion[]
}
export interface DashboardAdminOverview {
  contextType: 'PLATFORM' | 'SYSTEM'
  dataSources: DashboardDataSource[]
  dashboards: DashboardDefinition[]
}
export interface DashboardRuntimeComponent {
  componentKey: string
  componentType: DashboardComponentType
  title: string
  outcome: 'READY' | 'ERROR'
  value?: unknown
  items: Record<string, unknown>[]
  metricDefinition: string
  layout: Record<string, unknown>
  displayConfig: Record<string, unknown>
  drillTarget: Record<string, unknown>
  drillAvailable: boolean
  errorCode?: string
  message: string
  updatedAt: string
}
export interface DashboardRuntime {
  configured: boolean
  contextType: 'PLATFORM' | 'SYSTEM'
  dashboardId?: number
  versionId?: number
  versionNumber?: number
  name?: string
  description?: string
  publishedAt?: string
  components: DashboardRuntimeComponent[]
  message: string
}
export interface DashboardPreview {
  dashboardId: number
  draftRevision: number
  valid: boolean
  issues: { code: string; message: string; componentKey: string }[]
  components: DashboardRuntimeComponent[]
}

export interface KpiReminder {
  id: number
  todoId?: number
  messageId?: number
  recipientAccountId: number
  status: 'SENT' | 'ACKNOWLEDGED' | 'RESOLVED'
  sentAt: string
  acknowledgedAt?: string
}

export interface KpiResult {
  id: number
  periodKey: string
  dimensionKey: string
  actualValue: number
  targetValue: number
  achievementRate: number
  status: 'ACHIEVED' | 'UNDER_TARGET' | 'STALE'
  stale: boolean
  explanation: Record<string, unknown>
  calculatedAt: string
  drillItems: Record<string, unknown>[]
  drillAvailable: boolean
  reminders: KpiReminder[]
}

export interface KpiDefinition {
  id: number
  code: string
  name: string
  dataSourceVersionId: number
  targetValue: number
  targetOperator: 'GTE' | 'LTE'
  periodType: 'MONTH' | 'QUARTER' | 'YEAR'
  responsibleType: 'PERSON' | 'DEPARTMENT' | 'ROLE'
  responsibleIds: number[]
  visibilityPermission: Record<string, unknown>
  drillPermission: Record<string, unknown>
  reminderEnabled: boolean
  reminderRecipientAccountIds: number[]
  reminderBelowPercent: number
  status: 'ACTIVE' | 'INACTIVE'
  version: number
  updatedAt: string
  latestResult?: KpiResult
}

export interface KpiOverview {
  kpis: KpiDefinition[]
}

export interface KpiPreview {
  kpiId: number
  kpiVersion: number
  periodKey: string
  targetValue: number
  actualValue: number
  achievementRate: number
  status: 'ACHIEVED' | 'UNDER_TARGET'
  targetExplanation: string
  sourceExplanation: string
  calculatedAt: string
  sourceResult: ReportResult
}

export interface ReportFieldMetadata {
  code: string
  name: string
  fieldType: string
  searchable: boolean
  readable: boolean
  indexed: boolean
  builtIn: boolean
}
export interface ReportModuleMetadata {
  moduleId: number
  moduleCode: string
  moduleName: string
  versionId: number
  versionNumber: number
  fields: ReportFieldMetadata[]
}
export interface ReportMetadata {
  modules: ReportModuleMetadata[]
  metricOperations: ('COUNT' | 'SUM' | 'AVG' | 'MIN' | 'MAX')[]
  dimensionTypes: ('FIELD' | 'TIME' | 'PERSON' | 'DEPARTMENT' | 'STATUS')[]
  relationTypes: ('REFERENCE' | 'SUBTABLE' | 'CASCADE')[]
  maximumModules: number
  maximumScanRows: number
  arbitrarySqlAllowed: false
}
export interface ReportQueryPlan {
  mode: 'SINGLE_MODULE' | 'MULTI_MODULE'
  modules: Record<string, unknown>[]
  relations: Record<string, unknown>[]
  outputFields: string[]
  metric: Record<string, unknown>
  dimension: Record<string, unknown>
  fixedFilters: Record<string, unknown>[]
  sort: Record<string, unknown>
  timeField: Record<string, unknown>
  estimatedRows: number
  maximumScanRows: number
  steps: string[]
  permissionFilters: string[]
  arbitrarySqlAllowed: false
}
export interface ReportResult {
  outcome: 'READY'
  value?: unknown
  groups: { key?: unknown; label: string; value?: unknown; rowCount: number }[]
  items: Record<string, unknown>[]
  metricDefinition: string
  sourceFields: string[]
  permissionFilters: string[]
  queryPlan: ReportQueryPlan
  dataSourceVersionId?: number
  dataSourceVersionNumber?: number
  definitionHash?: string
  updatedAt: string
}
export interface ReportPreview {
  dataSourceId: number
  draftRevision: number
  valid: boolean
  issues: { code: string; message: string; path: string }[]
  queryPlan?: ReportQueryPlan
  sampleResult?: ReportResult
}

export interface PrintLayout {
  header?: string
  footer?: string
  signatureLabel?: string
  fieldCodes: string[]
  detailFieldCodes: string[]
  rowsPerPage: number
}
export interface PrintFieldOption { id: number; code: string; name: string; fieldType: string }
export interface PrintModuleOption {
  id: number; code: string; name: string; publishedVersionId: number; publishedVersionNumber: number
  fields: PrintFieldOption[]
}
export interface PrintTemplateVersion {
  id: number; versionNumber: number; draftRevision: number; snapshotHash: string; publishedAt: string; current: boolean
}
export interface PrintTemplateDefinition {
  id: number; moduleId: number; moduleCode: string; moduleName: string; code: string; name: string
  pageSize: 'A4' | 'A5'; orientation: 'PORTRAIT' | 'LANDSCAPE'; draftRevision: number
  layout: PrintLayout; status: 'DRAFT' | 'PUBLISHED'; version: number; currentVersionId?: number
  versions: PrintTemplateVersion[]; createdAt: string; updatedAt: string
}
export interface PrintAdminOverview { modules: PrintModuleOption[]; templates: PrintTemplateDefinition[] }
export interface PrintPreviewPageField { code: string; name: string; value: string; detail: boolean }
export interface PrintPreviewPage {
  pageNumber: number; pageCount: number; header?: string; recordNumber?: string; recordTitle: string
  fields: PrintPreviewPageField[]; signatureLabel?: string; footer?: string
}
export interface PrintPreview {
  templateId: number; draftRevision: number; previewHash: string; moduleVersionId: number
  moduleVersionNumber: number; sampleRecordId: number; visibleFieldCodes: string[]; omittedFieldCodes: string[]
  pages: PrintPreviewPage[]; renderedAt: string
}
export interface RuntimePrintTemplate {
  templateId: number; code: string; name: string; pageSize: string; orientation: string
  versionId: number; versionNumber: number; snapshotHash: string; publishedAt: string
}
export interface PrintJob {
  id: number; recordId: number; templateVersionId: number; templateVersionNumber: number; templateName: string
  status: string; outputFileId: number; outputFileName: string; previewPath: string; downloadPath: string
  pageCount: number; pages: PrintPreviewPage[]; authorizationSnapshot: Record<string, unknown>; recordSnapshot: Record<string, unknown>
  errorMessage?: string; createdAt: string; finishedAt: string
}

export interface OperationsSecretRef {
  id: number
  secretCode: string
  secretType: 'APPLICATION' | 'SSO' | 'WEBHOOK' | 'MODEL'
  provider: string
  referencePath: string
  currentVersion: string
  status: string
  lastVerifiedAt?: string
  updatedAt: string
}
export interface OperationsRotation {
  id: number
  secretRefId: number
  secretCode: string
  fromVersion: string
  toVersion: string
  status: string
  switched: boolean
  activeVersion: string
  consumerCodes: string[]
  consumerChecks: Record<string, unknown>[]
  securityRunId?: number
  oldVersionStatus: string
  failureCode?: string
  requestedAt: string
  finishedAt?: string
}
export interface OperationsFinding {
  id: number
  severity: string
  code: string
  title: string
  detail: string
  evidence: Record<string, unknown>
  status: string
  createdAt: string
}
export interface OperationsVerificationRun {
  id: number
  verificationType: 'SECURITY' | 'PERFORMANCE'
  scenarioCode: string
  status: string
  input: Record<string, unknown>
  result: Record<string, unknown>
  threshold: Record<string, unknown>
  startedAt: string
  finishedAt?: string
  findings: OperationsFinding[]
}
export interface OperationsSecurityOverview {
  secretRefs: OperationsSecretRef[]
  rotations: OperationsRotation[]
  securityRuns: OperationsVerificationRun[]
  performanceRuns: OperationsVerificationRun[]
  secretPolicy: string
  performancePolicy: string
}
export interface OperationsOneTimeSecret {
  secretRef: OperationsSecretRef
  rotation: OperationsRotation
  oneTimeSecret: string
  displayOnce: boolean
  notice: string
}
