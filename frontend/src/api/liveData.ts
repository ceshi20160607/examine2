import { apiClient } from './client';
import type {
  ActionContract,
  ApiResponse,
  AsyncTask,
  BusinessDetailView,
  DynamicColumn,
  DynamicFilter,
  DynamicListSchema,
  FieldDefinitionVO,
  FileRef,
  OperationRecord,
  PageResult,
  SortCriterion,
} from './types';
import type { RuntimeModuleGroup, RuntimeModuleItem, RuntimeRecordRow } from '../features/runtime/records/runtimeData';
import type { StatusTone } from '../shared/status';

interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  profile: {
    accountId: string;
    accountName: string;
  };
  defaultLanding?: {
    route?: string;
    systemId?: string;
  };
  traceId: string;
}

interface RegisterWithSystemResponse {
  accessToken: string;
  refreshToken: string;
  accountId: string;
  systemId: string;
  systemMemberId: string;
  initGuideSteps: string[];
  auditLogId: string;
}

interface PasswordResetResponse {
  resetTicket: string;
  verifyCode?: string;
  expiresAt: string;
  traceId: string;
}

export interface BackendModuleGroup {
  groupId: string;
  name: string;
  sort?: number;
  publishStatus?: string;
  publishedVersion?: string;
}

export interface BackendModule {
  moduleId: string;
  groupId: string;
  moduleCode: string;
  name: string;
  status: number | string;
  publishStatus?: string;
  currentVersion?: string;
}

interface BackendActionView {
  actionCode: string;
  actionName: string;
  enabled: boolean;
  disabledReason?: string;
  selectionLimit?: {
    maxSelected?: number;
  };
}

interface BackendColumnPermission {
  fieldId: string;
  fieldCode: string;
  label: string;
  width?: number;
  sortable: boolean;
  writable: boolean;
  permissionMode?: string;
  maskRule?: string;
}

interface BackendFilterCapability {
  fieldId: string;
  fieldCode: string;
  label: string;
  fieldType: string;
  operators: string[];
  advanced: boolean;
}

interface BackendRuntimeListSchema {
  moduleId: string;
  moduleCode: string;
  sceneCode?: string;
  columns: BackendColumnPermission[];
  filters: BackendFilterCapability[];
  sorters: Array<{
    fieldCode: string;
    defaultDirection?: string;
    defaultSort?: boolean;
  }>;
  rowDetailTarget?: {
    drawerCode?: string;
  };
  toolbarActions: BackendActionView[];
  batchActions: BackendActionView[];
  permissionSnapshotId: string;
  permissionSnapshotVersion?: string;
  schemaVersion?: string;
}

interface BackendFieldValue {
  fieldCode: string;
  label: string;
  value?: unknown;
  displayValue?: string;
  permissionMode?: string;
  maskRule?: string;
  writable: boolean;
}

interface BackendRecordRow {
  recordId: string;
  title: string;
  summary?: string;
  fields: BackendFieldValue[];
  actions: BackendActionView[];
  disabledReasons?: Record<string, string>;
  permissionSnapshotVersion: string;
  updatedAt?: string;
}

interface BackendRuntimeSearch {
  listSchema: BackendRuntimeListSchema;
  page: PageResult<BackendRecordRow>;
  serverChecks?: string[];
}

interface BackendDetailTab {
  tabCode: string;
  tabName: string;
  visible: boolean;
  payload?: Record<string, unknown>;
}

interface BackendBusinessDetail {
  summary: {
    recordId: string;
    title: string;
    summary?: string;
    status?: string;
    statusColor?: string;
  };
  baseFields: BackendFieldValue[];
  tabs: BackendDetailTab[];
  approvalSidebar?: {
    visible: boolean;
    flowInstanceId?: string;
    currentNodeName?: string;
    pendingTaskId?: string;
    status?: string;
    disabledReason?: string;
  };
  fieldMaskResults?: Array<{
    fieldCode: string;
    permissionMode: string;
    maskRule?: string;
    desensitizedValue?: string;
    reason?: string;
  }>;
  actions?: BackendActionView[];
}

export interface RuntimeLiveData {
  groups: RuntimeModuleGroup[];
  modules: RuntimeModuleItem[];
  activeModule: RuntimeModuleItem;
  schema: DynamicListSchema;
  fields: FieldDefinitionVO[];
  rows: RuntimeRecordRow[];
  total: number;
  traceId: string;
}

export interface RuntimeRecordQuery {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  fieldFilters?: RuntimeFieldFilter[];
  sorts?: RuntimeSort[];
  sceneCode?: string;
}

export interface RuntimeFieldFilter {
  fieldCode: string;
  operator: string;
  value?: unknown;
}

export interface RuntimeSort {
  fieldCode: string;
  direction: 'ASC' | 'DESC';
}

export interface RuntimeRecordSaveInput {
  fieldValues: Record<string, unknown>;
  childRows?: Record<string, Array<Record<string, unknown>>>;
  attachmentIds?: string[];
  draftId?: string;
}

export interface RuntimeRecordMutationResult {
  recordId: string;
  result: string;
  changedFields?: unknown[];
  permissionSnapshotVersion?: string;
  idempotencyKey?: string;
  traceId: string;
  auditLogId: string;
  operatedAt?: string;
}

export interface RuntimeActionResult {
  recordId: string;
  actionCode: string;
  result: string;
  accepted: boolean;
  disabledReason?: string;
  traceId: string;
  auditLogId: string;
  operatedAt?: string;
}

export interface RuntimeDraft {
  draftId: string;
  recordId?: string;
  fieldValues: Record<string, unknown>;
  validationIssues?: Array<{
    fieldCode: string;
    message: string;
    focusable: boolean;
  }>;
  permissionSnapshotVersion?: string;
  traceId: string;
  auditLogId: string;
  updatedAt?: string;
}

export interface RuntimeImportPrecheckResult {
  precheckId: string;
  passed: boolean;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  duplicateRows: number;
  issues: Array<{
    rowNo?: number;
    fieldCode?: string;
    level?: string;
    message: string;
    suggestion?: string;
  }>;
  resultFile?: FileRef;
  errorFile?: FileRef;
  task: AsyncTask;
  traceId: string;
  auditLogId: string;
}

export interface UploadFileView {
  fileId: string;
  fileName: string;
  fileType?: string;
  sizeBytes?: number;
  checksum?: string;
  status?: string;
  storagePolicyCode?: string;
  storageMode?: string;
  objectStorageConnected?: boolean;
  previewUrl?: string;
  downloadUrl?: string;
  uploadedBy?: string;
  uploadedAt?: string;
}

export interface UploadResultView {
  file: UploadFileView;
  retryable: boolean;
  traceId: string;
  auditLogId?: string;
}

export interface RuntimeImportConfirmResult {
  precheckId: string;
  task: AsyncTask;
  precheckWarnings?: RuntimeImportPrecheckResult['issues'];
}

export interface RuntimeExportResult {
  task: AsyncTask;
  scope: string;
  fields: string[];
  desensitizeMode: string;
  expectedResultFile?: FileRef;
  expectedErrorFile?: FileRef;
}

export interface MessageSearchOptions {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  readStatus?: string;
  archiveStatus?: string;
  templateCode?: string;
  type?: string;
  timeRange?: string;
}

export interface TodoSearchOptions {
  pageNo?: number;
  pageSize?: number;
  typeCode?: string;
  keyword?: string;
  status?: string;
  priority?: string;
  dueRange?: string;
}

export interface MessageCard {
  messageId: string;
  scope: string;
  systemId?: string;
  tenantId?: string;
  templateCode: string;
  type: string;
  title: string;
  content: string;
  readStatus: string;
  archiveStatus: string;
  target?: MessageTarget;
  traceId: string;
  createdAt: string;
}

export interface MessageActionResult {
  scope: string;
  systemId?: string;
  tenantId?: string;
  action: string;
  affectedCount: number;
  status: string;
  traceId: string;
  auditLogId: string;
  operatedAt: string;
}

export interface MessageTarget {
  scope: string;
  targetType: string;
  targetId: string;
  targetSystemId?: string;
  targetTenantId?: string;
  requiresSystemSwitch: boolean;
  fallbackAction?: string;
}

export interface TodoSearchResult {
  scope: string;
  systemId?: string;
  tenantId?: string;
  selectedTypeCode?: string;
  typeTree: TodoTypeNode[];
  page: PageResult<TodoRow>;
  traceId: string;
}

export interface TodoTypeNode {
  typeCode: string;
  typeName: string;
  count: number;
  icon?: string;
  children?: TodoTypeNode[];
}

export interface TodoRow {
  todoId: string;
  scope: string;
  type: string;
  title: string;
  sourceName: string;
  moduleCode?: string;
  objectTitle?: string;
  dueAt?: string;
  status: string;
  priority?: string;
  target?: TodoTarget;
  primaryAction?: TodoAction;
  actionPermissions: TodoAction[];
  traceId: string;
}

export interface TodoTarget {
  targetType: string;
  routeName: string;
  params: Record<string, string>;
  requiresSystemSwitch: boolean;
}

export interface TodoAction {
  actionCode: string;
  actionName: string;
  enabled: boolean;
  disabledReason?: string;
  resultDrawerCode?: string;
}

export interface TodoActionResult {
  todoId: string;
  actionCode: string;
  status: string;
  message?: string;
  traceId?: string;
  auditLogId?: string;
  target?: TodoTarget;
  operatedAt?: string;
}

export interface WorkSearchOptions {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  status?: string;
  projectId?: string;
}

export interface WorkDashboard {
  overview?: {
    activeProjectCount?: number;
    projectTaskCount?: number;
    plainTaskCount?: number;
    overdueTaskCount?: number;
    dailyReportSubmitted?: boolean;
  };
  todayWarnings?: WorkWarning[];
  monthlyCalendar?: CalendarDay[];
  myTasks?: WorkTask[];
  dailyReportReminder?: {
    message?: string;
    status?: string;
  };
  traceId?: string;
}

export interface WorkWarning {
  warningId?: string;
  title: string;
  level?: string;
  dueAt?: string;
}

export interface CalendarDay {
  date: string;
  items: Array<{
    itemId?: string;
    itemType: string;
    title: string;
    color?: string;
  }>;
}

export interface WorkTask {
  taskId: string;
  taskType: string;
  title: string;
  projectName?: string;
  assignee?: {
    memberName?: string;
  };
  status?: {
    itemName?: string;
    itemCode?: string;
    semantic?: string;
  };
  tags?: Array<{
    itemName?: string;
    color?: string;
  }>;
  progress?: number;
  dueAt?: string;
  commentCount?: number;
  warningLevel?: string;
}

export interface DailyReport {
  reportId: string;
  date: string;
  status: string;
  content: string;
  sourceSummary?: string;
  submitter?: {
    memberName?: string;
  };
  submittedAt?: string;
}

export interface WorkProject {
  projectId: string;
  projectCode: string;
  projectName: string;
  progress?: number;
  startDate?: string;
  endDate?: string;
}

export interface DailyReportAutoDraft {
  draftId: string;
  reportDate: string;
  content: string;
  manualConfirmRequired: boolean;
  traceId: string;
}

