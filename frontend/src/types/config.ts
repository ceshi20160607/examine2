export type DesiredStatus = 'ENABLED' | 'DISABLED' | 'ARCHIVED'
export type PageType = 'LIST' | 'FORM' | 'DETAIL'
export type FieldPermissionMode = 'INHERIT' | 'STAGED' | 'ENFORCED'

export interface ConfigRoot {
  systemId: string
  status: 'CLEAN' | 'DIRTY' | 'CHECKING' | 'CHECK_FAILED' | 'CHECKED' | 'PUBLISHING'
  draftRevision: string
  activeVersionId?: string
  baseVersionId?: string
  version: string
  lastCheck?: CheckSummary
}
export interface CheckSummary { id: string; status: string; blockerCount: number; warningCount: number; draftRevision: string; version: string }
export interface CheckIssue { id: string; severity: 'BLOCKER' | 'WARNING'; code: string; resourceType?: string; resourceId?: string; propertyPath?: string; message: string; suggestedAction?: string }
export interface CheckReport extends CheckSummary { draftChecksum: string; snapshotSizeBytes: number; expiresAt: string; issues: CheckIssue[] }
export interface ConfigVersion { id: string; versionNo: string; sourceType: 'PUBLISH' | 'ROLLBACK'; basedOnVersionId?: string; rollbackTargetVersionId?: string; snapshotChecksum: string; snapshotSizeBytes: number; publishedAt: string; publishedBy: string; reason: string; active: boolean }
export interface VersionDiff { fromVersionId: string; toVersionId: string; changes: Record<string, { added: string[]; removed: string[]; changed: string[] }> }
export interface PublishResult { version: ConfigVersion; root: ConfigRoot; authzEpoch: number }
export interface PermissionPreview {
  memberId: string
  tenantId: string
  permissionVersion: string
  root: boolean
  sourceRoles: { id: string; code: string; name: string; publishedVersion: string }[]
  dataScopes: { roleId: string; id: string; code: string; kind: string }[]
  active: PreviewTree
  draft: PreviewTree
}
export interface PreviewTree { versionId?: string; revision?: string; groups: PreviewGroup[] }
export interface PreviewGroup { id: string; code: string; name: string; sortOrder: number; modules: PreviewModule[] }
export interface PreviewModule { id: string; code: string; name: string; sortOrder: number; fields: string[]; actions: string[] }
export interface ConfigGroup { id: string; code: string; name: string; description?: string; iconKey?: string; sortOrder: number; status: DesiredStatus; version: string; updatedRevision: string }
export interface ConfigModule { id: string; groupId: string; code: string; name: string; description?: string; iconKey?: string; sortOrder: number; status: DesiredStatus; allowComments: boolean; allowTeam: boolean; version: string; updatedRevision: string }
export interface ConfigDictionary { id: string; code: string; name: string; type: string; category?: string; description?: string; status: DesiredStatus; version: string; updatedRevision: string }
export interface ConfigDictionaryItem { id: string; dictionaryId: string; parentId?: string; code: string; label: string; semanticKey?: string; color?: string; iconKey?: string; sortOrder: number; depth: number; isDefault: boolean; status: DesiredStatus; version: string; updatedRevision: string; children: ConfigDictionaryItem[] }
export interface ConfigField { id: string; moduleId: string; dictionaryId?: string; targetModuleId?: string; code: string; name: string; type: string; sortOrder: number; required: boolean; hidden: boolean; readonly: boolean; searchable: boolean; filterable: boolean; showInList: boolean; showInDetail: boolean; indexMode: string; status: DesiredStatus; readPermissionMode: FieldPermissionMode; writePermissionMode: FieldPermissionMode; properties: Record<string, unknown>; version: string; updatedRevision: string }
export interface SharedFilterScenario { code: string; name: string; filter: RuntimeFilterNode | null; sort: RuntimeSortItem[] }
export interface ConfigPageLayout extends Record<string, unknown> {
  filterScenarios?: SharedFilterScenario[]
  defaultFilterScenarioCode?: string | null
}
export interface ConfigPage { id: string; moduleId: string; code: string; name: string; type: PageType; isDefault: boolean; status: DesiredStatus; layout: ConfigPageLayout; version: string; updatedRevision: string }
export interface ConfigComponent { id: string; pageId: string; parentComponentId?: string; fieldId?: string; key: string; type: string; sortOrder: number; gridRow: number; gridColumn: number; gridSpan: number; properties: Record<string, unknown>; version: string; updatedRevision: string }
export interface ConfigAction { id: string; moduleId: string; code: string; name: string; type: string; placement: string; permissionCode: string; confirmMessage?: string; sortOrder: number; status: DesiredStatus; properties: Record<string, unknown>; version: string; updatedRevision: string }
export interface ConfigRule { id: string; moduleId: string; code: string; name: string; type: string; priority: number; condition: Record<string, unknown>; effects: Record<string, unknown>[]; status: DesiredStatus; version: string; updatedRevision: string }
export interface RuntimeNavigation { activeVersionId: string; versionNo: string; groups: RuntimeGroup[] }
export interface RuntimeGroup { id: string; code: string; name: string; iconKey?: string; sortOrder: number; modules: RuntimeModule[] }
export interface RuntimeModule { id: string; code: string; name: string; iconKey?: string; sortOrder: number; permissionCode: string; defaultPageCode?: string }
export interface RuntimeDefinition { activeVersionId: string; versionNo: string; module: Record<string, unknown>; fields: Record<string, unknown>[]; pages: Record<string, unknown>[]; components: Record<string, unknown>[]; actions: Record<string, unknown>[]; rules: Record<string, unknown>[]; dictionaries: Record<string, unknown>[]; dictionaryItems: Record<string, unknown>[]; recordsAvailable: boolean }
export interface RuntimeQueryLimits { defaultSize: number; maxSize: number; maxSorts: number }
export interface RuntimeFieldCapability {
  fieldCode: string
  fieldName: string
  logicalFieldId: string
  type: string
  mode: string
  readable: boolean
  writable: boolean
  sensitiveReadable: boolean
  sensitiveQueryable: boolean
  masked: boolean
  operators: string[]
  sortable: boolean
  showInList: boolean
  showInDetail: boolean
  options: Array<{ value: string; label: string; parentValue?: string | null }>
  schema: Record<string, unknown>
}
export interface RuntimePublishedPageSection { sectionCode: string; title: string; sortOrder: number; columns: number; collapsible: boolean; collapsed: boolean }
export interface RuntimePublishedPageField { fieldCode: string; sectionCode?: string | null; sortOrder: number; gridRow: number; gridColumn: number; gridSpan: number; width: number; fixed: 'NONE' | 'LEFT' | 'RIGHT' }
export interface RuntimePublishedPage { pageId: string; pageCode: string; type: PageType; density: 'DEFAULT' | 'COMPACT'; columns: number; gap: number; labelPosition: 'TOP' | 'LEFT'; stickyActions: boolean; pageSize: number; sections: RuntimePublishedPageSection[]; fields: RuntimePublishedPageField[] }
export interface RuntimePublishedCondition { join?: 'AND' | 'OR' | null; fieldCode?: string | null; operator?: string | null; value?: unknown; children: RuntimePublishedCondition[] }
export interface RuntimePublishedEffect { effect: 'VISIBLE' | 'REQUIRED' | 'READ_ONLY' | 'ACTION_ENABLED' | 'DELETE_ALLOWED' | 'APPROVAL_REQUIRED'; targetCode?: string | null; targetAction?: string | null; value: boolean }
export interface RuntimePublishedRule { ruleCode: string; type: string; priority: number; condition: RuntimePublishedCondition; effects: RuntimePublishedEffect[] }
export interface RuntimePublishedContract { schemaVersionId: string; pages: RuntimePublishedPage[]; rules: RuntimePublishedRule[] }
export interface RuntimeRuleDecision { hiddenFields: Set<string>; requiredFields: Set<string>; readonlyFields: Set<string>; disabledActions: Set<string>; deleteAllowed: boolean; approvalRequired: boolean }
export interface RuntimeRecordSchema {
  schemaVersionId: string
  moduleSnapshotId: string
  logicalModuleId: string
  checksum: string
  runtimeState: 'READY' | 'UNAVAILABLE'
  unavailableReason?: string
  authzEpoch: number
  fields: RuntimeFieldCapability[]
  actions: string[]
  queryLimits: RuntimeQueryLimits
  publishedRuntime?: RuntimePublishedContract
}
export interface RuntimeFilterPredicate { kind: 'PREDICATE'; fieldCode: string; operator: string; value?: unknown }
export interface RuntimeFilterGroup { kind: 'AND' | 'OR' | 'NOT'; children: RuntimeFilterNode[] }
export type RuntimeFilterNode = RuntimeFilterPredicate | RuntimeFilterGroup
export interface RuntimeSortItem { fieldCode: string; direction: 'ASC' | 'DESC'; nulls: 'FIRST' | 'LAST'; currency?: string }
export type RuntimeRecordScope = 'active' | 'archived' | 'trash' | 'draft'
export interface RuntimeRecordQuery {
  schemaVersionId: string
  page: number
  size: number
  recordScope: RuntimeRecordScope
  q: string | null
  filter: RuntimeFilterNode | null
  sort: RuntimeSortItem[]
  columns: string[]
  viewId: string | null
}
export interface RuntimeFieldValue { fieldCode: string; fieldName: string; type: string; value?: unknown; displayValue?: string; failureCorrelationId?: string }
export interface RuntimeSortAnchor { values: unknown[]; recordId: string }
export interface RuntimeRecordSummary { recordId: string; recordNo: string; version: number; status: string; title?: string; values: RuntimeFieldValue[]; sortAnchor?: RuntimeSortAnchor }
export interface RuntimeRecordPage { rows: RuntimeRecordSummary[]; page: number; size: number; total: number; queryHash?: string; querySnapshotToken?: string; invalidNodes: string[] }
export interface RuntimeMyDraftsQuery { page: number; size: number; q: string | null }
export interface RuntimeRecordNeighborInput { direction: 'PREVIOUS' | 'NEXT'; querySnapshotToken: string; sortAnchor: RuntimeSortAnchor }
export interface RuntimeRecordNeighborResponse {
  neighbor: RuntimeRecordSummary | null
  boundary: boolean
  querySnapshotToken: string
  correlationId: string
}
export interface RuntimeBatchCommandItemInput { recordId: string; expectedVersion: number }
export interface RuntimeBatchCommandInput { items: RuntimeBatchCommandItemInput[] }
export interface RuntimeBatchTransferInput extends RuntimeBatchCommandInput { targetMemberId: string }
export type RuntimeBatchEditChange =
  | { fieldCode: string; operation: 'SET'; value: unknown }
  | { fieldCode: string; operation: 'CLEAR' }
