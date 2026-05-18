import { httpGet, httpPost } from '@/api/http'
import type { ApiResult } from '@/api/http'
import { setSessionPayload } from '@/store/context'

export type PlatSystem = {
  id: number
  name?: string
  ownerPlatAccountId?: number
  multiTenantEnabled?: number
}

export type PlatTenant = {
  id: number
  systemId?: number
  tenantName?: string
  status?: number
}

export type SessionPayload = {
  platId: number
  username: string
  systemId: number
  tenantId: number
}

export async function listMySystems(): Promise<ApiResult<PlatSystem[]>> {
  return httpGet<PlatSystem[]>('/v1/platform/systems')
}

export async function createSystem(name: string, multiTenantEnabled = 0): Promise<ApiResult<PlatSystem>> {
  return httpPost<PlatSystem>('/v1/platform/systems', { name, multiTenantEnabled })
}

export async function enterSystem(systemId: number): Promise<ApiResult<SessionPayload>> {
  const r = await httpPost<SessionPayload>('/v1/platform/context/enter-system', { systemId })
  if (r?.data) {
    setSessionPayload(r.data)
  }
  return r
}

export async function listTenants(systemId: number): Promise<ApiResult<PlatTenant[]>> {
  return httpGet<PlatTenant[]>(`/v1/platform/tenants?systemId=${systemId}`)
}

export async function selectTenant(tenantId: number): Promise<ApiResult<SessionPayload>> {
  const r = await httpPost<SessionPayload>('/v1/platform/context/select-tenant', { tenantId })
  if (r?.data) {
    setSessionPayload(r.data)
  }
  return r
}

