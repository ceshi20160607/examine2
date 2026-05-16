import { httpGet, httpPost } from '@/api/http'
import type { ApiResult } from '@/api/http'

export type ModuleDept = {
  id: number
  appId?: number
  parentId?: number
  deptCode?: string
  deptName?: string
  sortNo?: number
  status?: number
  remark?: string | null
}

export type PickerOption = { value: number | string; text: string; parentId?: number }

export function listDepts(appId: number): Promise<ApiResult<ModuleDept[]>> {
  return httpGet<ModuleDept[]>(`/v1/system/module/depts/apps/${appId}`)
}

export function listDeptPickerOptions(appId: number): Promise<ApiResult<PickerOption[]>> {
  return httpGet<PickerOption[]>(`/v1/system/module/depts/apps/${appId}/picker`)
}

export function upsertDept(
  appId: number,
  cmd: {
    id?: number | null
    parentId?: number
    deptCode: string
    deptName: string
    sortNo?: number
    status?: number
    remark?: string | null
  }
): Promise<ApiResult<ModuleDept>> {
  return httpPost<ModuleDept>(`/v1/system/module/depts/apps/${appId}/upsert`, cmd)
}

export function deleteDepts(ids: number[]): Promise<ApiResult<void>> {
  return httpPost<void>('/v1/system/module/depts/delete', { ids })
}
