/**
 * API contract types synced from docs/api/api.md.
 *
 * Contract artifact only: no request client, no page implementation, no runtime logic.
 */

export const API_CONTRACT_VERSION = '0.1.0-frozen';

export type ApiScope = 'platform' | 'system';

export type ApiCodeDomain =
  | 'AUTH'
  | 'PLATFORM'
  | 'TENANT'
  | 'MEMBER'
  | 'PERMISSION'
  | 'MODULE'
  | 'FIELD'
  | 'DICT'
  | 'RECORD'
  | 'FLOW'
  | 'TODO'
  | 'MESSAGE'
  | 'WORK'
  | 'SSO'
  | 'SECRET'
  | 'OPENAPI'
  | 'AGENT'
  | 'LOG'
  | 'TASK'
  | 'OPS';

export interface ApiResponse<T> {
  code: 'SUCCESS' | `${ApiCodeDomain}_${string}`;
  message: string;
  requestId: string;
  traceId: string;
  auditLogId?: string;
  data: T;
  errorFields?: ErrorField[];
  disabledReason?: string;
}

export interface ErrorField {
  field: string;
  message: string;
  code?: string;
}

export type FilterOperator =
  | 'EQ'
  | 'NE'
  | 'LIKE'
  | 'IN'
  | 'BETWEEN'
  | 'GT'
  | 'GTE'
  | 'LT'
  | 'LTE'
  | 'IS_NULL'
  | 'IS_NOT_NULL';

export interface FilterCriterion {
  field: string;
  operator: FilterOperator;
  value?: unknown;
}

export interface SortCriterion {
  field: string;
  direction: 'ASC' | 'DESC';
}

export interface PageRequest {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  filters?: FilterCriterion[];
  sorts?: SortCriterion[];
}

export interface PageResult<T> {
  records: T[];
  pageNo: number;
  pageSize: number;
  total: number;
  hasNext: boolean;
}

export type AsyncTaskStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'SUCCESS'
  | 'PARTIAL_SUCCESS'
  | 'FAILED'
  | 'CANCELED'
  | 'ROLLBACKING'
  | 'ROLLED_BACK';

export interface AsyncTask {
  taskId: string;
  bizType: string;
  idempotencyKey: string;
  status: AsyncTaskStatus;
  progress: number;
  retryable: boolean;
  cancelable: boolean;
  rollbackSupported: boolean;
  resultFile?: FileRef;
  errorFile?: FileRef;
  failureReason?: string;
  partialSuccessCount?: number;
  partialFailureCount?: number;
  traceId: string;
  auditLogId: string;
  createdBy: string;
  createdAt: string;
}

export interface FileRef {
  fileId: string;
  fileName: string;
  downloadUrl?: string;
}

export interface SystemSwitchContext {
  accountId: string;
  accountMemberBindingId: string;
  systemId: string;
  systemCode: string;
  systemName: string;
  tenantId: string;
  systemMemberId: string;
  effectiveRoleIds: string[];
  dataScope: DataScopeRule;
  permissionSnapshotSummary: PermissionSnapshotSummary;
  messageTodoScope: MessageTodoScope;
  expiresAt: string;
}

export interface TenantSwitchContext {
  systemId: string;
  tenantId: string;
  tenantName: string;
  tenantRoleIds: string[];
  tenantDataScope: DataScopeRule;
  isTenantSwitchable: boolean;
  disabledReason?: string;
  permissionSnapshotSummary: PermissionSnapshotSummary;
}

export interface DataScopeRule {
  type: 'ALL' | 'DEPARTMENT' | 'SELF' | 'CUSTOM';
  expression?: string;
}

export interface PermissionSnapshotSummary {
  snapshotId: string;
  permissionVersion: string;
  disabledReason?: string | null;
}

export interface MessageTodoScope {
  scope: ApiScope;
  systemId?: string;
  tenantId?: string;
}

export interface EffectivePermissionSnapshot {
  snapshotId: string;
  permissionVersion: string;
  systemMemberId: string;
  tenantId: string;
  sourceRoleIds: string[];
  denyPolicyIds: string[];
  field: Record<string, 'READABLE' | 'WRITABLE' | 'MASKED' | 'HIDDEN'>;
  action: Record<string, boolean>;
  dataScope: DataScopeRule;
  disabledReason?: string | null;
  explain: PermissionExplainItem[];
}

export interface PermissionExplainItem {
  type: 'ROLE_ALLOW' | 'DENY_POLICY' | 'DATA_SCOPE' | 'FIELD_MASK' | 'SYSTEM_RULE';
  target: string;
  roleId?: string;
  policyId?: string;
}

export interface PermissionDecisionVO {
  allowed: boolean;
  disabledReason?: string;
  missingPermissions?: string[];
  dataScopeExpression?: string;
  fieldMaskRules?: Record<string, string>;
  explain?: PermissionExplainItem[];
}

export interface ModuleGroupVO {
  groupId: string;
  name: string;
  sort: number;
  visibleRoleIds: string[];
  publishStatus: PublishStatus;
  publishedVersion?: string;
}

