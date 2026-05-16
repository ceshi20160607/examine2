import { httpGet, httpPost, httpRequest } from '@/api/http'
import type { ApiResult } from '@/api/http'

export function queryRecords(cmd: {
  appId: number
  modelId: number
  page: number
  limit: number
  filters?: any[]
}): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/records/query', cmd)
}

export function getRecord(recordId: number): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/records/${recordId}`)
}

export function createRecord(cmd: { appId: number; modelId: number; data: any }): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/records', cmd)
}

export function updateRecord(recordId: number, data: any): Promise<ApiResult<any>> {
  return httpPost<any>(`/v1/system/records/${recordId}/update`, { data })
}

export function deleteRecord(recordId: number): Promise<ApiResult<any>> {
  return httpRequest<any>('DELETE', `/v1/system/records/${recordId}`)
}

export type RecordHistoryRow = {
  id?: number
  recordId?: number
  action?: string
  dataJson?: string
  diffJson?: string
  createUserId?: number
  createTime?: string
}

export function listRecordHistory(recordId: number, limit = 50): Promise<ApiResult<RecordHistoryRow[]>> {
  return httpGet<RecordHistoryRow[]>(`/v1/system/records/${recordId}/history?limit=${limit}`)
}

