import { httpGet, httpPost } from '@/api/http'
import type { ApiResult } from '@/api/http'

// ---- Dict ----
export type ModuleDictRow = { id: number; dictCode?: string; dictName?: string; status?: number; remark?: string }
export type ModuleDictItemRow = {
  id: number
  dictId?: number
  itemValue?: string
  itemLabel?: string
  sortNo?: number
  status?: number
}

export function listDictsByApp(appId: number): Promise<ApiResult<ModuleDictRow[]>> {
  return httpGet<ModuleDictRow[]>(`/v1/system/module/dicts/apps/${appId}`)
}

export function upsertDict(
  appId: number,
  cmd: { id?: number | null; dictCode: string; dictName: string; status?: number; remark?: string | null }
): Promise<ApiResult<any>> {
  return httpPost(`/v1/system/module/dicts/apps/${appId}/upsert`, {
    id: cmd.id ?? null,
    dictCode: cmd.dictCode,
    dictName: cmd.dictName,
    status: cmd.status ?? 1,
    remark: cmd.remark ?? null
  })
}

export function listDictItems(dictId: number): Promise<ApiResult<ModuleDictItemRow[]>> {
  return httpGet<ModuleDictItemRow[]>(`/v1/system/module/dicts/${dictId}/items`)
}

export function upsertDictItem(
  dictId: number,
  cmd: { id?: number | null; itemValue: string; itemLabel: string; sortNo?: number; status?: number }
): Promise<ApiResult<any>> {
  return httpPost(`/v1/system/module/dicts/${dictId}/items/upsert`, {
    id: cmd.id ?? null,
    itemValue: cmd.itemValue,
    itemLabel: cmd.itemLabel,
    sortNo: cmd.sortNo ?? 0,
    status: cmd.status ?? 1
  })
}

// ---- List Views ----
export type ModuleListViewRow = { id: number; viewCode?: string; viewName?: string; defaultFlag?: number; status?: number }
export type ModuleListViewColRow = {
  id: number
  viewId?: number
  fieldId?: number
  colTitle?: string
  width?: number
  sortNo?: number
  visibleFlag?: number
}
export type ModuleFilterTplRow = { id: number; tplCode?: string; tplName?: string; menuId?: number; status?: number }

export function listViewsByModel(modelId: number): Promise<ApiResult<ModuleListViewRow[]>> {
  return httpGet<ModuleListViewRow[]>(`/v1/system/module/list-views/models/${modelId}`)
}

