import { httpGet, httpPost } from '@/api/http'
import type { ApiResult } from '@/api/http'
import { idToString, type IdValue } from '@/utils/id'

export type FlowTemp = {
  id: IdValue
  tempCode?: string
  tempName?: string
  latestVerNo?: number
  status?: number
  remark?: string
}

export type FlowRecord = {
  id: IdValue
  title?: string
  status?: number
  bizType?: string
  bizId?: string
}

export type FlowTask = { id: string; recordId?: string; nodeName?: string; status?: number }

export function pageTemps(page = 1, size = 20): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/flow/temps/page?page=${q(page)}&size=${q(size)}`)
}

export function getTemp(id: IdValue): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/flow/temps/${pathId(id)}`)
}

export function upsertTemp(cmd: any): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temps/upsert', cmd)
}

export function deleteTemps(ids: any[]): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temps/delete', { ids })
}

export function pageTempVers(tempId: IdValue, page = 1, size = 20): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/flow/temp-vers/page?tempId=${pathId(tempId)}&page=${q(page)}&size=${q(size)}`)
}

export function getTempVer(id: IdValue): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/flow/temp-vers/${pathId(id)}`)
}

export function upsertTempVer(cmd: any): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-vers/upsert', cmd)
}

export function deleteTempVers(ids: any[]): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-vers/delete', { ids })
}

export function publishTempVer(id: IdValue): Promise<ApiResult<any>> {
  return httpPost<any>(`/v1/system/flow/temp-vers/${pathId(id)}/publish`)
}

/** 流程图可视化设计器负载（写节点/边表并生成 graphJson） */
export function loadGraphDesigner(tempVerId: IdValue): Promise<ApiResult<any>> {
  return httpGet<any>(
    `/v1/system/flow/temp-vers/${pathId(tempVerId)}/graph-designer`
  )
}

export function saveGraphDesigner(tempVerId: IdValue, body: any): Promise<ApiResult<any>> {
  return httpPost<any>(
    `/v1/system/flow/temp-vers/${pathId(tempVerId)}/graph-designer`,
    body
  )
}

export function startInstance(cmd: any): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/instances/start', cmd)
}

export function pageInstances(page = 1, size = 20, keyword?: string): Promise<ApiResult<any>> {
  const kw = (keyword || '').trim()
  return httpGet<any>(`/v1/system/flow/instances/page?page=${q(page)}&size=${q(size)}${kw ? `&keyword=${encodeURIComponent(kw)}` : ''}`)
}

export function pageMyInstances(page = 1, size = 20, keyword?: string): Promise<ApiResult<any>> {
  const kw = (keyword || '').trim()
  return httpGet<any>(
    `/v1/system/flow/instances/my/page?page=${q(page)}&size=${q(size)}${kw ? `&keyword=${encodeURIComponent(kw)}` : ''}`
  )
}

export function getInstance(instanceId: IdValue): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/flow/instances/${pathId(instanceId)}`)
}

export function listInstanceTasks(instanceId: IdValue): Promise<ApiResult<any[]>> {
  return httpGet<any[]>(`/v1/system/flow/instances/${pathId(instanceId)}/tasks`)
}

export function listInstanceActions(instanceId: IdValue): Promise<ApiResult<any[]>> {
  return httpGet<any[]>(`/v1/system/flow/instances/${pathId(instanceId)}/actions`)
}

export function listInstanceTraces(instanceId: IdValue): Promise<ApiResult<any[]>> {
  return httpGet<any[]>(`/v1/system/flow/instances/${pathId(instanceId)}/traces`)
}

export function inboxPending(limit = 50): Promise<ApiResult<FlowTask[]>> {
  return httpGet<FlowTask[]>(`/v1/system/flow/inbox/tasks/pending?limit=${q(limit)}`)
}

export function inboxCc(limit = 50): Promise<ApiResult<FlowTask[]>> {
  return httpGet<FlowTask[]>(`/v1/system/flow/inbox/cc?limit=${q(limit)}`)
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
export function pageTempVerNodes(tempVerId: IdValue): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/flow/temp-ver-nodes/page?tempVerId=${pathId(tempVerId)}&page=1&size=200`)
}
export function upsertTempVerNode(cmd: any): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-ver-nodes/upsert', cmd)
}
export function deleteTempVerNodes(ids: any[]): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-ver-nodes/delete', { ids })
}

export function pageTempVerLines(tempVerId: IdValue): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/flow/temp-ver-lines/page?tempVerId=${pathId(tempVerId)}&page=1&size=200`)
}
export function upsertTempVerLine(cmd: any): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-ver-lines/upsert', cmd)
}
export function deleteTempVerLines(ids: any[]): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-ver-lines/delete', { ids })
}

export function pageTempVerLineConds(lineId: IdValue): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/flow/temp-ver-line-conds/page?lineId=${pathId(lineId)}&page=1&size=200`)
}
export function upsertTempVerLineCond(cmd: any): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-ver-line-conds/upsert', cmd)
}
export function deleteTempVerLineConds(ids: any[]): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-ver-line-conds/delete', { ids })
}

export function pageTempVerSettings(tempVerId: IdValue): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/flow/temp-ver-settings/page?tempVerId=${pathId(tempVerId)}&page=1&size=200`)
}
export function upsertTempVerSetting(cmd: any): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-ver-settings/upsert', cmd)
}
export function deleteTempVerSettings(ids: any[]): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-ver-settings/delete', { ids })
}

export function pageTempVerNodeSettings(tempVerId: IdValue): Promise<ApiResult<any>> {
  return httpGet<any>(`/v1/system/flow/temp-ver-node-settings/page?tempVerId=${pathId(tempVerId)}&page=1&size=200`)
}
export function upsertTempVerNodeSetting(cmd: any): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-ver-node-settings/upsert', cmd)
}
export function deleteTempVerNodeSettings(ids: any[]): Promise<ApiResult<any>> {
  return httpPost<any>('/v1/system/flow/temp-ver-node-settings/delete', { ids })
}

function pathId(value: IdValue): string {
  return encodeURIComponent(idToString(value))
}

function q(value: string | number): string {
  return encodeURIComponent(String(value))
}

