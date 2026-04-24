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

export function deleteRecord(recordId: number): Promise<ApiResult<any>> {
  return httpRequest<any>('DELETE', `/v1/system/records/${recordId}`)
}

