import { httpGet } from '@/api/http'
import type { ApiResult } from '@/api/http'

export type PickerOption = { value: number | string; text: string; roleId?: number; deptId?: number }

export function listMemberPickerOptions(
  appId: number,
  scope?: string,
  deptId?: number
): Promise<ApiResult<PickerOption[]>> {
  const qs: string[] = []
  if (scope) qs.push(`scope=${encodeURIComponent(scope)}`)
  if (deptId) qs.push(`deptId=${deptId}`)
  const q = qs.length ? `?${qs.join('&')}` : ''
  return httpGet<PickerOption[]>(`/v1/system/module/rbac/apps/${appId}/picker/members${q}`)
}

export function listDepartmentPickerOptions(appId: number): Promise<ApiResult<PickerOption[]>> {
  return httpGet<PickerOption[]>(`/v1/system/module/depts/apps/${appId}/picker`)
}
