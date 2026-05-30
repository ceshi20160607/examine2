import { httpGet, httpPost } from '@/api/http'
import type { ApiResult } from '@/api/http'
import { idToString, type IdValue } from '@/utils/id'

export type ModuleApp = { id: string; appCode?: string; appName?: string; status?: number }
export type ModuleModel = { id: string; appId?: string; modelCode?: string; modelName?: string; status?: number }
export type ModuleField = {
  id: string
  fieldCode?: string
  fieldName?: string
  fieldType?: string
  dictCode?: string | null
  refModelId?: IdValue | null
  refDisplayField?: string | null
  relationModuleLabel?: string | null
  configJson?: string | null
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
  id?: IdValue | null
  appCode: string
  appName: string
  iconUrl?: string | null
  publishedFlag?: number
  remark?: string | null
  status?: number
}): Promise<ApiResult<any>> {
  return httpPost('/v1/system/module/meta/apps/upsert', {
    id: cmd.id ?? null,
    appCode: cmd.appCode,
    appName: cmd.appName,
    iconUrl: cmd.iconUrl ?? null,
    publishedFlag: cmd.publishedFlag ?? 0,
    remark: cmd.remark ?? null,
    status: cmd.status ?? 1
  })
}

export function deleteApps(ids: IdValue[]): Promise<ApiResult<void>> {
  return httpPost<void>('/v1/system/module/meta/apps/delete', { ids })
}

export function listModelsByApp(appId: IdValue): Promise<ApiResult<ModuleModel[]>> {
  return httpGet<ModuleModel[]>(`/v1/system/module/meta/apps/${pathId(appId)}/models`)
}

export function upsertModel(cmd: {
  id?: IdValue | null
  appId: IdValue
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

export function listFieldsByModel(modelId: IdValue): Promise<ApiResult<ModuleField[]>> {
  return httpGet<ModuleField[]>(`/v1/system/module/meta/models/${pathId(modelId)}/fields`)
}

export function deleteModels(ids: IdValue[]): Promise<ApiResult<void>> {
  return httpPost<void>('/v1/system/module/meta/models/delete', { ids })
}

export type FieldTypeDefinition = {
  code: string
  label: string
  needsDict?: boolean
  needsRef?: boolean
  allowsMulti?: boolean
  displayOnly?: boolean
  configKeys?: string[]
}

export function listFieldTypeDefinitions(): Promise<ApiResult<FieldTypeDefinition[]>> {
  return httpGet<FieldTypeDefinition[]>('/v1/system/module/meta/field-types')
}

export function upsertField(cmd: {
  id?: IdValue | null
  appId: IdValue
  modelId: IdValue
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
  refModelId?: IdValue | null
  refDisplayField?: string | null
  relationModuleLabel?: string | null
  configJson?: string | null
  multiFlag?: number
  defaultValue?: string | null
  sortNo?: number
  status?: number
}): Promise<ApiResult<ModuleField>> {
  return httpPost<ModuleField>('/v1/system/module/meta/fields/upsert', cmd)
}

export function deleteFields(ids: IdValue[]): Promise<ApiResult<void>> {
  return httpPost<void>('/v1/system/module/meta/fields/delete', { ids })
}

export type ModuleRelation = {
  id: string
  appId?: string
  srcModelId?: string
  dstModelId?: string
  relType?: string
  configJson?: string | null
}

export function listRelationsByApp(appId: IdValue): Promise<ApiResult<ModuleRelation[]>> {
  return httpGet<ModuleRelation[]>(`/v1/system/module/meta/apps/${pathId(appId)}/relations`)
}

export function upsertRelation(cmd: {
  id?: IdValue | null
  appId: IdValue
  srcModelId: IdValue
  dstModelId: IdValue
  relType: string
  configJson?: string | null
}): Promise<ApiResult<ModuleRelation>> {
  return httpPost<ModuleRelation>('/v1/system/module/meta/relations/upsert', cmd)
}

export function deleteRelations(ids: IdValue[]): Promise<ApiResult<void>> {
  return httpPost<void>('/v1/system/module/meta/relations/delete', { ids })
}

function pathId(value: IdValue): string {
  return encodeURIComponent(idToString(value))
}

