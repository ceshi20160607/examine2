export type ApiId = string;
export type IsoDateTimeString = string;
export type EntityId = string;
export type JsonPrimitive = string | number | boolean | null;
export type JsonValue = JsonPrimitive | JsonValue[] | { [key: string]: JsonValue };
export type DynamicRawValue = JsonValue | FileBindDTO[] | DynamicFieldValue[];

export interface ApiResponseMeta {
  path: string;
  method: HttpMethod;
  idempotencyKey?: string;
  requestHash?: string;
  idempotencyReplay?: boolean;
  resultSnapshotId?: string;
  [key: string]: JsonValue | undefined;
}

export interface ApiResponse<TData> {
  requestId: string;
  traceId: string;
  timestamp: IsoDateTimeString;
  success: true;
  code: string;
  message: string;
  data: TData;
  meta: ApiResponseMeta;
  errors: [];
}

export interface ApiErrorResponse {
  requestId: string;
  traceId: string;
  timestamp: IsoDateTimeString;
  success: false;
  code: string;
  message: string;
  data: null;
  meta: ApiResponseMeta;
  errors: ApiErrorDetail[];
}

export type ApiResult<TData> = ApiResponse<TData> | ApiErrorResponse;

export interface ApiErrorDetail {
  errorId: string;
  targetType?: ErrorTargetType;
  fieldCode?: string;
  objectType?: string;
  objectId?: EntityId;
  reason: string;
  expected?: JsonValue;
  actual?: JsonValue;
  retryable: boolean;
  userMessage: string;
}

export type ErrorTargetType =
  | "FIELD"
  | "RECORD"
  | "PERMISSION"
  | "STATE"
  | "OPENAPI"
  | "FILE"
  | "EXPORT"
  | "FLOW";

export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
export type SortDirection = "ASC" | "DESC";
export type FilterOperator =
  | "EQ"
  | "NE"
  | "LIKE"
  | "IN"
  | "BETWEEN"
  | "GT"
  | "GE"
  | "LT"
  | "LE"
  | "IS_NULL"
  | "NOT_NULL";

export interface SortRule {
  field: string;
  direction: SortDirection;
}

export interface FilterRule<TValue = JsonValue> {
  field: string;
  op: FilterOperator;
  value?: TValue;
}

export interface PageQuery<TFilterValue = JsonValue> {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  filters?: FilterRule<TFilterValue>[];
  sorter?: SortRule[];
}

export interface PageResult<TRecord> {
  records: TRecord[];
  total: number;
  pageNo: number;
  pageSize: number;
  hasNext: boolean;
}

export interface DynamicFieldValue<TValue = DynamicRawValue> {
  fieldId?: EntityId;
  fieldCode: string;
  fieldType?: DynamicFieldType;
  value: TValue;
  displayValue?: JsonValue;
  valueSnapshot?: Record<string, JsonValue>;
  readonlyReason?: string;
}

export interface FileBindDTO {
  fileId: EntityId;
  fieldCode: string;
  displayName?: string;
  sortOrder?: number;
  bizType?: string;
}

export interface AvailableAction {
  actionCode: string;
  label: string;
  visible: boolean;
  enabled: boolean;
  disabledReason?: string;
  requiredPermission?: string;
  stateReason?: string;
  confirmRequired?: boolean;
  danger?: boolean;
}

export interface PermissionHint {
  permissionCode: string;
  granted: boolean;
  disabledByRole?: boolean;
  disabledByState?: boolean;
  disabledByDataScope?: boolean;
  message?: string;
}

export interface FieldPermission {
  fieldCode: string;
  visible: boolean;
  writable: boolean;
  exportPlain?: boolean;
  openApiReadable?: boolean;
  openApiWritable?: boolean;
  readonlyReason?: string;
}

export type DataScopeType = "SELF" | "DEPT" | "DEPT_TREE" | "ALL" | "CUSTOM";

