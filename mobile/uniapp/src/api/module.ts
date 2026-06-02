import { buildApiUrl, httpGet, httpPost } from '@/api/http'
import type { ApiResult } from '@/api/http'
import { idToString, type IdValue } from '@/utils/id'

// ---- Dict ----
export type ModuleDictRow = { id: string; dictCode?: string; dictName?: string; status?: number; remark?: string }
export type ModuleDictItemRow = {
  id: string
  dictId?: string
  itemValue?: string
  itemLabel?: string
  sortNo?: number
  status?: number
}

export function listDictsByApp(appId: IdValue): Promise<ApiResult<ModuleDictRow[]>> {
  return httpGet<ModuleDictRow[]>(`/v1/system/module/dicts/apps/${pathId(appId)}`)
}

export function upsertDict(
  appId: IdValue,
  cmd: { id?: IdValue | null; dictCode: string; dictName: string; status?: number; remark?: string | null }
): Promise<ApiResult<any>> {
  return httpPost(`/v1/system/module/dicts/apps/${pathId(appId)}/upsert`, {
    id: cmd.id ?? null,
    dictCode: cmd.dictCode,
    dictName: cmd.dictName,
    status: cmd.status ?? 1,
    remark: cmd.remark ?? null
  })
}

export function deleteDicts(ids: IdValue[]): Promise<ApiResult<void>> {
  return httpPost<void>('/v1/system/module/dicts/delete', { ids })
}

export function listDictItems(dictId: IdValue): Promise<ApiResult<ModuleDictItemRow[]>> {
  return httpGet<ModuleDictItemRow[]>(`/v1/system/module/dicts/${pathId(dictId)}/items`)
}

export function upsertDictItem(
  dictId: IdValue,
  cmd: { id?: IdValue | null; itemValue: string; itemLabel: string; sortNo?: number; status?: number }
): Promise<ApiResult<any>> {
  return httpPost(`/v1/system/module/dicts/${pathId(dictId)}/items/upsert`, {
    id: cmd.id ?? null,
    itemValue: cmd.itemValue,
    itemLabel: cmd.itemLabel,
    sortNo: cmd.sortNo ?? 0,
    status: cmd.status ?? 1
  })
}

export function deleteDictItems(ids: IdValue[]): Promise<ApiResult<void>> {
  return httpPost<void>('/v1/system/module/dicts/items/delete', { ids })
}

// ---- List Views ----
export type ModuleListViewRow = { id: string; viewCode?: string; viewName?: string; defaultFlag?: number; status?: number }
export type ModuleListViewColRow = {
  id: string
  viewId?: string
  fieldId?: string
  colTitle?: string
  width?: number
  sortNo?: number
  visibleFlag?: number
  fixedType?: string | null
  formatJson?: string | null
}
export type ModuleFilterTplRow = { id: string; tplCode?: string; tplName?: string; menuId?: string; status?: number }
export type ModuleFilterFieldRow = {
  id: string
  tplId?: string
  fieldId?: string
  opCode?: string
  defaultValue?: string | null
  requiredFlag?: number
  sortNo?: number
}

export function listViewsByModel(modelId: IdValue): Promise<ApiResult<ModuleListViewRow[]>> {
  return httpGet<ModuleListViewRow[]>(`/v1/system/module/list-views/models/${pathId(modelId)}`)
}

export function upsertListView(cmd: {
  id?: IdValue | null
  appId: IdValue
  modelId: IdValue
  platId?: IdValue | null
  viewCode: string
  viewName: string
  defaultFlag?: number
  status?: number
}): Promise<ApiResult<any>> {
  return httpPost('/v1/system/module/list-views/upsert', {
    id: cmd.id ?? null,
    appId: cmd.appId,
    modelId: cmd.modelId,
    platId: cmd.platId ?? null,
    viewCode: cmd.viewCode,
    viewName: cmd.viewName,
    defaultFlag: cmd.defaultFlag ?? 0,
    status: cmd.status ?? 1
  })
}

export function deleteListViews(ids: IdValue[]): Promise<ApiResult<void>> {
  return httpPost<void>('/v1/system/module/list-views/delete', { ids })
}

