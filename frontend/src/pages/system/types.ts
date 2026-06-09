import type {
  ApiEndpointId,
  AvailableAction,
  DataScopeRuleDTO,
  EntityId,
  FieldPermission,
  IsoDateTimeString,
  JsonValue,
  PageQuery,
  PageResult,
  PermissionHint,
  SystemStatus,
  TenantStatus,
} from "../../api";
import type { PermissionDecision } from "../../stores";

export type DictScopeType = "SYSTEM" | "TENANT";
export type ManageStatus = "ENABLED" | "DISABLED";
export type DictStatus = "ENABLED" | "DISABLED" | "DELETED";
export type MemberStatus = "ENABLED" | "DISABLED";

export interface PageRequestState {
  loading: boolean;
  empty: boolean;
  errorMessage?: string;
  requestId?: string;
  retryable: boolean;
}

export interface PageActionState extends PermissionDecision {
  actionCode: string;
  apiId: ApiEndpointId;
  label: string;
}

export interface PageContextBlock {
  blocked: boolean;
  missing: string[];
  message?: string;
}

export interface SystemProfileVO {
  systemId: EntityId;
  name?: string;
  code?: string;
  systemName?: string;
  systemCode?: string;
  domain?: string;
  tenantMode: "SINGLE" | "MULTI";
  defaultTenantId?: EntityId;
  status: SystemStatus;
  version?: number;
  updatedAt?: IsoDateTimeString;
}

export interface SystemProfileSaveBO {
  name: string;
  code?: string;
  domain?: string;
  defaultTenantId?: EntityId;
  version?: number;
}

export interface TenantVO {
  tenantId: EntityId;
  tenantCode?: string;
  tenantName: string;
  status: TenantStatus;
  version?: number;
  createdAt?: IsoDateTimeString;
  updatedAt?: IsoDateTimeString;
}

export interface TenantSaveBO {
  tenantCode: string;
  tenantName: string;
}

export interface StatusChangeBO<TStatus extends string = ManageStatus> {
  targetStatus: TStatus;
  reason?: string;
  version?: number;
}

export interface TenantSwitchBO {
  tenantId: EntityId;
}

export interface MemberQuery extends PageQuery {
  deptId?: EntityId;
  roleId?: EntityId;
  status?: MemberStatus;
}

export interface MemberListVO {
  memberId: EntityId;
  accountId: EntityId;
  loginName: string;
  displayName: string;
  tenantIds: EntityId[];
  deptPath?: string;
  roles: string[];
  dataScopeSummary?: string;
  status: MemberStatus;
}

export interface MemberDetailVO extends MemberListVO {
  deptIds: EntityId[];
  postName?: string;
  fieldPermissions?: FieldPermission[];
  availableActions?: AvailableAction[];
  updatedAt?: IsoDateTimeString;
}

export interface MemberInviteBO {
  accountId?: EntityId;
  loginName?: string;
  tenantIds?: EntityId[];
  deptIds?: EntityId[];
  postName?: string;
  roleIds?: EntityId[];
}

export interface MemberUpdateBO {
  tenantIds?: EntityId[];
  deptIds?: EntityId[];
  postName?: string;
}

export interface MemberRoleAssignBO {
  roleIds: EntityId[];
}

export type MemberPageResult = PageResult<MemberListVO>;

export interface DepartmentNodeVO {
  deptId: EntityId;
  parentId?: EntityId;
  code?: string;
  name?: string;
  deptCode?: string;
  deptName?: string;
  sortOrder?: number;
  memberCount?: number;
  children?: DepartmentNodeVO[];
}

export interface DepartmentSaveBO {
  parentId?: EntityId;
  code: string;
  name: string;
  sortOrder?: number;
}

export interface RoleVO {
  roleId: EntityId;
  code: string;
  name: string;
  description?: string;
  status: ManageStatus;
  protected?: boolean;
  memberCount?: number;
  version?: number;
  updatedAt?: IsoDateTimeString;
}

export interface RoleSaveBO {
  code: string;
  name: string;
  description?: string;
}

export interface RolePermissionSaveBO {
  menuIds?: EntityId[];
  operationCodes?: string[];
  fieldPermissions?: FieldPermission[];
  dataScopes?: DataScopeRuleDTO[];
  explicitDeny?: string[];
}

export interface RolePermissionDetailVO extends RolePermissionSaveBO {
  roleId: EntityId;
  roleName?: string;
  availableActions?: AvailableAction[];
  permissionHints?: PermissionHint[];
  version?: number;
}

export interface PermissionCatalogVO {
  menus: JsonValue[];
  operations: string[];
  moduleFields: FieldPermission[];
  exports?: JsonValue[];
  flowActions?: string[];
  openApiScopes?: string[];
  dataScopeSchema: JsonValue;
}

export interface DictTypeQuery extends PageQuery {
  scopeType?: DictScopeType;
  status?: DictStatus;
  referenced?: boolean;
}

export interface DictTypeVO {
  dictTypeId: EntityId;
  systemId: EntityId;
  scopeType: DictScopeType;
  tenantId?: EntityId;
  code: string;
  name: string;
  description?: string;
  status: DictStatus;
  sortOrder?: number;
  systemBuiltIn: boolean;
  itemCount: number;
  enabledItemCount: number;
  referenced: boolean;
  cacheVersion: number;
  version: number;
  createdAt?: IsoDateTimeString;
  updatedAt?: IsoDateTimeString;
}

export interface DictTypeSaveBO {
  scopeType: DictScopeType;
  tenantId?: EntityId;
  code: string;
  name: string;
  description?: string;
  sortOrder?: number;
}

export interface DictTypeUpdateBO {
  name: string;
  description?: string;
  sortOrder?: number;
  version: number;
}

export interface DictItemQuery {
  treeMode?: boolean;
  parentId?: EntityId;
  status?: DictStatus;
}

export interface DictItemVO {
  dictItemId: EntityId;
  dictTypeId: EntityId;
  parentId?: EntityId;
  code: string;
  label: string;
  value: string;
  description?: string;
  status: DictStatus;
  sortOrder?: number;
  depthLevel: number;
  depthPath: string;
  leaf: boolean;
  systemBuiltIn: boolean;
  referenced: boolean;
  cacheVersion: number;
  version: number;
  children?: DictItemVO[];
  createdAt?: IsoDateTimeString;
  updatedAt?: IsoDateTimeString;
}

export interface DictItemSaveBO {
  parentId?: EntityId;
  code: string;
  label: string;
  value: string;
  description?: string;
  status?: ManageStatus;
  sortOrder?: number;
}

export interface DictItemUpdateBO {
  label: string;
  value: string;
  description?: string;
  sortOrder?: number;
  version: number;
}

export interface DictUsageVO {
  dictTypeId: EntityId;
  dictItemId?: EntityId;
  fieldUsages: Array<{
    moduleId: EntityId;
    moduleCode: string;
    fieldId: EntityId;
    fieldCode: string;
    publishedVersionId?: EntityId;
    status?: string;
  }>;
  recordUsageCount: number;
  enabledChildrenCount: number;
  canDisable: boolean;
  canDelete: boolean;
  blockingReasons: string[];
}

export interface DictCacheRefreshVO {
  dictTypeId: EntityId;
  cacheVersion: number;
  refreshMode: string;
  refreshedAt: IsoDateTimeString;
  affectedKeys: string[];
}

export interface DictWriteResult<TData> {
  entity: TData;
  cacheRefresh?: DictCacheRefreshVO;
}