export interface DataScopeRuleDTO {
  scopeType: DataScopeType;
  deptIds?: EntityId[];
  memberIds?: EntityId[];
  customConditions?: FilterRule[];
  minVisibleRule?: string;
}

export interface EffectivePermissionVO {
  memberId: EntityId;
  roles: string[];
  menus: string[];
  operations: string[];
  fieldPermissions: FieldPermission[];
  dataScopes: DataScopeRuleDTO[];
  availableActions: AvailableAction[];
  version: number;
}

export interface FieldDefinitionVO {
  fieldId: EntityId;
  fieldCode: string;
  fieldName: string;
  fieldType: DynamicFieldType;
  required: boolean;
  unique: boolean;
  uniqueConstraints?: UniqueConstraintDTO[];
  options?: JsonValue;
  dictTypeId?: EntityId;
  relationConfig?: JsonValue;
  subTableConfig?: JsonValue;
  serialConfig?: JsonValue;
  defaultValue?: JsonValue;
  status: FieldStatus;
  version: number;
  fieldPermissions?: FieldPermission[];
}

export interface UniqueConstraintDTO {
  constraintCode: string;
  fieldCodes: string[];
  enabled: boolean;
}

export interface RuntimeModuleSchemaVO {
  moduleId: EntityId;
  moduleCode: string;
  publishedVersionId: EntityId;
  listSchema: JsonValue;
  formSchema: JsonValue;
  detailSchema: JsonValue;
  fieldDefinitions: FieldDefinitionVO[];
  availableActions: AvailableAction[];
  permissionHints: PermissionHint[];
  statusRules: JsonValue;
}

export interface RecordListItemVO {
  recordId: EntityId;
  recordNo?: string;
  moduleId: EntityId;
  title: string;
  recordStatus: RecordStatus;
  flowStatus?: FlowInstanceStatus;
  values: DynamicFieldValue[];
  availableActions: AvailableAction[];
  createdByName?: string;
  updatedAt?: IsoDateTimeString;
  recordVersion: number;
}

export interface RecordDetailVO {
  recordId: EntityId;
  recordStatus: RecordStatus;
  recordVersion: number;
  values: DynamicFieldValue[];
  fileRefs?: FileInfoVO[];
  flowSummary?: JsonValue;
  historySummary?: JsonValue;
  availableActions: AvailableAction[];
  fieldPermissions: FieldPermission[];
  auditFields?: JsonValue;
}

export interface RecordMutationResultVO {
  recordId: EntityId;
  recordStatus: RecordStatus;
  recordVersion: number;
  flowInstanceId?: EntityId;
  changedFields?: string[];
  idempotencyReplay?: boolean;
  availableActions: AvailableAction[];
}

export interface FlowTaskDetailVO {
  taskId: EntityId;
  taskVersion: number;
  instanceId: EntityId;
  recordId: EntityId;
  moduleId: EntityId;
  nodeId: EntityId;
  nodeName: string;
  recordSummary: JsonValue;
  formSchema: JsonValue;
  values: DynamicFieldValue[];
  history: JsonValue;
  diagram?: JsonValue;
  availableActions: AvailableAction[];
}

export interface FileInfoVO {
  fileId: EntityId;
  fileName: string;
  extension?: string;
  contentType: string;
  size: number;
  status: FileStatus;
  tempExpiresAt?: IsoDateTimeString;
  previewable: boolean;
  previewableReason?: string;
  downloadable: boolean;
  downloadableReason?: string;
  references?: JsonValue;
}

export interface ExportJobDetailVO {
  jobId: EntityId;
  moduleId: EntityId;
  templateId?: EntityId;
  status: ExportJobStatus;
  progress: number;
  filterSnapshot?: JsonValue;
  permissionSnapshot?: JsonValue;
  resultFileId?: EntityId;
  failureReason?: ExportFailureReason;
  retryable: boolean;
  retryCount?: number;
  maxRetryCount?: number;
  pollingIntervalMs?: number;
}