export interface ModuleVO {
  moduleId: string;
  groupId: string;
  moduleCode: string;
  name: string;
  status: EnableStatus;
  publishStatus: PublishStatus;
  currentVersion: string;
}

export type EnableStatus = 'ENABLED' | 'DISABLED';
export type PublishStatus = 'DRAFT' | 'PUBLISHED' | 'CHANGED' | 'ARCHIVED';

export interface FieldDefinitionVO {
  fieldId: string;
  fieldCode: string;
  name: string;
  fieldType: FieldType;
  storageType: string;
  filterOperators: FilterOperator[];
  sortable: boolean;
  required: boolean;
  maskRule?: string;
  importExportRule?: string;
}

export type FieldType =
  | 'TEXT'
  | 'LONG_TEXT'
  | 'NUMBER'
  | 'AMOUNT'
  | 'DATE'
  | 'DATETIME'
  | 'SINGLE_SELECT'
  | 'MULTI_SELECT'
  | 'TREE_SELECT'
  | 'CASCADE_SELECT'
  | 'USER'
  | 'DEPARTMENT'
  | 'FILE'
  | 'IMAGE'
  | 'RELATION'
  | 'CHILD_TABLE'
  | 'AUTO_NUMBER'
  | 'FORMULA'
  | 'BOOLEAN';

export interface DynamicListSchema {
  moduleId: string;
  moduleCode: string;
  sceneId: string;
  columns: DynamicColumn[];
  filters: DynamicFilter[];
  sorters: SortCriterion[];
  page: PageRequest;
  rowClickTarget: string;
  batchActions: ActionContract[];
  toolbarActions: ActionContract[];
  importExportConfig: ImportExportConfig;
  emptyState: EmptyState;
  permissionSnapshotId: string;
}

export interface DynamicColumn {
  fieldId: string;
  fieldCode: string;
  title: string;
  width?: number;
  sortable: boolean;
  masked?: boolean;
}

export interface DynamicFilter {
  fieldId: string;
  fieldCode: string;
  operators: FilterOperator[];
  component: string;
}

export interface ActionContract {
  actionCode: string;
  name: string;
  enabled: boolean;
  disabledReason?: string;
  requireSelection?: boolean;
  maxSelection?: number;
}

export interface ImportExportConfig {
  importEnabled: boolean;
  exportEnabled: boolean;
  exportAllEnabled: boolean;
  resultTaskRequired: boolean;
}

export interface EmptyState {
  title: string;
  actionCode?: string;
}

export interface BusinessRecordRow {
  recordId: string;
  title: string;
  summary?: string;
  fields: Record<string, unknown>;
  actions: ActionContract[];
  disabledReasons?: Record<string, string>;
  permissionSnapshotVersion: string;
}

export interface BusinessDetailView {
  summary: Record<string, unknown>;
  baseFields: Record<string, unknown>;
  childRows: Record<string, unknown[]>;
  relations: DynamicRelation[];
  attachments: FileRef[];
  printRecords: OperationRecord[];
  operationLogs: OperationRecord[];
  approvalSidebar?: ApprovalSidebar;
  fieldMaskResults: Record<string, string>;
}

export interface DynamicRelation {
  sourceRecordId: string;
  targetRecordId: string;
  relationType: string;
}

export interface OperationRecord {
  operator: string;
  action: string;
  operatedAt: string;
  traceId: string;
}

export interface ApprovalSidebar {
  instanceId: string;
  currentNodeName: string;
  availableActions: ActionContract[];
  timeline: ApprovalTimelineItem[];
}

export interface ApprovalTimelineItem {
  nodeName: string;
  operator?: string;
  result?: string;
  operatedAt?: string;
}

export interface TodoRowView {
  todoId: string;
  scope: ApiScope;
  type: string;
  title: string;
  sourceName: string;
  moduleCode?: string;
  objectTitle?: string;
  assigneeId: string;
  dueAt?: string;
  status: string;
  priority?: string;
  target: MessageTarget;
  primaryAction?: ActionContract;
  actionPermissions: ActionContract[];
  traceId: string;
}

export interface MessageTarget {
  scope: ApiScope;
  targetType:
    | 'system_switch'
    | 'platform_auth'
    | 'platform_task'
    | 'platform_log'
    | 'agent_result'
    | 'audit_log'
    | 'business_record'
    | 'approval_task'
    | 'async_task';
  targetId: string;
  targetSystemId?: string;
  targetTenantId?: string;
  requiresSystemSwitch: boolean;
  fallbackAction?: string | null;
}

export interface NotificationTemplate {
  templateCode: string;
  scope: ApiScope;
  templateType: string;
  variables: string[];
  channels: string[];
  targetRule: string;
  dedupeKey?: string;
  readReceiptRequired: boolean;
  quietPolicy?: string;
  retryPolicy?: string;
}

export interface MessageDeliveryLog {
  messageId: string;
  channel: string;
  status: string;
  failureReason?: string;
  retryCount: number;
  readReceipt?: string;
  doNotDisturb?: boolean;
  archiveStatus?: string;
  traceId: string;
}

