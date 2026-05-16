import { httpGet } from '@/api/http'
import type { ApiResult } from '@/api/http'

export type PickerOption = { value: number | string; text: string; roleId?: number }

export function listMemberPickerOptions(appId: number): Promise<ApiResult<PickerOption[]>> {
  return httpGet<PickerOption[]>(`/v1/system/module/rbac/apps/${appId}/picker/members`)
}

export function listDepartmentPickerOptions(appId: number): Promise<ApiResult<PickerOption[]>> {
  return httpGet<PickerOption[]>(`/v1/system/module/rbac/apps/${appId}/picker/departments`)
}
