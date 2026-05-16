import { httpGet, httpPost } from '@/api/http'
import type { ApiResult } from '@/api/http'

export type ModuleApp = { id: number; appCode?: string; appName?: string; status?: number }
export type ModuleModel = { id: number; appId?: number; modelCode?: string; modelName?: string; status?: number }
export type ModuleField = {
  id: number
  fieldCode?: string
  fieldName?: string
  fieldType?: string
  dictCode?: string | null
  refModelId?: number | null
  refDisplayField?: string | null
  requiredFlag?: number
  uniqueFlag?: number
  hiddenFlag?: number
  tips?: string | null
  maxLength?: number | null
  minLength?: number | null
  validateType?: string | null
  dateFormat?: string | null
  multiFlag?: number
  defaultValue?: string | null
  sortNo?: number
  status?: number
}

export function listApps(): Promise<ApiResult<ModuleApp[]>> {
  return httpGet<ModuleApp[]>('/v1/system/module/meta/apps')
}

export function upsertApp(cmd: {
  appCode: string
  appName: string
  iconUrl?: string | null
  publishedFlag?: number
  remark?: string | null
  status?: number
}): Promise<ApiResult<any>> {
  return httpPost('/v1/system/module/meta/apps/upsert', {
    appCode: cmd.appCode,
    appName: cmd.appName,
    iconUrl: cmd.iconUrl ?? null,
    publishedFlag: cmd.publishedFlag ?? 0,
    remark: cmd.remark ?? null,
    status: cmd.status ?? 1
  })
}

export function listModelsByApp(appId: number): Promise<ApiResult<ModuleModel[]>> {
  return httpGet<ModuleModel[]>(`/v1/system/module/meta/apps/${appId}/models`)
}

export function upsertModel(cmd: {
  id?: number | null
  appId: number
  modelCode: string
  modelName: string
  status?: number
  remark?: string | null
}): Promise<ApiResult<any>> {
  return httpPost('/v1/system/module/meta/models/upsert', {
    id: cmd.id ?? null,
    appId: cmd.appId,
    modelCode: cmd.modelCode,
    modelName: cmd.modelName,
    status: cmd.status ?? 1,
    remark: cmd.remark ?? null
  })
}

export function listFieldsByModel(modelId: number): Promise<ApiResult<ModuleField[]>> {
  return httpGet<ModuleField[]>(`/v1/system/module/meta/models/${modelId}/fields`)
}

export function upsertField(cmd: {
  id?: number | null
  appId: number
  modelId: number
  fieldCode: string
  fieldName: string
  fieldType: string
  requiredFlag?: number
  uniqueFlag?: number
  hiddenFlag?: number
  tips?: string | null
  maxLength?: number | null
  minLength?: number | null
  validateType?: string | null
  dateFormat?: string | null
  dictCode?: string | null
  refModelId?: number | null
  refDisplayField?: string | null
  multiFlag?: number
  defaultValue?: string | null
  sortNo?: number
  status?: number
}): Promise<ApiResult<ModuleField>> {
  return httpPost<ModuleField>('/v1/system/module/meta/fields/upsert', cmd)
}

export function deleteFields(ids: number[]): Promise<ApiResult<void>> {
  return httpPost<void>('/v1/system/module/meta/fields/delete', { ids })
}

