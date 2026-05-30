import { httpGet, httpPost } from '@/api/http'
import type { ApiResult } from '@/api/http'
import { idToString, type IdValue } from '@/utils/id'

export type ModuleDept = {
  id: string
  appId?: string
  parentId?: string
  /** 祖先至本级 ID 路径，如 1,5,23 */
  depth?: string | null
  deptCode?: string
  deptName?: string
  sortNo?: number
  status?: number
  remark?: string | null
}

export type PickerOption = {
  value: IdValue
  text: string
  parentId?: IdValue
  depth?: string | null
}

export function listDepts(appId: IdValue): Promise<ApiResult<ModuleDept[]>> {
  return httpGet<ModuleDept[]>(`/v1/system/module/depts/apps/${pathId(appId)}`)
}

export function listDeptPickerOptions(appId: IdValue): Promise<ApiResult<PickerOption[]>> {
  return httpGet<PickerOption[]>(`/v1/system/module/depts/apps/${pathId(appId)}/picker`)
}

export function upsertDept(
  appId: IdValue,
  cmd: {
    id?: IdValue | null
    parentId?: IdValue
    deptCode: string
    deptName: string
    sortNo?: number
    status?: number
    remark?: string | null
  }
): Promise<ApiResult<ModuleDept>> {
  return httpPost<ModuleDept>(`/v1/system/module/depts/apps/${pathId(appId)}/upsert`, cmd)
}

export function deleteDepts(ids: IdValue[]): Promise<ApiResult<void>> {
  return httpPost<void>('/v1/system/module/depts/delete', { ids })
}

function pathId(value: IdValue): string {
  return encodeURIComponent(idToString(value))
}