export interface ExportFailureReason {
  code: string;
  message: string;
  retryable: boolean;
  stackSummary?: string;
  failedAt?: IsoDateTimeString;
}

export interface OpenApiScope {
  scopeCode: string;
  moduleCode?: string;
  actions: string[];
  readableFieldCodes?: string[];
  writableFieldCodes?: string[];
  dataScope?: DataScopeRuleDTO;
  filePermissions?: string[];
  flowPermissions?: string[];
}

export interface OpenApiAccessLogVO {
  logId: EntityId;
  requestId: string;
  clientId: EntityId;
  accessKey: string;
  systemId: EntityId;
  tenantId?: EntityId;
  apiId: ApiId;
  method: HttpMethod;
  path: string;
  statusCode: number;
  errorCode?: string;
  signatureResult?: string;
  nonceResult?: string;
  idempotencyResult?: string;
  rateLimitResult?: string;
  scopeResult?: string;
  durationMs: number;
  createdAt: IsoDateTimeString;
}

export interface AuditLogDetailVO {
  logId: EntityId;
  requestId: string;
  traceId: string;
  source: string;
  apiId: ApiId;
  httpMethod: HttpMethod;
  path: string;
  operatorType?: string;
  operatorId?: EntityId;
  operatorName?: string;
  bizType?: string;
  bizId?: EntityId;
  beforeStatus?: string;
  afterStatus?: string;
  changedFields?: string[];
  beforeSnapshot?: JsonValue;
  afterSnapshot?: JsonValue;
  errorCode?: string;
  durationMs?: number;
  createdAt: IsoDateTimeString;
}

export type DynamicFieldType =
  | "TEXT"
  | "TEXTAREA"
  | "NUMBER"
  | "DECIMAL"
  | "DATE"
  | "DATETIME"
  | "SELECT"
  | "MULTI_SELECT"
  | "RADIO"
  | "CHECKBOX"
  | "DICT"
  | "BOOLEAN"
  | "ATTACHMENT"
  | "IMAGE"
  | "SERIAL"
  | "RELATION"
  | "SUB_TABLE"
  | "ADDRESS"
  | "TAG"
  | "JSON";

export type AccountStatus = "NORMAL" | "DISABLED" | "LOCKED";
export type SystemStatus = "DRAFT" | "ENABLED" | "DISABLED" | "ARCHIVED";
export type TenantStatus = "ENABLED" | "DISABLED";
export type AppStatus = "DRAFT" | "ENABLED" | "DISABLED" | "ARCHIVED";
export type ModuleStatus = "DRAFT" | "PUBLISHED" | "DISABLED" | "ARCHIVED";
export type FieldStatus = "DRAFT" | "ENABLED" | "DISABLED" | "DELETED";
export type VersionStatus = "DRAFT" | "PUBLISHED" | "DISCARDED";
export type RecordStatus =
  | "DRAFT"
  | "SUBMITTED"
  | "IN_APPROVAL"
  | "APPROVED"
  | "REJECTED"
  | "WITHDRAWN"
  | "ARCHIVED"
  | "DELETED";
export type FlowTemplateStatus = "DRAFT" | "PUBLISHED" | "DISABLED";
export type FlowInstanceStatus = "IN_APPROVAL" | "APPROVED" | "REJECTED" | "WITHDRAWN" | "TERMINATED";
export type FlowTaskStatus = "PENDING" | "DONE" | "CANCELED" | "TRANSFERRED" | "RETURNED";
export type FileStatus = "TEMP" | "REFERENCED" | "DELETED" | "EXPIRED";
export type ExportJobStatus = "QUEUED" | "PROCESSING" | "SUCCESS" | "FAILED" | "CANCELED";
export type OpenApiClientStatus = "DRAFT" | "ENABLED" | "DISABLED" | "EXPIRED";
