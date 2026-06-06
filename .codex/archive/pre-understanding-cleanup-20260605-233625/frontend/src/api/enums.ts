export const API_SUCCESS_CODE = 'SUCCESS';

export const ErrorCode = {
  Success: 'SUCCESS',
  SystemError: 'SYSTEM_ERROR',
  PlatParamRequired: 'PLAT_PARAM_REQUIRED',
  PlatDataNotFound: 'PLAT_DATA_NOT_FOUND',
  PlatStatusInvalid: 'PLAT_STATUS_INVALID',
  PlatPermissionTypeInvalid: 'PLAT_PERMISSION_TYPE_INVALID',
  PlatLoginFailed: 'PLAT_LOGIN_FAILED',
  ModuleParamRequired: 'MODULE_PARAM_REQUIRED',
  ModuleDataNotFound: 'MODULE_DATA_NOT_FOUND',
  ModuleFieldInvalid: 'MODULE_FIELD_INVALID',
  ModuleRecordNoDuplicate: 'MODULE_RECORD_NO_DUPLICATE',
  ModuleStatusInvalid: 'MODULE_STATUS_INVALID',
  FlowParamRequired: 'FLOW_PARAM_REQUIRED',
  FlowDataNotFound: 'FLOW_DATA_NOT_FOUND',
  FlowStatusInvalid: 'FLOW_STATUS_INVALID',
  FlowActionTypeInvalid: 'FLOW_ACTION_TYPE_INVALID',
  FlowTaskAssigneeInvalid: 'FLOW_TASK_ASSIGNEE_INVALID',
  UploadParamRequired: 'UPLOAD_PARAM_REQUIRED',
  UploadDataNotFound: 'UPLOAD_DATA_NOT_FOUND',
  UploadStatusInvalid: 'UPLOAD_STATUS_INVALID',
  AppParamRequired: 'APP_PARAM_REQUIRED',
  AppDataNotFound: 'APP_DATA_NOT_FOUND',
  AppStatusInvalid: 'APP_STATUS_INVALID'
} as const;

export type ErrorCodeValue = (typeof ErrorCode)[keyof typeof ErrorCode];

export const CommonStatus = {
  Enabled: 'ENABLED',
  Disabled: 'DISABLED',
  Locked: 'LOCKED',
  Draft: 'DRAFT',
  Published: 'PUBLISHED',
  Active: 'ACTIVE',
  Running: 'RUNNING',
  Completed: 'COMPLETED',
  Approved: 'APPROVED',
  Deleted: 'DELETED',
  Pending: 'PENDING',
  Done: 'DONE',
  Rejected: 'REJECTED',
  Canceled: 'CANCELED',
  Transferred: 'TRANSFERRED',
  Expired: 'EXPIRED',
  Temp: 'TEMP',
  Referenced: 'REFERENCED'
} as const;

export type CommonStatusValue = (typeof CommonStatus)[keyof typeof CommonStatus];

export const ModuleStatus = {
  Draft: CommonStatus.Draft,
  Published: CommonStatus.Published,
  Disabled: CommonStatus.Disabled
} as const;

export type ModuleStatusValue = (typeof ModuleStatus)[keyof typeof ModuleStatus];

export const statusText: Record<string, string> = {
  ENABLED: '启用',
  DISABLED: '停用',
  LOCKED: '锁定',
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  ACTIVE: '生效中',
  RUNNING: '运行中',
  COMPLETED: '已完成',
  APPROVED: '已通过',
  DELETED: '已删除',
  PENDING: '待处理',
  DONE: '已处理',
  REJECTED: '已退回',
  CANCELED: '已取消',
  TRANSFERRED: '已转交',
  EXPIRED: '过期',
  TEMP: '临时',
  REFERENCED: '已引用'
};

export const DataScopeType = {
  Owner: 'OWNER',
  Dept: 'DEPT',
  DeptTree: 'DEPT_TREE',
  Role: 'ROLE',
  All: 'ALL'
} as const;

export const FieldType = {
  Text: 'TEXT',
  Number: 'NUMBER',
  Date: 'DATE',
  Select: 'SELECT',
  MultiSelect: 'MULTI_SELECT',
  Boolean: 'BOOLEAN',
  File: 'FILE'
} as const;

export const PageType = {
  List: 'LIST',
  Form: 'FORM',
  Detail: 'DETAIL'
} as const;

export const FlowActionType = {
  Approve: 'APPROVE',
  Reject: 'REJECT',
  Transfer: 'TRANSFER',
  Cancel: 'CANCEL'
} as const;

export const UploadJobType = {
  Import: 'IMPORT',
  Export: 'EXPORT'
} as const;

export const PermissionType = {
  Menu: 'MENU',
  Button: 'BUTTON',
  Api: 'API',
  Field: 'FIELD',
  DataScope: 'DATA_SCOPE'
} as const;
