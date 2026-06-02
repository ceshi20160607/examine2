import { httpGet, httpPost, httpRequest } from '@/api/http'
import type { ApiResult } from '@/api/http'
import { idToString, type IdValue } from '@/utils/id'

export function queryRecords(cmd: {
  appId: IdValue
  modelId: IdValue
  page: number
  limit: number
  filters?: any[]
  includeFieldCodes?: string[]
}): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/records/query', cmd)
}

export function getRecord(recordId: IdValue): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/records/${pathId(recordId)}`)
}

export function createRecord(cmd: { appId: IdValue; modelId: IdValue; data: any }): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/records', cmd)
}

export function updateRecord(recordId: IdValue, data: any): Promise<ApiResult<any>> {
  return httpPost<any>(`/v1/system/records/${pathId(recordId)}/update`, { data })
}

export function deleteRecord(recordId: IdValue): Promise<ApiResult<any>> {
  return httpRequest<any>('DELETE', `/v1/system/records/${pathId(recordId)}`)
}

export type RecordHistoryRow = {
  id?: IdValue
  recordId?: IdValue
  action?: string
  dataJson?: string
  diffJson?: string
  createUserId?: IdValue
  createTime?: string
}

export function listRecordHistory(recordId: IdValue, limit = 50): Promise<ApiResult<RecordHistoryRow[]>> {
  return httpGet<RecordHistoryRow[]>(`/v1/system/records/${pathId(recordId)}/history?limit=${encodeURIComponent(String(limit))}`)
}

export function queryRecordsByRelation(cmd: {
  relationId: IdValue
  parentRecordId: IdValue
  query?: {
    appId?: IdValue
    modelId?: IdValue
    page?: number
    limit?: number
    filters?: any[]
    includeFieldCodes?: string[]
  }
}): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/records/query-by-relation', cmd)
}

export function attachRelation(cmd: {
  relationId: IdValue
  parentRecordId: IdValue
  childRecordId: IdValue
}): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/records/relations/attach', cmd)
}

export function detachRelation(cmd: {
  relationId: IdValue
  parentRecordId: IdValue
  childRecordId: IdValue
}): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/records/relations/detach', cmd)
}

function pathId(value: IdValue): string {
  return encodeURIComponent(idToString(value))
}