export function upsertListView(cmd: {
  id?: number | null
  appId: number
  modelId: number
  platId?: number | null
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

export function listViewCols(viewId: number): Promise<ApiResult<ModuleListViewColRow[]>> {
  return httpGet<ModuleListViewColRow[]>(`/v1/system/module/list-views/${viewId}/cols`)
}

export function upsertViewCol(cmd: {
  id?: number | null
  viewId: number
  fieldId: number
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

export function listFilterTpls(modelId: number): Promise<ApiResult<ModuleFilterTplRow[]>> {
  return httpGet<ModuleFilterTplRow[]>(`/v1/system/module/list-views/models/${modelId}/filter-tpls`)
}

export function upsertFilterTpl(cmd: {
  id?: number | null
  appId: number
  modelId: number
  menuId?: number | null
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

// ---- Export ----
export type ModuleExportTplRow = {
  id: number | string
  tplCode?: string
  tplName?: string
  fileType?: string
  status?: number
}
export type ModuleExportTplFieldRow = {
  id: number | string
  tplId?: number | string
  fieldId?: number | string
  colTitle?: string
  sortNo?: number
  formatJson?: string
}

export function listExportTplsByModel(modelId: number): Promise<ApiResult<ModuleExportTplRow[]>> {
  return httpGet<ModuleExportTplRow[]>(`/v1/system/module/exports/models/${modelId}/tpls`)
}

export function upsertExportTpl(cmd: {
  id?: number | null
  appId: number
  modelId: number
  menuId?: number | null
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

export function deleteExportTpl(ids: Array<string | number>): Promise<ApiResult<any>> {
  return httpPost('/v1/system/module/exports/tpls/delete', { ids })
}

export function listExportTplFields(tplId: number): Promise<ApiResult<ModuleExportTplFieldRow[]>> {
  return httpGet<ModuleExportTplFieldRow[]>(`/v1/system/module/exports/tpls/${tplId}/fields`)
}

export function upsertExportTplField(cmd: {
  id?: string | number | null
  tplId: number
  fieldId: number
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

export function deleteExportTplField(ids: Array<string | number>): Promise<ApiResult<any>> {
  return httpPost('/v1/system/module/exports/fields/delete', { ids })
}

export type ModuleExportJobRow = {
  id: number | string
  status?: number
  tplId?: number | string
  modelId?: number | string
  resultFileId?: number | string
  errorMsg?: string
}

export function pageExportJobs(params: {
  page: number
  size: number
  tplId?: string
  modelId?: string
  status?: string
}): Promise<ApiResult<any>> {
  const q: string[] = []
  if (params.tplId?.trim()) q.push(`tplId=${encodeURIComponent(params.tplId.trim())}`)
  if (params.modelId?.trim()) q.push(`modelId=${encodeURIComponent(params.modelId.trim())}`)
  if (params.status?.trim()) q.push(`status=${encodeURIComponent(params.status.trim())}`)
  const ext = q.length ? '&' + q.join('&') : ''
  return httpGet<any>(`/v1/system/module/export-jobs/page?page=${params.page}&size=${params.size}${ext}`)
}

export function getExportJobDetail(jobId: number): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/module/export-jobs/${jobId}`)
}

export function createExportJob(tplId: string | number, query: any): Promise<ApiResult<any>> {
  return httpPost<any>(`/v1/system/module/export-jobs/tpls/${tplId}`, query)
}

// ---- RBAC ----
export type ModuleRbacRoleRow = { id: number; roleCode?: string; roleName?: string; status?: number }
export type ModuleRbacMemberRow = { id: number; platId?: number; roleId?: number; status?: number }
export type ModuleRbacMenuRow = {
  id: number
  parentId?: number
  menuName?: string
  permKey?: string
  apiPattern?: string
  pageId?: number
  sortNo?: number
  visibleFlag?: number
}

export function listRbacRoles(appId: number): Promise<ApiResult<ModuleRbacRoleRow[]>> {
  return httpGet<ModuleRbacRoleRow[]>(`/v1/system/module/rbac/apps/${appId}/roles`)
}

export function upsertRbacRole(appId: number, cmd: { id?: number | null; roleCode: string; roleName: string; status?: number }): Promise<ApiResult<any>> {
  return httpPost(`/v1/system/module/rbac/apps/${appId}/roles/upsert`, {
    id: cmd.id ?? null,
    roleCode: cmd.roleCode,
    roleName: cmd.roleName,
    status: cmd.status ?? 1
  })
}

export function listRbacMenus(appId: number): Promise<ApiResult<ModuleRbacMenuRow[]>> {
  return httpGet<ModuleRbacMenuRow[]>(`/v1/system/module/rbac/apps/${appId}/menus`)
}

export function upsertRbacMenu(
  appId: number,
  cmd: {
    id?: number | null
    parentId: number
    menuName: string
    pageId?: number | null
    sortNo?: number
    visibleFlag?: number
    permKey?: string | null
    apiPattern?: string | null
  }
): Promise<ApiResult<any>> {
  return httpPost(`/v1/system/module/rbac/apps/${appId}/menus/upsert`, {
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

export function listRbacMembers(appId: number): Promise<ApiResult<ModuleRbacMemberRow[]>> {
  return httpGet<ModuleRbacMemberRow[]>(`/v1/system/module/rbac/apps/${appId}/members`)
}

export function assignRbacMemberRole(cmd: { appId: number; memberPlatId: number; roleId: number }): Promise<ApiResult<any>> {
  return httpPost('/v1/system/module/rbac/members/assign-role', cmd)
}

export function listRoleMenuPerms(roleId: number): Promise<ApiResult<any[]>> {
  return httpGet<any[]>(`/v1/system/module/rbac/roles/${roleId}/menu-perms`)
}

export function setRoleMenuPerms(cmd: { roleId: number; menuIds: number[]; permLevel: number }): Promise<ApiResult<any>> {
  return httpPost('/v1/system/module/rbac/roles/menu-perms/set', cmd)
}

export function permPreview(uri: string): Promise<ApiResult<Record<string, any>>> {
  return httpGet<Record<string, any>>(`/v1/system/module/auth/perm-preview?uri=${encodeURIComponent(uri)}`)
}

