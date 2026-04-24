import { httpGet, httpPost } from '@/api/http'
import type { ApiResult } from '@/api/http'

export type FlowTemp = {
  id: number | string
  tempCode?: string
  tempName?: string
  latestVerNo?: number
  status?: number
  remark?: string
}

export type FlowRecord = {
  id: number | string
  title?: string
  status?: number
  bizType?: string
  bizId?: string
}

export type FlowTask = { id: number; recordId?: number; nodeName?: string; status?: number }

export function pageTemps(page = 1, size = 20): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/flow/temps/page?page=${page}&size=${size}`)
}

export function getTemp(id: number | string): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/flow/temps/${encodeURIComponent(String(id))}`)
}

export function upsertTemp(cmd: any): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temps/upsert', cmd)
}

export function deleteTemps(ids: any[]): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temps/delete', { ids })
}

export function pageTempVers(tempId: number, page = 1, size = 20): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/flow/temp-vers/page?tempId=${tempId}&page=${page}&size=${size}`)
}

export function getTempVer(id: number | string): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/flow/temp-vers/${encodeURIComponent(String(id))}`)
}

export function upsertTempVer(cmd: any): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-vers/upsert', cmd)
}

export function deleteTempVers(ids: any[]): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-vers/delete', { ids })
}

export function publishTempVer(id: number | string): Promise<ApiResult<any>> {
  return httpPost<any>(`/v1/system/flow/temp-vers/${encodeURIComponent(String(id))}/publish`)
}

export function startInstance(cmd: any): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/instances/start', cmd)
}

export function pageInstances(page = 1, size = 20, keyword?: string): Promise<ApiResult<any>> {
  const kw = (keyword || '').trim()
  return httpGet<any>(`/v1/system/flow/instances/page?page=${page}&size=${size}${kw ? `&keyword=${encodeURIComponent(kw)}` : ''}`)
}

export function pageMyInstances(page = 1, size = 20, keyword?: string): Promise<ApiResult<any>> {
  const kw = (keyword || '').trim()
  return httpGet<any>(
    `/v1/system/flow/instances/my/page?page=${page}&size=${size}${kw ? `&keyword=${encodeURIComponent(kw)}` : ''}`
  )
}

export function getInstance(instanceId: number): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/flow/instances/${instanceId}`)
}

export function listInstanceTasks(instanceId: number): Promise<ApiResult<any[]>> {
  return httpGet<any[]>(`/v1/system/flow/instances/${instanceId}/tasks`)
}

export function listInstanceActions(instanceId: number): Promise<ApiResult<any[]>> {
  return httpGet<any[]>(`/v1/system/flow/instances/${instanceId}/actions`)
}

export function listInstanceTraces(instanceId: number): Promise<ApiResult<any[]>> {
  return httpGet<any[]>(`/v1/system/flow/instances/${instanceId}/traces`)
}

export function inboxPending(limit = 50): Promise<ApiResult<FlowTask[]>> {
  return httpGet<FlowTask[]>(`/v1/system/flow/inbox/tasks/pending?limit=${limit}`)
}

export function inboxCc(limit = 50): Promise<ApiResult<FlowTask[]>> {
  return httpGet<FlowTask[]>(`/v1/system/flow/inbox/cc?limit=${limit}`)
}

export function byBiz(bizType: string, bizId: string): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/flow/instances/by-biz?bizType=${encodeURIComponent(bizType)}&bizId=${encodeURIComponent(bizId)}`)
}

export function byBizWithPending(bizType: string, bizId: string): Promise<ApiResult<any>> {
  return httpGet<any>(
    `/v1/system/flow/instances/by-biz/with-pending-tasks?bizType=${encodeURIComponent(bizType)}&bizId=${encodeURIComponent(bizId)}`
  )
}

export function byBizActionable(bizType: string, bizId: string): Promise<ApiResult<any>> {
  return httpGet<any>(
    `/v1/system/flow/instances/by-biz/actionable-tasks?bizType=${encodeURIComponent(bizType)}&bizId=${encodeURIComponent(bizId)}`
  )
}

export function actTask(path: string, body?: any): Promise<ApiResult<any>> {
  return httpPost<any>(path, body)
}

// temp-ver nodes/lines/settings
export function pageTempVerNodes(tempVerId: number): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/flow/temp-ver-nodes/page?tempVerId=${tempVerId}&page=1&size=200`)
}
export function upsertTempVerNode(cmd: any): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-ver-nodes/upsert', cmd)
}
export function deleteTempVerNodes(ids: any[]): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-ver-nodes/delete', { ids })
}

export function pageTempVerLines(tempVerId: number): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/flow/temp-ver-lines/page?tempVerId=${tempVerId}&page=1&size=200`)
}
export function upsertTempVerLine(cmd: any): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-ver-lines/upsert', cmd)
}
export function deleteTempVerLines(ids: any[]): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-ver-lines/delete', { ids })
}

export function pageTempVerLineConds(lineId: number): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/flow/temp-ver-line-conds/page?lineId=${lineId}&page=1&size=200`)
}
export function upsertTempVerLineCond(cmd: any): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-ver-line-conds/upsert', cmd)
}
export function deleteTempVerLineConds(ids: any[]): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-ver-line-conds/delete', { ids })
}

export function pageTempVerSettings(tempVerId: number): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/flow/temp-ver-settings/page?tempVerId=${tempVerId}&page=1&size=200`)
}
export function upsertTempVerSetting(cmd: any): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-ver-settings/upsert', cmd)
}
export function deleteTempVerSettings(ids: any[]): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-ver-settings/delete', { ids })
}

export function pageTempVerNodeSettings(tempVerId: number): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/flow/temp-ver-node-settings/page?tempVerId=${tempVerId}&page=1&size=200`)
}
export function upsertTempVerNodeSetting(cmd: any): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-ver-node-settings/upsert', cmd)
}
export function deleteTempVerNodeSettings(ids: any[]): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-ver-node-settings/delete', { ids })
}