export function listViewCols(viewId: IdValue): Promise<ApiResult<ModuleListViewColRow[]>> {
  return httpGet<ModuleListViewColRow[]>(`/v1/system/module/list-views/${pathId(viewId)}/cols`)
}

export function upsertViewCol(cmd: {
  id?: IdValue | null
  viewId: IdValue
  fieldId: IdValue
  colTitle: string
  width?: number | null
  sortNo?: number
  visibleFlag?: number
  fixedType?: any
  formatJson?: any
}): Promise<ApiResult<any>> {
  return httpPost('/v1/system/module/list-views/cols/upsert', {
    id: cmd.id ?? null,
    viewId: cmd.viewId,
    fieldId: cmd.fieldId,
    colTitle: cmd.colTitle,
    width: cmd.width ?? null,
    sortNo: cmd.sortNo ?? 0,
    visibleFlag: cmd.visibleFlag ?? 1,
    fixedType: cmd.fixedType ?? null,
    formatJson: cmd.formatJson ?? null
  })
}

export function deleteViewCols(ids: IdValue[]): Promise<ApiResult<void>> {
  return httpPost<void>('/v1/system/module/list-views/cols/delete', { ids })
}

export function listFilterTpls(modelId: IdValue): Promise<ApiResult<ModuleFilterTplRow[]>> {
  return httpGet<ModuleFilterTplRow[]>(`/v1/system/module/list-views/models/${pathId(modelId)}/filter-tpls`)
}

export function upsertFilterTpl(cmd: {
  id?: IdValue | null
  appId: IdValue
  modelId: IdValue
  menuId?: IdValue | null
  tplCode: string
  tplName: string
  status?: number
}): Promise<ApiResult<any>> {
  return httpPost('/v1/system/module/list-views/filter-tpls/upsert', {
    id: cmd.id ?? null,
    appId: cmd.appId,
    modelId: cmd.modelId,
    menuId: cmd.menuId ?? null,
    tplCode: cmd.tplCode,
    tplName: cmd.tplName,
    status: cmd.status ?? 1
  })
}

export function deleteFilterTpls(ids: IdValue[]): Promise<ApiResult<void>> {
  return httpPost<void>('/v1/system/module/list-views/filter-tpls/delete', { ids })
}

export function listFilterFields(tplId: IdValue): Promise<ApiResult<ModuleFilterFieldRow[]>> {
  return httpGet<ModuleFilterFieldRow[]>(`/v1/system/module/list-views/filter-tpls/${pathId(tplId)}/fields`)
}

export function upsertFilterField(cmd: {
  id?: IdValue | null
  tplId: IdValue
  fieldId: IdValue
  opCode?: string
  defaultValue?: string | null
  requiredFlag?: number
  sortNo?: number
}): Promise<ApiResult<any>> {
  return httpPost('/v1/system/module/list-views/filter-fields/upsert', {
    id: cmd.id ?? null,
    tplId: cmd.tplId,
    fieldId: cmd.fieldId,
    opCode: cmd.opCode ?? 'eq',
    defaultValue: cmd.defaultValue ?? null,
    requiredFlag: cmd.requiredFlag ?? 0,
    sortNo: cmd.sortNo ?? 0
  })
}

export function deleteFilterFields(ids: IdValue[]): Promise<ApiResult<void>> {
  return httpPost<void>('/v1/system/module/list-views/filter-fields/delete', { ids })
}

// ---- Export ----
export type ModuleExportTplRow = {
  id: IdValue
  tplCode?: string
  tplName?: string
  fileType?: string
  status?: number
}
export type ModuleExportTplFieldRow = {
  id: IdValue
  tplId?: IdValue
  fieldId?: IdValue
  colTitle?: string
  sortNo?: number
  formatJson?: string
}

export function listExportTplsByModel(modelId: IdValue): Promise<ApiResult<ModuleExportTplRow[]>> {
  return httpGet<ModuleExportTplRow[]>(`/v1/system/module/exports/models/${pathId(modelId)}/tpls`)
}