export interface RuntimeBatchEditInput extends RuntimeBatchCommandInput { changes: RuntimeBatchEditChange[] }
export type RuntimeBatchCommandResultCode = 'APPLIED' | 'RECORD_NOT_FOUND' | 'STATE_INVALID' | 'VERSION_STALE'
export interface RuntimeBatchCommandItemResult {
  recordId: string
  resultCode: RuntimeBatchCommandResultCode
  newVersion?: number
  currentVersion?: number
  status?: string
}
export interface RuntimeBatchCommandResponse {
  allApplied: boolean
  items: RuntimeBatchCommandItemResult[]
  correlationId?: string
}
export type RuntimeBatchArchiveItemInput = RuntimeBatchCommandItemInput
export type RuntimeBatchArchiveInput = RuntimeBatchCommandInput
export type RuntimeBatchArchiveResultCode = RuntimeBatchCommandResultCode
export type RuntimeBatchArchiveItemResult = RuntimeBatchCommandItemResult
export type RuntimeBatchArchiveResponse = RuntimeBatchCommandResponse
export type RuntimeBatchTransferResponse = RuntimeBatchCommandResponse
export type RuntimeBatchEditResponse = RuntimeBatchCommandResponse
export interface RuntimeSavedView { viewId: string; version: number; moduleCode: string; name: string; query: RuntimeRecordQuery; columns: string[]; correlationId: string }
export interface RuntimeSavedViewList { items: RuntimeSavedView[]; correlationId: string }
export interface RuntimeDeleteSavedViewResponse { viewId: string; version: number; deleted: boolean; correlationId: string }
export interface RuntimeRecordDetail extends RuntimeRecordSummary { schemaVersionId: string; actions: string[] }
export type RuntimeRecordFlowStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN' | 'TERMINATED'
export interface RuntimeRecordFlowState {
  instanceId: string
  status: RuntimeRecordFlowStatus
  version: number
  updatedAt: string
}
export interface RuntimeRelationTargetInput { targetRecordId: string; targetExpectedVersion: number; ordinal: number }
export interface RuntimeRelationInput { fieldCode: string; targets: RuntimeRelationTargetInput[] }
export interface RuntimeSubRowInput { clientRowKey: string; rowId?: string; expectedVersion?: number; ordinal: number; values: Record<string, unknown> }
export interface RuntimeSubtableInput { fieldCode: string; rows: RuntimeSubRowInput[] }
export interface RuntimeCreateRecordInput {
  schemaVersionId: string
  title?: string
  values: Record<string, unknown>
  relations: RuntimeRelationInput[]
  subtables: RuntimeSubtableInput[]
}
export interface RuntimeUpdateRecordInput extends RuntimeCreateRecordInput { expectedVersion: number }
export interface RuntimeAutosaveRecordInput { schemaVersionId: string; title?: string; expectedVersion: number; values: Record<string, unknown> }
export interface RuntimeAutosaveRecordResponse { record: RuntimeRecordDetail; lastSavedAt: string; expiresAt: string }
export type RuntimeLifecycleCommand = 'archive' | 'unarchive' | 'trash' | 'restore-from-trash' | 'discard' | 'recover'
export interface RuntimeVersionConflictData {
  currentVersion: number
  currentSnapshot: RuntimeRecordDetail
  conflictFields: string[]
}

