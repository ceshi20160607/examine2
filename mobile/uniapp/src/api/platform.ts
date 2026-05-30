import { httpDelete, httpGet, httpPost } from '@/api/http'
import type { ApiResult } from '@/api/http'
import { setSessionPayload } from '@/store/context'
import { idToString, type IdValue } from '@/utils/id'

export type PlatSystem = {
  id: string
  name?: string
  ownerPlatAccountId?: string
  multiTenantEnabled?: number
  status?: number
}

export type PlatTenant = {
  id: string
  systemId?: string
  tenantName?: string
  status?: number
}

export type SessionPayload = {
  platId: string
  username: string
  systemId: string
  tenantId: string
}

export type PlatformMessage = {
  id: string
  title?: string
  content?: string
  createdAt?: string
  createTime?: string
}

export type PlatformFlowTask = {
  id: string
  recordId?: string
  instanceId?: string
  systemId?: string
  tenantId?: string
  title?: string
  nodeName?: string
  readFlag?: number
  createTime?: string
  createdAt?: string
}

export async function listMySystems(): Promise<ApiResult<PlatSystem[]>> {
  return httpGet<PlatSystem[]>('/v1/platform/systems')
}

export async function createSystem(name: string, multiTenantEnabled = 0): Promise<ApiResult<PlatSystem>> {
  return httpPost<PlatSystem>('/v1/platform/systems', { name, multiTenantEnabled })
}

export async function setSystemStatus(systemId: IdValue, status: 1 | 2): Promise<ApiResult<void>> {
  return httpPost<void>(`/v1/platform/systems/${encodeURIComponent(idToString(systemId))}/status`, { status })
}

export async function deleteSystem(systemId: IdValue): Promise<ApiResult<void>> {
  return httpDelete<void>(`/v1/platform/systems/${encodeURIComponent(idToString(systemId))}`)
}

export async function enterSystem(systemId: IdValue): Promise<ApiResult<SessionPayload>> {
  const r = await httpPost<SessionPayload>('/v1/platform/context/enter-system', { systemId: idToString(systemId) })
  if (r?.data) {
    setSessionPayload(r.data)
  }
  return r
}

export async function listTenants(systemId: IdValue): Promise<ApiResult<PlatTenant[]>> {
  return httpGet<PlatTenant[]>(`/v1/platform/tenants?systemId=${encodeURIComponent(idToString(systemId))}`)
}

export async function selectTenant(tenantId: IdValue): Promise<ApiResult<SessionPayload>> {
  const r = await httpPost<SessionPayload>('/v1/platform/context/select-tenant', { tenantId: idToString(tenantId) })
  if (r?.data) {
    setSessionPayload(r.data)
  }
  return r
}

export function listPlatformMessages(limit = 50): Promise<ApiResult<PlatformMessage[]>> {
  return httpGet<PlatformMessage[]>(`/v1/platform/messages?limit=${limit}`)
}

export function listPlatformTodos(limit = 50, systemId?: IdValue, tenantId?: IdValue): Promise<ApiResult<PlatformFlowTask[]>> {
  const q = [`limit=${limit}`]
  const sid = idToString(systemId)
  const tid = idToString(tenantId)
  if (sid) q.push(`systemId=${encodeURIComponent(sid)}`)
  if (tid) q.push(`tenantId=${encodeURIComponent(tid)}`)
  return httpGet<PlatformFlowTask[]>(`/v1/platform/todos?${q.join('&')}`)
}

export function listPlatformCc(
  limit = 50,
  onlyUnread?: number,
  systemId?: IdValue,
  tenantId?: IdValue
): Promise<ApiResult<PlatformFlowTask[]>> {
  const q = [`limit=${limit}`]
  const sid = idToString(systemId)
  const tid = idToString(tenantId)
  if (onlyUnread != null) q.push(`onlyUnread=${onlyUnread}`)
  if (sid) q.push(`systemId=${encodeURIComponent(sid)}`)
  if (tid) q.push(`tenantId=${encodeURIComponent(tid)}`)
  return httpGet<PlatformFlowTask[]>(`/v1/platform/cc?${q.join('&')}`)
}

export function readPlatformCc(taskId: IdValue): Promise<ApiResult<void>> {
  return httpPost<void>(`/v1/platform/cc/${encodeURIComponent(idToString(taskId))}/read`, {})
}