export function upsertExportTpl(cmd: {
  id?: IdValue | null
  appId: IdValue
  modelId: IdValue
  menuId?: IdValue | null
  tplCode: string
  tplName: string
  fileType?: string
  status?: number
}): Promise<ApiResult<any>> {
  return httpPost('/v1/system/module/exports/tpls/upsert', {
    id: cmd.id ?? null,
    appId: cmd.appId,
    modelId: cmd.modelId,
    menuId: cmd.menuId ?? null,
    tplCode: cmd.tplCode,
    tplName: cmd.tplName,
    fileType: cmd.fileType ?? 'csv',
    status: cmd.status ?? 1
  })
}

export function deleteExportTpl(ids: IdValue[]): Promise<ApiResult<any>> {
  return httpPost('/v1/system/module/exports/tpls/delete', { ids })
}

export function listExportTplFields(tplId: IdValue): Promise<ApiResult<ModuleExportTplFieldRow[]>> {
  return httpGet<ModuleExportTplFieldRow[]>(`/v1/system/module/exports/tpls/${pathId(tplId)}/fields`)
}

export function upsertExportTplField(cmd: {
  id?: IdValue | null
  tplId: IdValue
  fieldId: IdValue
  colTitle: string
  sortNo?: number
  formatJson?: any
}): Promise<ApiResult<any>> {
  return httpPost('/v1/system/module/exports/fields/upsert', {
    id: cmd.id ?? null,
    tplId: cmd.tplId,
    fieldId: cmd.fieldId,
    colTitle: cmd.colTitle,
    sortNo: cmd.sortNo ?? 0,
    formatJson: cmd.formatJson ?? null
  })
}

export function deleteExportTplField(ids: IdValue[]): Promise<ApiResult<any>> {
  return httpPost('/v1/system/module/exports/fields/delete', { ids })
}

export type ModuleExportJobRow = {
  id: IdValue
  status?: number
  tplId?: IdValue
  modelId?: IdValue
  resultFileId?: IdValue
  errorMsg?: string
}

export function pageExportJobs(params: {
  page: number
  size: number
  tplId?: string
  modelId?: string
  status?: string
}): Promise<ApiResult<any>> {
  const qs: string[] = []
  if (params.tplId?.trim()) qs.push(`tplId=${encodeURIComponent(params.tplId.trim())}`)
  if (params.modelId?.trim()) qs.push(`modelId=${encodeURIComponent(params.modelId.trim())}`)
  if (params.status?.trim()) qs.push(`status=${encodeURIComponent(params.status.trim())}`)
  const ext = qs.length ? '&' + qs.join('&') : ''
  return httpGet<any>(`/v1/system/module/export-jobs/page?page=${q(params.page)}&size=${q(params.size)}${ext}`)
}

