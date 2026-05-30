import { httpGet } from '@/api/http'
import type { ApiResult } from '@/api/http'
import { idToString, type IdValue } from '@/utils/id'

export type PickerOption = { value: IdValue; text: string; roleId?: IdValue; deptId?: IdValue }

export function listMemberPickerOptions(
  appId: IdValue,
  scope?: string,
  deptId?: IdValue
): Promise<ApiResult<PickerOption[]>> {
  const qs: string[] = []
  if (scope) qs.push(`scope=${encodeURIComponent(scope)}`)
  const did = idToString(deptId)
  if (did) qs.push(`deptId=${encodeURIComponent(did)}`)
  const q = qs.length ? `?${qs.join('&')}` : ''
  return httpGet<PickerOption[]>(`/v1/system/module/rbac/apps/${pathId(appId)}/picker/members${q}`)
}

export function listDepartmentPickerOptions(appId: IdValue): Promise<ApiResult<PickerOption[]>> {
  return httpGet<PickerOption[]>(`/v1/system/module/depts/apps/${pathId(appId)}/picker`)
}

function pathId(value: IdValue): string {
  return encodeURIComponent(idToString(value))
}