export interface PlatformSystem {
  systemId: string;
  systemCode: string;
  systemName: string;
  tenantMode: number;
  ownerAccountId: string;
  status: number;
  disabledReason?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface PlatformSystemLifecycleResult {
  result: string;
  systemId: string;
  traceId: string;
  auditLogId: string;
  asyncTaskId?: string | null;
  operatedAt?: string;
}

export interface PlatformHealth {
  status: string;
  checks: string[];
  warnings: string[];
  traceId: string;
  checkedAt: string;
}

export interface OpsHealthCheck {
  checkId: string;
  scope: string;
  systemId?: string;
  checkType: string;
  status: string;
  checks: Array<{
    itemCode: string;
    itemName: string;
    status: string;
    description?: string;
    repairEntry?: string;
  }>;
  risks: Array<{
    riskCode: string;
    severity: string;
    description: string;
    disabledReason?: string;
  }>;
  repairEntries: string[];
  traceId: string;
  auditLogId: string;
  checkedAt: string;
}

export interface OpsFeatureFlagView {
  flagId: string;
  flagCode: string;
  scope: string;
  systemId?: string;
  tenantId?: string;
  status: number;
  rules: string;
  rollbackVersion: string;
  traceId: string;
  auditLogId: string;
  updatedAt: string;
}

export interface OpsQuotaView {
  quotaId: string;
  scope: string;
  systemId?: string;
  tenantId?: string;
  quotaType: string;
  quotaLimit: number;
  quotaUsed: number;
  warnThreshold: number;
  status: string;
  traceId: string;
  auditLogId: string;
  updatedAt: string;
}

export interface OpsRateLimitPolicyView {
  policyId: string;
  scope: string;
  systemId?: string;
  tenantId?: string;
  policyCode: string;
  limitRule: string;
  status: number;
  traceId: string;
  auditLogId: string;
  updatedAt: string;
}

export interface OpsDeploymentView {
  deploymentId: string;
  deploymentNo: string;
  envCode: string;
  backendVersion: string;
  frontendVersion: string;
  configVersion: string;
  status: string;
  rollbackPlan: string;
  destructiveScriptConfirmed: boolean;
  traceId: string;
  auditLogId: string;
  createdAt: string;
}

export interface OpsApiCachePolicyView {
  policyId: string;
  policyCode: string;
  cacheDomain: string;
  keyRule: string;
  invalidationRule: string;
  status: number;
  traceId: string;
  auditLogId: string;
  updatedAt: string;
}

export interface RoleView {
  roleId: string;
  scope: string;
  systemId?: string;
  tenantId?: string;
  roleCode: string;
  roleName: string;
  roleType: string;
  builtin: boolean;
  status: number;
  description?: string;
  updatedAt?: string;
}

export interface DepartmentNode {
  deptId: string;
  parentId?: string;
  deptCode: string;
  deptName: string;
  sortOrder?: number;
  status: number;
  children?: DepartmentNode[];
}

export interface MemberView {
  systemMemberId: string;
  systemId: string;
  tenantId: string;
  deptId?: string;
  memberName: string;
  employeeNo?: string;
  mobile?: string;
  email?: string;
  status: number;
  bindingStatus?: string;
  roleIds?: string[];
  updatedAt?: string;
}

export interface IdentityProviderView {
  providerId: string;
  name: string;
  protocol: string;
  issuer?: string;
  clientId?: string;
  domainWhitelist?: string[];
  status: string;
  updatedAt?: string;
}

export interface IdentityProviderTestResult {
  providerId: string;
  passed: boolean;
  disabledReason?: string;
  traceId: string;
  auditLogId?: string;
  checkedAt?: string;
}

export interface IdentityProviderPublishResult {
  providerId: string;
  publishStatus: string;
  warnings?: string[];
  traceId: string;
  auditLogId?: string;
  publishedAt?: string;
}

export interface SystemSsoPolicyView {
  systemId: string;
  enabledProviderIds?: string[];
  tenantDomains?: string[];
  orgMapping?: Record<string, unknown>;
  employeeBinding?: Record<string, unknown>;
  jitMemberPolicy?: Record<string, unknown>;
  noMemberFeedback?: Record<string, unknown>;
  status?: string;
  traceId?: string;
  updatedAt?: string;
}

export interface AuditLogView {
  logId: string;
  logType: string;
  scope: string;
  systemId?: string;
  tenantId?: string;
  operator?: string;
  action: string;
  objectType?: string;
  objectId?: string;
  result: string;
  requestId?: string;
  traceId: string;
  auditLogId?: string;
  createdAt?: string;
}

export interface AgentPolicyView {
  policyId: string;
  systemId?: string;
  tenantId?: string;
  policyCode: string;
  status: number;
  publishStatus?: {
    status?: string;
    version?: string;
    publishable?: boolean;
  };
  createdAt?: string;
}

export interface SsoOrgSyncPrecheckResult {
  task?: AsyncTask;
  matchedDepartmentCount: number;
  unmatchedDepartmentCount: number;
  matchedMemberCount: number;
  unboundMemberCount: number;
  draftBindingCount: number;
  status: string;
  disabledReason?: string;
  traceId: string;
  auditLogId?: string;
}

export interface AgentPolicyPublishCheckResult {
  policyId: string;
  passed: boolean;
  targetVersion?: string;
  items: Array<{
    itemCode: string;
    itemName: string;
    level: string;
    passed: boolean;
    message: string;
  }>;
  impactRefs: Array<Record<string, unknown>>;
  traceId: string;
}

export interface ModelAuthorizationView {
  authorizationId: string;
  authorizationCode: string;
  modelProvider: string;
  modelName: string;
  status: number;
  version?: string;
  createdAt?: string;
}

export interface FlowDefinitionView {
  flowId: string;
  flowCode: string;
  flowName: string;
  boundModuleId?: string;
  status: number;
  publishStatus: string;
  currentVersion?: string;
  canvas?: FlowCanvasView;
  propertyPanels?: FlowNodePropertyPanel[];
  updatedAt?: string;
}

export interface FlowNodePosition {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface FlowNodeConfigView {
  nodeKey: string;
  nodeType: string;
  nodeName: string;
  position: FlowNodePosition;
  propertyPayload?: Record<string, unknown>;
  status: number;
  propertyPanelCode?: string;
  runtimeExecutable?: boolean;
}

export interface FlowConditionExpression {
  expressionId?: string;
  fieldCode?: string;
  operator?: string;
  expectedValue?: unknown;
  expressionText?: string;
}

export interface FlowEdgeView {
  edgeKey: string;
  sourceNodeKey: string;
  targetNodeKey: string;
  branchLabel?: string;
  conditionPayload?: FlowConditionExpression | null;
}

export interface FlowCanvasView {
  flowId: string;
  nodes: FlowNodeConfigView[];
  edges: FlowEdgeView[];
  branchLabels?: Array<{
    edgeKey: string;
    sourceNodeKey: string;
    branchLabel: string;
    defaultBranch?: boolean;
    sort?: number;
  }>;
  validationSummary?: {
    connected: boolean;
    hasEndNode: boolean;
    warnings: string[];
  };
}

export interface FlowNodePropertyField {
  fieldCode: string;
  fieldName: string;
  fieldType: string;
  required: boolean;
  options?: string[];
  defaultValue?: unknown;
  description?: string;
}

export interface FlowNodePropertyPanel {
  nodeType: string;
  panelCode: string;
  title: string;
  payloadType: string;
  fields: FlowNodePropertyField[];
  supportedActions: string[];
}

export interface FlowNodeLibraryItem {
  nodeType: string;
  nodeTypeName: string;
  category: string;
  icon?: string;
  description?: string;
  defaultPropertyPayload?: Record<string, unknown>;
  propertySchema?: FlowNodePropertyField[];
  requiredCapabilities?: string[];
}

export interface FlowSimulationResult {
  simulationId: string;
  flowId: string;
  versionNo: string;
  passed: boolean;
  stepTraces: Array<{
    sequence: number;
    nodeKey: string;
    nodeName: string;
    nodeType: string;
    inputSummary?: string;
    outputSummary?: string;
    nextNodeKeys?: string[];
    elapsedMs?: number;
  }>;
  conditionDecisions: Array<Record<string, unknown>>;
  failureItems: ModulePublishCheckItem[];
  traceId: string;
  createdAt?: string;
}

export interface DictItemView {
  itemId: string;
  dictTypeId: string;
  parentId?: string;
  itemCode: string;
  itemName: string;
  color?: string;
  icon?: string;
  semantic?: string;
  sort?: number;
  defaultFlag?: boolean;
  kanbanEnabled?: boolean;
  status: number;
  disabledReason?: string;
}

export interface DictTypeView {
  dictTypeId: string;
  dictCode: string;
  dictName: string;
  dictKind?: string;
  status: number;
  publishedVersion?: string;
  previewItems?: DictItemView[];
  updatedAt?: string;
}

export interface FlowPublishCheckResult {
  passed: boolean;
  failureItems: ModulePublishCheckItem[];
  warningItems: ModulePublishCheckItem[];
  impactRefs: Array<Record<string, unknown>>;
  traceId: string;
  auditLogId?: string;
}

export interface FlowPublishResult {
  result: string;
  flowId: string;
  version?: string;
  snapshotId?: string;
  traceId: string;
  auditLogId?: string;
  asyncTaskId?: string | null;
  operatedAt?: string;
}

export interface WorkFieldConfigView {
  fieldId: string;
  fieldCode: string;
  fieldName: string;
  fieldType: string;
  required: boolean;
  listVisible: boolean;
  filterable: boolean;
  sortable: boolean;
  dictTypeCode?: string;
  dictItems?: Array<Record<string, unknown>>;
  cardVisible: boolean;
  kanbanEligible: boolean;
  permissionCode?: string;
}

export interface WorkConfigView {
  projectTaskFields?: WorkFieldConfigView[];
  plainTaskFields?: WorkFieldConfigView[];
  dailyReportFields?: WorkFieldConfigView[];
  projectTaskKanban?: Record<string, unknown>;
  plainTaskKanban?: Record<string, unknown>;
  kanbanConfigs?: Array<Record<string, unknown>>;
  dailyReportAutoSourceRule?: Record<string, unknown>;
  traceId?: string;
}

export interface WorkConfigPublishCheckResult {
  passed: boolean;
  targetVersion?: string;
  items: Array<{
    itemCode: string;
    itemName: string;
    level: string;
    passed: boolean;
    message: string;
  }>;
  impactRefs: Array<Record<string, unknown>>;
  traceId: string;
}

export interface HomePageWidgetConfig {
  widgetCode: string;
  widgetName: string;
  widgetType: string;
  sourceType: string;
  sort?: number;
  visible?: boolean;
}

export interface HomePageConfigView {
  systemId?: string;
  tenantId?: string;
  title: string;
  subtitle: string;
  visualTone: string;
  widgets: HomePageWidgetConfig[];
  publishState?: Record<string, unknown>;
  updatedAt?: string;
  traceId?: string;
}

export interface PageComponentConfig {
  componentCode: string;
  componentType: string;
  title: string;
  dataSource: string;
  boundFieldCode?: string;
  sort?: number;
  visible?: boolean;
  props?: Record<string, unknown>;
}

export interface ModulePageDesignView {
  pageId: string;
  systemId: string;
  tenantId: string;
  moduleId: string;
  moduleCode: string;
  pageCode: string;
  pageName: string;
  pageType: string;
  route: string;
  layoutMode: string;
  components: PageComponentConfig[];
  visibleRoleIds: string[];
  publishStatus: string;
  publishedVersion?: string;
  permissionSnapshotId?: string;
  traceId?: string;
  updatedAt?: string;
}

export interface NotificationTemplateView {
  templateId: string;
  systemId: string;
  templateCode: string;
  scope: string;
  templateType: string;
  variables: string[];
  channels: string[];
  targetRule?: Record<string, unknown>;
  dedupeKey?: string;
  readReceiptRequired?: boolean;
  status: number;
  auditLogId?: string;
  updatedAt?: string;
}

export interface NotificationTemplatePublishCheckResult {
  passed: boolean;
  failureItems: Array<{ itemCode: string; level: string; message: string; suggestion?: string }>;
  warningItems: Array<{ itemCode: string; level: string; message: string; suggestion?: string }>;
  impactRefs: string[];
  traceId: string;
  auditLogId?: string;
  checkedAt?: string;
}

export interface OpenApiScopeView {
  scopeCode: string;
  scopeName: string;
  description?: string;
  enabled: boolean;
  permissionCode?: string;
  version?: string;
  lastUsedAt?: string;
  disabledReason?: string;
}

export interface OpenApiAppView {
  externalAppId: string;
  systemId: string;
  tenantId?: string;
  externalAppCode: string;
  appName: string;
  status: number;
  scopes?: OpenApiScopeView[];
  callbackUrl?: string;
  openApiSecretRef?: {
    secretRefId: string;
    version?: string;
    rotationStatus?: string;
    displayName?: string;
    activeVersion?: string;
    nextRotationDueAt?: string;
  };
  rateLimit?: Record<string, unknown>;
  operation?: {
    traceId?: string;
    auditLogId?: string;
    result?: string;
    disabledReason?: string;
  };
  updatedAt?: string;
}

export interface OpenApiSecretRotationJob {
  jobId: string;
  status: string;
  newVersion?: string;
  rollbackPlan?: string;
  operation?: {
    traceId?: string;
    auditLogId?: string;
    result?: string;
  };
}

export interface SystemDataSourceView {
  dataSourceId: string;
  systemId: string;
  tenantId?: string;
  sourceCode: string;
  sourceName: string;
  sourceType: 'SYSTEM_INTERNAL' | 'EXTERNAL_API' | 'DATABASE_DIRECT' | string;
  connectionConfig?: Record<string, unknown>;
  authConfig?: Record<string, unknown>;
  syncConfig?: Record<string, unknown>;
  desensitizeConfig?: Record<string, unknown>;
  status: number;
  publishStatus?: string;
  publishedVersion?: string;
  lastCheckStatus?: string;
  lastCheckTraceId?: string;
  lastCheckedAt?: string;
  operation?: {
    traceId?: string;
    auditLogId?: string;
    result?: string;
    disabledReason?: string;
  };
  updatedAt?: string;
}

export interface SystemDataSourceCheckResult {
  dataSourceId: string;
  passed: boolean;
  items: Array<{
    itemCode: string;
    itemName: string;
    level: string;
    passed: boolean;
    message: string;
  }>;
  traceId: string;
  auditLogId?: string;
  checkedAt?: string;
  targetVersion?: string;
}

export interface NoMemberAccessRequestView {
  requestId: string;
  status: string;
  identityProvider: string;
  externalUserId: string;
  targetSystemId: string;
  tenantId?: string;
  requestRole?: string;
  approverId?: string;
  approveResult?: string;
  roleIds?: string[];
  dataScope?: Record<string, unknown>;
  rejectReason?: string;
  traceId: string;
  disabledReason?: string;
  businessAccessAllowed: boolean;
  accountMemberBindingId?: string;
  systemMemberId?: string;
  ssoBindingId?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ModulePublishCheckItem {
  itemCode: string;
  itemName: string;
  severity: string;
  objectType: string;
  objectId: string;
  message: string;
  fixAction?: string;
}

export interface ModulePublishCheckResult {
  passed: boolean;
  failureItems: ModulePublishCheckItem[];
  warningItems: ModulePublishCheckItem[];
  impactRefs: Array<Record<string, unknown>>;
  traceId: string;
}

export interface ModulePublishResult {
  result: string;
  targetId: string;
  version?: string;
  traceId: string;
  auditLogId?: string;
  asyncTaskId?: string | null;
  operatedAt?: string;
}

export interface RuntimeSceneSaveResult {
  sceneId: string;
  moduleId: string;
  sceneCode: string;
  sceneName: string;
  defaultScene: boolean;
  updatedAt?: string;
}

export interface AccountActionResult {
  result: string;
  traceId: string;
  auditLogId: string;
  operatedAt?: string;
}

export interface PlatformAdminData {
  systems: PageResult<PlatformSystem>;
  roles: PageResult<RoleView>;
  identityProviders: IdentityProviderView[];
  modelAuthorizations: PageResult<ModelAuthorizationView>;
  logs: PageResult<AuditLogView>;
  health?: PlatformHealth;
  warnings: string[];
}

export interface PlatformAdminPageOptions {
  systemsPageNo?: number;
  rolesPageNo?: number;
  modelAuthorizationsPageNo?: number;
  logsPageNo?: number;
}

export interface SystemAdminData {
  departments: DepartmentNode[];
  members: PageResult<MemberView>;
  roles: PageResult<RoleView>;
  moduleGroups: BackendModuleGroup[];
  modules: PageResult<BackendModule>;
  dictTypes: PageResult<DictTypeView>;
  flows: PageResult<FlowDefinitionView>;
  notificationTemplates: PageResult<NotificationTemplateView>;
  dataSources: PageResult<SystemDataSourceView>;
  openApiApps: PageResult<OpenApiAppView>;
  ssoPolicy?: SystemSsoPolicyView;
  workConfig?: WorkConfigView;
  homePageConfig?: HomePageConfigView;
  pageDesigns: ModulePageDesignView[];
  agentPolicies: PageResult<AgentPolicyView>;
  logs: PageResult<AuditLogView>;
  warnings: string[];
}

export interface SystemAdminPageOptions {
  membersPageNo?: number;
  rolesPageNo?: number;
  modulesPageNo?: number;
  dictTypesPageNo?: number;
  flowsPageNo?: number;
  notificationTemplatesPageNo?: number;
  dataSourcesPageNo?: number;
  openApiAppsPageNo?: number;
  agentPoliciesPageNo?: number;
  logsPageNo?: number;
}

export async function loginWithPassword(loginName: string, password: string): Promise<LoginResponse> {
  const response = await apiClient.post<LoginResponse>('/api/v1/auth/login', {
    loginName,
    password,
    loginTarget: 'PLATFORM',
  });
  const data = unwrap(response);
  localStorage.setItem('unexamine.accountId', data.profile.accountId);
  localStorage.setItem('unexamine.accessToken', data.accessToken);
  localStorage.setItem('unexamine.refreshToken', data.refreshToken);
  return data;
}

export async function registerWithSystem(input: {
  accountName: string;
  mobile: string;
  email: string;
  password: string;
  systemName: string;
  systemCode: string;
}): Promise<RegisterWithSystemResponse> {
  const response = await apiClient.post<RegisterWithSystemResponse>('/api/v1/auth/register-with-system', {
    ...input,
    tenantMode: 1,
    templateCode: 'starter',
  });
  const data = unwrap(response);
  localStorage.setItem('unexamine.accountId', data.accountId);
  localStorage.setItem('unexamine.accessToken', data.accessToken);
  localStorage.setItem('unexamine.refreshToken', data.refreshToken);
  return data;
}

export async function requestPasswordReset(loginName: string): Promise<PasswordResetResponse> {
  return unwrap(await apiClient.post<PasswordResetResponse>('/api/v1/auth/password-reset/request', {
    loginName,
    verifyChannel: 'MOBILE',
  }));
}

export async function confirmPasswordReset(resetTicket: string, verifyCode: string, newPassword: string): Promise<void> {
  unwrap(await apiClient.post('/api/v1/auth/password-reset/confirm', {
    resetTicket,
    verifyCode,
    newPassword,
  }));
}

export async function updateCurrentPassword(oldPassword: string, newPassword: string): Promise<AccountActionResult> {
  return unwrap(await apiClient.patch<AccountActionResult>('/api/v1/account/me/password', {
    oldPassword,
    newPassword,
  }));
}

export async function loadSystemModuleNavigation(systemId: string): Promise<{
  groups: RuntimeModuleGroup[];
  modules: RuntimeModuleItem[];
}> {
  const [groupResponse, moduleResponse] = await Promise.all([
    apiClient.get<BackendModuleGroup[]>(`/api/v1/systems/${systemId}/module-groups`),
    apiClient.get<PageResult<BackendModule>>(`/api/v1/systems/${systemId}/modules?pageNo=1&pageSize=100`),
  ]);
  const groups = unwrap(groupResponse)
    .slice()
    .sort((left, right) => (left.sort ?? 0) - (right.sort ?? 0))
    .map((group, index) => ({
      groupId: group.groupId,
      name: group.name,
      routePath: `/modules?group=${encodeURIComponent(group.groupId)}`,
      visible: group.publishStatus !== 'ARCHIVED',
      disabledReason: group.publishStatus === 'DRAFT' && index > 0 ? '模块组尚未发布' : undefined,
    }));
  const modules = unwrap(moduleResponse).records.map((module, index) => toRuntimeModuleItem(module, index === 0));
  return { groups, modules };
}

export async function loadRuntimeLiveData(systemId: string, moduleId?: string, query: RuntimeRecordQuery = {}): Promise<RuntimeLiveData> {
  const navigation = await loadSystemModuleNavigation(systemId);
  const activeModule = navigation.modules.find((module) => module.moduleId === moduleId)
    ?? navigation.modules.find((module) => !module.disabledReason)
    ?? navigation.modules[0];
  if (!activeModule) {
    throw new Error('当前系统没有可访问的业务模块。');
  }

  const response = await apiClient.post<BackendRuntimeSearch>(
    `/api/v1/systems/${systemId}/runtime/modules/${activeModule.moduleId}/records/search`,
    {
      pageNo: query.pageNo ?? 1,
      pageSize: query.pageSize ?? 10,
      keyword: query.keyword ?? '',
      sceneCode: query.sceneCode,
      fieldFilters: query.fieldFilters ?? [],
      sorts: query.sorts ?? [],
    },
  );
  const search = unwrap(response);
  const pageOffset = ((query.pageNo ?? 1) - 1) * (query.pageSize ?? 10);
  const rows = await Promise.all(search.page.records.map((row, index) => toRuntimeRecordRow(systemId, activeModule.moduleId, row, search.listSchema, pageOffset + index)));
  return {
    ...navigation,
    activeModule: { ...activeModule, active: true, count: search.page.total },
    modules: navigation.modules.map((module) => ({
      ...module,
      active: module.moduleId === activeModule.moduleId,
      count: module.moduleId === activeModule.moduleId ? search.page.total : module.count,
    })),
    schema: toDynamicListSchema(search.listSchema),
    fields: toRuntimeFields(search.listSchema),
    rows,
    total: search.page.total,
    traceId: response.traceId,
  };
}

export async function saveRuntimeDraft(systemId: string, moduleId: string, input: RuntimeRecordSaveInput & {
  recordId?: string;
}): Promise<RuntimeDraft> {
  return unwrap(await apiClient.post<RuntimeDraft>(
    `/api/v1/systems/${systemId}/runtime/modules/${moduleId}/drafts`,
    {
      draftId: input.draftId,
      recordId: input.recordId,
      fieldValues: input.fieldValues,
      childRows: input.childRows ?? {},
      attachmentIds: input.attachmentIds ?? [],
      clientVersion: 'frontend-runtime-form@1',
      sourceType: 'WEB_FORM',
    },
    createIdempotencyKey('runtime_draft'),
  ));
}

export async function saveRuntimeRecord(systemId: string, moduleId: string, input: RuntimeRecordSaveInput & {
  recordId?: string;
}): Promise<RuntimeRecordMutationResult> {
  const body = {
    fieldValues: input.fieldValues,
    childRows: input.childRows ?? {},
    attachmentIds: input.attachmentIds ?? [],
    draftId: input.draftId,
    sourceType: 'WEB_FORM',
  };
  if (input.recordId) {
    return unwrap(await apiClient.patch<RuntimeRecordMutationResult>(
      `/api/v1/systems/${systemId}/runtime/modules/${moduleId}/records/${input.recordId}`,
      body,
      createIdempotencyKey('runtime_update'),
    ));
  }
  return unwrap(await apiClient.post<RuntimeRecordMutationResult>(
    `/api/v1/systems/${systemId}/runtime/modules/${moduleId}/records`,
    body,
    createIdempotencyKey('runtime_create'),
  ));
}

export async function deleteRuntimeRecord(systemId: string, moduleId: string, recordId: string): Promise<RuntimeRecordMutationResult> {
  return unwrap(await apiClient.delete<RuntimeRecordMutationResult>(
    `/api/v1/systems/${systemId}/runtime/modules/${moduleId}/records/${recordId}`,
    createIdempotencyKey('runtime_delete'),
  ));
}

export async function executeRuntimeRecordAction(systemId: string, moduleId: string, recordId: string, actionCode: string, parameters: Record<string, unknown> = {}): Promise<RuntimeActionResult> {
  return unwrap(await apiClient.post<RuntimeActionResult>(
    `/api/v1/systems/${systemId}/runtime/modules/${moduleId}/records/${recordId}/actions/${actionCode}`,
    {
      parameters,
      selectedRecordIds: [recordId],
      reason: parameters.reason ?? '前端运行时操作',
      sourceType: 'WEB_ACTION',
    },
    createIdempotencyKey(`runtime_action_${actionCode}`),
  ));
}

export async function saveRuntimeScene(systemId: string, moduleId: string, input: {
  sceneCode: string;
  sceneName: string;
  defaultScene?: boolean;
  columnFieldIds?: string[];
  filterFieldIds?: string[];
  sortFieldIds?: string[];
}): Promise<RuntimeSceneSaveResult> {
  return unwrap(await apiClient.post<RuntimeSceneSaveResult>(
    `/api/v1/systems/${systemId}/modules/${moduleId}/scenes`,
    {
      sceneCode: input.sceneCode,
      sceneName: input.sceneName,
      defaultScene: input.defaultScene ?? false,
      visibleRoleIds: [],
      columnFieldIds: input.columnFieldIds ?? [],
      filterFieldIds: input.filterFieldIds ?? [],
      sortFieldIds: input.sortFieldIds ?? [],
    },
    createIdempotencyKey('runtime_scene_save'),
  ));
}

export async function precheckRuntimeImport(systemId: string, moduleId: string, input: {
  fileId: string;
  templateCode?: string;
  duplicateStrategy?: string;
  fieldMapping?: Record<string, string>;
}): Promise<RuntimeImportPrecheckResult> {
  return unwrap(await apiClient.post<RuntimeImportPrecheckResult>(
    `/api/v1/systems/${systemId}/runtime/modules/${moduleId}/imports/precheck`,
    {
      fileId: input.fileId,
      templateCode: input.templateCode || 'default',
      fieldMapping: input.fieldMapping ?? {},
      duplicateStrategy: input.duplicateStrategy || 'SKIP',
    },
    createIdempotencyKey('runtime_import_precheck'),
  ));
}

export async function uploadRuntimeImportFile(file: File): Promise<UploadResultView> {
  const body = new FormData();
  body.append('file', file);
  return unwrap(await apiClient.postForm<UploadResultView>(
    '/api/v1/uploads/files?sourceType=IMPORT_EXPORT',
    body,
    createIdempotencyKey('runtime_import_upload'),
  ));
}

export async function confirmRuntimeImport(systemId: string, moduleId: string, input: {
  precheckId: string;
  duplicateStrategy?: string;
  rollbackSupported?: boolean;
}): Promise<RuntimeImportConfirmResult> {
  return unwrap(await apiClient.post<RuntimeImportConfirmResult>(
    `/api/v1/systems/${systemId}/runtime/modules/${moduleId}/imports/confirm`,
    {
      precheckId: input.precheckId,
      duplicateStrategy: input.duplicateStrategy || 'SKIP',
      rollbackSupported: input.rollbackSupported ?? true,
    },
    createIdempotencyKey('runtime_import_confirm'),
  ));
}

export async function createRuntimeExport(systemId: string, moduleId: string, input: {
  scope: 'ALL_MATCHED' | 'SELECTED' | 'TEMPLATE_ONLY';
  selectedRecordIds?: string[];
  fields?: string[];
  filters?: Record<string, unknown>;
}): Promise<RuntimeExportResult> {
  return unwrap(await apiClient.post<RuntimeExportResult>(
    `/api/v1/systems/${systemId}/runtime/modules/${moduleId}/exports`,
    {
      scope: input.scope,
      selectedRecordIds: input.selectedRecordIds ?? [],
      fields: input.fields ?? [],
      fileFormat: 'XLSX',
      desensitizeMode: 'PERMISSION',
      filters: input.filters ?? {},
    },
    createIdempotencyKey('runtime_export'),
  ));
}

export async function loadSystemMessages(systemId: string, options: MessageSearchOptions = {}): Promise<PageResult<MessageCard>> {
  const pageNo = options.pageNo ?? 1;
  const pageSize = options.pageSize ?? 20;
  const readStatus = options.readStatus && options.readStatus !== 'all' ? options.readStatus : undefined;
  const archiveStatus = options.archiveStatus ?? 'active';
  return unwrap(await apiClient.post<PageResult<MessageCard>>(
    `/api/v1/systems/${systemId}/messages/search?pageNo=${pageNo}&pageSize=${pageSize}`,
    {
      systemId,
      readStatus,
      archiveStatus,
      templateCode: options.templateCode,
      type: options.type,
      timeRange: options.timeRange,
      keyword: options.keyword,
    },
  ));
}

export async function markSystemMessageRead(systemId: string, messageId: string): Promise<void> {
  unwrap(await apiClient.post(`/api/v1/systems/${systemId}/messages/mark-read`, {
    messageIds: [messageId],
    reason: 'message card opened',
  }));
}

export async function archiveSystemMessage(systemId: string, messageId: string): Promise<MessageActionResult> {
  return unwrap(await apiClient.post<MessageActionResult>(`/api/v1/systems/${systemId}/messages/archive`, {
    messageIds: [messageId],
    reason: 'message archived from center',
  }));
}

export async function markAllSystemMessagesRead(systemId: string, options: MessageSearchOptions = {}): Promise<MessageActionResult> {
  return unwrap(await apiClient.post<MessageActionResult>(`/api/v1/systems/${systemId}/messages/mark-all-read`, {
    templateCode: options.templateCode,
    type: options.type,
    timeRange: options.timeRange,
    keyword: options.keyword,
  }));
}

export async function loadPlatformMessages(options: MessageSearchOptions = {}): Promise<PageResult<MessageCard>> {
  const pageNo = options.pageNo ?? 1;
  const pageSize = options.pageSize ?? 10;
  const readStatus = options.readStatus && options.readStatus !== 'all' ? options.readStatus : undefined;
  return unwrap(await apiClient.post<PageResult<MessageCard>>(
    `/api/v1/platform/messages/search?pageNo=${pageNo}&pageSize=${pageSize}`,
    {
      readStatus,
      archiveStatus: options.archiveStatus ?? 'active',
      templateCode: options.templateCode,
      type: options.type,
      timeRange: options.timeRange,
      keyword: options.keyword,
    },
  ));
}

export async function markPlatformMessageRead(messageId: string): Promise<void> {
  unwrap(await apiClient.post('/api/v1/platform/messages/mark-read', {
    messageIds: [messageId],
    reason: 'platform message card opened',
  }));
}

export async function loadSystemTodos(systemId: string, options: TodoSearchOptions = {}): Promise<TodoSearchResult> {
  const pageNo = options.pageNo ?? 1;
  const pageSize = options.pageSize ?? 20;
  return unwrap(await apiClient.post<TodoSearchResult>(
    `/api/v1/systems/${systemId}/todos/search?pageNo=${pageNo}&pageSize=${pageSize}`,
    {
      scope: 'system',
      typeCode: options.typeCode,
      keyword: options.keyword,
      status: options.status ?? 'PENDING',
      priority: options.priority,
      dueRange: options.dueRange,
    },
  ));
}

export async function loadPlatformTodos(options: TodoSearchOptions = {}): Promise<TodoSearchResult> {
  const pageNo = options.pageNo ?? 1;
  const pageSize = options.pageSize ?? 10;
  return unwrap(await apiClient.post<TodoSearchResult>(
    `/api/v1/platform/todos/search?pageNo=${pageNo}&pageSize=${pageSize}`,
    {
      scope: 'platform',
      typeCode: options.typeCode,
      keyword: options.keyword,
      status: options.status ?? 'PENDING',
      priority: options.priority,
      dueRange: options.dueRange,
    },
  ));
}

export async function executeSystemTodoAction(
  systemId: string,
  todoId: string,
  actionCode: string,
  payload: {
    comment?: string;
    reason?: string;
    transferTargetId?: string;
    transferTargetName?: string;
  } = {},
): Promise<TodoActionResult> {
  return unwrap(await apiClient.post<TodoActionResult>(`/api/v1/systems/${systemId}/todos/${todoId}/actions/${actionCode}`, {
    idempotencyKey: `todo_${todoId}_${actionCode}_${Date.now()}`,
    ...payload,
  }));
}

export async function loadWorkDashboard(systemId: string): Promise<WorkDashboard> {
  return unwrap(await apiClient.get<WorkDashboard>(`/api/v1/systems/${systemId}/work/dashboard`));
}

export async function loadProjectTasks(systemId: string, options: WorkSearchOptions = {}): Promise<PageResult<WorkTask>> {
  const pageNo = options.pageNo ?? 1;
  const pageSize = options.pageSize ?? 20;
  return unwrap(await apiClient.post<PageResult<WorkTask>>(
    `/api/v1/systems/${systemId}/work/project-tasks/search?pageNo=${pageNo}&pageSize=${pageSize}`,
    {
      keyword: options.keyword,
      status: options.status,
      projectId: options.projectId,
    },
  ));
}

export async function loadPlainTasks(systemId: string, options: WorkSearchOptions = {}): Promise<PageResult<WorkTask>> {
  const pageNo = options.pageNo ?? 1;
  const pageSize = options.pageSize ?? 20;
  return unwrap(await apiClient.post<PageResult<WorkTask>>(
    `/api/v1/systems/${systemId}/work/plain-tasks/search?pageNo=${pageNo}&pageSize=${pageSize}`,
    {
      keyword: options.keyword,
      status: options.status,
      projectId: options.projectId,
    },
  ));
}

export async function loadDailyReports(systemId: string, options: WorkSearchOptions = {}): Promise<PageResult<DailyReport>> {
  const pageNo = options.pageNo ?? 1;
  const pageSize = options.pageSize ?? 20;
  return unwrap(await apiClient.post<PageResult<DailyReport>>(
    `/api/v1/systems/${systemId}/work/daily-reports/search?pageNo=${pageNo}&pageSize=${pageSize}`,
    {
      keyword: options.keyword,
      status: options.status,
    },
  ));
}

export async function loadWorkProjects(systemId: string, options: WorkSearchOptions = {}): Promise<PageResult<WorkProject>> {
  const pageNo = options.pageNo ?? 1;
  const pageSize = options.pageSize ?? 50;
  return unwrap(await apiClient.post<PageResult<WorkProject>>(
    `/api/v1/systems/${systemId}/work/projects/search?pageNo=${pageNo}&pageSize=${pageSize}`,
    {
      keyword: options.keyword,
      status: options.status,
    },
  ));
}

export async function createWorkProject(systemId: string, input: {
  projectName: string;
  projectCode?: string;
  ownerMemberId?: string;
  status?: string;
  progress?: number;
  startDate?: string;
  endDate?: string;
}): Promise<WorkProject> {
  return unwrap(await apiClient.post<WorkProject>(`/api/v1/systems/${systemId}/work/projects`, {
    projectCode: input.projectCode || `PRJ-${Date.now()}`,
    projectName: input.projectName,
    ownerMemberId: input.ownerMemberId,
    status: input.status || 'TODO',
    progress: input.progress ?? 0,
    startDate: input.startDate,
    endDate: input.endDate,
  }));
}

export async function createProjectTask(systemId: string, input: {
  title: string;
  projectId?: string;
  assigneeMemberId?: string;
  status?: string;
  progress?: number;
  dueAt?: string;
  fieldValues?: Record<string, unknown>;
}): Promise<WorkTask> {
  const result = unwrap(await apiClient.post<{ task: WorkTask }>(`/api/v1/systems/${systemId}/work/project-tasks`, {
    title: input.title,
    projectId: input.projectId,
    assigneeMemberId: input.assigneeMemberId,
    collaborators: [],
    status: input.status || 'TODO',
    tagCodes: [],
    progress: input.progress ?? 0,
    dueAt: input.dueAt,
    fieldValues: input.fieldValues ?? {},
  }));
  return result.task;
}

export async function createPlainTask(systemId: string, input: {
  title: string;
  assigneeMemberId?: string;
  status?: string;
  progress?: number;
  dueAt?: string;
  fieldValues?: Record<string, unknown>;
}): Promise<WorkTask> {
  const result = unwrap(await apiClient.post<{ task: WorkTask }>(`/api/v1/systems/${systemId}/work/plain-tasks`, {
    title: input.title,
    assigneeMemberId: input.assigneeMemberId,
    collaborators: [],
    status: input.status || 'TODO',
    tagCodes: [],
    progress: input.progress ?? 0,
    dueAt: input.dueAt,
    fieldValues: input.fieldValues ?? {},
  }));
  return result.task;
}

export async function createDailyReport(systemId: string, input: {
  reportDate: string;
  content: string;
  status?: string;
  sourceIds?: string[];
  submitNow?: boolean;
}): Promise<DailyReport> {
  return unwrap(await apiClient.post<DailyReport>(`/api/v1/systems/${systemId}/work/daily-reports`, {
    reportDate: input.reportDate,
    content: input.content,
    status: input.status || 'DRAFT',
    sourceIds: input.sourceIds ?? [],
    submitNow: input.submitNow ?? false,
  }));
}

export async function autoDraftDailyReport(systemId: string): Promise<DailyReportAutoDraft> {
  return unwrap(await apiClient.post<DailyReportAutoDraft>(`/api/v1/systems/${systemId}/work/daily-reports/auto-draft`));
}

export async function loadPlatformAdminData(options: PlatformAdminPageOptions = {}): Promise<PlatformAdminData> {
  const warnings: string[] = [];
  const systemsPageNo = options.systemsPageNo ?? 1;
  const rolesPageNo = options.rolesPageNo ?? 1;
  const modelAuthorizationsPageNo = options.modelAuthorizationsPageNo ?? 1;
  const logsPageNo = options.logsPageNo ?? 1;
  const [systems, roles, identityProviders, modelAuthorizations, logs, health] = await Promise.all([
    safeLoad(async () => unwrap(await apiClient.get<PageResult<PlatformSystem>>(`/api/v1/platform/systems?pageNo=${systemsPageNo}&pageSize=20`)), '系统生命周期', warnings, emptyPage<PlatformSystem>()),
    safeLoad(async () => unwrap(await apiClient.get<PageResult<RoleView>>(`/api/v1/platform/roles?pageNo=${rolesPageNo}&pageSize=20`)), '平台角色', warnings, emptyPage<RoleView>()),
    safeLoad(async () => unwrap(await apiClient.get<IdentityProviderView[]>('/api/v1/platform/identity-providers')), '企业 SSO 身份源', warnings, []),
    safeLoad(async () => unwrap(await apiClient.get<PageResult<ModelAuthorizationView>>(`/api/v1/platform/agent/model-authorizations?pageNo=${modelAuthorizationsPageNo}&pageSize=20`)), '平台 AI Agent 授权', warnings, emptyPage<ModelAuthorizationView>()),
    safeLoad(async () => unwrap(await apiClient.post<PageResult<AuditLogView>>(`/api/v1/platform/logs/search?pageNo=${logsPageNo}&pageSize=12`, {
      scope: 'platform',
    })), '平台日志', warnings, emptyPage<AuditLogView>()),
    safeLoad(async () => unwrap(await apiClient.get<PlatformHealth>('/api/v1/platform/health')), '平台健康', warnings, undefined),
  ]);
  return { systems, roles, identityProviders, modelAuthorizations, logs, health, warnings };
}

export async function createPlatformSystem(input: {
  systemName: string;
  systemCode: string;
  tenantMode: number;
}): Promise<PlatformSystem> {
  return unwrap(await apiClient.post<PlatformSystem>('/api/v1/platform/systems', {
    ...input,
    templateCode: 'starter',
  }));
}

export async function loadNoMemberAccessRequests(systemId: string, status = ''): Promise<PageResult<NoMemberAccessRequestView>> {
  const params = new URLSearchParams({ pageNo: '1', pageSize: '20' });
  if (status) {
    params.set('status', status);
  }
  return unwrap(await apiClient.get<PageResult<NoMemberAccessRequestView>>(
    `/api/v1/systems/${systemId}/no-member-access-requests?${params.toString()}`,
  ));
}

export async function createNoMemberAccessRequest(systemId: string, input: {
  identityProvider?: string;
  externalUserId?: string;
  tenantId?: string;
  requestRole?: string;
  requestReason: string;
}): Promise<NoMemberAccessRequestView> {
  const idempotencyKey = createIdempotencyKey('no_member_access_request');
  return unwrap(await apiClient.post<NoMemberAccessRequestView>(
    `/api/v1/systems/${systemId}/no-member-access-requests`,
    {
      identityProvider: input.identityProvider ?? 'LOCAL_ACCOUNT',
      externalUserId: input.externalUserId ?? localStorage.getItem('unexamine.accountId') ?? 'current_account',
      tenantId: input.tenantId,
      requestRole: input.requestRole ?? 'SYSTEM_MEMBER',
      requestReason: input.requestReason,
      idempotencyKey,
    },
    idempotencyKey,
  ));
}

export async function runPlatformSystemLifecycle(systemId: string, action: 'enable' | 'disable' | 'restore' | 'delete', reason: string): Promise<PlatformSystemLifecycleResult> {
  const body = {
    reason,
    impactScope: 'PLATFORM_SYSTEM_LIFECYCLE',
    idempotencyKey: createIdempotencyKey(`platform_system_${action}`),
  };
  if (action === 'delete') {
    return unwrap(await apiClient.delete<PlatformSystemLifecycleResult>(
      `/api/v1/platform/systems/${systemId}`,
      body.idempotencyKey,
      body,
    ));
  }
  return unwrap(await apiClient.post<PlatformSystemLifecycleResult>(
    `/api/v1/platform/systems/${systemId}/${action}`,
    body,
    body.idempotencyKey,
  ));
}

export async function createPlatformIdentityProvider(input: {
  name: string;
  protocol: string;
  issuer?: string;
  clientId?: string;
  secretRefId?: string;
  certRefId?: string;
  domainWhitelist?: string[];
}): Promise<IdentityProviderView> {
  return unwrap(await apiClient.post<IdentityProviderView>('/api/v1/platform/identity-providers', {
    ...input,
    jitPolicy: { enabled: true, createAccount: true },
    mfaPolicy: { inherited: true },
    status: 'DRAFT',
  }));
}

export async function testPlatformIdentityProvider(providerId: string, redirectUri: string, testLoginName: string): Promise<IdentityProviderTestResult> {
  return unwrap(await apiClient.post<IdentityProviderTestResult>(`/api/v1/platform/identity-providers/${providerId}/test`, {
    redirectUri,
    testLoginName,
  }));
}

export async function publishPlatformIdentityProvider(providerId: string): Promise<IdentityProviderPublishResult> {
  return unwrap(await apiClient.post<IdentityProviderPublishResult>(`/api/v1/platform/identity-providers/${providerId}/publish`));
}

export async function createPlatformModelAuthorization(input: {
  authorizationCode: string;
  modelProvider: string;
  modelName: string;
}): Promise<ModelAuthorizationView> {
  const idempotencyKey = createIdempotencyKey('platform_model_authorization');
  return unwrap(await apiClient.post<ModelAuthorizationView>(
    '/api/v1/platform/agent/model-authorizations',
    {
      authorizationCode: input.authorizationCode,
      modelProvider: input.modelProvider,
      modelName: input.modelName,
      quota: {
        tokenLimit: 100000,
        requestLimit: 10000,
        costLimit: 0,
        resetPolicy: 'MONTHLY',
      },
      dataOutboundPolicy: {
        allowExternalModel: false,
        allowedRegions: [],
        outboundFields: [],
        retentionDays: 0,
      },
      status: 1,
      idempotencyKey,
    },
    idempotencyKey,
  ));
}

export async function runPlatformHealthCheck(checkType = 'FLOW'): Promise<OpsHealthCheck> {
  return unwrap(await apiClient.post<OpsHealthCheck>('/api/v1/platform/ops/health-check', {
    checkType,
    checkItems: [],
    requestedBy: localStorage.getItem('unexamine.accountId') ?? 'frontend',
    idempotencyKey: createIdempotencyKey(`platform_health_${checkType}`),
  }));
}

export async function updatePlatformFeatureFlag(flagId: string): Promise<OpsFeatureFlagView> {
  return unwrap(await apiClient.patch<OpsFeatureFlagView>(`/api/v1/platform/ops/feature-flags/${flagId}`, {
    status: 1,
    rules: 'role=PLATFORM_ROOT; percent=20',
    rollbackVersion: `flag_v${Date.now()}`,
  }));
}

export async function updatePlatformQuota(quotaId: string): Promise<OpsQuotaView> {
  return unwrap(await apiClient.patch<OpsQuotaView>(`/api/v1/platform/ops/quotas/${quotaId}`, {
    limit: 800,
    warnThreshold: 640,
  }));
}

export async function updatePlatformRateLimitPolicy(policyId: string): Promise<OpsRateLimitPolicyView> {
  return unwrap(await apiClient.patch<OpsRateLimitPolicyView>(`/api/v1/platform/ops/rate-limit-policies/${policyId}`, {
    limitRule: 'dimension=appKey; window=1m; limit=800; overflow=REJECT_WITH_CODE',
    status: 1,
  }));
}

export async function createPlatformBackupTask(): Promise<AsyncTask> {
  const idempotencyKey = createIdempotencyKey('ops_backup');
  return unwrap(await apiClient.post<AsyncTask>('/api/v1/platform/ops/backups', {
    backupType: 'FULL',
    scope: 'PLATFORM',
    boundaryPayload: {
      includes: ['database', 'files', 'config', 'secret_refs'],
      secretPolicy: 'secret_ref_only',
    },
    idempotencyKey,
  }, idempotencyKey));
}

export async function runPlatformRestoreDrill(backupId = 'backup_20260623_001'): Promise<AsyncTask> {
  const idempotencyKey = createIdempotencyKey('ops_restore_drill');
  return unwrap(await apiClient.post<AsyncTask>(`/api/v1/platform/ops/backups/${backupId}/restore-drill`, {
    drillScope: 'CONFIG_AND_FILES',
    dryRun: true,
    idempotencyKey,
  }, idempotencyKey));
}

export async function createPlatformArchiveRestoreRequest(): Promise<AsyncTask> {
  const idempotencyKey = createIdempotencyKey('ops_archive_restore');
  return unwrap(await apiClient.post<AsyncTask>('/api/v1/platform/ops/archive-restore-requests', {
    scope: 'PLATFORM',
    objectType: 'BUSINESS_LOG',
    archiveCondition: 'older_than_180_days',
    reason: 'platform ops dry-run from admin page',
    idempotencyKey,
  }, idempotencyKey));
}

export async function loadPlatformDeployments(): Promise<PageResult<OpsDeploymentView>> {
  return unwrap(await apiClient.get<PageResult<OpsDeploymentView>>('/api/v1/platform/ops/deployments?pageNo=1&pageSize=10'));
}

export async function rollbackPlatformDeployment(deploymentId: string): Promise<AsyncTask> {
  const idempotencyKey = createIdempotencyKey('ops_deployment_rollback');
  return unwrap(await apiClient.post<AsyncTask>(`/api/v1/platform/ops/deployments/${deploymentId}/rollback`, {
    targetVersion: 'previous-stable',
    dryRun: true,
    confirmNoDestructiveScript: true,
    idempotencyKey,
  }, idempotencyKey));
}

export async function loadPlatformApiCachePolicy(): Promise<OpsApiCachePolicyView[]> {
  return unwrap(await apiClient.get<OpsApiCachePolicyView[]>('/api/v1/platform/ops/api-cache-policy'));
}

export async function updatePlatformApiCachePolicy(): Promise<OpsApiCachePolicyView[]> {
  return unwrap(await apiClient.patch<OpsApiCachePolicyView[]>('/api/v1/platform/ops/api-cache-policy', {
    keyRule: 'system:{systemId}:member:{memberId}:perm',
    invalidationRule: 'role_permission_changed OR member_binding_changed OR publish_version_changed',
    status: 1,
  }));
}

export async function runSystemHealthCheck(systemId: string, checkType = 'SYSTEM'): Promise<OpsHealthCheck> {
  return unwrap(await apiClient.post<OpsHealthCheck>(`/api/v1/systems/${systemId}/ops/health-check`, {
    checkType,
    checkItems: [],
    requestedBy: localStorage.getItem('unexamine.accountId') ?? 'frontend',
    idempotencyKey: createIdempotencyKey(`system_health_${checkType}`),
  }));
}

export async function loadSystemAdminData(systemId: string, options: SystemAdminPageOptions = {}): Promise<SystemAdminData> {
  const warnings: string[] = [];
  const membersPageNo = options.membersPageNo ?? 1;
  const rolesPageNo = options.rolesPageNo ?? 1;
  const modulesPageNo = options.modulesPageNo ?? 1;
  const dictTypesPageNo = options.dictTypesPageNo ?? 1;
  const flowsPageNo = options.flowsPageNo ?? 1;
  const notificationTemplatesPageNo = options.notificationTemplatesPageNo ?? 1;
  const dataSourcesPageNo = options.dataSourcesPageNo ?? 1;
  const openApiAppsPageNo = options.openApiAppsPageNo ?? 1;
  const agentPoliciesPageNo = options.agentPoliciesPageNo ?? 1;
  const logsPageNo = options.logsPageNo ?? 1;
  const [
    departments,
    members,
    roles,
    moduleGroups,
    modules,
    dictTypes,
    flows,
    notificationTemplates,
    dataSources,
    openApiApps,
    ssoPolicy,
    workConfig,
    homePageConfig,
    agentPolicies,
    logs,
  ] = await Promise.all([
    safeLoad(async () => unwrap(await apiClient.get<DepartmentNode[]>(`/api/v1/systems/${systemId}/org/departments`)), '组织架构', warnings, []),
    safeLoad(async () => unwrap(await apiClient.get<PageResult<MemberView>>(`/api/v1/systems/${systemId}/members?pageNo=${membersPageNo}&pageSize=20`)), '系统成员', warnings, emptyPage<MemberView>()),
    safeLoad(async () => unwrap(await apiClient.get<PageResult<RoleView>>(`/api/v1/systems/${systemId}/roles?pageNo=${rolesPageNo}&pageSize=20`)), '系统角色', warnings, emptyPage<RoleView>()),
    safeLoad(async () => unwrap(await apiClient.get<BackendModuleGroup[]>(`/api/v1/systems/${systemId}/module-groups`)), '模块分组', warnings, []),
    safeLoad(async () => unwrap(await apiClient.get<PageResult<BackendModule>>(`/api/v1/systems/${systemId}/modules?pageNo=${modulesPageNo}&pageSize=50`)), '模块列表', warnings, emptyPage<BackendModule>()),
    safeLoad(async () => unwrap(await apiClient.get<PageResult<DictTypeView>>(`/api/v1/systems/${systemId}/dict-types?pageNo=${dictTypesPageNo}&pageSize=20`)), '数据字典', warnings, emptyPage<DictTypeView>()),
    safeLoad(async () => unwrap(await apiClient.get<PageResult<FlowDefinitionView>>(`/api/v1/systems/${systemId}/flows?pageNo=${flowsPageNo}&pageSize=20`)), '流程管理', warnings, emptyPage<FlowDefinitionView>()),
    safeLoad(async () => unwrap(await apiClient.get<PageResult<NotificationTemplateView>>(`/api/v1/systems/${systemId}/notification-templates?pageNo=${notificationTemplatesPageNo}&pageSize=20`)), '消息模板', warnings, emptyPage<NotificationTemplateView>()),
    safeLoad(async () => unwrap(await apiClient.get<PageResult<SystemDataSourceView>>(`/api/v1/systems/${systemId}/data-sources?pageNo=${dataSourcesPageNo}&pageSize=20`)), '数据源', warnings, emptyPage<SystemDataSourceView>()),
    safeLoad(async () => unwrap(await apiClient.get<PageResult<OpenApiAppView>>(`/api/v1/systems/${systemId}/openapi/apps?pageNo=${openApiAppsPageNo}&pageSize=20`)), 'OpenAPI 对外应用', warnings, emptyPage<OpenApiAppView>()),
    safeLoad(async () => unwrap(await apiClient.get<SystemSsoPolicyView>(`/api/v1/systems/${systemId}/sso/policies`)), '系统 SSO 策略', warnings, undefined),
    safeLoad(async () => unwrap(await apiClient.get<WorkConfigView>(`/api/v1/systems/${systemId}/work/config`)), '工作配置', warnings, undefined),
    safeLoad(async () => unwrap(await apiClient.get<HomePageConfigView>(`/api/v1/systems/${systemId}/work/home-page-config`)), '首页设计配置', warnings, undefined),
    safeLoad(async () => unwrap(await apiClient.get<PageResult<AgentPolicyView>>(`/api/v1/systems/${systemId}/agent/policies?pageNo=${agentPoliciesPageNo}&pageSize=20`)), '系统 AI Agent 策略', warnings, emptyPage<AgentPolicyView>()),
    safeLoad(async () => unwrap(await apiClient.post<PageResult<AuditLogView>>(`/api/v1/systems/${systemId}/logs/search?pageNo=${logsPageNo}&pageSize=12`, {
      scope: 'system',
      systemId,
    })), '系统日志', warnings, emptyPage<AuditLogView>()),
  ]);
  const pageDesignModuleId = modules.records[0]?.moduleId;
  const pageDesigns = pageDesignModuleId
    ? await safeLoad(async () => unwrap(await apiClient.get<ModulePageDesignView[]>(`/api/v1/systems/${systemId}/modules/${pageDesignModuleId}/pages`)), '页面设计器', warnings, [])
    : [];
  return {
    departments,
    members,
    roles,
    moduleGroups,
    modules,
    dictTypes,
    flows,
    notificationTemplates,
    dataSources,
    openApiApps,
    ssoPolicy,
    workConfig,
    homePageConfig,
    pageDesigns,
    agentPolicies,
    logs,
    warnings,
  };
}

export async function loadSystemHomePageConfig(systemId: string): Promise<HomePageConfigView> {
  return unwrap(await apiClient.get<HomePageConfigView>(`/api/v1/systems/${systemId}/work/home-page`));
}

export async function createSystemModule(systemId: string, input: {
  groupId?: string;
  moduleCode: string;
  name: string;
  status?: number;
  description?: string;
}): Promise<BackendModule> {
  return unwrap(await apiClient.post<BackendModule>(
    `/api/v1/systems/${systemId}/modules`,
    {
      groupId: input.groupId,
      moduleCode: input.moduleCode,
      name: input.name,
      status: input.status ?? 1,
      description: input.description,
    },
    createIdempotencyKey('system_module_create'),
  ));
}

export async function createSystemModuleField(systemId: string, moduleId: string, input: {
  fieldCode: string;
  name: string;
  fieldType: string;
  dictTypeId?: string;
  required?: boolean;
  sortable?: boolean;
}): Promise<FieldDefinitionVO> {
  return unwrap(await apiClient.post<FieldDefinitionVO>(
    `/api/v1/systems/${systemId}/modules/${moduleId}/fields`,
    {
      fieldCode: input.fieldCode,
      name: input.name,
      fieldType: input.fieldType,
      storageType: input.fieldType === 'NUMBER' ? 'DECIMAL' : 'VARCHAR',
      required: input.required ?? false,
      sortable: input.sortable ?? true,
      dictTypeId: input.dictTypeId,
      permissionMetadata: {
        readableRoleIds: [],
        writableRoleIds: [],
        runtimeReadable: true,
        runtimeWritable: true,
        maskedWhenDenied: 'HIDDEN',
        permissionVersion: `field_perm_${Date.now()}`,
      },
      importExportRule: {
        importable: true,
        exportable: true,
        requiredOnImport: input.required ?? false,
        duplicateKey: input.fieldCode,
        desensitizeMode: 'PERMISSION',
      },
    },
    createIdempotencyKey('system_module_field_create'),
  ));
}

export async function listSystemModuleFields(systemId: string, moduleId: string): Promise<PageResult<FieldDefinitionVO>> {
  return unwrap(await apiClient.get<PageResult<FieldDefinitionVO>>(
    `/api/v1/systems/${systemId}/modules/${moduleId}/fields?pageNo=1&pageSize=100`,
  ));
}

export async function createSystemModuleAction(systemId: string, moduleId: string, input: {
  actionCode: string;
  actionName: string;
  actionType?: string;
  position?: string;
}): Promise<Record<string, unknown>> {
  return unwrap(await apiClient.post<Record<string, unknown>>(
    `/api/v1/systems/${systemId}/modules/${moduleId}/actions`,
    {
      actionCode: input.actionCode,
      actionName: input.actionName,
      actionType: input.actionType ?? 'ROW',
      position: input.position ?? 'ROW',
      selectionRule: {
        selectionMode: input.position === 'BATCH_BAR' ? 'MULTIPLE' : 'SINGLE',
        minSelected: 1,
        maxSelected: input.position === 'BATCH_BAR' ? 100 : 1,
        requiredStatuses: [],
        sameTenantRequired: true,
        forbiddenReason: '当前选择不满足动作执行条件。',
      },
      resultContract: {
        resultType: 'SYNC_RESULT',
        returnsAuditLog: true,
        returnsAsyncTask: false,
        resultDrawer: 'actionResultDrawer',
        traceField: 'traceId',
        userVisibleStates: ['SUCCESS', 'FAILED'],
      },
      enabled: true,
    },
    createIdempotencyKey('system_module_action_create'),
  ));
}

export async function createSystemDictType(systemId: string, input: {
  dictCode: string;
  dictName: string;
  dictKind?: string;
}): Promise<DictTypeView> {
  return unwrap(await apiClient.post<DictTypeView>(`/api/v1/systems/${systemId}/dict-types`, {
    dictCode: input.dictCode,
    dictName: input.dictName,
    dictKind: input.dictKind ?? 'NORMAL',
    status: 1,
  }));
}

export async function createSystemDictItem(systemId: string, dictTypeId: string, input: {
  itemCode: string;
  itemName: string;
  color?: string;
  icon?: string;
  semantic?: string;
  kanbanEnabled?: boolean;
}): Promise<DictItemView> {
  return unwrap(await apiClient.post<DictItemView>(`/api/v1/systems/${systemId}/dict-types/${dictTypeId}/items`, {
    itemCode: input.itemCode,
    itemName: input.itemName,
    color: input.color ?? '#2563EB',
    icon: input.icon ?? 'tag',
    semantic: input.semantic ?? input.itemCode,
    sort: 99,
    defaultFlag: false,
    kanbanEnabled: input.kanbanEnabled ?? true,
    status: 1,
  }));
}

export async function createSystemFlow(systemId: string, input: {
  flowCode: string;
  flowName: string;
  boundModuleId?: string;
}): Promise<FlowDefinitionView> {
  return unwrap(await apiClient.post<FlowDefinitionView>(`/api/v1/systems/${systemId}/flows`, {
    flowCode: input.flowCode,
    flowName: input.flowName,
    boundModuleId: input.boundModuleId,
    status: 1,
    triggerRule: {
      triggerType: 'MANUAL_ACTION',
      actionCodes: ['record.submitApproval'],
      conditionExpression: '',
      manualStartAllowed: true,
      idempotencyRequired: true,
    },
    canvas: {
      nodes: [],
      edges: [],
    },
    description: '系统后台创建的审批流程草稿',
  }));
}

export async function loadFlowNodeLibrary(systemId: string): Promise<FlowNodeLibraryItem[]> {
  return unwrap(await apiClient.get<FlowNodeLibraryItem[]>(`/api/v1/systems/${systemId}/flows/node-library`));
}

export async function loadFlowCanvas(systemId: string, flowId: string): Promise<FlowCanvasView> {
  return unwrap(await apiClient.get<FlowCanvasView>(`/api/v1/systems/${systemId}/flows/${flowId}/canvas`));
}

export async function saveFlowCanvas(systemId: string, flowId: string, canvas: {
  nodes: FlowNodeConfigView[];
  edges: FlowEdgeView[];
}): Promise<FlowDefinitionView> {
  return unwrap(await apiClient.put<FlowDefinitionView>(
    `/api/v1/systems/${systemId}/flows/${flowId}/canvas`,
    {
      nodes: canvas.nodes.map((node) => ({
        nodeKey: node.nodeKey,
        nodeType: node.nodeType,
        nodeName: node.nodeName,
        position: node.position,
        propertyPayload: node.propertyPayload ?? {},
        status: node.status ?? 1,
      })),
      edges: canvas.edges.map((edge) => ({
        edgeKey: edge.edgeKey,
        sourceNodeKey: edge.sourceNodeKey,
        targetNodeKey: edge.targetNodeKey,
        branchLabel: edge.branchLabel,
        conditionPayload: edge.conditionPayload ?? null,
      })),
    },
    createIdempotencyKey('flow_canvas_save'),
  ));
}

export async function simulateSystemFlow(systemId: string, flowId: string, fieldValues: Record<string, unknown> = {}): Promise<FlowSimulationResult> {
  return unwrap(await apiClient.post<FlowSimulationResult>(`/api/v1/systems/${systemId}/flows/${flowId}/simulate`, {
    versionNo: 'DRAFT',
    fieldValues,
    idempotencyKey: createIdempotencyKey('flow_simulate'),
  }));
}

export async function runFlowPublishCheck(systemId: string, flowId: string, reason: string): Promise<FlowPublishCheckResult> {
  const idempotencyKey = createIdempotencyKey('flow_publish_check');
  return unwrap(await apiClient.post<FlowPublishCheckResult>(`/api/v1/systems/${systemId}/flows/${flowId}/publish-check`, {
    reason,
    idempotencyKey,
  }));
}

export async function publishSystemFlow(systemId: string, flowId: string, reason: string): Promise<FlowPublishResult> {
  const idempotencyKey = createIdempotencyKey('flow_publish');
  return unwrap(await apiClient.post<FlowPublishResult>(
    `/api/v1/systems/${systemId}/flows/${flowId}/publish`,
    {
      reason,
      idempotencyKey,
    },
    idempotencyKey,
  ));
}

export async function createNotificationTemplate(systemId: string, input: {
  templateCode: string;
  templateType: string;
  channels?: string[];
}): Promise<NotificationTemplateView> {
  return unwrap(await apiClient.post<NotificationTemplateView>(`/api/v1/systems/${systemId}/notification-templates`, {
    templateCode: input.templateCode,
    scope: 'SYSTEM',
    templateType: input.templateType,
    variables: ['title', 'operatorName', 'targetName'],
    channels: input.channels ?? ['MESSAGE'],
    targetRule: {
      scope: 'SYSTEM',
      targetType: 'business_record',
      requiresSystemSwitch: false,
      fallbackAction: 'OPEN_SYSTEM_MESSAGE_CENTER',
    },
    dedupeKey: `${input.templateCode}:targetId`,
    readReceiptRequired: true,
    quietPolicy: { enabled: false, quietTimeRange: '', bypassTypes: [] },
    retryPolicy: { maxRetryCount: 3, intervalSeconds: 60, retryFailedOnly: true },
    status: 1,
  }));
}

export async function runNotificationTemplatePublishCheck(systemId: string, templateCode: string): Promise<NotificationTemplatePublishCheckResult> {
  return unwrap(await apiClient.post<NotificationTemplatePublishCheckResult>(`/api/v1/systems/${systemId}/notification-templates/${templateCode}/publish-check`));
}

export async function createOpenApiApp(systemId: string, input: {
  externalAppCode: string;
  appName: string;
  callbackUrl?: string;
  scopes?: string[];
}): Promise<OpenApiAppView> {
  const idempotencyKey = createIdempotencyKey('openapi_app_create');
  return unwrap(await apiClient.post<OpenApiAppView>(
    `/api/v1/systems/${systemId}/openapi/apps`,
    {
      externalAppCode: input.externalAppCode,
      appName: input.appName,
      status: 1,
      scopes: input.scopes ?? ['runtime.record.read'],
      callbackUrl: input.callbackUrl,
      rateLimit: {
        dimension: 'APP',
        windowSeconds: 60,
        limit: 120,
        burstLimit: 30,
        overflowPolicy: 'REJECT',
        status: 1,
      },
      secretMaterialRef: `sec_${input.externalAppCode}`,
      requestedSecretVersion: `v${Date.now()}`,
      idempotencyKey,
    },
    idempotencyKey,
  ));
}

export async function createSystemDataSource(systemId: string, input: {
  sourceCode: string;
  sourceName: string;
  sourceType: string;
  connectionConfig?: Record<string, unknown>;
  authConfig?: Record<string, unknown>;
  syncConfig?: Record<string, unknown>;
  desensitizeConfig?: Record<string, unknown>;
}): Promise<SystemDataSourceView> {
  const idempotencyKey = createIdempotencyKey('system_data_source_create');
  return unwrap(await apiClient.post<SystemDataSourceView>(
    `/api/v1/systems/${systemId}/data-sources`,
    {
      ...input,
      status: 1,
      idempotencyKey,
    },
    idempotencyKey,
  ));
}

export async function checkSystemDataSourceConnection(systemId: string, dataSourceId: string): Promise<SystemDataSourceCheckResult> {
  return unwrap(await apiClient.post<SystemDataSourceCheckResult>(
    `/api/v1/systems/${systemId}/data-sources/${dataSourceId}/connection-check`,
  ));
}

export async function runSystemDataSourcePublishCheck(systemId: string, dataSourceId: string): Promise<SystemDataSourceCheckResult> {
  return unwrap(await apiClient.post<SystemDataSourceCheckResult>(
    `/api/v1/systems/${systemId}/data-sources/${dataSourceId}/publish-check`,
  ));
}

export async function rotateOpenApiSecret(systemId: string, externalAppId: string): Promise<OpenApiSecretRotationJob> {
  const idempotencyKey = createIdempotencyKey('openapi_secret_rotate');
  return unwrap(await apiClient.post<OpenApiSecretRotationJob>(
    `/api/v1/systems/${systemId}/openapi/apps/${externalAppId}/rotate-secret`,
    {
      newMaterialRef: `sec_rotated_${Date.now()}`,
      requestedVersion: `v${Date.now()}`,
      dualWriteValidation: true,
      switchAfterValidation: true,
      rollbackPlan: '保留旧版本，失败后切回上一可用 SecretRef。',
      idempotencyKey,
    },
    idempotencyKey,
  ));
}

export async function createSystemDepartment(systemId: string, input: {
  parentId?: string;
  deptCode: string;
  deptName: string;
  sortOrder?: number;
  status?: number;
}): Promise<DepartmentNode> {
  return unwrap(await apiClient.post<DepartmentNode>(`/api/v1/systems/${systemId}/org/departments`, {
    parentId: input.parentId,
    deptCode: input.deptCode,
    deptName: input.deptName,
    sortOrder: input.sortOrder ?? 99,
    status: input.status ?? 1,
  }));
}

export async function createSystemMember(systemId: string, input: {
  deptId?: string;
  memberName: string;
  employeeNo?: string;
  mobile?: string;
  email?: string;
  roleIds?: string[];
}): Promise<MemberView> {
  return unwrap(await apiClient.post<MemberView>(`/api/v1/systems/${systemId}/members`, {
    deptId: input.deptId,
    memberName: input.memberName,
    employeeNo: input.employeeNo,
    mobile: input.mobile,
    email: input.email,
    status: 1,
    roleIds: input.roleIds ?? [],
  }));
}

export async function createSystemRole(systemId: string, input: {
  roleName: string;
  roleCode: string;
  roleType?: string;
  description?: string;
}): Promise<RoleView> {
  return unwrap(await apiClient.post<RoleView>(`/api/v1/systems/${systemId}/roles`, {
    roleName: input.roleName,
    roleCode: input.roleCode,
    roleType: input.roleType ?? 'CUSTOM',
    status: 1,
    description: input.description,
  }));
}

export async function createPlatformRole(input: {
  roleName: string;
  roleCode: string;
  roleType?: string;
  description?: string;
}): Promise<RoleView> {
  return unwrap(await apiClient.post<RoleView>('/api/v1/platform/roles', {
    roleName: input.roleName,
    roleCode: input.roleCode,
    roleType: input.roleType ?? 'CUSTOM',
    status: 1,
    description: input.description,
  }));
}

export async function updateSystemSsoPolicy(systemId: string, input: {
  enabledProviderIds?: string[];
  tenantDomains?: string[];
  orgMapping?: Record<string, unknown>;
  employeeBinding?: Record<string, unknown>;
  jitMemberPolicy?: Record<string, unknown>;
  noMemberFeedback?: Record<string, unknown>;
  status?: string;
}): Promise<SystemSsoPolicyView> {
  return unwrap(await apiClient.patch<SystemSsoPolicyView>(`/api/v1/systems/${systemId}/sso/policies`, {
    enabledProviderIds: input.enabledProviderIds ?? [],
    tenantDomains: input.tenantDomains ?? [],
    orgMapping: input.orgMapping ?? {},
    employeeBinding: input.employeeBinding ?? {},
    jitMemberPolicy: input.jitMemberPolicy ?? { enabled: true, requireApproval: true },
    noMemberFeedback: input.noMemberFeedback ?? { status: 'SUBMITTED', disabledReason: 'NO_SYSTEM_MEMBER_MAPPING' },
    status: input.status ?? 'DRAFT',
  }));
}

export async function precheckSystemSsoOrgSync(systemId: string, input: {
  identityProvider: string;
  tenantId?: string;
  externalDepartmentIds?: string[];
  externalUserIds?: string[];
}): Promise<SsoOrgSyncPrecheckResult> {
  return unwrap(await apiClient.post<SsoOrgSyncPrecheckResult>(`/api/v1/systems/${systemId}/sso/org-sync/precheck`, {
    identityProvider: input.identityProvider,
    tenantId: input.tenantId,
    externalDepartmentIds: input.externalDepartmentIds ?? [],
    externalUserIds: input.externalUserIds ?? [],
    dryRun: true,
    idempotencyKey: createIdempotencyKey('system_sso_org_precheck'),
  }));
}

export async function createSystemAgentPolicy(systemId: string, policyCode: string): Promise<AgentPolicyView> {
  const idempotencyKey = createIdempotencyKey('system_agent_policy');
  return unwrap(await apiClient.post<AgentPolicyView>(
    `/api/v1/systems/${systemId}/agent/policies`,
    {
      policyCode,
      scope: {
        moduleScope: ['*'],
        fieldScope: {},
        actionScope: ['read', 'statistics', 'draft_write'],
        dataScopeExpression: 'CURRENT_MEMBER_PERMISSION',
        outboundLimit: {
          maxRows: 500,
          allowFileExport: false,
          allowThirdPartyWebhook: false,
        },
        desensitizePolicy: {
          mode: 'MASK_SENSITIVE',
          maskedFields: [],
          preview: {},
        },
        policyVersion: `policy_${Date.now()}`,
      },
      status: 1,
      idempotencyKey,
    },
    idempotencyKey,
  ));
}

export async function runSystemAgentPolicyPublishCheck(systemId: string, policyId: string): Promise<AgentPolicyPublishCheckResult> {
  return unwrap(await apiClient.post<AgentPolicyPublishCheckResult>(`/api/v1/systems/${systemId}/agent/policies/${policyId}/publish-check`));
}

export async function updateSystemWorkConfig(systemId: string, config: WorkConfigView, changeReason: string): Promise<WorkConfigView> {
  return unwrap(await apiClient.patch<WorkConfigView>(`/api/v1/systems/${systemId}/work/config`, {
    projectTaskFields: config.projectTaskFields ?? [],
    plainTaskFields: config.plainTaskFields ?? [],
    dailyReportFields: config.dailyReportFields ?? [],
    projectTaskKanban: config.projectTaskKanban,
    plainTaskKanban: config.plainTaskKanban,
    dailyReportAutoSourceRule: config.dailyReportAutoSourceRule ?? {
      sourceTypes: ['TASK', 'TODO', 'MESSAGE', 'BUSINESS_LOG', 'APPROVAL'],
      taskScope: 'CURRENT_MEMBER_PROJECT_AND_PLAIN_TASKS',
      todoScope: 'CURRENT_MEMBER_TODOS',
      messageScope: 'CURRENT_MEMBER_MESSAGES',
      logScope: 'CURRENT_MEMBER_BUSINESS_LOGS',
      approvalScope: 'CURRENT_MEMBER_APPROVALS',
      permissionPolicy: 'CURRENT_MEMBER_PERMISSION',
      manualConfirmRequired: true,
    },
    changeReason,
  }));
}

export async function runWorkConfigPublishCheck(systemId: string): Promise<WorkConfigPublishCheckResult> {
  return unwrap(await apiClient.post<WorkConfigPublishCheckResult>(`/api/v1/systems/${systemId}/work/config/publish-check`));
}

export async function updateSystemHomePageConfig(systemId: string, config: {
  title: string;
  subtitle: string;
  visualTone: string;
  widgets: HomePageWidgetConfig[];
  changeReason: string;
}): Promise<HomePageConfigView> {
  return unwrap(await apiClient.patch<HomePageConfigView>(`/api/v1/systems/${systemId}/work/home-page-config`, config));
}

export async function runHomePagePublishCheck(systemId: string): Promise<WorkConfigPublishCheckResult> {
  return unwrap(await apiClient.post<WorkConfigPublishCheckResult>(`/api/v1/systems/${systemId}/work/home-page-config/publish-check`));
}

export async function saveModulePageDesign(systemId: string, moduleId: string, input: {
  pageCode: string;
  pageName: string;
  pageType: string;
  route: string;
  layoutMode: string;
  components: PageComponentConfig[];
  visibleRoleIds?: string[];
  changeReason: string;
}): Promise<ModulePageDesignView> {
  return unwrap(await apiClient.post<ModulePageDesignView>(`/api/v1/systems/${systemId}/modules/${moduleId}/pages`, input));
}

export async function runModulePagePublishCheck(systemId: string, moduleId: string, pageCode: string): Promise<ModulePublishCheckResult> {
  return unwrap(await apiClient.post<ModulePublishCheckResult>(`/api/v1/systems/${systemId}/modules/${moduleId}/pages/${pageCode}/publish-check`));
}

export async function publishModulePage(systemId: string, moduleId: string, pageCode: string, reason: string): Promise<ModulePublishResult> {
  const idempotencyKey = createIdempotencyKey('module_page_publish');
  return unwrap(await apiClient.post<ModulePublishResult>(
    `/api/v1/systems/${systemId}/modules/${moduleId}/pages/${pageCode}/publish`,
    {
      reason,
      idempotencyKey,
    },
    idempotencyKey,
  ));
}

export async function loadRuntimeModulePage(systemId: string, moduleId: string, pageCode: string): Promise<ModulePageDesignView> {
  return unwrap(await apiClient.get<ModulePageDesignView>(`/api/v1/systems/${systemId}/runtime/modules/${moduleId}/pages/${pageCode}`));
}

export async function runModulePublishCheck(systemId: string, moduleId: string, reason: string): Promise<ModulePublishCheckResult> {
  const idempotencyKey = createIdempotencyKey('module_publish_check');
  return unwrap(await apiClient.post<ModulePublishCheckResult>(
    `/api/v1/systems/${systemId}/modules/${moduleId}/publish-check`,
    {
      reason,
      idempotencyKey,
    },
  ));
}

export async function publishSystemModule(systemId: string, moduleId: string, reason: string): Promise<ModulePublishResult> {
  const idempotencyKey = createIdempotencyKey('module_publish');
  return unwrap(await apiClient.post<ModulePublishResult>(
    `/api/v1/systems/${systemId}/modules/${moduleId}/publish`,
    {
      reason,
      idempotencyKey,
    },
    idempotencyKey,
  ));
}

export async function rollbackSystemModule(systemId: string, moduleId: string, reason: string): Promise<ModulePublishResult> {
  const idempotencyKey = createIdempotencyKey('module_rollback');
  return unwrap(await apiClient.post<ModulePublishResult>(
    `/api/v1/systems/${systemId}/modules/${moduleId}/rollback`,
    {
      reason,
      idempotencyKey,
    },
    idempotencyKey,
  ));
}

export function messageTargetToPath(target: MessageTarget | undefined, currentSystemId: string): string {
  if (!target) {
    return `/systems/${currentSystemId}/messages`;
  }
  const systemId = target.targetSystemId ?? currentSystemId;
  if (target.targetType === 'approval_task') {
    return `/systems/${systemId}/todos`;
  }
  if (target.targetType === 'business_record') {
    return `/systems/${systemId}/modules`;
  }
  if (target.targetType === 'agent_result' || target.targetType === 'async_task') {
    return `/systems/${systemId}/work`;
  }
  if (target.requiresSystemSwitch) {
    return `/systems/${systemId}/dashboard`;
  }
  return `/systems/${systemId}/messages`;
}

export function platformMessageTargetToPath(target: MessageTarget | undefined): string {
  if (!target) {
    return '/platform';
  }
  if ((target.targetType === 'business_record' || target.targetType === 'approval_task' || target.targetType === 'system_switch') && target.targetSystemId) {
    return `/platform?targetSystemId=${encodeURIComponent(target.targetSystemId)}`;
  }
  if (target.targetType === 'platform_task' || target.targetType === 'platform_log' || target.targetType === 'audit_log') {
    return '/platform/admin';
  }
  return '/platform';
}

export function todoTargetToPath(target: TodoTarget | undefined, currentSystemId: string): string {
  if (!target) {
    return `/systems/${currentSystemId}/todos`;
  }
  const systemId = target.params.systemId || currentSystemId;
  if (target.targetType.includes('RECORD') || target.routeName.includes('Record')) {
    return `/systems/${systemId}/modules`;
  }
  return `/systems/${systemId}/todos`;
}

export function platformTodoTargetToPath(target: TodoTarget | undefined): string {
  if (!target) {
    return '/platform';
  }
  const systemId = target.params.systemId;
  if (systemId && (target.targetType.includes('SYSTEM') || target.requiresSystemSwitch)) {
    return `/platform?targetSystemId=${encodeURIComponent(systemId)}`;
  }
  if (target.targetType.includes('RECORD') && systemId) {
    return `/platform?targetSystemId=${encodeURIComponent(systemId)}`;
  }
  return '/platform/admin';
}

function toRuntimeModuleItem(module: BackendModule, active: boolean): RuntimeModuleItem {
  const enabled = module.status === 1 || module.status === 'ENABLED';
  return {
    groupId: module.groupId,
    moduleId: module.moduleId,
    moduleCode: module.moduleCode,
    name: module.name,
    count: 0,
    publishVersion: module.currentVersion ?? module.publishStatus ?? 'draft',
    active,
    disabledReason: enabled ? undefined : '当前模块已停用或未授权。',
  };
}

async function toRuntimeRecordRow(
  systemId: string,
  moduleId: string,
  row: BackendRecordRow,
  schema: BackendRuntimeListSchema,
  index: number,
): Promise<RuntimeRecordRow> {
  const detail = await loadRecordDetail(systemId, moduleId, row, schema);
  const fields = fieldMap(row.fields);
  const status = displayField(fields, ['status', 'recordStatus', 'state']) || String(detail.summary.status ?? '进行中');
  return {
    recordId: row.recordId,
    title: row.title,
    summary: row.summary,
    fields,
    actions: toActions(row.actions),
    disabledReasons: row.disabledReasons ?? {},
    permissionSnapshotVersion: row.permissionSnapshotVersion,
    serialNo: String(index + 1).padStart(3, '0'),
    favorite: false,
    model: displayField(fields, ['model', 'brandModel', 'name']) || row.summary || '-',
    statusLabel: status,
    statusTone: statusTone(status),
    department: displayField(fields, ['department', 'deptName', 'ownerDepartment']) || '-',
    owner: displayField(fields, ['owner', 'assignee', 'driver', 'responsiblePerson']) || '-',
    buyDate: displayField(fields, ['buyDate', 'createdAt', 'purchaseDate']) || '-',
    inspectionDue: displayField(fields, ['inspectionDue', 'dueAt', 'deadline']) || '-',
    lastMaintenance: displayField(fields, ['lastMaintenance', 'updatedAt']) || row.updatedAt || '-',
    traceId: `TRACE-${row.recordId}`,
    auditLogId: `AUD-${row.recordId}`,
    editDisabledReason: row.disabledReasons?.edit,
    deleteDisabledReason: row.disabledReasons?.delete,
    transferDisabledReason: row.disabledReasons?.transfer,
    detail,
  };
}

async function loadRecordDetail(
  systemId: string,
  moduleId: string,
  row: BackendRecordRow,
  schema: BackendRuntimeListSchema,
): Promise<BusinessDetailView> {
  try {
    const response = await apiClient.get<BackendBusinessDetail>(`/api/v1/systems/${systemId}/runtime/modules/${moduleId}/records/${row.recordId}`);
    if (response.code === 'SUCCESS' && response.data) {
      return toBusinessDetailView(response.data, row, schema);
    }
  } catch {
    // Detail fallback keeps the list usable when a single detail call fails.
  }
  return fallbackBusinessDetail(row, schema);
}

function toBusinessDetailView(detail: BackendBusinessDetail, row: BackendRecordRow, schema: BackendRuntimeListSchema): BusinessDetailView {
  const childRows = Object.fromEntries(
    detail.tabs
      .filter((tab) => tab.visible)
      .map((tab) => [tab.tabName, tabPayloadRows(tab.payload)]),
  );
  return {
    summary: {
      标题: detail.summary.title,
      摘要: detail.summary.summary ?? row.summary ?? '',
      状态: detail.summary.status ?? '',
      权限版本: row.permissionSnapshotVersion,
      schemaVersion: schema.schemaVersion ?? '',
    },
    baseFields: Object.fromEntries(detail.baseFields.map((field) => [field.label, field.displayValue ?? String(field.value ?? '')])),
    childRows,
    relations: [],
    attachments: tabFiles(detail.tabs),
    printRecords: [],
    operationLogs: operationRows(detail.tabs),
    approvalSidebar: detail.approvalSidebar?.visible
      ? {
          instanceId: detail.approvalSidebar.flowInstanceId ?? '',
          currentNodeName: detail.approvalSidebar.currentNodeName ?? detail.approvalSidebar.status ?? '当前节点',
          availableActions: toActions(detail.actions ?? []),
          timeline: [
            {
              nodeName: detail.approvalSidebar.currentNodeName ?? '当前节点',
              result: detail.approvalSidebar.status,
            },
          ],
        }
      : undefined,
    fieldMaskResults: Object.fromEntries((detail.fieldMaskResults ?? []).map((item) => [item.fieldCode, item.reason ?? item.permissionMode])),
  };
}

function fallbackBusinessDetail(row: BackendRecordRow, schema: BackendRuntimeListSchema): BusinessDetailView {
  const fields = Object.fromEntries(row.fields.map((field) => [field.label, field.displayValue ?? String(field.value ?? '')]));
  return {
    summary: {
      标题: row.title,
      摘要: row.summary ?? '',
      权限版本: row.permissionSnapshotVersion,
      列表版本: schema.schemaVersion ?? '',
    },
    baseFields: fields,
    childRows: { 业务字段: [fields] },
    relations: [],
    attachments: [],
    printRecords: [],
    operationLogs: [],
    fieldMaskResults: {},
  };
}

function toDynamicListSchema(schema: BackendRuntimeListSchema): DynamicListSchema {
  const columns: DynamicColumn[] = schema.columns.map((column) => ({
    fieldId: column.fieldId,
    fieldCode: column.fieldCode,
    title: column.label,
    width: column.width,
    sortable: column.sortable,
    masked: column.permissionMode === 'MASKED' || Boolean(column.maskRule),
  }));
  const filters: DynamicFilter[] = schema.filters.map((filter) => ({
    fieldId: filter.fieldId,
    fieldCode: filter.fieldCode,
    operators: filter.operators as DynamicFilter['operators'],
    component: filter.advanced ? 'advanced-input' : 'quick-input',
  }));
  const sorters: SortCriterion[] = schema.sorters
    .filter((sorter) => sorter.defaultSort)
    .map((sorter) => ({
      field: sorter.fieldCode,
      direction: sorter.defaultDirection === 'DESC' ? 'DESC' : 'ASC',
    }));
  return {
    moduleId: schema.moduleId,
    moduleCode: schema.moduleCode,
    sceneId: schema.sceneCode ?? 'default',
    columns,
    filters,
    sorters,
    page: { pageNo: 1, pageSize: 10, filters: [], sorts: sorters },
    rowClickTarget: schema.rowDetailTarget?.drawerCode ?? 'recordDetailDrawer',
    batchActions: toActions(schema.batchActions),
    toolbarActions: toActions(schema.toolbarActions),
    importExportConfig: {
      importEnabled: schema.toolbarActions.some((action) => action.actionCode.includes('import')),
      exportEnabled: schema.toolbarActions.some((action) => action.actionCode.includes('export')),
      exportAllEnabled: schema.toolbarActions.some((action) => action.actionCode.includes('export')),
      resultTaskRequired: true,
    },
    emptyState: { title: '暂无业务数据', actionCode: 'create' },
    permissionSnapshotId: schema.permissionSnapshotId,
  };
}

function toRuntimeFields(schema: BackendRuntimeListSchema): FieldDefinitionVO[] {
  return schema.columns.map((column) => ({
    fieldId: column.fieldId,
    fieldCode: column.fieldCode,
    name: column.label,
    fieldType: 'TEXT',
    storageType: 'VARCHAR',
    filterOperators: ['LIKE', 'EQ'],
    sortable: column.sortable,
    required: false,
    maskRule: column.maskRule,
    importExportRule: 'IMPORT_EXPORT',
  }));
}

function toActions(actions: BackendActionView[]): ActionContract[] {
  return actions.map((action) => ({
    actionCode: action.actionCode,
    name: action.actionName,
    enabled: action.enabled,
    disabledReason: action.disabledReason,
    maxSelection: action.selectionLimit?.maxSelected,
  }));
}

function fieldMap(fields: BackendFieldValue[]): Record<string, unknown> {
  return Object.fromEntries(fields.map((field) => [field.fieldCode, field.displayValue ?? field.value ?? '']));
}

function displayField(fields: Record<string, unknown>, candidates: string[]): string {
  for (const candidate of candidates) {
    const value = fields[candidate];
    if (value !== undefined && value !== null && String(value).trim()) {
      return String(value);
    }
  }
  return '';
}

function statusTone(status: string): StatusTone {
  if (/失败|拒绝|逾期|停用|风险/.test(status)) {
    return 'danger';
  }
  if (/待|审批|确认|临期/.test(status)) {
    return 'warning';
  }
  if (/完成|通过|启用|在用/.test(status)) {
    return 'success';
  }
  return 'info';
}

function tabPayloadRows(payload: Record<string, unknown> | undefined): Record<string, unknown>[] {
  if (!payload) {
    return [];
  }
  const rows = Object.values(payload).find((value): value is Record<string, unknown>[] => Array.isArray(value) && value.every((item) => typeof item === 'object' && item !== null && !Array.isArray(item)));
  if (rows) {
    return rows;
  }
  return [payload];
}

function tabFiles(tabs: BackendDetailTab[]): FileRef[] {
  const files: FileRef[] = [];
  tabs.forEach((tab) => {
    Object.values(tab.payload ?? {}).forEach((value) => {
      if (Array.isArray(value)) {
        value.forEach((item) => {
          if (isRecord(item) && typeof item.fileId === 'string') {
            files.push({
              fileId: item.fileId,
              fileName: String(item.fileName ?? item.name ?? item.fileId),
              downloadUrl: typeof item.downloadUrl === 'string' ? item.downloadUrl : undefined,
            });
          }
        });
      }
    });
  });
  return files;
}

function operationRows(tabs: BackendDetailTab[]): OperationRecord[] {
  return tabs.flatMap((tab) => tabPayloadRows(tab.payload))
    .filter((row) => row.traceId || row.action)
    .map((row) => ({
      operator: String(row.operator ?? row.operatedBy ?? '-'),
      action: String(row.action ?? tabNameFromRow(row)),
      operatedAt: String(row.operatedAt ?? row.createdAt ?? '-'),
      traceId: String(row.traceId ?? '-'),
    }));
}

function tabNameFromRow(row: Record<string, unknown>): string {
  return String(row.title ?? row.name ?? '操作记录');
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

async function safeLoad<T>(loader: () => Promise<T>, label: string, warnings: string[], fallback: T): Promise<T> {
  try {
    return await loader();
  } catch (error) {
    warnings.push(`${label}加载失败：${error instanceof Error ? error.message : '未知错误'}`);
    return fallback;
  }
}

function emptyPage<T>(): PageResult<T> {
  return {
    records: [],
    pageNo: 1,
    pageSize: 20,
    total: 0,
    hasNext: false,
  };
}

function createIdempotencyKey(prefix: string): string {
  return `${prefix}_${Date.now()}_${Math.random().toString(16).slice(2, 10)}`;
}

function unwrap<T>(response: ApiResponse<T>): T {
  if (response.code !== 'SUCCESS') {
    throw new Error(`${response.message || response.code}${response.traceId ? ` traceId=${response.traceId}` : ''}`);
  }
  return response.data;
}