export interface WorkDashboardVO {
  projectSummary: Record<string, unknown>;
  todayWarnings: WorkWarning[];
  calendarItems: WorkCalendarItem[];
  myTasks: WorkTaskVO[];
  dailyReportReminder?: string;
}

export interface WorkTaskVO {
  taskId: string;
  taskType: 'PROJECT' | 'PLAIN';
  title: string;
  projectId?: string;
  assignee: string;
  collaborators: string[];
  status: string;
  tags: string[];
  progress: number;
  dueAt?: string;
  relatedObject?: MessageTarget;
}

export interface WorkWarning {
  warningType: string;
  title: string;
  dueAt?: string;
  target?: MessageTarget;
}

export interface WorkCalendarItem {
  date: string;
  itemType: 'PROJECT_TASK' | 'PLAIN_TASK' | 'DAILY_REPORT' | 'TODO';
  title: string;
  target?: MessageTarget;
}

export interface KanbanQueryRequest {
  taskType: 'PROJECT' | 'PLAIN';
  columnFieldId: string;
  swimlaneFieldId?: string;
  groupFieldId?: string;
  pageCursor?: string;
}

export interface DailyReportVO {
  reportId: string;
  date: string;
  status: string;
  content: string;
  sourceSummary?: string;
  submitter: string;
}

export interface DailyReportAutoSourceRule {
  sourceTypes: string[];
  taskScope: string;
  todoScope: string;
  messageScope: string;
  logScope: string;
  approvalScope: string;
  permissionPolicy: string;
  manualConfirmRequired: boolean;
}

export interface IdentityProvider {
  providerId: string;
  name: string;
  protocol: string;
  issuer: string;
  clientId: string;
  secretRef: SecretRef;
  certRef?: SecretRef;
  domainWhitelist: string[];
  jitPolicy: string;
  mfaPolicy?: string;
  status: EnableStatus;
}

export interface SecretRef {
  secretRefId: string;
  refType: 'IDENTITY_PROVIDER' | 'OPENAPI' | 'MODEL' | 'CERTIFICATE';
  version: string;
  expiresAt?: string;
  rotationStatus?: string;
  lastUsedAt?: string;
  displayName: string;
}

export interface SecretRotationJob {
  jobId: string;
  status: AsyncTaskStatus;
  newVersion: string;
  rollbackPlan: string;
  traceId: string;
  auditLogId: string;
}

export interface NoMemberAccessRequest {
  requestId: string;
  status: 'SUBMITTED' | 'REVIEWING' | 'APPROVED' | 'REJECTED' | 'CANCELED';
  identityProvider: string;
  externalUserId: string;
  targetSystemId: string;
  tenantId: string;
  requestRole?: string;
  approverId?: string;
  approveResult?: string;
  roleIds: string[];
  dataScope?: DataScopeRule;
  rejectReason?: string;
  traceId: string;
}

export interface OpenApiApp {
  externalAppId: string;
  appName: string;
  status: EnableStatus;
  openApiSecretRef: SecretRef;
  scopes: string[];
  callbackUrl?: string;
  rateLimit?: string;
  lastUsedAt?: string;
}

export interface AgentPolicyScope {
  moduleScope: string[];
  fieldScope: Record<string, string[]>;
  actionScope: string[];
  dataScopeExpression?: string;
  outboundLimit?: string;
  desensitizePolicy?: string;
  policyVersion: string;
}

export interface AgentSession {
  sessionId: string;
  scope: ApiScope;
  systemId?: string;
  tenantId?: string;
  systemMemberId?: string;
  modelVersion: string;
  promptVersion: string;
  permissionSnapshotVersion?: string;
}

export interface AgentConfirmation {
  confirmationId: string;
  confirmType: 'platformAgentConfirm' | 'systemAgentWriteConfirm' | 'workAgentDraftConfirm';
  status: 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'EXPIRED';
  auditLogId: string;
}

export interface AuditLog {
  logId: string;
  logType: 'LOGIN' | 'BUSINESS' | 'RISK' | 'IMPORT_EXPORT' | 'AGENT' | 'OPENAPI';
  scope: ApiScope;
  systemId?: string;
  tenantId?: string;
  operator: string;
  action: string;
  objectType: string;
  objectId: string;
  result: string;
  requestId: string;
  traceId: string;
  auditLogId: string;
  ip?: string;
  device?: string;
  fieldDiff?: Record<string, unknown>;
  desensitizeResult?: Record<string, string>;
  permissionSnapshot?: PermissionSnapshotSummary;
  failureReason?: string;
}

export const API_ENDPOINT_GROUPS = [
  'auth-account',
  'platform-system-tenant-member',
  'role-permission',
  'module-config',
  'runtime-record',
  'workflow-todo-message',
  'work-management',
  'sso-secret-openapi',
  'ai-agent',
  'log-task-ops',
] as const;

export type ApiEndpointGroup = (typeof API_ENDPOINT_GROUPS)[number];
