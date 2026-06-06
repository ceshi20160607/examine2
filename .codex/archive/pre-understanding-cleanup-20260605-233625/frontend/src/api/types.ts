import type { CommonStatusValue } from './enums';

export type Id = number;
export type ByteFlag = 0 | 1;

export interface ApiResult<T> {
  code: string;
  message: string;
  data: T;
}

export interface RequestContext {
  accountId?: Id;
  tenantId?: Id;
  systemId?: Id;
  appId?: Id;
  moduleId?: Id;
}

export interface PlatformLoginDTO {
  username: string;
  password: string;
}

export interface PlatformSystemSaveBO {
  systemCode: string;
  systemName: string;
  description?: string;
}

export interface PlatformTenantSaveBO {
  tenantCode: string;
  tenantName: string;
  adminAccountId?: Id;
  expireAt?: string;
  configJson?: string;
}

export interface PlatformAccountSaveBO {
  username: string;
  displayName: string;
  mobile?: string;
  email?: string;
  password: string;
}

export interface PlatformRoleSaveBO {
  tenantId: Id;
  systemId: Id;
  appId?: Id;
  roleCode: string;
  roleName: string;
  roleType: string;
}

export interface PlatformPermissionSaveBO {
  tenantId: Id;
  systemId: Id;
  appId?: Id;
  moduleId?: Id;
  permissionCode: string;
  permissionName: string;
  permissionType: string;
  resourcePath?: string;
}

export interface RolePermissionAssignBO {
  roleId: Id;
  permissionIds: Id[];
}

export interface AccountRoleAssignBO {
  accountId: Id;
  tenantId: Id;
  systemId: Id;
  roleIds: Id[];
}

export interface ModuleModelSaveBO {
  tenantId: Id;
  systemId: Id;
  appId: Id;
  moduleCode: string;
  moduleName: string;
  dataScopeType: string;
  flowEnabled: ByteFlag;
  importEnabled: ByteFlag;
  exportEnabled: ByteFlag;
}

export interface ModuleFieldOptionBO {
  optionValue: string;
  optionLabel: string;
  sortOrder?: number;
}

export interface ModuleFieldSaveBO {
  moduleId: Id;
  fieldCode: string;
  fieldName: string;
  fieldType: string;
  requiredFlag: ByteFlag;
  uniqueFlag: ByteFlag;
  listVisible: ByteFlag;
  searchable: ByteFlag;
  editable: ByteFlag;
  defaultValue?: string;
  validationJson?: string;
  sortOrder?: number;
  options?: ModuleFieldOptionBO[];
}

export interface ModulePageSaveBO {
  moduleId: Id;
  pageCode: string;
  pageName: string;
  pageType: string;
  layoutJson: string;
  buttonJson?: string;
}

export interface ModuleRecordSaveBO {
  moduleId: Id;
  recordNo: string;
  ownerAccountId?: Id;
  deptId?: Id;
  values: Record<string, unknown>;
}

export interface FlowTemplateSaveBO {
  tenantId: Id;
  appId: Id;
  moduleId: Id;
  templateCode: string;
  templateName: string;
}

export interface FlowTemplatePublishBO {
  templateId: Id;
  versionNo: string;
  graphJson: string;
}

export interface FlowStartBO {
  tenantId: Id;
  moduleId: Id;
  recordId: Id;
  templateId: Id;
  templateVersionId: Id;
  assigneeId: Id;
  taskName: string;
}

export interface FlowTaskHandleBO {
  actionType: string;
  commentText?: string;
  transferTo?: Id;
}

export interface UploadFileCreateBO {
  tenantId: Id;
  storageConfigId: Id;
  originalName: string;
  fileExt: string;
  mimeType: string;
  fileSize: number;
  storagePath: string;
  sha256?: string;
}

export interface AttachmentCreateBO {
  fileId: Id;
  bizType: string;
  bizId: Id;
  fieldCode?: string;
}

export interface UploadImportExportJobBO {
  tenantId: Id;
  moduleId: Id;
  jobType: string;
  sourceFileId?: Id;
  requestJson?: string;
}

export interface AppApplicationSaveBO {
  tenantId: Id;
  systemId: Id;
  appCode: string;
  appName: string;
  visibleScope: string;
  description?: string;
}

export interface AppPublishBO {
  appId: Id;
  versionNo: string;
  versionName: string;
  snapshotJson: string;
}

export interface OpenApiClientSaveBO {
  tenantId: Id;
  systemId: Id;
  clientCode: string;
  clientName: string;
  rateLimitPerMinute?: number;
  expiredAt?: string;
}

export interface OpenApiCredentialCreateBO {
  clientId: Id;
  accessKey: string;
  secret: string;
  signAlgorithm: string;
}

export interface OpenApiScopeSaveBO {
  clientId: Id;
  appId: Id;
  moduleId?: Id;
  scopeCode: string;
  actions: string;
}

export interface OpenApiIpWhitelistSaveBO {
  clientId: Id;
  ipValue: string;
}

export interface OpenApiIdempotentSaveBO {
  clientId: Id;
  idempotentKey: string;
  requestHash: string;
  responseHash?: string;
  expiredAt: string;
}

export interface PlatformManageVO {
  id: Id;
  code?: string;
  name?: string;
  status?: CommonStatusValue | string;
  tenantId?: Id;
  systemId?: Id;
  appId?: Id;
  moduleId?: Id;
  type?: string;
  resourcePath?: string;
  mobile?: string;
  email?: string;
  roleIds?: Id[];
  permissionIds?: Id[];
  createdAt?: string;
  updatedAt?: string;
}

export interface ModuleManageVO {
  id: Id;
  tenantId?: Id;
  systemId?: Id;
  appId?: Id;
  moduleId?: Id;
  code?: string;
  name?: string;
  type?: string;
  status?: CommonStatusValue | string;
  configJson?: string;
  values?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface FlowManageVO {
  id: Id;
  tenantId?: Id;
  appId?: Id;
  moduleId?: Id;
  recordId?: Id;
  code?: string;
  name?: string;
  status?: CommonStatusValue | string;
  templateId?: Id;
  templateVersionId?: Id;
  assigneeId?: Id;
  graphJson?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface UploadManageVO {
  id: Id;
  tenantId?: Id;
  fileId?: Id;
  moduleId?: Id;
  name?: string;
  type?: string;
  size?: number;
  storagePath?: string;
  status?: CommonStatusValue | string;
  bizType?: string;
  bizId?: Id;
  failureReason?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface AppManageVO {
  id: Id;
  tenantId?: Id;
  systemId?: Id;
  clientId?: Id;
  appId?: Id;
  moduleId?: Id;
  code?: string;
  name?: string;
  status?: CommonStatusValue | string;
  type?: string;
  versionNo?: string;
  value?: string;
  detail?: string;
  expiredAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export type ApiList<T> = T[];
