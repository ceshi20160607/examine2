import { httpGet, httpPost, httpPut, httpRequest } from '@/api/http'
import type { ApiResult } from '@/api/http'

export type OpenAppClient = {
  id: number
  clientCode?: string
  clientName?: string
  contactName?: string
  contactMobile?: string
  contactEmail?: string
  status?: number
  remark?: string
  createTime?: string
  updateTime?: string
}

export type OpenAppClientDetail = {
  client?: OpenAppClient
  activeAccessKey?: string
}

export type OpenAppCredential = {
  clientId?: number
  accessKey?: string
  secret?: string
}

export function listOpenApps(): Promise<ApiResult<OpenAppClient[]>> {
  return httpGet<OpenAppClient[]>('/v1/platform/apps')
}

export function getOpenApp(id: number): Promise<ApiResult<OpenAppClientDetail>> {
  return httpGet<OpenAppClientDetail>(`/v1/platform/apps/${id}`)
}

export function createOpenApp(body: {
  clientCode: string
  clientName: string
  contactName?: string | null
  contactMobile?: string | null
  contactEmail?: string | null
  remark?: string | null
}): Promise<ApiResult<OpenAppCredential>> {
  return httpPost<OpenAppCredential>('/v1/platform/apps', body)
}

export function updateOpenApp(
  id: number,
  body: {
    clientName: string
    contactName?: string | null
    contactMobile?: string | null
    contactEmail?: string | null
    remark?: string | null
  }
): Promise<ApiResult<void>> {
  return httpPut<void>(`/v1/platform/apps/${id}`, body)
}

export function setOpenAppStatus(id: number, status: 1 | 2): Promise<ApiResult<void>> {
  return httpPost<void>(`/v1/platform/apps/${id}/status`, { status })
}

export function deleteOpenApp(id: number): Promise<ApiResult<void>> {
  return httpRequest<void>('DELETE', `/v1/platform/apps/${id}`)
}

export function rotateOpenAppSecret(id: number): Promise<ApiResult<OpenAppCredential>> {
  return httpPost<OpenAppCredential>(`/v1/platform/apps/${id}/rotate-secret`, {})
}