export function getExportJobDetail(jobId: IdValue): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/module/export-jobs/${pathId(jobId)}`)
}

export function createExportJob(tplId: IdValue, query: any): Promise<ApiResult<any>> {
  return httpPost<any>(`/v1/system/module/export-jobs/tpls/${pathId(tplId)}`, query)
}

export function buildExportTplUrl(tplId: IdValue, limit = 200, fileType?: string): string {
  // for uni.downloadFile
  const suffix = fileType === 'csv' || fileType === 'xlsx' ? `/${fileType}` : ''
  return buildApiUrl(`/v1/system/module/exports/tpls/${pathId(tplId)}/export${suffix}?limit=${q(limit)}`)
}

export function buildExportTplCsvUrl(tplId: IdValue, limit = 200): string {
  return buildExportTplUrl(tplId, limit, 'csv')
}

// ---- RBAC ----
export type ModuleRbacRoleRow = { id: string; roleCode?: string; roleName?: string; status?: number; dataScope?: number }
export type ModuleRbacMemberRow = { id: string; platId?: string; roleId?: string; status?: number }
export type ModuleRbacMenuRow = {
  id: string
  parentId?: IdValue
  menuName?: string
  permKey?: string
  apiPattern?: string
  pageId?: IdValue
  sortNo?: number
  visibleFlag?: number
}

export function listRbacRoles(appId: IdValue): Promise<ApiResult<ModuleRbacRoleRow[]>> {
  return httpGet<ModuleRbacRoleRow[]>(`/v1/system/module/rbac/apps/${pathId(appId)}/roles`)
}

export function upsertRbacRole(
  appId: IdValue,
  cmd: { id?: IdValue | null; roleCode: string; roleName: string; status?: number; dataScope?: number }
): Promise<ApiResult<any>> {
  return httpPost(`/v1/system/module/rbac/apps/${pathId(appId)}/roles/upsert`, {
    id: cmd.id ?? null,
    roleCode: cmd.roleCode,
    roleName: cmd.roleName,
    status: cmd.status ?? 1,
    dataScope: cmd.dataScope ?? 1
  })
}

export function searchRbacAccounts(keyword: string): Promise<ApiResult<Array<{ platId: string; username?: string; text: string }>>> {
  return httpGet(`/v1/system/module/rbac/account-search?keyword=${encodeURIComponent(keyword)}`)
}

export function listRbacMenus(appId: IdValue): Promise<ApiResult<ModuleRbacMenuRow[]>> {
  return httpGet<ModuleRbacMenuRow[]>(`/v1/system/module/rbac/apps/${pathId(appId)}/menus`)
}

/** 运行时：当前用户可见菜单（已按角色过滤） */
export function listRuntimeMenus(appId: IdValue): Promise<ApiResult<ModuleRbacMenuRow[]>> {
  return httpGet<ModuleRbacMenuRow[]>(`/v1/system/module/rbac/apps/${pathId(appId)}/runtime-menus`)
}

export function upsertRbacMenu(
  appId: IdValue,
  cmd: {
    id?: IdValue | null
    parentId: IdValue
    menuName: string
    pageId?: IdValue | null
    sortNo?: number
    visibleFlag?: number
    permKey?: string | null
    apiPattern?: string | null
  }
): Promise<ApiResult<any>> {
  return httpPost(`/v1/system/module/rbac/apps/${pathId(appId)}/menus/upsert`, {
    id: cmd.id ?? null,
    parentId: cmd.parentId,
    menuName: cmd.menuName,
    pageId: cmd.pageId ?? null,
    sortNo: cmd.sortNo ?? 0,
    visibleFlag: cmd.visibleFlag ?? 1,
    permKey: cmd.permKey ?? null,
    apiPattern: cmd.apiPattern ?? null
  })
}

export function listRbacMembers(appId: IdValue): Promise<ApiResult<ModuleRbacMemberRow[]>> {
  return httpGet<ModuleRbacMemberRow[]>(`/v1/system/module/rbac/apps/${pathId(appId)}/members`)
}

export function assignRbacMemberRole(cmd: {
  appId: IdValue
  memberPlatId: IdValue
  roleId: IdValue
  deptId?: IdValue | null
}): Promise<ApiResult<any>> {
  return httpPost('/v1/system/module/rbac/members/assign-role', cmd)
}

export function listRoleMenuPerms(roleId: IdValue): Promise<ApiResult<any[]>> {
  return httpGet<any[]>(`/v1/system/module/rbac/roles/${pathId(roleId)}/menu-perms`)
}

export function setRoleMenuPerms(cmd: { roleId: IdValue; menuIds: IdValue[]; permLevel: number }): Promise<ApiResult<any>> {
  return httpPost('/v1/system/module/rbac/roles/menu-perms/set', cmd)
}

export function listRolePagePerms(roleId: IdValue): Promise<ApiResult<any[]>> {
  return httpGet<any[]>(`/v1/system/module/rbac/roles/${pathId(roleId)}/page-perms`)
}

export function setRolePagePerms(cmd: { roleId: IdValue; pageIds: IdValue[]; permLevel: number }): Promise<ApiResult<any>> {
  return httpPost('/v1/system/module/rbac/roles/page-perms/set', cmd)
}

export function permPreview(uri: string): Promise<ApiResult<Record<string, any>>> {
  return httpGet<Record<string, any>>(`/v1/system/auth/perm-preview?uri=${encodeURIComponent(uri)}`)
}

export type ModulePermissionInfo = {
  permKeys?: string[]
  ownerWildcard?: boolean
}

export function listModulePermissions(): Promise<ApiResult<ModulePermissionInfo>> {
  return httpGet<ModulePermissionInfo>('/v1/system/auth/permissions')
}

function pathId(value: IdValue): string {
  return encodeURIComponent(idToString(value))
}

function q(value: string | number): string {
  return encodeURIComponent(String(value))
}