export interface RuntimeOperationCapability { permissionCode: string; enabled: boolean; disabledReason?: string }
export interface RuntimeCompositionCapabilities {
  relationRead: RuntimeOperationCapability
  relationAdd: RuntimeOperationCapability
  relationRemove: RuntimeOperationCapability
  relationReorder: RuntimeOperationCapability
  subtableRead: RuntimeOperationCapability
  subtableAdd: RuntimeOperationCapability
  subtableUpdate: RuntimeOperationCapability
  subtableRemove: RuntimeOperationCapability
  subtableReorder: RuntimeOperationCapability
}
export interface RuntimeRelationItem { targetRecordId: string; targetVersion: number; ordinal: number; title: string }
export interface RuntimeRelationCandidatePage {
  items: RuntimeRelationItem[]
  page: number
  size: number
  total: number
  correlationId: string
}
export interface RuntimeRelationPage {
  items: RuntimeRelationItem[]
  page: number
  size: number
  total: number
  capabilities: RuntimeCompositionCapabilities
  correlationId: string
}
export interface RuntimeSubRow { rowId: string; version: number; ordinal: number; values: RuntimeFieldValue[] }
export interface RuntimeSubtablePage {
  items: RuntimeSubRow[]
  page: number
  size: number
  total: number
  capabilities: RuntimeCompositionCapabilities
  correlationId: string
}
export interface RuntimeRecordMutationResponse { recordId: string; version: number; schemaVersionId: string; status: string; historyId: string; correlationId: string }
export interface RuntimeRelationMutationInput { expectedVersion: number; add: RuntimeRelationTargetInput[]; remove: string[]; order: string[] }
export interface RuntimeSubRowCreateInput { clientRowKey: string; ordinal: number; values: Record<string, unknown> }
export interface RuntimeSubRowUpdateInput { rowId: string; expectedVersion: number; ordinal: number; values: Record<string, unknown> }
export interface RuntimeSubRowRef { rowId: string; expectedVersion: number }
export interface RuntimeSubtableMutationInput { expectedVersion: number; add: RuntimeSubRowCreateInput[]; update: RuntimeSubRowUpdateInput[]; remove: RuntimeSubRowRef[]; order: string[] }
export interface RuntimeReferenceRetryResponse { recordId: string; recordVersion: number; fieldCode: string; recalculationState: string; correlationId: string }
